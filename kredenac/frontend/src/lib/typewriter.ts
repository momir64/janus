import { h } from "./dom";
import { sleep } from "./timing";

// Keeps the caret, an atomic inline, from being stranded on a line of its own.
export const WORD_JOINER = String.fromCharCode(0x2060);

export interface TypewriterHandle {
  caret: HTMLSpanElement;
  done: Promise<void>;
}

export function typeInto(
  el: HTMLElement,
  text: string,
  msPerChar = 45,
  startDelay = 0
): TypewriterHandle {
  const textNode = document.createTextNode("");
  const caret = h("span", { class: "caret" });
  el.append(textNode, document.createTextNode(WORD_JOINER), caret);

  const done = (async () => {
    if (startDelay > 0) await sleep(startDelay);
    caret.classList.add("caret--active");
    for (const char of text) {
      textNode.textContent += char;
      await sleep(msPerChar);
    }
    caret.classList.remove("caret--active");
  })();

  return { caret, done };
}

export async function retype(el: HTMLElement, text: string, msPerChar = 55): Promise<void> {
  await deleteFrom(el, msPerChar);
  el.replaceChildren();
  const handle = typeInto(el, text, msPerChar);
  await handle.done;
  handle.caret.remove();
}

export async function retypePlaceholder(
  input: HTMLInputElement,
  from: string,
  to: string,
  msPerChar = 55,
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

async function deleteFrom(el: HTMLElement, msPerChar = 30): Promise<void> {
  const textNode = [...el.childNodes].find((n) => n.nodeType === Node.TEXT_NODE);
  if (!textNode?.textContent) return;

  const caret = el.querySelector(".caret");
  caret?.classList.add("caret--active");
  while (textNode.textContent.length > 0) {
    textNode.textContent = textNode.textContent.slice(0, -1);
    await sleep(msPerChar);
  }
  caret?.classList.remove("caret--active");
}
