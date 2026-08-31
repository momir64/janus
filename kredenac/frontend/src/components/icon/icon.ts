import { h } from "../../lib/dom";

import stickyNote from "../../assets/icons/sticky-note.svg";
import deleteIcon from "../../assets/icons/delete.svg";
import download from "../../assets/icons/download.svg";
import settings from "../../assets/icons/settings.svg";
import addNote from "../../assets/icons/add-note.svg";
import folder from "../../assets/icons/folder.svg";
import logout from "../../assets/icons/logout.svg";
import upload from "../../assets/icons/upload.svg";
import close from "../../assets/icons/close.svg";
import back from "../../assets/icons/back.svg";
import edit from "../../assets/icons/edit.svg";

const icons = {
  upload,
  download,
  delete: deleteIcon,
  "add-note": addNote,
  edit,
  logout,
  settings,
  back,
  close,
  folder,
  "sticky-note": stickyNote,
};

export type IconName = keyof typeof icons;

export function icon(name: IconName): HTMLImageElement {
  return h("img", {
    src: icons[name],
    alt: "",
    class: `icon icon--${name}`,
  });
}

export function iconButton(button: HTMLElement, name: IconName, onClick: () => void): void {
  button.append(icon(name));
  button.addEventListener("click", onClick);
}
