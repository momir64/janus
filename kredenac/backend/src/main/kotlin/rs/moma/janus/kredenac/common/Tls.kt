package rs.moma.janus.kredenac.common

import java.security.spec.PKCS8EncodedKeySpec
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManagerFactory
import java.security.SecureRandom
import kotlin.io.path.inputStream
import kotlin.io.encoding.Base64
import java.security.KeyFactory
import kotlin.io.path.readText
import java.security.KeyStore
import java.nio.file.Path

object Tls {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    val password: CharArray = CharArray(32) { ALPHABET[SecureRandom().nextInt(ALPHABET.length)] }

    fun keyStore(certificate: Path, privateKey: Path, alias: String): KeyStore =
        KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry(alias, readKey(privateKey), password, arrayOf(readCertificate(certificate)))
        }

    fun trustManager(authority: Path): TrustManagerFactory {
        val store = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setCertificateEntry("lokot-ca", readCertificate(authority))
        }
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
    }

    private fun readCertificate(file: Path) =
        file.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificate(it) }

    private fun readKey(file: Path) =
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(der(file.readText())))

    private fun der(pem: String): ByteArray = Base64.Mime
        .decode(pem.lineSequence().filterNot { it.startsWith("-----") }.joinToString("\n"))
}