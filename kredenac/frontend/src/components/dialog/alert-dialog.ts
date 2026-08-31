import { button } from "../button/button";
import { h } from "../../lib/dom";
import { openDialog } from "./dialog";
import { dialogMessage } from "./dialog-message";

/**
 * A single-OK dialog for something that has already happened and cannot be
 * declined - a passkey removed, a session ended. It has no X, ignores the
 * backdrop and Escape, and its OK carries whatever follows, so the reader
 * cannot end up back on a page that is no longer theirs.
 */
interface AlertOptions {
  /**
   * A frame other than the standard 640px one:
   *
   *   narrow  : 560px, the signed-out notice
   *   session : 680px, the current-session passkey notice
   */
  frame?: "narrow" | "session";
}

export function alertDialog(
  message: string | string[],
  onOk: () => void | Promise<void>,
  options: AlertOptions = {}
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
            label: "OK",
            variant: "dialog",
            hatch: "br",
            onClick: async () => {
              handle.close();
              await onOk();
            },
          })
        )
      ),
    { dismissible: false, variant: options.frame ? `alert-${options.frame}` : "alert" }
  );

  const close = dialog.close;
  dialog.close = () => {
    paragraph.stop();
    close();
  };
}
