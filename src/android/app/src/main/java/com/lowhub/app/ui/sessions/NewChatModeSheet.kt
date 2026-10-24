package com.lowhub.app.ui.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lowhub.app.R
import com.lowhub.app.ui.components.FrostedButton

/**
 * [T-tavern-ai] Mode chooser shown before a user-initiated new chat is
 * minted: a plain conversation, or a tavern roleplay (which continues
 * into the TavernSetupSheet for card picking). Two large frosted rows
 * — the same frosted-glass family as the tavern surfaces.
 *
 * Purely presentational: the caller owns what each choice does (mint
 * the draft, open the setup sheet), so the plain path can stay
 * byte-identical to the pre-sheet flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatModeSheet(
    onDismiss: () -> Unit,
    onNormalChat: () -> Unit,
    onTavernChat: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.new_chat_mode_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            NewChatModeRow(
                icon = Icons.Outlined.ChatBubble,
                title = stringResource(R.string.new_chat_mode_normal),
                subtitle = stringResource(R.string.new_chat_mode_normal_sub),
                onClick = onNormalChat,
            )
            Spacer(Modifier.height(12.dp))
            NewChatModeRow(
                icon = Icons.Outlined.MenuBook,
                title = stringResource(R.string.new_chat_mode_tavern),
                subtitle = stringResource(R.string.new_chat_mode_tavern_sub),
                onClick = onTavernChat,
            )
        }
    }
}

/** One big frosted option row: icon + title + one-line subtitle. */
@Composable
private fun NewChatModeRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    FrostedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
