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
