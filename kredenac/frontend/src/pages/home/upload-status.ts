import { h } from "../../lib/dom";
import { isDesktop } from "../../lib/breakpoint";
import { truncateFilename } from "../../lib/format";
import type { DropzoneHandle } from "./dropzone/dropzone";

const DOT_INTERVAL_MS = 600;

interface UploadStatusOptions {
  zone: DropzoneHandle;
  button: HTMLButtonElement;
}

export interface UploadStatusHandle {
  cancelLink: HTMLElement;
  set: (filename: string | null) => void;
  relabel: () => void;
}

function dotRun(): { el: HTMLElement; show: (count: number) => void } {
  const dots = [0, 1, 2].map(() => h("span", {}, "."));
  return {
    el: h("span", {}, ...dots),
    show: (count) =>
      dots.forEach((dot, i) => {
        dot.style.visibility = i < count ? "visible" : "hidden";
      }),
  };
}

export function uploadStatus({ zone, button }: UploadStatusOptions): UploadStatusHandle {
  const buttonLabel = button.querySelector<HTMLElement>(".btn__label")!;
  const idleZone = zone.label.textContent ?? "";
  const idleButton = buttonLabel.textContent ?? "";

  const zoneDots = dotRun();
  const buttonDots = dotRun();

  let filename: string | null = null;
  let ticker = 0;

  const cancelLink = h(
    "button",
    {
      type: "button",
      class: "home-page__cancel home-page__notice",
      hidden: true,
      onclick: (e: MouseEvent) => {
        e.stopPropagation();
        // TODO: abort the request itself once an AbortSignal is threaded
        //  through request() and api.files.upload; today this only restores
        //  the controls.
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
    const shown = truncateFilename(filename, isDesktop() ? 24 : 16, false);
    zone.label.replaceChildren(`Uploading ${shown}`, zoneDots.el);
    buttonLabel.replaceChildren(`Uploading ${shown}`, buttonDots.el);
  }

  function set(next: string | null): void {
    filename = next;
    const uploading = next !== null;

    zone.setUploading(uploading);
    button.classList.toggle("btn--uploading", uploading);
    button.disabled = uploading;
    cancelLink.hidden = !uploading;

    clearInterval(ticker);
    relabel();
    if (!uploading) return;

    let step = 0;
    const tick = (): void => {
      const count = (step % 3) + 1;
      zoneDots.show(count);
      buttonDots.show(count);
      step += 1;
    };
    tick();
    ticker = window.setInterval(tick, DOT_INTERVAL_MS);
  }

  return { cancelLink, set, relabel };
}
