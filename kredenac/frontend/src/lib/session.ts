
let csrfToken: string | null = null;

export function setCsrfToken(token: string): void {
  csrfToken = token;
}

export function clearSession(): void {
  csrfToken = null;
}

export function getCsrfToken(): string | null {
  return csrfToken;
}

export function isAuthenticated(): boolean {
  return csrfToken !== null;
}
