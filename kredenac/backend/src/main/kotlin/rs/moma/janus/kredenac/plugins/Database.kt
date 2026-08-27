package rs.moma.janus.kredenac.plugins

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.db.RefreshTokenTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import rs.moma.janus.kredenac.db.CredentialTable
import org.jetbrains.exposed.v1.jdbc.Database
import rs.moma.janus.kredenac.db.FilesTable
import rs.moma.janus.kredenac.db.NotesTable
import rs.moma.janus.kredenac.db.UserTable
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
        SchemaUtils.create(UserTable, CredentialTable, RefreshTokenTable, NotesTable, FilesTable)
    }
}
