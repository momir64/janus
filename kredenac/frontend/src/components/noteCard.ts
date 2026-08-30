import { h } from "../lib/dom";
import { icon } from "./icon";
import { cornerHatch } from "./decorations";
import { formatTimestamp } from "../lib/format";
import type { NoteEntry } from "../types";

interface NoteCardOptions {
  note: NoteEntry;
  onEdit: () => void;
  onDelete: () => void;
}

export function noteCard({ note, onEdit, onDelete }: NoteCardOptions): HTMLElement {
  return h(
    "div",
    { class: "note-card" },
    cornerHatch("tl"),
    // Top-right, on its own offset rather than the title's (2035:160 mobile,
    // 2032:34 desktop).
    note.updatedAt
      ? h("span", { class: "note-card__date" }, formatTimestamp(note.updatedAt))
      : null,
    // A note needs only one of the two, so the empty half is left out
    // entirely rather than rendered as a blank line.
    note.title ? h("h3", { class: "note-card__title" }, note.title) : null,
    note.content ? h("p", { class: "note-card__body" }, note.content) : null,
    h(
      "div",
      { class: "note-card__actions" },
      h("button", { class: "icon-btn", "aria-label": "Edit", onclick: onEdit }, icon("edit")),
      h("button", { class: "icon-btn", "aria-label": "Delete", onclick: onDelete }, icon("delete"))
    )
  );
}

