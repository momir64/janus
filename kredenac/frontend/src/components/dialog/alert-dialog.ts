import { openMessageDialog } from "./dialog-message";
import { button } from "../button/button";

interface AlertOptions {
  frame?: "narrow" | "compact" | "session" | "wide";
  dismissible?: boolean;
}

// "wide" is the shared frame the confirm dialogs use, the rest are alert-only.
const frameVariant = (frame: AlertOptions["frame"]): string =>
  frame === "wide" ? "wide" : frame ? `alert-${frame}` : "alert";

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
    { dismissible: options.dismissible ?? false, variant: frameVariant(options.frame) }
  );
}
