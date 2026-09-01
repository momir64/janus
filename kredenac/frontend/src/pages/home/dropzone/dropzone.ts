import { ref, template } from "../../../lib/render/dom";
import { icon } from "../../../components/icon/icon";
import markup from "./dropzone.html?raw";

interface DropzoneOptions {
  onFile: (file: File) => void;
}

export interface DropzoneHandle {
  el: HTMLElement;
  label: HTMLElement;
  openPicker: () => void;
  setUploading: (uploading: boolean) => void;
}

const build = template(markup);

export function dropzone({ onFile }: DropzoneOptions): DropzoneHandle {
  const root = build();
  const input = ref<HTMLInputElement>(root, "input");
  let uploading = false;

  ref(root, "icon").replaceWith(icon("upload"));

  root.addEventListener("click", () => {
    if (!uploading) input.click();
  });

  input.addEventListener("change", () => {
    const file = input.files?.[0];
    if (file) onFile(file);
    input.value = "";
  });

  ["dragover", "dragleave", "drop"].forEach((event) =>
    root.addEventListener(event, (e) => {
      e.preventDefault();
      root.classList.toggle("dropzone--active", event === "dragover");
      if (event !== "drop" || uploading) return;
      const file = (e as DragEvent).dataTransfer?.files[0];
      if (file) onFile(file);
    })
  );

  return {
    el: root,
    label: ref(root, "label"),
    openPicker: () => input.click(),
    setUploading: (next) => {
      uploading = next;
      root.classList.toggle("dropzone--uploading", next);
    },
  };
}
