package ru.mirea.clinic.repository

import ru.mirea.clinic.db.withConnection
import ru.mirea.clinic.model.Patient
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * Доступ к данным пациентов приложения.
 * Пациент связывается с учетной записью Firebase по полю firebase_uid.
 */
class PatientRepository(private val dataSource: DataSource) {

    fun findByUid(firebaseUid: String): Patient? = dataSource.withConnection { connection ->
        val sql = "SELECT id, firebase_uid, full_name, phone FROM patients WHERE firebase_uid = ?"
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, firebaseUid)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toPatient() else null }
        }
    }

    /**
     * Возвращает существующего пациента или создает нового
     * при первом обращении пользователя к серверу.
     */
    fun findOrCreate(firebaseUid: String, fullName: String, phone: String? = null): Patient {
        findByUid(firebaseUid)?.let { return it }
        return dataSource.withConnection { connection ->
            val sql = """
                INSERT INTO patients (firebase_uid, full_name, phone)
                VALUES (?, ?, ?)
                ON CONFLICT (firebase_uid) DO UPDATE SET full_name = EXCLUDED.full_name
                RETURNING id, firebase_uid, full_name, phone
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, firebaseUid)
                statement.setString(2, fullName)
                statement.setString(3, phone)
                statement.executeQuery().use { rs ->
                    check(rs.next()) { "Patient was not created" }
                    rs.toPatient()
                }
            }
        }
    }

    fun updateProfile(firebaseUid: String, fullName: String, phone: String?): Patient? =
        dataSource.withConnection { connection ->
            val sql = """
                UPDATE patients
                SET full_name = ?, phone = ?
                WHERE firebase_uid = ?
                RETURNING id, firebase_uid, full_name, phone
            """.trimIndent()
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, fullName)
                statement.setString(2, phone)
                statement.setString(3, firebaseUid)
                statement.executeQuery().use { rs -> if (rs.next()) rs.toPatient() else null }
            }
        }
}

private fun ResultSet.toPatient(): Patient = Patient(
    id = getInt("id"),
    firebaseUid = getString("firebase_uid"),
    fullName = getString("full_name"),
    phone = getString("phone"),
)
