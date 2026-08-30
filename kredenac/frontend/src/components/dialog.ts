import { h } from "../lib/dom";
import { icon } from "./icon";

export interface DialogHandle {
  close: () => void;
}

interface DialogOptions {
  /**
   * Modifier applied to both the dialog and its backdrop, e.g. "note" gives
   * `dialog--note` / `dialog-backdrop--note`. The backdrop needs it too
   * because the note editor is a full page rather than a modal on mobile.
   */
  variant?: string;
}

const open = new Set<DialogHandle>();

/** Dismisses every open dialog, e.g. when navigating away underneath one. */
export function closeAllDialogs(): void {
  for (const dialog of [...open]) dialog.close();
}

/** Opens a modal dialog (corner-bracketed box, backdrop, X close) and returns a handle to close it. */
export function openDialog(content: (dialog: DialogHandle) => Node, options: DialogOptions = {}): DialogHandle {
  const variant = options.variant;
  const backdrop = h("div", {
    class: `dialog-backdrop${variant ? ` dialog-backdrop--${variant}` : ""}`,
  });

  const onKeydown = (e: KeyboardEvent) => {
    if (e.key === "Escape") handle.close();
  };

  const handle: DialogHandle = {
    close: () => {
      document.removeEventListener("keydown", onKeydown);
      backdrop.remove();
      open.delete(handle);
    },
  };
  open.add(handle);

  const dialog = h(
    "div",
    { class: `dialog${variant ? ` dialog--${variant}` : ""}`, role: "dialog", "aria-modal": "true" },
    h("button", { class: "dialog__close", "aria-label": "Close", onclick: () => handle.close() }, icon("close")),
    content(handle)
  );

  backdrop.append(dialog);
  backdrop.addEventListener("click", (e) => {
    if (e.target === backdrop) handle.close();
  });

  document.addEventListener("keydown", onKeydown);
  document.body.append(backdrop);
  return handle;
}
