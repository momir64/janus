import { h } from "../lib/dom";
import { WORD_JOINER } from "../lib/typewriter";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

const TYPE_MS = 22;
const DELETE_MS = 12;
const HOLD_MS = 3000;

export interface MessageLineOptions {
  /** Extra class on the line, for pages that place it differently. */
  className?: string;
  /**
   * Width kept clear at the ends when deciding whether the copy fits on one
   * line, so it breaks a little before it would actually touch the edges.
   */
  fitPadding?: number;
  /** Runs whenever the line gains or loses a row, so siblings can make way. */
  onLayout?: () => void;
}

export interface MessageLineHandle {
  el: HTMLElement;
  /** Types the message in, holds it, then types it away. */
  show: (text: string) => void;
  /** Cuts the hold short, so the message starts leaving now. */
  dismiss: () => void;
}

/** The message as it currently stands, so a resize can re-flow it in place. */
interface Shown {
  text: string;
  nodes: Text[];
  breaks: HTMLElement[];
  caret: HTMLElement;
  joiner: Text;
  /** True once typing has finished and before deleting starts. */
  settled: boolean;
}

/**
 * Whether `text` fits the line on one row. Measured with a hidden probe in
 * the message's own box, so it answers for the font and width actually in
 * force rather than an assumed one. The probe is absolutely positioned and
 * removed before anything can paint, so it is never seen.
 */
function fitsOneLine(el: HTMLElement, text: string, padding: number): boolean {
  const probe = h("span", { class: "message-line__probe" }, text);
  el.append(probe);
  const width = probe.getBoundingClientRect().width;
  probe.remove();
  return width <= el.clientWidth - padding;
}

/** The rows `text` takes at the line's current width: one, or its own split. */
function layOut(el: HTMLElement, text: string, padding: number): string[] {
  const oneLine = text.replace(/\n/g, " ");
  return fitsOneLine(el, oneLine, padding) ? [oneLine] : text.split("\n");
}

/**
 * A page's one response line, written the way the rest of the site writes: typed in a character at a time behind a blinking caret, held, then
 * backspaced away. It is positioned out of flow, so a message arriving never
 * moves the control above it.
 *
 * A "\n" in the text marks where the copy breaks when it is too wide for one
 * line — the same optional-break idea the dialogs use, decided here rather
 * than left to the renderer because the line is typed rather than laid out
 * in one go. A settled message re-decides on resize.
 */
export function messageLine({
  className,
  fitPadding = 0,
  onLayout,
}: MessageLineOptions = {}): MessageLineHandle {
  const el = h("p", { class: className ? `message-line ${className}` : "message-line" });

  // A newer message supersedes whatever is on screen rather than queueing
  // behind it: the run it belongs to is abandoned at the next checkpoint,
  // and its nodes have already been detached by replaceChildren.
  let run = 0;
  let shown: Shown | null = null;

  // Set by dismiss(). Ends the hold if one is running, and is honoured on
  // arrival if the message is still typing itself in.
  let holdResolve: (() => void) | null = null;
  let dismissed = false;

  /** Rebuilds the message at full length for the width it now has. */
  function reflow(): void {
    if (!shown?.settled || !el.isConnected) return; // mid-animation, or stale

    const lines = layOut(el, shown.text, fitPadding);
    if (lines.length === shown.nodes.length) return;

    el.replaceChildren();
    shown.nodes = [];
    shown.breaks = [];

    for (const line of lines) {
      if (shown.nodes.length > 0) {
        const br = h("br");
        el.append(br);
        shown.breaks.push(br);
      }
      const node = document.createTextNode(line);
      el.append(node);
      shown.nodes.push(node);
    }
    el.append(shown.joiner, shown.caret);
    onLayout?.();
  }

  // One listener for the life of the page; a settled message re-flows, an
  // animating one is left to finish at the width it started with.
  let pending = 0;
  window.addEventListener("resize", () => {
    cancelAnimationFrame(pending);
    pending = requestAnimationFrame(reflow);
  });

  /** The pause between writing the message and taking it away. */
  function hold(): Promise<void> {
    if (dismissed) return Promise.resolve();
    return new Promise<void>((resolve) => {
      const timer = setTimeout(resolve, HOLD_MS);
      holdResolve = () => {
        clearTimeout(timer);
        resolve();
      };
    }).finally(() => {
      holdResolve = null;
    });
  }

  async function show(text: string): Promise<void> {
    const current = ++run;
    const stale = () => run !== current;
    dismissed = false;

    el.replaceChildren();

    // The caret and its word joiner stay last; each line is inserted ahead
    // of them as it is typed. They go in before the width is read, since a
    // line with nothing in it can be display:none where it sits in flow.
    const caret = h("span", { class: "caret caret--active" });
    const joiner = document.createTextNode(WORD_JOINER);
    el.append(joiner, caret);

    // Nothing can be measured before the line is in the document: a message
    // raised while its page is still being built would measure zero, take
    // that for a fit and be left to the renderer to wrap. One frame is
    // enough for the box to have a width.
    if (!el.clientWidth) {
      await new Promise(requestAnimationFrame);
      if (stale()) return;
    }

    const state: Shown = { text, nodes: [], breaks: [], caret, joiner, settled: false };
    shown = state;

    for (const line of layOut(el, text, fitPadding)) {
      if (state.nodes.length > 0) {
        const br = h("br");
        el.insertBefore(br, joiner);
        state.breaks.push(br);
      }
      const node = document.createTextNode("");
      el.insertBefore(node, joiner);
      state.nodes.push(node);
      onLayout?.();

      for (const char of line) {
        node.textContent += char;
        await sleep(TYPE_MS);
        if (stale()) return;
      }
    }

    caret.classList.remove("caret--active");
    state.settled = true;
    await hold();
    if (stale()) return;
    state.settled = false;

    // Backspaced the way it was written: the last line first, each break
    // going with the line it introduced.
    caret.classList.add("caret--active");
    for (let i = state.nodes.length - 1; i >= 0; i--) {
      const node = state.nodes[i];
      while (node.textContent) {
        node.textContent = node.textContent.slice(0, -1);
        await sleep(DELETE_MS);
        if (stale()) return;
      }
      state.breaks[i - 1]?.remove();
      onLayout?.();
    }

    el.replaceChildren();
    shown = null;
    onLayout?.();
  }

  return {
    el,
    show: (text) => void show(text),
    dismiss: () => {
      dismissed = true;
      holdResolve?.();
    },
  };
}
