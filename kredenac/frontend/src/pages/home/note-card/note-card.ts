import { ref, template } from "../../../lib/dom";
import { icon } from "../../../components/icon/icon";
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

/** Sets the element's text, or takes it out when there is none to set. */
function fill(el: HTMLElement, text: string | undefined): void {
  if (text) el.textContent = text;
  else el.remove();
}

export function noteCard({ note, onEdit, onDelete }: NoteCardOptions): HTMLElement {
  const root = build();

  // Drawn rather than written, so it stays with the component's code.
  root.prepend(cornerHatch("tl"));

  fill(ref(root, "date"), note.updatedAt && formatTimestamp(note.updatedAt));
  fill(ref(root, "title"), note.title);
  fill(ref(root, "body"), note.content);

  const edit = ref(root, "edit");
  edit.append(icon("edit"));
  edit.addEventListener("click", onEdit);

  const remove = ref(root, "delete");
  remove.append(icon("delete"));
  remove.addEventListener("click", onDelete);

  return root;
}
