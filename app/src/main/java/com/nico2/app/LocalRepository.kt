package com.nico2.app

import com.google.gson.Gson
import com.google.gson.JsonParseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

data class Conversation(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class Message(
    val id: String,
    val conversationId: String,
    val text: String,
    val isUser: Boolean,
    val createdAt: Long,
    val isPinned: Boolean = false,
    val attachments: List<LocalAttachment> = emptyList(),
)

data class LocalAttachment(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    @Transient val inMemoryData: ByteArray? = null,
)

data class MemoryItem(
    val id: String,
    val text: String,
    val category: String,
    val createdAt: Long,
    val enabled: Boolean,
)

data class LocalStore(
    val schemaVersion: Int = LocalRepository.CURRENT_SCHEMA_VERSION,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<Message> = emptyList(),
    val memoryItems: List<MemoryItem> = emptyList(),
    val activeConversationId: String? = null,
)

class LocalSession(initialStore: LocalStore = LocalStore()) {
    var persistedStore: LocalStore = initialStore
        private set
    var visibleStore: LocalStore = initialStore
        private set
    var isIncognito: Boolean = false
        private set

    fun enableIncognito(): LocalStore {
        isIncognito = true
        visibleStore = persistedStore
        return visibleStore
    }

    fun disableIncognito(latestPersistedStore: LocalStore): LocalStore {
        isIncognito = false
        persistedStore = latestPersistedStore
        visibleStore = latestPersistedStore
        return visibleStore
    }

    fun update(transform: (LocalStore) -> LocalStore): LocalStore {
        visibleStore = transform(visibleStore)
        if (!isIncognito) persistedStore = visibleStore
        return visibleStore
    }

    fun acceptPersistedUpdate(store: LocalStore): LocalStore {
        persistedStore = store
        if (!isIncognito) visibleStore = store
        return visibleStore
    }
}

enum class ConversationFilter {
    All,
    Pinned,
    Recent,
    WithMessages,
}

fun LocalStore.deleteConversation(conversationId: String): LocalStore = copy(
    conversations = conversations.filterNot { it.id == conversationId },
    messages = messages.filterNot { it.conversationId == conversationId },
    activeConversationId = activeConversationId.takeUnless { it == conversationId },
)

fun LocalStore.deleteMessage(messageId: String): LocalStore =
    copy(messages = messages.filterNot { it.id == messageId })

fun LocalStore.setMessagePinned(messageId: String, pinned: Boolean): LocalStore =
    copy(messages = messages.map {
        if (it.id == messageId) it.copy(isPinned = pinned) else it
    })

fun LocalStore.deleteMemoryItem(memoryItemId: String): LocalStore =
    copy(memoryItems = memoryItems.filterNot { it.id == memoryItemId })

fun LocalStore.searchConversations(
    query: String,
    filter: ConversationFilter = ConversationFilter.All,
    now: Long = System.currentTimeMillis(),
): List<Conversation> {
    val normalizedQuery = query.trim()
    val matchingConversations = conversations.filter { conversation ->
        val conversationMessages = messages.filter {
            it.conversationId == conversation.id
        }
        val matchesQuery = normalizedQuery.isEmpty() ||
            conversation.title.contains(normalizedQuery, ignoreCase = true) ||
            conversationMessages.any { it.text.contains(normalizedQuery, ignoreCase = true) }
        val matchesFilter = when (filter) {
            ConversationFilter.All -> true
            ConversationFilter.Pinned -> conversationMessages.any { it.isPinned }
            ConversationFilter.Recent -> now - conversation.updatedAt <= RECENT_WINDOW_MILLIS
            ConversationFilter.WithMessages -> conversationMessages.isNotEmpty()
        }
        matchesQuery && matchesFilter
    }
    return matchingConversations.sortedByDescending { it.updatedAt }
}

private const val RECENT_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1_000L

class LocalStorageException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

class LocalRepository(
    private val file: File,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()
    private val gson = Gson()
    private val attachmentsDirectory = File(file.parentFile, "attachments")

    suspend fun load(): LocalStore = withContext(ioDispatcher) {
        mutex.withLock { readUnlocked() }
    }

    suspend fun save(store: LocalStore) = withContext(ioDispatcher) {
        mutex.withLock {
            validate(store)
            writeUnlocked(store)
        }
    }

    suspend fun update(transform: (LocalStore) -> LocalStore): LocalStore =
        withContext(ioDispatcher) {
            mutex.withLock {
                val updated = transform(readUnlocked())
                validate(updated)
                writeUnlocked(updated)
                updated
            }
        }

    suspend fun clear() = withContext(ioDispatcher) {
        mutex.withLock { writeUnlocked(LocalStore()) }
    }

    suspend fun storageBytes(): Long = withContext(ioDispatcher) {
        mutex.withLock {
            (if (file.exists()) file.length() else 0L) +
                attachmentsDirectory.walkTopDown().filter(File::isFile).sumOf(File::length)
        }
    }

    suspend fun awaitIdle() = withContext(ioDispatcher) {
        mutex.withLock { Unit }
    }

    private fun readUnlocked(): LocalStore {
        if (!file.exists()) return LocalStore()
        if (file.length() > MAX_FILE_BYTES) {
            throw LocalStorageException("فایل حافظه از اندازهٔ مجاز بزرگ‌تر است.")
        }

        return try {
            val root = file.reader(Charsets.UTF_8).use {
                com.google.gson.JsonParser.parseReader(it)
            }?.asJsonObject ?: throw JsonParseException("فایل خالی است.")
            val sourceSchemaVersion = root.get("schemaVersion")?.asInt ?: 1
            if (sourceSchemaVersion !in 1..CURRENT_SCHEMA_VERSION) {
                throw LocalStorageException("نسخهٔ فایل حافظه پشتیبانی نمی‌شود.")
            }
            root.getAsJsonArray("messages")?.forEach { entry ->
                if (!entry.asJsonObject.has("attachments")) {
                    entry.asJsonObject.add("attachments", com.google.gson.JsonArray())
                }
            }
            root.addProperty("schemaVersion", CURRENT_SCHEMA_VERSION)
            val loaded = gson.fromJson(root, LocalStore::class.java)
                ?: throw JsonParseException("فایل خالی است.")
            val migrated = loaded.copy(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                messages = loaded.messages.map { it.copy(attachments = it.attachments.orEmpty()) },
            )
            validate(migrated)
            if (sourceSchemaVersion != CURRENT_SCHEMA_VERSION) writeUnlocked(migrated)
            migrated
        } catch (error: LocalStorageException) {
            throw error
        } catch (error: Exception) {
            throw LocalStorageException("فایل حافظه خراب یا قابل خواندن نیست.", error)
        }
    }

    private fun writeUnlocked(store: LocalStore) {
        val json = gson.toJson(store)
        if (json.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) {
            throw LocalStorageException("حافظه به سقف اندازهٔ مجاز رسیده است.")
        }
        val parent = file.parentFile
            ?: throw LocalStorageException("مسیر ذخیره‌سازی محلی معتبر نیست.")
        val temporaryFile = File(parent, "${file.name}.tmp")
        try {
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("ساخت پوشهٔ خصوصی ذخیره‌سازی ممکن نشد.")
            }
            temporaryFile.outputStream().bufferedWriter(Charsets.UTF_8).use {
                it.write(json)
            }
            if (!temporaryFile.renameTo(file)) {
                throw IOException("جایگزینی اتمیک فایل حافظه ممکن نشد.")
            }
        } catch (error: Exception) {
            temporaryFile.delete()
            throw LocalStorageException("نوشتن فایل حافظهٔ محلی ناموفق بود.", error)
        }
    }

    private fun validate(store: LocalStore) {
        if (store.schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw LocalStorageException("نسخهٔ فایل حافظه پشتیبانی نمی‌شود.")
        }
        if (store.conversations.size > MAX_CONVERSATIONS) {
            throw LocalStorageException("تعداد گفتگوها از سقف مجاز بیشتر است.")
        }
        if (store.messages.size > MAX_MESSAGES) {
            throw LocalStorageException("تعداد پیام‌ها از سقف مجاز بیشتر است.")
        }
        if (store.memoryItems.size > MAX_MEMORY_ITEMS) {
            throw LocalStorageException("تعداد موارد حافظه از سقف مجاز بیشتر است.")
        }
        if (store.conversations.any {
                it.id.isBlank() || it.title.length > MAX_TITLE_LENGTH ||
                    it.createdAt < 0L || it.updatedAt < 0L
            }
        ) {
            throw LocalStorageException("اطلاعات یکی از گفتگوها معتبر نیست.")
        }
        if (store.conversations.map { it.id }.toSet().size != store.conversations.size) {
            throw LocalStorageException("شناسهٔ گفتگوها تکراری است.")
        }
        val conversationIds = store.conversations.mapTo(HashSet()) { it.id }
        if (store.messages.any {
                it.id.isBlank() || it.conversationId !in conversationIds ||
                    it.text.length > MAX_MESSAGE_LENGTH || it.createdAt < 0L ||
                    it.attachments.size > MAX_ATTACHMENTS_PER_MESSAGE ||
                    it.attachments.any { attachment ->
                        attachment.id.isBlank() ||
                            !ATTACHMENT_ID_PATTERN.matches(attachment.id) ||
                            attachment.displayName.isBlank() ||
                            attachment.displayName.length > MAX_ATTACHMENT_NAME_LENGTH ||
                            attachment.mimeType !in ALLOWED_ATTACHMENT_MIME_TYPES ||
                            attachment.sizeBytes !in 1..MAX_ATTACHMENT_BYTES
                    }
            }
        ) {
            throw LocalStorageException("اطلاعات یکی از پیام‌ها معتبر نیست.")
        }
        if (store.messages.map { it.id }.toSet().size != store.messages.size) {
            throw LocalStorageException("شناسهٔ پیام‌ها تکراری است.")
        }
        if (store.memoryItems.any {
                it.id.isBlank() || it.text.isBlank() ||
                    it.text.length > MAX_MEMORY_TEXT_LENGTH ||
                    it.category.length > MAX_CATEGORY_LENGTH || it.createdAt < 0L
            }
        ) {
            throw LocalStorageException("اطلاعات یکی از موارد حافظه معتبر نیست.")
        }
        if (store.memoryItems.map { it.id }.toSet().size != store.memoryItems.size) {
            throw LocalStorageException("شناسهٔ موارد حافظه تکراری است.")
        }
        if (store.activeConversationId != null &&
            store.activeConversationId !in conversationIds
        ) {
            throw LocalStorageException("گفتگوی فعال در فایل حافظه وجود ندارد.")
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
        const val MAX_FILE_BYTES = 4 * 1024 * 1024
        const val MAX_CONVERSATIONS = 100
        const val MAX_MESSAGES = 5_000
        const val MAX_MEMORY_ITEMS = 100
        const val MAX_TITLE_LENGTH = 120
        const val MAX_MESSAGE_LENGTH = 10_000
        const val MAX_MEMORY_TEXT_LENGTH = 4_000
        const val MAX_CATEGORY_LENGTH = 80
        const val MAX_ATTACHMENTS_PER_MESSAGE = 3
        const val MAX_ATTACHMENT_NAME_LENGTH = 180
        const val MAX_ATTACHMENT_BYTES = 8L * 1024 * 1024
        val ALLOWED_ATTACHMENT_MIME_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "image/webp",
            "application/pdf",
            "text/plain",
        )
        private val ATTACHMENT_ID_PATTERN = Regex("[a-fA-F0-9-]{36}")
    }
}
