package ru.mirea.clinic.repository

import ru.mirea.clinic.db.withConnection
import ru.mirea.clinic.model.DoctorReview
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Доступ к отзывам и оценкам врачей.
 */
class ReviewRepository(private val dataSource: DataSource) {

    fun findByDoctor(doctorId: Int, limit: Int = 20): List<DoctorReview> =
        dataSource.withConnection { connection ->
            val sql = """
                SELECT r.id, r.doctor_id, r.rating, r.review_text, r.created_at, p.full_name
                FROM doctor_reviews r
                         JOIN patients p ON p.id = r.patient_id
                WHERE r.doctor_id = ?
                ORDER BY r.created_at DESC
                LIMIT ?
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, doctorId)
                statement.setInt(2, limit)
                statement.executeQuery().use { rs -> rs.mapAll { it.toReview() } }
            }
        }

    fun create(doctorId: Int, patientId: Int, rating: Int, text: String?): DoctorReview =
        dataSource.withConnection { connection ->
            val sql = """
                INSERT INTO doctor_reviews (doctor_id, patient_id, rating, review_text)
                VALUES (?, ?, ?, ?)
                RETURNING id, doctor_id, rating, review_text, created_at,
                          (SELECT full_name FROM patients WHERE id = ?) AS full_name
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setInt(1, doctorId)
                statement.setInt(2, patientId)
                statement.setInt(3, rating)
                statement.setString(4, text)
                statement.setInt(5, patientId)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "Review was not created" }
                    rs.toReview()
                }
            }
        }
}

private fun ResultSet.toReview(): DoctorReview = DoctorReview(
    id = getInt("id"),
    doctorId = getInt("doctor_id"),
    patientName = getString("full_name"),
    rating = getInt("rating"),
    text = getString("review_text"),
    createdAt = getTimestamp("created_at").toLocalDateTime().toString(),
)
