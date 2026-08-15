package rs.moma.janus.kredenac.crypto.webauthn

enum class ChallengeKind(val value: String) {
    REGISTRATION("registration"),
    ASSERTION("assertion")
}
