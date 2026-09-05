package rs.moma.janus.kredenac.utils

import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import rs.moma.janus.kredenac.crypto.webauthn.PRIVEZAK_SIGNERS
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERTaggedObject
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x500.X500Name
import java.security.cert.X509Certificate
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERSequence
import java.security.KeyPairGenerator
import org.bouncycastle.asn1.DERSet
import java.security.PrivateKey
import java.security.KeyFactory
import java.security.PublicKey
import java.security.KeyPair
import java.math.BigInteger
import java.util.Date

class KeyAttestation(
    private val packageName: String = "rs.moma.janus.privezak",
    private val signer: String = PRIVEZAK_SIGNERS.first()
) {
    private val rootPair = keyPair()
    val root: PublicKey get() = rootPair.public

    fun chain(attestedKey: ByteArray, challenge: ByteArray): List<ByteArray> {
        val intermediatePair = keyPair()
        val attested = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(attestedKey))

        val leaf = certificate("CN=Attested Key", attested, "CN=Intermediate", intermediatePair.private, challenge)
        val intermediate = certificate("CN=Intermediate", intermediatePair.public, "CN=Root", rootPair.private)
        val rootCertificate = certificate("CN=Root", rootPair.public, "CN=Root", rootPair.private)
        return listOf(leaf, intermediate, rootCertificate).map { it.encoded }
    }

    private fun certificate(
        subject: String, subjectKey: PublicKey, issuer: String,
        issuerKey: PrivateKey, challenge: ByteArray? = null
    ): X509Certificate {
        val now = System.currentTimeMillis()
        val builder = JcaX509v3CertificateBuilder(
            X500Name(issuer), BigInteger.valueOf(now), Date(now - 3600_000), Date(now + 3600_000),
            X500Name(subject), subjectKey
        )
        if (challenge != null)
            builder.addExtension(ASN1ObjectIdentifier(KEY_DESCRIPTION_OID), false, keyDescription(challenge))
        return JcaX509CertificateConverter()
            .getCertificate(builder.build(JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey)))
    }

    private fun keyDescription(challenge: ByteArray) = DERSequence(
        arrayOf(
            ASN1Integer(300), ASN1Enumerated(1), ASN1Integer(300), ASN1Enumerated(1),
            DEROctetString(challenge), DEROctetString(ByteArray(0)),
            DERSequence(DERTaggedObject(true, ATTESTATION_APPLICATION_ID_TAG, applicationId())),
            DERSequence()
        )
    )

    private fun applicationId() = DEROctetString(
        DERSequence(
            arrayOf(
                DERSet(DERSequence(arrayOf(DEROctetString(packageName.toByteArray()), ASN1Integer(1)))),
                DERSet(DEROctetString(signer.hexToByteArray()))
            )
        ).encoded
    )

    private fun keyPair(): KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private companion object {
        const val KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17"
        const val ATTESTATION_APPLICATION_ID_TAG = 709
    }
}
