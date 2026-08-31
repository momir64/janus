import { clearSession, getCsrfToken, setCsrfToken } from "./session";
import type { FileDto, NoteDto, PasskeyDto } from "../types";

export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string
  ) {
    super(message);
  }
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

interface Session {
  csrfToken: string;
  expiresIn?: number;
}

const MAX_REFRESH_MARGIN_MS = 30_000;
let renewal = 0;

function startSession({ csrfToken, expiresIn }: Session): void {
  setCsrfToken(csrfToken);
  clearTimeout(renewal);
  if (!expiresIn) return;
  const lifetime = expiresIn * 1000;
  const margin = Math.min(MAX_REFRESH_MARGIN_MS, lifetime * 0.1);
  renewal = window.setTimeout(() => void refreshSession(), Math.max(lifetime - margin, 5_000));
}

const ANONYMOUS = ["/auth/login", "/auth/register", "/auth/refresh"];
const isAnonymous = (path: string): boolean => ANONYMOUS.some((p) => path.startsWith(p));

async function failure(response: Response): Promise<ApiError> {
  const body = await response.text().catch(() => "");
  try {
    const parsed = JSON.parse(body) as { code?: string; message?: string };
    return new ApiError(response.status, parsed.message ?? response.statusText, parsed.code);
  } catch {
    return new ApiError(response.status, body || response.statusText);
  }
}

// The rotation detects reuse, so two requests refreshing at once would present
// the same token twice and revoke the chain. They share one attempt instead.
let refreshing: Promise<boolean> | null = null;

function refreshSession(): Promise<boolean> {
  refreshing ??= api.auth
    .refresh()
    .then(() => true)
    .catch(() => false)
    .finally(() => {
      refreshing = null;
    });
  return refreshing;
}

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();

  const send = (): Promise<Response> => {
    const headers = new Headers(init.headers);
    if (!SAFE_METHODS.has(method)) {
      const csrf = getCsrfToken();
      if (csrf) headers.set("X-CSRF-Token", csrf);
    }
    return fetch(`/api${path}`, { ...init, headers, credentials: "include" });
  };

  let response = await send();

  if (response.status === 401 && !isAnonymous(path)) {
    if (await refreshSession()) response = await send();
    else {
      clearSession();
      window.dispatchEvent(new CustomEvent("kredenac:session-expired"));
    }
  }

  if (response.status === 401 && isAnonymous(path)) clearSession();
  if (!response.ok) throw await failure(response);

  return response;
}

async function json<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await request(path, init);
  return response.status === 204 ? (undefined as T) : ((await response.json()) as T);
}

function withJsonBody(body: unknown, method: "POST" | "PUT" = "POST"): RequestInit {
  return { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) };
}

export const api = {
  auth: {
    requestMagicLink: (email: string) =>
      request("/auth/register/verify", withJsonBody({ email })).then(() => undefined),

    registerStart: (token: string) =>
      json<{ excludeCredentials: string[]; challenge: string; rpId: string; email: string }>(
        "/auth/register/start",
        withJsonBody({ token })
      ),

    registerFinish: (body: { clientDataJSON: string; attestationObject: string }) =>
      request("/auth/register/finish", withJsonBody(body)).then(() => undefined),

    loginStart: () => json<{ challenge: string; rpId: string }>("/auth/login/start", { method: "POST" }),

    loginFinish: async (body: {
      credentialId: string;
      clientDataJSON: string;
      authenticatorData: string;
      signature: string;
    }) => {
      startSession(await json<Session>("/auth/login/finish", withJsonBody(body)));
    },

    refresh: async () => {
      const session = await json<Session>("/auth/refresh", { method: "POST" });
      startSession(session);
    },

    addPasskeyStart: () =>
      json<{ challenge: string; rpId: string }>("/auth/credentials/add/start", { method: "POST" }),

    addPasskeyVerify: (body: {
      credentialId: string;
      clientDataJSON: string;
      authenticatorData: string;
      signature: string;
    }) =>
      json<{ excludeCredentials: string[]; challenge: string; rpId: string; }>(
        "/auth/credentials/add/verify",
        withJsonBody(body)
      ),

    addPasskeyFinish: (body: { clientDataJSON: string; attestationObject: string }) =>
      request("/auth/credentials/add/finish", withJsonBody(body)).then(() => undefined),

    logout: async () => {
      await request("/auth/logout", { method: "POST" });
      clearSession();
    },

    listCredentials: () => json<PasskeyDto[]>("/auth/credentials"),

    deleteCredential: (id: string) => request(`/auth/credentials/${id}`, { method: "DELETE" }).then(() => undefined),

    deleteAccount: async () => {
      await request("/auth/account", { method: "DELETE" });
      clearSession();
    },
  },

  files: {
    list: () => json<FileDto[]>("/files"),

    upload: (file: File, signal?: AbortSignal) => {
      const form = new FormData();
      form.set("size", String(file.size));
      form.set("file", file);
      return request("/files", { method: "POST", body: form, signal }).then(() => undefined);
    },

    download: async (id: string, filename: string) => {
      const response = await request(`/files/${id}`);
      const blob = new Blob([await response.arrayBuffer()], { type: "application/octet-stream" });
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      link.click();
      setTimeout(() => URL.revokeObjectURL(url), 0);
    },

    delete: (id: string) => request(`/files/${id}`, { method: "DELETE" }).then(() => undefined),
  },

  notes: {
    list: () => json<NoteDto[]>("/notes"),

    create: (title: string, content: string) =>
      request("/notes", withJsonBody({ title, content })).then(() => undefined),

    update: (id: string, title: string, content: string) =>
      request(`/notes/${id}`, withJsonBody({ title, content }, "PUT")).then(() => undefined),

    delete: (id: string) => request(`/notes/${id}`, { method: "DELETE" }).then(() => undefined),
  },
};
