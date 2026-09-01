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
import kotlin.time.Clock
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

    suspend fun register(email: String, parsedAttestation: ParsedAttestation) {
        val userId = userRepository.findIdByEmail(email) ?: userRepository.insert(email)
        credentialRepository.insert(
            userId, parsedAttestation.credentialId, parsedAttestation.algorithm,
            parsedAttestation.publicKey, parsedAttestation.aaguid
        )
    }

    context(owner: Owner)
    private suspend fun notifyPasskeyChange(action: String, client: ClientInfo) {
        val email = userRepository.findEmailById(owner.userId) ?: return
        val rows = clientRows(client)

        emailService.notify(
            to = email,
            subject = "A passkey was $action your account",
            html = """
                <p>A passkey was just $action your Kredenac account.</p>
                <ul>${rows.joinToString("") { "<li><b>${it.first}:</b> ${it.second}</li>" }}</ul>
                <p>If this wasn't you, review your passkeys in settings and add a new one from a device you trust.</p>
            """.trimIndent()
        )  // todo: move later to email service and make a better email template in resources instead
    }

    context(owner: Owner)
    suspend fun addCredential(parsedAttestation: ParsedAttestation, client: ClientInfo) {
        notifyPasskeyChange("added to", client)
        credentialRepository.insert(
            owner.userId, parsedAttestation.credentialId, parsedAttestation.algorithm,
            parsedAttestation.publicKey, parsedAttestation.aaguid
        )
    }

    suspend fun credentialIdsFor(email: String): List<String> {
        val userId = userRepository.findIdByEmail(email) ?: return emptyList()
        return context(Owner(userId)) { credentialIds() }
    }

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
        notifyPasskeyChange("removed from", client)
        deleteCredential(credentialId)
    }

    suspend fun revokeCompromisedCredential(userId: Uuid, credentialId: Uuid) {
        try {
            context(Owner(userId)) {
                deleteCredential(credentialId)
            }
        } catch (_: NotFoundException) {
        }

        val email = userRepository.findEmailById(userId) ?: return
        emailService.notify(
            to = email,
            subject = "Security alert: a passkey was disabled",
            html = """
                <p>We detected unusual activity from one of your passkeys and disabled it as a precaution.</p>
                <p>If you don't recognize this activity, we recommend reviewing your remaining passkeys and adding a new one from a trusted device.</p>
            """.trimIndent()
        )
    }

    context(owner: Owner)
    suspend fun consumeReauthToken(token: String) {
        if (tokenRepository.consume(token) != owner.userId.toString())
            throw UnauthorizedException("Reauth token does not belong to this session")
    }

    private fun clientRows(client: ClientInfo) = listOf(
        "Device" to client.device,
        "Browser" to client.browser,
        "IP address" to client.ip,
        "Location" to client.location,
        "Time" to Clock.System.now().toString()
    ).filter { !it.second.isNullOrBlank() }

    context(owner: Owner)
    suspend fun deleteAccount(client: ClientInfo) {
        val email = userRepository.findEmailById(owner.userId)
        refreshTokenRepository.deleteAllForUser()
        credentialRepository.deleteAll()

        filesRepository.findAll().forEach { fileContentRepository.delete(it.id) }
        filesRepository.deleteAll()

        notesRepository.deleteAll()
        userRepository.delete(owner.userId)

        email?.let {
            emailService.notify(
                to = it,
                subject = "Your Kredenac account was deleted",
                html = """
                    <p>Your Kredenac account and everything stored in it were permanently deleted.</p>
                    <ul>${clientRows(client).joinToString("") { row -> "<li><b>${row.first}:</b> ${row.second}</li>" }}</ul>
                    <p>If this wasn't you, the account cannot be recovered - register again to start over.</p>
                """.trimIndent() // todo: move to email service, and use a better email resource template
            )
        }
    }

    context(owner: Owner)
    suspend fun getEncryptionKey(): ByteArray =
        userRepository.encryptionKeyFor(owner.userId) ?: throw NotFoundException("User not found")

    private fun StoredCredential.toDto(currentCredentialId: Uuid) = CredentialDto(
        id = id.toString(),
        deviceName = aaguid?.let(aaguidNames::get),
        currentSession = id == currentCredentialId,
        createdAt = createdAt.toString(),
        lastUsedAt = lastUsedAt?.toString(),
        lastUsedIp = lastUsedIp,
        lastUsedLocation = lastUsedLocation
    )
}
