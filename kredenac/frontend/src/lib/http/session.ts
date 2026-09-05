let csrfToken: string | null = null;
let privezak = false;

export function setCsrfToken(token: string): void {
  csrfToken = token;
}

export function setPrivezakSession(attested: boolean): void {
  privezak = attested;
}

export function isPrivezakSession(): boolean {
  return privezak;
}

export function clearSession(): void {
  csrfToken = null;
  privezak = false;
}

export function getCsrfToken(): string | null {
  return csrfToken;
}

export function isAuthenticated(): boolean {
  return csrfToken !== null;
}
