package com.nico2.app

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.text.style.TextOverflow
import com.nico2.app.ui.components.AppBackground
import com.nico2.app.ui.components.GlassCard
import com.nico2.app.ui.components.GlassCapsule
import com.nico2.app.ui.components.GoldButton
import com.nico2.app.ui.components.GlowDivider
import com.nico2.app.ui.components.NicoPalette
import com.nico2.app.ui.components.SectionHeader
import com.nico2.app.ui.components.OrbVisualState
import com.nico2.app.ui.components.PremiumLivingOrb

internal enum class SettingsTab {
    Api,
    Personality,
    Audio,
    Memory,
}

private val Gold = NicoPalette.Gold
private val MutedGold = NicoPalette.MutedGold
private val Ink = NicoPalette.Background
private val Card = NicoPalette.Charcoal
private val Raised = NicoPalette.Raised
private val Outline = Color(0xFF35332D)
private val MainText = NicoPalette.WarmWhite
private val SecondaryText = NicoPalette.WarmGray

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var current: Context? = this
    while (current != null) {
        if (current is LifecycleOwner) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}

@Composable
internal fun MainDrawer(
    onSelectTab: (SettingsTab) -> Unit,
    onShowDemo: () -> Unit,
    onNewChat: () -> Unit,
) {
    AppBackground(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GlassCard(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            contentPadding = 14.dp,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    color = Color(0xFF342A17),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MutedGold.copy(alpha = 0.6f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✦", color = Gold, style = MaterialTheme.typography.titleLarge)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = stringResource(R.string.drawer_title),
                        color = MainText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.drawer_subtitle),
                        color = MutedGold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        SectionHeader(title = stringResource(R.string.settings_title))
        DrawerItem(R.string.settings_api) { onSelectTab(SettingsTab.Api) }
        DrawerItem(R.string.settings_personality) { onSelectTab(SettingsTab.Personality) }
        DrawerItem(R.string.settings_audio) { onSelectTab(SettingsTab.Audio) }
        DrawerItem(R.string.settings_memory) { onSelectTab(SettingsTab.Memory) }

        Spacer(Modifier.height(14.dp))
        Spacer(Modifier.height(8.dp))
        SectionHeader(title = stringResource(R.string.drawer_chat_section))
        DrawerItem(R.string.show_demo) { onShowDemo() }
        DrawerItem(R.string.clear_chat) { onNewChat() }
    }
    }
}

@Composable
private fun DrawerItem(@androidx.annotation.StringRes label: Int, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = {
            Text(stringResource(label), color = MainText)
        },
        selected = false,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.NavigationDrawerItemDefaults.colors(
            unselectedContainerColor = Card,
            unselectedTextColor = MainText,
        ),
    )
}

