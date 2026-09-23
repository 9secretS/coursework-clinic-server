package ru.mirea.clinic.repository

import ru.mirea.clinic.db.withConnection
import ru.mirea.clinic.model.Doctor
import ru.mirea.clinic.model.Slot
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Доступ к данным врачей и их расписания.
 */
class DoctorRepository(private val dataSource: DataSource) {

    private val baseQuery = """
        SELECT d.id,
               d.full_name,
               d.room,
               d.description,
               d.experience_years,
               s.name AS specialty,
               dep.name AS department,
               COALESCE(r.avg_rating, 0)   AS rating,
               COALESCE(r.review_count, 0) AS review_count
        FROM doctors d
                 JOIN specialties s ON s.id = d.specialty_id
                 JOIN departments dep ON dep.id = d.department_id
                 LEFT JOIN (SELECT doctor_id,
                                   ROUND(AVG(rating)::numeric, 1) AS avg_rating,
                                   COUNT(*)                       AS review_count
                            FROM doctor_reviews
                            GROUP BY doctor_id) r ON r.doctor_id = d.id
    """.trimIndent()

    fun findAll(): List<Doctor> = dataSource.withConnection { connection ->
        connection.prepareStatement("$baseQuery ORDER BY d.full_name").use { statement ->
            statement.executeQuery().use { rs -> rs.mapAll { it.toDoctor() } }
        }
    }

    fun search(query: String): List<Doctor> = dataSource.withConnection { connection ->
        val sql = "$baseQuery WHERE d.full_name ILIKE ? OR s.name ILIKE ? ORDER BY d.full_name"
        connection.prepareStatement(sql).use { statement ->
            val pattern = "%${query.trim()}%"
            statement.setString(1, pattern)
            statement.setString(2, pattern)
            statement.executeQuery().use { rs -> rs.mapAll { it.toDoctor() } }
        }
    }

    fun findById(id: Int): Doctor? = dataSource.withConnection { connection ->
        connection.prepareStatement("$baseQuery WHERE d.id = ?").use { statement ->
            statement.setInt(1, id)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toDoctor() else null }
        }
    }

    /**
     * Свободные слоты врача, начиная с текущего момента.
     */
    fun findFreeSlots(doctorId: Int): List<Slot> = dataSource.withConnection { connection ->
        val sql = """
            SELECT id, doctor_id, starts_at, ends_at, status
            FROM schedule_slots
            WHERE doctor_id = ?
              AND status = 'FREE'
              AND starts_at > now()
            ORDER BY starts_at
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.setInt(1, doctorId)
            statement.executeQuery().use { rs -> rs.mapAll { it.toSlot() } }
        }
    }
}

internal fun ResultSet.toDoctor(): Doctor = Doctor(
    id = getInt("id"),
    fullName = getString("full_name"),
    specialty = getString("specialty"),
    department = getString("department"),
    room = getString("room"),
    description = getString("description"),
    experienceYears = getInt("experience_years"),
    rating = getDouble("rating"),
    reviewCount = getInt("review_count"),
)

internal fun ResultSet.toSlot(): Slot = Slot(
    id = getInt("id"),
    doctorId = getInt("doctor_id"),
    startsAt = getTimestamp("starts_at").toLocalDateTime().toString(),
    endsAt = getTimestamp("ends_at").toLocalDateTime().toString(),
    status = getString("status"),
)

internal fun <T> ResultSet.mapAll(mapper: (ResultSet) -> T): List<T> =
    buildList { while (next()) add(mapper(this@mapAll)) }
