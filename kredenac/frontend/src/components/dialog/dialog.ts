import { ref, template } from "../../lib/dom";
import { icon } from "../icon/icon";
import markup from "./dialog.html?raw";

export interface DialogHandle {
  close: () => void;
}

interface DialogOptions {
  /**
   * Whether the dialog can be dismissed without answering it. The alert
   * dialogs are not: their OK is an acknowledgement that something has
   * already happened, and it carries what follows - a logout, or a return to
   * the login page.
   */
  dismissible?: boolean;
  /**
   * Modifier applied to both the dialog and its backdrop, e.g. "note" gives
   * `dialog--note` / `dialog-backdrop--note`. The backdrop needs it too
   * because the note editor is a full page rather than a modal on mobile.
   */
  variant?: string;
}

const open = new Set<DialogHandle>();

const build = template(markup);

/** Dismisses every open dialog, e.g. when navigating away underneath one. */
export function closeAllDialogs(): void {
  for (const dialog of [...open]) dialog.close();
}

/** Opens a modal dialog (corner-bracketed box, backdrop, X close) and returns a handle to close it. */
export function openDialog(content: (dialog: DialogHandle) => Node, options: DialogOptions = {}): DialogHandle {
  const variant = options.variant;
  const dismissible = options.dismissible ?? true;

  // Whatever opened the dialog keeps the focus otherwise, so an Enter meant
  // for the dialog presses that control again - and opens a second copy of
  // the very dialog being answered.
  const opener = document.activeElement as HTMLElement | null;
  const backdrop = build();
  const dialog = ref(backdrop, "dialog");
  if (variant) {
    backdrop.classList.add(`dialog-backdrop--${variant}`);
    dialog.classList.add(`dialog--${variant}`);
  }

  const onKeydown = (e: KeyboardEvent) => {
    if (e.key === "Escape") handle.close();
  };

  const handle: DialogHandle = {
    close: () => {
      document.removeEventListener("keydown", onKeydown);
      backdrop.remove();
      open.delete(handle);
      // Back to the control that opened it, unless the answer took it away
      // with it - a deleted card's own delete button.
      if (opener?.isConnected) opener.focus({ preventScroll: true });
    },
  };
  open.add(handle);

  const close = ref(backdrop, "close");
  if (dismissible) {
    close.append(icon("close"));
    close.addEventListener("click", () => handle.close());
  } else {
    close.remove();
  }

  ref(backdrop, "content").replaceWith(content(handle));

  if (dismissible) {
    backdrop.addEventListener("click", (e) => {
      if (e.target === backdrop) handle.close();
    });
    document.addEventListener("keydown", onKeydown);
  }

  document.body.append(backdrop);
  dialog.focus({ preventScroll: true });
  return handle;
}
