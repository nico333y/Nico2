package com.nico2.app.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import com.nico2.app.R
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ModalBottomSheet

object NicoPalette {
    val Background = Color(0xFF090908)
    val Charcoal = Color(0xFF171715)
    val Raised = Color(0xFF201F1C)
    val Gold = Color(0xFFE8BE68)
    val MutedGold = Color(0xFFA98C56)
    val WarmWhite = Color(0xFFF4F0E7)
    val WarmGray = Color(0xFFB8B2A7)
    val Hairline = Color(0x36E8BE68)
}

@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0D0D0C),
                        NicoPalette.Background,
                        Color(0xFF0A0A09),
                    ),
                ),
            ),
        contentAlignment = contentAlignment,
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(250.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x1AE0B455), Color(0x080F0D08), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .size(220.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0x0DAD8740), Color(0x050F0D08), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        content()
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    contentPadding: Dp = 16.dp,
    containerColor: Color = Color(0xDD181817),
    borderColor: Color = NicoPalette.Hairline,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .shadow(4.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f))
            .border(BorderStroke(1.dp, borderColor), shape),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.045f)),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
fun GoldButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit = {
        Text(
            text = label,
            color = Color(0xFF17130A),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    },
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(
                    listOf(Color(0xFFF0D18E), NicoPalette.Gold, Color(0xFFC99945)),
                ) else Brush.horizontalGradient(
                    listOf(Color(0xFF49463F), Color(0xFF393732)),
                ),
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun GoldIconButton(
    @StringRes description: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    primary: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        label = "icon-button-press",
    )
    val accessibleDescription = stringResource(description)
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.55f
            }
            .clip(shape)
            .background(
                if (primary && enabled) Brush.linearGradient(
                    listOf(Color(0xFFF0D18E), NicoPalette.Gold, Color(0xFFC99945)),
                )
                else if (selected) Brush.linearGradient(
                    listOf(Color(0xFF42351F), Color(0xFF282219)),
                )
                else Brush.linearGradient(
                    listOf(Color(0xAA242320), Color(0xAA191918)),
                ),
            )
            .border(
                BorderStroke(
                    1.dp,
                if (selected) NicoPalette.MutedGold else Color.White.copy(alpha = 0.08f),
                ),
                shape,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription = accessibleDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 18.dp)
                    .clip(CircleShape)
                    .background(NicoPalette.Gold),
            )
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = NicoPalette.WarmWhite,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (trailing != null) Row(content = trailing)
        }
        if (subtitle != null) {
            Text(
                subtitle,
                modifier = Modifier.fillMaxWidth(),
                color = NicoPalette.WarmGray,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
            )
        }
    }
}

@Composable
fun GlowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, NicoPalette.Hairline, Color.White.copy(alpha = 0.1f), Color.Transparent),
                ),
            ),
    )
}

@Composable
fun GlassCapsule(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val capsuleModifier = modifier
            .clip(CircleShape)
            .background(Color(0xCC1B1A18))
            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .then(
                if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
                else Modifier,
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
    Row(
        modifier = capsuleModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AttachmentOptionsSheet(
    onDismiss: () -> Unit,
    onTakePhoto: () -> Unit,
    onChooseImage: () -> Unit,
    onChooseFile: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NicoPalette.Charcoal,
        contentColor = NicoPalette.WarmWhite,
        dragHandle = {
            Box(
                Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(NicoPalette.MutedGold.copy(alpha = 0.65f)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = stringResource(R.string.attachment_sheet_title),
                subtitle = stringResource(R.string.attachment_sheet_subtitle),
            )
            GlowDivider()
            AttachmentOption(R.string.attachment_take_photo, onTakePhoto)
            AttachmentOption(R.string.attachment_choose_photo, onChooseImage)
            AttachmentOption(R.string.attachment_choose_file, onChooseFile)
        }
    }
}

@Composable
private fun AttachmentOption(@StringRes label: Int, onClick: () -> Unit) {
    val title = stringResource(label)
    val status = stringResource(R.string.not_available_yet)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .background(Color(0xAA242320))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics {
                contentDescription = "$title، $status"
                role = Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("◇", color = NicoPalette.MutedGold, style = MaterialTheme.typography.titleLarge)
        Text(
            title,
            modifier = Modifier.weight(1f),
            color = NicoPalette.WarmGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.not_available_yet),
            color = NicoPalette.MutedGold,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
