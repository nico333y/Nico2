package com.nico2.app

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.net.URLEncoder

data class GeminiModel(
    val id: String,
    val displayName: String,
    val supportsText: Boolean,
    val supportsLive: Boolean,
)

data class GeminiTurn(
    val text: String,
    val isUser: Boolean,
    val attachments: List<GeminiInlineAttachment> = emptyList(),
)

data class GeminiInlineAttachment(
    val mimeType: String,
    val data: ByteArray,
)

class GeminiApiException(
    val statusCode: Int,
    message: String,
) : Exception(message)

internal data class GeminiHttpResponse(val statusCode: Int, val body: String)

internal fun interface GeminiHttpTransport {
    fun execute(apiKey: String, url: String, method: String, body: String?): GeminiHttpResponse
}

private object UrlConnectionGeminiTransport : GeminiHttpTransport {
    override fun execute(
        apiKey: String,
        url: String,
        method: String,
        body: String?,
    ): GeminiHttpResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output ->
                    output.write(body.toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return GeminiHttpResponse(status, response)
        } finally {
            connection.disconnect()
        }
    }
}

class GeminiApiClient internal constructor(
    private val transport: GeminiHttpTransport = UrlConnectionGeminiTransport,
) {
    suspend fun listModels(apiKey: String): List<GeminiModel> = withContext(Dispatchers.IO) {
        val models = mutableListOf<GeminiModel>()
        var pageToken: String? = null
        var pages = 0
        do {
            val pageQuery = pageToken?.let {
                "&pageToken=${URLEncoder.encode(it, Charsets.UTF_8.name())}"
            }.orEmpty()
            val response = request(
                apiKey = apiKey,
                url = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100$pageQuery",
                method = "GET",
            )
            val payload = JsonParser.parseString(response).asJsonObject
            payload.getAsJsonArray("models")?.forEach { entry ->
                val model = entry.asJsonObject.toGeminiModel()
                if (model.id in SUPPORTED_MODEL_IDS &&
                    (model.supportsText || model.supportsLive)
                ) {
                    models += model
                }
            }
            pageToken = payload.get("nextPageToken")
                ?.takeUnless { it.isJsonNull }
                ?.asString
            pages += 1
        } while (pageToken != null && pages < MAX_MODEL_PAGES)

        models.distinctBy { it.id }.sortedBy { it.displayName.lowercase() }
    }

    suspend fun generateReply(
        apiKey: String,
        selectedModelId: String,
        availableModels: List<GeminiModel>,
        turns: List<GeminiTurn>,
        systemInstruction: String,
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val candidates = textFallbackOrder(selectedModelId, availableModels)
        if (candidates.isEmpty()) {
            throw GeminiApiException(0, "هیچ مدل متنی سازگاری برای این کلید پیدا نشد.")
        }

        var quotaError: GeminiApiException? = null
        for (model in candidates) {
            try {
                return@withContext model.id to generateWithModel(
                    apiKey = apiKey,
                    modelId = model.id,
                    turns = turns,
                    systemInstruction = systemInstruction,
                )
            } catch (error: GeminiApiException) {
                if (error.statusCode != HTTP_TOO_MANY_REQUESTS) throw error
                quotaError = error
            }
        }
        throw quotaError ?: GeminiApiException(
            HTTP_TOO_MANY_REQUESTS,
            "سهمیهٔ همهٔ مدل‌های متنی در دسترس پر شده است.",
        )
    }

    private fun generateWithModel(
        apiKey: String,
        modelId: String,
        turns: List<GeminiTurn>,
        systemInstruction: String,
    ): String {
        val content = JsonArray()
        turns.takeLast(MAX_CONTEXT_TURNS).forEach { turn ->
            val parts = JsonArray()
            if (turn.text.isNotBlank()) {
                parts.add(JsonObject().apply { addProperty("text", turn.text) })
            }
            turn.attachments.forEach { attachment ->
                require(attachment.mimeType in LocalRepository.ALLOWED_ATTACHMENT_MIME_TYPES)
                require(attachment.data.size <= LocalRepository.MAX_ATTACHMENT_BYTES)
                parts.add(JsonObject().apply {
                    add("inlineData", JsonObject().apply {
                        addProperty("mimeType", attachment.mimeType)
                        addProperty(
                            "data",
                            Base64.getEncoder().encodeToString(attachment.data),
                        )
                    })
                })
            }
            if (parts.size() == 0) return@forEach
            content.add(JsonObject().apply {
                addProperty("role", if (turn.isUser) "user" else "model")
                add("parts", parts)
            })
        }
        val body = JsonObject().apply {
            add("contents", content)
            add("systemInstruction", JsonObject().apply {
                add("parts", JsonArray().apply {
                    add(JsonObject().apply { addProperty("text", systemInstruction) })
                })
            })
            add("generationConfig", JsonObject().apply {
                addProperty("temperature", 0.7)
            })
        }
        val encodedModel = URLEncoder.encode(modelId, Charsets.UTF_8.name())
        val response = request(
            apiKey = apiKey,
            url = "https://generativelanguage.googleapis.com/v1beta/models/$encodedModel:generateContent",
            method = "POST",
            body = body.toString(),
        )
        val payload = JsonParser.parseString(response).asJsonObject
        val candidates = payload.getAsJsonArray("candidates")
        val parts = candidates
            ?.firstOrNull()
            ?.asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
        val answer = parts?.mapNotNull { part ->
            part.asJsonObject.get("text")?.takeUnless { it.isJsonNull }?.asString
        }?.joinToString("")?.trim()
        return answer?.takeIf { it.isNotBlank() }
            ?: throw GeminiApiException(0, "مدل پاسخ متنی برنگرداند.")
    }

    private fun request(
        apiKey: String,
        url: String,
        method: String,
        body: String? = null,
    ): String {
        val result = transport.execute(apiKey, url, method, body)
        if (result.statusCode !in 200..299) {
            val serverMessage = runCatching {
                JsonParser.parseString(result.body).asJsonObject
                    .getAsJsonObject("error")
                    ?.get("message")
                    ?.asString
            }.getOrNull()
            val message = serverMessage
                ?.replace(apiKey, "[کلید پنهان]")
                ?.takeIf { it.isNotBlank() }
                ?: "درخواست Gemini ناموفق بود (HTTP ${result.statusCode})."
            throw GeminiApiException(result.statusCode, message)
        }
        return result.body
    }

    private fun JsonObject.toGeminiModel(): GeminiModel {
        val modelName = get("name")?.asString.orEmpty()
        val id = modelName.removePrefix("models/")
        val methods = getAsJsonArray("supportedGenerationMethods")
            ?.mapNotNull { it.asString }
            .orEmpty()
        return GeminiModel(
            id = id,
            displayName = get("displayName")?.asString?.ifBlank { null } ?: id,
            supportsText = "generateContent" in methods,
            supportsLive = id == LIVE_MODEL_ID || "BidiGenerateContent" in methods,
        )
    }

    companion object {
        private const val MAX_CONTEXT_TURNS = 16
        private const val MAX_MODEL_PAGES = 20
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val LIVE_MODEL_ID = "gemini-3.8-live"
        private val SUPPORTED_MODEL_IDS = setOf(
            "gemini-3.8-flash",
            "gemini-3.7-flash",
            "gemini-2.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            LIVE_MODEL_ID,
        )

        internal fun textFallbackOrder(
            selectedModelId: String,
            models: List<GeminiModel>,
        ): List<GeminiModel> {
            val eligible = models.filter { it.supportsText }
            return buildList {
                eligible.firstOrNull { it.id == selectedModelId }?.let(::add)
                eligible.filterNot { it.id == selectedModelId }
                    .sortedBy { it.displayName.lowercase() }
                    .forEach(::add)
            }
        }
    }
}
