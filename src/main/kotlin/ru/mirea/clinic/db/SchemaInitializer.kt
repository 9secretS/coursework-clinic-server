package ru.mirea.clinic.db

import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.sql.DataSource

/**
 * Инициализация схемы базы данных и подготовка начальных данных
 * при запуске сервера.
 */
class SchemaInitializer(private val dataSource: DataSource) {

    private val logger = LoggerFactory.getLogger(SchemaInitializer::class.java)

    fun initialize() {
        executeScript("/db/schema.sql")
        executeScript("/db/seed.sql")
        seedSlots()
        logger.info("Database schema is ready")
    }

    private fun executeScript(resourcePath: String) {
        val script = javaClass.getResourceAsStream(resourcePath)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("SQL script not found: $resourcePath")

        dataSource.withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.execute(script)
            }
        }
    }

    /**
     * Создает расписание приема на ближайшие рабочие дни,
     * если слоты еще не заведены.
     */
    private fun seedSlots() {
        dataSource.inTransaction { connection ->
            val existing = connection.prepareStatement("SELECT count(*) FROM schedule_slots").use { statement ->
                statement.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
            if (existing > 0) {
                logger.info("Schedule slots already exist: {}", existing)
                return@inTransaction
            }

            val doctorIds = connection.prepareStatement("SELECT id FROM doctors ORDER BY id").use { statement ->
                statement.executeQuery().use { rs ->
                    buildList { while (rs.next()) add(rs.getInt("id")) }
                }
            }

            val insert = """
                INSERT INTO schedule_slots (doctor_id, starts_at, ends_at, status)
                VALUES (?, ?, ?, 'FREE')
                ON CONFLICT (doctor_id, starts_at) DO NOTHING
            """.trimIndent()

            connection.prepareStatement(insert).use { statement ->
                val today = LocalDate.now()
                doctorIds.forEach { doctorId ->
                    (1..WORKING_DAYS).forEach { dayOffset ->
                        val day = today.plusDays(dayOffset.toLong())
                        var time = LocalTime.of(WORK_START_HOUR, 0)
                        while (time.hour < WORK_END_HOUR) {
                            val startsAt = LocalDateTime.of(day, time)
                            val endsAt = startsAt.plusMinutes(SLOT_MINUTES)
                            statement.setInt(1, doctorId)
                            statement.setTimestamp(2, java.sql.Timestamp.valueOf(startsAt))
                            statement.setTimestamp(3, java.sql.Timestamp.valueOf(endsAt))
                            statement.addBatch()
                            time = time.plusMinutes(SLOT_MINUTES)
                        }
                    }
                }
                statement.executeBatch()
            }
            logger.info("Schedule slots created for {} doctors", doctorIds.size)
        }
    }

    private companion object {
        const val WORKING_DAYS = 7
        const val WORK_START_HOUR = 9
        const val WORK_END_HOUR = 17
        const val SLOT_MINUTES = 30L
    }
}
