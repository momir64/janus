import { h } from "../lib/dom";

/**
 * The paragraph shared by the dialogs, laid out from segments so it can
 * reproduce Figma's line breaks without forcing them.
 *
 * There are three behaviours wanted, and CSS alone only gets two of them:
 *
 *   wide   : everything on one line
 *   design : one line per segment, Figma's breaks
 *   narrow : ordinary wrapping, ignoring the segments entirely
 *
 * Laying the segments out as inline-blocks covers the first two. It gets the
 * third wrong though: a segment too wide for the line wraps inside itself
 * while the next one still starts fresh, which reads as ragged half-lines.
 * So the layout is measured - if any segment had to wrap internally, the
 * segmenting is dropped and the copy flows as plain text.
 *
 * The same idea as optionalBreaks(), which does it for copy broken by <br>
 * instead of by segment.
 *
 * Returns the paragraph and the teardown for the observer watching it.
 */
export function dialogMessage(message: string | string[]): {
  el: HTMLElement;
  stop: () => void;
} {
  const segments = Array.isArray(message) ? message : [message];

  const paragraph = h(
    "p",
    { class: "dialog__message" },
    // Real spaces between the segments are what give the browser its break
    // opportunities.
    ...segments.flatMap((seg, i) => (i === 0 ? [h("span", {}, seg)] : [" ", h("span", {}, seg)]))
  );

  // Re-measured on width changes only; toggling the class alters height, so
  // reacting to that as well would loop.
  let lastWidth = -1;
  const reflow = (): void => {
    const width = paragraph.clientWidth;
    if (width === lastWidth) return;
    lastWidth = width;

    paragraph.classList.add("dialog__message--segmented");

    // An inline-block segment cannot be split across lines, so with every
    // segment on a line of its own the paragraph is exactly as many lines
    // tall as there are segments - and never more, unless one had to wrap
    // inside itself. Fewer is the wide case the design wants, several
    // segments sharing a line.
    //
    // Counted from the height rather than from client rects over each
    // segment: engines disagree on what those return, and an inline-block
    // reports a single rect in some of them however its text wraps inside.
    const lineHeight = Number.parseFloat(getComputedStyle(paragraph).lineHeight);
    if (!Number.isFinite(lineHeight) || lineHeight <= 0) return;
    const lines = Math.round(paragraph.clientHeight / lineHeight);

    if (lines > segments.length) paragraph.classList.remove("dialog__message--segmented");
  };

  reflow();
  const observer = new ResizeObserver(reflow);
  observer.observe(paragraph);

  return { el: paragraph, stop: () => observer.disconnect() };
}
