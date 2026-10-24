package com.lowhub.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lowhub.app.R
import com.lowhub.app.agent.TavernAi
import com.lowhub.app.agent.TavernCharacter
import com.lowhub.app.agent.TavernStore
import com.lowhub.app.agent.TavernUserRole
import com.lowhub.app.agent.TavernWorldBook
import com.lowhub.app.data.repository.ProviderRepository
import com.lowhub.app.ui.components.FrostedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [T-tavern-mode] The reusable tavern library: character cards, user
 * roles and world books, managed in three tabs. Items are files under
 * TavernStore; chats bind SNAPSHOTS of them, so editing or deleting a
 * library item here never changes a chat that already picked it.
 *
 * Navigation (list ⇄ editor) is local state — no extra routes — and
 * every write hops to IO per TavernStore's contract.
 */
private sealed interface TavernEditTarget {
    data class Character(val item: TavernCharacter?) : TavernEditTarget
    data class UserRole(val item: TavernUserRole?) : TavernEditTarget
    data class WorldBook(val item: TavernWorldBook?) : TavernEditTarget
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TavernLibraryScreen(
    providerRepository: ProviderRepository,
    onBack: () -> Unit,
    onAiCreateClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var editTarget by remember { mutableStateOf<TavernEditTarget?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var characters by remember { mutableStateOf<List<TavernCharacter>>(emptyList()) }
    var userRoles by remember { mutableStateOf<List<TavernUserRole>>(emptyList()) }
    var worldBooks by remember { mutableStateOf<List<TavernWorldBook>>(emptyList()) }

    // [T-tavern-ai] The AI-create flow is a pushed route; popping back
    // here does not recompose this screen's effects, so without a
    // resume reload a freshly created card would be invisible until
    // some other state change happened to reload the lists.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) reloadKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(reloadKey) {
        val loaded = withContext(Dispatchers.IO) {
            Triple(
                TavernStore.listCharacters(context),
                TavernStore.listUserRoles(context),
                TavernStore.listWorldBooks(context),
            )
        }
        characters = loaded.first
        userRoles = loaded.second
        worldBooks = loaded.third
    }

    fun closeEditorAndReload() {
        editTarget = null
        reloadKey++
    }

    when (val target = editTarget) {
        is TavernEditTarget.Character -> TavernCharacterEditor(
            item = target.item,
            providerRepository = providerRepository,
            onClose = { editTarget = null },
            onSaved = { character ->
                scope.launch {
                    withContext(Dispatchers.IO) { TavernStore.saveCharacter(context, character) }
                    closeEditorAndReload()
                }
            },
            onDelete = target.item?.let { existing ->
                {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            TavernStore.deleteCharacter(context, existing.id)
                        }
                        closeEditorAndReload()
                    }
                }
            },
        )

