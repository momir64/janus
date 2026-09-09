import { fromBase64Url, toBase64Url } from "./base64url";
import { signalUnknownCredential } from "./signal";
import { failure } from "../http/failure";
import { api } from "../http/api";

const PUB_KEY_CRED_PARAMS: PublicKeyCredentialParameters[] = [
  { type: "public-key", alg: -7 },
  { type: "public-key", alg: -257 },
  { type: "public-key", alg: -8 },
];

async function createCredential(
  challenge: string,
  rpId: string,
  email: string,
  userHandle: string,
  excludeCredentials: string[]
): Promise<PublicKeyCredential> {
  const credential = await navigator.credentials.create({
    publicKey: {
      challenge: fromBase64Url(challenge),
      rp: { id: rpId, name: "Kredenac" },
      user: { id: fromBase64Url(userHandle), name: email, displayName: email },
      pubKeyCredParams: PUB_KEY_CRED_PARAMS,
      authenticatorSelection: { residentKey: "required", userVerification: "required" },
      attestation: "direct",
      excludeCredentials: excludeCredentials.map((id) => ({ type: "public-key", id: fromBase64Url(id) })),
      timeout: 60_000,
    },
  });

  if (!(credential instanceof PublicKeyCredential))
    throw new DOMException("Passkey creation was cancelled", "NotAllowedError");
  return credential;
}

export async function startRegistration(token: string): Promise<RegistrationHandle> {
  const { challenge, rpId, email, userHandle, excludeCredentials } = await api.auth.registerStart(token);

  return {
    email,
    complete: async () => {
      const credential = await createCredential(challenge, rpId, email, userHandle, excludeCredentials);
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

interface Assertion {
  credentialId: string;
  clientDataJSON: string;
  authenticatorData: string;
  signature: string;
}

const REVOKED = ["passkey_unknown", "passkey_cloned"];

async function submitAssertion<T>(
  rpId: string,
  assertion: Assertion,
  send: (assertion: Assertion) => Promise<T>
): Promise<T> {
  try {
    return await send(assertion);
  } catch (error) {
    const { code } = failure(error);
    if (code && REVOKED.includes(code)) await signalUnknownCredential(rpId, assertion.credentialId);
    throw error;
  }
}

export async function login(): Promise<void> {
  const { challenge, rpId } = await api.auth.loginStart();
  const assertion = await assertCredential(challenge, rpId);
  await submitAssertion(rpId, assertion, (it) => api.auth.loginFinish(it));
}

export interface NewPasskeyChallenge {
  excludeCredentials: string[];
  challenge: string;
  rpId: string;
  email: string;
  userHandle: string;
}

export async function verifyForNewPasskey(): Promise<NewPasskeyChallenge> {
  const { challenge, rpId } = await api.auth.addPasskeyStart();
  const assertion = await assertCredential(challenge, rpId);
  return submitAssertion(rpId, assertion, (it) => api.auth.addPasskeyVerify(it));
}

export async function reauthenticate(): Promise<string> {
  const { challenge, rpId } = await api.auth.reauthStart();
  const assertion = await assertCredential(challenge, rpId);
  const { token } = await submitAssertion(rpId, assertion, (it) => api.auth.reauthFinish(it));
  return token;
}

export async function addPasskey(
  { excludeCredentials, challenge, rpId, email, userHandle }: NewPasskeyChallenge
): Promise<void> {
  const credential = await createCredential(challenge, rpId, email, userHandle, excludeCredentials);
  const response = credential.response as AuthenticatorAttestationResponse;

  await api.auth.addPasskeyFinish({
    clientDataJSON: toBase64Url(response.clientDataJSON),
    attestationObject: toBase64Url(response.attestationObject),
  });
}
