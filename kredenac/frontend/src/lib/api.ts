import { clearSession, getCsrfToken, setCsrfToken } from "./session";
import type { FileEntry, NoteEntry, Passkey } from "../types";

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

async function request(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);

  if (!SAFE_METHODS.has(method)) {
    const csrf = getCsrfToken();
    if (csrf) headers.set("X-CSRF-Token", csrf);
  }

  const response = await fetch(`/api${path}`, { ...init, headers, credentials: "include" });

  if (response.status === 401) {
    clearSession();
    // Case 39. The ceremony and refresh endpoints are how a session is
    // obtained in the first place, so a 401 from one of them is a failed
    // sign-in rather than a session that ended - and is reported by the page
    // that asked for it.
    if (!path.startsWith("/auth/login") && !path.startsWith("/auth/register") && path !== "/auth/refresh") {
      window.dispatchEvent(new CustomEvent("kredenac:session-expired"));
    }
    throw new ApiError(401, "Not authenticated");
  }

  if (!response.ok) {
    const text = await response.text().catch(() => "");
    throw new ApiError(response.status, text || response.statusText);
  }

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

    registerStart: () => json<{ challenge: string; rpId: string }>("/auth/register/start", { method: "POST" }),

    registerFinish: (body: { token: string; clientDataJSON: string; attestationObject: string }) =>
      request("/auth/register/finish", withJsonBody(body)).then(() => undefined),

    loginStart: () => json<{ challenge: string; rpId: string }>("/auth/login/start", { method: "POST" }),

    loginFinish: async (body: {
      credentialId: string;
      clientDataJSON: string;
      authenticatorData: string;
      signature: string;
    }) => {
      const { csrfToken } = await json<{ csrfToken: string }>("/auth/login/finish", withJsonBody(body));
      setCsrfToken(csrfToken);
    },

    refresh: async () => {
      const { csrfToken } = await json<{ csrfToken: string }>("/auth/refresh", { method: "POST" });
      setCsrfToken(csrfToken);
    },

    logout: async () => {
      await request("/auth/logout", { method: "POST" });
      clearSession();
    },

    addCredentialStart: () =>
      json<{ challenge: string; rpId: string }>("/auth/credentials/add/start", { method: "POST" }),

    addCredentialFinish: (body: { clientDataJSON: string; attestationObject: string }) =>
      request("/auth/credentials/add/finish", withJsonBody(body)).then(() => undefined),

    // The backend only returns { id, algorithm } per credential today — the
    // rest of Passkey's fields are optional for exactly that reason.
    listCredentials: () => json<Passkey[]>("/auth/credentials"),

    deleteCredential: (id: string) => request(`/auth/credentials/${id}`, { method: "DELETE" }).then(() => undefined),

    deleteAccount: async () => {
      await request("/auth/account", { method: "DELETE" });
      clearSession();
    },
  },

  files: {
    list: () => json<FileEntry[]>("/files"),

    upload: (file: File) => {
      const form = new FormData();
      form.set("size", String(file.size));
      form.set("file", file);
      return request("/files", { method: "POST", body: form }).then(() => undefined);
    },

    download: async (id: string, filename: string) => {
      const response = await request(`/files/${id}`);
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = filename;
      link.click();
      URL.revokeObjectURL(url);
    },

    delete: (id: string) => request(`/files/${id}`, { method: "DELETE" }).then(() => undefined),
  },

  notes: {
    // TODO: a note may carry only a title or only a body — the editor accepts
    //  either and sends "" for the missing half. Confirm the backend's
    //  NoteDto treats an empty title/content as valid rather than rejecting
    //  it; if it does reject, the empty half needs omitting from the body
    //  instead.
    list: () => json<NoteEntry[]>("/notes"),

    create: (title: string, content: string) =>
      request("/notes", withJsonBody({ title, content })).then(() => undefined),

    update: (id: string, title: string, content: string) =>
      request(`/notes/${id}`, withJsonBody({ title, content }, "PUT")).then(() => undefined),

    delete: (id: string) => request(`/notes/${id}`, { method: "DELETE" }).then(() => undefined),
  },
};
