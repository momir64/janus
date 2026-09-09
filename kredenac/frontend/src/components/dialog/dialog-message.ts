import { openDialog, type DialogHandle, type DialogOptions } from "./dialog";
import { h } from "../../lib/render/dom";

function dialogMessage(message: string | string[]): {
  el: HTMLElement;
  stop: () => void;
} {
  const segments = Array.isArray(message) ? message : [message];

  const paragraph = h(
    "p",
    { class: "dialog__message" },
    ...segments.flatMap((seg, i) => (i === 0 ? [h("span", {}, seg)] : [" ", h("span", {}, seg)]))
  );

  let lastWidth = -1;
  const reflow = (): void => {
    const width = paragraph.clientWidth;
    if (width === lastWidth) return;
    lastWidth = width;

    paragraph.classList.add("dialog__message--segmented");

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

export function openMessageDialog(
  message: string | string[],
  buttons: (handle: DialogHandle) => HTMLElement[],
  options: DialogOptions = {}
): void {
  const paragraph = dialogMessage(message);

  const dialog = openDialog(
    (handle) =>
      h(
        "div",
        { class: "dialog__body" },
        paragraph.el,
        h("div", { class: "dialog__actions" }, ...buttons(handle))
      ),
    options
  );

  const close = dialog.close;
  dialog.close = () => {
    paragraph.stop();
    close();
  };
}
