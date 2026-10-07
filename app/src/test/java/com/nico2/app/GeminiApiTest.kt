package com.nico2.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiApiTest {
    @Test
    fun classifiesLiveFailuresByHttpStatusAndConnectionStage() {
        assertEquals(
            LiveFailureCategory.ApiKey,
            classifyLiveFailure(403, LiveFailureStage.Connection),
        )
        assertEquals(
            LiveFailureCategory.Quota,
            classifyLiveFailure(429, LiveFailureStage.Setup),
        )
        assertEquals(
            LiveFailureCategory.Model,
            classifyLiveFailure(404, LiveFailureStage.Setup),
        )
        assertEquals(
            LiveFailureCategory.Server,
            classifyLiveFailure(503, LiveFailureStage.Setup),
        )
        assertEquals(
            LiveFailureCategory.NoResponse,
            classifyLiveFailure(null, LiveFailureStage.Setup),
        )
        assertEquals(
            LiveFailureCategory.Network,
            classifyLiveFailure(null, LiveFailureStage.Connection, "UnknownHostException"),
        )
        assertEquals(
            LiveFailureCategory.Audio,
            classifyLiveFailure(null, LiveFailureStage.Audio, "IllegalStateException"),
        )
        assertEquals(
            LiveFailureCategory.Quota,
            classifyLiveFailure(
                null,
                LiveFailureStage.Setup,
                serverStatus = "RESOURCE_EXHAUSTED",
            ),
        )
    }

    @Test
    fun liveFailureDiagnosticIncludesCategoryStageModelAndHttpStatus() {
        val diagnostic = formatLiveFailureDiagnostic(
            category = "API key",
            stage = "WebSocket",
            modelId = "gemini-live",
            httpStatus = 403,
            details = "Permission denied",
        )

        assertTrue(diagnostic.contains("تشخیص: API key"))
        assertTrue(diagnostic.contains("مرحله: WebSocket"))
        assertTrue(diagnostic.contains("مدل: gemini-live"))
        assertTrue(diagnostic.contains("HTTP: 403"))
        assertTrue(diagnostic.contains("Permission denied"))
    }

    @Test
    fun noSetupResponseIsNotMisreportedAsAnApiServerFailure() {
        assertEquals(
            LiveFailureCategory.NoResponse,
            classifyLiveFailure(
                httpStatus = null,
                stage = LiveFailureStage.Setup,
                errorType = null,
                serverStatus = null,
            ),
        )
    }

    @Test
    fun liveSetupUsesLiveApiResponseModalitiesShape() {
        val setup = buildGeminiLiveSetup("gemini-3.8-live").getAsJsonObject("setup")

        assertEquals("models/gemini-3.8-live", setup.get("model").asString)
        assertEquals(2, setup.size())
        assertFalse(setup.has("thinkingConfig"))
        assertFalse(setup.has("responseModalities"))
        assertEquals(
            "AUDIO",
            setup.getAsJsonObject("generationConfig")
                .getAsJsonArray("responseModalities")
                .single()
                .asString,
        )
    }

    @Test
    fun liveChatSetupEnablesTranscriptsWithoutChangingAudioModality() {
        val setup = buildGeminiLiveSetup(
            modelId = "gemini-3.8-live",
            includeInputAudioTranscription = true,
            includeOutputAudioTranscription = true,
            systemInstruction = "Be concise.",
        ).getAsJsonObject("setup")

        assertEquals(
            "AUDIO",
            setup.getAsJsonObject("generationConfig")
                .getAsJsonArray("responseModalities")
                .single()
                .asString,
        )
        assertFalse(setup.has("responseModalities"))
        assertTrue(setup.has("inputAudioTranscription"))
        assertTrue(setup.has("outputAudioTranscription"))
        assertEquals(
            "Be concise.",
            setup.getAsJsonObject("systemInstruction")
                .getAsJsonArray("parts")
                .single()
                .asJsonObject
                .get("text")
                .asString,
        )
    }

    @Test
    fun liveChatPayloadIncludesHistoryWithRolesAndCompletesTurn() {
        val content = buildGeminiLiveClientContent(
            listOf(
                GeminiTurn("Question", isUser = true),
                GeminiTurn("Previous answer", isUser = false),
            ),
        ).getAsJsonObject("clientContent")

        assertEquals(2, content.getAsJsonArray("turns").size())
        assertEquals(
            "user",
            content.getAsJsonArray("turns")[0].asJsonObject.get("role").asString,
        )
        assertEquals(
            "model",
            content.getAsJsonArray("turns")[1].asJsonObject.get("role").asString,
        )
        assertTrue(content.get("turnComplete").asBoolean)
    }

    @Test
    fun liveSetupDoesNotDuplicateModelResourcePrefix() {
        val setup = buildGeminiLiveSetup("models/gemini-3.8-live").getAsJsonObject("setup")

        assertEquals("models/gemini-3.8-live", setup.get("model").asString)
    }

    @Test
    fun liveDiagnosticsRedactApiKeysAndLimitDisplayedLength() {
        val apiKey = "private-live-key"
        val diagnostic = sanitizeLiveDiagnostic(
            """HTTP failure at ?key=$apiKey and key=AIzaSy0123456789012345678901234567890123""",
            apiKey,
        )

        assertFalse(diagnostic.contains(apiKey))
        assertFalse(diagnostic.contains("AIzaSy0123456789012345678901234567890123"))
        assertTrue(diagnostic.contains("[REDACTED]"))
        assertEquals(320, sanitizeLiveDiagnostic("x".repeat(1_000), "").length)
    }

    @Test
    fun listsOnlyRequestedModelsAndRecognizesLiveModel() = runBlocking {
        val transport = FakeTransport(
            GeminiHttpResponse(
                200,
                """
                {
                  "models": [
                    {
                      "name": "models/gemini-3.8-flash",
                      "displayName": "Gemini 3.8 Flash",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-3.8-live",
                      "displayName": "Gemini 3.8 Live",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-3.7-flash",
                      "displayName": "Gemini 3.7 Flash",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-2.6-flash",
                      "displayName": "Gemini 2.6 Flash",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-3.5-flash-lite",
                      "displayName": "Gemini 3.5 Flash Lite",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-3.1-flash-lite",
                      "displayName": "Gemini 3.1 Flash Lite",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/gemini-2.5-pro",
                      "displayName": "Gemini 2.5 Pro",
                      "supportedGenerationMethods": ["generateContent"]
                    },
                    {
                      "name": "models/embedding-model",
                      "supportedGenerationMethods": ["embedContent"]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )

        val models = GeminiApiClient(transport).listModels("private-key")

        assertEquals(
            listOf(
                "gemini-2.6-flash",
                "gemini-3.1-flash-lite",
                "gemini-3.5-flash-lite",
                "gemini-3.7-flash",
                "gemini-3.8-flash",
                "gemini-3.8-live",
            ),
            models.map { it.id }.sorted(),
        )
        assertTrue(models.single { it.id == "gemini-3.8-live" }.supportsLive)
        assertTrue(models.single { it.id == "gemini-3.8-live" }.supportsText)
        assertTrue(models.filter { it.id != "gemini-3.8-live" }.all { it.supportsText })
    }

    @Test
    fun selectedTextModelIsTriedBeforeOtherAvailableModels() {
        val models = listOf(
            GeminiModel("gemini-z", "Zulu", supportsText = true, supportsLive = false),
            GeminiModel("gemini-a", "Alpha", supportsText = true, supportsLive = false),
            GeminiModel("gemini-live", "Live", supportsText = false, supportsLive = true),
        )

        val ordered = GeminiApiClient.textFallbackOrder("gemini-z", models)

        assertEquals(listOf("gemini-z", "gemini-a"), ordered.map { it.id })
    }

    @Test
    fun liveFallbackPrefersOfficialLiveModelAndExcludesTextOnlyModels() {
        val models = listOf(
            GeminiModel("gemini-live-z", "Zulu Live", supportsText = false, supportsLive = true),
            GeminiModel("gemini-3.8-live", "Gemini 3.8 Live", supportsText = false, supportsLive = true),
            GeminiModel("gemini-text", "Text", supportsText = true, supportsLive = false),
        )

        val ordered = GeminiLiveSession.liveFallbackOrder(models)

        assertEquals(listOf("gemini-3.8-live", "gemini-live-z"), ordered.map { it.id })
    }

    @Test
    fun retriesNextTextModelOnQuotaAndReturnsModelUsed() = runBlocking {
        val transport = FakeTransport(
            GeminiHttpResponse(429, """{"error":{"message":"quota exceeded"}}"""),
            GeminiHttpResponse(
                200,
                """{"candidates":[{"content":{"parts":[{"text":"پاسخ"}]}}]}""",
            ),
        )
        val models = listOf(
            GeminiModel("gemini-primary", "Primary", supportsText = true, supportsLive = false),
            GeminiModel("gemini-fallback", "Fallback", supportsText = true, supportsLive = false),
        )

        val result = GeminiApiClient(transport).generateReply(
            apiKey = "private-key",
            selectedModelId = "gemini-primary",
            availableModels = models,
            turns = listOf(GeminiTurn("سلام", isUser = true)),
            systemInstruction = "Be helpful.",
        )

        assertEquals("gemini-fallback" to "پاسخ", result)
        assertEquals(
            listOf(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-primary:generateContent",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-fallback:generateContent",
            ),
            transport.urls,
        )
    }

    @Test
    fun sendsImagePartsAndSystemInstructionsToSelectedModel() = runBlocking {
        val transport = FakeTransport(
            GeminiHttpResponse(
                200,
                """{"candidates":[{"content":{"parts":[{"text":"Image understood"}]}}]}""",
            ),
        )
        val models = listOf(
            GeminiModel("gemini-text", "Text model", supportsText = true, supportsLive = false),
        )

        GeminiApiClient(transport).generateReply(
            apiKey = "private-key",
            selectedModelId = "gemini-text",
            availableModels = models,
            turns = listOf(
                GeminiTurn(
                    text = "What is in this image?",
                    isUser = true,
                    attachments = listOf(
                        GeminiInlineAttachment("image/jpeg", byteArrayOf(1, 2, 3)),
                    ),
                ),
            ),
            systemInstruction = "Reply in the user's language.",
        )

        val body = transport.bodies.single().orEmpty()
        assertTrue(body.contains("\"mimeType\":\"image/jpeg\""))
        assertTrue(body.contains("\"data\":\"AQID\""))
        assertTrue(body.contains("Reply in the user's language."))
    }

    @Test
    fun doesNotRetryOtherModelsForInvalidKeyAndRedactsKeyFromError() = runBlocking {
        val apiKey = "sensitive-test-key"
        val transport = FakeTransport(
            GeminiHttpResponse(401, """{"error":{"message":"Rejected $apiKey"}}"""),
            GeminiHttpResponse(
                200,
                """{"candidates":[{"content":{"parts":[{"text":"should not be used"}]}}]}""",
            ),
        )
        val models = listOf(
            GeminiModel("gemini-primary", "Primary", supportsText = true, supportsLive = false),
            GeminiModel("gemini-fallback", "Fallback", supportsText = true, supportsLive = false),
        )

        val error = assertThrows(GeminiApiException::class.java) {
            runBlocking {
                GeminiApiClient(transport).generateReply(
                    apiKey = apiKey,
                    selectedModelId = "gemini-primary",
                    availableModels = models,
                    turns = listOf(GeminiTurn("سلام", isUser = true)),
                    systemInstruction = "Be helpful.",
                )
            }
        }

        assertTrue(error.message.orEmpty().contains("[کلید پنهان]"))
        assertTrue(!error.message.orEmpty().contains(apiKey))
        assertEquals(1, transport.urls.size)
    }

    private class FakeTransport(
        vararg responses: GeminiHttpResponse,
    ) : GeminiHttpTransport {
        private val queuedResponses = ArrayDeque(responses.toList())
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String?>()

        override fun execute(
            apiKey: String,
            url: String,
            method: String,
            body: String?,
        ): GeminiHttpResponse {
            urls += url
            bodies += body
            return queuedResponses.removeFirst()
        }
    }
}