@Composable
internal fun SettingsScreen(
    selectedTab: SettingsTab,
    onTabSelected: (SettingsTab) -> Unit,
    onBack: () -> Unit,
    onOpenMemory: () -> Unit,
    reduceAnimations: Boolean,
    onReduceAnimationsChange: (Boolean) -> Unit,
    isIncognito: Boolean,
    onIncognitoChange: (Boolean) -> Unit,
    apiKeyConfigured: Boolean,
    apiModelsStatus: String,
    onSaveApiKey: (String) -> Unit,
    onRemoveApiKey: () -> Unit,
    onRefreshModels: () -> Unit,
    preferences: UserPreferences,
    onPreferencesChange: (UserPreferences) -> Unit,
    onAppLanguageChange: (String) -> Unit,
    onOpenLiveVoice: () -> Unit,
) {
    AppBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
                contentPadding = 8.dp,
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButtonLike(
                        label = stringResource(R.string.back_to_chat),
                        onClick = onBack,
                    )
                    Text(
                        text = stringResource(R.string.settings_title),
                        modifier = Modifier.weight(1f),
                        color = MainText,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(86.dp))
                }
            }

            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                contentPadding = 7.dp,
                shape = RoundedCornerShape(22.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsTab.entries.forEach { tab ->
                        val label = when (tab) {
                            SettingsTab.Api -> stringResource(R.string.settings_api)
                            SettingsTab.Personality -> stringResource(R.string.settings_personality)
                            SettingsTab.Audio -> stringResource(R.string.settings_audio)
                            SettingsTab.Memory -> stringResource(R.string.settings_memory)
                        }
                        Surface(
                            modifier = Modifier
                                .defaultMinSize(minHeight = 48.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(
                                    role = Role.Tab,
                                    onClick = {
                                        if (tab == SettingsTab.Memory) onOpenMemory()
                                        else onTabSelected(tab)
                                    },
                                )
                                .semantics { contentDescription = label },
                            shape = RoundedCornerShape(18.dp),
                            color = if (selectedTab == tab) Color(0xFF352C1B) else Card,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedTab == tab) MutedGold else Outline,
                            ),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                color = if (selectedTab == tab) Gold else SecondaryText,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingCard {
                AppLanguageSettings(
                    language = preferences.appLanguage,
                    onLanguageChange = onAppLanguageChange,
                )
            }
            SettingCard {
                SettingSwitch(
                    title = stringResource(R.string.incognito_mode),
                    detail = stringResource(
                        if (isIncognito) R.string.incognito_enabled_detail
                        else R.string.incognito_disabled_detail,
                    ),
                    checked = isIncognito,
                    onCheckedChange = onIncognitoChange,
                )
            }
            when (selectedTab) {
                SettingsTab.Api -> ApiSettings(
                    apiKeyConfigured = apiKeyConfigured,
                    status = apiModelsStatus,
                    onSaveApiKey = onSaveApiKey,
                    onRemoveApiKey = onRemoveApiKey,
                    onRefreshModels = onRefreshModels,
                )
                SettingsTab.Personality -> PersonalitySettings(preferences, onPreferencesChange)
                SettingsTab.Audio -> AudioSettings(preferences, onPreferencesChange, onOpenLiveVoice)
                SettingsTab.Memory -> Unit
            }

            SettingCard {
                SettingSwitch(
                    title = stringResource(R.string.reduce_animations),
                    detail = stringResource(R.string.reduce_animations_detail),
                    checked = reduceAnimations,
                    onCheckedChange = onReduceAnimationsChange,
                )
            }
        }
    }
}
}

@Composable
private fun ApiSettings(
    apiKeyConfigured: Boolean,
    status: String,
    onSaveApiKey: (String) -> Unit,
    onRemoveApiKey: () -> Unit,
    onRefreshModels: () -> Unit,
) {
    var apiKeyDraft by remember { mutableStateOf("") }
    SettingCard {
        SectionHeader(
            title = stringResource(R.string.api_status_title),
            subtitle = stringResource(R.string.api_inactive_notice),
        )
        GlowDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(9.dp)
                    .background(MutedGold, CircleShape),
            )
            Spacer(Modifier.width(9.dp))
            Text(
                stringResource(
                    if (apiKeyConfigured) R.string.api_key_saved
                    else R.string.api_key_missing,
                ),
                color = SecondaryText,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.api_key_security_notice),
            color = SecondaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.api_key_input_label)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        Text(status, color = SecondaryText, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = {
                onSaveApiKey(apiKeyDraft.trim())
                apiKeyDraft = ""
            },
            enabled = apiKeyDraft.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                disabledContentColor = SecondaryText,
            ),
        ) {
            Text(stringResource(R.string.api_key_button))
        }
        OutlinedButton(
            onClick = onRefreshModels,
            enabled = apiKeyConfigured,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.api_models_refresh))
        }
        OutlinedButton(
            onClick = onRemoveApiKey,
            enabled = apiKeyConfigured,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.api_key_remove))
        }
    }
}

@Composable
private fun AppLanguageSettings(
    language: String,
    onLanguageChange: (String) -> Unit,
) {
    val labels = listOf(
        stringResource(R.string.app_language_persian),
        stringResource(R.string.app_language_english),
        stringResource(R.string.app_language_turkish),
    )
    val codes = listOf("fa", "en", "tr")
    SettingDropdown(
        label = stringResource(R.string.app_language),
        selected = labels[codes.indexOf(language).takeIf { it >= 0 } ?: 0],
        options = labels,
        onSelected = { selected -> onLanguageChange(codes[labels.indexOf(selected)]) },
    )
}

