import { contentTab, setContentTab, type ContentTab } from "../../lib/state/tab-state";
import { closeAllDialogs, openDialog } from "../../components/dialog/dialog";
import { FILE_MESSAGES, NOTE_MESSAGES } from "../../lib/strings/messages";
import { attachScrollbar } from "../../components/scroll-list/scrollbar";
import { closeCutEdge } from "../../components/scroll-list/closing-edge";
import { h, mount, onResize, ref, template } from "../../lib/render/dom";
import { messageHint } from "../../components/message-hint/message-hint";
import { confirmDialog } from "../../components/dialog/confirm-dialog";
import { optionalBreaks } from "../../lib/render/optional-breaks";
import { appNav, type AppTab } from "../../components/nav/nav";
import { truncateFilename } from "../../lib/strings/format";
import { button } from "../../components/button/button";
import { isDesktop } from "../../lib/render/breakpoint";
import { noteEditor } from "./note-editor/note-editor";
import type { FileDto, NoteDto } from "../../types";
import { navigate } from "../../lib/state/router";
import { failure } from "../../lib/http/failure";
import { fileCard } from "./file-card/file-card";
import { noteCard } from "./note-card/note-card";
import { dropzone } from "./dropzone/dropzone";
import { uploadStatus } from "./upload-status";
import markup from "./home-page.html?raw";
import { api } from "../../lib/http/api";

const build = template(markup);

const MAX_FILE_BYTES = 50 * 1024 * 1024;
const NAME_IN_MESSAGE = 24;
const MAX_FILENAME_LENGTH = 255;

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
  let uploading: AbortController | null = null;
  const upload = uploadStatus({
    zone,
    button: uploadButton,
    onCancel: () => uploading?.abort(),
  });

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
    if (file.size > MAX_FILE_BYTES) {
      message.show(FILE_MESSAGES.fileTooLarge);
      return;
    }
    if (file.name.length > MAX_FILENAME_LENGTH) {
      message.show(FILE_MESSAGES.filenameTooLong);
      return;
    }

    uploading = new AbortController();
    upload.set(file.name);
    try {
      await api.files.upload(file, uploading.signal, upload.setPercent);
      await loadFiles();
    } catch (error) {
      const { dom, status, code } = failure(error);
      if (dom === "AbortError") return;
      if (code === "file_limit") message.show(FILE_MESSAGES.fileLimitReached);
      else message.show(status === 413 ? FILE_MESSAGES.fileTooLarge : FILE_MESSAGES.uploadFailed);
    } finally {
      uploading = null;
      upload.set(null);
    }
  }

  function named(text: string, filename: string): string {
    return text.replace("{filename}", truncateFilename(filename, NAME_IN_MESSAGE));
  }

  function fileFailure(error: unknown, filename: string, otherwise: string): void {
    const { status } = failure(error);
    message.show(named(status === 404 ? FILE_MESSAGES.fileMissing : otherwise, filename));
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

  function renderFiles(files: FileDto[]): void {
    renderList(filesList, files, "No files yet.", (file) =>
      fileCard({
        file,
        onDownload: () =>
          api.files
            .download(file.id, file.filename)
            .catch((error) => fileFailure(error, file.filename, FILE_MESSAGES.downloadFailed)),
        onDelete: () =>
          confirmDialog(["Are you sure you want to delete", `${file.filename}?`], async () => {
            try {
              await api.files.delete(file.id);
              await loadFiles();
            } catch (error) {
              fileFailure(error, file.filename, FILE_MESSAGES.deleteFailed);
            }
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

  function renderNotes(notes: NoteDto[]): void {
    renderList(notesList, notes, "No notes yet.", (note) =>
      noteCard({
        note,
        onEdit: () => openNote(note),
        onDelete: () =>
          confirmDialog(["Are you sure you want to delete", `"${note.title}"?`], async () => {
            try {
              await api.notes.delete(note.id);
              await loadNotes();
            } catch (error) {
              const { status } = failure(error);
              noteMessage.show(
                status === 404 ? NOTE_MESSAGES.noteMissing : NOTE_MESSAGES.deleteFailed
              );
            }
          }),
      })
    );
  }

  function openNote(note?: NoteDto): void {
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
