export interface FileDto {
  id: string;
  filename: string;
  size: number;
  createdAt: string;
}

export interface NoteDto {
  id: string;
  title: string;
  content: string;
  updatedAt: string;
}

export interface PasskeyDto {
  id: string;
  credentialId: string;
  deviceName?: string;
  lastUsedIp?: string;
  lastUsedLocation?: string;
  lastUsedAt?: string;
  createdAt?: string;
  currentSession?: boolean;
}
