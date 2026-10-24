package com.lowhub.app.agent

import android.content.Context
import com.lowhub.app.data.model.LLMMessage
import com.lowhub.app.data.model.ThinkingLevel
import com.lowhub.app.data.repository.ProviderRepository
import com.lowhub.app.logging.AppLogger
import com.lowhub.app.ui.chat.TitleCandidates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * [T-tavern-ai] One-shot / short multi-turn LLM calls backing the
 * tavern library's AI assists: autocomplete a rough character card,
 * expand a user role, generate world-book entries from a theme, and
 * extract a finished card from a co-creation chat.
 *
 * Model choice deliberately mirrors the title-generation ladder
 * (`SessionListViewModel.regenerateTitle`): candidates are
 * [TitleCandidates.forSession] (title sub group → session primary →
 * default primary group → every other eligible model), providers are
 * built by [TitleCandidates.providerFor], and failures walk to the
 * next candidate via [TitleCandidates.walk] instead of surfacing. The
 * tavern calls have no chat session of their own, so the ladder runs
 * with the synthetic session id [SESSION_ID] and no primary binding —
 * tiers 1 (sub group), 2c (default primary group) and 3 (the rest)
 * still apply in the same order.
 *
 * Failure contract: every entry point returns null on ANY failure (no
 * candidates, provider errors, unparseable output). The UI maps null
 * to a single "AI failed" line — nothing here throws into the UI.
 */
