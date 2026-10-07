package com.nico2.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import com.nico2.app.ui.components.AppBackground
import com.nico2.app.ui.components.AttachmentOptionsSheet
import com.nico2.app.ui.components.GlassCard
import com.nico2.app.ui.components.GlassCapsule
import com.nico2.app.ui.components.GoldIconButton
import com.nico2.app.ui.components.NicoPalette

private val Background = NicoPalette.Background
private val SurfaceDark = NicoPalette.Charcoal
private val SurfaceRaised = NicoPalette.Raised
private val Gold = NicoPalette.Gold
private val MutedGold = NicoPalette.MutedGold
private val TextPrimary = NicoPalette.WarmWhite
private val TextSecondary = NicoPalette.WarmGray
private val OutlineDark = Color(0xFF35332D)

private val ChatColors = darkColorScheme(
    primary = Gold,
    onPrimary = Background,
    secondary = MutedGold,
    background = Background,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    outline = OutlineDark,
)

private data class ChatMessage(
    val id: String,
    val text: String,
    val fromUser: Boolean,
    val demo: Boolean = false,
    val pinned: Boolean = false,
    val attachments: List<LocalAttachment> = emptyList(),
)

@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val layoutDirection = if (
        context.resources.configuration.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
    ) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        MaterialTheme(colorScheme = ChatColors) {
            ChatContent()
        }
    }
}

