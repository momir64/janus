// TODO: development only. Replaces the api layer with in-memory data so the
//  signed-in screens can be walked through without a backend — without it
//  the first list request 401s, which clears the session and bounces
//  Settings straight back to the login page. Remove this file together with
//  the shortcuts in login-page.ts and `onBeforeSubmit` in
//  login-email-field.ts.
import type { FileDto, NoteDto, PasskeyDto } from "../types";
import { clearSession } from "./session";
import { sleep } from "./timing";
import { api } from "./api";

const LOREM =
  "Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus " +
  "ex sapien vitae pellentesque sem placerat. In id cursus mi pretium tellus " +
  "duis convallis. Tempus leo eu aenean sed diam urna tempor.";

let enabled = false;

export function enableDevMode(): void {
  if (enabled) return;
  enabled = true;

  let files: FileDto[] = Array.from({ length: 12 }, (_, i) => ({
    id: `f${i}`,
    filename: "Some filename...",
    size: Math.round(1024 ** 2 * (0.4 + i * 12.7)),
    createdAt: new Date(2022, 11, 31, 12, 34 - i).toISOString(),
  }));

  let notes: NoteDto[] = [
    ...Array.from({ length: 6 }, (_, i) => ({
      id: `n${i}`,
      title: "Some title...",
      content: LOREM.slice(0, 60 + i * 40),
      updatedAt: new Date(2022, 11, 31, 12, 34 - i).toISOString(),
    })),
    {
      id: "n-title-only",
      title: "A note that is only a title",
      content: "",
      updatedAt: new Date(2022, 11, 31, 12, 28).toISOString(),
    },
    {
      id: "n-body-only",
      title: "",
      content: LOREM.slice(0, 150),
      updatedAt: new Date(2022, 11, 31, 12, 27).toISOString(),
    },
  ];

  let passkeys: PasskeyDto[] = Array.from({ length: 6 }, (_, i) => ({
    id: `p${i}`,
    deviceName: "Passkey device name",
    currentSession: i === 0,
    lastUsedIp: i === 1 ? undefined : "192.168.0.16",
    lastUsedLocation: i === 1 ? undefined : "Belgrade, RS",
    lastUsedAt: i === 1 ? undefined : new Date(2022, 11, 31, 12, 34).toISOString(),
    createdAt: new Date(2000, 2, 15, 12, 34).toISOString(),
  }));

  let seq = 0;

  api.files.list = async () => files;
  api.files.upload = async (file: File, signal?: AbortSignal, onProgress?: (percent: number) => void) => {
    for (const percent of [8, 27, 51, 76, 99]) {
      if (signal?.aborted) throw new DOMException("Upload was cancelled", "AbortError");
      onProgress?.(percent);
      await sleep(400);
    }

    files = [
      ...files,
      {
        id: `f${++seq}`,
        filename: file.name,
        size: file.size,
        createdAt: new Date().toISOString(),
      },
    ];
  };
  api.files.download = async () => undefined;
  api.files.delete = async (id: string) => {
    files = files.filter((f) => f.id !== id);
  };

  api.notes.list = async () => notes;
  api.notes.create = async (title: string, content: string) => {
    notes = [...notes, { id: `n${++seq}`, title, content, updatedAt: new Date().toISOString() }];
  };
  api.notes.update = async (id: string, title: string, content: string) => {
    notes = notes.map((n) => (n.id === id ? { ...n, title, content, updatedAt: new Date().toISOString() } : n));
  };
  api.notes.delete = async (id: string) => {
    notes = notes.filter((n) => n.id !== id);
  };

  api.auth.listCredentials = async () => passkeys;
  api.auth.deleteCredential = async (id: string) => {
    passkeys = passkeys.filter((p) => p.id !== id);
  };
  api.auth.refresh = async () => undefined;
  api.auth.logout = async () => clearSession();
  api.auth.deleteAccount = async () => clearSession();
}
