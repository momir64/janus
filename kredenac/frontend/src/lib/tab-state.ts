export type ContentTab = "files" | "notes";

/**
 * Which of the two lists the app page is showing. Kept here rather than in
 * the address bar: it is a view state, not a place, and a reader who returns
 * from settings expects the tab they left rather than the default one.
 */
let current: ContentTab = "files";

export function contentTab(): ContentTab {
  return current;
}

export function setContentTab(tab: ContentTab): void {
  current = tab;
}