@Composable
private fun PersonalitySettings(
    preferences: UserPreferences,
    onPreferencesChange: (UserPreferences) -> Unit,
) {
    var tone by remember(preferences.tone) { mutableStateOf(preferences.tone) }
    var role by remember(preferences.role) { mutableStateOf(preferences.role) }
    var answerLength by remember(preferences.answerLength) {
        mutableStateOf(preferences.answerLength)
    }
    var useEmoji by remember(preferences.useEmoji) { mutableStateOf(preferences.useEmoji) }
    var creativity by remember(preferences.creativity) {
        mutableFloatStateOf(preferences.creativity)
    }
    var customInstruction by remember(preferences.customInstruction) {
        mutableStateOf(preferences.customInstruction)
    }

    val tones = listOf(
        stringResource(R.string.tone_friendly),
        stringResource(R.string.tone_formal),
        stringResource(R.string.tone_casual),
    )
    val roles = listOf(
        stringResource(R.string.role_helper),
        stringResource(R.string.role_teacher),
        stringResource(R.string.role_companion),
    )
    val lengths = listOf(
        stringResource(R.string.length_short),
        stringResource(R.string.length_medium),
        stringResource(R.string.length_long),
    )

    SettingCard {
        SectionHeader(
            title = stringResource(R.string.personality_settings_title),
            subtitle = stringResource(R.string.settings_temporary),
        )
        GlowDivider()
        SettingDropdown(
            stringResource(R.string.personality_tone),
            optionLabel(tone, listOf("friendly", "formal", "casual"), tones),
            tones,
        ) {
            tone = listOf("friendly", "formal", "casual")[tones.indexOf(it)]
            onPreferencesChange(preferences.copy(tone = tone))
        }
        SettingDropdown(
            stringResource(R.string.personality_role),
            optionLabel(role, listOf("helper", "teacher", "companion"), roles),
            roles,
        ) {
            role = listOf("helper", "teacher", "companion")[roles.indexOf(it)]
            onPreferencesChange(preferences.copy(role = role))
        }
        SettingDropdown(
            stringResource(R.string.personality_length),
            optionLabel(answerLength, listOf("short", "medium", "long"), lengths),
            lengths,
        ) {
            answerLength = listOf("short", "medium", "long")[lengths.indexOf(it)]
            onPreferencesChange(preferences.copy(answerLength = answerLength))
        }
        SettingSwitch(
            title = stringResource(R.string.use_emoji),
            detail = stringResource(R.string.settings_temporary),
            checked = useEmoji,
            onCheckedChange = {
                useEmoji = it
                onPreferencesChange(preferences.copy(useEmoji = it))
            },
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.creativity),
                    modifier = Modifier.weight(1f),
                    color = MainText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${(creativity * 100).toInt()}٪",
                    color = MutedGold,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Slider(
                value = creativity,
                onValueChange = {
                    creativity = it
                    onPreferencesChange(preferences.copy(creativity = it))
                },
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Gold,
                    activeTrackColor = MutedGold,
                    inactiveTrackColor = Outline,
                ),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(
                stringResource(R.string.custom_instruction),
                color = MainText,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextField(
                value = customInstruction,
                onValueChange = {
                    customInstruction = it
                    onPreferencesChange(preferences.copy(customInstruction = it))
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.custom_instruction_hint),
                        color = SecondaryText,
                    )
                },
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Raised,
                    unfocusedContainerColor = Raised,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = MainText,
                    unfocusedTextColor = MainText,
                    cursorColor = Gold,
                ),
            )
        }
        Text(
            stringResource(R.string.settings_temporary),
            color = SecondaryText,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun optionLabel(id: String, ids: List<String>, labels: List<String>): String =
    labels.getOrElse(ids.indexOf(id).takeIf { it >= 0 } ?: 0) { labels.first() }

@Composable
private fun AudioSettings(
    preferences: UserPreferences,
    onPreferencesChange: (UserPreferences) -> Unit,
    onOpenLiveVoice: () -> Unit,
) {
    var language by remember(preferences.audioLanguage) {
        mutableStateOf(preferences.audioLanguage)
    }
    var voice by remember(preferences.audioVoice) {
        mutableStateOf(preferences.audioVoice)
    }
    var speed by remember(preferences.audioSpeed) { mutableFloatStateOf(preferences.audioSpeed) }
    var pitch by remember(preferences.audioPitch) { mutableFloatStateOf(preferences.audioPitch) }
    val languages = listOf(
        stringResource(R.string.language_persian),
        stringResource(R.string.language_turkish),
        stringResource(R.string.language_english),
    )
    val voices = listOf(
        stringResource(R.string.voice_option_one),
        stringResource(R.string.voice_option_two),
    )
    SettingCard {
        SectionHeader(
            title = stringResource(R.string.audio_settings_title),
            subtitle = stringResource(R.string.audio_demo_notice),
        )
        GlowDivider()
        SettingDropdown(
            stringResource(R.string.audio_language),
            optionLabel(language, listOf("persian", "turkish", "english"), languages),
            languages,
        ) {
            language = listOf("persian", "turkish", "english")[languages.indexOf(it)]
            onPreferencesChange(preferences.copy(audioLanguage = language))
        }
        SettingDropdown(
            stringResource(R.string.audio_voice),
            optionLabel(voice, listOf("voice_one", "voice_two"), voices),
            voices,
        ) {
            voice = listOf("voice_one", "voice_two")[voices.indexOf(it)]
            onPreferencesChange(preferences.copy(audioVoice = voice))
        }
        DemoSlider(stringResource(R.string.audio_speed), speed) {
            speed = it
            onPreferencesChange(preferences.copy(audioSpeed = it))
        }
        DemoSlider(stringResource(R.string.audio_pitch), pitch) {
            pitch = it
            onPreferencesChange(preferences.copy(audioPitch = it))
        }
        Button(
            onClick = onOpenLiveVoice,
            enabled = true,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = Raised,
                disabledContentColor = SecondaryText,
            ),
        ) {
            Text(stringResource(R.string.audio_test_disabled))
        }
    }
}

