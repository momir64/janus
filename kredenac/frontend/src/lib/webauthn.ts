import { fromBase64Url, toBase64Url } from "./base64url";
import { api } from "./api";

const PUB_KEY_CRED_PARAMS: PublicKeyCredentialParameters[] = [
  { type: "public-key", alg: -7 },
  { type: "public-key", alg: -257 },
  { type: "public-key", alg: -8 },
];

async function createCredential(
  challenge: string,
  rpId: string,
  userName: string,
  excludeCredentials: string[]
): Promise<PublicKeyCredential> {
  const credential = await navigator.credentials.create({
    publicKey: {
      challenge: fromBase64Url(challenge),
      rp: { id: rpId, name: "Kredenac" },
      user: { id: crypto.getRandomValues(new Uint8Array(16)), name: userName, displayName: userName },
      pubKeyCredParams: PUB_KEY_CRED_PARAMS,
      authenticatorSelection: { residentKey: "required", userVerification: "required" },
      attestation: "none",
      excludeCredentials: excludeCredentials.map((id) => ({ type: "public-key", id: fromBase64Url(id) })),
      timeout: 60_000,
    },
  });

  if (!(credential instanceof PublicKeyCredential))
    throw new DOMException("Passkey creation was cancelled", "NotAllowedError");
  return credential;
}

export async function startRegistration(token: string): Promise<RegistrationHandle> {
  const { challenge, rpId, email, excludeCredentials } = await api.auth.registerStart(token);

  return {
    email,
    complete: async () => {
      const credential = await createCredential(challenge, rpId, email, excludeCredentials);
      const response = credential.response as AuthenticatorAttestationResponse;

      await api.auth.registerFinish({
        clientDataJSON: toBase64Url(response.clientDataJSON),
        attestationObject: toBase64Url(response.attestationObject),
      });
    },
  };
}

export interface RegistrationHandle {
  email: string;
  complete: () => Promise<void>;
}

async function assertCredential(challenge: string, rpId: string) {
  const credential = await navigator.credentials.get({
    publicKey: {
      challenge: fromBase64Url(challenge),
      rpId,
      userVerification: "required",
      timeout: 60_000,
    },
  });

  if (!(credential instanceof PublicKeyCredential))
    throw new DOMException("Passkey login was cancelled", "NotAllowedError");
  const response = credential.response as AuthenticatorAssertionResponse;

  return {
    credentialId: toBase64Url(credential.rawId),
    clientDataJSON: toBase64Url(response.clientDataJSON),
    authenticatorData: toBase64Url(response.authenticatorData),
    signature: toBase64Url(response.signature),
  };
}

export async function login(): Promise<void> {
  const { challenge, rpId } = await api.auth.loginStart();
  await api.auth.loginFinish(await assertCredential(challenge, rpId));
}

export interface NewPasskeyChallenge {
  excludeCredentials: string[];
  challenge: string;
  rpId: string;
}

export async function verifyForNewPasskey(): Promise<NewPasskeyChallenge> {
  const { challenge, rpId } = await api.auth.addPasskeyStart();
  return api.auth.addPasskeyVerify(await assertCredential(challenge, rpId));
}

export async function addPasskey(
  { excludeCredentials, challenge, rpId }: NewPasskeyChallenge,
  userName: string
): Promise<void> {
  const credential = await createCredential(challenge, rpId, userName, excludeCredentials);
  const response = credential.response as AuthenticatorAttestationResponse;

  await api.auth.addPasskeyFinish({
    clientDataJSON: toBase64Url(response.clientDataJSON),
    attestationObject: toBase64Url(response.attestationObject),
  });
}
