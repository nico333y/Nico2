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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

enum class GeminiLiveState {
    Connecting,
    Active,
    Failed,
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
    private val liveModels = liveFallbackOrder(availableModels)
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
    @Volatile private var currentModelIndex = -1

    fun start() {
        if (liveModels.isEmpty()) {
            report(GeminiLiveState.Failed, "برای این کلید، مدل Gemini Live در دسترس نیست.")
            return
        }
        connectToNextModel()
    }

    fun setMuted(muted: Boolean) {
        inputMuted.set(muted)
    }

    fun setSpeakerOutput(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val targetType = if (enabled) {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            } else {
                android.media.AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            val target = audioManager.availableCommunicationDevices
                .firstOrNull { it.type == targetType }
            if (target == null || !audioManager.setCommunicationDevice(target)) {
                report(GeminiLiveState.Failed, "تغییر مسیر خروجی صدا انجام نشد.")
            }
        } else {
            setLegacySpeakerOutput(enabled)
        }
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
        stopAudio()
        webSocket?.close(1000, "User ended session")
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

    private fun connectToNextModel() {
        if (isClosed.get()) return
        val nextIndex = currentModelIndex + 1
        if (nextIndex >= liveModels.size) {
            fail("سهمیه یا اتصال مدل‌های Live در دسترس نیست.")
            return
        }
        currentModelIndex = nextIndex
        report(GeminiLiveState.Connecting, null)
        val model = liveModels[nextIndex]
        val encodedKey = URLEncoder.encode(apiKey, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url("$LIVE_ENDPOINT?key=$encodedKey")
            .build()
        webSocket = client.newWebSocket(
            request,
            LiveSocketListener(model, nextIndex),
        )
    }

    private inner class LiveSocketListener(
        private val model: GeminiModel,
        private val modelIndex: Int,
    ) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val setup = JsonObject().apply {
                add("setup", JsonObject().apply {
                    addProperty("model", "models/${model.id}")
                    add("generationConfig", JsonObject().apply {
                        add("responseModalities", com.google.gson.JsonArray().apply {
                            add("AUDIO")
                        })
                        add("speechConfig", JsonObject().apply {
                            add("voiceConfig", JsonObject().apply {
                                add("prebuiltVoiceConfig", JsonObject().apply {
                                    addProperty(
                                        "voiceName",
                                        if (preferences.audioVoice == "voice_two") "Puck" else "Kore",
                                    )
                                })
                            })
                        })
                    })
                    add("inputAudioTranscription", JsonObject().apply {
                        add("languageCodes", com.google.gson.JsonArray().apply {
                            add(
                                when (preferences.audioLanguage) {
                                    "turkish" -> "tr-TR"
                                    "english" -> "en-US"
                                    else -> "fa-IR"
                                },
                            )
                        })
                    })
                    add("outputAudioTranscription", JsonObject())
                })
            }
            if (!webSocket.send(setup.toString())) {
                fail("ارسال پیکربندی Gemini Live انجام نشد.")
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            try {
                val message = JsonParser.parseString(text).asJsonObject
                if (message.has("error")) {
                    val error = message.getAsJsonObject("error")
                    val code = error.get("code")?.asInt
                    val status = error.get("status")?.asString
                    if ((code == HTTP_TOO_MANY_REQUESTS || status == "RESOURCE_EXHAUSTED") &&
                        modelIndex == currentModelIndex
                    ) {
                        webSocket.close(1000, "Quota exhausted")
                        connectToNextModel()
                    } else {
                        fail("Gemini Live درخواست را نپذیرفت (کد ${code ?: "نامشخص"}).")
                    }
                    return
                }
                if (message.has("setupComplete")) {
                    if (!startAudio()) return
                    report(GeminiLiveState.Active, model.displayName)
                }
                handleServerContent(message.getAsJsonObject("serverContent"))
            } catch (error: Exception) {
                fail("پاسخ Live قابل پردازش نبود.")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (isClosed.get() || hasFailed.get() || modelIndex != currentModelIndex) return
            if (response?.code == HTTP_TOO_MANY_REQUESTS && modelIndex == currentModelIndex) {
                connectToNextModel()
            } else {
                stopAudio()
                fail(
                    if (response?.code == HTTP_UNAUTHORIZED || response?.code == HTTP_FORBIDDEN) {
                        "کلید API رد شد؛ کلید ذخیره‌شده را بررسی کنید."
                    } else {
                        "اتصال Gemini Live برقرار نشد."
                    },
                )
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!isClosed.get() && !hasFailed.get() && modelIndex == currentModelIndex) {
                stopAudio()
                report(GeminiLiveState.Closed, null)
            }
        }
    }

    private fun handleServerContent(serverContent: JsonObject?) {
        if (serverContent == null) return
        serverContent.getAsJsonObject("modelTurn")
            ?.getAsJsonArray("parts")
            ?.forEach { part ->
                val inlineData = part.asJsonObject.getAsJsonObject("inlineData")
                val encodedAudio = inlineData?.get("data")?.asString
                if (!encodedAudio.isNullOrBlank()) {
                    val pcm = Base64.decode(encodedAudio, Base64.DEFAULT)
                    audioTrack?.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
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
        }
        if (serverContent.get("interrupted")?.asBoolean == true) {
            outputTranscript.clear()
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
            setSpeakerOutput(true)

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
                report(GeminiLiveState.Failed, "بستن میکروفون با خطا روبه‌رو شد.")
            } finally {
                recorder.release()
                audioRecord = null
            }
        }
        audioTrack?.let { track ->
            try {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
            } catch (_: IllegalStateException) {
                report(GeminiLiveState.Failed, "بستن خروجی صوتی با خطا روبه‌رو شد.")
            } finally {
                track.release()
                audioTrack = null
            }
        }
    }

    private fun fail(message: String) {
        if (!hasFailed.compareAndSet(false, true)) return
        stopAudio()
        restoreAudioRouting()
        webSocket?.cancel()
        report(GeminiLiveState.Failed, message)
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
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
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
