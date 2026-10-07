package com.nico2.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import com.google.gson.JsonArray
import com.google.gson.JsonParseException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resumeWithException

private const val MAX_DIAGNOSTIC_LENGTH = 320
private const val LIVE_CHAT_SETUP_TIMEOUT_MILLIS = 15_000L
private const val LIVE_CHAT_RESPONSE_TIMEOUT_MILLIS = 60_000L
private const val LIVE_ENDPOINT =
    "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

internal enum class LiveFailureCategory {
    Network,
    ApiKey,
    Quota,
    Model,
    ApiRequest,
    Server,
    NoResponse,
    Device,
    Audio,
    Protocol,
    Unknown,
}

internal enum class LiveFailureStage {
    Connection,
    Setup,
    Audio,
    AudioStream,
    Session,
    Protocol,
    Unknown,
}

internal fun classifyLiveFailure(
    httpStatus: Int?,
    stage: LiveFailureStage,
    errorType: String? = null,
    serverStatus: String? = null,
): LiveFailureCategory {
    when (serverStatus.orEmpty().uppercase()) {
        "UNAUTHENTICATED", "PERMISSION_DENIED" -> return LiveFailureCategory.ApiKey
        "RESOURCE_EXHAUSTED" -> return LiveFailureCategory.Quota
        "NOT_FOUND" -> return LiveFailureCategory.Model
        "UNAVAILABLE", "INTERNAL" -> return LiveFailureCategory.Server
        "INVALID_ARGUMENT" -> return LiveFailureCategory.ApiRequest
    }
    when (httpStatus) {
        401, 403 -> return LiveFailureCategory.ApiKey
        404 -> return LiveFailureCategory.Model
        429 -> return LiveFailureCategory.Quota
        400 -> return LiveFailureCategory.ApiRequest
        in 500..599 -> return LiveFailureCategory.Server
        in 400..499 -> return LiveFailureCategory.ApiRequest
    }
    if (errorType.orEmpty().lowercase() in NETWORK_ERROR_TYPES) {
        return LiveFailureCategory.Network
    }
    return when (stage) {
        LiveFailureStage.Connection -> LiveFailureCategory.Network
        LiveFailureStage.Setup -> LiveFailureCategory.NoResponse
        LiveFailureStage.Session -> LiveFailureCategory.Server
        LiveFailureStage.Audio, LiveFailureStage.AudioStream -> LiveFailureCategory.Audio
        LiveFailureStage.Protocol -> LiveFailureCategory.Protocol
        LiveFailureStage.Unknown -> LiveFailureCategory.Unknown
    }
}

internal fun liveFailureDiagnosticCode(
    category: LiveFailureCategory,
    stage: LiveFailureStage,
    httpStatus: Int?,
): String {
    if (httpStatus != null) return "HTTP_$httpStatus"
    return when {
        category == LiveFailureCategory.NoResponse &&
            stage == LiveFailureStage.Setup -> "LIVE_SETUP_NO_RESPONSE"
        category == LiveFailureCategory.NoResponse -> "LIVE_NO_RESPONSE"
        category == LiveFailureCategory.Network -> "LIVE_NETWORK_CONNECTION"
        category == LiveFailureCategory.ApiKey -> "LIVE_API_KEY_OR_PERMISSION"
        category == LiveFailureCategory.Quota -> "LIVE_API_QUOTA"
        category == LiveFailureCategory.Model -> "LIVE_MODEL_OR_ACCESS"
        category == LiveFailureCategory.ApiRequest -> "LIVE_INVALID_API_REQUEST"
        category == LiveFailureCategory.Server -> "LIVE_SERVER_ERROR"
        category == LiveFailureCategory.Device -> "LIVE_DEVICE_ERROR"
        category == LiveFailureCategory.Audio -> "LIVE_AUDIO_ERROR"
        category == LiveFailureCategory.Protocol -> "LIVE_PROTOCOL_ERROR"
        else -> "LIVE_UNKNOWN_ERROR"
    }
}

