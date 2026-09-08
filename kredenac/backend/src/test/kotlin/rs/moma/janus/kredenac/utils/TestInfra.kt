package rs.moma.janus.kredenac.utils

import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import rs.moma.janus.kredenac.tables.RefreshTokenTable
import rs.moma.janus.kredenac.tables.CredentialTable
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import rs.moma.janus.kredenac.tables.FilesTable
import rs.moma.janus.kredenac.tables.NotesTable
import rs.moma.janus.kredenac.tables.UserTable
import org.jetbrains.exposed.v1.jdbc.Database
import rs.moma.janus.kredenac.common.Env
import rs.moma.janus.kredenac.common.Tls
import io.lettuce.core.api.coroutines
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.SslOptions
import java.security.SecureRandom
import io.lettuce.core.RedisURI
import kotlin.io.path.readText
import java.sql.DriverManager
import kotlin.io.path.exists
import kotlin.io.path.Path

// Real Postgres and Redis, but never the ones used by the app:
// a separate database and a separate Redis index, both emptied before each test.
@OptIn(ExperimentalLettuceCoroutinesApi::class)
object TestInfra {
    private const val TEST_DATABASE = "kredenac_test"
    private const val TEST_REDIS_INDEX = 15

    val hmacSecret: ByteArray by lazy { key() }
    val masterKey: ByteArray by lazy { key() }
    val piiEncryptionKey: ByteArray by lazy { key() }
    val tokenEncryptionKey: ByteArray by lazy { key() }

    private fun key() = ByteArray(32).also(SecureRandom()::nextBytes)

    private fun delivered(service: String, name: String) =
        Path(Env.get("LOKOT_DIR"), service, name).let { it.takeIf { it.exists() } ?: Path("..", it.toString()) }
            .readText().trim()

    private val host get() = Env.get("POSTGRES_HOST")
    private val port get() = Env.get("POSTGRES_PORT")
    private val user get() = delivered("postgres", "user")
    private val password get() = delivered("postgres", "password")

    private val database: Database by lazy {
        createTestDatabaseIfMissing()
        val db = Database.connect(
            url = "jdbc:postgresql://$host:$port/$TEST_DATABASE",
            driver = "org.postgresql.Driver",
            user = user,
            password = password
        )
        transaction(db) {
            val tables = arrayOf(UserTable, CredentialTable, RefreshTokenTable, NotesTable, FilesTable)
            SchemaUtils.create(tables = tables)
            SchemaUtils.addMissingColumnsStatements(tables = tables).forEach { exec(it) }
        }
        db
    }

    private fun createTestDatabaseIfMissing() {
        DriverManager.getConnection("jdbc:postgresql://$host:$port/postgres", user, password).use { connection ->
            val exists = connection.createStatement()
                .executeQuery("SELECT 1 FROM pg_database WHERE datname = '$TEST_DATABASE'")
                .use { it.next() }
            if (!exists) connection.createStatement().execute("CREATE DATABASE $TEST_DATABASE")
        }
    }

    val redis: RedisCoroutinesCommands<String, String> by lazy {
        val uri = RedisURI.Builder.redis(Env.get("REDIS_HOST"), Env.get("REDIS_PORT").toInt())
            .withSsl(true).withVerifyPeer(true)
            .withPassword(delivered("redis", "redis.conf").removePrefix("requirepass ").toCharArray())
            .withDatabase(TEST_REDIS_INDEX)
            .build()

        val authority = Path(Env.get("LOKOT_DIR"), "redis", "ca.crt").let {
            it.takeIf { it.exists() } ?: Path("..", it.toString())
        }
        val client = RedisClient.create(uri)
        client.options = ClientOptions.builder()
            .sslOptions(SslOptions.builder().jdkSslProvider().trustManager(Tls.trustManager(authority)).build())
            .build()
        client.connect().coroutines()
    }

    suspend fun reset() {
        transaction(database) {
            exec(
                "TRUNCATE ${FilesTable.tableName}, ${NotesTable.tableName}, " +
                        "${RefreshTokenTable.tableName}, ${CredentialTable.tableName}, ${UserTable.tableName} CASCADE"
            )
        }
        redis.flushdb()
    }
}
