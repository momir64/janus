export interface FileEntry {
  id: string;
  filename: string;
  contentType: string;
  size: number;
  createdAt: string;
}

export interface NoteEntry {
  id: string;
  title: string;
  content: string;
  updatedAt: string;
}

// The backend only sends `id` and `algorithm` today (see backend
// dtos/Auth.kt: CredentialDto — the todo there says device name, created
// time, last-used time and location are meant to be added later). Mobile
// (2030:17) lists the address and the city on separate lines, desktop
// (139:135) combines them, so they are held apart here. The
// Settings page renders these fields when present and falls back
// gracefully when they're not, so the UI is ready once the API catches up.
export interface Passkey {
  id: string;
  algorithm: string;
  deviceName?: string;
  lastUsedIp?: string;
  lastUsedLocation?: string;
  lastUsedAt?: string;
  createdAt?: string;
  currentSession?: boolean;
}
