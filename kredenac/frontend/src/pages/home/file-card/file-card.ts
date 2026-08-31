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

  // TODO: the backend's FileDto has no upload timestamp, so `createdAt`
  //  comes back empty and the row simply drops the date. Renders as soon
  //  as the API sends one.
  const date = ref(root, "date");
  if (file.createdAt) date.textContent = formatTimestamp(file.createdAt);
  else date.remove();

  iconButton(ref(root, "delete"), "delete", onDelete);
  iconButton(ref(root, "download"), "download", onDownload);

  return root;
}
