package com.nico2.app

import android.content.Context

data class UserPreferences(
    val appLanguage: String = LANGUAGE_PERSIAN,
    val selectedModel: String = "",
    val reduceAnimations: Boolean = false,
    val tone: String = "friendly",
    val role: String = "helper",
    val answerLength: String = "medium",
    val useEmoji: Boolean = false,
    val creativity: Float = 0.45f,
    val customInstruction: String = "",
    val audioLanguage: String = "persian",
    val audioVoice: String = "voice_one",
    val audioSpeed: Float = 0.5f,
    val audioPitch: Float = 0.5f,
) {
    fun write(context: Context) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_APP_LANGUAGE, appLanguage)
            .putString(KEY_SELECTED_MODEL, selectedModel)
            .putBoolean(KEY_REDUCE_ANIMATIONS, reduceAnimations)
            .putString(KEY_TONE, tone)
            .putString(KEY_ROLE, role)
            .putString(KEY_ANSWER_LENGTH, answerLength)
            .putBoolean(KEY_USE_EMOJI, useEmoji)
            .putFloat(KEY_CREATIVITY, creativity.coerceIn(0f, 1f))
            .putString(KEY_CUSTOM_INSTRUCTION, customInstruction.take(MAX_INSTRUCTION_LENGTH))
            .putString(KEY_AUDIO_LANGUAGE, audioLanguage)
            .putString(KEY_AUDIO_VOICE, audioVoice)
            .putFloat(KEY_AUDIO_SPEED, audioSpeed.coerceIn(0f, 1f))
            .putFloat(KEY_AUDIO_PITCH, audioPitch.coerceIn(0f, 1f))
            .apply()
    }

    companion object {
        const val LANGUAGE_PERSIAN = "fa"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_TURKISH = "tr"

        private const val FILE_NAME = "user-settings"
        private const val MAX_INSTRUCTION_LENGTH = 2_000
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_REDUCE_ANIMATIONS = "reduce_animations"
        private const val KEY_TONE = "personality_tone"
        private const val KEY_ROLE = "personality_role"
        private const val KEY_ANSWER_LENGTH = "personality_answer_length"
        private const val KEY_USE_EMOJI = "personality_use_emoji"
        private const val KEY_CREATIVITY = "personality_creativity"
        private const val KEY_CUSTOM_INSTRUCTION = "personality_custom_instruction"
        private const val KEY_AUDIO_LANGUAGE = "audio_language"
        private const val KEY_AUDIO_VOICE = "audio_voice"
        private const val KEY_AUDIO_SPEED = "audio_speed"
        private const val KEY_AUDIO_PITCH = "audio_pitch"

        fun read(context: Context): UserPreferences {
            val saved = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            return UserPreferences(
                appLanguage = saved.getString(KEY_APP_LANGUAGE, LANGUAGE_PERSIAN)
                    ?: LANGUAGE_PERSIAN,
                selectedModel = saved.getString(KEY_SELECTED_MODEL, "").orEmpty(),
                reduceAnimations = saved.getBoolean(KEY_REDUCE_ANIMATIONS, false),
                tone = saved.getString(KEY_TONE, "friendly") ?: "friendly",
                role = saved.getString(KEY_ROLE, "helper") ?: "helper",
                answerLength = saved.getString(KEY_ANSWER_LENGTH, "medium") ?: "medium",
                useEmoji = saved.getBoolean(KEY_USE_EMOJI, false),
                creativity = saved.getFloat(KEY_CREATIVITY, 0.45f).coerceIn(0f, 1f),
                customInstruction = saved.getString(KEY_CUSTOM_INSTRUCTION, "").orEmpty(),
                audioLanguage = saved.getString(KEY_AUDIO_LANGUAGE, "persian") ?: "persian",
                audioVoice = saved.getString(KEY_AUDIO_VOICE, "voice_one") ?: "voice_one",
                audioSpeed = saved.getFloat(KEY_AUDIO_SPEED, 0.5f).coerceIn(0f, 1f),
                audioPitch = saved.getFloat(KEY_AUDIO_PITCH, 0.5f).coerceIn(0f, 1f),
            )
        }
    }
}
