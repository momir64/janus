import "./style.scss";
import { registerRoute, navigate, startRouter } from "./lib/router";
import { isAuthenticated } from "./lib/session";
import { api } from "./lib/api";
import { loginPage } from "./pages/loginPage";
import { verifyPage } from "./pages/verifyPage";
import { appPage } from "./pages/appPage";
import { settingsPage } from "./pages/settingsPage";

// "/" is the whole app: the login page before signing in, the files and
// notes it guards afterwards.
registerRoute("/", async () => (isAuthenticated() ? appPage() : loginPage()));

// The token is a path segment, not a query parameter.
// TODO: the backend still builds the query form — MagicLinkService.kt has
//  "$frontendOrigin/verify?token=$token" — so it needs changing to
//  "$frontendOrigin/verify/$token" before a real magic link lands here.
//  Bare "/verify" stays registered for the invalid-link page.
registerRoute("/verify", verifyPage);
registerRoute("/verify/:token", verifyPage);

registerRoute("/settings", async () => {
  if (!isAuthenticated()) {
    navigate("/");
    return document.createComment("redirecting");
  }
  return settingsPage();
});

registerRoute("*", async () => {
  navigate("/");
  return document.createComment("redirecting");
});

const root = document.querySelector<HTMLDivElement>("#app")!;

// Auth cookies are httpOnly; silently try to trade the refresh cookie (if
// any) for a fresh CSRF token before the router's first render decides
// which page to show.
api.auth
  .refresh()
  .catch(() => undefined)
  .finally(() => startRouter(root));
