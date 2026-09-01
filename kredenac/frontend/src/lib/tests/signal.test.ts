import { signalAcceptedCredentials, signalUnknownCredential, signalUserDetails } from "../webauthn/signal";
import { afterEach, describe, expect, it, vi } from "vitest";

function stubSignals(methods: Record<string, unknown>): void {
  vi.stubGlobal("PublicKeyCredential", methods);
}

afterEach(() => vi.unstubAllGlobals());

describe("signal", () => {
  it("reports a credential the server no longer accepts", async () => {
    const signalUnknown = vi.fn().mockResolvedValue(undefined);
    stubSignals({ signalUnknownCredential: signalUnknown });

    await signalUnknownCredential("kredenac.moma.rs", "Y3JlZA");

    expect(signalUnknown).toHaveBeenCalledWith({ rpId: "kredenac.moma.rs", credentialId: "Y3JlZA" });
  });

  it("sends the remaining credentials under the account handle", async () => {
    const signalAll = vi.fn().mockResolvedValue(undefined);
    stubSignals({ signalAllAcceptedCredentials: signalAll });

    await signalAcceptedCredentials("kredenac.moma.rs", "aGFuZGxl", ["one", "two"]);

    expect(signalAll).toHaveBeenCalledWith({
      rpId: "kredenac.moma.rs",
      userId: "aGFuZGxl",
      allAcceptedCredentialIds: ["one", "two"],
    });
  });

  it("uses the email for both the name and the display name", async () => {
    const signalUser = vi.fn().mockResolvedValue(undefined);
    stubSignals({ signalCurrentUserDetails: signalUser });

    await signalUserDetails("kredenac.moma.rs", "aGFuZGxl", "alice@example.com");

    expect(signalUser).toHaveBeenCalledWith({
      rpId: "kredenac.moma.rs",
      userId: "aGFuZGxl",
      name: "alice@example.com",
      displayName: "alice@example.com",
    });
  });

  it("stays quiet when the passkey manager refuses the signal", async () => {
    stubSignals({ signalAllAcceptedCredentials: vi.fn().mockRejectedValue(new Error("refused")) });

    await expect(signalAcceptedCredentials("kredenac.moma.rs", "aGFuZGxl", [])).resolves.toBeUndefined();
  });

  it("does nothing on a browser without the signal api", async () => {
    stubSignals({});

    await expect(signalUnknownCredential("kredenac.moma.rs", "Y3JlZA")).resolves.toBeUndefined();
    await expect(signalUserDetails("kredenac.moma.rs", "aGFuZGxl", "alice@example.com")).resolves.toBeUndefined();
  });
});
