type Attrs = Record<string, string | number | boolean | ((event: any) => void) | undefined>;
type Child = Node | string | null | undefined | false;

const isEventAttr = (key: string): key is `on${string}` => key.startsWith("on");

/** Minimal hyperscript-style element builder, used instead of a framework. */
export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Attrs = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);

  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === false) continue;
    if (isEventAttr(key)) {
      el.addEventListener(key.slice(2).toLowerCase(), value as EventListener);
    } else if (key === "class") {
      el.className = String(value);
    } else if (key in el && typeof (el as unknown as Record<string, unknown>)[key] !== "function") {
      (el as unknown as Record<string, unknown>)[key] = value;
    } else {
      el.setAttribute(key, String(value));
    }
  }

  append(el, children);
  return el;
}

export function svg(tag: string, attrs: Attrs = {}): SVGElement {
  const el = document.createElementNS("http://www.w3.org/2000/svg", tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === false) continue;
    el.setAttribute(key, String(value));
  }
  return el;
}

export function append(el: Element, children: Child[]): void {
  for (const child of children) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(child));
  }
}

export function clear(el: Element): void {
  el.replaceChildren();
}

export function mount(root: Element, ...children: Child[]): void {
  clear(root);
  append(root, children);
}

/**
 * Turns a component's markup into a factory that clones it.
 *
 * Whitespace-only text nodes and comments are stripped as it is parsed: an HTML file is
 * indented to be read, and every one of those gaps is a real text node that
 * building the same markup by hand never produced. Left in, they add a space
 * between inline items, and they defeat :empty - which at least one
 * component relies on to take no space when it has nothing to say.
 */
export function template(markup: string): () => HTMLElement {
  const parsed = document.createElement("template");
  parsed.innerHTML = markup.trim();

  // Comments go with them: they annotate the file for whoever reads it, and
  // have no business in the rendered document.
  const walker = document.createTreeWalker(
    parsed.content,
    NodeFilter.SHOW_TEXT | NodeFilter.SHOW_COMMENT
  );
  const drop: ChildNode[] = [];
  while (walker.nextNode()) {
    const node = walker.currentNode as ChildNode;
    if (node.nodeType === Node.COMMENT_NODE || !(node as Text).data.trim()) drop.push(node);
  }
  drop.forEach((node) => node.remove());

  const root = parsed.content.firstElementChild;
  if (!root) throw new Error("template has no root element");
  return () => root.cloneNode(true) as HTMLElement;
}

/** The element marked `data-ref="name"` inside `root`, which must be there. */
export function ref<T extends HTMLElement = HTMLElement>(root: HTMLElement, name: string): T {
  const found = root.querySelector<T>(`[data-ref="${name}"]`);
  if (!found) throw new Error(`missing [data-ref="${name}"]`);
  return found;
}
