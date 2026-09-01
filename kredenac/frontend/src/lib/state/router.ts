import { mount } from "../render/dom";

type Route = {
  path: string;
  segments: string[];
  render: (params: URLSearchParams) => Node | Promise<Node>;
};

const split = (path: string): string[] => path.split("/").filter(Boolean);

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

let backInterceptor: (() => boolean) | null = null;
const routes: Route[] = [];
let root: Element;

export function interceptBack(handler: (() => boolean) | null): void {
  backInterceptor = handler;
}

export function registerRoute(path: string, render: Route["render"]): void {
  routes.push({ path, segments: split(path), render });
}

export function navigate(path: string): void {
  if (location.pathname + location.search !== path) history.pushState(null, "", path);
  void render();
}

async function render(): Promise<void> {
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

  void render();
}