@Composable
private fun ChatContent() {
    val context = LocalContext.current
    val repository = remember(context) {
        LocalRepository(File(context.filesDir, "local-memory.json"))
    }
    val attachmentStorage = remember(context) { AttachmentStorage(context.filesDir) }
    val apiKeyStore = remember(context) { GeminiApiKeyStore(context) }
    val geminiApi = remember { GeminiApiClient() }
    var userPreferences by remember(context) {
        mutableStateOf(UserPreferences.read(context))
    }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val session = remember { LocalSession() }
    val drawerState = androidx.compose.material3.rememberDrawerState(
        initialValue = androidx.compose.material3.DrawerValue.Closed,
    )
    var currentPage by remember { mutableStateOf(AppPage.Chat) }
    var voiceMiniPlayerVisible by remember { mutableStateOf(false) }
    var selectedSettingsTab by remember { mutableStateOf(SettingsTab.Api) }
    var reduceAnimations by remember { mutableStateOf(userPreferences.reduceAnimations) }
    var isIncognito by remember { mutableStateOf(false) }
    var isStorageLoaded by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf<String?>(null) }
    var storageBytes by remember { mutableStateOf(0L) }
    var operationGeneration by remember { mutableIntStateOf(0) }
    var localStore by remember { mutableStateOf(LocalStore()) }
    var selectedConversationId by remember { mutableStateOf<String?>(null) }
    var demoSession by remember { mutableStateOf<List<ChatMessage>?>(null) }
    var draft by remember { mutableStateOf("") }
    var nextMessageId by remember { mutableIntStateOf(0) }
    var showResponseNotice by remember { mutableStateOf(false) }
    var shouldScrollAfterUpdate by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    var showModels by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(userPreferences.selectedModel) }
    var geminiApiKey by remember { mutableStateOf<String?>(null) }
    var availableModels by remember { mutableStateOf<List<GeminiModel>>(emptyList()) }
    var apiModelsStatus by remember { mutableStateOf("") }
    var isLoadingModels by remember { mutableStateOf(false) }
    var isGeneratingReply by remember { mutableStateOf(false) }
    var showAttachmentOptions by remember { mutableStateOf(false) }
    val pendingAttachments = remember { mutableStateListOf<LocalAttachment>() }
    val demoUserMessage = stringResource(R.string.demo_user_message)
    val demoAssistantMessage = stringResource(R.string.demo_assistant_message)
    val demoFollowUp = stringResource(R.string.demo_follow_up)

    val messages = remember(localStore, selectedConversationId, demoSession) {
        demoSession ?: localStore.messages
            .filter { it.conversationId == selectedConversationId }
            .map { message ->
                ChatMessage(
                    id = message.id,
                    text = message.text,
                    fromUser = message.isUser,
                    pinned = message.isPinned,
                    attachments = message.attachments,
                )
            }
    }

    LaunchedEffect(repository) {
        try {
            val loaded = repository.load()
            localStore = session.acceptPersistedUpdate(loaded)
            attachmentStorage.deleteUnreferenced(loaded)
            selectedConversationId = loaded.activeConversationId
                ?: loaded.conversations.maxByOrNull { it.updatedAt }?.id
            storageBytes = repository.storageBytes()
        } catch (error: LocalStorageException) {
            storageError = error.message ?: context.getString(R.string.storage_read_error)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            storageError = context.getString(R.string.storage_read_error)
        } finally {
            isStorageLoaded = true
        }
    }

    LaunchedEffect(apiKeyStore) {
        try {
            val savedKey = apiKeyStore.read()
            if (!savedKey.isNullOrBlank()) {
                geminiApiKey = savedKey
                isLoadingModels = true
                val models = geminiApi.listModels(savedKey)
                availableModels = models
                selectedModel = models.firstOrNull {
                    it.id == userPreferences.selectedModel && (it.supportsText || it.supportsLive)
                }?.id ?: preferredTextModel(models)?.id.orEmpty()
                userPreferences = userPreferences.copy(selectedModel = selectedModel).also {
                    it.write(context)
                }
                apiModelsStatus = context.getString(
                    if (models.isEmpty()) R.string.api_models_empty
                    else R.string.api_models_loaded,
                    models.size,
                )
            }
        } catch (error: Exception) {
            apiModelsStatus = error.message ?: context.getString(R.string.api_key_invalid)
        } finally {
            isLoadingModels = false
        }
    }

    BackHandler(enabled = currentPage != AppPage.Chat) {
        currentPage = AppPage.Chat
    }

    LaunchedEffect(messages, demoSession, geminiApiKey, isGeneratingReply) {
        showResponseNotice = demoSession == null &&
            geminiApiKey.isNullOrBlank() &&
            !isGeneratingReply &&
            messages.lastOrNull()?.fromUser == true
    }

    suspend fun updateStoreNow(transform: (LocalStore) -> LocalStore): LocalStore {
        check(isStorageLoaded) { context.getString(R.string.storage_read_error) }
        if (isIncognito) {
            return session.update(transform).also { localStore = it }
        }
        val generation = operationGeneration
        val updated = repository.update(transform)
        if (generation != operationGeneration) {
            throw CancellationException("Storage operation was superseded.")
        }
        localStore = session.acceptPersistedUpdate(updated)
        storageError = null
        storageBytes = repository.storageBytes()
        return updated
    }

    val updateStore: ((LocalStore) -> LocalStore) -> Unit = { transform ->
        if (isStorageLoaded) {
            coroutineScope.launch {
                try {
                    updateStoreNow(transform)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: LocalStorageException) {
                    storageError = error.message ?: context.getString(R.string.storage_write_error)
                } catch (error: Exception) {
                    storageError = error.message ?: context.getString(R.string.storage_write_error)
                }
            }
        }
    }
    val pruneUnreferencedAttachments: () -> Unit = {
        if (!isIncognito) {
            coroutineScope.launch {
                try {
                    repository.awaitIdle()
                    attachmentStorage.deleteUnreferenced(repository.load())
                    storageBytes = repository.storageBytes()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    storageError = error.message ?: context.getString(R.string.storage_write_error)
                }
            }
        }
    }

    val bottomThreshold = with(LocalDensity.current) { 72.dp.toPx() }
    val isNearBottom by remember(listState, bottomThreshold) {
        derivedStateOf { listIsNearBottom(listState, bottomThreshold) }
    }

    LaunchedEffect(messages.size, showResponseNotice) {
        if (shouldScrollAfterUpdate && messages.isNotEmpty()) {
            val lastItemIndex = if (showResponseNotice) messages.size else messages.lastIndex
            listState.animateScrollToItem(lastItemIndex)
            shouldScrollAfterUpdate = false
        }
    }

    val showSnackbar: (Int) -> Unit = { messageId ->
        coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(messageId)) }
    }
    val showSnackbarText: (String) -> Unit = { message ->
        coroutineScope.launch { snackbarHostState.showSnackbar(message) }
    }
    val capturePhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            coroutineScope.launch {
                try {
                    val (attachment, _) = attachmentStorage.importBitmap(bitmap)
                    addPendingAttachment(attachment, pendingAttachments, showSnackbarText, context)
                } catch (error: Exception) {
                    showSnackbarText(error.message ?: context.getString(R.string.attachment_import_failed))
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }
    val chooseImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            importPickedAttachment(
                uri,
                context,
                attachmentStorage,
                pendingAttachments,
                coroutineScope,
                showSnackbarText,
            )
        }
    }
    val chooseFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            importPickedAttachment(
                uri,
                context,
                attachmentStorage,
                pendingAttachments,
                coroutineScope,
                showSnackbarText,
            )
        }
    }

    val sendMessage: () -> Unit = {
        val text = draft.trim()
        val userText = text.ifBlank {
            pendingAttachments.firstOrNull()?.displayName.orEmpty()
        }
        val apiKey = geminiApiKey
        if (voiceMiniPlayerVisible) {
            showSnackbar(R.string.voice_text_input_unavailable)
        } else if ((text.isNotEmpty() || pendingAttachments.isNotEmpty()) &&
            isStorageLoaded && storageError == null && !isGeneratingReply
        ) {
            if (apiKey.isNullOrBlank()) {
                showSnackbar(R.string.api_key_missing)
            } else if (selectedModel.isBlank()) {
                showSnackbar(R.string.api_model_unavailable)
            } else {
            shouldScrollAfterUpdate = isNearBottom
            val now = System.currentTimeMillis()
            val hadDemoMessages = demoSession != null
            val conversationId = if (hadDemoMessages) null else selectedConversationId
            val id = conversationId ?: UUID.randomUUID().toString()
            val title = userText.lineSequence().first().take(LocalRepository.MAX_TITLE_LENGTH)
            val storedText = userText.take(LocalRepository.MAX_MESSAGE_LENGTH)
            val attachmentsForMessage = pendingAttachments.toList()
            val previousMessages = if (hadDemoMessages) emptyList() else messages.takeLast(15)
            demoSession = null
            selectedConversationId = id
            showResponseNotice = false
            draft = ""
            isGeneratingReply = true
            coroutineScope.launch {
                try {
                    val storedAttachments = if (isIncognito) {
                        attachmentsForMessage
                    } else {
                        attachmentsForMessage.map { attachmentStorage.persist(it) }
                    }
                    val newMessage = Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = id,
                        text = storedText,
                        isUser = true,
                        createdAt = now,
                        attachments = storedAttachments,
                    )
                    updateStoreNow { store ->
                        val existing = store.conversations.firstOrNull { it.id == id }
                        val updatedConversation = if (existing == null) {
                            Conversation(id = id, title = title, createdAt = now, updatedAt = now)
                        } else {
                            existing.copy(updatedAt = now)
                        }
                        store.copy(
                            conversations = store.conversations
                                .filterNot { it.id == id } + updatedConversation,
                            messages = store.messages + newMessage,
                            activeConversationId = id,
                        )
                    }
                    pendingAttachments.clear()
                    val turns = buildList {
                        previousMessages.forEach { message ->
                            add(
                                GeminiTurn(
                                    text = message.text,
                                    isUser = message.fromUser,
                                    attachments = message.attachments.map { attachment ->
                                        GeminiInlineAttachment(
                                            attachment.mimeType,
                                            attachmentStorage.read(attachment),
                                        )
                                    },
                                ),
                            )
                        }
                        add(
                            GeminiTurn(
                                text = storedText,
                                isUser = true,
                                attachments = storedAttachments.map { attachment ->
                                    GeminiInlineAttachment(
                                        attachment.mimeType,
                                        attachment.inMemoryData
                                            ?: attachmentStorage.read(attachment),
                                    )
                                },
                            ),
                        )
                    }
                    val liveModelSelected = availableModels.any {
                        it.id == selectedModel && it.supportsLive
                    }
                    val (usedModel, answer) = if (liveModelSelected) {
                        selectedModel to generateGeminiLiveTextReply(
                            context = context,
                            apiKey = apiKey,
                            modelId = selectedModel,
                            turns = turns,
                            systemInstruction = buildAssistantInstruction(userPreferences),
                        )
                    } else {
                        geminiApi.generateReply(
                            apiKey = apiKey,
                            selectedModelId = selectedModel,
                            availableModels = availableModels,
                            turns = turns,
                            systemInstruction = buildAssistantInstruction(userPreferences),
                        )
                    }
                    val answerCreatedAt = System.currentTimeMillis()
                    val assistantMessage = Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = id,
                        text = answer.take(LocalRepository.MAX_MESSAGE_LENGTH),
                        isUser = false,
                        createdAt = answerCreatedAt,
                    )
                    updateStore { store ->
                        if (store.conversations.none { it.id == id }) {
                            store
                        } else {
                            store.copy(
                                conversations = store.conversations.map { conversation ->
                                    if (conversation.id == id) {
                                        conversation.copy(updatedAt = answerCreatedAt)
                                    } else conversation
                                },
                                messages = store.messages + assistantMessage,
                            )
                        }
                    }
                    showResponseNotice = false
                    if (usedModel != selectedModel) {
                        selectedModel = usedModel
                        val displayName = availableModels
                            .firstOrNull { it.id == usedModel }
                            ?.displayName ?: usedModel
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.api_model_fallback, displayName),
                        )
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: LocalStorageException) {
                    if (!isIncognito) attachmentStorage.deleteUnreferenced(localStore)
                    showSnackbarText(error.message ?: context.getString(R.string.attachment_import_failed))
                } catch (error: GeminiApiException) {
                    val message = if (error.statusCode == 429) {
                        context.getString(R.string.api_quota_exhausted)
                    } else {
                        context.getString(
                            R.string.api_request_failed,
                            error.message ?: context.getString(R.string.api_key_invalid),
                        )
                    }
                    snackbarHostState.showSnackbar(message)
                } catch (error: Exception) {
                    showSnackbarText(
                        context.getString(
                            R.string.api_request_failed,
                            error.message ?: context.getString(R.string.api_key_invalid),
                        ),
                    )
                } finally {
                    isGeneratingReply = false
                }
            }
            }
        }
    }

    val refreshModels: () -> Unit = {
        val apiKey = geminiApiKey
        if (apiKey.isNullOrBlank()) {
            apiModelsStatus = context.getString(R.string.api_key_missing)
        } else if (!isLoadingModels) {
            coroutineScope.launch {
                isLoadingModels = true
                apiModelsStatus = context.getString(R.string.api_models_loading)
                try {
                    val models = geminiApi.listModels(apiKey)
                    availableModels = models
                    if (models.none {
                            it.id == selectedModel && (it.supportsText || it.supportsLive)
                        }
                    ) {
                        selectedModel = preferredTextModel(models)?.id.orEmpty()
                    }
                    userPreferences = userPreferences.copy(selectedModel = selectedModel).also {
                        it.write(context)
                    }
                    apiModelsStatus = context.getString(
                        if (models.isEmpty()) R.string.api_models_empty
                        else R.string.api_models_loaded,
                        models.size,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    apiModelsStatus = error.message ?: context.getString(R.string.api_key_invalid)
                } finally {
                    isLoadingModels = false
                }
            }
        }
    }

    val saveApiKey: (String) -> Unit = { candidate ->
        if (candidate.isBlank()) {
            apiModelsStatus = context.getString(R.string.api_key_missing)
        } else {
            coroutineScope.launch {
                isLoadingModels = true
                apiModelsStatus = context.getString(R.string.api_models_loading)
                try {
                    val models = geminiApi.listModels(candidate)
                    if (models.none { it.supportsText || it.supportsLive }) {
                        throw GeminiApiException(
                            0,
                            context.getString(R.string.api_models_empty),
                        )
                    }
                    apiKeyStore.write(candidate)
                    geminiApiKey = candidate
                    availableModels = models
                    selectedModel = preferredTextModel(models)?.id.orEmpty()
                    userPreferences = userPreferences.copy(selectedModel = selectedModel).also {
                        it.write(context)
                    }
                    apiModelsStatus = context.getString(R.string.api_key_saved_success)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    apiModelsStatus = error.message ?: context.getString(R.string.api_key_invalid)
                } finally {
                    isLoadingModels = false
                }
            }
        }
    }

    val removeApiKey: () -> Unit = {
        coroutineScope.launch {
            try {
                apiKeyStore.clear()
                geminiApiKey = null
                availableModels = emptyList()
                selectedModel = ""
                apiModelsStatus = context.getString(R.string.api_key_removed)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                apiModelsStatus = error.message ?: context.getString(R.string.api_key_invalid)
            }
        }
    }

    val saveLiveTranscript: (Boolean, String) -> Unit = { isUser, transcript ->
        val text = transcript.trim().take(LocalRepository.MAX_MESSAGE_LENGTH)
        if (text.isNotBlank() && isStorageLoaded && storageError == null) {
            val now = System.currentTimeMillis()
            val conversationId = selectedConversationId ?: UUID.randomUUID().toString()
            val conversationTitle = text.lineSequence().first()
                .take(LocalRepository.MAX_TITLE_LENGTH)
            if (selectedConversationId == null) {
                selectedConversationId = conversationId
                demoSession = null
            }
            updateStore { store ->
                val conversation = store.conversations.firstOrNull { it.id == conversationId }
                    ?: Conversation(
                        id = conversationId,
                        title = conversationTitle,
                        createdAt = now,
                        updatedAt = now,
                    )
                store.copy(
                    conversations = store.conversations
                        .filterNot { it.id == conversationId } + conversation.copy(updatedAt = now),
                    messages = store.messages + Message(
                        id = UUID.randomUUID().toString(),
                        conversationId = conversationId,
                        text = text,
                        isUser = isUser,
                        createdAt = now,
                    ),
                    activeConversationId = conversationId,
                )
            }
        }
    }

    val openSettings: (SettingsTab) -> Unit = { tab ->
        selectedSettingsTab = tab
        currentPage = if (tab == SettingsTab.Memory) AppPage.Memory else AppPage.Settings
        coroutineScope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentPage == AppPage.Chat,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SurfaceDark,
                drawerContentColor = TextPrimary,
            ) {
                MainDrawer(
                    onSelectTab = openSettings,
                    onShowDemo = {
                        coroutineScope.launch { drawerState.close() }
                        shouldScrollAfterUpdate = isNearBottom
                        demoSession = demoMessages(
                            firstId = nextMessageId,
                            userMessage = demoUserMessage,
                            assistantMessage = demoAssistantMessage,
                            followUp = demoFollowUp,
                        )
                        nextMessageId += 3
                        showResponseNotice = false
                    },
                    onNewChat = {
                        demoSession = null
                        selectedConversationId = null
                        showResponseNotice = false
                        coroutineScope.launch { drawerState.close() }
                    },
                )
            }
        },
        scrimColor = Color.Black.copy(alpha = 0.76f),
    ) {
        AppBackground(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
                    .padding(bottom = if (voiceMiniPlayerVisible) 128.dp else 0.dp),
                containerColor = Color.Transparent,
                snackbarHost = {
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                },
            ) { contentPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .padding(horizontal = 16.dp),
                ) {
                    if (isIncognito) {
                        IncognitoBanner()
                    }
                    storageError?.let { error ->
                        StorageErrorBanner(
                            message = error,
                            onOpenMemory = { currentPage = AppPage.Memory },
                        )
                    }
                    ChatHeader(
                        selectedModel = availableModels.firstOrNull { it.id == selectedModel }?.displayName.orEmpty(),
                        availableModels = availableModels.filter { it.supportsText || it.supportsLive },
                        isLoadingModels = isLoadingModels,
                        apiKeyConfigured = !geminiApiKey.isNullOrBlank(),
                        showModels = showModels,
                        onMenuClick = { coroutineScope.launch { drawerState.open() } },
                        onModelClick = { showModels = true },
                        onModelDismiss = { showModels = false },
                        onModelSelected = {
                            selectedModel = it
                            userPreferences = userPreferences.copy(selectedModel = it).also {
                                updated -> updated.write(context)
                            }
                            showModels = false
                        },
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (messages.isEmpty()) {
                            EmptyChat(modifier = Modifier.fillMaxSize())
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                reverseLayout = false,
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    vertical = 18.dp,
                                ),
                            ) {
                                items(messages, key = { it.id }) { message ->
                                    MessageCard(
                                        message = message,
                                        onCopy = {
                                            val clipboard = context.getSystemService(
                                                Context.CLIPBOARD_SERVICE,
                                            ) as ClipboardManager
                                            clipboard.setPrimaryClip(
                                                ClipData.newPlainText(
                                                    context.getString(R.string.assistant_label),
                                                    message.text,
                                                ),
                                            )
                                            showSnackbar(R.string.copy_success)
                                        },
                                        onDelete = { messageToDelete = message },
                                        onPin = {
                                            if (message.demo) {
                                                demoSession = demoSession?.map {
                                                    if (it.id == message.id) {
                                                        it.copy(pinned = !it.pinned)
                                                    } else it
                                                }
                                            } else {
                                                updateStore {
                                                    it.setMessagePinned(message.id, !message.pinned)
                                                }
                                            }
                                            showSnackbar(
                                                if (message.pinned) R.string.message_unpinned
                                                else R.string.message_pinned,
                                            )
                                        },
                                    )
                                }
                                if (showResponseNotice || isGeneratingReply) {
                                    item(key = "response-notice") {
                                        ResponseNotice(
                                            text = context.getString(
                                                if (isGeneratingReply) R.string.api_generating
                                                else R.string.ai_unavailable,
                                            ),
                                        )
                                    }
                                }
                            }
                        }

                        if (messages.isNotEmpty() && !isNearBottom) {
                            JumpToLatest(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(bottom = 12.dp),
                                onClick = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(
                                            if (showResponseNotice) messages.size else messages.lastIndex,
                                        )
                                    }
                                },
                            )
                        }
                    }

                    if (voiceMiniPlayerVisible) {
                        Text(
                            stringResource(R.string.voice_text_input_unavailable),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Composer(
                        draft = draft,
                        onDraftChange = { draft = it },
                        onSend = sendMessage,
                        onVoice = { currentPage = AppPage.Voice },
                        onAttach = { showAttachmentOptions = true },
                        enabled = !voiceMiniPlayerVisible,
                        sendEnabled = !voiceMiniPlayerVisible && (draft.isNotBlank() &&
                            isStorageLoaded && storageError == null && !isGeneratingReply ||
                            pendingAttachments.isNotEmpty() &&
                            isStorageLoaded && storageError == null && !isGeneratingReply),
                        attachments = pendingAttachments,
                        onRemoveAttachment = { id ->
                            pendingAttachments.removeAll { it.id == id }
                        },
                    )
                }
                }
            }

            if (showAttachmentOptions) {
                AttachmentOptionsSheet(
                    onDismiss = { showAttachmentOptions = false },
                    onTakePhoto = {
                        showAttachmentOptions = false
                        capturePhoto.launch(null)
                    },
                    onChooseImage = {
                        showAttachmentOptions = false
                        chooseImage.launch(arrayOf("image/*"))
                    },
                    onChooseFile = {
                        showAttachmentOptions = false
                        chooseFile.launch(
                            arrayOf("image/*", "application/pdf", "text/plain"),
                        )
                    },
                )
            }

            AnimatedVisibility(
                visible = currentPage != AppPage.Chat,
                modifier = Modifier.fillMaxSize(),
                enter = if (reduceAnimations) EnterTransition.None
                else fadeIn(tween(150)) + slideInVertically(tween(170)) { it / 18 },
                exit = if (reduceAnimations) ExitTransition.None
                else fadeOut(tween(120)) + slideOutVertically(tween(140)) { it / 18 },
            ) {
                when (currentPage) {
                    AppPage.Chat -> Unit
                    AppPage.Settings -> SettingsScreen(
                        selectedTab = selectedSettingsTab,
                        onTabSelected = {
                            selectedSettingsTab = it
                            if (it == SettingsTab.Memory) currentPage = AppPage.Memory
                        },
                        onBack = { currentPage = AppPage.Chat },
                        onOpenMemory = { currentPage = AppPage.Memory },
                        reduceAnimations = reduceAnimations,
                        onReduceAnimationsChange = { enabled ->
                            reduceAnimations = enabled
                            userPreferences = userPreferences.copy(reduceAnimations = enabled).also {
                                it.write(context)
                            }
                        },
                        isIncognito = isIncognito,
                        onIncognitoChange = { enabled ->
                            if (enabled == isIncognito || !isStorageLoaded) return@SettingsScreen
                            if (enabled) {
                                coroutineScope.launch {
                                    try {
                                        repository.awaitIdle()
                                        isIncognito = true
                                        localStore = session.enableIncognito()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        storageError = context.getString(R.string.storage_write_error)
                                    }
                                }
                            } else {
                                coroutineScope.launch {
                                    try {
                                        val persisted = repository.load()
                                        localStore = session.disableIncognito(persisted)
                                        selectedConversationId = persisted.activeConversationId
                                            ?: persisted.conversations.maxByOrNull { it.updatedAt }?.id
                                        isIncognito = false
                                        storageError = null
                                        storageBytes = repository.storageBytes()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (error: Exception) {
                                        storageError = error.message
                                            ?: context.getString(R.string.storage_read_error)
                                    }
                                }
                            }
                        },
                        apiKeyConfigured = !geminiApiKey.isNullOrBlank(),
                        apiModelsStatus = if (isLoadingModels) {
                            context.getString(R.string.api_models_loading)
                        } else {
                            apiModelsStatus
                        },
                        onSaveApiKey = saveApiKey,
                        onRemoveApiKey = removeApiKey,
                        onRefreshModels = refreshModels,
                        preferences = userPreferences,
                        onPreferencesChange = { updated ->
                            userPreferences = updated
                            updated.write(context)
                        },
                        onAppLanguageChange = { language ->
                            val updated = userPreferences.copy(appLanguage = language)
                            updated.write(context)
                            userPreferences = updated
                            (context as? android.app.Activity)?.recreate()
                        },
                        onOpenLiveVoice = { currentPage = AppPage.Voice },
                    )
                    AppPage.Voice -> Unit
                    AppPage.Memory -> MemoryScreen(
                        store = localStore,
                        storageBytes = storageBytes,
                        storageError = storageError,
                        isIncognito = isIncognito,
                        onOpenConversation = { conversationId ->
                            demoSession = null
                            selectedConversationId = conversationId
                            currentPage = AppPage.Chat
                            updateStore { it.copy(activeConversationId = conversationId) }
                        },
                        onSaveMemoryItem = { memoryItem ->
                            updateStore { store ->
                                store.copy(
                                    memoryItems = store.memoryItems
                                        .filterNot { it.id == memoryItem.id } + memoryItem,
                                )
                            }
                        },
                        onDeleteConversation = { id ->
                            updateStore { it.deleteConversation(id) }
                            pruneUnreferencedAttachments()
                            if (selectedConversationId == id) selectedConversationId = null
                        },
                        onDeleteMemoryItem = { id ->
                            updateStore { it.deleteMemoryItem(id) }
                        },
                        onClearAll = {
                            operationGeneration += 1
                            isStorageLoaded = false
                            coroutineScope.launch {
                                try {
                                    repository.awaitIdle()
                                    repository.clear()
                                    attachmentStorage.deleteAll()
                                    val empty = LocalStore()
                                    if (isIncognito) {
                                        session.disableIncognito(empty)
                                        localStore = session.enableIncognito()
                                    } else {
                                        localStore = session.acceptPersistedUpdate(empty)
                                    }
                                    selectedConversationId = null
                                    demoSession = null
                                    storageError = null
                                    storageBytes = repository.storageBytes()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    storageError = error.message
                                        ?: context.getString(R.string.storage_write_error)
                                } finally {
                                    isStorageLoaded = true
                                }
                            }
                        },
                        onResetCorruptStorage = {
                            operationGeneration += 1
                            isStorageLoaded = false
                            coroutineScope.launch {
                                try {
                                    repository.awaitIdle()
                                    repository.clear()
                                    attachmentStorage.deleteAll()
                                    val empty = LocalStore()
                                    if (isIncognito) {
                                        session.disableIncognito(empty)
                                        localStore = session.enableIncognito()
                                    } else {
                                        localStore = session.acceptPersistedUpdate(empty)
                                    }
                                    selectedConversationId = null
                                    storageError = null
                                    storageBytes = repository.storageBytes()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    storageError = error.message
                                        ?: context.getString(R.string.storage_write_error)
                                } finally {
                                    isStorageLoaded = true
                                }
                            }
                        },
                        onBack = {
                            selectedSettingsTab = SettingsTab.Api
                            currentPage = AppPage.Settings
                        },
                    )
                }
            }

            VoiceScreen(
                isFullScreen = currentPage == AppPage.Voice,
                showMiniPlayer = currentPage == AppPage.Chat,
                onMinimize = { currentPage = AppPage.Chat },
                onRestore = { currentPage = AppPage.Voice },
                onMiniPlayerVisibilityChange = { voiceMiniPlayerVisible = it },
                reduceAnimations = reduceAnimations,
                apiKey = geminiApiKey,
                availableModels = availableModels,
                preferences = userPreferences,
                onTranscript = saveLiveTranscript,
            )
        }
    }

    messageToDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (message.demo) {
                            demoSession = demoSession?.filterNot { it.id == message.id }
                        } else {
                            updateStore { it.deleteMessage(message.id) }
                            pruneUnreferencedAttachments()
                        }
                        messageToDelete = null
                        showSnackbar(R.string.message_deleted)
                    },
                ) {
                    Text(stringResource(R.string.delete_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = SurfaceRaised,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }
}

private enum class AppPage {
    Chat,
    Settings,
    Voice,
    Memory,
}

@Composable
private fun ChatHeader(
    selectedModel: String,
    availableModels: List<GeminiModel>,
    isLoadingModels: Boolean,
    apiKeyConfigured: Boolean,
    showModels: Boolean,
    onMenuClick: () -> Unit,
    onModelClick: () -> Unit,
    onModelDismiss: () -> Unit,
    onModelSelected: (String) -> Unit,
) {
    val apiStatusDescription = stringResource(
        if (apiKeyConfigured) R.string.api_key_saved else R.string.api_status_description,
    )
    val modelMenuDescription = stringResource(R.string.model_menu_description)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(22.dp),
        contentPadding = 7.dp,
        containerColor = Color(0xCC171716),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                GoldIconButton(
                    description = R.string.menu_description,
                    onClick = onMenuClick,
                ) {
                    UiIcon(IconKind.Menu, R.string.menu_description)
                }
            }

            Box(modifier = Modifier.weight(2f), contentAlignment = Alignment.Center) {
                Box {
                    GlassCapsule(
                        modifier = Modifier.semantics {
                            contentDescription = modelMenuDescription
                            role = Role.Button
                        },
                        onClick = onModelClick,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides LayoutDirection.Rtl,
                            ) {
                                Text(
                                    text = selectedModel.ifBlank {
                                        stringResource(R.string.choose_model)
                                    },
                                    maxLines = 1,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = TextPrimary,
                                )
                            }
                            UiIcon(IconKind.ChevronDown, R.string.model_menu_description, size = 15.dp)
                        }
                    }
                    DropdownMenu(expanded = showModels, onDismissRequest = onModelDismiss) {
                        if (isLoadingModels) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.api_models_loading)) },
                                enabled = false,
                                onClick = {},
                            )
                        } else if (availableModels.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            if (apiKeyConfigured) R.string.api_models_empty
                                            else R.string.api_key_missing,
                                        ),
                                    )
                                },
                                enabled = false,
                                onClick = {},
                            )
                        }
                        availableModels.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.displayName) },
                                onClick = {
                                    onModelSelected(model.id)
                                },
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                GlassCapsule(
                    modifier = Modifier.semantics {
                        contentDescription = apiStatusDescription
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        UiIcon(
                            IconKind.Status,
                            R.string.api_status_description,
                            tint = MutedGold,
                            size = 12.dp,
                        )
                        CompositionLocalProvider(
                            LocalLayoutDirection provides LayoutDirection.Rtl,
                        ) {
                            Text(
                                text = stringResource(
                                    if (apiKeyConfigured) R.string.api_key_saved
                                    else R.string.api_not_configured,
                                ),
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyChat(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                color = Color(0xFF211D15),
                border = androidx.compose.foundation.BorderStroke(1.dp, MutedGold.copy(alpha = 0.55f)),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    UiIcon(
                        IconKind.Chat,
                        R.string.empty_icon_description,
                        tint = Gold,
                        size = 30.dp,
                    )
                }
            }
            Text(
                text = stringResource(R.string.empty_title),
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.empty_subtitle),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF242118),
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    text = stringResource(R.string.local_only_badge),
                    color = MutedGold,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun IncognitoBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.dp, Color(0xFF69542D), RoundedCornerShape(14.dp)),
        color = Color(0xFF282116),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(
            text = stringResource(R.string.incognito_active_banner),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = Color(0xFFE4D4AF),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StorageErrorBanner(
    message: String,
    onOpenMemory: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .border(1.dp, Color(0xFF69542D), RoundedCornerShape(14.dp)),
        color = Color(0xFF282116),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                color = Color(0xFFE4D4AF),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onOpenMemory) {
                Text(stringResource(R.string.open_memory_to_repair), color = Gold)
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessage,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onPin: () -> Unit,
) {
    val bubbleAlignment =
        if (message.fromUser) Alignment.CenterStart else Alignment.CenterEnd
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .align(bubbleAlignment)
                .widthIn(max = 320.dp),
            horizontalAlignment = if (message.fromUser) Alignment.Start else Alignment.End,
        ) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(22.dp),
                contentPadding = 15.dp,
                containerColor = if (message.fromUser) Color(0xDD262117) else Color(0xDD1B1B1A),
                borderColor = if (message.fromUser) Gold.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.07f),
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    if (message.fromUser) R.string.user_label
                                    else R.string.assistant_label,
                                ),
                                color = if (message.fromUser) Gold else MutedGold,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (message.demo) DemoBadge()
                            if (message.pinned) {
                                Text(
                                    text = stringResource(R.string.pinned_label),
                                    color = MutedGold,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                        Text(
                            text = message.text,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 25.sp,
                        )
                        message.attachments.forEach { attachment ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF28251E),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    Gold.copy(alpha = 0.2f),
                                ),
                            ) {
                                Text(
                                    text = "${attachment.displayName} • ${
                                        android.text.format.Formatter.formatShortFileSize(
                                            LocalContext.current,
                                            attachment.sizeBytes,
                                        )
                                    }",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.padding(top = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MessageAction(IconKind.Copy, R.string.copy_description, onCopy)
                    MessageAction(
                        IconKind.Pin,
                        if (message.pinned) R.string.unpin_description else R.string.pin_description,
                        onPin,
                    )
                    MessageAction(IconKind.Delete, R.string.delete_description, onDelete)
                }
            }
        }
    }
}

