import { h } from "../lib/dom";
import { button } from "./button";
import { messageLine } from "./messageLine";
import { NOTE_MESSAGES } from "../lib/messages";
import type { NoteEntry } from "../types";

// TODO: the backend validates neither length - NotesRoutes accepts whatever
//  it is given and the columns have no limit of their own - so these are
//  enforced here alone. Add the matching server-side checks.
const TITLE_LIMIT = 200;
const BODY_LIMIT = 2000;

interface NoteEditorOptions {
  note?: NoteEntry;
  /**
   * TODO: development only - a case number from NOTE_MESSAGES, shown as soon
   *  as the editor opens. Remove with the rest of the ?m= scaffolding.
   */
  preview?: string;
  onSave: (title: string, content: string) => void | Promise<void>;
  onCancel: () => void;
}

/** The title + body form shared by the desktop "Note dialog" and the mobile "Create/Edit Note" page. */
export function noteEditor({ note, preview, onSave, onCancel }: NoteEditorOptions): HTMLElement {
  // Sits above the title field, out of flow, so nothing below it moves.
  const message = messageLine({ className: "note-editor__message" });

  // Which case the line is currently making, so that fixing what it
  // complains about takes it away rather than leaving it to time out over a
  // field that now reads correctly.
  let shownCase: string | null = null;

  const showMessage = (caseNumber: string): void => {
    shownCase = caseNumber;
    message.show(NOTE_MESSAGES[caseNumber]);
  };

  const resolveMessage = (caseNumber: string): void => {
    if (shownCase !== caseNumber) return;
    shownCase = null;
    message.dismiss();
  };

  const titleInput = h("input", {
    type: "text",
    placeholder: "Some title...",
    value: note?.title ?? "",
    maxlength: TITLE_LIMIT,
    // Case 26. The attribute stops the typing at the limit rather than past
    // it, so reaching it is the moment worth saying something.
    oninput: () => {
      if (titleInput.value.length >= TITLE_LIMIT) showMessage("26");
      else resolveMessage("26");
      if (titleInput.value.trim()) resolveMessage("25");
    },
  });

  const bodyInput = h("textarea", {
    placeholder: "Lorem ipsum dolor sit amet...",
    value: note?.content ?? "",
    maxlength: BODY_LIMIT,
    // Case 27.
    oninput: () => {
      if (bodyInput.value.length >= BODY_LIMIT) showMessage("27");
      else resolveMessage("27");
      if (bodyInput.value.trim()) resolveMessage("25");
    },
  });

  const submit = async () => {
    const title = titleInput.value.trim();
    const content = bodyInput.value.trim();
    // Either half is enough to make a note; only an entirely empty one is
    // refused, and the empty field is saved as such rather than filled in.
    if (!title && !content) {
      showMessage("25"); // case 25
      return;
    }
    // TODO: case 29 - report a failed save here, once the note messages are
    //  wired to their triggers. The dialog stays open on a rejection, so
    //  this line is the one the reader is looking at.
    await onSave(title, content);
  };

  // TODO: development only, with `preview` above. One frame, so the dialog
  //  has put the line in the document and it has a width to measure.
  if (preview && NOTE_MESSAGES[preview]) {
    requestAnimationFrame(() => showMessage(preview));
  }

  return h(
    "div",
    { class: "note-editor" },
    message.el,
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
