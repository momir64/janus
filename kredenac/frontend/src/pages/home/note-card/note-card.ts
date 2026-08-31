import { ref, template } from "../../../lib/dom";
import { iconButton } from "../../../components/icon/icon";
import { cornerHatch } from "../../../components/decorations/decorations";
import { formatTimestamp } from "../../../lib/format";
import markup from "./note-card.html?raw";
import type { NoteEntry } from "../../../types";

interface NoteCardOptions {
  note: NoteEntry;
  onEdit: () => void;
  onDelete: () => void;
}

const build = template(markup);

function fill(el: HTMLElement, text: string | undefined): void {
  if (text) el.textContent = text;
  else el.remove();
}

export function noteCard({ note, onEdit, onDelete }: NoteCardOptions): HTMLElement {
  const root = build();

  root.prepend(cornerHatch("tl"));

  fill(ref(root, "date"), note.updatedAt && formatTimestamp(note.updatedAt));
  fill(ref(root, "title"), note.title);
  fill(ref(root, "body"), note.content);

  iconButton(ref(root, "edit"), "edit", onEdit);
  iconButton(ref(root, "delete"), "delete", onDelete);

  return root;
}