@Composable
private fun DemoBadge() {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF39301B),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            text = stringResource(R.string.demo_badge),
            color = Gold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun MessageAction(
    kind: IconKind,
    @StringRes description: Int,
    onClick: () -> Unit,
) {
    GoldIconButton(
        description = description,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 2.dp),
    ) {
        UiIcon(kind, description, tint = TextSecondary, size = 18.dp)
    }
}

@Composable
private fun ResponseNotice(text: String) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF54452A), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF262116),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                UiIcon(IconKind.Info, R.string.response_status_description, tint = Gold, size = 19.dp)
                Text(
                    text = text,
                    color = Color(0xFFE4D4AF),
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 21.sp,
                )
            }
        }
    }
}

@Composable
private fun JumpToLatest(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceRaised,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDark),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                UiIcon(IconKind.ChevronDown, R.string.jump_to_latest_description, tint = Gold, size = 17.dp)
                Text(
                    text = stringResource(R.string.jump_to_latest),
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
    onAttach: () -> Unit,
    enabled: Boolean,
    sendEnabled: Boolean,
    attachments: List<LocalAttachment>,
    onRemoveAttachment: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            shape = RoundedCornerShape(26.dp),
            contentPadding = 7.dp,
            containerColor = Color(0xE2191918),
            borderColor = if (isFocused) Gold.copy(alpha = 0.48f)
            else Color.White.copy(alpha = 0.09f),
        ) {
            if (attachments.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    attachments.forEach { attachment ->
                        Surface(
                            modifier = Modifier.clickable {
                                onRemoveAttachment(attachment.id)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFF28251E),
                        ) {
                            Text(
                                text = "${attachment.displayName} ×",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                color = TextPrimary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
            GoldIconButton(
                description = R.string.voice_screen_description,
                onClick = onVoice,
                selected = false,
            ) {
                UiIcon(IconKind.Voice, R.string.voice_screen_description, tint = MutedGold)
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                TextField(
                    value = draft,
                    onValueChange = onDraftChange,
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.message_placeholder),
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    shape = RoundedCornerShape(22.dp),
                    maxLines = 4,
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF242321),
                        unfocusedContainerColor = Color(0xFF242321),
                        disabledContainerColor = Color(0xFF242321),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Gold,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                    ),
                )
            }

            GoldIconButton(
                description = R.string.send_description,
                onClick = onSend,
                enabled = sendEnabled,
                primary = true,
                modifier = Modifier.padding(bottom = 2.dp),
            ) {
                UiIcon(IconKind.Send, R.string.send_description, tint = Background)
            }

            GoldIconButton(
                description = R.string.attachment_description,
                onClick = onAttach,
                enabled = enabled,
            ) {
                UiIcon(IconKind.Attachment, R.string.attachment_description, tint = MutedGold)
            }
            }
        }
    }
}