@Composable
private fun DemoSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, modifier = Modifier.weight(1f), color = MainText)
            Text(
                "${(value * 100).toInt()}٪",
                color = MutedGold,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = androidx.compose.material3.SliderDefaults.colors(
                thumbColor = Gold,
                activeTrackColor = MutedGold,
                inactiveTrackColor = Outline,
            ),
        )
    }
}

@Composable
private fun SettingDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MainText, style = MaterialTheme.typography.bodyMedium)
        Box {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(role = Role.Button) { expanded = true }
                    .semantics { contentDescription = label },
                shape = RoundedCornerShape(14.dp),
                color = Raised,
                border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
            ) {
                Text(
                    text = selected.ifBlank { stringResource(R.string.select_option) },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    color = if (selected.isBlank()) SecondaryText else MainText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = MainText, style = MaterialTheme.typography.bodyMedium)
            Text(detail, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingCard(content: @Composable ColumnScope.() -> Unit) {
    GlassCard(
        borderColor = NicoPalette.Hairline,
        containerColor = Color(0xDD191918),
        content = content,
    )
}

@Composable
internal fun VoiceScreen(
    onExit: () -> Unit,
    reduceAnimations: Boolean,
    apiKey: String?,
    availableModels: List<GeminiModel>,
    preferences: UserPreferences,
    onTranscript: (Boolean, String) -> Unit,
) {
    var muted by remember { mutableStateOf(false) }
    var speakerOutput by remember { mutableStateOf(true) }
    var isLiveActive by remember { mutableStateOf(false) }
    var isCameraActive by remember { mutableStateOf(false) }
    var liveState by remember { mutableStateOf(GeminiLiveState.Closed) }
    var liveDetail by remember { mutableStateOf<String?>(null) }
    var liveSession by remember { mutableStateOf<GeminiLiveSession?>(null) }
    var activeLiveSessionId by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = context.findLifecycleOwner()
    val liveModels = remember(availableModels) {
        GeminiLiveSession.liveFallbackOrder(availableModels)
    }
    val latestTranscriptCallback by rememberUpdatedState(onTranscript)
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            isLiveActive = true
            liveDetail = null
        } else {
            liveState = GeminiLiveState.Error
            liveDetail = contextualLiveFailure(
                context = context,
                category = LiveFailureCategory.Audio,
                stage = LiveFailureStage.Audio,
                modelId = liveModels.firstOrNull()?.id,
                details = context.getString(R.string.voice_permission_denied),
            )
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            isCameraActive = true
            liveDetail = null
        } else {
            liveDetail = contextualLiveFailure(
                context = context,
                category = LiveFailureCategory.Device,
                stage = LiveFailureStage.Audio,
                modelId = liveModels.firstOrNull()?.id,
                details = context.getString(R.string.voice_camera_permission_denied),
            )
        }
    }

    DisposableEffect(isLiveActive, apiKey, liveModels) {
        val sessionId = UUID.randomUUID().toString()
        val session = if (isLiveActive && !apiKey.isNullOrBlank()) {
            GeminiLiveSession(
                context = context,
                apiKey = apiKey,
                availableModels = liveModels,
                preferences = preferences,
                sessionId = sessionId,
                onState = { state, detail ->
                    if (activeLiveSessionId == sessionId) {
                        liveState = state
                        liveDetail = detail
                        if (state == GeminiLiveState.Error || state == GeminiLiveState.Closed) {
                            isLiveActive = false
                            isCameraActive = false
                        }
                    }
                },
                onTranscript = { isUser, text ->
                    if (activeLiveSessionId == sessionId) {
                        latestTranscriptCallback(isUser, text)
                    }
                },
            )
        } else {
            null
        }
        activeLiveSessionId = session?.sessionId
        liveSession = session
        session?.start()
        onDispose {
            if (activeLiveSessionId == sessionId) activeLiveSessionId = null
            session?.close()
            if (liveSession === session) liveSession = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && isLiveActive) {
                isLiveActive = false
                isCameraActive = false
                liveDetail = context.getString(R.string.voice_stopped_background)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val startLive: () -> Unit = {
        when {
            apiKey.isNullOrBlank() -> {
                liveState = GeminiLiveState.Error
                liveDetail = contextualLiveFailure(
                    context = context,
                    category = LiveFailureCategory.ApiKey,
                    stage = LiveFailureStage.Connection,
                    modelId = liveModels.firstOrNull()?.id,
                    details = context.getString(R.string.api_key_missing),
                )
            }
            liveModels.isEmpty() -> {
                liveState = GeminiLiveState.Error
                liveDetail = contextualLiveFailure(
                    context = context,
                    category = LiveFailureCategory.Model,
                    stage = LiveFailureStage.Setup,
                    modelId = null,
                    details = context.getString(R.string.voice_live_unavailable),
                )
            }
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> {
                isLiveActive = true
                liveDetail = null
            }
            else -> microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    AppBackground(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020304)),
    ) {
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentPadding = 8.dp,
            shape = RoundedCornerShape(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                VoiceControl(
                    title = stringResource(R.string.back_to_chat),
                    symbol = "‹",
                    modifier = Modifier.size(48.dp),
                    onClick = onExit,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        stringResource(R.string.voice_screen_title),
                        color = MainText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.voice_demo_status),
                        color = MutedGold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.size(48.dp))
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PremiumLivingOrb(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = when (liveState) {
                        GeminiLiveState.Connecting, GeminiLiveState.Configuring ->
                            OrbVisualState.Connecting
                        GeminiLiveState.Listening -> OrbVisualState.Listening
                        GeminiLiveState.Responding -> OrbVisualState.Responding
                        else -> OrbVisualState.Calm
                    },
                    reducedMotion = reduceAnimations,
                )
            }

            if (isCameraActive && isLiveActive &&
                (liveState == GeminiLiveState.Listening ||
                    liveState == GeminiLiveState.Responding) &&
                lifecycleOwner != null
            ) {
                LiveCameraPreview(
                    lifecycleOwner = lifecycleOwner,
                    liveSession = liveSession,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                        .size(width = 124.dp, height = 164.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, MutedGold, RoundedCornerShape(18.dp))
                        .semantics {
                            contentDescription = context.getString(R.string.voice_camera_preview)
                        },
                    onError = { error ->
                        val category = when (error) {
                            LiveCameraError.CameraUnavailable -> LiveFailureCategory.Device
                            LiveCameraError.FrameSendFailed -> LiveFailureCategory.Server
                        }
                        val stage = when (error) {
                            LiveCameraError.CameraUnavailable -> LiveFailureStage.Audio
                            LiveCameraError.FrameSendFailed -> LiveFailureStage.Session
                        }
                        val detail = context.getString(
                            when (error) {
                                LiveCameraError.CameraUnavailable -> R.string.voice_camera_unavailable
                                LiveCameraError.FrameSendFailed -> R.string.voice_camera_send_failed
                            },
                        )
                        liveDetail = contextualLiveFailure(
                            context = context,
                            category = category,
                            stage = stage,
                            modelId = liveModels.firstOrNull()?.id,
                            details = detail,
                        )
                        isCameraActive = false
                    },
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            GoldButton(
                label = stringResource(
                    if (isLiveActive) R.string.voice_live_active
                    else R.string.voice_live_start,
                ),
                onClick = {
                    if (isLiveActive) {
                        isLiveActive = false
                        isCameraActive = false
                        liveSession?.close()
                    } else {
                        startLive()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLiveActive,
            )
            Text(
                liveDetail ?: stringResource(
                    when {
                        isCameraActive &&
                            (liveState == GeminiLiveState.Listening ||
                                liveState == GeminiLiveState.Responding) ->
                            R.string.voice_camera_streaming
                        liveState == GeminiLiveState.Connecting -> R.string.voice_live_connecting
                        liveState == GeminiLiveState.Configuring -> R.string.voice_live_configuring
                        liveState == GeminiLiveState.Ready ||
                            liveState == GeminiLiveState.Listening ->
                            R.string.voice_live_ready
                        liveState == GeminiLiveState.Responding ->
                            R.string.voice_live_responding
                        liveState == GeminiLiveState.Error -> R.string.voice_live_unavailable
                        else -> R.string.voice_unavailable_notice
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState())
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF211D15))
                    .border(1.dp, Color(0xFF54452A), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                color = Color(0xFFE4D4AF),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                VoiceControl(
                    title = stringResource(R.string.voice_end),
                    symbol = "×",
                    modifier = Modifier.weight(1f),
                    emphasized = true,
                    onClick = onExit,
                )
                VoiceControl(
                    title = stringResource(if (muted) R.string.voice_unmute else R.string.voice_mute),
                    symbol = if (muted) "○" else "◖",
                    modifier = Modifier.weight(1f),
                    selected = muted,
                    onClick = {
                        muted = !muted
                        liveSession?.setMuted(muted)
                    },
                )
                VoiceControl(
                    title = stringResource(R.string.voice_output),
                    symbol = "♫",
                    modifier = Modifier.weight(1f),
                    selected = speakerOutput,
                    onClick = {
                        speakerOutput = !speakerOutput
                        liveSession?.setSpeakerOutput(speakerOutput)
                    },
                )
                VoiceControl(
                    title = stringResource(
                        if (isCameraActive) R.string.voice_camera_off else R.string.voice_camera,
                    ),
                    symbol = "□",
                    modifier = Modifier.weight(1f),
                    selected = isCameraActive,
                    onClick = {
                        if (!isLiveActive || liveSession == null) {
                            liveDetail = context.getString(R.string.voice_camera_requires_live)
                        } else if (isCameraActive) {
                            isCameraActive = false
                        } else if (context.checkSelfPermission(Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            isCameraActive = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                )
            }
            Text(
                text = stringResource(R.string.voice_controls_live),
                color = SecondaryText,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        }
    }
        }
}

@Composable
private fun VoiceControl(
    title: String,
    symbol: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 72.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = title
                role = Role.Button
            }
            .background(
                if (emphasized) Color(0xFF49301F)
                else Color(0xB51E1D1B),
            )
            .border(
                1.dp,
                if (selected || emphasized) MutedGold else Outline,
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 5.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            symbol,
            color = if (emphasized || selected) Gold else MainText,
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            title,
            color = if (emphasized || selected) Gold else SecondaryText,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
internal fun MemoryScreen(
    store: LocalStore,
    storageBytes: Long,
    storageError: String?,
    isIncognito: Boolean,
    onOpenConversation: (String) -> Unit,
    onSaveMemoryItem: (MemoryItem) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteMemoryItem: (String) -> Unit,
    onClearAll: () -> Unit,
    onResetCorruptStorage: () -> Unit,
    onBack: () -> Unit,
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ConversationFilter.All) }
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var addMemory by remember { mutableStateOf(false) }
    var memoryToDelete by remember { mutableStateOf<MemoryItem?>(null) }
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var clearConfirmationText by remember { mutableStateOf("") }
    var showResetConfirmation by remember { mutableStateOf(false) }
    val conversations = remember(store, search, filter) {
        store.searchConversations(search, filter)
    }

    AppBackground(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButtonLike(stringResource(R.string.back_to_settings), onBack)
            Text(
                stringResource(R.string.settings_memory),
                modifier = Modifier.weight(1f),
                color = MainText,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(100.dp))
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isIncognito) {
                item {
                    InfoCard(
                        text = stringResource(R.string.incognito_memory_notice),
                        highlight = true,
                    )
                }
            }
            if (storageError != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        InfoCard(text = storageError, highlight = true)
                        OutlinedButton(
                            onClick = { showResetConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.reset_corrupt_storage))
                        }
                    }
                }
            }
            item {
                SettingCard {
                    SectionHeader(
                        title = stringResource(R.string.memory_overview),
                        subtitle = stringResource(R.string.memory_private_storage),
                    )
                    GlowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.memory_conversations_count),
                            value = store.conversations.size.toString(),
                        )
                        StatCard(
                            modifier = Modifier.weight(1f),
                            title = stringResource(R.string.memory_messages_count),
                            value = store.messages.size.toString(),
                        )
                    }
                    Text(
                        stringResource(R.string.memory_file_size, formatStorageBytes(storageBytes)),
                        color = SecondaryText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        enabled = store.conversations.isNotEmpty() ||
                            store.messages.isNotEmpty() || store.memoryItems.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.clear_all_memory))
                    }
                }
            }

            item {
                SectionHeader(
                    title = stringResource(
                        if (filter == ConversationFilter.Pinned) R.string.memory_pinned_conversations
                        else R.string.memory_conversations_section,
                    ),
                    subtitle = stringResource(R.string.memory_conversations_detail),
                )
            }

            item {
                TextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            stringResource(R.string.memory_search_hint),
                            color = SecondaryText,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    colors = memoryTextFieldColors(),
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ConversationFilter.entries.forEach { option ->
                        val label = when (option) {
                            ConversationFilter.All -> stringResource(R.string.filter_all)
                            ConversationFilter.Pinned -> stringResource(R.string.filter_pinned)
                            ConversationFilter.Recent -> stringResource(R.string.filter_recent)
                            ConversationFilter.WithMessages -> stringResource(R.string.filter_with_messages)
                        }
                        FilterChip(label, filter == option) { filter = option }
                    }
                }
            }

            if (conversations.isEmpty()) {
                item {
                    InfoCard(text = stringResource(R.string.no_matching_conversations))
                }
            } else {
                items(conversations, key = { "conversation-${it.id}" }) { conversation ->
                    ConversationCard(
                        conversation = conversation,
                        messages = store.messages.filter { it.conversationId == conversation.id },
                        onOpen = { onOpenConversation(conversation.id) },
                        onDelete = { conversationToDelete = conversation },
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = stringResource(R.string.memory_knowledge),
                        subtitle = stringResource(R.string.memory_knowledge_detail),
                        modifier = Modifier.weight(1f),
                    )
                    GoldButton(
                        label = stringResource(R.string.add_memory_item),
                        onClick = { addMemory = true },
                    ) {
                        Text(
                            stringResource(R.string.add_memory_item),
                            color = Color(0xFF17130A),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            if (store.memoryItems.isEmpty()) {
                item {
                    InfoCard(text = stringResource(R.string.no_memory_items))
                }
            } else {
                items(store.memoryItems, key = { "memory-${it.id}" }) { item ->
                    MemoryItemCard(
                        item = item,
                        onEdit = { editingMemory = item },
                        onDelete = { memoryToDelete = item },
                        onEnabledChange = { enabled ->
                            onSaveMemoryItem(item.copy(enabled = enabled))
                        },
                    )
                }
            }
            }
        }
    }

    if (addMemory || editingMemory != null) {
        MemoryItemDialog(
            item = editingMemory,
            onDismiss = {
                addMemory = false
                editingMemory = null
            },
            onSave = { text, category ->
                val existing = editingMemory
                onSaveMemoryItem(
                    MemoryItem(
                        id = existing?.id ?: UUID.randomUUID().toString(),
                        text = text,
                        category = category,
                        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        enabled = existing?.enabled ?: true,
                    ),
                )
                addMemory = false
                editingMemory = null
            },
        )
    }

    memoryToDelete?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.delete_memory_title),
            message = stringResource(R.string.delete_memory_confirmation),
            confirmLabel = stringResource(R.string.delete_action),
            onDismiss = { memoryToDelete = null },
            onConfirm = {
                onDeleteMemoryItem(item.id)
                memoryToDelete = null
            },
        )
    }

    conversationToDelete?.let { conversation ->
        ConfirmDialog(
            title = stringResource(R.string.delete_conversation_title),
            message = stringResource(R.string.delete_conversation_confirmation),
            confirmLabel = stringResource(R.string.delete_action),
            onDismiss = { conversationToDelete = null },
            onConfirm = {
                onDeleteConversation(conversation.id)
                conversationToDelete = null
            },
        )
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showClearConfirmation = false
                clearConfirmationText = ""
            },
            title = { Text(stringResource(R.string.clear_all_memory)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.clear_all_confirmation))
                    TextField(
                        value = clearConfirmationText,
                        onValueChange = { clearConfirmationText = it },
                        placeholder = { Text(stringResource(R.string.clear_confirmation_hint)) },
                        singleLine = true,
                        colors = memoryTextFieldColors(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = clearConfirmationText == stringResource(R.string.clear_confirmation_phrase),
                    onClick = {
                        onClearAll()
                        showClearConfirmation = false
                        clearConfirmationText = ""
                    },
                ) {
                    Text(stringResource(R.string.clear_all_action))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showClearConfirmation = false
                    clearConfirmationText = ""
                }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            containerColor = Raised,
            titleContentColor = MainText,
            textContentColor = SecondaryText,
        )
    }

    if (showResetConfirmation) {
        ConfirmDialog(
            title = stringResource(R.string.reset_corrupt_storage),
            message = stringResource(R.string.reset_corrupt_confirmation),
            confirmLabel = stringResource(R.string.clear_all_action),
            onDismiss = { showResetConfirmation = false },
            onConfirm = {
                onResetCorruptStorage()
                showResetConfirmation = false
            },
        )
    }
    }

