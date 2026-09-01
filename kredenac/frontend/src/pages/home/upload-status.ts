import type { DropzoneHandle } from "./dropzone/dropzone";
import { h } from "../../lib/render/dom";

interface UploadStatusOptions {
  zone: DropzoneHandle;
  onCancel: () => void;
  button: HTMLButtonElement;
}

export interface UploadStatusHandle {
  cancelLink: HTMLElement;
  set: (filename: string | null) => void;
  setPercent: (percent: number) => void;
  relabel: () => void;
}

export function uploadStatus({ zone, button, onCancel }: UploadStatusOptions): UploadStatusHandle {
  const buttonLabel = button.querySelector<HTMLElement>(".btn__label")!;
  const idleZone = zone.label.textContent ?? "";
  const idleButton = buttonLabel.textContent ?? "";

  let filename: string | null = null;
  let percent = 0;

  const cancelLink = h(
    "button",
    {
      type: "button",
      class: "home-page__cancel home-page__notice",
      hidden: true,
      onclick: (e: MouseEvent) => {
        e.stopPropagation();
        onCancel();
        set(null);
      },
    },
    "Cancel file upload"
  );

  function relabel(): void {
    if (filename === null) {
      zone.label.replaceChildren(idleZone);
      buttonLabel.replaceChildren(idleButton);
      return;
    }
    const label = `File upload is at ${percent}%`;
    zone.label.replaceChildren(label);
    buttonLabel.replaceChildren(label);
  }

  function setPercent(next: number): void {
    if (filename === null || next === percent) return;
    percent = next;
    relabel();
  }

  function set(next: string | null): void {
    filename = next;
    percent = 0;
    const uploading = next !== null;

    zone.setUploading(uploading);
    button.classList.toggle("btn--uploading", uploading);
    button.disabled = uploading;
    cancelLink.hidden = !uploading;

    relabel();
  }

  return { cancelLink, set, setPercent, relabel };
}
