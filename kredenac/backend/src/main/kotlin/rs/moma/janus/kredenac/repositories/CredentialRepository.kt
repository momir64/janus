package rs.moma.janus.kredenac.repositories

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.tables.CredentialTable
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import rs.moma.janus.kredenac.common.toByteArray
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import rs.moma.janus.kredenac.common.Owner
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.uuid.Uuid

class StoredCredential(
    val id: Uuid,
    val userId: Uuid,
    val credentialId: ByteArray,
    val algorithm: String,
    val publicKey: ByteArray,
    val signCount: Long,
    val aaguid: Uuid?,
    val createdAt: Instant,
    val lastUsedAt: Instant?,
    val lastUsedIp: String?,
    val lastUsedLocation: String?
) {
    fun withUse(signCount: Long, at: Instant, ip: String?, location: String?) = StoredCredential(
        id, userId, credentialId, algorithm, publicKey, signCount, aaguid, createdAt, at, ip, location
    )
}

class CredentialRepository(private val hmacSecret: ByteArray) {
    suspend fun insert(
        userId: Uuid, credentialId: ByteArray,
        algorithm: String, publicKey: ByteArray, aaguid: Uuid?
    ): Uuid = withContext(Dispatchers.IO) {
        val credential = StoredCredential(
            Uuid.random(), userId, credentialId, algorithm, publicKey, 0,
            aaguid, Clock.System.now(), null, null, null
        )

        transaction {
            CredentialTable.insert {
                it[id] = credential.id
                it[CredentialTable.userId] = credential.userId
                it[CredentialTable.credentialId] = credential.credentialId
                it[CredentialTable.algorithm] = credential.algorithm
                it[CredentialTable.publicKey] = credential.publicKey
                it[signCount] = credential.signCount
                it[CredentialTable.aaguid] = credential.aaguid
                it[createdAt] = credential.createdAt
                it[integrityHash] = credential.hash()
            }
        }
        credential.id
    }

    suspend fun findByCredentialId(credentialId: ByteArray): StoredCredential? = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.selectAll()
                .where { CredentialTable.credentialId eq credentialId }
                .map { it.toStoredCredential() }
                .singleOrNull()
        }
    }

    context(owner: Owner)
    suspend fun findAll(): List<StoredCredential> = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.selectAll()
                .where { CredentialTable.userId eq owner.userId }
                .map { it.toStoredCredential() }
        }
    }

    suspend fun recordUse(credential: StoredCredential, newSignCount: Long, ip: String?, location: String?) =
        withContext(Dispatchers.IO) {
            val used = credential.withUse(newSignCount, Clock.System.now(), ip, location)
            transaction {
                CredentialTable.update({ (CredentialTable.id eq used.id) and (CredentialTable.userId eq used.userId) }) {
                    it[signCount] = used.signCount
                    it[lastUsedAt] = used.lastUsedAt
                    it[lastUsedIp] = used.lastUsedIp
                    it[lastUsedLocation] = used.lastUsedLocation
                    it[integrityHash] = used.hash()
                }
            }
        }

    context(owner: Owner)
    suspend fun delete(id: Uuid): Boolean = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.deleteWhere { (CredentialTable.id eq id) and (CredentialTable.userId eq owner.userId) } > 0
        }
    }

    context(owner: Owner)
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.deleteWhere { CredentialTable.userId eq owner.userId }
        }
    }

    private fun StoredCredential.hash(): String {
        val input = ByteArrayOutputStream()
        fun put(bytes: ByteArray?) {
            input.write((bytes?.size ?: 0).toLong().toByteArray())
            if (bytes != null) input.write(bytes)
        }
        put(id.toByteArray())
        put(userId.toByteArray())
        put(credentialId)
        put(algorithm.toByteArray())
        put(publicKey)
        put(signCount.toByteArray())
        put(aaguid?.toByteArray())
        put(createdAt.epochSeconds.toByteArray())
        put(lastUsedAt?.epochSeconds?.toByteArray())
        put(lastUsedIp?.toByteArray())
        put(lastUsedLocation?.toByteArray())
        return HmacUtil.hash(hmacSecret, input.toByteArray())
    }

    private fun ResultRow.toStoredCredential(): StoredCredential {
        val credential = StoredCredential(
            id = this[CredentialTable.id],
            userId = this[CredentialTable.userId],
            credentialId = this[CredentialTable.credentialId],
            algorithm = this[CredentialTable.algorithm],
            publicKey = this[CredentialTable.publicKey],
            signCount = this[CredentialTable.signCount],
            aaguid = this[CredentialTable.aaguid],
            createdAt = this[CredentialTable.createdAt],
            lastUsedAt = this[CredentialTable.lastUsedAt],
            lastUsedIp = this[CredentialTable.lastUsedIp],
            lastUsedLocation = this[CredentialTable.lastUsedLocation]
        )

        if (credential.hash() != this[CredentialTable.integrityHash])
            throw CompromisedException("Credential data (id=${credential.id}) for user=${credential.userId} failed integrity check")

        return credential
    }
}
