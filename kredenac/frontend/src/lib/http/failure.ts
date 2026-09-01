import { ApiError } from "./api";

export interface Failure {
  dom?: string;
  status?: number;
  code?: string;
  offline: boolean;
}

export function failure(error: unknown): Failure {
  if (error instanceof DOMException) return { dom: error.name, offline: false };
  if (error instanceof ApiError) return { status: error.status, code: error.code, offline: false };
  return { offline: error instanceof TypeError };
}
