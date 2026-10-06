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
import com.google.gson.JsonParseException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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

private const val MAX_DIAGNOSTIC_LENGTH = 320

internal fun buildGeminiLiveSetup(modelId: String): JsonObject =
    JsonObject().apply {
        add("setup", JsonObject().apply {
            addProperty(
                "model",
                if (modelId.startsWith("models/")) modelId else "models/$modelId",
            )
            add("generationConfig", JsonObject().apply {
                add("responseModalities", com.google.gson.JsonArray().apply { add("AUDIO") })
            })
        })
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
                report(GeminiLiveState.Error, "تغییر مسیر خروجی صدا انجام نشد.")
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
            if (!webSocket.send(buildGeminiLiveSetup(model.id).toString())) {
                fail("ارسال پیکربندی Gemini Live انجام نشد.")
                return
            }
            scheduleStageTimeout(
                modelIndex = modelIndex,
                timeoutMillis = SETUP_TIMEOUT_MILLIS,
                message = {
                    if (setupResponseCount == 0) {
                        appContext.getString(R.string.live_setup_no_response, model.id)
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
                fail("پاسخ JSON از Gemini Live قابل پردازش نبود (${error.javaClass.simpleName}).")
                return
            }
            if (!parsed.isJsonObject) {
                fail("پاسخ Gemini Live از نوع JSON object نبود.")
                return
            }
            val message = parsed.asJsonObject

            val serverError = message.get("setupError")
                ?.takeUnless { it.isJsonNull }
                ?: message.get("error")?.takeUnless { it.isJsonNull }
            if (serverError != null) {
                val details = sanitizeLiveDiagnostic(serverError.toString(), apiKey)
                fail("Gemini Live setup رد شد: $details")
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
            fail(formatHandshakeFailure(model, t, response))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isClosed.get() && !hasFailed.get() && modelIndex == currentModelIndex) {
                clearStageTimeout()
                stopAudio()
                val safeReason = sanitizeLiveDiagnostic(reason, apiKey)
                val closeDetail = "Gemini Live بستن اتصال را اعلام کرد: code=$code" +
                    if (safeReason.isBlank()) "" else "، reason=$safeReason"
                fail(closeDetail)
            }
        }
    }

    private fun scheduleStageTimeout(
        modelIndex: Int,
        timeoutMillis: Long,
        message: () -> String,
    ) {
        clearStageTimeout()
        val timeout = Runnable {
            if (!isClosed.get() && !hasFailed.get() &&
                currentModelIndex == modelIndex && !isRecording.get()
            ) {
                fail(message())
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
                            fail("ارسال صدای میکروفون به Gemini Live ناموفق بود.")
                            break
                        }
                    } else if (count < 0 && isRecording.get()) {
                        fail("خواندن صدای میکروفون ناموفق بود.")
                        break
                    }
                }
            }
            return true
        } catch (error: SecurityException) {
            stopAudio()
            fail("اجازهٔ دسترسی به میکروفون داده نشد.")
        } catch (error: Exception) {
            stopAudio()
            fail(error.message ?: "راه‌اندازی صدای زنده ناموفق بود.")
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

    private fun fail(message: String) {
        if (!hasFailed.compareAndSet(false, true)) return
        clearStageTimeout()
        stopAudio()
        restoreAudioRouting()
        webSocket?.cancel()
        report(GeminiLiveState.Error, message)
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
        private const val LIVE_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
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
