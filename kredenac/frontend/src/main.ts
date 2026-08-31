import "./style.scss";
import { registerRoute, navigate, startRouter } from "./lib/router";
import { alertDialog } from "./components/dialog/alert-dialog";
import { GLOBAL_MESSAGES } from "./lib/messages";
import { isAuthenticated } from "./lib/session";
import { api } from "./lib/api";
import { loginPage } from "./pages/login/login-page";
import { verifyPage } from "./pages/verify/verify-page";
import { homePage } from "./pages/home/home-page";
import { settingsPage } from "./pages/settings/settings-page";

registerRoute("/", async () => (isAuthenticated() ? homePage() : loginPage()));

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

let signedOutShown = false;
window.addEventListener("kredenac:session-expired", () => {
  if (signedOutShown) return;
  signedOutShown = true;
  alertDialog(
    GLOBAL_MESSAGES.signedOut.split("\n"),
    () => {
      signedOutShown = false;
      navigate("/");
    },
    { frame: "narrow" }
  );
});

const root = document.querySelector<HTMLDivElement>("#app")!;

api.auth
  .refresh()
  .catch(() => undefined)
  .finally(() => startRouter(root));
