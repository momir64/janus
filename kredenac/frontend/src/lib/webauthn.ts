import { fromBase64Url, toBase64Url } from "./base64url";
import { api } from "./api";

// Algorithms the backend's VerifyUtil accepts, restricted to the set every
// modern platform authenticator and security key supports.
const PUB_KEY_CRED_PARAMS: PublicKeyCredentialParameters[] = [
  { type: "public-key", alg: -7 }, // ES256
  { type: "public-key", alg: -257 }, // RS256
  { type: "public-key", alg: -8 }, // EdDSA
];

async function createCredential(
  challenge: string,
  rpId: string,
  userName: string
): Promise<PublicKeyCredential> {
  const credential = await navigator.credentials.create({
    publicKey: {
      challenge: fromBase64Url(challenge),
      rp: { id: rpId, name: "Kredenac" },
      user: { id: crypto.getRandomValues(new Uint8Array(16)), name: userName, displayName: userName },
      pubKeyCredParams: PUB_KEY_CRED_PARAMS,
      authenticatorSelection: { residentKey: "required", userVerification: "required" },
      attestation: "none",
      timeout: 60_000,
    },
  });

  if (!(credential instanceof PublicKeyCredential)) throw new Error("Passkey creation was cancelled");
  return credential;
}

/** Registration ceremony for a brand new account, finished via the emailed magic-link token. */
export async function registerWithToken(token: string, email: string): Promise<void> {
  const { challenge, rpId } = await api.auth.registerStart();
  const credential = await createCredential(challenge, rpId, email);
  const response = credential.response as AuthenticatorAttestationResponse;

  await api.auth.registerFinish({
    token,
    clientDataJSON: toBase64Url(response.clientDataJSON),
    attestationObject: toBase64Url(response.attestationObject),
  });
}

/** Adds an additional passkey to the already-authenticated account. */
export async function addCredential(email: string): Promise<void> {
  const { challenge, rpId } = await api.auth.addCredentialStart();
  const credential = await createCredential(challenge, rpId, email);
  const response = credential.response as AuthenticatorAttestationResponse;

  await api.auth.addCredentialFinish({
    clientDataJSON: toBase64Url(response.clientDataJSON),
    attestationObject: toBase64Url(response.attestationObject),
  });
}

/** Usernameless login: the platform's passkey picker resolves the credential. */
export async function login(): Promise<void> {
  const { challenge, rpId } = await api.auth.loginStart();

  const credential = await navigator.credentials.get({
    publicKey: {
      challenge: fromBase64Url(challenge),
      rpId,
      userVerification: "required",
      timeout: 60_000,
    },
  });

  if (!(credential instanceof PublicKeyCredential)) throw new Error("Passkey login was cancelled");
  const response = credential.response as AuthenticatorAssertionResponse;

  await api.auth.loginFinish({
    credentialId: toBase64Url(credential.rawId),
    clientDataJSON: toBase64Url(response.clientDataJSON),
    authenticatorData: toBase64Url(response.authenticatorData),
    signature: toBase64Url(response.signature),
  });
}
