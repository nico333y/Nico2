package com.nico2.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LocalRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun repository(name: String = "local-memory.json") =
        LocalRepository(File(temporaryFolder.root, name))

    @Test
    fun conversationAndMessageSurviveRepositoryRecreation() = runBlocking {
        val conversation = Conversation("c1", "سلام", 10, 20)
        val message = Message("m1", "c1", "پیام محلی", true, 15)
        val firstRepository = repository()

        firstRepository.save(
            LocalStore(
                conversations = listOf(conversation),
                messages = listOf(message),
                activeConversationId = conversation.id,
            ),
        )

        val restored = repository().load()
        assertEquals(2, restored.schemaVersion)
        assertEquals(listOf(conversation), restored.conversations)
        assertEquals(listOf(message), restored.messages)
        assertEquals(conversation.id, restored.activeConversationId)
    }

    @Test
    fun migratesSchemaOneMessagesWithoutAttachments() = runBlocking {
        val file = File(temporaryFolder.root, "local-memory.json")
        file.writeText(
            """
            {
              "schemaVersion": 1,
              "conversations": [{"id":"c1","title":"Old","createdAt":1,"updatedAt":2}],
              "messages": [{"id":"m1","conversationId":"c1","text":"Legacy","isUser":true,"createdAt":1}],
              "memoryItems": [],
              "activeConversationId": "c1"
            }
            """.trimIndent(),
        )

        val migrated = repository().load()

        assertEquals(2, migrated.schemaVersion)
        assertTrue(migrated.messages.single().attachments.isEmpty())
        assertTrue(file.readText().contains("\"schemaVersion\":2"))
    }

    @Test
    fun attachmentFilesArePrivateReadableAndCleanedWhenUnreferenced() = runBlocking {
        val storage = AttachmentStorage(temporaryFolder.root)
        val content = byteArrayOf(1, 2, 3, 4)
        val attachment = LocalAttachment(
            id = "123e4567-e89b-12d3-a456-426614174000",
            displayName = "sample.txt",
            mimeType = "text/plain",
            sizeBytes = content.size.toLong(),
            inMemoryData = content,
        )

        val stored = storage.persist(attachment)

        assertEquals(null, stored.inMemoryData)
        assertEquals(content.toList(), storage.read(stored).toList())
        assertEquals(content.size.toLong(), storage.storageBytes())
        storage.deleteUnreferenced(LocalStore())
        assertEquals(0L, storage.storageBytes())
    }

    @Test
    fun reportsActualStorageFileSize() = runBlocking {
        val file = File(temporaryFolder.root, "local-memory.json")
        val repo = LocalRepository(file)
        assertEquals(0L, repo.storageBytes())
        repo.save(LocalStore())
        assertEquals(file.length(), repo.storageBytes())
    }

    @Test
    fun updateDeletesConversationAndItsMessages() = runBlocking {
        val repo = repository()
        repo.save(
            LocalStore(
                conversations = listOf(
                    Conversation("c1", "اول", 1, 1),
                    Conversation("c2", "دوم", 2, 2),
                ),
                messages = listOf(
                    Message("m1", "c1", "پیام", true, 1),
                    Message("m2", "c2", "پیام دیگر", true, 2),
                ),
                activeConversationId = "c1",
            ),
        )

        val updated = repo.update { it.deleteConversation("c1") }
        assertEquals(listOf("c2"), updated.conversations.map { it.id })
        assertEquals(listOf("m2"), updated.messages.map { it.id })
        assertEquals(null, updated.activeConversationId)
    }

    @Test
    fun pinAndUnpinPersistOnDisk() = runBlocking {
        val repo = repository()
        repo.save(
            LocalStore(
                conversations = listOf(Conversation("c1", "گفتگو", 1, 1)),
                messages = listOf(Message("m1", "c1", "متن", true, 1)),
            ),
        )

        repo.update { it.setMessagePinned("m1", true) }
        assertTrue(repository().load().messages.single().isPinned)
        repo.update { it.setMessagePinned("m1", false) }
        assertFalse(repository().load().messages.single().isPinned)
    }

    @Test
    fun deletedMessageDoesNotReturnAfterReload() = runBlocking {
        val repo = repository()
        repo.save(
            LocalStore(
                conversations = listOf(Conversation("c1", "گفتگو", 1, 1)),
                messages = listOf(Message("m1", "c1", "متن", true, 1)),
            ),
        )

        repo.update { it.deleteMessage("m1") }

        assertTrue(repository().load().messages.isEmpty())
    }

    @Test
    fun searchMatchesConversationTitleAndMessageTextWithFilters() {
        val now = 1_000_000_000L
        val store = LocalStore(
            conversations = listOf(
                Conversation("recent", "موضوع آفتاب", now, now),
                Conversation("old", "بایگانی", 1, now - 8L * 24L * 60L * 60L * 1_000L),
                Conversation("empty", "بدون پیام", now, now),
            ),
            messages = listOf(
                Message("pinned", "recent", "متن دلخواه", true, now, isPinned = true),
                Message("old-message", "old", "پیام قدیمی", false, 1),
            ),
        )

        assertEquals(
            listOf("recent"),
            store.searchConversations("دلخواه", now = now).map { it.id },
        )
        assertEquals(
            listOf("recent"),
            store.searchConversations("آفتاب", now = now).map { it.id },
        )
        assertEquals(
            listOf("recent"),
            store.searchConversations("", ConversationFilter.Pinned, now).map { it.id },
        )
        assertEquals(
            listOf("recent", "empty"),
            store.searchConversations("", ConversationFilter.Recent, now).map { it.id },
        )
        assertEquals(
            listOf("recent", "old"),
            store.searchConversations("", ConversationFilter.WithMessages, now).map { it.id },
        )
    }

    @Test
    fun incognitoChangesAreDroppedWhenModeEnds() = runBlocking {
        val repo = repository()
        val savedConversation = Conversation("c1", "ذخیره‌شده", 1, 1)
        repo.save(LocalStore(conversations = listOf(savedConversation)))
        val session = LocalSession(repo.load())
        val persistedBeforeIncognito = repo.load()

        session.enableIncognito()
        session.update { store ->
            store.copy(
                messages = listOf(Message("m1", "c1", "ناشناس", true, 2)),
                memoryItems = listOf(MemoryItem("i1", "مورد", "عمومی", 2, true)),
            )
        }

        assertEquals(persistedBeforeIncognito, repo.load())
        session.disableIncognito(repo.load())
        assertTrue(session.visibleStore.messages.isEmpty())
        assertTrue(session.visibleStore.memoryItems.isEmpty())
        assertEquals(listOf(savedConversation), session.visibleStore.conversations)
    }

    @Test
    fun memoryItemDeletionAndToggleArePersisted() = runBlocking {
        val repo = repository()
        val item = MemoryItem("i1", "ترجیح", "شخصی", 5, enabled = true)
        repo.save(LocalStore(memoryItems = listOf(item)))
        repo.update { store ->
            store.copy(memoryItems = store.memoryItems.map { it.copy(enabled = false) })
        }
        assertFalse(repository().load().memoryItems.single().enabled)

        repo.update { it.deleteMemoryItem("i1") }
        assertTrue(repository().load().memoryItems.isEmpty())
    }

    @Test
    fun corruptFileReturnsReadableErrorAndCanBeCleared() = runBlocking {
        val repo = repository()
        File(temporaryFolder.root, "local-memory.json").writeText("{broken")

        val error = assertThrows(LocalStorageException::class.java) {
            runBlocking { repo.load() }
        }
        assertTrue(error.message.orEmpty().contains("خراب"))
        repo.clear()
        assertEquals(LocalStore(), repo.load())
    }

    @Test
    fun rejectsUnknownSchemaAndOversizedFiles(): Unit {
        runBlocking {
            val repo = repository()
            assertThrows(LocalStorageException::class.java) {
                runBlocking { repo.save(LocalStore(schemaVersion = 3)) }
            }

            val oversizedRepository = repository("oversized.json")
            File(temporaryFolder.root, "oversized.json")
                .writeBytes(ByteArray(LocalRepository.MAX_FILE_BYTES + 1))
            assertThrows(LocalStorageException::class.java) {
                runBlocking { oversizedRepository.load() }
            }
        }
    }
}
