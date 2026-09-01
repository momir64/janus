import { contentTab } from "../../lib/state/tab-state";
import { closeAllDialogs } from "../dialog/dialog";
import { icon, type IconName } from "../icon/icon";
import { navigate } from "../../lib/state/router";
import { api } from "../../lib/http/api";
import { h } from "../../lib/render/dom";

export type AppTab = "files" | "notes" | "settings";

interface AppNavOptions {
  active: AppTab;
  onTabChange: (tab: AppTab) => void;
}

export async function logout(): Promise<void> {
  closeAllDialogs();
  await api.auth.logout();
  navigate("/");
}

function navButton(
  iconName: IconName,
  label: string,
  active: boolean,
  onClick: () => void,
  showLabel = true,
  iconLast = false
): HTMLElement {
  const parts = [icon(iconName), showLabel ? h("span", { class: "btn__label" }, label) : null];
  const classes = ["btn", "btn--nav"];
  if (active) classes.push("is-active");
  if (iconLast) classes.push("btn--nav--trailing");
  if (iconName === "settings") classes.push("btn--nav--gear");

  return h(
    "button",
    { class: classes.join(" "), "aria-label": label, onclick: onClick },
    ...(iconLast ? parts.reverse() : parts)
  );
}

export function appNav({ active, onTabChange }: AppNavOptions): [HTMLElement, HTMLElement] {
  const top = h(
    "nav",
    { class: "top-nav" },
    active === "settings"
      ? navButton("back", "Go back", false, () => onTabChange(contentTab()))
      : navButton("settings", "Settings", false, () => onTabChange("settings")),
    navButton("logout", "Log out", false, logout, true, true)
  );

  const bottom = h(
    "nav",
    { class: "bottom-nav" },
    navButton("folder", "Files", active === "files", () => onTabChange("files"), false),
    navButton("sticky-note", "Notes", active === "notes", () => onTabChange("notes"), false),
    navButton("settings", "Settings", active === "settings", () => onTabChange("settings"), false),
    navButton("logout", "Log out", false, logout, false)
  );

  return [top, bottom];
}
