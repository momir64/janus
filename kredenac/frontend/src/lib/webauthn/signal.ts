interface SignalApi {
  signalUnknownCredential?: (options: { rpId: string; credentialId: string }) => Promise<void>;
  signalAllAcceptedCredentials?: (options: {
    rpId: string;
    userId: string;
    allAcceptedCredentialIds: string[];
  }) => Promise<void>;
  signalCurrentUserDetails?: (options: {
    rpId: string;
    userId: string;
    name: string;
    displayName: string;
  }) => Promise<void>;
}

const signals = (): SignalApi | undefined =>
  (globalThis as { PublicKeyCredential?: SignalApi }).PublicKeyCredential;

async function silence(run: () => Promise<void> | undefined): Promise<void> {
  try {
    await run();
  } catch {
    // A passkey manager that refuses a signal must not fail the action that reported it.
  }
}

export function signalUnknownCredential(rpId: string, credentialId: string): Promise<void> {
  return silence(() => signals()?.signalUnknownCredential?.({ rpId, credentialId }));
}

export function signalAcceptedCredentials(
  rpId: string,
  userHandle: string,
  credentialIds: string[]
): Promise<void> {
  return silence(() =>
    signals()?.signalAllAcceptedCredentials?.({
      rpId,
      userId: userHandle,
      allAcceptedCredentialIds: credentialIds,
    })
  );
}

export function signalUserDetails(rpId: string, userHandle: string, email: string): Promise<void> {
  return silence(() =>
    signals()?.signalCurrentUserDetails?.({ rpId, userId: userHandle, name: email, displayName: email })
  );
}
