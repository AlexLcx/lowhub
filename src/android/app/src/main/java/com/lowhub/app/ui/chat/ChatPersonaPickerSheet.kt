package com.lowhub.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lowhub.app.R
import com.lowhub.app.agent.PersonaInfo
import com.lowhub.app.agent.PersonaStore
import com.lowhub.app.ui.settings.SoulIconGlyph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [T-multi-persona] Persona picker for the chat screen (global switch,
 * plan A). Lists the persona library; tapping a row activates that
 * persona via [PersonaStore.setActive] on IO, then reports through
 * [onSelected] and dismisses.
 *
 * No ChatScreen-side switch logic is needed after selection: setActive
 * writes through [com.lowhub.app.agent.SoulStore.save], which refreshes
 * `SoulStore.cachedMetadata` — the chat title, bubble identity and the
 * next message's system prompt all follow automatically.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPersonaPickerSheet(
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var personas by remember { mutableStateOf<List<PersonaInfo>>(emptyList()) }
    var activeId by remember { mutableStateOf(PersonaStore.DEFAULT_ID) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            PersonaStore.listPersonas(context) to PersonaStore.activeId(context)
        }
        personas = result.first
        activeId = result.second
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(R.string.persona_section_title),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            personas.forEach { persona ->
                val isActive = persona.id == activeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (isActive) {
                                onDismiss()
                            } else {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        PersonaStore.setActive(context, persona.id)
                                    }
                                    onSelected(persona.id)
                                    onDismiss()
                                }
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SoulIconGlyph(icon = persona.metadata.icon, sizeDp = 28.dp, emojiSp = 20.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = persona.metadata.name,
                        fontSize = 15.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    if (isActive) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.persona_current),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
