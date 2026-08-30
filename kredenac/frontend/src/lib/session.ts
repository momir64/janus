/**
 * Auth cookies are httpOnly (set by the backend), so the only client-visible
 * signal of being logged in is holding a CSRF token — issued on login/refresh
 * and required as `X-CSRF-Token` on every mutating request.
 */

type Listener = (authenticated: boolean) => void;

let csrfToken: string | null = null;
const listeners = new Set<Listener>();

export function setCsrfToken(token: string): void {
  csrfToken = token;
  listeners.forEach((fn) => fn(true));
}

export function clearSession(): void {
  csrfToken = null;
  listeners.forEach((fn) => fn(false));
}

export function getCsrfToken(): string | null {
  return csrfToken;
}

export function isAuthenticated(): boolean {
  return csrfToken !== null;
}

export function onAuthChange(listener: Listener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}
