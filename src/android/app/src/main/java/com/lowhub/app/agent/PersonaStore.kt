package com.lowhub.app.agent

import android.content.Context
import com.lowhub.app.logging.AppLogger
import java.io.File
import java.util.UUID

/** One persona in the library: its stable id plus the parsed SOUL metadata. */
data class PersonaInfo(val id: String, val metadata: SoulMetadata)

/**
 * [T-multi-persona] A library of personas (Soul files) with one active.
 *
 * Layout under `<filesDir>/minis-global/memory/personas/`:
 *   - `<id>.md`   – one Soul file per persona, written with
 *                   [SoulMDParser.serialize]; the icon stays inline in its
 *                   in-memory form (emoji / data URI) — persona files do NOT
 *                   use the SOUL.icon.png sidecar, that split belongs to the
 *                   live SOUL.md only.
 *   - `active.txt` – the id of the currently active persona.
 *
 * The live SOUL.md (see [SoulStore]) remains the single source every
 * existing consumer reads (system prompt, chat identity, minis-config).
 * Switching the active persona copies that persona into SOUL.md through
 * [SoulStore.save], so no consumer needs to know personas exist.
 *
 * All functions do synchronous file IO — callers must hop to an IO thread.
 */
object PersonaStore {

    private const val TAG = "PersonaStore"
    private const val PERSONAS_SUBDIR = "minis-global/memory/personas"
    private const val ACTIVE_FILE_NAME = "active.txt"
    const val DEFAULT_ID = "default"

    private fun personasDir(context: Context): File = File(context.filesDir, PERSONAS_SUBDIR)

    private fun personaFile(context: Context, id: String): File =
        File(personasDir(context), "$id.md")

    private fun activeFile(context: Context): File =
        File(personasDir(context), ACTIVE_FILE_NAME)

    private fun personaFiles(context: Context): List<File> =
        personasDir(context).listFiles { f -> f.isFile && f.extension == "md" }?.toList()
            ?: emptyList()

    private fun readActiveRaw(context: Context): String? =
        try {
            activeFile(context).takeIf { it.isFile }?.readText()?.trim()?.ifEmpty { null }
        } catch (t: Throwable) {
            null
        }

    private fun writeActiveRaw(context: Context, id: String) {
        val file = activeFile(context)
        file.parentFile?.mkdirs()
        file.writeText(id)
    }

    /**
     * Seed the library from the live SOUL.md on first run: the current Soul
     * becomes the `default` persona and is marked active, so upgrading
     * single-persona users see no behavior change. Idempotent; failures are
     * logged and swallowed so app launch can never break on this.
     */
    @Synchronized
    fun ensureInitialized(context: Context) {
        try {
            if (personaFiles(context).isNotEmpty()) return
            val current = SoulStore.load(context)
                ?: SoulMDParser.parse(SoulStore.DEFAULT_CONTENT)
            personasDir(context).mkdirs()
            personaFile(context, DEFAULT_ID)
                .writeText(SoulMDParser.serialize(current))
            writeActiveRaw(context, DEFAULT_ID)
            AppLogger.info(TAG, "seeded persona library from current SOUL.md")
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "ensureInitialized failed: ${t.message}")
        }
    }

    /** The active persona id; falls back when active.txt is missing or stale. */
    fun activeId(context: Context): String {
        ensureInitialized(context)
        val ids = personaFiles(context).map { it.nameWithoutExtension }
        if (ids.isEmpty()) return DEFAULT_ID
        val stored = readActiveRaw(context)
        if (stored != null && stored in ids) return stored
        return if (DEFAULT_ID in ids) DEFAULT_ID else ids.sorted().first()
    }

    /** All personas: the active one first, the rest by name. */
    fun listPersonas(context: Context): List<PersonaInfo> {
        ensureInitialized(context)
        val active = activeId(context)
        val infos = personaFiles(context).mapNotNull { file ->
            try {
                val parsed = SoulMDParser.parse(file.readText())
                PersonaInfo(file.nameWithoutExtension, parsed.metadata)
            } catch (t: Throwable) {
                AppLogger.warning(TAG, "persona file unreadable (${file.name}): ${t.message}")
                null
            }
        }
        return infos.sortedWith(
            compareByDescending<PersonaInfo> { it.id == active }
                .thenBy { it.metadata.name.lowercase() },
        )
    }

    /** Parse one persona file; null when missing or unreadable. */
    fun loadPersona(context: Context, id: String): SoulFile? {
        val file = personaFile(context, id)
        if (!file.isFile) return null
        return try {
            SoulMDParser.parse(file.readText())
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "loadPersona($id) failed: ${t.message}")
            null
        }
    }

    private fun writePersonaFile(context: Context, id: String, file: SoulFile) {
        val target = personaFile(context, id)
        target.parentFile?.mkdirs()
        target.writeText(SoulMDParser.serialize(file))
    }

    /**
     * Persist edits to one persona. When it is the active one, the live
     * SOUL.md (and its metadata cache) is updated through [SoulStore.save]
     * so the change takes effect immediately.
     */
    @Synchronized
    fun savePersona(context: Context, id: String, file: SoulFile) {
        writePersonaFile(context, id, file)
        if (id == activeId(context)) {
            SoulStore.save(context, file)
        }
    }

    /**
     * Create a persona from the default content with [name] (blank →
     * "New Persona"). Does NOT activate it. Returns the new id.
     */
    @Synchronized
    fun createPersona(context: Context, name: String): String {
        ensureInitialized(context)
        val id = "p-" + UUID.randomUUID().toString().take(8)
        val base = SoulMDParser.parse(SoulStore.DEFAULT_CONTENT)
        val named = base.copy(
            metadata = base.metadata.copy(name = name.trim().ifBlank { "New Persona" }),
        )
        writePersonaFile(context, id, named)
        return id
    }

    /**
     * Delete a persona. Refuses (false) when it is the only one left. When
     * the active persona is deleted, the first remaining persona becomes
     * active and is written to the live SOUL.md.
     */
    @Synchronized
    fun deletePersona(context: Context, id: String): Boolean {
        ensureInitialized(context)
        if (personaFiles(context).size <= 1) return false
        val wasActive = id == activeId(context)
        try {
            personaFile(context, id).delete()
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "deletePersona($id) failed: ${t.message}")
            return false
        }
        if (wasActive) {
            // Do NOT go through setActive here: its snapshot step would
            // write the live soul back into the just-deleted file.
            val next = listPersonas(context).firstOrNull() ?: return true
            loadPersona(context, next.id)?.let { SoulStore.save(context, it) }
            try {
                writeActiveRaw(context, next.id)
            } catch (t: Throwable) {
                AppLogger.warning(TAG, "deletePersona re-activate failed: ${t.message}")
            }
        }
        return true
    }

    /**
     * Make [id] the active persona: snapshot the live SOUL.md back into
     * the outgoing persona's file first (so edits made through any other
     * path are not lost), then write the target persona into SOUL.md via
     * [SoulStore.save] and record it in active.txt. No-op for unknown ids.
     */
    @Synchronized
    fun setActive(context: Context, id: String) {
        ensureInitialized(context)
        val target = loadPersona(context, id) ?: return
        try {
            val currentId = activeId(context)
            if (currentId != id) {
                SoulStore.load(context)?.let { writePersonaFile(context, currentId, it) }
            }
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "setActive snapshot failed: ${t.message}")
        }
        SoulStore.save(context, target)
        try {
            writeActiveRaw(context, id)
        } catch (t: Throwable) {
            AppLogger.warning(TAG, "setActive write active.txt failed: ${t.message}")
        }
    }
}
