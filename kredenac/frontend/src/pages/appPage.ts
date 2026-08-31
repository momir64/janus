import { h, mount } from "../lib/dom";
import { navigate } from "../lib/router";
import { api } from "../lib/api";
import { icon } from "../components/icon";
import { messageLine } from "../components/messageLine";
import { FILE_MESSAGES, NOTE_MESSAGES } from "../lib/messages";
import { optionalBreaks } from "../lib/optionalBreaks";
import { truncateFilename } from "../lib/format";
import { isDesktop } from "../lib/breakpoint";
import { button } from "../components/button";
import { appNav, type AppTab } from "../components/nav";
import { fileRow } from "../components/fileRow";
import { noteCard } from "../components/noteCard";
import { confirmDialog } from "../components/confirmDialog";
import { closeAllDialogs, openDialog } from "../components/dialog";
import { closeCutEdge } from "../lib/scrollEdge";
import { attachScrollbar } from "../lib/scrollbar";
import { noteEditor } from "../components/noteEditor";
import { contentTab, setContentTab, type ContentTab } from "../lib/appTab";
import type { FileEntry, NoteEntry } from "../types";

export async function appPage(): Promise<Node> {
  let activeTab: AppTab = contentTab();

  const filesList = h("div", { class: "app-page__list" });
  const notesList = h("div", { class: "app-page__list app-page__list--notes" });

  const fileInput = h("input", { type: "file", hidden: true, onchange: handleFileSelected });

  // Desktop shows a large drag 'n' drop dropzone; mobile shows a compact
  // upload button instead — that's what the Figma file itself does, not
  // just a smaller dropzone. Both trigger the same hidden file input.
  const DROPZONE_LABEL = "Drag 'n' drop or click to select a file.";
  const dropzoneLabel = h("span", {}, DROPZONE_LABEL);
  const dropzone = h(
    "div",
    { class: "dropzone", onclick: () => uploadingName === null && fileInput.click() },
    icon("upload"),
    dropzoneLabel,
    fileInput
  );

  const UPLOAD_LABEL = "Upload new file";
  const uploadButton = button({ label: UPLOAD_LABEL, variant: "corners", onClick: () => fileInput.click() });
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
      class: "app-page__cancel app-page__notice",
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
    dropzone.classList.toggle("dropzone--uploading", uploading);
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
  const message = messageLine({
    className: "message-line--inline app-page__notice",
    fitPadding: 32,
  });

  function placeMessage(): void {
    if (!filesColumn.isConnected) return; // a stale page's listener still firing
    const host = isDesktop() ? dropzone : filesColumn;
    if (message.el.parentElement === host) return;

    if (host === dropzone) {
      dropzone.append(message.el, cancelLink);
    } else {
      // attachScrollbar wraps the list, so it is that wrapper which is the
      // column's child and the anchor to insert before. Inserting before the
      // list itself throws, since it is no longer a child of this box.
      const anchor = filesList.closest(".scroll-area") ?? filesList;
      filesColumn.insertBefore(message.el, anchor);
      filesColumn.insertBefore(cancelLink, anchor);
    }
  }

  window.addEventListener("resize", () => {
    placeMessage();
    renderUploadLabels(); // the name is cut shorter on the narrower control
  });

  const filesColumn = h(
    "div",
    { class: "app-page__column", "data-tab": "files" },
    dropzone,
    uploadButton,
    message.el,
    cancelLink,
    filesList
  );

  // Same split as the upload control: desktop gets the icon-trailing "Add a
  // new note" button, mobile gets the compact corner-bracketed one.
  // Below whichever button opened the editor - only one of the two is ever
  // on screen - in the same slot the files column gives its own line.
  const noteMessage = messageLine({
    className: "message-line--inline app-page__notice",
    fitPadding: 32,
  });

  const notesColumn = h(
    "div",
    { class: "app-page__column", "data-tab": "notes" },
    button({ label: "Add a new note", variant: "icon", icon: "add-note", onClick: openCreateNote }),
    button({ label: "Create new note", variant: "corners", onClick: openCreateNote }),
    noteMessage.el,
    notesList
  );

  ["dragover", "dragleave", "drop"].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.toggle("dropzone--active", evt === "dragover");
      if (evt === "drop") {
        if (uploadingName !== null) return;
        const file = (e as DragEvent).dataTransfer?.files[0];
        if (file) void uploadFile(file);
      }
    })
  );

  function handleFileSelected(): void {
    const file = fileInput.files?.[0];
    if (file) void uploadFile(file);
    fileInput.value = "";
  }

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
      showListError(filesList, FILE_MESSAGES["24"]);
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
      { class: "app-page__empty app-page__empty--error" },
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
      mount(filesList, h("p", { class: "app-page__empty" }, "No files yet."));
      return;
    }

    // Mounted directly, with no wrapper element, so the list's flex gap
    // actually falls between the rows.
    mount(
      filesList,
      ...files.map((file) =>
        fileRow({
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
      showListError(notesList, NOTE_MESSAGES["31"]);
    }
  }

  function renderNotes(notes: NoteEntry[]): void {
    if (notes.length === 0) {
      mount(notesList, h("p", { class: "app-page__empty" }, "No notes yet."));
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
              //  noteMessage.show(), a 404 being 28 and anything else 30.
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

  const navSlot = h("div", {});
  const page = h(
    "div",
    // Owns the viewport height so the lists can scroll inside themselves
    // rather than the whole page moving under the nav.
    { class: "app-shell" },
    navSlot,
    h(
      "div",
      { class: "app-page" },
      h("div", { class: "app-page__columns" }, filesColumn, notesColumn)
    )
  );

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