internal fun formatLiveFailureDiagnostic(
    category: String,
    stage: String,
    modelId: String,
    details: String,
    httpStatus: Int? = null,
    diagnosticCode: String = "LIVE_UNKNOWN_ERROR",
    diagnosisLabel: String = "تشخیص",
    stageLabel: String = "مرحله",
    modelLabel: String = "مدل",
    httpLabel: String = "HTTP",
    codeLabel: String = "کد خطا",
    detailsLabel: String = "جزئیات",
): String = buildString {
    append(codeLabel).append(": ").append(diagnosticCode)
    append(diagnosisLabel).append(": ").append(category)
    append("\n").append(stageLabel).append(": ").append(stage)
    append("\n").append(modelLabel).append(": ").append(modelId)
    httpStatus?.let { append("\n").append(httpLabel).append(": ").append(it) }
    if (details.isNotBlank()) append("\n").append(detailsLabel).append(": ").append(details)
}

internal fun contextualLiveFailure(
    context: Context,
    category: LiveFailureCategory,
    stage: LiveFailureStage,
    modelId: String?,
    details: String,
    httpStatus: Int? = null,
): String {
    val categoryText = context.getString(
        when (category) {
            LiveFailureCategory.Network -> R.string.live_error_category_network
            LiveFailureCategory.ApiKey -> R.string.live_error_category_api_key
            LiveFailureCategory.Quota -> R.string.live_error_category_quota
            LiveFailureCategory.Model -> R.string.live_error_category_model
            LiveFailureCategory.ApiRequest -> R.string.live_error_category_api_request
            LiveFailureCategory.Server -> R.string.live_error_category_server
            LiveFailureCategory.NoResponse -> R.string.live_error_category_no_response
            LiveFailureCategory.Device -> R.string.live_error_category_device
            LiveFailureCategory.Audio -> R.string.live_error_category_audio
            LiveFailureCategory.Protocol -> R.string.live_error_category_protocol
            LiveFailureCategory.Unknown -> R.string.live_error_category_unknown
        },
    )
    val stageText = context.getString(
        when (stage) {
            LiveFailureStage.Connection -> R.string.live_error_stage_connection
            LiveFailureStage.Setup -> R.string.live_error_stage_setup
            LiveFailureStage.Audio -> R.string.live_error_stage_audio
            LiveFailureStage.AudioStream -> R.string.live_error_stage_audio_stream
            LiveFailureStage.Session -> R.string.live_error_stage_session
            LiveFailureStage.Protocol -> R.string.live_error_stage_protocol
            LiveFailureStage.Unknown -> R.string.live_error_stage_unknown
        },
    )
    return formatLiveFailureDiagnostic(
        category = categoryText,
        stage = stageText,
        modelId = modelId ?: context.getString(R.string.live_error_model_unknown),
        details = details,
        httpStatus = httpStatus,
        diagnosticCode = liveFailureDiagnosticCode(category, stage, httpStatus),
        diagnosisLabel = context.getString(R.string.live_error_diagnosis_label),
        stageLabel = context.getString(R.string.live_error_stage_label),
        modelLabel = context.getString(R.string.live_error_model_label),
        httpLabel = context.getString(R.string.live_error_http_label),
        codeLabel = context.getString(R.string.live_error_code_label),
        detailsLabel = context.getString(R.string.live_error_details_label),
    )
}

private val NETWORK_ERROR_TYPES = setOf(
    "connectexception",
    "ioexception",
    "unknownhostexception",
    "noroutetohostexception",
    "sockettimeoutexception",
    "sslhandshakeexception",
)

internal fun buildGeminiLiveSetup(
    modelId: String,
    includeInputAudioTranscription: Boolean = false,
    includeOutputAudioTranscription: Boolean = false,
    systemInstruction: String? = null,
): JsonObject =
    JsonObject().apply {
        add("setup", JsonObject().apply {
            addProperty(
                "model",
                if (modelId.startsWith("models/")) modelId else "models/$modelId",
            )
            add("generationConfig", JsonObject().apply {
                add("responseModalities", JsonArray().apply { add("AUDIO") })
            })
            if (includeInputAudioTranscription) {
                add("inputAudioTranscription", JsonObject())
            }
            if (includeOutputAudioTranscription) {
                add("outputAudioTranscription", JsonObject())
            }
            systemInstruction?.takeIf(String::isNotBlank)?.let { instruction ->
                add("systemInstruction", JsonObject().apply {
                    add("parts", JsonArray().apply {
                        add(JsonObject().apply { addProperty("text", instruction) })
                    })
                })
            }
        })
    }

