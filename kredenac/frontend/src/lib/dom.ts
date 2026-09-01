const MARKUP_ATTRS = new Set(["innerHTML", "outerHTML", "srcdoc", "insertAdjacentHTML"]);
const URL_ATTRS = new Set(["href", "src", "action", "formaction", "xlink:href", "ping"]);
const EXECUTABLE_SCHEME = /^\s*(javascript|vbscript|data:text\/html)/i;

function safeUrl(value: string): string {
  return EXECUTABLE_SCHEME.test(value) ? "" : value;
}

type Attrs = Record<string, string | number | boolean | ((event: any) => void) | undefined>;
type Child = Node | string | null | undefined | false;

const isEventAttr = (key: string): key is `on${string}` => key.startsWith("on");

export function h<K extends keyof HTMLElementTagNameMap>(
  tag: K,
  attrs: Attrs = {},
  ...children: Child[]
): HTMLElementTagNameMap[K] {
  const el = document.createElement(tag);

  for (let [key, value] of Object.entries(attrs)) {
    if (value === undefined || value === false) continue;
    if (MARKUP_ATTRS.has(key)) {
      throw new Error(`${key} is not settable here: it would parse its value as HTML`);
    }
    if (URL_ATTRS.has(key)) value = safeUrl(String(value));
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

function clear(el: Element): void {
  el.replaceChildren();
}

export function mount(root: Element, ...children: Child[]): void {
  clear(root);
  append(root, children);
}

export function template(markup: string): () => HTMLElement {
  const parsed = document.createElement("template");
  parsed.innerHTML = markup.trim();

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

export function ref<T extends HTMLElement = HTMLElement>(root: HTMLElement, name: string): T {
  const found = root.querySelector<T>(`[data-ref="${name}"]`);
  if (!found) throw new Error(`missing [data-ref="${name}"]`);
  return found;
}

export function onResize(el: Element, handler: () => void): void {
  window.addEventListener("resize", () => {
    if (el.isConnected) handler();
  });
}
