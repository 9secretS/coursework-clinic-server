package ru.mirea.clinic.repository

import ru.mirea.clinic.db.inTransaction
import ru.mirea.clinic.db.withConnection
import ru.mirea.clinic.model.Appointment
import java.sql.ResultSet
import javax.sql.DataSource

/** Результат операции создания записи на прием. */
sealed interface BookingResult {
    data class Success(val appointment: Appointment) : BookingResult
    data object SlotNotFound : BookingResult
    data object SlotAlreadyBooked : BookingResult
}

/**
 * Доступ к записям пациентов на прием.
 */
class AppointmentRepository(private val dataSource: DataSource) {

    private val baseQuery = """
        SELECT a.id            AS appointment_id,
               a.status        AS appointment_status,
               a.created_at    AS created_at,
               a.cancelled_at  AS cancelled_at,
               sl.id           AS id,
               sl.doctor_id    AS doctor_id,
               sl.starts_at    AS starts_at,
               sl.ends_at      AS ends_at,
               sl.status       AS status,
               d.id            AS doctor_pk,
               d.full_name     AS full_name,
               d.room          AS room,
               d.description   AS description,
               d.experience_years AS experience_years,
               s.name          AS specialty,
               dep.name        AS department,
               COALESCE(r.avg_rating, 0)   AS rating,
               COALESCE(r.review_count, 0) AS review_count
        FROM appointments a
                 JOIN schedule_slots sl ON sl.id = a.slot_id
                 JOIN doctors d ON d.id = sl.doctor_id
                 JOIN specialties s ON s.id = d.specialty_id
                 JOIN departments dep ON dep.id = d.department_id
                 LEFT JOIN (SELECT doctor_id,
                                   ROUND(AVG(rating)::numeric, 1) AS avg_rating,
                                   COUNT(*)                       AS review_count
                            FROM doctor_reviews
                            GROUP BY doctor_id) r ON r.doctor_id = d.id
    """.trimIndent()

    fun findByPatient(patientId: Int): List<Appointment> = dataSource.withConnection { connection ->
        val sql = "$baseQuery WHERE a.patient_id = ? ORDER BY sl.starts_at"
        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, patientId)
            statement.executeQuery().use { rs -> rs.mapAll { it.toAppointment() } }
        }
    }

    /**
     * Создает запись на прием, помечая выбранный слот занятым.
     * Проверка занятости и изменение статуса выполняются в одной транзакции.
     */
    fun book(patientId: Int, slotId: Int): BookingResult = dataSource.inTransaction { connection ->
        val status = connection
            .prepareStatement("SELECT status FROM schedule_slots WHERE id = ? FOR UPDATE")
            .use { statement ->
                statement.setInt(1, slotId)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getString("status") else null }
            }
            ?: return@inTransaction BookingResult.SlotNotFound

        if (status != "FREE") {
            return@inTransaction BookingResult.SlotAlreadyBooked
        }

        connection.prepareStatement("UPDATE schedule_slots SET status = 'BOOKED' WHERE id = ?")
            .use { statement ->
                statement.setInt(1, slotId)
                statement.executeUpdate()
            }

        val appointmentId = connection
            .prepareStatement(
                "INSERT INTO appointments (patient_id, slot_id, status) VALUES (?, ?, 'ACTIVE') RETURNING id"
            )
            .use { statement ->
                statement.setInt(1, patientId)
                statement.setInt(2, slotId)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "Appointment was not created" }
                    rs.getInt("id")
                }
            }

        val created = connection.prepareStatement("$baseQuery WHERE a.id = ?").use { statement ->
            statement.setInt(1, appointmentId)
            statement.executeQuery().use { rs ->
                check(rs.next()) { "Appointment was not found after creation" }
                rs.toAppointment()
            }
        }
        BookingResult.Success(created)
    }

    /**
     * Отменяет запись пациента и освобождает слот расписания.
     */
    fun cancel(patientId: Int, appointmentId: Int): Boolean = dataSource.inTransaction { connection ->
        val slotId = connection
            .prepareStatement(
                "SELECT slot_id FROM appointments WHERE id = ? AND patient_id = ? AND status = 'ACTIVE'"
            )
            .use { statement ->
                statement.setInt(1, appointmentId)
                statement.setInt(2, patientId)
                statement.executeQuery().use { rs -> if (rs.next()) rs.getInt("slot_id") else null }
            }
            ?: return@inTransaction false

        connection
            .prepareStatement("UPDATE appointments SET status = 'CANCELLED', cancelled_at = now() WHERE id = ?")
            .use { statement ->
                statement.setInt(1, appointmentId)
                statement.executeUpdate()
            }

        connection.prepareStatement("UPDATE schedule_slots SET status = 'FREE' WHERE id = ?")
            .use { statement ->
                statement.setInt(1, slotId)
                statement.executeUpdate()
            }
        true
    }
}

private fun ResultSet.toAppointment(): Appointment = Appointment(
    id = getInt("appointment_id"),
    doctor = toDoctor().copy(id = getInt("doctor_pk")),
    slot = toSlot(),
    status = getString("appointment_status"),
    createdAt = getTimestamp("created_at").toLocalDateTime().toString(),
    cancelledAt = getTimestamp("cancelled_at")?.toLocalDateTime()?.toString(),
)
