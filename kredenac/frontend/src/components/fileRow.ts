import { h } from "../lib/dom";
import { icon } from "./icon";
import { formatSize, formatTimestamp } from "../lib/format";
import type { FileEntry } from "../types";

interface FileRowOptions {
  file: FileEntry;
  onDownload: () => void;
  onDelete: () => void;
}

export function fileRow({ file, onDownload, onDelete }: FileRowOptions): HTMLElement {
  return h(
    "div",
    { class: "file-row" },
    h("span", { class: "file-row__name", title: file.filename }, file.filename),
    // Figma sets the label Light and only the value Regular (2032:44).
    h("span", { class: "file-row__size" }, "Size: ", h("b", {}, formatSize(file.size))),
    // TODO: the backend's FileDto has no upload timestamp, so `createdAt`
    //  comes back empty and the row simply drops the date. Renders as soon
    //  as the API sends one.
    file.createdAt
      ? h("span", { class: "file-row__date" }, formatTimestamp(file.createdAt))
      : null,
    h(
      "div",
      { class: "file-row__actions" },
      h("button", { class: "icon-btn", "aria-label": "Delete", onclick: onDelete }, icon("delete")),
      h("button", { class: "icon-btn", "aria-label": "Download", onclick: onDownload }, icon("download"))
    )
  );
}
