package com.example.template.support.persistence

import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.sql.DriverManager
import java.util.UUID

// A fresh in-memory H2 database built by the production Flyway migrations for one persistence adapter test, so
// tests share no rows and need no cleanup.
class MigratedH2Database(
    target: String = LATEST,
) : AutoCloseable {
    private val dataSource =
        HikariDataSource().apply {
            jdbcUrl = "jdbc:h2:mem:adapter-${UUID.randomUUID()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            username = "sa"
            password = ""
            maximumPoolSize = 2
        }

    init {
        migrate(target)
    }

    // One attempt, so Exposed reports an expected SQL failure once instead of retrying the statement.
    private val database = Database.connect(dataSource, databaseConfig = DatabaseConfig { defaultMaxAttempts = 1 })

    fun migrate(target: String = LATEST) {
        Flyway
            .configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .target(target)
            .load()
            .migrate()
    }

    // Adapters join the caller's transaction instead of opening one, so each test call supplies a short test-only
    // transaction.
    fun <T> inTransaction(block: () -> T): T = transaction(database) { block() }

    fun execute(sql: String) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(sql) }
        }
    }

    // DB_CLOSE_DELAY=-1 keeps the database after the pool closes, so it is dropped through a connection outside the
    // pool. Returning a pooled connection after SHUTDOWN fails because the pool resets the closed connection.
    override fun close() {
        TransactionManager.closeAndUnregister(database)
        dataSource.close()
        DriverManager.getConnection(dataSource.jdbcUrl, "sa", "").use { connection ->
            connection.createStatement().use { it.execute("SHUTDOWN") }
        }
    }

    private companion object {
        const val LATEST = "latest"
    }
}
