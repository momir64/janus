import { formatSize, formatTimestamp } from "../../../lib/format";
import { iconButton } from "../../../components/icon/icon";
import { ref, template } from "../../../lib/dom";
import type { FileDto } from "../../../types";
import markup from "./file-card.html?raw";

interface FileCardOptions {
  file: FileDto;
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
