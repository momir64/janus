package rs.moma.janus.kredenac.plugins

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.tables.RefreshTokenTable
import rs.moma.janus.kredenac.tables.CredentialTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import rs.moma.janus.kredenac.tables.FilesTable
import rs.moma.janus.kredenac.tables.NotesTable
import rs.moma.janus.kredenac.tables.UserTable
import org.jetbrains.exposed.v1.jdbc.Database
import com.zaxxer.hikari.HikariDataSource
import rs.moma.janus.kredenac.common.Env
import com.zaxxer.hikari.HikariConfig

fun configureDatabase() {
    val dbHost = Env.get("POSTGRES_HOST")
    val dbPort = Env.get("POSTGRES_PORT")
    val dbName = Env.get("POSTGRES_DB")

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://$dbHost:$dbPort/$dbName"
        username = Env.get("POSTGRES_USER")
        password = Env.get("POSTGRES_PASSWORD")
        driverClassName = "org.postgresql.Driver"
        maximumPoolSize = 10
    }
    val dataSource = HikariDataSource(hikariConfig)
    Database.connect(dataSource)

    transaction {
        val tables = arrayOf(UserTable, CredentialTable, RefreshTokenTable, NotesTable, FilesTable)
        SchemaUtils.create(tables = tables)
        SchemaUtils.addMissingColumnsStatements(tables = tables).forEach { exec(it) }
    }
}
