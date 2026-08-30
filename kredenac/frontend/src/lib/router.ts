import { mount } from "./dom";

type Route = { path: string; render: (params: URLSearchParams) => Node | Promise<Node> };

const routes: Route[] = [];
let root: Element;
let backInterceptor: (() => boolean) | null = null;

/**
 * Lets a view claim the next Back press for itself. Used where a control has
 * opened something that reads as a step of its own — the login button turning
 * into the email field — so Back closes that before it leaves the page.
 *
 * The handler returns true to consume the press, which leaves the route
 * untouched, or false to let the router render as usual.
 */
export function interceptBack(handler: (() => boolean) | null): void {
  backInterceptor = handler;
}

export function registerRoute(path: string, render: Route["render"]): void {
  routes.push({ path, render });
}

export function navigate(path: string): void {
  // Renders either way; the entry is only pushed when the address actually
  // changes, so signing in or out at the same path still redraws.
  if (location.pathname + location.search !== path) history.pushState(null, "", path);
  void render();
}

async function render(): Promise<void> {
  const route = routes.find((r) => r.path === location.pathname) ?? routes.find((r) => r.path === "*");
  if (!route) return;
  const node = await route.render(new URLSearchParams(location.search));
  mount(root, node);
}

export function startRouter(appRoot: Element): void {
  root = appRoot;
  window.addEventListener("popstate", () => {
    if (backInterceptor?.()) return;
    void render();
  });

  document.addEventListener("click", (e) => {
    const link = (e.target as Element).closest<HTMLAnchorElement>("a[data-link]");
    if (!link) return;
    e.preventDefault();
    navigate(link.getAttribute("href") ?? "/");
  });

  render();
}
