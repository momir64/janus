import { clearSession, getCsrfToken, isAuthenticated } from "../http/session";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "../http/api";

const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

const noContent = () => new Response(null, { status: 204 });

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  clearSession();

  const win = new EventTarget() as unknown as Window & typeof globalThis;
  win.setTimeout = ((...args: Parameters<typeof setTimeout>) => globalThis.setTimeout(...args)) as never;
  vi.stubGlobal("window", win);

  vi.useFakeTimers();
  fetchMock = vi.fn();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe("session establishment", () => {
  it("keeps the CSRF token from a sign-in and schedules a refresh before the token expires", async () => {
    fetchMock.mockResolvedValueOnce(json({ csrfToken: "abc", expiresIn: 300 }));

    await api.auth.loginFinish({
      credentialId: "a",
      clientDataJSON: "b",
      authenticatorData: "c",
      signature: "d",
    });

    expect(getCsrfToken()).toBe("abc");
    expect(isAuthenticated()).toBe(true);

    // 300s with a 30s margin: nothing at 269s, a refresh at 271s.
    fetchMock.mockResolvedValue(json({ csrfToken: "next", expiresIn: 300 }));
    await vi.advanceTimersByTimeAsync(269_000);
    expect(fetchMock).toHaveBeenCalledTimes(1);

    await vi.advanceTimersByTimeAsync(2_000);
    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/auth/refresh");
  });

  it("sends the CSRF token on unsafe methods only", async () => {
    fetchMock.mockResolvedValueOnce(json({ csrfToken: "abc", expiresIn: 300 }));
    await api.auth.loginFinish({ credentialId: "a", clientDataJSON: "b", authenticatorData: "c", signature: "d" });

    fetchMock.mockResolvedValueOnce(json([]));
    await api.notes.list();
    expect(new Headers(fetchMock.mock.calls[1][1].headers).get("X-CSRF-Token")).toBeNull();

    fetchMock.mockResolvedValueOnce(noContent());
    await api.notes.create("t", "c");
    expect(new Headers(fetchMock.mock.calls[2][1].headers).get("X-CSRF-Token")).toBe("abc");
  });
});

describe("recovering from an expired access token", () => {
  it("refreshes once for concurrent 401s, then retries each request", async () => {
    const calls: string[] = [];
    fetchMock.mockImplementation((url: string) => {
      calls.push(url);
      if (url === "/api/auth/refresh") return Promise.resolve(json({ csrfToken: "fresh", expiresIn: 300 }));
      return Promise.resolve(calls.filter((c) => c === url).length === 1 ? json({}, 401) : json([]));
    });

    await Promise.all([api.notes.list(), api.files.list(), api.auth.listCredentials()]);

    expect(calls.filter((c) => c === "/api/auth/refresh")).toHaveLength(1);
    expect(getCsrfToken()).toBe("fresh");
  });

  it("ends the session and announces it when the refresh itself fails", async () => {
    const expired = vi.fn();
    window.addEventListener("kredenac:session-expired", expired);

    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(url === "/api/auth/refresh" ? json({}, 401) : json({}, 401))
    );

    await expect(api.notes.list()).rejects.toBeInstanceOf(ApiError);
    expect(isAuthenticated()).toBe(false);
    expect(expired).toHaveBeenCalledTimes(1);
  });

  it("never refreshes for the endpoints that establish a session", async () => {
    fetchMock.mockResolvedValue(json({ message: "Unauthorized" }, 401));

    await expect(
      api.auth.loginFinish({ credentialId: "a", clientDataJSON: "b", authenticatorData: "c", signature: "d" })
    ).rejects.toBeInstanceOf(ApiError);

    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual(["/api/auth/login/finish"]);
  });
});

describe("errors", () => {
  it("carries the status, message and code the server sent", async () => {
    fetchMock.mockResolvedValueOnce(
      json({ message: "Sign count did not increase", code: "passkey_cloned" }, 401)
    );

    const error = (await api.auth
      .loginFinish({ credentialId: "a", clientDataJSON: "b", authenticatorData: "c", signature: "d" })
      .catch((e: unknown) => e)) as ApiError;

    expect(error.status).toBe(401);
    expect(error.code).toBe("passkey_cloned");
    expect(error.message).toBe("Sign count did not increase");
  });

  it("falls back to the body or the status text when there is no json", async () => {
    fetchMock.mockResolvedValueOnce(new Response("plain words", { status: 500 }));
    const error = (await api.notes.list().catch((e: unknown) => e)) as ApiError;
    expect(error.message).toBe("plain words");
    expect(error.code).toBeUndefined();
  });
});
