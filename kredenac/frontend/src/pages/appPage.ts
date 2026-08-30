import { h, mount } from "../lib/dom";
import { navigate } from "../lib/router";
import { api } from "../lib/api";
import { icon } from "../components/icon";
import { button } from "../components/button";
import { appNav, type AppTab } from "../components/nav";
import { fileRow } from "../components/fileRow";
import { noteCard } from "../components/noteCard";
import { confirmDialog } from "../components/confirmDialog";
import { closeAllDialogs, openDialog } from "../components/dialog";
import { noteEditor } from "../components/noteEditor";
import type { FileEntry, NoteEntry } from "../types";

export async function appPage(params?: URLSearchParams): Promise<Node> {
  const requested = params?.get("tab");
  let activeTab: AppTab = requested === "notes" ? "notes" : "files";

  const filesList = h("div", { class: "app-page__list" });
  const notesList = h("div", { class: "app-page__list app-page__list--notes" });

  const fileInput = h("input", { type: "file", hidden: true, onchange: handleFileSelected });

  // Desktop shows a large drag 'n' drop dropzone; mobile shows a compact
  // upload button instead — that's what the Figma file itself does, not
  // just a smaller dropzone. Both trigger the same hidden file input.
  const dropzone = h(
    "div",
    { class: "dropzone", onclick: () => fileInput.click() },
    icon("upload"),
    h("span", {}, "Drag 'n' drop or click to select a file."),
    fileInput
  );

  const uploadButton = button({ label: "Upload new file", variant: "corners", onClick: () => fileInput.click() });

  const filesColumn = h(
    "div",
    { class: "app-page__column", "data-tab": "files" },
    dropzone,
    uploadButton,
    filesList
  );

  // Same split as the upload control: desktop gets the icon-trailing "Add a
  // new note" button, mobile gets the compact corner-bracketed one.
  const notesColumn = h(
    "div",
    { class: "app-page__column", "data-tab": "notes" },
    button({ label: "Add a new note", variant: "icon", icon: "add-note", onClick: openCreateNote }),
    button({ label: "Create new note", variant: "corners", onClick: openCreateNote }),
    notesList
  );

  ["dragover", "dragleave", "drop"].forEach((evt) =>
    dropzone.addEventListener(evt, (e) => {
      e.preventDefault();
      dropzone.classList.toggle("dropzone--active", evt === "dragover");
      if (evt === "drop") {
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
    // A failed list still renders the empty state rather than leaving the
    // column blank on an unhandled rejection.
    const files = await api.files.list().catch(() => [] as FileEntry[]);
    renderFiles(files);
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
    const notes = await api.notes.list().catch(() => [] as NoteEntry[]);
    renderNotes(notes);
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
    filesColumn.hidden = tab !== "files";
    notesColumn.hidden = tab !== "notes";
    mount(navSlot, ...appNav({ active: activeTab, onTabChange: handleTabChange }));
  }

  function handleTabChange(tab: AppTab): void {
    // On mobile the note editor sits above the page with the tab bar still
    // reachable, so a tab press has to dismiss it first.
    closeAllDialogs();
    if (tab === "settings") navigate("/app/settings");
    else setActiveTab(tab);
  }

  const navSlot = h("div", {});
  const page = h(
    "div",
    {},
    navSlot,
    h(
      "div",
      { class: "app-page" },
      h("div", { class: "app-page__columns" }, filesColumn, notesColumn)
    )
  );

  setActiveTab(activeTab); // honours ?tab=, e.g. arriving from Settings
  void loadFiles();
  void loadNotes();

  return page;
}