class TavernAi(
    private val context: Context,
    private val providerRepository: ProviderRepository,
) {

    companion object {
        private const val TAG = "TavernAi"

        /** Synthetic session tag for the candidate ladder / OpenCode header. */
        private const val SESSION_ID = "tavern-ai"

        private const val CARD_SYSTEM =
            "You are an expert character-card author for immersive roleplay " +
                "(SillyTavern-style character cards). Expand the user's rough " +
                "draft into a complete, vivid, ready-to-play character card. " +
                "Reply with a single valid JSON object only — no prose, no " +
                "markdown fence. The JSON keys are: name, description, " +
                "personality, scenario, exampleDialogue, firstMessage, tags. " +
                "Write every value in the same language as the user's draft. " +
                "description: who the character is — identity, appearance, " +
                "background, notable details. personality: traits, voice, " +
                "speech patterns, quirks, how they treat the user. scenario: " +
                "the world and the opening situation of the roleplay. " +
                "exampleDialogue: one short sample exchange that shows how " +
                "the character speaks (plain text, label the speakers by " +
                "name / 'User'). firstMessage: the character's opening " +
                "message, written in the character's first person, setting " +
                "the scene and inviting the user in. tags: 2-5 short labels. " +
                "Keep any facts the user already gave; invent consistent " +
                "detail for the gaps."

        private const val ROLE_SYSTEM =
            "You write reusable 'user role' cards for immersive roleplay: " +
                "the persona the USER plays opposite the AI character. " +
                "Expand the user's rough draft into a complete role. Reply " +
                "with a single valid JSON object only — no prose, no " +
                "markdown fence. The JSON keys are: name, description. " +
                "Write every value in the same language as the user's draft. " +
                "description: identity, appearance, background, personality, " +
                "and the role's relationship to the world / the AI character. " +
                "Keep any facts the user already gave; invent consistent " +
                "detail for the gaps."

        private const val WORLD_SYSTEM =
            "You are a lorebook author for immersive roleplay (SillyTavern " +
                "world books). Given a book name and a theme, generate 3 to " +
                "6 world-book entries that flesh out the theme. Reply with " +
                "a single valid JSON array only — no prose, no markdown " +
                "fence. Each element is an object with keys: title (short), " +
                "keywords (array of 1-4 trigger words or phrases that would " +
                "appear in conversation when this lore becomes relevant), " +
                "content (the lore itself, 1-3 sentences of concrete facts, " +
                "written as reference notes, not story prose), alwaysOn " +
                "(boolean — true only for facts that must ALWAYS apply, use " +
                "sparingly), order (integer priority, higher injects first; " +
                "use 0 for normal entries). Write all content in the same " +
                "language as the theme."

        private const val EXTRACT_SYSTEM =
            "You are an expert character-card author for immersive " +
                "roleplay. Below is a co-creation conversation between a " +
                "user and an assistant who asked questions to shape a " +
                "character. Distill the agreed character into one complete " +
                "character card. Reply with a single valid JSON object " +
                "only — no prose, no markdown fence. The JSON keys are: " +
                "name, description, personality, scenario, exampleDialogue, " +
                "firstMessage, tags. Write every value in the language the " +
                "character was discussed in. description: identity, " +
                "appearance, background. personality: traits, voice, speech " +
                "patterns. scenario: the world and opening situation. " +
                "exampleDialogue: one short sample exchange in the " +
                "character's voice. firstMessage: the character's opening " +
                "message, in the character's first person, setting the " +
                "scene. tags: 2-5 short labels."
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CharacterDraftJson(
        val name: String = "",
        val description: String = "",
        val personality: String = "",
        val scenario: String = "",
        val exampleDialogue: String = "",
        val firstMessage: String = "",
        val tags: List<String> = emptyList(),
    )

    @Serializable
    private data class UserRoleDraftJson(
        val name: String = "",
        val description: String = "",
    )

    @Serializable
    private data class WorldEntryJson(
        val title: String = "",
        val keywords: List<String> = emptyList(),
        val content: String = "",
        val alwaysOn: Boolean = false,
        val order: Int = 0,
    )

    // ---- the ladder (mirrors regenerateTitle) -------------------------

    /**
     * One request/response round over [history] (role to text pairs,
     * roles "user" / "assistant"), with [system] as the system prompt.
     * Returns the model's trimmed text, or null when every candidate
     * failed or answered empty.
     */
    suspend fun chatMulti(
        system: String,
        history: List<Pair<String, String>>,
    ): String? {
        if (history.isEmpty()) return null
        // The title ladder this mirrors runs on Dispatchers.IO (credential
        // reads + network); do the same so callers can stay on Main.
        return withContext(Dispatchers.IO) { chatMultiOnIo(system, history) }
    }

    private suspend fun chatMultiOnIo(
        system: String,
        history: List<Pair<String, String>>,
    ): String? {
        val candidates = TitleCandidates.forSession(
            providerRepository,
            sessionId = SESSION_ID,
            primary = null,
            sessionModelId = null,
        )
        if (candidates.isEmpty()) {
            AppLogger.warning(TAG, "chatMulti: no eligible model candidates")
            return null
        }
        val messages = history.map { (role, text) ->
            LLMMessage(
                role = if (role == "assistant") {
                    LLMMessage.Role.ASSISTANT
                } else {
                    LLMMessage.Role.USER
                },
                content = text,
            )
        }
        return TitleCandidates.walk(candidates, origin = "tavern-ai") { entry ->
            val provider = TitleCandidates.providerFor(
                providerRepository, context, entry, sessionId = SESSION_ID,
            ) ?: return@walk null
            // Reasoning models burn budget on hidden thinking before any
            // content (the T334 lesson from title generation); cards are
            // far longer than titles, so give every model real room and
            // the reasoning ones more.
            val maxTokens = if (entry.model.supportsReasoning == true) 8192 else 4096
            val response = provider.sendMessage(
                messages = messages,
                systemPrompt = system,
                maxTokens = maxTokens,
                // null so the field is omitted — same call shape as the
                // title ladder (some models 400 on a pinned temperature).
                temperature = null,
                thinkingLevel = ThinkingLevel.OFF,
            )
            response.text.trim().ifEmpty { null }
        }
    }

    /** Single-turn convenience over [chatMulti]. */
    suspend fun chatOnce(system: String, user: String): String? =
        chatMulti(system, listOf("user" to user))

    // ---- character cards ----------------------------------------------

    /**
     * Expand the editor's current field values into a full character
     * card. Non-blank fields go to the model as the draft; the result
     * keeps the passed [id] (or mints one) so the editor can fill its
     * fields straight from the returned card.
     */
    suspend fun completeCharacter(
        id: String,
        name: String,
        description: String,
        personality: String,
        scenario: String,
        exampleDialogue: String,
        firstMessage: String,
        tags: List<String>,
    ): TavernCharacter? {
        val draft = buildString {
            append("Draft character card (expand and complete it):\n")
            fun field(label: String, value: String) {
                if (value.isNotBlank()) append("$label: ${value.trim()}\n")
            }
            field("name", name)
            field("description", description)
            field("personality", personality)
            field("scenario", scenario)
            field("exampleDialogue", exampleDialogue)
            field("firstMessage", firstMessage)
            if (tags.isNotEmpty()) {
                append("tags: ${tags.joinToString(", ")}\n")
            }
            if (name.isBlank() && description.isBlank() && personality.isBlank() &&
                scenario.isBlank() && exampleDialogue.isBlank() && firstMessage.isBlank()
            ) {
                append("(The draft is empty — invent an original, appealing character.)\n")
            }
        }
        val text = chatOnce(CARD_SYSTEM, draft) ?: return null
        return parseCharacter(id, text)
    }

    /** Distill a co-creation chat transcript into a finished card. */
    suspend fun extractCharacter(transcript: String): TavernCharacter? {
        val text = chatOnce(EXTRACT_SYSTEM, transcript) ?: return null
        return parseCharacter(TavernStore.newCharacterId(), text)
    }

    private fun parseCharacter(id: String, text: String): TavernCharacter? {
        val body = extractJson(text, '{', '}') ?: return null
        val draft = try {
            json.decodeFromString<CharacterDraftJson>(body)
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "character JSON parse failed: ${t.message}")
            return null
        }
        if (draft.name.isBlank() && draft.description.isBlank()) return null
        return TavernCharacter(
            id = id,
            name = draft.name.trim(),
            description = draft.description.trim(),
            personality = draft.personality.trim(),
            scenario = draft.scenario.trim(),
            firstMessage = draft.firstMessage.trim(),
            exampleDialogue = draft.exampleDialogue.trim(),
            tags = draft.tags.map { it.trim() }.filter { it.isNotEmpty() },
            updatedAt = System.currentTimeMillis(),
        )
    }

    // ---- user roles -----------------------------------------------------

    /** Expand a rough user-role draft into a complete role card. */
    suspend fun completeUserRole(
        id: String,
        name: String,
        description: String,
    ): TavernUserRole? {
        val draft = buildString {
            append("Draft user role (expand and complete it):\n")
            if (name.isNotBlank()) append("name: ${name.trim()}\n")
            if (description.isNotBlank()) append("description: ${description.trim()}\n")
            if (name.isBlank() && description.isBlank()) {
                append("(The draft is empty — invent an appealing role for the user to play.)\n")
            }
        }
        val text = chatOnce(ROLE_SYSTEM, draft) ?: return null
        val body = extractJson(text, '{', '}') ?: return null
        val parsed = try {
            json.decodeFromString<UserRoleDraftJson>(body)
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "user role JSON parse failed: ${t.message}")
            return null
        }
        if (parsed.name.isBlank() && parsed.description.isBlank()) return null
        return TavernUserRole(
            id = id,
            name = parsed.name.trim(),
            description = parsed.description.trim(),
            updatedAt = System.currentTimeMillis(),
        )
    }

    // ---- world books ------------------------------------------------------

    /**
     * Generate 3–6 entries for the book named [bookName] around
     * [theme]. Returned entries carry fresh ids from [TavernStore];
     * the editor appends them unsaved, like hand-typed ones.
     */
    suspend fun generateWorldEntries(
        bookName: String,
        theme: String,
    ): List<TavernWorldBook.Entry>? {
        val prompt = buildString {
            append("World book name: ${bookName.ifBlank { "(untitled)" }}\n")
            append("Theme: ${theme.trim()}\n")
            append("Generate the entries as one JSON array.")
        }
        val text = chatOnce(WORLD_SYSTEM, prompt) ?: return null
        val body = extractJson(text, '[', ']') ?: return null
        val parsed = try {
            json.decodeFromString<List<WorldEntryJson>>(body)
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "world entries JSON parse failed: ${t.message}")
            return null
        }
        val entries = parsed
            .filter { it.content.isNotBlank() }
            .map { entry ->
                TavernWorldBook.Entry(
                    id = TavernStore.newEntryId(),
                    title = entry.title.trim(),
                    keywords = entry.keywords.map { it.trim() }.filter { it.isNotEmpty() },
                    content = entry.content.trim(),
                    alwaysOn = entry.alwaysOn,
                    enabled = true,
                    order = entry.order,
                )
            }
        return entries.ifEmpty { null }
    }

    // ---- output hygiene ---------------------------------------------------

    /**
     * Models habitually wrap JSON in prose or a ```json fence (the same
     * behaviour parseGroupSuggestion defends against): strip a fence,
     * then take the span from the first [open] to the last [close].
     */
    private fun extractJson(text: String, open: Char, close: Char): String? {
        var body = text.trim()
        if (body.startsWith("```")) {
            body = body.removePrefix("```json").removePrefix("```JSON")
                .removePrefix("```")
                .trim()
            if (body.endsWith("```")) body = body.removeSuffix("```").trim()
        }
        val start = body.indexOf(open)
        val end = body.lastIndexOf(close)
        if (start < 0 || end <= start) {
            AppLogger.warning(TAG, "no JSON span in model output")
            return null
        }
        return body.substring(start, end + 1)
    }
}
