package ru.mirea.clinic.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.slf4j.LoggerFactory
import ru.mirea.clinic.config.DatabaseConfig
import java.sql.Connection
import javax.sql.DataSource

/**
 * Фабрика подключения к PostgreSQL. Пул соединений создается один раз
 * при запуске сервера, параметры берутся из переменных окружения.
 */
class DatabaseFactory(private val config: DatabaseConfig) {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    val dataSource: DataSource by lazy { createDataSource() }

    private fun createDataSource(): HikariDataSource {
        logger.info("Connecting to database {}", config.url)
        val hikari = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = 10
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            validate()
        }
        return HikariDataSource(hikari)
    }

    fun isAvailable(): Boolean = runCatching {
        dataSource.connection.use { connection -> connection.isValid(2) }
    }.getOrDefault(false)

    fun close() {
        (dataSource as? HikariDataSource)?.close()
    }
}

/**
 * Выполняет блок кода на соединении из пула.
 */
fun <T> DataSource.withConnection(block: (Connection) -> T): T =
    connection.use { connection -> block(connection) }

/**
 * Выполняет блок кода в транзакции.
 */
fun <T> DataSource.inTransaction(block: (Connection) -> T): T = connection.use { connection ->
    val previous = connection.autoCommit
    connection.autoCommit = false
    try {
        val result = block(connection)
        connection.commit()
        result
    } catch (error: Throwable) {
        connection.rollback()
        throw error
    } finally {
        connection.autoCommit = previous
    }
}
