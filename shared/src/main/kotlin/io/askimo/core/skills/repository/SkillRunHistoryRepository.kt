/* SPDX-License-Identifier: AGPLv3
 *
 * Copyright (c) 2026 Hai Nguyen
 */
package io.askimo.core.skills.repository

import io.askimo.core.db.AbstractSQLiteRepository
import io.askimo.core.db.DatabaseManager
import io.askimo.core.logging.logger
import io.askimo.core.skills.domain.SkillRunHistoryTable
import io.askimo.core.skills.domain.SkillRunRecord
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Repository for persisting and querying [SkillRunRecord] entries.
 *
 * [SkillRunRecord.activityLog] is stored as a newline-delimited text block so the
 * SQLite file stays human-readable without requiring a JSON library.
 */
class SkillRunHistoryRepository internal constructor(
    databaseManager: DatabaseManager = DatabaseManager.getInstance(),
) : AbstractSQLiteRepository(databaseManager) {

    private val log = logger<SkillRunHistoryRepository>()

    /**
     * Persists a new run record. The [record.id] must already be set (UUID).
     */
    fun save(record: SkillRunRecord) {
        transaction(database) {
            SkillRunHistoryTable.insert {
                it[id] = record.id
                it[skillPath] = record.skillPath
                it[userInput] = record.userInput
                it[response] = record.response
                it[error] = record.error
                it[activityLog] = encodeLog(record.activityLog)
                it[createdAt] = record.createdAt
            }
        }
        log.debug("Saved skill run record '{}' for skill '{}'", record.id, record.skillPath)
    }

    /**
     * Returns up to [limit] run records across all skills, newest first.
     */
    fun findAll(limit: Int = 200): List<SkillRunRecord> = transaction(database) {
        SkillRunHistoryTable
            .selectAll()
            .orderBy(SkillRunHistoryTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map {
                SkillRunRecord(
                    id = it[SkillRunHistoryTable.id],
                    skillPath = it[SkillRunHistoryTable.skillPath],
                    userInput = it[SkillRunHistoryTable.userInput],
                    response = it[SkillRunHistoryTable.response],
                    error = it[SkillRunHistoryTable.error],
                    activityLog = decodeLog(it[SkillRunHistoryTable.activityLog]),
                    createdAt = it[SkillRunHistoryTable.createdAt],
                )
            }
    }

    /**
     * Returns up to [limit] run records for the given [skillPath], newest first.
     */
    fun findBySkillPath(skillPath: String, limit: Int = 50): List<SkillRunRecord> = transaction(database) {
        SkillRunHistoryTable
            .selectAll()
            .where { SkillRunHistoryTable.skillPath eq skillPath }
            .orderBy(SkillRunHistoryTable.createdAt, SortOrder.DESC)
            .limit(limit)
            .map {
                SkillRunRecord(
                    id = it[SkillRunHistoryTable.id],
                    skillPath = it[SkillRunHistoryTable.skillPath],
                    userInput = it[SkillRunHistoryTable.userInput],
                    response = it[SkillRunHistoryTable.response],
                    error = it[SkillRunHistoryTable.error],
                    activityLog = decodeLog(it[SkillRunHistoryTable.activityLog]),
                    createdAt = it[SkillRunHistoryTable.createdAt],
                )
            }
    }

    /**
     * Deletes all run records for the given [skillPath].
     */
    fun deleteBySkillPath(skillPath: String) {
        transaction(database) {
            SkillRunHistoryTable.deleteWhere { SkillRunHistoryTable.skillPath eq skillPath }
        }
        log.debug("Deleted all run records for skill '{}'", skillPath)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun encodeLog(entries: List<String>): String = entries.joinToString("\n") { it.replace("\n", "\\n") }

    private fun decodeLog(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return raw.lines().map { it.replace("\\n", "\n") }
    }
}
