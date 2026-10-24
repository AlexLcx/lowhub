package com.lowhub.app.agent

import android.content.Context
import com.lowhub.app.logging.AppLogger
import java.io.File
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * [T-tavern-mode] One AI character card in the tavern library.
 *
 * [description] is the persona write-up, [personality] the character's
 * traits / voice, [scenario] the setting the roleplay opens in, and
 * [firstMessage] the optional in-character opening line inserted as the
 * first assistant message when a tavern chat starts.
 *
 * [T-tavern-ai] SillyTavern-style extras: [exampleDialogue] is a sample
 * exchange teaching the model the character's voice (injected as a
 * style block, never as history), and [tags] are library-only labels —
 * shown in the list, never injected. Both default empty so pre-existing
 * card files (which lack the keys) still decode.
 */
@Serializable
data class TavernCharacter(
    val id: String,
    val name: String,
    val description: String = "",
    val personality: String = "",
    val scenario: String = "",
    val firstMessage: String = "",
    val exampleDialogue: String = "",
    val tags: List<String> = emptyList(),
    val updatedAt: Long = 0L,
)

/** [T-tavern-mode] A reusable role the USER plays in tavern chats. */
@Serializable
data class TavernUserRole(
    val id: String,
    val name: String,
    val description: String = "",
    val updatedAt: Long = 0L,
)

/**
 * [T-tavern-mode] A world book (lorebook): a named set of entries whose
 * [Entry.content] is woven into the system prompt when the entry fires —
 * either always ([Entry.alwaysOn]) or when one of its keywords appears in
 * the recent conversation.
 */
@Serializable
data class TavernWorldBook(
    val id: String,
    val name: String,
    val entries: List<Entry> = emptyList(),
    val updatedAt: Long = 0L,
) {
    @Serializable
    data class Entry(
        val id: String,
        val title: String = "",
        val keywords: List<String> = emptyList(),
        val content: String = "",
        val alwaysOn: Boolean = false,
        val enabled: Boolean = true,
        /**
         * [T-tavern-ai] Injection priority (SillyTavern's "Order"):
         * when several entries fire, higher [order] is injected first
         * and therefore survives the lore char budget first. Defaults
         * to 0 so pre-existing book files still decode.
         */
        val order: Int = 0,
    )
}

/**
 * [T-tavern-mode] The tavern setup bound to ONE session. Holds full
 * snapshots (copies) of the library items chosen at setup time, so later
 * library edits or deletions never mutate a running chat's setup.
 */
@Serializable
data class TavernSessionConfig(
    val character: TavernCharacter,
    val userRole: TavernUserRole? = null,
    val worldBook: TavernWorldBook? = null,
)

/**
 * [T-tavern-mode] File-backed store for the tavern library (character
 * cards, user roles, world books) and per-session tavern snapshots.
 *
 * Layout under `<filesDir>/minis-global/tavern/`:
 *   - `characters/<id>.json`  – one file per character card
 *   - `user-roles/<id>.json`  – one file per user role
 *   - `worldbooks/<id>.json`  – one file per world book
 *   - `sessions/<sessionId>.json` – the [TavernSessionConfig] snapshot
 *     bound to a chat session
 *
 * Deliberately NOT Room: tavern data is app-local ephemera in the same
 * spirit as [PersonaStore]'s side files, and keeping it out of the chat
 * database means no migration risk on the sessions/messages tables.
 *
 * All functions do synchronous file IO — callers must hop to an IO
 * thread. Failures follow [PersonaStore]'s contract: writes degrade
 * silently (logged), unreadable files read back as null / are skipped
 * in lists, so a corrupt library file can never break chat.
 */
object TavernStore {

    private const val TAG = "TavernStore"
    private const val TAVERN_SUBDIR = "minis-global/tavern"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    // ---- paths -------------------------------------------------------

    private fun tavernDir(context: Context): File = File(context.filesDir, TAVERN_SUBDIR)

    private fun charactersDir(context: Context): File = File(tavernDir(context), "characters")

    private fun userRolesDir(context: Context): File = File(tavernDir(context), "user-roles")

    private fun worldBooksDir(context: Context): File = File(tavernDir(context), "worldbooks")

    private fun sessionsDir(context: Context): File = File(tavernDir(context), "sessions")

