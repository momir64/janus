import { h } from "./dom";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

export interface TypewriterHandle {
  /** The blinking caret span, appended after the text node. */
  caret: HTMLSpanElement;
  /** Resolves once the animation (type or delete) finishes. */
  done: Promise<void>;
}

/**
 * Types `text` into `el` one character at a time with a blinking caret.
 * `el` is expected to be empty; its content becomes a text node followed by the caret.
 *
 * `startDelay` holds the caret blinking on the empty line before the first
 * character lands, so the typing reads as something about to happen rather
 * than as content that was already there.
 */
export function typeInto(
  el: HTMLElement,
  text: string,
  msPerChar = 45,
  startDelay = 0
): TypewriterHandle {
  const textNode = document.createTextNode("");
  const caret = h("span", { class: "caret" });
  el.append(textNode, caret);

  const done = (async () => {
    if (startDelay > 0) await sleep(startDelay);
    for (const char of text) {
      textNode.textContent += char;
      await sleep(msPerChar);
    }
  })();

  return { caret, done };
}

/**
 * Backspaces whatever `el` currently shows, then types `text` in its place.
 * Used for the button labels, which carry no caret once they settle.
 */
export async function retype(el: HTMLElement, text: string, msPerChar = 55): Promise<void> {
  await deleteFrom(el, msPerChar);
  el.replaceChildren();
  const handle = typeInto(el, text, msPerChar);
  await handle.done;
  handle.caret.remove();
}

/**
 * Steps an input's placeholder from `from` to `to`, one character at a time.
 * The input itself stays live and focused throughout.
 */
export async function retypePlaceholder(
  input: HTMLInputElement,
  from: string,
  to: string,
  msPerChar = 55,
  /** Runs while the field is empty, between the delete and type passes. */
  onSwitch?: () => void
): Promise<void> {
  for (let i = from.length; i >= 0; i--) {
    input.placeholder = from.slice(0, i);
    await sleep(msPerChar);
  }
  onSwitch?.();
  for (let i = 1; i <= to.length; i++) {
    input.placeholder = to.slice(0, i);
    await sleep(msPerChar);
  }
}

/** Deletes the text content of `el` (created by {@link typeInto}) one character at a time, from the end. */
export async function deleteFrom(el: HTMLElement, msPerChar = 30): Promise<void> {
  const textNode = [...el.childNodes].find((n) => n.nodeType === Node.TEXT_NODE);
  if (!textNode?.textContent) return;

  while (textNode.textContent.length > 0) {
    textNode.textContent = textNode.textContent.slice(0, -1);
    await sleep(msPerChar);
  }
}
