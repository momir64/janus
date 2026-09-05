package rs.moma.janus.kredenac.utils

import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
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
import io.lettuce.core.api.coroutines
import io.lettuce.core.ClientOptions
import io.lettuce.core.RedisClient
import io.lettuce.core.SslOptions
import kotlin.io.encoding.Base64
import io.lettuce.core.RedisURI
import java.sql.DriverManager
import java.io.File

// Real Postgres and Redis, but never the ones used by the app:
// a separate database and a separate Redis index, both emptied before each test.
@OptIn(ExperimentalLettuceCoroutinesApi::class)
object TestInfra {
    private const val TEST_DATABASE = "kredenac_test"
    private const val TEST_REDIS_INDEX = 15

    val hmacSecret: ByteArray by lazy { Env.getBytes("DB_HMAC_SECRET") }
    val masterKey: ByteArray by lazy { decode("MASTER_KEY_BASE64") }
    val piiEncryptionKey: ByteArray by lazy { decode("PII_ENCRYPTION_KEY_BASE64") }
    val tokenEncryptionKey: ByteArray by lazy { decode("TOKEN_ENCRYPTION_KEY_BASE64") }

    private fun decode(key: String) = Base64.withPadding(PRESENT_OPTIONAL).decode(Env.get(key))

    private val host get() = Env.get("POSTGRES_HOST")
    private val port get() = Env.get("POSTGRES_PORT")
    private val user get() = Env.get("POSTGRES_USER")
    private val password get() = Env.get("POSTGRES_PASSWORD")

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
            .withPassword(Env.get("REDIS_PASSWORD").toCharArray())
            .withDatabase(TEST_REDIS_INDEX)
            .build()

        val truststore = resolve(Env.get("REDIS_TLS_TRUSTSTORE_PATH"))
        val client = RedisClient.create(uri)
        client.options = ClientOptions.builder()
            .sslOptions(
                SslOptions.builder().jdkSslProvider()
                    .truststore(truststore, Env.get("REDIS_TLS_TRUSTSTORE_PASSWORD")).build()
            )
            .build()
        client.connect().coroutines()
    }

    // Paths in .env are relative to where the app runs, which is the directory above this one.
    private fun resolve(path: String): File = File(path).takeIf { it.exists() } ?: File("..", path)

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
