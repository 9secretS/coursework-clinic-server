package ru.mirea.clinic

import ru.mirea.clinic.config.FirebaseConfig
import ru.mirea.clinic.security.FirebaseTokenVerifier
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Тестовые сценарии 4-5 (таблица Б.1): поведение проверки токена
 * Firebase Authentication при отсутствии или неверном пути к ключу.
 */
class FirebaseTokenVerifierTest {

    @Test
    fun `verifier is not configured when service account path is missing`() {
        val verifier = FirebaseTokenVerifier(FirebaseConfig(serviceAccountPath = null))

        assertFalse(verifier.isConfigured)
    }

    @Test
    fun `verifier fails fast when service account file does not exist`() {
        assertFailsWith<IllegalArgumentException> {
            FirebaseTokenVerifier(
                FirebaseConfig(serviceAccountPath = "C:/missing/firebase-service-account.json")
            )
        }
    }
}
