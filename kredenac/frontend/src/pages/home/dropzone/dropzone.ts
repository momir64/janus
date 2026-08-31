import { ref, template } from "../../../lib/dom";
import { icon } from "../../../components/icon/icon";
import markup from "./dropzone.html?raw";

interface DropzoneOptions {
  /** Called for a file dropped on the zone or chosen through the picker. */
  onFile: (file: File) => void;
}

export interface DropzoneHandle {
  el: HTMLElement;
  /** The zone's own text, which the page rewrites while a file is going up. */
  label: HTMLElement;
  /** Opens the file picker - the mobile upload button shares it. */
  openPicker: () => void;
  /** Mid-upload the zone takes nothing further and drops its glyph. */
  setUploading: (uploading: boolean) => void;
}

const build = template(markup);

/**
 * The desktop drag 'n' drop zone. Mobile shows a compact upload button
 * instead - that is what the Figma file itself does, rather than a smaller
 * zone - and both trigger the same hidden file input, which lives here.
 */
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
