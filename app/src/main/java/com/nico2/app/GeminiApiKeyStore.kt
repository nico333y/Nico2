package com.nico2.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class GeminiApiKeyStore(context: Context) {
    private val file = File(context.filesDir, ENCRYPTED_KEY_FILE)
    private val atomicFile = AtomicFile(file)

    suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext null
        try {
            val stored = atomicFile.openRead().use { it.readBytes() }
            require(stored.size > IV_SIZE) { "Encrypted key file is invalid." }
            val iv = stored.copyOfRange(0, IV_SIZE)
            val ciphertext = stored.copyOfRange(IV_SIZE, stored.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_SIZE_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            throw GeminiApiKeyStoreException("کلید ذخیره‌شده قابل خواندن نیست؛ آن را دوباره وارد کنید.", error)
        }
    }

    suspend fun write(apiKey: String) = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "API key must not be empty." }
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.iv + cipher.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
            val output = atomicFile.startWrite()
            try {
                output.write(encrypted)
                atomicFile.finishWrite(output)
            } catch (error: Exception) {
                atomicFile.failWrite(output)
                throw error
            }
        } catch (error: Exception) {
            throw GeminiApiKeyStoreException("ذخیرهٔ امن کلید API انجام نشد.", error)
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        try {
            atomicFile.delete()
            keyStore().deleteEntry(KEY_ALIAS)
        } catch (error: Exception) {
            throw GeminiApiKeyStoreException("حذف کلید ذخیره‌شده انجام نشد.", error)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val store = keyStore()
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nico2_gemini_api_key"
        private const val ENCRYPTED_KEY_FILE = "gemini-api-key.enc"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
        private const val TAG_SIZE_BITS = 128
    }
}

class GeminiApiKeyStoreException(message: String, cause: Throwable? = null) : Exception(message, cause)