private fun demoMessages(
    firstId: Int,
    userMessage: String,
    assistantMessage: String,
    followUp: String,
) = listOf(
    ChatMessage(
        id = "demo-$firstId",
        text = userMessage,
        fromUser = true,
        demo = true,
    ),
    ChatMessage(
        id = "demo-${firstId + 1}",
        text = assistantMessage,
        fromUser = false,
        demo = true,
    ),
    ChatMessage(
        id = "demo-${firstId + 2}",
        text = followUp,
        fromUser = true,
        demo = true,
    ),
)

private fun preferredTextModel(models: List<GeminiModel>): GeminiModel? =
    models.firstOrNull { it.id == "gemini-3.8-flash" && it.supportsText }
        ?: models.firstOrNull {
            it.supportsText &&
                it.id.contains("flash", ignoreCase = true) &&
                !it.id.contains("lite", ignoreCase = true)
        }
        ?: models.firstOrNull { it.supportsText }

private fun buildAssistantInstruction(preferences: UserPreferences): String = buildString {
    append("You are a helpful assistant.")
    append(" Use a ${preferences.tone} tone and act as a ${preferences.role}.")
    append(" Keep answers ${preferences.answerLength} unless the task needs more detail.")
    append(" Set response creativity to ${preferences.creativity}.")
    append(if (preferences.useEmoji) " Use emoji occasionally." else " Avoid unnecessary emoji.")
    preferences.customInstruction
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.let { append("\nAdditional user instruction: ").append(it) }
    append("\n")
    append(SUPPORTED_LANGUAGE_INSTRUCTION)
}

