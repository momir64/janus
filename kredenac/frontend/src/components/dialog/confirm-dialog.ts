import { button } from "../button/button";
import { openMessageDialog } from "./dialog-message";

interface ConfirmOptions {
  frame?: "wide" | "roomy" | "session";
}

export function confirmDialog(
  message: string | string[],
  onConfirm: () => void | Promise<void>,
  options: ConfirmOptions = {}
): void {
  openMessageDialog(
    message,
    (handle) => [
      button({
        label: "YES",
        variant: "dialog",
        hatch: "tl",
        onClick: async () => {
          handle.close();
          await onConfirm();
        },
      }),
      button({ label: "NO", variant: "dialog", hatch: "br", onClick: () => handle.close() }),
    ],
    options.frame ? { variant: options.frame } : {}
  );
}
