import { registerRoute, navigate, startRouter } from "./lib/state/router";
import { alertDialog } from "./components/dialog/alert-dialog";
import { settingsPage } from "./pages/settings/settings-page";
import { GLOBAL_MESSAGES } from "./lib/strings/messages";
import { verifyPage } from "./pages/verify/verify-page";
import { isAuthenticated } from "./lib/http/session";
import { loginPage } from "./pages/login/login-page";
import { homePage } from "./pages/home/home-page";
import { api } from "./lib/http/api";
import "./style.scss";

registerRoute("/", async () => (isAuthenticated() ? homePage() : loginPage()));

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
