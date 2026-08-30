import { mount } from "./dom";

type Route = {
  path: string;
  /** The path split on "/", with ":name" marking a captured segment. */
  segments: string[];
  render: (params: URLSearchParams) => Node | Promise<Node>;
};

const split = (path: string): string[] => path.split("/").filter(Boolean);

/**
 * Segment-by-segment match, returning the captured ":name" values. They are
 * handed to the view in the same bag as the query string, so a view reads
 * `params.get("token")` without caring which half of the URL it came from.
 */
function matchRoute(route: Route, pathname: string): URLSearchParams | null {
  if (route.path === "*") return new URLSearchParams();
  const parts = split(pathname);
  if (parts.length !== route.segments.length) return null;

  const captured = new URLSearchParams();
  for (let i = 0; i < parts.length; i++) {
    const segment = route.segments[i];
    if (segment.startsWith(":")) captured.set(segment.slice(1), decodeURIComponent(parts[i]));
    else if (segment !== parts[i]) return null;
  }
  return captured;
}

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
  routes.push({ path, segments: split(path), render });
}

export function navigate(path: string): void {
  // Renders either way; the entry is only pushed when the address actually
  // changes, so signing in or out at the same path still redraws.
  if (location.pathname + location.search !== path) history.pushState(null, "", path);
  void render();
}

async function render(): Promise<void> {
  // Literal routes are registered before the catch-all, so the first match
  // in order is the most specific one.
  let route: Route | undefined;
  let params: URLSearchParams | undefined;
  for (const candidate of routes) {
    const captured = matchRoute(candidate, location.pathname);
    if (captured) {
      params = captured;
      route = candidate;
      break;
    }
  }
  if (!route || !params) return;

  // Path captures win over a query key of the same name: the address bar is
  // not somewhere a caller can talk their way past the route's own shape.
  for (const [key, value] of new URLSearchParams(location.search)) {
    if (!params.has(key)) params.set(key, value);
  }

  const node = await route.render(params);
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
