import { button } from "../button/button";
import { h } from "../../lib/dom";
import { openDialog } from "./dialog";
import { dialogMessage } from "./dialog-message";

interface ConfirmOptions {
  /**
   * A frame other than the standard one, for the confirmations that carry a
   * warning above their question and need the room to hold it:
   *
   *   wide    : 720px, account deletion (146:127)
   *   roomy   : 640px, the last passkey
   *   session : 740px, the current session's passkey
   */
  frame?: "wide" | "roomy" | "session";
}

/**
 * YES/NO confirmation, matching the Deletion / Account deletion dialogs in
 * the design. `message` takes an array to reproduce Figma's line breaks - see
 * dialogMessage, which lays it out.
 */
export function confirmDialog(
  message: string | string[],
  onConfirm: () => void | Promise<void>,
  options: ConfirmOptions = {}
): void {
  const paragraph = dialogMessage(message);

  const dialog = openDialog(
    (handle) =>
      h(
        "div",
        { class: "dialog__body" },
        paragraph.el,
        h(
          "div",
          { class: "dialog__actions" },
          button({
            label: "YES",
            variant: "dialog",
            hatch: "tl",
            onClick: async () => {
              handle.close();
              await onConfirm();
            },
          }),
          button({ label: "NO", variant: "dialog", hatch: "br", onClick: () => handle.close() })
        )
      ),
    options.frame ? { variant: options.frame } : {}
  );

  const close = dialog.close;
  dialog.close = () => {
    paragraph.stop();
    close();
  };
}