internal fun buildGeminiLiveClientContent(turns: List<GeminiTurn>): JsonObject =
    JsonObject().apply {
        add("clientContent", JsonObject().apply {
            add("turns", JsonArray().apply {
                turns.forEach { turn ->
                    add(JsonObject().apply {
                        addProperty("role", if (turn.isUser) "user" else "model")
                        add("parts", JsonArray().apply {
                            if (turn.text.isNotBlank()) {
                                add(JsonObject().apply { addProperty("text", turn.text) })
                            }
                        })
                    })
                }
            })
            addProperty("turnComplete", true)
        })
    }

internal suspend fun generateGeminiLiveTextReply(
    context: Context,
    apiKey: String,
    modelId: String,
    turns: List<GeminiTurn>,
    systemInstruction: String,
): String = withContext(Dispatchers.IO) {
    require(turns.none { turn -> turn.attachments.isNotEmpty() }) {
        context.getString(R.string.live_text_attachments_unsupported)
    }
    suspendCancellableCoroutine { continuation ->
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        val handler = Handler(Looper.getMainLooper())
        val completed = AtomicBoolean(false)
        val responseText = StringBuilder()
        var setupComplete = false
        var webSocket: WebSocket? = null
        var timeout: Runnable? = null

        fun finish(result: Result<String>) {
            if (!completed.compareAndSet(false, true)) return
            timeout?.let(handler::removeCallbacks)
            webSocket?.close(1000, "Text turn complete")
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            if (continuation.isActive) continuation.resumeWith(result)
        }

        fun scheduleTimeout(message: String) {
            timeout?.let(handler::removeCallbacks)
            timeout = Runnable { finish(Result.failure(IOException(message))) }
            handler.postDelayed(
                timeout!!,
                if (setupComplete) {
                    LIVE_CHAT_RESPONSE_TIMEOUT_MILLIS
                } else {
                    LIVE_CHAT_SETUP_TIMEOUT_MILLIS
                },
            )
        }

        continuation.invokeOnCancellation {
            completed.set(true)
            timeout?.let(handler::removeCallbacks)
            webSocket?.cancel()
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        }

        val request = Request.Builder()
            .url("$LIVE_ENDPOINT?key=${URLEncoder.encode(apiKey, Charsets.UTF_8.name())}")
            .build()
        webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(socket: WebSocket, response: Response) {
                    webSocket = socket
                    val setup = buildGeminiLiveSetup(
                        modelId = modelId,
                        includeOutputAudioTranscription = true,
                        systemInstruction = systemInstruction,
                    )
                    if (!socket.send(setup.toString())) {
                        finish(Result.failure(IOException("ارسال setup به Gemini Live انجام نشد.")))
                        return
                    }
                    scheduleTimeout("Gemini Live در زمان تعیین‌شده setup را تأیید نکرد.")
                }

                override fun onMessage(socket: WebSocket, text: String) {
                    val message = try {
                        JsonParser.parseString(text).takeIf { it.isJsonObject }?.asJsonObject
                            ?: throw JsonParseException("Expected a JSON object.")
                    } catch (error: JsonParseException) {
                        finish(Result.failure(IOException("پاسخ Gemini Live قابل پردازش نبود.")))
                        return
                    }
                    val serverError = message.get("setupError")
                        ?.takeUnless { it.isJsonNull }
                        ?: message.get("error")?.takeUnless { it.isJsonNull }
                    if (serverError != null) {
                        finish(
                            Result.failure(
                                IOException(
                                    "Gemini Live درخواست چت را نپذیرفت: " +
                                        sanitizeLiveDiagnostic(serverError.toString(), apiKey),
                                ),
                            ),
                        )
                        return
                    }
                    if (!setupComplete && message.get("setupComplete")?.isJsonObject == true) {
                        setupComplete = true
                        val content = buildGeminiLiveClientContent(turns)
                        if (!socket.send(content.toString())) {
                            finish(Result.failure(IOException("ارسال پیام چت به Gemini Live انجام نشد.")))
                            return
                        }
                        scheduleTimeout("Gemini Live در زمان تعیین‌شده پاسخ متنی نداد.")
                        return
                    }
                    if (!setupComplete) return

                    val serverContent = message.get("serverContent")
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject ?: return
                    serverContent.get("outputTranscription")
                        ?.takeIf { it.isJsonObject }
                        ?.asJsonObject
                        ?.get("text")
                        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                        ?.asString
                        ?.let(responseText::append)
                    if (serverContent.get("turnComplete")?.asBoolean == true) {
                        val answer = responseText.toString().trim()
                        if (answer.isBlank()) {
                            finish(Result.failure(IOException("Gemini Live پاسخ متنی برنگرداند.")))
                        } else {
                            finish(Result.success(answer))
                        }
                    }
                }

                override fun onFailure(socket: WebSocket, error: Throwable, response: Response?) {
                    val responseDetail = response?.let {
                        " HTTP ${it.code} ${sanitizeLiveDiagnostic(it.message, apiKey)}"
                    }.orEmpty()
                    finish(
                        Result.failure(
                            IOException(
                                "اتصال چت به Gemini Live ناموفق بود:$responseDetail " +
                                    error.javaClass.simpleName,
                            ),
                        ),
                    )
                }

                override fun onClosed(socket: WebSocket, code: Int, reason: String) {
                    if (!completed.get()) {
                        finish(
                            Result.failure(
                                IOException(
                                    "Gemini Live اتصال چت را بست: code=$code " +
                                        sanitizeLiveDiagnostic(reason, apiKey),
                                ),
                            ),
                        )
                    }
                }
            },
        )
    }
}

