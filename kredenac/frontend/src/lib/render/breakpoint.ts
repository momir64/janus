const DESKTOP_MIN_WIDTH = 900;

export const isDesktop = (): boolean =>
  window.matchMedia(`(min-width: ${DESKTOP_MIN_WIDTH}px)`).matches;
