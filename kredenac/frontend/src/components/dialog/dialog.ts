import { ref, template } from "../../lib/dom";
import { iconButton } from "../icon/icon";
import markup from "./dialog.html?raw";

export interface DialogHandle {
  close: () => void;
}

export interface DialogOptions {
  dismissible?: boolean;
  variant?: string;
}

const open = new Set<DialogHandle>();

function shieldPage(): void {
  const page = document.querySelector<HTMLElement>("#app");
  if (page) page.inert = open.size > 0;
}

const build = template(markup);

export function closeAllDialogs(): void {
  for (const dialog of [...open]) dialog.close();
}

export function openDialog(content: (dialog: DialogHandle) => Node, options: DialogOptions = {}): DialogHandle {
  const variant = options.variant;
  const dismissible = options.dismissible ?? true;
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
      shieldPage();
      if (opener?.isConnected) opener.focus({ preventScroll: true });
    },
  };
  open.add(handle);
  shieldPage();

  const close = ref(backdrop, "close");
  if (dismissible) iconButton(close, "close", () => handle.close());
  else close.remove();

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
