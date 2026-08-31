import { h, onResize } from "../../lib/dom";
import { WORD_JOINER } from "../../lib/typewriter";
import { sleep } from "../../lib/timing";

const TYPE_MS = 22;
const DELETE_MS = 12;
const HOLD_MS = 3000;

export interface MessageHintOptions {
  className?: string;
  fitPadding?: number;
  onLayout?: () => void;
}

export interface MessageHintHandle {
  el: HTMLElement;
  show: (text: string) => void;
  dismiss: () => void;
}

interface Shown {
  text: string;
  nodes: Text[];
  breaks: HTMLElement[];
  caret: HTMLElement;
  joiner: Text;
  settled: boolean;
}

function fitsOneLine(el: HTMLElement, text: string, padding: number): boolean {
  const probe = h("span", { class: "message-hint__probe" }, text);
  el.append(probe);
  const width = probe.getBoundingClientRect().width;
  probe.remove();
  return width <= el.clientWidth - padding;
}

function layOut(el: HTMLElement, text: string, padding: number): string[] {
  const oneLine = text.replace(/\n/g, " ");
  return fitsOneLine(el, oneLine, padding) ? [oneLine] : text.split("\n");
}

export function messageHint({
  className,
  fitPadding = 0,
  onLayout,
}: MessageHintOptions = {}): MessageHintHandle {
  const el = h("p", { class: className ? `message-hint ${className}` : "message-hint" });

  let run = 0;
  let shown: Shown | null = null;

  let holdResolve: (() => void) | null = null;
  let dismissed = false;

  function reflow(): void {
    if (!shown?.settled) return;

    const lines = layOut(el, shown.text, fitPadding);
    if (lines.length === shown.nodes.length) return;

    el.replaceChildren(shown.joiner, shown.caret);
    shown.nodes = [];
    shown.breaks = [];
    lines.forEach((line) => addLine(shown as Shown, line));
    onLayout?.();
  }

  function addLine(state: Shown, text: string): Text {
    if (state.nodes.length > 0) {
      const br = h("br");
      el.insertBefore(br, state.joiner);
      state.breaks.push(br);
    }
    const node = document.createTextNode(text);
    el.insertBefore(node, state.joiner);
    state.nodes.push(node);
    return node;
  }

  let pending = 0;
  onResize(el, () => {
    cancelAnimationFrame(pending);
    pending = requestAnimationFrame(reflow);
  });

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

    const caret = h("span", { class: "caret caret--active" });
    const joiner = document.createTextNode(WORD_JOINER);
    el.append(joiner, caret);

    if (!el.clientWidth) {
      await new Promise(requestAnimationFrame);
      if (stale()) return;
    }

    const state: Shown = { text, nodes: [], breaks: [], caret, joiner, settled: false };
    shown = state;

    for (const line of layOut(el, text, fitPadding)) {
      const node = addLine(state, "");
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