@Composable
private fun ConversationCard(
    conversation: Conversation,
    messages: List<Message>,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val conversationDeleteLabel = stringResource(R.string.delete_conversation_title)
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        contentPadding = 14.dp,
        containerColor = Color(0xDD1B1B19),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    modifier = Modifier.weight(1f),
                    color = MainText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.conversation_message_count, messages.size),
                    color = MutedGold,
                    style = MaterialTheme.typography.labelSmall,
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .semantics {
                            contentDescription = conversationDeleteLabel
                            role = Role.Button
                        },
                ) { Text("×", color = SecondaryText, style = MaterialTheme.typography.titleMedium) }
            }
            messages.lastOrNull()?.let {
                Text(
                    it.text,
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

@Composable
private fun MemoryItemCard(
    item: MemoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
) {
    SettingCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.category, color = MutedGold, style = MaterialTheme.typography.labelMedium)
                Text(
                    item.text,
                    color = MainText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(checked = item.enabled, onCheckedChange = onEnabledChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.edit_action)) }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.delete_action), color = Color(0xFFE28B73))
            }
        }
    }
}

@Composable
private fun MemoryItemDialog(
    item: MemoryItem?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var text by remember(item?.id) { mutableStateOf(item?.text.orEmpty()) }
    var category by remember(item?.id) { mutableStateOf(item?.category.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (item == null) R.string.add_memory_item_title
                    else R.string.edit_memory_item,
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it.take(LocalRepository.MAX_MEMORY_TEXT_LENGTH) },
                    placeholder = { Text(stringResource(R.string.memory_text_hint)) },
                    minLines = 2,
                    colors = memoryTextFieldColors(),
                )
                TextField(
                    value = category,
                    onValueChange = { category = it.take(LocalRepository.MAX_CATEGORY_LENGTH) },
                    placeholder = { Text(stringResource(R.string.memory_category_hint)) },
                    singleLine = true,
                    colors = memoryTextFieldColors(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank() && category.isNotBlank(),
                onClick = { onSave(text.trim(), category.trim()) },
            ) {
                Text(stringResource(R.string.save_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        containerColor = Raised,
        titleContentColor = MainText,
        textContentColor = SecondaryText,
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        containerColor = Raised,
        titleContentColor = MainText,
        textContentColor = SecondaryText,
    )
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFF352C1B) else Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) MutedGold else Outline),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = if (selected) Gold else SecondaryText,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun StatCard(modifier: Modifier, title: String, value: String) {
    Surface(modifier = modifier, color = Raised, shape = RoundedCornerShape(14.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(title, color = SecondaryText, style = MaterialTheme.typography.labelSmall)
            Text(value, color = Gold, style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun InfoCard(text: String, highlight: Boolean = false) {
    Surface(
        modifier = Modifier.border(
            1.dp,
            if (highlight) MutedGold else Outline,
            RoundedCornerShape(16.dp),
        ),
        shape = RoundedCornerShape(16.dp),
        color = if (highlight) Color(0xFF282116) else Card,
    ) {
        Text(
            text,
            modifier = Modifier.padding(14.dp),
            color = if (highlight) Color(0xFFE4D4AF) else SecondaryText,
            style = MaterialTheme.typography.bodySmall,
            lineHeight = 21.sp,
        )
    }
}

@Composable
private fun memoryTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Raised,
    unfocusedContainerColor = Raised,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = MainText,
    unfocusedTextColor = MainText,
    cursorColor = Gold,
)

private fun formatStorageBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
}

@Composable
private fun TextButtonLike(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        shape = RoundedCornerShape(14.dp),
        color = Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, Outline),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            color = Gold,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}
