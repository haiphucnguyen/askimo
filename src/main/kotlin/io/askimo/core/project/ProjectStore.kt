/* SPDX-License-Identifier: Apache-2.0
 *
 * Copyright (c) 2025 Hai Nguyen
 */
package io.askimo.core.project

import io.askimo.core.util.AskimoHome
import io.askimo.core.util.TimeUtil.stamp
import kotlinx.serialization.json.Json
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import java.nio.file.StandardOpenOption.WRITE
import java.util.UUID
import kotlin.io.path.exists

object ProjectStore {
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
    private val baseDir: Path get() = AskimoHome.base()
    private val projectsDir: Path get() = AskimoHome.projectsDir()
    private val activeFile: Path get() = baseDir.resolve("active")

    fun create(
        name: String,
        rootAbsPath: String,
    ): ProjectMeta {
        ensureLayout()
        if (getByName(name) != null) error("Project '$name' already exists.")
        val id = UUID.randomUUID().toString()
        val now = stamp()
        val meta =
            ProjectMeta(
                id = id,
                name = name,
                root = normalizeAbs(rootAbsPath),
                createdAt = now,
                updatedAt = now,
                lastUsedAt = now,
            )
        writeProjectFile(meta)
        setActive(meta.id)
        return meta
    }

    fun list(): List<ProjectMeta> {
        ensureLayout()
        return Files.list(projectsDir).use { stream ->
            stream
                .iterator()
                .asSequence()
                .filter { Files.isRegularFile(it) && it.fileName.toString().startsWith("prj_") }
                .mapNotNull { path: Path -> readProjectFile(path) }
                .toList()
        }
    }

    fun getById(id: String): ProjectMeta? = projectsDir.resolve("prj_$id.json").let { if (it.exists()) readProjectFile(it) else null }

    fun getByName(name: String): ProjectMeta? = list().firstOrNull { it.name.equals(name, ignoreCase = true) }

    fun save(meta: ProjectMeta) {
        writeProjectFile(meta.copy(updatedAt = stamp()))
    }

    fun setActive(projectId: String) {
        val meta = getById(projectId) ?: error("Unknown project id '$projectId'")
        val ptr = ActivePointer(projectId = meta.id, root = meta.root, selectedAt = stamp())
        atomicWrite(activeFile, json.encodeToString(ptr))
    }

    fun getActive(): Pair<ProjectMeta, ActivePointer>? {
        if (!activeFile.exists()) return null
        return runCatching {
            val ptr = json.decodeFromString<ActivePointer>(Files.readString(activeFile))
            val meta = getById(ptr.projectId) ?: return null
            meta to ptr
        }.getOrNull()
    }

    fun softDelete(id: String): Boolean {
        val file = projectsDir.resolve("prj_$id.json")
        if (!file.exists()) return false
        val trash = baseDir.resolve("trash").also { if (!it.exists()) Files.createDirectories(it) }
        Files.move(
            file,
            trash.resolve("${file.fileName}.${System.currentTimeMillis()}.bak"),
            REPLACE_EXISTING,
        )
        if (activeFile.exists()) {
            getActive()?.first?.let { if (it.id == id) Files.deleteIfExists(activeFile) }
        }
        return true
    }

    // ---- Internals ----
    private fun ensureLayout() {
        if (!baseDir.exists()) Files.createDirectories(baseDir)
        if (!projectsDir.exists()) Files.createDirectories(projectsDir)
    }

    private fun writeProjectFile(meta: ProjectMeta) {
        val path = projectsDir.resolve("prj_${meta.id}.json")
        val payload = ProjectFileV1(project = meta)
        atomicWrite(path, json.encodeToString(payload))
    }

    private fun readProjectFile(path: Path): ProjectMeta? = runCatching {
        val pf = json.decodeFromString<ProjectFileV1>(Files.readString(path))
        pf.project
    }.getOrNull()

    private fun atomicWrite(
        path: Path,
        content: String,
    ) {
        val parent = path.parent
        if (parent != null && !Files.exists(parent)) Files.createDirectories(parent)

        val tmp = path.resolveSibling("${path.fileName}.tmp")

        Files.writeString(
            tmp,
            content,
            CREATE,
            TRUNCATE_EXISTING,
            WRITE,
        )

        // Best-effort fsync (FileChannel has force())
        runCatching {
            FileChannel.open(tmp, READ).use { it.force(true) }
        }

        // Atomic move where supported; fall back to non-atomic replace if needed
        val moved =
            runCatching {
                Files.move(
                    tmp,
                    path,
                    REPLACE_EXISTING,
                    ATOMIC_MOVE,
                )
                true
            }.getOrElse {
                Files.move(tmp, path, REPLACE_EXISTING)
                true
            }

        // (optional) verify write succeeded
        if (!moved) error("Failed to write $path")
    }

    private fun normalizeAbs(p: String): String = Paths
        .get(p)
        .toAbsolutePath()
        .normalize()
        .toString()
}
