package com.nico2.app

import android.content.ContentResolver
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class AttachmentStorage(private val filesDir: File) {
    private val attachmentDir = File(filesDir, ATTACHMENT_DIRECTORY)

    suspend fun importUri(
        resolver: ContentResolver,
        uri: Uri,
        suggestedName: String?,
        mimeType: String?,
    ): Pair<LocalAttachment, ByteArray> = withContext(Dispatchers.IO) {
        val safeMimeType = normalizedMimeType(mimeType)
        val bytes = resolver.openInputStream(uri)?.use { input ->
            input.readBounded(LocalRepository.MAX_ATTACHMENT_BYTES)
        } ?: throw LocalStorageException("فایل انتخاب‌شده باز نشد.")
        if (bytes.isEmpty()) throw LocalStorageException("فایل انتخاب‌شده خالی است.")
        val name = suggestedName
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.take(LocalRepository.MAX_ATTACHMENT_NAME_LENGTH)
            ?.takeIf { it.isNotBlank() }
            ?: defaultName(safeMimeType)
        LocalAttachment(
            id = UUID.randomUUID().toString(),
            displayName = name,
            mimeType = safeMimeType,
            sizeBytes = bytes.size.toLong(),
            inMemoryData = bytes,
        ) to bytes
    }

    suspend fun importBitmap(bitmap: Bitmap): Pair<LocalAttachment, ByteArray> =
        withContext(Dispatchers.IO) {
            val output = ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                throw LocalStorageException("فشرده‌سازی عکس ناموفق بود.")
            }
            val bytes = output.toByteArray()
            if (bytes.size > LocalRepository.MAX_ATTACHMENT_BYTES) {
                throw LocalStorageException("حجم عکس از سقف مجاز بیشتر است.")
            }
            LocalAttachment(
                id = UUID.randomUUID().toString(),
                displayName = "photo-${System.currentTimeMillis()}.jpg",
                mimeType = "image/jpeg",
                sizeBytes = bytes.size.toLong(),
                inMemoryData = bytes,
            ) to bytes
        }

    suspend fun persist(attachment: LocalAttachment): LocalAttachment =
        withContext(Dispatchers.IO) {
            val data = attachment.inMemoryData
                ?: throw LocalStorageException("محتوای پیوست در دسترس نیست.")
            if (data.size.toLong() != attachment.sizeBytes ||
                data.size > LocalRepository.MAX_ATTACHMENT_BYTES
            ) {
                throw LocalStorageException("اندازهٔ پیوست معتبر نیست.")
            }
            if (!attachmentDir.exists() && !attachmentDir.mkdirs()) {
                throw LocalStorageException("ساخت پوشهٔ خصوصی پیوست‌ها ناموفق بود.")
            }
            val target = attachmentFile(attachment.id)
            val temporary = File(attachmentDir, "${attachment.id}.tmp")
            try {
                temporary.writeBytes(data)
                if (!temporary.renameTo(target)) {
                    throw LocalStorageException("ذخیرهٔ خصوصی پیوست ناموفق بود.")
                }
            } catch (error: Exception) {
                temporary.delete()
                if (error is LocalStorageException) throw error
                throw LocalStorageException("ذخیرهٔ خصوصی پیوست ناموفق بود.", error)
            }
            attachment.copy(inMemoryData = null)
        }

    suspend fun read(attachment: LocalAttachment): ByteArray = withContext(Dispatchers.IO) {
        attachment.inMemoryData?.let { return@withContext it }
        val file = attachmentFile(attachment.id)
        if (!file.isFile || file.length() != attachment.sizeBytes ||
            file.length() > LocalRepository.MAX_ATTACHMENT_BYTES
        ) {
            throw LocalStorageException("فایل پیوست «${attachment.displayName}» در دسترس نیست.")
        }
        file.readBytes()
    }

    suspend fun deleteUnreferenced(store: LocalStore) = withContext(Dispatchers.IO) {
        val referenced = store.messages
            .flatMap { it.attachments }
            .mapTo(HashSet()) { it.id }
        if (attachmentDir.exists()) {
            attachmentDir.listFiles()?.forEach { file ->
                if (file.extension != "tmp" && file.nameWithoutExtension !in referenced) {
                    if (!file.delete()) {
                        throw LocalStorageException("پاک‌سازی پیوست قدیمی ناموفق بود.")
                    }
                }
            }
        }
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        if (attachmentDir.exists() && !attachmentDir.deleteRecursively()) {
            throw LocalStorageException("پاک‌کردن پیوست‌های محلی ناموفق بود.")
        }
    }

    suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
        attachmentDir.walkTopDown()
            .filter(File::isFile)
            .sumOf(File::length)
    }

    private fun attachmentFile(id: String): File {
        require(ATTACHMENT_ID_PATTERN.matches(id)) { "Invalid attachment id." }
        return File(attachmentDir, "$id.bin")
    }

    private fun normalizedMimeType(mimeType: String?): String {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it in LocalRepository.ALLOWED_ATTACHMENT_MIME_TYPES }
        return normalized ?: throw LocalStorageException(
            "این نوع فایل پشتیبانی نمی‌شود. عکس، PDF یا فایل متنی انتخاب کنید.",
        )
    }

    private fun defaultName(mimeType: String): String = when (mimeType) {
        "image/jpeg" -> "image.jpg"
        "image/png" -> "image.png"
        "image/webp" -> "image.webp"
        "application/pdf" -> "document.pdf"
        else -> "document.txt"
    }

    private fun java.io.InputStream.readBounded(maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            if (total > maxBytes) {
                throw LocalStorageException("فایل بزرگ‌تر از سقف مجاز ۸ مگابایت است.")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        private const val ATTACHMENT_DIRECTORY = "attachments"
        private const val JPEG_QUALITY = 86
        private val ATTACHMENT_ID_PATTERN = Regex("[a-fA-F0-9-]{36}")
    }
}
