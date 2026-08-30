// Matches $breakpoint-desktop in styles/_tokens.scss.
const DESKTOP_MIN_WIDTH = 900;

/** Whether the desktop layout is the one in force. */
export const isDesktop = (): boolean =>
  window.matchMedia(`(min-width: ${DESKTOP_MIN_WIDTH}px)`).matches;
