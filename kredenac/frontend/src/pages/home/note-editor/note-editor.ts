import { ref, template } from "../../../lib/dom";
import { button } from "../../../components/button/button";
import { messageHint } from "../../../components/message-hint/message-hint";
import { NOTE_MESSAGES } from "../../../lib/messages";
import markup from "./note-editor.html?raw";
import type { NoteEntry } from "../../../types";

// TODO: the backend validates neither length - NotesRoutes accepts whatever
//  it is given and the columns have no limit of their own - so these are
//  enforced here alone. Add the matching server-side checks.
const TITLE_LIMIT = 200;
const BODY_LIMIT = 2000;

interface NoteEditorOptions {
  note?: NoteEntry;
  onSave: (title: string, content: string) => void | Promise<void>;
  onCancel: () => void;
}

const build = template(markup);

/** The title + body form shared by the desktop "Note dialog" and the mobile "Create/Edit Note" page. */
export function noteEditor({ note, onSave, onCancel }: NoteEditorOptions): HTMLElement {
  const root = build();
  const titleInput = ref<HTMLInputElement>(root, "title");
  const bodyInput = ref<HTMLTextAreaElement>(root, "body");

  // Sits above the title field, out of flow, so nothing below it moves.
  const message = messageHint({ className: "note-editor__message" });
  root.prepend(message.el);

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

  titleInput.value = note?.title ?? "";
  titleInput.maxLength = TITLE_LIMIT;
  // Case 26. The attribute stops the typing at the limit rather than past
  // it, so reaching it is the moment worth saying something.
  titleInput.addEventListener("input", () => {
    if (titleInput.value.length >= TITLE_LIMIT) showMessage("26");
    else resolveMessage("26");
    if (titleInput.value.trim()) resolveMessage("25");
  });

  bodyInput.value = note?.content ?? "";
  bodyInput.maxLength = BODY_LIMIT;
  // Case 27.
  bodyInput.addEventListener("input", () => {
    if (bodyInput.value.length >= BODY_LIMIT) showMessage("27");
    else resolveMessage("27");
    if (bodyInput.value.trim()) resolveMessage("25");
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

  ref(root, "actions").append(
    button({ label: "CANCEL", variant: "dialog", hatch: "tl", onClick: onCancel }),
    button({ label: note ? "SAVE" : "CREATE", variant: "dialog", hatch: "br", onClick: submit })
  );

  return root;
}
