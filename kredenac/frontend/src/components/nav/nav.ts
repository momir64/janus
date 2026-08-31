import { h } from "../../lib/dom";
import { icon, type IconName } from "../icon/icon";
import { navigate } from "../../lib/router";
import { api } from "../../lib/api";
import { closeAllDialogs } from "../dialog/dialog";
import { contentTab } from "../../lib/tab-state";

export type AppTab = "files" | "notes" | "settings";

interface AppNavOptions {
  active: AppTab;
  onTabChange: (tab: AppTab) => void;
}

export async function logout(): Promise<void> {
  // The note editor sits above the page on mobile with the tab bar still
  // reachable, so it has to be dismissed here too — the tab buttons do the
  // same via handleTabChange, but Log out is not one of them.
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
  // Figma puts the glyph before the label on Settings / Go back (149:201,
  // 152:223) but after it on Log out (149:188), and inset each differently —
  // hence the modifiers, which the desktop nav uses to place them.
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

/**
 * Renders both the desktop top-corner nav and the mobile bottom-tab nav;
 * CSS shows one per breakpoint. On the Settings tab, the desktop nav swaps
 * its "Settings" button for "Go back" (mobile keeps the same four tabs).
 */
export function appNav({ active, onTabChange }: AppNavOptions): [HTMLElement, HTMLElement] {
  const top = h(
    "nav",
    { class: "top-nav" },
    active === "settings"
      // Back to whichever list was last open, not always the first one.
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