private fun importPickedAttachment(
    uri: Uri,
    context: Context,
    storage: AttachmentStorage,
    pending: MutableList<LocalAttachment>,
    scope: CoroutineScope,
    showMessage: (String) -> Unit,
) {
    scope.launch {
        try {
            val name = withContext(Dispatchers.IO) {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                }
            }
            val (attachment, _) = storage.importUri(
                resolver = context.contentResolver,
                uri = uri,
                suggestedName = name,
                mimeType = context.contentResolver.getType(uri),
            )
            addPendingAttachment(attachment, pending, showMessage, context)
        } catch (error: Exception) {
            showMessage(error.message ?: context.getString(R.string.attachment_import_failed))
        }
    }
}

private fun addPendingAttachment(
    attachment: LocalAttachment,
    pending: MutableList<LocalAttachment>,
    showMessage: (String) -> Unit,
    context: Context,
) {
    val alreadySelectedBytes = pending.sumOf { it.sizeBytes }
    if (pending.size >= LocalRepository.MAX_ATTACHMENTS_PER_MESSAGE) {
        showMessage(context.getString(R.string.attachment_count_limit))
        return
    }
    if (alreadySelectedBytes + attachment.sizeBytes > MAX_TOTAL_ATTACHMENT_BYTES) {
        showMessage(context.getString(R.string.attachment_total_size_limit))
        return
    }
    pending += attachment
}

