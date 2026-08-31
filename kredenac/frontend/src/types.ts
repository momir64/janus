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
