package com.lowhub.app.agent

import android.content.Context
import com.lowhub.app.logging.AppLogger
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * [T-writing-controls] Per-session creative-writing controls: how the
 * assistant should WRITE (novel craft, reply length, prose style) and
 * the sampling knobs (temperature / top-p) for the session's turns.
 *
 * [lengthTier] is one of the stable wire names "CONCISE" / "STANDARD" /
 * "DETAILED" / "ULTRA" (see [WritingStore] tier tables); it drives the
 * prompt's length instruction and the request's output budget whenever
 * [novelMode] does not override it with [customKChars] (a target length
 * in thousands of characters, e.g. 3 = roughly 3000 characters).
 *
 * [style] is one of "PROFESSIONAL" / "VIVID" / "CREATIVE".
 *
 * [temperature] / [topP] are sampling overrides: null means "unset —
 * send nothing and let the provider / per-model config decide", which
 * is exactly what every pre-existing session sends today. 0.0 is a
 * legitimate temperature value, hence the nullable encoding (same
 * contract as ModelOverrides).
 */
@Serializable
data class WritingSettings(
    val novelMode: Boolean = false,
    val lengthTier: String = WritingStore.TIER_STANDARD,
    val customKChars: Int? = null,
    val style: String = WritingStore.STYLE_PROFESSIONAL,
    val temperature: Double? = null,
    val topP: Double? = null,
)

/**
 * [T-writing-controls] File-backed store for per-session
 * [WritingSettings], mirroring [TavernStore]'s side-file pattern:
 *
 *   `<filesDir>/minis-global/writing/sessions/<sessionId>.json`
 *
 * Deliberately NOT Room (same reasoning as tavern): writing prefs are
 * app-local presentation state, and a side file means zero migration
 * risk on the chat tables. All functions do synchronous file IO —
 * callers must hop to an IO thread. Failures follow [PersonaStore]'s
 * contract: writes degrade silently (logged), unreadable files read
 * back as null, so a corrupt settings file can never break chat.
 */
object WritingStore {

    private const val TAG = "WritingStore"
    private const val WRITING_SUBDIR = "minis-global/writing"

    // Length tier wire names (persisted — never rename).
    const val TIER_CONCISE = "CONCISE"
    const val TIER_STANDARD = "STANDARD"
    const val TIER_DETAILED = "DETAILED"
    const val TIER_ULTRA = "ULTRA"

    // Prose style wire names (persisted — never rename).
    const val STYLE_PROFESSIONAL = "PROFESSIONAL"
    const val STYLE_VIVID = "VIVID"
    const val STYLE_CREATIVE = "CREATIVE"

    /** The settings every untouched session behaves with. */
    val DEFAULT = WritingSettings()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private fun sessionsDir(context: Context): File =
        File(File(context.filesDir, WRITING_SUBDIR), "sessions")

    /** Ids become file names — strip anything that is not file-safe. */
    private fun safeFileName(id: String): String =
        id.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun sessionFile(context: Context, sessionId: String): File =
        File(sessionsDir(context), "${safeFileName(sessionId)}.json")

    fun load(context: Context, sessionId: String): WritingSettings? {
        val file = sessionFile(context, sessionId)
        if (!file.isFile) return null
        return try {
            json.decodeFromString<WritingSettings>(file.readText())
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "unreadable file ${file.name}: ${t.message}")
            null
        }
    }

    @Synchronized
    fun save(context: Context, sessionId: String, settings: WritingSettings) {
        val file = sessionFile(context, sessionId)
        try {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(settings))
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "write ${file.name} failed: ${t.message}")
        }
    }

    @Synchronized
    fun delete(context: Context, sessionId: String) {
        val file = sessionFile(context, sessionId)
        try {
            if (file.isFile) file.delete()
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "delete ${file.name} failed: ${t.message}")
        }
    }
}
