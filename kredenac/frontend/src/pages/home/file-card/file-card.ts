import { ref, template } from "../../../lib/dom";
import { iconButton } from "../../../components/icon/icon";
import { formatSize, formatTimestamp } from "../../../lib/format";
import markup from "./file-card.html?raw";
import type { FileEntry } from "../../../types";

interface FileCardOptions {
  file: FileEntry;
  onDownload: () => void;
  onDelete: () => void;
}

const build = template(markup);

export function fileCard({ file, onDownload, onDelete }: FileCardOptions): HTMLElement {
  const root = build();

  const name = ref(root, "name");
  name.textContent = file.filename;
  name.title = file.filename;

  ref(root, "size").textContent = formatSize(file.size);

  ref(root, "date").textContent = formatTimestamp(file.createdAt);

  iconButton(ref(root, "delete"), "delete", onDelete);
  iconButton(ref(root, "download"), "download", onDownload);

  return root;
}
