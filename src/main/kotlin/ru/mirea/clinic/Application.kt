package ru.mirea.clinic

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory
import ru.mirea.clinic.config.AppConfig
import ru.mirea.clinic.db.DatabaseFactory
import ru.mirea.clinic.db.SchemaInitializer
import ru.mirea.clinic.plugins.configurePlugins
import ru.mirea.clinic.routes.configureRouting
import ru.mirea.clinic.security.FirebaseTokenVerifier

/**
 * Точка входа серверной части приложения.
 *
 * Порядок запуска: чтение конфигурации из переменных окружения,
 * подключение к PostgreSQL, инициализация схемы и начальных данных,
 * запуск HTTP-сервера Ktor с маршрутами REST API.
 */
fun main() {
    val logger = LoggerFactory.getLogger("ru.mirea.clinic.Application")
    val config = AppConfig.fromEnv()

    val verifier = FirebaseTokenVerifier(config.firebase)
    if (!verifier.isConfigured) {
        logger.warn(
            "FIREBASE_SERVICE_ACCOUNT_PATH is not set: server runs in demo mode " +
                "and treats the Authorization token as a user id"
        )
    }

    val databaseFactory = DatabaseFactory(config.database)
    SchemaInitializer(databaseFactory.dataSource).initialize()

    Runtime.getRuntime().addShutdownHook(Thread { databaseFactory.close() })

    logger.info("Starting Ktor server on port {}", config.port)
    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        configurePlugins()
        configureRouting(databaseFactory, verifier)
    }.start(wait = true)
}
