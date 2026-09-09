package rs.moma.janus.lokot.externals

import kotlinx.cinterop.*
import crypto.*

class Certificate(val certificate: String, val privateKey: String)
class Issued(val authority: String, val leaves: Map<String, Certificate>)
class Leaf(val name: String, val commonName: String, val altNames: List<String>)

@OptIn(ExperimentalForeignApi::class)
object Certificates {
    private const val AUTHORITY_BITS = 4096
    private const val LEAF_BITS = 2048
    private const val ASCII = 4097 // MBSTRING_ASC
    private const val SUBJECT_ALT_NAME = 85
    private const val BASIC_CONSTRAINTS = 87
    private const val KEY_USAGE = 83
    private const val EXTENDED_KEY_USAGE = 126
    private const val RSA = 6 // EVP_PKEY_RSA
    private const val DAY = 86400L

    fun issue(authorityName: String, leaves: List<Leaf>, days: Int): Issued {
        val authorityKey = generateKey(AUTHORITY_BITS) ?: error("could not generate the CA key")
        try {
            val authority = certificate(
                subject = authorityName, issuer = null, key = authorityKey, signWith = authorityKey,
                days = days, extensions = listOf(
                    BASIC_CONSTRAINTS to "critical,CA:TRUE",
                    KEY_USAGE to "critical,keyCertSign,cRLSign",
                )
            ) ?: error("could not sign the CA certificate")

            val signed = leaves.associate { leaf ->
                val key = generateKey(LEAF_BITS) ?: error("could not generate the ${leaf.name} key")
                try {
                    val certificate = certificate(
                        subject = leaf.commonName, issuer = authorityName, key = key, signWith = authorityKey,
                        days = days, extensions = listOf(
                            BASIC_CONSTRAINTS to "critical,CA:FALSE",
                            KEY_USAGE to "critical,digitalSignature,keyEncipherment",
                            EXTENDED_KEY_USAGE to "serverAuth",
                            SUBJECT_ALT_NAME to leaf.altNames.joinToString(","),
                        )
                    ) ?: error("could not sign the ${leaf.name} certificate")
                    leaf.name to Certificate(certificate, privateKeyPem(key))
                } finally {
                    EVP_PKEY_free(key)
                }
            }
            return Issued(authority, signed)
        } finally {
            EVP_PKEY_free(authorityKey)
        }
    }

    fun signedBy(certificate: String, authority: String): Boolean {
        val leaf = read(certificate) ?: return false
        val issuer = read(authority) ?: return false
        try {
            val key = X509_get_pubkey(issuer) ?: return false
            try {
                return X509_verify(leaf, key) == 1
            } finally {
                EVP_PKEY_free(key)
            }
        } finally {
            X509_free(leaf)
            X509_free(issuer)
        }
    }

    fun matchesHost(certificate: String, host: String): Boolean {
        val leaf = read(certificate) ?: return false
        try {
            return X509_check_host(leaf, host, host.length.convert(), 0u, null) == 1
        } finally {
            X509_free(leaf)
        }
    }

    private fun read(pem: String): CPointer<X509>? {
        val bytes = pem.encodeToByteArray()
        val bio = bytes.usePinned { BIO_new_mem_buf(it.addressOf(0), bytes.size) } ?: return null
        try {
            return PEM_read_bio_X509(bio, null, null, null)
        } finally {
            BIO_free(bio)
        }
    }

    private fun generateKey(bits: Int): CPointer<EVP_PKEY>? {
        val exponent = BN_new() ?: return null
        val rsa = RSA_new()
        try {
            BN_set_word(exponent, 65537u)
            if (rsa == null || RSA_generate_key_ex(rsa, bits, exponent, null) != 1) return null
            val key = EVP_PKEY_new() ?: return null
            if (EVP_PKEY_assign(key, RSA, rsa) != 1) {
                EVP_PKEY_free(key)
                return null
            }
            return key
        } finally {
            BN_free(exponent)
        }
    }

    private fun certificate(
        subject: String,
        issuer: String?,
        key: CPointer<EVP_PKEY>,
        signWith: CPointer<EVP_PKEY>,
        days: Int,
        extensions: List<Pair<Int, String>>,
    ): String? = memScoped {
        val certificate = X509_new() ?: return null
        try {
            X509_set_version(certificate, 2) // v3, counted from zero
            ASN1_INTEGER_set_int64(X509_get_serialNumber(certificate), serial())
            X509_gmtime_adj(X509_getm_notBefore(certificate), 0.convert())
            X509_gmtime_adj(X509_getm_notAfter(certificate), (days * DAY).convert())
            X509_set_pubkey(certificate, key)

            val name = X509_get_subject_name(certificate)
            X509_NAME_add_entry_by_txt(name, "CN", ASCII, subject.cstr.ptr.reinterpret(), -1, -1, 0)
            X509_set_subject_name(certificate, name)

            val issuerName = commonName(issuer)
            X509_set_issuer_name(certificate, issuerName ?: name)
            issuerName?.let { X509_NAME_free(it) } // set_issuer_name keeps a copy of its own

            val context = alloc<X509V3_CTX>()
            X509V3_set_ctx(context.ptr, certificate, certificate, null, null, 0)
            extensions.forEach { (nid, value) ->
                if (value.isEmpty()) return@forEach
                val extension = X509V3_EXT_conf_nid(null, context.ptr, nid, value)
                    ?: error("libcrypto refused the extension '$value'")
                X509_add_ext(certificate, extension, -1)
                X509_EXTENSION_free(extension)
            }

            if (X509_sign(certificate, signWith, EVP_sha256()) == 0) return null
            pem { PEM_write_bio_X509(it, certificate) }
        } finally {
            X509_free(certificate)
        }
    }

    private fun commonName(text: String?): CPointer<X509_NAME>? {
        if (text == null) return null
        val name = X509_NAME_new() ?: return null
        memScoped { X509_NAME_add_entry_by_txt(name, "CN", ASCII, text.cstr.ptr.reinterpret(), -1, -1, 0) }
        return name
    }

    private fun privateKeyPem(key: CPointer<EVP_PKEY>): String =
        pem { PEM_write_bio_PrivateKey(it, key, null, null, 0, null, null) } ?: error("could not write the key")

    private fun pem(write: (CPointer<BIO>) -> Int): String? {
        val bio = BIO_new(BIO_s_mem()) ?: return null
        try {
            if (write(bio) != 1) return null
            val length = BIO_ctrl_pending(bio).toInt()
            val bytes = ByteArray(length)
            bytes.usePinned { BIO_read(bio, it.addressOf(0), length) }
            return bytes.decodeToString()
        } finally {
            BIO_free(bio)
        }
    }

    private fun serial(): Long = Crypto.randomBytes(8)
        .foldIndexed(0L) { index, value, byte -> value or ((byte.toLong() and 0xFF) shl (8 * index)) } and Long.MAX_VALUE
}
