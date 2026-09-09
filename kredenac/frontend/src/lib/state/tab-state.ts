export type ContentTab = "files" | "notes";

let current: ContentTab = "files";

export function contentTab(): ContentTab {
  return current;
}

export function setContentTab(tab: ContentTab): void {
  current = tab;
}