internal fun sanitizeLiveDiagnostic(text: String, apiKey: String): String {
    var sanitized = text
    if (apiKey.isNotBlank()) {
        sanitized = sanitized.replace(apiKey, "[REDACTED]")
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        if (encodedKey != apiKey) sanitized = sanitized.replace(encodedKey, "[REDACTED]")
    }
    sanitized = sanitized
        .replace(Regex("(?i)([?&]key=)[^&\\s\"']+"), "$1[REDACTED]")
        .replace(Regex("AIza[0-9A-Za-z_-]{20,}"), "[REDACTED_API_KEY]")
        .replace(Regex("\\s+"), " ")
        .trim()
    return sanitized.take(MAX_DIAGNOSTIC_LENGTH)
}

enum class GeminiLiveState {
    Connecting,
    Configuring,
    Ready,
    Listening,
    Responding,
    Error,
    Closed,
}

class GeminiLiveSession(
    context: Context,
    private val apiKey: String,
    availableModels: List<GeminiModel>,
    private val preferences: UserPreferences,
    private val onState: (GeminiLiveState, String?) -> Unit,
    private val onTranscript: (isUser: Boolean, text: String) -> Unit,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val liveModel = availableModels.firstOrNull { it.supportsLive }
    private val hasStarted = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)
    private val hasFailed = AtomicBoolean(false)
    private val isRecording = AtomicBoolean(false)
    private val inputMuted = AtomicBoolean(false)
    private val inputTranscript = StringBuilder()
    private val outputTranscript = StringBuilder()
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var audioTrack: AudioTrack? = null
    @Volatile private var previousAudioMode: Int? = null
    @Volatile private var previousSpeakerState: Boolean? = null
    @Volatile private var previousCommunicationDevice: android.media.AudioDeviceInfo? = null
    @Volatile private var currentModelIndex = 0
    @Volatile private var stageTimeout: Runnable? = null
    @Volatile private var setupConfirmed = false
    @Volatile private var setupSent = false
    @Volatile private var setupResponseCount = 0
    @Volatile private var lastSetupResponseFields = ""

    fun start() {
        if (!hasStarted.compareAndSet(false, true)) return
        if (liveModel == null) {
            report(GeminiLiveState.Error, "برای این کلید، مدل Gemini Live در دسترس نیست.")
            return
        }
        connect()
    }

    fun setMuted(muted: Boolean) {
        inputMuted.set(muted)
    }

    fun setSpeakerOutput(enabled: Boolean): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = if (enabled) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val target = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == targetType }
            if (target == null || !audioManager.setCommunicationDevice(target)) {
                fail(
                    "تغییر مسیر خروجی صدا انجام نشد.",
                    stage = LiveFailureStage.Audio,
                )
                return false
            }
        } else {
            setLegacySpeakerOutput(enabled)
        }
        return true
    }

    fun sendVideoFrame(jpeg: ByteArray): Boolean {
        if (isClosed.get() || !isRecording.get() ||
            jpeg.isEmpty() || jpeg.size > MAX_VIDEO_FRAME_BYTES
        ) {
            return false
        }
        val frame = JsonObject().apply {
            add("realtimeInput", JsonObject().apply {
                add("video", JsonObject().apply {
                    addProperty("mimeType", "image/jpeg")
                    addProperty("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                })
            })
        }
        return webSocket?.send(frame.toString()) == true
    }

    fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        clearStageTimeout()
        stopAudio()
        webSocket?.cancel()
        webSocket = null
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        restoreAudioRouting()
    }

    private fun restoreAudioRouting() {
        previousAudioMode?.let { audioManager.mode = it }
        previousAudioMode = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val previousDevice = previousCommunicationDevice
            if (previousDevice == null) audioManager.clearCommunicationDevice()
            else audioManager.setCommunicationDevice(previousDevice)
            previousCommunicationDevice = null
        } else {
            previousSpeakerState?.let(::setLegacySpeakerOutput)
            previousSpeakerState = null
        }
    }

    private fun connect() {
        if (isClosed.get()) return
        val model = liveModel ?: return
        setupConfirmed = false
        setupSent = false
        setupResponseCount = 0
        lastSetupResponseFields = ""
        report(GeminiLiveState.Connecting, null)
        scheduleStageTimeout(
            modelIndex = currentModelIndex,
            timeoutMillis = CONNECT_TIMEOUT_MILLIS,
            stage = LiveFailureStage.Connection,
            message = { appContext.getString(R.string.live_connection_timeout, model.id) },
        )
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$LIVE_ENDPOINT?key=$encodedKey")
            .build()
        webSocket = client.newWebSocket(
            request,
            LiveSocketListener(model, currentModelIndex),
        )
    }

    private inner class LiveSocketListener(
        private val model: GeminiModel,
        private val modelIndex: Int,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (modelIndex != currentModelIndex || isClosed.get()) return
            clearStageTimeout()
            report(GeminiLiveState.Configuring, null)
            if (setupSent) return
            setupSent = true
            if (!webSocket.send(
                    buildGeminiLiveSetup(
                        modelId = model.id,
                        includeInputAudioTranscription = true,
                        includeOutputAudioTranscription = true,
                    ).toString(),
                )
            ) {
                fail(
                    "ارسال پیکربندی Gemini Live انجام نشد.",
                    stage = LiveFailureStage.Protocol,
                )
                return
            }
            scheduleStageTimeout(
                modelIndex = modelIndex,
                timeoutMillis = SETUP_TIMEOUT_MILLIS,
                stage = LiveFailureStage.Setup,
                message = {
                    if (setupResponseCount == 0) {
                        appContext.getString(
                            R.string.live_setup_no_response,
                            model.id,
                            webSocket?.queueSize() ?: 0L,
                        )
                    } else {
                        appContext.getString(
                            R.string.live_setup_unconfirmed_response,
                            model.id,
                            setupResponseCount,
                            lastSetupResponseFields,
                        )
                    }
                },
            )
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (modelIndex != currentModelIndex || isClosed.get()) return
            val parsed = try {
                JsonParser.parseString(text)
            } catch (error: JsonParseException) {
                fail(
                    "پاسخ JSON از Gemini Live قابل پردازش نبود (${error.javaClass.simpleName}).",
                    stage = LiveFailureStage.Protocol,
                )
                return
            }
            if (!parsed.isJsonObject) {
                fail(
                    "پاسخ Gemini Live از نوع JSON object نبود.",
                    stage = LiveFailureStage.Protocol,
                )
                return
            }
            val message = parsed.asJsonObject

            val serverError = message.get("setupError")
                ?.takeUnless { it.isJsonNull }
                ?: message.get("error")?.takeUnless { it.isJsonNull }
            if (serverError != null) {
                val details = sanitizeLiveDiagnostic(serverError.toString(), apiKey)
                val serverErrorObject = serverError.takeIf { it.isJsonObject }?.asJsonObject
                val serverStatus = serverErrorObject
                    ?.get("status")
                    ?.takeUnless { it.isJsonNull }
                    ?.asString
                val serverCode = serverErrorObject
                    ?.get("code")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
                    ?.asInt
                    ?.takeIf { it in 400..599 }
                fail(
                    "Gemini Live setup رد شد: $details",
                    stage = LiveFailureStage.Setup,
                    httpStatus = serverCode,
                    serverStatus = serverStatus,
                )
                return
            }

            if (!setupConfirmed && message.get("setupComplete")?.isJsonObject == true) {
                setupConfirmed = true
                lastSetupResponseFields = ""
                clearStageTimeout()
                report(GeminiLiveState.Ready, null)
                if (!startAudio()) return
            }
            if (!setupConfirmed) {
                setupResponseCount += 1
                lastSetupResponseFields = message.keySet()
                    .take(8)
                    .joinToString()
                    .take(MAX_DIAGNOSTIC_LENGTH)
                return
            }
            message.get("serverContent")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.let(::handleServerContent)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isClosed.get() || hasFailed.get() || modelIndex != currentModelIndex) return
            clearStageTimeout()
            stopAudio()
            fail(
                formatHandshakeFailure(model, t, response),
                stage = if (setupConfirmed) {
                    LiveFailureStage.Session
                } else {
                    LiveFailureStage.Connection
                },
                httpStatus = response?.code,
                errorType = t.javaClass.simpleName,
            )
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isClosed.get() && !hasFailed.get() && modelIndex == currentModelIndex) {
                clearStageTimeout()
                stopAudio()
                val safeReason = sanitizeLiveDiagnostic(reason, apiKey)
                val closeDetail = "Gemini Live بستن اتصال را اعلام کرد: code=$code" +
                    if (safeReason.isBlank()) "" else "، reason=$safeReason"
                fail(
                    closeDetail,
                    stage = if (setupConfirmed) {
                        LiveFailureStage.Session
                    } else {
                        LiveFailureStage.Setup
                    },
                )
            }
        }
    }

    private fun scheduleStageTimeout(
        modelIndex: Int,
        timeoutMillis: Long,
        stage: LiveFailureStage,
        message: () -> String,
    ) {
        clearStageTimeout()
        val timeout = Runnable {
            if (!isClosed.get() && !hasFailed.get() &&
                currentModelIndex == modelIndex && !isRecording.get()
            ) {
                fail(message(), stage = stage)
            }
        }
        stageTimeout = timeout
        mainHandler.postDelayed(timeout, timeoutMillis)
    }

    private fun formatHandshakeFailure(
        model: GeminiModel,
        error: Throwable,
        response: Response?,
    ): String = buildString {
        append("اتصال Gemini Live به مدل ")
        append(model.id)
        append(" ناموفق بود.")
        if (response != null) {
            append(" HTTP ")
            append(response.code)
            if (response.message.isNotBlank()) {
                append(" ")
                append(sanitizeLiveDiagnostic(response.message, apiKey))
            }
            val body = try {
                response.peekBody(MAX_ERROR_BODY_BYTES).string()
            } catch (readError: IOException) {
                "خواندن بدنهٔ خطای HTTP ناموفق بود (${readError.javaClass.simpleName})."
            }
            if (body.isNotBlank()) {
                append("\nپاسخ HTTP: ")
                append(sanitizeLiveDiagnostic(body, apiKey))
            }
        }
        append("\nعلت: ")
        append(error.javaClass.simpleName)
        error.message?.takeIf(String::isNotBlank)?.let {
            append(": ")
            append(sanitizeLiveDiagnostic(it, apiKey))
        }
    }

    private fun clearStageTimeout() {
        stageTimeout?.let(mainHandler::removeCallbacks)
        stageTimeout = null
    }

    private fun handleServerContent(serverContent: JsonObject?) {
        if (serverContent == null) return
        val parts = serverContent.get("modelTurn")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.get("parts")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
        parts?.forEach { part ->
            val inlineData = part.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.get("inlineData")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
            val encodedAudio = inlineData?.get("data")
                ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString
            if (!encodedAudio.isNullOrBlank()) {
                val pcm = Base64.decode(encodedAudio, Base64.DEFAULT)
                audioTrack?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                report(GeminiLiveState.Responding, null)
            }
        }
        serverContent.getAsJsonObject("inputTranscription")
            ?.get("text")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.let(inputTranscript::append)
        serverContent.getAsJsonObject("outputTranscription")
            ?.get("text")
            ?.takeUnless { it.isJsonNull }
            ?.asString
            ?.let(outputTranscript::append)

        if (serverContent.get("turnComplete")?.asBoolean == true) {
            inputTranscript.toString().trim().takeIf { it.isNotEmpty() }
                ?.let { reportTranscript(isUser = true, it) }
            outputTranscript.toString().trim().takeIf { it.isNotEmpty() }
                ?.let { reportTranscript(isUser = false, it) }
            inputTranscript.clear()
            outputTranscript.clear()
            report(GeminiLiveState.Listening, null)
        }
        if (serverContent.get("interrupted")?.asBoolean == true) {
            outputTranscript.clear()
            report(GeminiLiveState.Listening, null)
        }
    }

    private fun startAudio(): Boolean {
        if (isClosed.get()) return false
        if (!isRecording.compareAndSet(false, true)) return true
        try {
            previousAudioMode = audioManager.mode
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                previousCommunicationDevice = audioManager.communicationDevice
            } else {
                previousSpeakerState = isLegacySpeakerOutputEnabled()
            }
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            check(setSpeakerOutput(true)) { "تغییر مسیر خروجی صدا انجام نشد." }

            val inputMin = AudioRecord.getMinBufferSize(
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(inputMin > 0) { "میکروفون دستگاه در دسترس نیست." }
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                INPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(inputMin, INPUT_BUFFER_BYTES),
            )
            check(recorder.state == AudioRecord.STATE_INITIALIZED) {
                "راه‌اندازی میکروفون دستگاه ناموفق بود."
            }
            audioRecord = recorder

            val outputMin = AudioTrack.getMinBufferSize(
                OUTPUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            check(outputMin > 0) { "خروجی صوتی دستگاه در دسترس نیست." }
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(OUTPUT_SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(outputMin, OUTPUT_BUFFER_BYTES))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.playbackParams = PlaybackParams()
                .setSpeed(0.75f + preferences.audioSpeed * 0.5f)
                .setPitch(0.75f + preferences.audioPitch * 0.5f)

            recorder.startRecording()
            audioTrack?.play()
            report(GeminiLiveState.Listening, null)
            thread(name = "gemini-live-audio-input") {
                val samples = ShortArray(INPUT_CHUNK_SAMPLES)
                while (isRecording.get() && !isClosed.get()) {
                    val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    if (count > 0 && !inputMuted.get()) {
                        val bytes = ByteArray(count * Short.SIZE_BYTES)
                        val byteBuffer = java.nio.ByteBuffer.wrap(bytes)
                            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
                        repeat(count) { byteBuffer.putShort(samples[it]) }
                        val audioInput = JsonObject().apply {
                            add("realtimeInput", JsonObject().apply {
                                add("audio", JsonObject().apply {
                                    addProperty(
                                        "data",
                                        Base64.encodeToString(bytes, Base64.NO_WRAP),
                                    )
                                    addProperty("mimeType", "audio/pcm;rate=$INPUT_SAMPLE_RATE")
                                })
                            })
                        }
                        if (webSocket?.send(audioInput.toString()) != true) {
                            fail(
                                "ارسال صدای میکروفون به Gemini Live ناموفق بود.",
                                stage = LiveFailureStage.AudioStream,
                            )
                            break
                        }
                    } else if (count < 0 && isRecording.get()) {
                        fail(
                            "خواندن صدای میکروفون ناموفق بود (کد AudioRecord=$count).",
                            stage = LiveFailureStage.Audio,
                        )
                        break
                    }
                }
            }
            return true
        } catch (error: SecurityException) {
            stopAudio()
            fail(
                "اجازهٔ دسترسی به میکروفون داده نشد.",
                stage = LiveFailureStage.Audio,
            )
        } catch (error: Exception) {
            stopAudio()
            fail(
                "${error.javaClass.simpleName}: ${error.message ?: "راه‌اندازی صدای زنده ناموفق بود."}",
                stage = LiveFailureStage.Audio,
            )
        }
        return false
    }

    private fun stopAudio() {
        isRecording.set(false)
        audioRecord?.let { recorder ->
            try {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            } catch (_: IllegalStateException) {
                report(GeminiLiveState.Error, "بستن میکروفون با خطا روبه‌رو شد.")
            } finally {
                recorder.release()
                audioRecord = null
            }
        }
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            } catch (_: IllegalStateException) {
                report(GeminiLiveState.Error, "بستن خروجی صوتی با خطا روبه‌رو شد.")
            } finally {
                track.release()
                audioTrack = null
            }
        }
    }

    private fun fail(
        message: String,
        stage: LiveFailureStage = LiveFailureStage.Unknown,
        httpStatus: Int? = null,
        errorType: String? = null,
        serverStatus: String? = null,
    ) {
        if (!hasFailed.compareAndSet(false, true)) return
        clearStageTimeout()
        stopAudio()
        restoreAudioRouting()
        webSocket?.cancel()
        val category = classifyLiveFailure(httpStatus, stage, errorType, serverStatus)
        report(
            GeminiLiveState.Error,
            contextualLiveFailure(
                context = appContext,
                category = category,
                stage = stage,
                modelId = liveModel?.id,
                details = sanitizeLiveDiagnostic(message, apiKey),
                httpStatus = httpStatus,
            ),
        )
    }

    private fun report(state: GeminiLiveState, detail: String?) {
        mainHandler.post { onState(state, detail) }
    }

    private fun reportTranscript(isUser: Boolean, text: String) {
        mainHandler.post { onTranscript(isUser, text) }
    }

    @Suppress("DEPRECATION")
    private fun setLegacySpeakerOutput(enabled: Boolean) {
        audioManager.isSpeakerphoneOn = enabled
    }

    @Suppress("DEPRECATION")
    private fun isLegacySpeakerOutputEnabled(): Boolean = audioManager.isSpeakerphoneOn

    companion object {
        private const val CONNECT_TIMEOUT_MILLIS = 25_000L
        private const val SETUP_TIMEOUT_MILLIS = 15_000L
        private const val MAX_ERROR_BODY_BYTES = 8_192L
        private const val INPUT_SAMPLE_RATE = 16_000
        private const val OUTPUT_SAMPLE_RATE = 24_000
        private const val INPUT_CHUNK_SAMPLES = 2_048
        private const val INPUT_BUFFER_BYTES = 8_192
        private const val OUTPUT_BUFFER_BYTES = 16_384
        private const val MAX_VIDEO_FRAME_BYTES = 4 * 1024 * 1024

        fun liveFallbackOrder(models: List<GeminiModel>): List<GeminiModel> {
            val eligible = models.filter { it.supportsLive }
            return buildList {
                eligible.firstOrNull { it.id == "gemini-3.8-live" }?.let(::add)
                eligible.filterNot { it.id == "gemini-3.8-live" }
                    .sortedBy { it.displayName.lowercase() }
                    .forEach(::add)
            }
        }
    }
}
