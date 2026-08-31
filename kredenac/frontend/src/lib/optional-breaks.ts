/**
 * Makes the <br>s inside `el` optional.
 *
 * The design breaks its copy at chosen words, which only reads correctly
 * while every one of those lines still fits. Below that the segments wrap
 * inside themselves and the block comes apart into ragged half-lines — so
 * once any line no longer fits, the breaks are dropped as a set and the copy
 * flows as ordinary text.
 *
 * Detected by counting: with the breaks honoured the paragraph should be
 * exactly one line taller than it has visible breaks. Anything above that
 * means a line had to wrap on its own.
 */
export function optionalBreaks(el: HTMLElement): void {
  // Re-measured on width changes only; dropping the breaks alters height, so
  // reacting to that as well would loop.
  let lastWidth = -1;

  const measure = (): void => {
    const width = el.clientWidth;
    if (width === lastWidth) return;
    lastWidth = width;

    // Always measured with the breaks in place, so a window growing back
    // wide enough gets them again.
    el.classList.remove("u-no-breaks");

    // Only the breaks the current breakpoint enables count — the frames set
    // different ones, and the disabled set is display:none.
    const breaks = [...el.querySelectorAll("br")].filter(
      (br) => getComputedStyle(br).display !== "none"
    ).length;

    // Counted from the box height rather than from client rects. Engines
    // disagree on what getClientRects() returns over a Range: Chromium gives
    // a rect per line, Firefox one per inline run — so the <a> in this copy
    // reports a top a fraction off the text beside it and reads as a line of
    // its own, which dropped the breaks at every width. The height is
    // unambiguous, since the paragraph has no padding of its own.
    const lineHeight = Number.parseFloat(getComputedStyle(el).lineHeight);
    if (!Number.isFinite(lineHeight) || lineHeight <= 0) return;
    const lines = Math.round(el.clientHeight / lineHeight);

    if (lines > breaks + 1) el.classList.add("u-no-breaks");
  };

  measure();
  new ResizeObserver(measure).observe(el);
}
