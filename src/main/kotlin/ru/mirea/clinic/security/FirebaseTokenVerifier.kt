package ru.mirea.clinic.security

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import org.slf4j.LoggerFactory
import ru.mirea.clinic.config.FirebaseConfig
import java.io.File
import java.io.FileInputStream

/**
 * Пользователь, полученный из проверенного токена Firebase Authentication.
 */
data class AuthenticatedUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
)

/**
 * Проверка id-токена Firebase Authentication на стороне сервера.
 *
 * Если путь к service account не задан, verifier создается, но помечается
 * как ненастроенный ([isConfigured] = false), и сервер работает в
 * демонстрационном режиме. Если путь задан, но файл отсутствует,
 * сервер останавливается на старте (fail fast).
 */
class FirebaseTokenVerifier(private val config: FirebaseConfig) {

    private val logger = LoggerFactory.getLogger(FirebaseTokenVerifier::class.java)

    private val serviceAccountFile: File? = config.serviceAccountPath
        ?.takeIf { it.isNotBlank() }
        ?.let { path ->
            val file = File(path)
            require(file.exists()) {
                "Firebase service account file does not exist: $path"
            }
            file
        }

    /** Настроена ли реальная проверка токенов Firebase. */
    val isConfigured: Boolean
        get() = serviceAccountFile != null

    private val firebaseApp: FirebaseApp? by lazy {
        val file = serviceAccountFile ?: return@lazy null
        val existing = FirebaseApp.getApps().firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        existing ?: FileInputStream(file).use { stream ->
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(stream))
                .build()
            FirebaseApp.initializeApp(options)
        }
    }

    /**
     * Проверяет токен и возвращает пользователя.
     * В демонстрационном режиме токен интерпретируется как uid пользователя.
     */
    fun verify(idToken: String): AuthenticatedUser? {
        if (idToken.isBlank()) return null
        if (!isConfigured) {
            logger.debug("Firebase is not configured, demo mode is used for token")
            return AuthenticatedUser(uid = idToken, email = null, displayName = null)
        }
        val app = firebaseApp ?: return null
        return runCatching {
            val token = FirebaseAuth.getInstance(app).verifyIdToken(idToken)
            AuthenticatedUser(
                uid = token.uid,
                email = token.email,
                displayName = token.name,
            )
        }.onFailure { error ->
            logger.warn("Firebase token verification failed: {}", error.message)
        }.getOrNull()
    }
}
