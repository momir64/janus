import { h } from "../../lib/dom";

import upload from "../../assets/icons/upload.svg";
import download from "../../assets/icons/download.svg";
import deleteIcon from "../../assets/icons/delete.svg";
import addNote from "../../assets/icons/add-note.svg";
import edit from "../../assets/icons/edit.svg";
import logout from "../../assets/icons/logout.svg";
import settings from "../../assets/icons/settings.svg";
import back from "../../assets/icons/back.svg";
import close from "../../assets/icons/close.svg";
import folder from "../../assets/icons/folder.svg";
import stickyNote from "../../assets/icons/sticky-note.svg";

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

export function icon(name: IconName, size?: number): HTMLImageElement {
  return h("img", {
    src: icons[name],
    alt: "",
    // The per-name class lets a glyph carry its own treatment — the gear is
    // rotated 30 degrees in the design, on both breakpoints.
    class: `icon icon--${name}`,
    style: size ? `width:${size}px;height:${size}px` : undefined,
  });
}
