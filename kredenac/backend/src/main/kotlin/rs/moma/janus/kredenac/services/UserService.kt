package rs.moma.janus.kredenac.services

import rs.moma.janus.kredenac.repositories.RefreshTokenRepository
import rs.moma.janus.kredenac.repositories.FileContentRepository
import rs.moma.janus.kredenac.crypto.webauthn.ParsedAttestation
import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.repositories.StoredCredential
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.repositories.FilesRepository
import rs.moma.janus.kredenac.repositories.NotesRepository
import rs.moma.janus.kredenac.repositories.TokenRepository
import rs.moma.janus.kredenac.repositories.UserRepository
import rs.moma.janus.kredenac.common.NotFoundException
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import rs.moma.janus.kredenac.dtos.CredentialDto
import rs.moma.janus.kredenac.common.ClientInfo
import kotlin.time.Duration.Companion.minutes
import rs.moma.janus.kredenac.common.Owner
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

private val REAUTH_TTL = 2.minutes

private val aaguidNames: Map<Uuid, String> by lazy {
    val stream = UserService::class.java.getResourceAsStream("/aaguid-names.json") ?: return@lazy emptyMap()
    Json.decodeFromString<Map<String, String>>(stream.bufferedReader().use { it.readText() })
        .mapNotNull { (key, name) -> runCatching { Uuid.parse(key) }.getOrNull()?.let { it to name } }.toMap()
}

class UserService(
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val notesRepository: NotesRepository,
    private val filesRepository: FilesRepository,
    private val fileContentRepository: FileContentRepository,
    private val emailService: EmailService,
    private val tokenRepository: TokenRepository
) {
    private val secureRandom = SecureRandom()

    context(owner: Owner)
    suspend fun issueReauthToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.UrlSafe.withPadding(ABSENT).encode(bytes)
        tokenRepository.insert(token, REAUTH_TTL, owner.userId.toString())
        return token
    }

    suspend fun register(email: String, parsedAttestation: ParsedAttestation, client: ClientInfo) {
        val userId = userRepository.findIdByEmail(email) ?: userRepository.insert(email)
        credentialRepository.insert(
            userId, parsedAttestation.credentialId, parsedAttestation.algorithm, parsedAttestation.publicKey,
            parsedAttestation.aaguid, parsedAttestation.privezak, client.ip, client.location
        )
    }

    context(owner: Owner)
    suspend fun addCredential(parsedAttestation: ParsedAttestation, client: ClientInfo) {
        val email = userRepository.findEmailById(owner.userId) ?: return
        emailService.notifyPasskeyAdded(email, client)
        credentialRepository.insert(
            owner.userId, parsedAttestation.credentialId, parsedAttestation.algorithm, parsedAttestation.publicKey,
            parsedAttestation.aaguid, parsedAttestation.privezak, client.ip, client.location
        )
    }

    private fun Uuid.toHandle() = Base64.UrlSafe.withPadding(ABSENT).encode(toByteArray())

    suspend fun prepareRegistration(email: String): Pair<String, List<String>> {
        val userId = userRepository.findIdByEmail(email) ?: userRepository.insert(email)
        return userId.toHandle() to context(Owner(userId)) { credentialIds() }
    }

    context(owner: Owner)
    fun userHandle(): String = owner.userId.toHandle()

    context(owner: Owner)
    suspend fun email(): String = userRepository.findEmailById(owner.userId)
        ?: throw NotFoundException("User not found")

    context(owner: Owner)
    suspend fun credentialIds(): List<String> = credentialRepository.findAll().map {
        Base64.UrlSafe.withPadding(ABSENT).encode(it.credentialId)
    }

    context(owner: Owner)
    suspend fun listCredentials(currentCredentialId: Uuid): List<CredentialDto> =
        credentialRepository.findAll().map { it.toDto(currentCredentialId) }

    context(owner: Owner)
    private suspend fun deleteCredential(credentialId: Uuid) {
        if (!credentialRepository.delete(credentialId))
            throw NotFoundException("Credential not found")
        refreshTokenRepository.deleteChainsForCredential(credentialId)
    }

    context(owner: Owner)
    suspend fun deleteCredential(credentialId: Uuid, client: ClientInfo) {
        val email = userRepository.findEmailById(owner.userId) ?: return
        emailService.notifyPasskeyRemoved(email, client)
        deleteCredential(credentialId)
    }

    suspend fun revokeCompromisedCredential(userId: Uuid, credentialId: Uuid, client: ClientInfo) {
        try {
            context(Owner(userId)) {
                deleteCredential(credentialId)
            }
        } catch (_: NotFoundException) {
        }

        val email = userRepository.findEmailById(userId) ?: return
        emailService.notifyPasskeyDisabled(email, client)
    }

    context(owner: Owner)
    suspend fun consumeReauthToken(token: String) {
        if (tokenRepository.consume(token) != owner.userId.toString())
            throw UnauthorizedException("Reauth token does not belong to this session")
    }

    context(owner: Owner)
    suspend fun deleteAccount() {
        val email = userRepository.findEmailById(owner.userId)

        filesRepository.findAll().forEach { fileContentRepository.delete(it.id) }
        filesRepository.deleteAll()
        notesRepository.deleteAll()

        refreshTokenRepository.deleteAllForUser()
        credentialRepository.deleteAll()
        userRepository.delete(owner.userId)

        email?.let { emailService.notifyAccountDeleted(it) }
    }

    context(owner: Owner)
    suspend fun getEncryptionKey(): ByteArray =
        userRepository.encryptionKeyFor(owner.userId) ?: throw NotFoundException("User not found")

    private fun StoredCredential.toDto(currentCredentialId: Uuid) = CredentialDto(
        id = id.toString(),
        credentialId = Base64.UrlSafe.withPadding(ABSENT).encode(credentialId),
        deviceName = aaguid?.let(aaguidNames::get),
        currentSession = id == currentCredentialId,
        createdAt = createdAt.toString(),
        lastUsedAt = lastUsedAt?.toString(),
        lastUsedIp = lastUsedIp,
        lastUsedLocation = lastUsedLocation
    )
}
