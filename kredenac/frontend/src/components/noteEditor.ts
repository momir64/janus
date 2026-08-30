import { h } from "../lib/dom";
import { button } from "./button";
import type { NoteEntry } from "../types";

interface NoteEditorOptions {
  note?: NoteEntry;
  onSave: (title: string, content: string) => void | Promise<void>;
  onCancel: () => void;
}

/** The title + body form shared by the desktop "Note dialog" and the mobile "Create/Edit Note" page. */
export function noteEditor({ note, onSave, onCancel }: NoteEditorOptions): HTMLElement {
  const titleInput = h("input", {
    type: "text",
    placeholder: "Some title...",
    value: note?.title ?? "",
    maxlength: 200,
  });

  const bodyInput = h("textarea", {
    placeholder: "Lorem ipsum dolor sit amet...",
    value: note?.content ?? "",
  });

  const submit = async () => {
    const title = titleInput.value.trim();
    const content = bodyInput.value.trim();
    // Either half is enough to make a note; only an entirely empty one is
    // refused, and the empty field is saved as such rather than filled in.
    if (!title && !content) return;
    await onSave(title, content);
  };

  return h(
    "div",
    { class: "note-editor" },
    h("div", { class: "field" }, titleInput),
    h("div", { class: "field field--body note-editor__body" }, bodyInput),
    h(
      "div",
      { class: "note-editor__actions" },
      button({ label: "CANCEL", variant: "dialog", hatch: "tl", onClick: onCancel }),
      button({ label: note ? "SAVE" : "CREATE", variant: "dialog", hatch: "br", onClick: submit })
    )
  );
}
