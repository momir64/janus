import { openMessageDialog } from "./dialog-message";
import { button } from "../button/button";

interface AlertOptions {
  frame?: "narrow" | "session";
}

export function alertDialog(
  message: string | string[],
  onOk: () => void | Promise<void>,
  options: AlertOptions = {}
): void {
  openMessageDialog(
    message,
    (handle) => [
      button({
        label: "OK",
        variant: "dialog",
        hatch: "br",
        onClick: async () => {
          handle.close();
          await onOk();
        },
      }),
    ],
    { dismissible: false, variant: options.frame ? `alert-${options.frame}` : "alert" }
  );
}
