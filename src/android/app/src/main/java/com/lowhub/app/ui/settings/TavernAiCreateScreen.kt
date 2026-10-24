package com.lowhub.app.ui.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.lowhub.app.R
import com.lowhub.app.agent.TavernAi
import com.lowhub.app.agent.TavernCharacter
import com.lowhub.app.agent.TavernStore
import com.lowhub.app.data.repository.ProviderRepository
import com.lowhub.app.ui.components.FrostedButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [T-tavern-ai] "Create a character card by chatting": a lightweight
 * co-creation conversation (deliberately NOT the chat stack — no
 * ChatViewModel, no persisted session). The model interviews the user
 * for the key ingredients; at any point after one full exchange the
 * user can distill the transcript into a card, review/edit it in the
 * standard character editor, and save it into the tavern library.
 *
 * All calls go through [TavernAi]; failures surface as one error line
 * and leave the conversation untouched.
 */
private const val CO_CREATE_SYSTEM =
    "你是一位角色卡共创助手，正在帮用户把脑海中的角色灵感打磨成一张完整的角色卡。" +
        "请先简短追问关键设定：外貌、性格、背景、与用户（玩家）的关系、所在世界观等，一次聚焦一两个问题。" +
        "用户也可以直接给出灵感，你就顺着灵感继续追问缺失的部分。" +
        "每轮回复保持简短、聚焦于补齐设定；不要直接输出卡片或 JSON。" +
        "用用户所用的语言回复。"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TavernAiCreateScreen(
    providerRepository: ProviderRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val tavernAi = remember(context, providerRepository) {
        TavernAi(context.applicationContext, providerRepository)
    }
    // (role, text) — roles are "user" / "assistant", TavernAi's shape.
    var messages by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<TavernCharacter?>(null) }

    val readyToExtract = messages.any { it.first == "user" } &&
        messages.any { it.first == "assistant" }

    // Confirm phase: the standard character editor, prefilled with the
    // distilled card. Saving writes a NEW library card and leaves.
    draft?.let { card ->
        TavernCharacterEditor(
            item = card,
            providerRepository = providerRepository,
            onClose = { draft = null },
            onSaved = { character ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        TavernStore.saveCharacter(context, character)
                    }
                    onBack()
                }
            },
            onDelete = null,
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tavern_ai_create_title)) },
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
            val scrollState = rememberScrollState()
            LaunchedEffect(messages.size) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
            ) {
                if (messages.isEmpty()) {
                    Text(
                        text = stringResource(R.string.tavern_ai_create_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
                messages.forEach { (role, text) ->
                    TavernChatBubble(isUser = role == "user", text = text)
                }
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tavern_ai_working),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (failed) {
                Text(
                    text = stringResource(R.string.tavern_ai_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
            }
            FrostedButton(
                onClick = {
                    scope.launch {
                        busy = true
                        failed = false
                        val transcript = messages.joinToString("\n") { (role, text) ->
                            "${if (role == "user") "User" else "Assistant"}: $text"
                        }
                        val card = tavernAi.extractCharacter(transcript)
                        if (card != null) {
                            draft = card
                        } else {
                            failed = true
                        }
                        busy = false
                    }
                },
                enabled = readyToExtract && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tavern_ai_create_generate))
            }
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text(stringResource(R.string.tavern_ai_create_input_hint)) },
                    minLines = 1,
                    maxLines = 4,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = input.isNotBlank() && !busy,
                    onClick = {
                        val text = input.trim()
                        if (text.isEmpty()) return@IconButton
                        input = ""
                        val history = messages + ("user" to text)
                        messages = history
                        scope.launch {
                            busy = true
                            failed = false
                            val reply = tavernAi.chatMulti(CO_CREATE_SYSTEM, history)
                            if (reply != null) {
                                messages = history + ("assistant" to reply)
                            } else {
                                failed = true
                            }
                            busy = false
                        }
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.tavern_ai_create_send),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** One plain chat bubble: user right-aligned on primary, assistant left. */
@Composable
private fun TavernChatBubble(isUser: Boolean, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        if (isUser) Spacer(Modifier.weight(1f))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isUser) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.92f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
        if (!isUser) Spacer(Modifier.weight(1f))
    }
}
