import { ref, template } from "../../../lib/dom";
import { button } from "../../../components/button/button";
import { messageHint } from "../../../components/message-hint/message-hint";
import { NOTE_MESSAGES, type NoteMessage } from "../../../lib/messages";
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

export function noteEditor({ note, onSave, onCancel }: NoteEditorOptions): HTMLElement {
  const root = build();
  const titleInput = ref<HTMLInputElement>(root, "title");
  const bodyInput = ref<HTMLTextAreaElement>(root, "body");

  const message = messageHint({ className: "note-editor__message" });
  root.prepend(message.el);

  let shown: NoteMessage | null = null;

  const showMessage = (key: NoteMessage): void => {
    shown = key;
    message.show(NOTE_MESSAGES[key]);
  };

  const resolveMessage = (key: NoteMessage): void => {
    if (shown !== key) return;
    shown = null;
    message.dismiss();
  };

  titleInput.value = note?.title ?? "";
  titleInput.maxLength = TITLE_LIMIT;
  titleInput.addEventListener("input", () => {
    if (titleInput.value.length >= TITLE_LIMIT) showMessage("titleTooLong");
    else resolveMessage("titleTooLong");
    if (titleInput.value.trim()) resolveMessage("noteEmpty");
  });

  bodyInput.value = note?.content ?? "";
  bodyInput.maxLength = BODY_LIMIT;
  bodyInput.addEventListener("input", () => {
    if (bodyInput.value.length >= BODY_LIMIT) showMessage("bodyTooLong");
    else resolveMessage("bodyTooLong");
    if (bodyInput.value.trim()) resolveMessage("noteEmpty");
  });

  const submit = async () => {
    const title = titleInput.value.trim();
    const content = bodyInput.value.trim();
    if (!title && !content) {
      showMessage("noteEmpty");
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