private fun listIsNearBottom(
    state: androidx.compose.foundation.lazy.LazyListState,
    thresholdPx: Float,
): Boolean {
    val layoutInfo = state.layoutInfo
    if (layoutInfo.totalItemsCount == 0) return true
    val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return true
    val remainingItems = layoutInfo.totalItemsCount - 1 - lastVisibleItem.index
    val remainingPixels =
        layoutInfo.viewportEndOffset - (lastVisibleItem.offset + lastVisibleItem.size)
    return remainingItems <= 1 && remainingPixels <= thresholdPx
}

private const val MAX_TOTAL_ATTACHMENT_BYTES = 12L * 1024 * 1024

private enum class IconKind {
    Menu,
    ChevronDown,
    Status,
    Chat,
    Info,
    Copy,
    Pin,
    Delete,
    Voice,
    Send,
    Attachment,
}

@Composable
private fun UiIcon(
    kind: IconKind,
    @StringRes description: Int,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    size: androidx.compose.ui.unit.Dp = 22.dp,
) {
    val accessibleDescription = stringResource(description)
    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                contentDescription = accessibleDescription
                role = Role.Image
            },
    ) {
        val stroke = 1.8.dp.toPx()
        val left = this.size.width * 0.18f
        val right = this.size.width * 0.82f
        val top = this.size.height * 0.18f
        val bottom = this.size.height * 0.82f
        fun line(start: Offset, end: Offset, width: Float = stroke) {
            drawLine(tint, start, end, strokeWidth = width, cap = StrokeCap.Round)
        }

        when (kind) {
            IconKind.Menu -> {
                listOf(0.3f, 0.5f, 0.7f).forEach { y ->
                    line(
                        Offset(left, this.size.height * y),
                        Offset(right, this.size.height * y),
                    )
                }
            }
            IconKind.ChevronDown -> {
                line(Offset(left, this.size.height * 0.4f), Offset(this.size.width / 2, this.size.height * 0.62f))
                line(Offset(this.size.width / 2, this.size.height * 0.62f), Offset(right, this.size.height * 0.4f))
            }
            IconKind.Status -> drawCircle(tint, radius = this.size.minDimension * 0.38f)
            IconKind.Chat -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke * 2),
                    style = Stroke(width = stroke),
                )
                line(
                    Offset(this.size.width * 0.35f, bottom),
                    Offset(this.size.width * 0.27f, this.size.height * 0.94f),
                )
            }
            IconKind.Info -> {
                drawCircle(tint, radius = this.size.minDimension * 0.42f, style = Stroke(width = stroke))
                drawCircle(tint, radius = stroke * 0.65f, center = Offset(this.size.width / 2, this.size.height * 0.35f))
                line(
                    Offset(this.size.width / 2, this.size.height * 0.48f),
                    Offset(this.size.width / 2, this.size.height * 0.7f),
                )
            }
            IconKind.Copy -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(this.size.width * 0.3f, this.size.height * 0.2f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 0.52f, this.size.height * 0.58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke),
                    style = Stroke(width = stroke),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(this.size.width * 0.17f, this.size.height * 0.34f),
                    size = androidx.compose.ui.geometry.Size(this.size.width * 0.52f, this.size.height * 0.58f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(stroke),
                    style = Stroke(width = stroke),
                )
            }
            IconKind.Pin -> {
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.36f, this@Canvas.size.height * 0.2f)
                    lineTo(this@Canvas.size.width * 0.68f, this@Canvas.size.height * 0.2f)
                    lineTo(this@Canvas.size.width * 0.61f, this@Canvas.size.height * 0.47f)
                    lineTo(this@Canvas.size.width * 0.74f, this@Canvas.size.height * 0.61f)
                    lineTo(this@Canvas.size.width * 0.28f, this@Canvas.size.height * 0.61f)
                    lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.47f)
                    close()
                    moveTo(this@Canvas.size.width * 0.51f, this@Canvas.size.height * 0.61f)
                    lineTo(this@Canvas.size.width * 0.48f, this@Canvas.size.height * 0.88f)
                }
                drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            IconKind.Delete -> {
                line(Offset(this.size.width * 0.25f, this.size.height * 0.31f), Offset(this.size.width * 0.75f, this.size.height * 0.31f))
                line(Offset(this.size.width * 0.39f, this.size.height * 0.23f), Offset(this.size.width * 0.61f, this.size.height * 0.23f))
                line(Offset(this.size.width * 0.33f, this.size.height * 0.37f), Offset(this.size.width * 0.38f, bottom))
                line(Offset(this.size.width * 0.67f, this.size.height * 0.37f), Offset(this.size.width * 0.62f, bottom))
                line(Offset(this.size.width * 0.38f, bottom), Offset(this.size.width * 0.62f, bottom))
                line(Offset(this.size.width * 0.45f, this.size.height * 0.43f), Offset(this.size.width * 0.46f, this.size.height * 0.72f))
                line(Offset(this.size.width * 0.55f, this.size.height * 0.43f), Offset(this.size.width * 0.54f, this.size.height * 0.72f))
            }
            IconKind.Voice -> {
                val bars = listOf(0.35f, 0.55f, 0.78f, 0.55f, 0.35f)
                bars.forEachIndexed { index, height ->
                    val x = this.size.width * (0.22f + index * 0.14f)
                    line(
                        Offset(x, this.size.height * (0.5f - height * 0.32f)),
                        Offset(x, this.size.height * (0.5f + height * 0.32f)),
                        stroke * 1.3f,
                    )
                }
            }
            IconKind.Send -> {
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.15f, this@Canvas.size.height * 0.48f)
                    lineTo(this@Canvas.size.width * 0.85f, this@Canvas.size.height * 0.16f)
                    lineTo(this@Canvas.size.width * 0.62f, this@Canvas.size.height * 0.85f)
                    lineTo(this@Canvas.size.width * 0.48f, this@Canvas.size.height * 0.56f)
                    close()
                    moveTo(this@Canvas.size.width * 0.48f, this@Canvas.size.height * 0.56f)
                    lineTo(this@Canvas.size.width * 0.69f, this@Canvas.size.height * 0.34f)
                }
                drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
            IconKind.Attachment -> {
                val path = Path().apply {
                    moveTo(this@Canvas.size.width * 0.7f, this@Canvas.size.height * 0.37f)
                    lineTo(this@Canvas.size.width * 0.44f, this@Canvas.size.height * 0.68f)
                    close()
                    moveTo(this@Canvas.size.width * 0.69f, this@Canvas.size.height * 0.38f)
                    cubicTo(
                        this@Canvas.size.width * 0.92f,
                        this@Canvas.size.height * 0.61f,
                        this@Canvas.size.width * 0.58f,
                        this@Canvas.size.height * 0.95f,
                        this@Canvas.size.width * 0.33f,
                        this@Canvas.size.height * 0.72f,
                    )
                    lineTo(this@Canvas.size.width * 0.55f, this@Canvas.size.height * 0.44f)
                    cubicTo(
                        this@Canvas.size.width * 0.67f,
                        this@Canvas.size.height * 0.3f,
                        this@Canvas.size.width * 0.82f,
                        this@Canvas.size.height * 0.44f,
                        this@Canvas.size.width * 0.7f,
                        this@Canvas.size.height * 0.58f,
                    )
                }
                drawPath(path, tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
            }
        }
    }
}
