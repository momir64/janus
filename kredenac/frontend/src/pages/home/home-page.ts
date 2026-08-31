import { h, mount, onResize, ref, template } from "../../lib/dom";
import { navigate } from "../../lib/router";
import { api } from "../../lib/api";
import { messageHint } from "../../components/message-hint/message-hint";
import { FILE_MESSAGES, NOTE_MESSAGES } from "../../lib/messages";
import { optionalBreaks } from "../../lib/optional-breaks";
import { isDesktop } from "../../lib/breakpoint";
import { button } from "../../components/button/button";
import { dropzone } from "./dropzone/dropzone";
import { uploadStatus } from "./upload-status";
import markup from "./home-page.html?raw";
import { appNav, type AppTab } from "../../components/nav/nav";
import { fileCard } from "./file-card/file-card";
import { noteCard } from "./note-card/note-card";
import { confirmDialog } from "../../components/dialog/confirm-dialog";
import { closeAllDialogs, openDialog } from "../../components/dialog/dialog";
import { closeCutEdge } from "../../components/scroll-list/closing-edge";
import { attachScrollbar } from "../../components/scroll-list/scrollbar";
import { noteEditor } from "./note-editor/note-editor";
import { contentTab, setContentTab, type ContentTab } from "../../lib/tab-state";
import type { FileEntry, NoteEntry } from "../../types";

const build = template(markup);

export async function homePage(): Promise<Node> {
  let activeTab: AppTab = contentTab();

  const page = build();
  const filesList = ref(page, "files-list");
  const notesList = ref(page, "notes-list");
  const filesColumn = ref(page, "files-column");
  const notesColumn = ref(page, "notes-column");
  const navSlot = ref(page, "nav");

  const zone = dropzone({ onFile: (file) => void uploadFile(file) });
  const uploadButton = button({ label: "Upload new file", variant: "corners", onClick: zone.openPicker });
  const upload = uploadStatus({ zone, button: uploadButton });

  const columnHint = () =>
    messageHint({ className: "message-hint--inline home-page__notice", fitPadding: 32 });

  const message = columnHint();

  function placeMessage(): void {
    const host = isDesktop() ? zone.el : filesColumn;
    if (message.el.parentElement === host) return;

    if (host === zone.el) {
      zone.el.append(message.el, upload.cancelLink);
    } else {
      const anchor = filesList.closest(".scroll-list") ?? filesList;
      filesColumn.insertBefore(message.el, anchor);
      filesColumn.insertBefore(upload.cancelLink, anchor);
    }
  }

  onResize(page, () => {
    placeMessage();
    upload.relabel();
  });

  filesColumn.prepend(zone.el, uploadButton);
  placeMessage();

  const noteMessage = columnHint();

  notesColumn.prepend(
    button({ label: "Add a new note", variant: "icon", icon: "add-note", onClick: () => openNote() }),
    button({ label: "Create new note", variant: "corners", onClick: () => openNote() }),
    noteMessage.el
  );

  async function uploadFile(file: File): Promise<void> {
    await api.files.upload(file);
    await loadFiles();
  }

  async function loadFiles(): Promise<void> {
    try {
      renderFiles(await api.files.list());
    } catch {
      showListError(filesList, FILE_MESSAGES.listFailed);
    }
  }

  function showListError(list: HTMLElement, text: string): void {
    const lines = text.split("\n");
    const el = h(
      "p",
      { class: "home-page__empty home-page__empty--error" },
      ...lines.flatMap((line, i) => (i === lines.length - 1 ? [line] : [`${line} `, h("br")]))
    );
    mount(list, el);
    optionalBreaks(el);
  }

  function renderList<T>(
    list: HTMLElement,
    items: T[],
    empty: string,
    card: (item: T) => HTMLElement
  ): void {
    if (items.length === 0) mount(list, h("p", { class: "home-page__empty" }, empty));
    else mount(list, ...items.map(card));
  }

  function renderFiles(files: FileEntry[]): void {
    renderList(filesList, files, "No files yet.", (file) =>
      fileCard({
        file,
        onDownload: () => api.files.download(file.id, file.filename),
        onDelete: () =>
          confirmDialog(["Are you sure you want to delete", `${file.filename}?`], async () => {
            await api.files.delete(file.id);
            await loadFiles();
          }),
      })
    );
  }

  async function loadNotes(): Promise<void> {
    try {
      renderNotes(await api.notes.list());
    } catch {
      showListError(notesList, NOTE_MESSAGES.listFailed);
    }
  }

  function renderNotes(notes: NoteEntry[]): void {
    renderList(notesList, notes, "No notes yet.", (note) =>
      noteCard({
        note,
        onEdit: () => openNote(note),
        onDelete: () =>
          confirmDialog(["Are you sure you want to delete", `"${note.title}"?`], async () => {
            // TODO: cases 30 and 28 - report a failed delete through
            //  noteMessage.show(): a 404 is NOTE_MESSAGES.noteMissing,
            //  anything else NOTE_MESSAGES.deleteFailed.
            await api.notes.delete(note.id);
            await loadNotes();
          }),
      })
    );
  }

  function openNote(note?: NoteEntry): void {
    openDialog(
      (handle) =>
        noteEditor({
          note,
          onSave: async (title, content) => {
            await (note
              ? api.notes.update(note.id, title, content)
              : api.notes.create(title, content));
            handle.close();
            await loadNotes();
          },
          onCancel: () => handle.close(),
        }),
      { variant: "note" }
    );
  }

  function setActiveTab(tab: AppTab): void {
    activeTab = tab;
    if (tab !== "settings") setContentTab(tab as ContentTab);
    filesColumn.hidden = tab !== "files";
    notesColumn.hidden = tab !== "notes";
    mount(navSlot, ...appNav({ active: activeTab, onTabChange: handleTabChange }));
  }

  function handleTabChange(tab: AppTab): void {
    closeAllDialogs();
    if (tab === "settings") navigate("/settings");
    else setActiveTab(tab);
  }

  closeCutEdge(filesList);
  closeCutEdge(notesList);
  attachScrollbar(filesList);
  attachScrollbar(notesList);

  setActiveTab(activeTab);
  void loadFiles();
  void loadNotes();


  return page;
}