    /** Ids become file names — strip anything that is not file-safe. */
    private fun safeFileName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun characterFile(context: Context, id: String): File =
        File(charactersDir(context), "${safeFileName(id)}.json")

    private fun userRoleFile(context: Context, id: String): File =
        File(userRolesDir(context), "${safeFileName(id)}.json")

    private fun worldBookFile(context: Context, id: String): File =
        File(worldBooksDir(context), "${safeFileName(id)}.json")

    private fun sessionFile(context: Context, sessionId: String): File =
        File(sessionsDir(context), "${safeFileName(sessionId)}.json")

    // ---- id factories (used by the library editors for new items) -----

    fun newCharacterId(): String = "c-" + UUID.randomUUID().toString().take(8)

    fun newUserRoleId(): String = "u-" + UUID.randomUUID().toString().take(8)

    fun newWorldBookId(): String = "w-" + UUID.randomUUID().toString().take(8)

    fun newEntryId(): String = "e-" + UUID.randomUUID().toString().take(8)

    // ---- generic file helpers ----------------------------------------

    private inline fun <reified T> readJson(file: File): T? {
        if (!file.isFile) return null
        return try {
            json.decodeFromString<T>(file.readText())
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "unreadable file ${file.name}: ${t.message}")
            null
        }
    }

    private inline fun <reified T> writeJson(file: File, value: T) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(value))
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "write ${file.name} failed: ${t.message}")
        }
    }

    private fun deleteFile(file: File) {
        try {
            if (file.isFile) file.delete()
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "delete ${file.name} failed: ${t.message}")
        }
    }

    private fun jsonFiles(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && f.extension == "json" }?.toList() ?: emptyList()

    // ---- character cards ----------------------------------------------

    fun listCharacters(context: Context): List<TavernCharacter> =
        jsonFiles(charactersDir(context))
            .mapNotNull { readJson<TavernCharacter>(it) }
            .sortedBy { it.name.lowercase() }

    fun loadCharacter(context: Context, id: String): TavernCharacter? =
        readJson(characterFile(context, id))

    @Synchronized
    fun saveCharacter(context: Context, character: TavernCharacter) {
        writeJson(characterFile(context, character.id), character)
    }

    @Synchronized
    fun deleteCharacter(context: Context, id: String) {
        deleteFile(characterFile(context, id))
    }

    // ---- user roles -----------------------------------------------------

    fun listUserRoles(context: Context): List<TavernUserRole> =
        jsonFiles(userRolesDir(context))
            .mapNotNull { readJson<TavernUserRole>(it) }
            .sortedBy { it.name.lowercase() }

    fun loadUserRole(context: Context, id: String): TavernUserRole? =
        readJson(userRoleFile(context, id))

    @Synchronized
    fun saveUserRole(context: Context, role: TavernUserRole) {
        writeJson(userRoleFile(context, role.id), role)
    }

    @Synchronized
    fun deleteUserRole(context: Context, id: String) {
        deleteFile(userRoleFile(context, id))
    }

    // ---- world books ----------------------------------------------------

    fun listWorldBooks(context: Context): List<TavernWorldBook> =
        jsonFiles(worldBooksDir(context))
            .mapNotNull { readJson<TavernWorldBook>(it) }
            .sortedBy { it.name.lowercase() }

    fun loadWorldBook(context: Context, id: String): TavernWorldBook? =
        readJson(worldBookFile(context, id))

    @Synchronized
    fun saveWorldBook(context: Context, book: TavernWorldBook) {
        writeJson(worldBookFile(context, book.id), book)
    }

    @Synchronized
    fun deleteWorldBook(context: Context, id: String) {
        deleteFile(worldBookFile(context, id))
    }

    // ---- per-session snapshots ------------------------------------------

    fun loadSessionConfig(context: Context, sessionId: String): TavernSessionConfig? =
        readJson(sessionFile(context, sessionId))

    @Synchronized
    fun saveSessionConfig(context: Context, sessionId: String, config: TavernSessionConfig) {
        writeJson(sessionFile(context, sessionId), config)
    }

    @Synchronized
    fun deleteSessionConfig(context: Context, sessionId: String) {
        deleteFile(sessionFile(context, sessionId))
    }
}
