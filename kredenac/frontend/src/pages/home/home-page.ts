import { h, mount, ref, template } from "../../lib/dom";
import { navigate } from "../../lib/router";
import { api } from "../../lib/api";
import { messageHint } from "../../components/message-hint/message-hint";
import { FILE_MESSAGES, NOTE_MESSAGES } from "../../lib/messages";
import { optionalBreaks } from "../../lib/optional-breaks";
import { truncateFilename } from "../../lib/format";
import { isDesktop } from "../../lib/breakpoint";
import { button } from "../../components/button/button";
import { dropzone } from "./dropzone/dropzone";
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

  const DROPZONE_LABEL = "Drag 'n' drop or click to select a file.";
  const zone = dropzone({ onFile: (file) => void uploadFile(file) });
  const dropzoneLabel = zone.label;

  const UPLOAD_LABEL = "Upload new file";
  const uploadButton = button({ label: UPLOAD_LABEL, variant: "corners", onClick: zone.openPicker });
  const uploadLabel = uploadButton.querySelector<HTMLElement>(".btn__label")!;

  // Case 17. While a file is going up, both controls say so instead of
  // inviting another one, and the cancel link takes the slot the messages
  // use - the two can never be needed at once.
  let uploadingName: string | null = null;
  let dotTicker = 0;

  // Three dots, of which the trailing ones are merely invisible: the run
  // always occupies its full width, so a centred label does not slide left
  // and right as the count changes. Padding with spaces would not do - a
  // space and a dot are different widths in a proportional face.
  function dotRun(): { el: HTMLElement; show: (count: number) => void } {
    const dots = [0, 1, 2].map(() => h("span", {}, "."));
    return {
      el: h("span", {}, ...dots),
      show: (count) =>
        dots.forEach((dot, i) => {
          dot.style.visibility = i < count ? "visible" : "hidden";
        }),
    };
  }

  const dropzoneDots = dotRun();
  const buttonDots = dotRun();

  const cancelLink = h(
    "button",
    {
      type: "button",
      class: "home-page__cancel home-page__notice",
      hidden: true,
      // The dropzone is the ancestor here, and its own click opens the file
      // picker: without this, cancelling would immediately ask for another
      // file, since `uploading` is already false by the time it bubbles.
      onclick: (e: MouseEvent) => {
        e.stopPropagation();
        cancelUpload();
      },
    },
    "Cancel file upload"
  );

  /**
   * Writes the two labels for whatever is being uploaded. Kept apart from
   * setUploading so a window crossing the breakpoint can re-cut the name:
   * the button has room for less of it than the dropzone does.
   */
  function renderUploadLabels(): void {
    if (uploadingName === null) {
      dropzoneLabel.replaceChildren(DROPZONE_LABEL);
      uploadLabel.replaceChildren(UPLOAD_LABEL);
      return;
    }

    // No ellipsis, since the dots follow the name directly.
    const shown = truncateFilename(uploadingName, isDesktop() ? 24 : 16, false);
    dropzoneLabel.replaceChildren(`Uploading ${shown}`, dropzoneDots.el);
    uploadLabel.replaceChildren(`Uploading ${shown}`, buttonDots.el);
  }

  function setUploading(filename: string | null): void {
    uploadingName = filename;
    const uploading = filename !== null;
    zone.setUploading(uploading);
    // The uploading label is longer than "Upload new file", so it is set a
    // size smaller to stay on one line.
    uploadButton.classList.toggle("btn--uploading", uploading);
    uploadButton.disabled = uploading;
    cancelLink.hidden = !uploading;
    clearInterval(dotTicker);
    renderUploadLabels();
    if (filename === null) return;

    let step = 0;
    const tick = (): void => {
      const count = (step % 3) + 1;
      dropzoneDots.show(count);
      buttonDots.show(count);
      step += 1;
    };
    tick();
    dotTicker = window.setInterval(tick, 600);
  }

  function cancelUpload(): void {
    // TODO: abort the request itself once an AbortSignal is threaded through
    //  request() and api.files.upload; today this only restores the controls.
    setUploading(null);
  }

  // One line, shown wherever the upload control's cancel link would go: in
  // the dropzone on desktop, in the column between the button and the list
  // on mobile. Only one of those two controls is ever on screen, so the line
  // moves to whichever it is rather than being duplicated.
  // Broken a little before the copy would actually reach the dropzone's
  // dashed edge, which reads as cramped long before it truly overflows.
  const message = messageHint({
    className: "message-hint--inline home-page__notice",
    fitPadding: 32,
  });

  function placeMessage(): void {
    if (!filesColumn.isConnected) return; // a stale page's listener still firing
    const host = isDesktop() ? zone.el : filesColumn;
    if (message.el.parentElement === host) return;

    if (host === zone.el) {
      zone.el.append(message.el, cancelLink);
    } else {
      // attachScrollbar wraps the list, so it is that wrapper which is the
      // column's child and the anchor to insert before. Inserting before the
      // list itself throws, since it is no longer a child of this box.
      const anchor = filesList.closest(".scroll-list") ?? filesList;
      filesColumn.insertBefore(message.el, anchor);
      filesColumn.insertBefore(cancelLink, anchor);
    }
  }

  window.addEventListener("resize", () => {
    placeMessage();
    renderUploadLabels(); // the name is cut shorter on the narrower control
  });

  filesColumn.prepend(zone.el, uploadButton, message.el, cancelLink);

  // Same split as the upload control: desktop gets the icon-trailing "Add a
  // new note" button, mobile gets the compact corner-bracketed one.
  // Below whichever button opened the editor - only one of the two is ever
  // on screen - in the same slot the files column gives its own line.
  const noteMessage = messageHint({
    className: "message-hint--inline home-page__notice",
    fitPadding: 32,
  });

  notesColumn.prepend(
    button({ label: "Add a new note", variant: "icon", icon: "add-note", onClick: openCreateNote }),
    button({ label: "Create new note", variant: "corners", onClick: openCreateNote }),
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
      // Case 24. It takes the empty state's place rather than the line the
      // other file messages use: with nothing listed, "No files yet." would
      // otherwise claim the request had succeeded and found nothing.
      showListError(filesList, FILE_MESSAGES.listFailed);
    }
  }

  /**
   * A failed list, written where its contents would have gone. Static rather
   * than typed: this is the state the column is in, not a passing remark, so
   * it stays until a load succeeds.
   */
  function showListError(list: HTMLElement, text: string): void {
    const lines = text.split("\n");
    const el = h(
      "p",
      { class: "home-page__empty home-page__empty--error" },
      // The trailing space belongs to the line, not the break: with the
      // break dropped the two run together otherwise, and at the end of a
      // line it collapses to nothing.
      ...lines.flatMap((line, i) => (i === lines.length - 1 ? [line] : [`${line} `, h("br")]))
    );
    mount(list, el);
    // Same optional breaks as the dialogs, and only once the paragraph is in
    // the document: measured before that, it has no width to measure against.
    optionalBreaks(el);
  }

  function renderFiles(files: FileEntry[]): void {
    if (files.length === 0) {
      mount(filesList, h("p", { class: "home-page__empty" }, "No files yet."));
      return;
    }

    // Mounted directly, with no wrapper element, so the list's flex gap
    // actually falls between the rows.
    mount(
      filesList,
      ...files.map((file) =>
        fileCard({
          file,
          onDownload: () => api.files.download(file.id, file.filename),
          onDelete: () =>
            confirmDialog(["Are you sure you want to delete", `${file.filename}?`], async () => {
              await api.files.delete(file.id);
              await loadFiles();
            }),
        })
      )
    );
  }

  async function loadNotes(): Promise<void> {
    try {
      renderNotes(await api.notes.list());
    } catch {
      // Case 31, as for the files: "No notes yet." would otherwise report a
      // request that succeeded and found nothing.
      showListError(notesList, NOTE_MESSAGES.listFailed);
    }
  }

  function renderNotes(notes: NoteEntry[]): void {
    if (notes.length === 0) {
      mount(notesList, h("p", { class: "home-page__empty" }, "No notes yet."));
      return;
    }

    mount(
      notesList,
      ...notes.map((note) =>
        noteCard({
          note,
          onEdit: () => openEditNote(note),
          onDelete: () =>
            confirmDialog(["Are you sure you want to delete", `"${note.title}"?`], async () => {
              // TODO: cases 30 and 28 - report a failed delete through
              //  noteMessage.show(): a 404 is NOTE_MESSAGES.noteMissing,
      //  anything else NOTE_MESSAGES.deleteFailed.
              await api.notes.delete(note.id);
              await loadNotes();
            }),
        })
      )
    );
  }

  function openCreateNote(): void {
    openDialog(
      (handle) =>
        noteEditor({
          onSave: async (title, content) => {
            await api.notes.create(title, content);
            handle.close();
            await loadNotes();
          },
          onCancel: () => handle.close(),
        }),
      { variant: "note" }
    );
  }

  function openEditNote(note: NoteEntry): void {
    openDialog(
      (handle) =>
        noteEditor({
          note,
          onSave: async (title, content) => {
            await api.notes.update(note.id, title, content);
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
    // On mobile the note editor sits above the page with the tab bar still
    // reachable, so a tab press has to dismiss it first.
    closeAllDialogs();
    if (tab === "settings") navigate("/settings");
    else setActiveTab(tab);
  }

  closeCutEdge(filesList);
  closeCutEdge(notesList);
  attachScrollbar(filesList);
  attachScrollbar(notesList);

  setActiveTab(activeTab); // the tab last looked at, e.g. on returning from Settings
  void loadFiles();
  void loadNotes();

  // Needs the page in the document: the line reads which layout is in force
  // from its own box.
  requestAnimationFrame(placeMessage);

  return page;
}
