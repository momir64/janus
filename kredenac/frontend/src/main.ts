import "./style.scss";
import { registerRoute, navigate, startRouter } from "./lib/router";
import { alertDialog } from "./components/alertDialog";
import { GLOBAL_MESSAGES } from "./lib/messages";
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

// Case 39: the session ended under whatever page is open. One dialog only,
// however many requests fail together, and its OK is what returns to the
// login page.
let signedOutShown = false;
window.addEventListener("kredenac:session-expired", () => {
  if (signedOutShown) return;
  signedOutShown = true;
  alertDialog(
    GLOBAL_MESSAGES["39"].split("\n"),
    () => {
      signedOutShown = false;
      navigate("/");
    },
    { frame: "narrow" }
  );
});

const root = document.querySelector<HTMLDivElement>("#app")!;

// Auth cookies are httpOnly; silently try to trade the refresh cookie (if
// any) for a fresh CSRF token before the router's first render decides
// which page to show.
api.auth
  .refresh()
  .catch(() => undefined)
  .finally(() => startRouter(root));
