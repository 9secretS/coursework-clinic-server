package ru.mirea.clinic.config

/**
 * Параметры подключения к базе данных PostgreSQL.
 */
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
)

/**
 * Параметры Firebase Authentication.
 * Путь к service account может отсутствовать: в этом случае сервер
 * работает в демонстрационном режиме и не проверяет подпись токена.
 */
data class FirebaseConfig(
    val serviceAccountPath: String? = null,
)

/**
 * Конфигурация серверной части приложения.
 * Читается из переменных окружения, что позволяет использовать
 * локальную базу данных при разработке и удаленную при развертывании.
 */
data class AppConfig(
    val port: Int,
    val database: DatabaseConfig,
    val firebase: FirebaseConfig,
) {
    companion object {
        const val DEFAULT_PORT: Int = 8080
        const val DEFAULT_DATABASE_URL: String = "jdbc:postgresql://localhost:5432/clinic"
        const val DEFAULT_DATABASE_USER: String = "postgres"
        const val DEFAULT_DATABASE_PASSWORD: String = "postgres"

        /**
         * Собирает конфигурацию из переданной карты переменных окружения.
         * Отсутствующие значения заменяются значениями по умолчанию,
         * некорректный порт не приводит к падению приложения.
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): AppConfig {
            val port = env["PORT"]?.toIntOrNull() ?: DEFAULT_PORT
            val database = DatabaseConfig(
                url = env["DATABASE_URL"].orEmptyToNull() ?: DEFAULT_DATABASE_URL,
                user = env["DATABASE_USER"].orEmptyToNull() ?: DEFAULT_DATABASE_USER,
                password = env["DATABASE_PASSWORD"].orEmptyToNull() ?: DEFAULT_DATABASE_PASSWORD,
            )
            val firebase = FirebaseConfig(
                serviceAccountPath = env["FIREBASE_SERVICE_ACCOUNT_PATH"].orEmptyToNull(),
            )
            return AppConfig(port = port, database = database, firebase = firebase)
        }

        private fun String?.orEmptyToNull(): String? = this?.takeIf { it.isNotBlank() }
    }
}