        is TavernEditTarget.UserRole -> TavernUserRoleEditor(
            item = target.item,
            providerRepository = providerRepository,
            onClose = { editTarget = null },
            onSaved = { role ->
                scope.launch {
                    withContext(Dispatchers.IO) { TavernStore.saveUserRole(context, role) }
                    closeEditorAndReload()
                }
            },
            onDelete = target.item?.let { existing ->
                {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            TavernStore.deleteUserRole(context, existing.id)
                        }
                        closeEditorAndReload()
                    }
                }
            },
        )

        is TavernEditTarget.WorldBook -> TavernWorldBookEditor(
            item = target.item,
            providerRepository = providerRepository,
            onClose = { editTarget = null },
            onSaved = { book ->
                scope.launch {
                    withContext(Dispatchers.IO) { TavernStore.saveWorldBook(context, book) }
                    closeEditorAndReload()
                }
            },
            onDelete = target.item?.let { existing ->
                {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            TavernStore.deleteWorldBook(context, existing.id)
                        }
                        closeEditorAndReload()
                    }
                }
            },
        )

        null -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.tavern_library_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.settings_back),
                            )
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                val tabLabels = listOf(
                    stringResource(R.string.tavern_tab_characters),
                    stringResource(R.string.tavern_tab_user_roles),
                    stringResource(R.string.tavern_tab_world_books),
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabLabels.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = tab == index,
                            onClick = { tab = index },
                            shape = SegmentedButtonDefaults.itemShape(index, tabLabels.size),
                        ) {
                            Text(label)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // [T-tavern-ai] Co-create a character card by chatting
                // with the AI — a separate pushed route, offered from
                // the characters tab only.
                if (tab == 0) {
                    FrostedButton(
                        onClick = onAiCreateClick,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.tavern_ai_create_entry))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    when (tab) {
                        0 -> {
                            if (characters.isEmpty()) {
                                TavernEmptyText(stringResource(R.string.tavern_empty_characters))
                            }
                            characters.forEach { character ->
                                TavernListRow(
                                    title = character.name,
                                    // [T-tavern-ai] Tags lead the summary line
                                    // when the card carries any (library-only
                                    // labels; never injected into prompts).
                                    summary = listOf(
                                        character.tags.joinToString(", "),
                                        character.description
                                            .ifBlank { character.personality },
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    onClick = { editTarget = TavernEditTarget.Character(character) },
                                )
                            }
                        }

                        1 -> {
                            if (userRoles.isEmpty()) {
                                TavernEmptyText(stringResource(R.string.tavern_empty_user_roles))
                            }
                            userRoles.forEach { role ->
                                TavernListRow(
                                    title = role.name,
                                    summary = role.description,
                                    onClick = { editTarget = TavernEditTarget.UserRole(role) },
                                )
                            }
                        }

                        else -> {
                            if (worldBooks.isEmpty()) {
                                TavernEmptyText(stringResource(R.string.tavern_empty_world_books))
                            }
                            worldBooks.forEach { book ->
                                TavernListRow(
                                    title = book.name,
                                    summary = stringResource(
                                        R.string.tavern_worldbook_entry_count,
                                        book.entries.size,
                                    ),
                                    onClick = { editTarget = TavernEditTarget.WorldBook(book) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FrostedButton(
                    text = stringResource(
                        when (tab) {
                            0 -> R.string.tavern_new_character
                            1 -> R.string.tavern_new_user_role
                            else -> R.string.tavern_new_world_book
                        },
                    ),
                    onClick = {
                        editTarget = when (tab) {
                            0 -> TavernEditTarget.Character(null)
                            1 -> TavernEditTarget.UserRole(null)
                            else -> TavernEditTarget.WorldBook(null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TavernEmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
    )
}

@Composable
private fun TavernListRow(title: String, summary: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (summary.isNotBlank()) {
            Text(
                text = summary.replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ---------------------------------------------------------------------
// Editors
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TavernEditorScaffold(
    title: String,
    onClose: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        )
    }
}

/** Save (frosted) + optional delete-with-confirm row shared by the editors. */
@Composable
private fun TavernEditorActions(
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    Spacer(Modifier.height(20.dp))
    FrostedButton(
        text = stringResource(R.string.save),
        onClick = onSave,
        enabled = saveEnabled,
        modifier = Modifier.fillMaxWidth(),
    )
    if (onDelete != null) {
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.tavern_delete_item),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
    Spacer(Modifier.height(24.dp))
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.tavern_delete_confirm_title)) },
            text = { Text(stringResource(R.string.tavern_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete?.invoke()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Split a comma-separated editor field (ASCII or full-width commas). */
private fun parseCsvList(raw: String): List<String> =
    raw.split(',', '，').map { it.trim() }.filter { it.isNotEmpty() }

/**
 * [T-tavern-ai] Shared AI-action button for the tavern editors: frosted,
 * sparkle-led, and while a request is in flight it disables itself and
 * swaps to a spinner + "working" label. Results are filled back into
 * the editor by the caller — nothing auto-saves.
 */
@Composable
private fun TavernAiButton(
    text: String,
    busy: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    FrostedButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.tavern_ai_working))
        } else {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text)
        }
    }
}

/** The single failure line every tavern AI action degrades to. */
@Composable
private fun TavernAiErrorText() {
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.tavern_ai_failed),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun TavernCharacterEditor(
    item: TavernCharacter?,
    providerRepository: ProviderRepository,
    onClose: () -> Unit,
    onSaved: (TavernCharacter) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tavernAi = remember(context, providerRepository) {
        TavernAi(context.applicationContext, providerRepository)
    }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var personality by remember { mutableStateOf(item?.personality ?: "") }
    var scenario by remember { mutableStateOf(item?.scenario ?: "") }
    var firstMessage by remember { mutableStateOf(item?.firstMessage ?: "") }
    var exampleDialogue by remember { mutableStateOf(item?.exampleDialogue ?: "") }
    // Tags are edited as raw comma text and parsed only on save / AI
    // dispatch — same anti-comma-swallowing rule as entry keywords.
    var tagsText by remember { mutableStateOf(item?.tags?.joinToString(", ") ?: "") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiFailed by remember { mutableStateOf(false) }

    TavernEditorScaffold(
        title = stringResource(
            if (item == null) R.string.tavern_new_character else R.string.tavern_tab_characters,
        ),
        onClose = onClose,
    ) { modifier ->
        Column(modifier = modifier) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tavern_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.tavern_field_character_description)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text(stringResource(R.string.tavern_field_personality)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = scenario,
                onValueChange = { scenario = it },
                label = { Text(stringResource(R.string.tavern_field_scenario)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = firstMessage,
                onValueChange = { firstMessage = it },
                label = { Text(stringResource(R.string.tavern_field_first_message)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = exampleDialogue,
                onValueChange = { exampleDialogue = it },
                label = { Text(stringResource(R.string.tavern_field_example_dialogue)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text(stringResource(R.string.tavern_field_tags)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            // [T-tavern-ai] Expand whatever is typed so far into a full
            // card and fill the fields back in — the user reviews and
            // saves by hand, so a bad generation costs nothing.
            TavernAiButton(
                text = stringResource(R.string.tavern_ai_complete),
                busy = aiBusy,
            ) {
                scope.launch {
                    aiBusy = true
                    aiFailed = false
                    val result = tavernAi.completeCharacter(
                        id = item?.id ?: TavernStore.newCharacterId(),
                        name = name,
                        description = description,
                        personality = personality,
                        scenario = scenario,
                        exampleDialogue = exampleDialogue,
                        firstMessage = firstMessage,
                        tags = parseCsvList(tagsText),
                    )
                    if (result != null) {
                        if (result.name.isNotBlank()) name = result.name
                        if (result.description.isNotBlank()) description = result.description
                        if (result.personality.isNotBlank()) personality = result.personality
                        if (result.scenario.isNotBlank()) scenario = result.scenario
                        if (result.exampleDialogue.isNotBlank()) {
                            exampleDialogue = result.exampleDialogue
                        }
                        if (result.firstMessage.isNotBlank()) firstMessage = result.firstMessage
                        if (result.tags.isNotEmpty()) {
                            tagsText = result.tags.joinToString(", ")
                        }
                    } else {
                        aiFailed = true
                    }
                    aiBusy = false
                }
            }
            if (aiFailed) TavernAiErrorText()
            TavernEditorActions(
                saveEnabled = name.isNotBlank(),
                onSave = {
                    onSaved(
                        TavernCharacter(
                            id = item?.id ?: TavernStore.newCharacterId(),
                            name = name.trim(),
                            description = description,
                            personality = personality,
                            scenario = scenario,
                            firstMessage = firstMessage,
                            exampleDialogue = exampleDialogue,
                            tags = parseCsvList(tagsText),
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun TavernUserRoleEditor(
    item: TavernUserRole?,
    providerRepository: ProviderRepository,
    onClose: () -> Unit,
    onSaved: (TavernUserRole) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tavernAi = remember(context, providerRepository) {
        TavernAi(context.applicationContext, providerRepository)
    }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var description by remember { mutableStateOf(item?.description ?: "") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiFailed by remember { mutableStateOf(false) }

    TavernEditorScaffold(
        title = stringResource(
            if (item == null) R.string.tavern_new_user_role else R.string.tavern_tab_user_roles,
        ),
        onClose = onClose,
    ) { modifier ->
        Column(modifier = modifier) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tavern_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text(stringResource(R.string.tavern_field_role_description)) },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            TavernAiButton(
                text = stringResource(R.string.tavern_ai_complete),
                busy = aiBusy,
            ) {
                scope.launch {
                    aiBusy = true
                    aiFailed = false
                    val result = tavernAi.completeUserRole(
                        id = item?.id ?: TavernStore.newUserRoleId(),
                        name = name,
                        description = description,
                    )
                    if (result != null) {
                        if (result.name.isNotBlank()) name = result.name
                        if (result.description.isNotBlank()) description = result.description
                    } else {
                        aiFailed = true
                    }
                    aiBusy = false
                }
            }
            if (aiFailed) TavernAiErrorText()
            TavernEditorActions(
                saveEnabled = name.isNotBlank(),
                onSave = {
                    onSaved(
                        TavernUserRole(
                            id = item?.id ?: TavernStore.newUserRoleId(),
                            name = name.trim(),
                            description = description,
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
                onDelete = onDelete,
            )
        }
    }
}

@Composable
private fun TavernWorldBookEditor(
    item: TavernWorldBook?,
    providerRepository: ProviderRepository,
    onClose: () -> Unit,
    onSaved: (TavernWorldBook) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tavernAi = remember(context, providerRepository) {
        TavernAi(context.applicationContext, providerRepository)
    }
    var name by remember { mutableStateOf(item?.name ?: "") }
    var entries by remember { mutableStateOf(item?.entries ?: emptyList()) }
    // [T-tavern-mode] Keywords are edited as raw comma-separated text and
    // only parsed on save — re-joining the parsed list on every keystroke
    // would swallow the comma the user just typed.
    val keywordsText = remember {
        mutableStateMapOf<String, String>().apply {
            (item?.entries ?: emptyList()).forEach { entry ->
                put(entry.id, entry.keywords.joinToString(", "))
            }
        }
    }
    // [T-tavern-ai] Entry order is edited as raw text too and parsed on
    // save (invalid → 0): writing the parsed Int back on every keystroke
    // would make typing "-5" or clearing the field impossible.
    val orderText = remember {
        mutableStateMapOf<String, String>().apply {
            (item?.entries ?: emptyList()).forEach { entry ->
                put(entry.id, entry.order.toString())
            }
        }
    }
    var aiTheme by remember { mutableStateOf("") }
    var aiBusy by remember { mutableStateOf(false) }
    var aiFailed by remember { mutableStateOf(false) }

    fun updateEntry(id: String, transform: (TavernWorldBook.Entry) -> TavernWorldBook.Entry) {
        entries = entries.map { if (it.id == id) transform(it) else it }
    }

    TavernEditorScaffold(
        title = stringResource(
            if (item == null) R.string.tavern_new_world_book else R.string.tavern_tab_world_books,
        ),
        onClose = onClose,
    ) { modifier ->
        Column(modifier = modifier) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.tavern_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            // [T-tavern-ai] Describe a theme and let the model draft a
            // batch of entries for it; they append to the list below,
            // unsaved, exactly like hand-typed ones.
            OutlinedTextField(
                value = aiTheme,
                onValueChange = { aiTheme = it },
                label = { Text(stringResource(R.string.tavern_ai_theme_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            TavernAiButton(
                text = stringResource(R.string.tavern_ai_generate_entries),
                busy = aiBusy,
                enabled = aiTheme.isNotBlank(),
            ) {
                scope.launch {
                    aiBusy = true
                    aiFailed = false
                    val generated = tavernAi.generateWorldEntries(name, aiTheme)
                    if (generated != null) {
                        entries = entries + generated
                        generated.forEach { entry ->
                            keywordsText[entry.id] = entry.keywords.joinToString(", ")
                            orderText[entry.id] = entry.order.toString()
                        }
                    } else {
                        aiFailed = true
                    }
                    aiBusy = false
                }
            }
            if (aiFailed) TavernAiErrorText()
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.tavern_worldbook_entries),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            entries.forEach { entry ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        OutlinedTextField(
                            value = entry.title,
                            onValueChange = { v -> updateEntry(entry.id) { it.copy(title = v) } },
                            label = { Text(stringResource(R.string.tavern_entry_title)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = keywordsText[entry.id] ?: "",
                            onValueChange = { keywordsText[entry.id] = it },
                            label = { Text(stringResource(R.string.tavern_entry_keywords)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = entry.content,
                            onValueChange = { v -> updateEntry(entry.id) { it.copy(content = v) } },
                            label = { Text(stringResource(R.string.tavern_entry_content)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        // [T-tavern-ai] Injection priority — raw text
                        // state (orderText), parsed on save, invalid → 0.
                        OutlinedTextField(
                            value = orderText[entry.id] ?: "",
                            onValueChange = { orderText[entry.id] = it },
                            label = { Text(stringResource(R.string.tavern_entry_order)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.tavern_entry_always_on),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = entry.alwaysOn,
                                onCheckedChange = { v ->
                                    updateEntry(entry.id) { it.copy(alwaysOn = v) }
                                },
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.tavern_entry_enabled),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = entry.enabled,
                                onCheckedChange = { v ->
                                    updateEntry(entry.id) { it.copy(enabled = v) }
                                },
                            )
                        }
                        TextButton(
                            onClick = {
                                entries = entries.filterNot { it.id == entry.id }
                                keywordsText.remove(entry.id)
                                orderText.remove(entry.id)
                            },
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                            )
                            Text(stringResource(R.string.tavern_entry_delete))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = {
                    val entry = TavernWorldBook.Entry(id = TavernStore.newEntryId())
                    entries = entries + entry
                    keywordsText[entry.id] = ""
                    orderText[entry.id] = "0"
                },
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.tavern_entry_new))
            }
            TavernEditorActions(
                saveEnabled = name.isNotBlank(),
                onSave = {
                    onSaved(
                        TavernWorldBook(
                            id = item?.id ?: TavernStore.newWorldBookId(),
                            name = name.trim(),
                            entries = entries.map { entry ->
                                entry.copy(
                                    keywords = parseCsvList(keywordsText[entry.id] ?: ""),
                                    // Invalid priority text saves as 0.
                                    order = orderText[entry.id]?.trim()
                                        ?.toIntOrNull() ?: 0,
                                )
                            },
                            updatedAt = System.currentTimeMillis(),
                        ),
                    )
                },
                onDelete = onDelete,
            )
        }
    }
}
