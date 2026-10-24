package com.lowhub.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lowhub.app.R
import com.lowhub.app.agent.TavernCharacter
import com.lowhub.app.agent.TavernSessionConfig
import com.lowhub.app.agent.TavernStore
import com.lowhub.app.agent.TavernUserRole
import com.lowhub.app.agent.TavernWorldBook
import com.lowhub.app.ui.components.FrostedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class TavernPickSection { CHARACTER, USER_ROLE, WORLD_BOOK }

/**
 * [T-tavern-mode] Tavern setup sheet (chat overflow menu → "Tavern
 * Mode" / "Tavern Settings"). Picks one character card (required) plus
 * an optional user role and world book from the reusable library, then
 * hands a full SNAPSHOT to [onConfirm] — the session keeps its own
 * copies, so later library edits don't leak into the chat.
 *
 * In edit mode ([initialConfig] non-null) the snapshot's item ids
 * preselect the rows; a snapshot item that was deleted from the
 * library is merged back into the local list so it still shows as
 * selected and can be re-saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TavernSetupSheet(
    initialConfig: TavernSessionConfig?,
    onDismiss: () -> Unit,
    onConfirm: (TavernSessionConfig) -> Unit,
    onDisableTavern: (() -> Unit)? = null,
    onCreateLibraryItem: () -> Unit,
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var characters by remember { mutableStateOf<List<TavernCharacter>>(emptyList()) }
    var userRoles by remember { mutableStateOf<List<TavernUserRole>>(emptyList()) }
    var worldBooks by remember { mutableStateOf<List<TavernWorldBook>>(emptyList()) }
    var selectedCharacterId by remember { mutableStateOf(initialConfig?.character?.id) }
    var selectedUserRoleId by remember { mutableStateOf(initialConfig?.userRole?.id) }
    var selectedWorldBookId by remember { mutableStateOf(initialConfig?.worldBook?.id) }
    var expanded by remember { mutableStateOf<TavernPickSection?>(TavernPickSection.CHARACTER) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            Triple(
                TavernStore.listCharacters(context),
                TavernStore.listUserRoles(context),
                TavernStore.listWorldBooks(context),
            )
        }
        characters = result.first
        userRoles = result.second
        worldBooks = result.third
        // [T-tavern-mode] Merge snapshot items that vanished from the
        // library back in, so edit mode keeps showing them selected.
        initialConfig?.character?.let { snapshot ->
            if (characters.none { it.id == snapshot.id }) characters = listOf(snapshot) + characters
        }
        initialConfig?.userRole?.let { snapshot ->
            if (userRoles.none { it.id == snapshot.id }) userRoles = listOf(snapshot) + userRoles
        }
        initialConfig?.worldBook?.let { snapshot ->
            if (worldBooks.none { it.id == snapshot.id }) worldBooks = listOf(snapshot) + worldBooks
        }
        loaded = true
    }

    val selectedCharacter = characters.firstOrNull { it.id == selectedCharacterId }
    val selectedUserRole = userRoles.firstOrNull { it.id == selectedUserRoleId }
    val selectedWorldBook = worldBooks.firstOrNull { it.id == selectedWorldBookId }
    val isEditMode = initialConfig != null

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                text = stringResource(
                    if (isEditMode) R.string.tavern_settings_action else R.string.tavern_setup_title,
                ),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))

            if (loaded && characters.isEmpty()) {
                Text(
                    text = stringResource(R.string.tavern_setup_no_characters),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                FrostedButton(
                    text = stringResource(R.string.tavern_setup_create),
                    onClick = onCreateLibraryItem,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TavernPickRow(
                    label = stringResource(R.string.tavern_setup_character),
                    selectedName = selectedCharacter?.name
                        ?: stringResource(R.string.tavern_setup_not_selected),
                    expanded = expanded == TavernPickSection.CHARACTER,
                    onToggle = {
                        expanded = if (expanded == TavernPickSection.CHARACTER) {
                            null
                        } else {
                            TavernPickSection.CHARACTER
                        }
                    },
                )
                if (expanded == TavernPickSection.CHARACTER) {
                    characters.forEach { character ->
                        TavernOptionRow(
                            name = character.name,
                            selected = character.id == selectedCharacterId,
                            onClick = { selectedCharacterId = character.id },
                        )
                    }
                }
                TavernPickRow(
                    label = stringResource(R.string.tavern_setup_user_role),
                    selectedName = selectedUserRole?.name
                        ?: stringResource(R.string.tavern_setup_none),
                    expanded = expanded == TavernPickSection.USER_ROLE,
                    onToggle = {
                        expanded = if (expanded == TavernPickSection.USER_ROLE) {
                            null
                        } else {
                            TavernPickSection.USER_ROLE
                        }
                    },
                )
                if (expanded == TavernPickSection.USER_ROLE) {
                    TavernOptionRow(
                        name = stringResource(R.string.tavern_setup_none),
                        selected = selectedUserRoleId == null,
                        onClick = { selectedUserRoleId = null },
                    )
                    userRoles.forEach { role ->
                        TavernOptionRow(
                            name = role.name,
                            selected = role.id == selectedUserRoleId,
                            onClick = { selectedUserRoleId = role.id },
                        )
                    }
                }
                TavernPickRow(
                    label = stringResource(R.string.tavern_setup_world_book),
                    selectedName = selectedWorldBook?.name
                        ?: stringResource(R.string.tavern_setup_none),
                    expanded = expanded == TavernPickSection.WORLD_BOOK,
                    onToggle = {
                        expanded = if (expanded == TavernPickSection.WORLD_BOOK) {
                            null
                        } else {
                            TavernPickSection.WORLD_BOOK
                        }
                    },
                )
                if (expanded == TavernPickSection.WORLD_BOOK) {
                    TavernOptionRow(
                        name = stringResource(R.string.tavern_setup_none),
                        selected = selectedWorldBookId == null,
                        onClick = { selectedWorldBookId = null },
                    )
                    worldBooks.forEach { book ->
                        TavernOptionRow(
                            name = book.name,
                            selected = book.id == selectedWorldBookId,
                            onClick = { selectedWorldBookId = book.id },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                FrostedButton(
                    text = stringResource(
                        if (isEditMode) R.string.tavern_save_settings else R.string.tavern_start,
                    ),
                    enabled = selectedCharacter != null,
                    onClick = {
                        selectedCharacter?.let { character ->
                            onConfirm(
                                TavernSessionConfig(
                                    character = character.copy(),
                                    userRole = selectedUserRole?.copy(),
                                    worldBook = selectedWorldBook?.copy(),
                                ),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDisableTavern != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            onDisableTavern()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.tavern_disable_action),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/** Section header row: field label on the left, current pick + chevron. */
@Composable
private fun TavernPickRow(
    label: String,
    selectedName: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = selectedName,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) {
                Icons.Filled.KeyboardArrowUp
            } else {
                Icons.Filled.KeyboardArrowDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One selectable option inside an expanded pick section. */
@Composable
private fun TavernOptionRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
