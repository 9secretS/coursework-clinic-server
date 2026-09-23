package ru.mirea.clinic

import ru.mirea.clinic.config.AppConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Тестовые сценарии 1-3 (таблица Б.1): чтение конфигурации сервера
 * из переменных окружения.
 */
class AppConfigTest {

    @Test
    fun `fromEnv uses defaults when optional values are missing`() {
        val config = AppConfig.fromEnv(emptyMap())

        assertEquals(8080, config.port)
        assertEquals("jdbc:postgresql://localhost:5432/clinic", config.database.url)
        assertEquals("postgres", config.database.user)
        assertEquals("postgres", config.database.password)
        assertNull(config.firebase.serviceAccountPath)
    }

    @Test
    fun `fromEnv reads explicit database and firebase settings`() {
        val config = AppConfig.fromEnv(
            mapOf(
                "PORT" to "9090",
                "DATABASE_URL" to "jdbc:postgresql://db.example.com:5432/clinic",
                "DATABASE_USER" to "clinic_user",
                "DATABASE_PASSWORD" to "secret",
                "FIREBASE_SERVICE_ACCOUNT_PATH" to "C:/keys/firebase.json",
            )
        )

        assertEquals(9090, config.port)
        assertEquals("jdbc:postgresql://db.example.com:5432/clinic", config.database.url)
        assertEquals("clinic_user", config.database.user)
        assertEquals("secret", config.database.password)
        assertEquals("C:/keys/firebase.json", config.firebase.serviceAccountPath)
    }

    @Test
    fun `fromEnv falls back to default port when port is invalid`() {
        val config = AppConfig.fromEnv(mapOf("PORT" to "not-a-number"))

        assertEquals(8080, config.port)
    }
}
