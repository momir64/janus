import { mount, ref, template } from "../../lib/dom";
import { navigate } from "../../lib/router";
import { registerWithToken } from "../../lib/webauthn";
import { optionalBreaks } from "../../lib/optional-breaks";
import { button } from "../../components/button/button";
import { messageHint } from "../../components/message-hint/message-hint";
import markup from "./verify-page.html?raw";
import invalidMarkup from "./verify-invalid.html?raw";
import emailSuccess from "../../assets/icons/email-success.svg";
import errorIcon from "../../assets/icons/error.svg";

const build = template(markup);
const buildInvalid = template(invalidMarkup);

/** Swaps the view for the invalid-link copy, which has no control of its own. */
function showInvalid(view: HTMLElement): void {
  view.classList.add("verify-page--invalid");

  // The view's own illustration is kept and re-pointed; everything else it
  // carries is replaced by the copy below.
  const illustration = ref<HTMLImageElement>(view, "illustration");
  illustration.src = errorIcon;

  const message = buildInvalid();
  ref(message, "retry").addEventListener("click", () => navigate("/"));

  mount(view, illustration, message);
  optionalBreaks(message);
}

export async function verifyPage(params: URLSearchParams): Promise<Node> {
  const token = params.get("token");

  if (!token) {
    const view = build();
    showInvalid(view);
    return view;
  }

  const view = build();
  ref<HTMLImageElement>(view, "illustration").src = emailSuccess;

  // TODO: the backend resolves the magic-link token to an email server-side
  //  and never returns it, so there is no endpoint to read the verified
  //  address from. Falls back to the placeholder the design itself uses.
  ref(view, "email").textContent = params.get("email") ?? "example@example.com";

  // Every registration failure belongs to this view — the invalid one has no
  // control to press — so the line sits below the button, as on the login
  // page.
  const message = messageHint();

  const registerButton = button({
    label: "REGISTER NEW PASSKEY",
    variant: "framed",
    onClick: async () => {
      registerButton.disabled = true;
      try {
        await registerWithToken(token, "Kredenac account");
        navigate("/");
      } catch {
        // TODO: wire the registration messages through message.show() and
        //  VERIFY_MESSAGES: NotAllowedError and a resolved
        //  non-PublicKeyCredential are registrationCancelled (cases 14, 16g),
        //  InvalidStateError passkeyExists (15), ConstraintError
        //  deviceCannotStore (16b), NotSupportedError deviceUnsupported
        //  (16c), SecurityError invalidDomain (16d), UnknownError and
        //  AbortError browserBlocked (16e), a 429 tooManyAttempts (16a), and
        //  a 5xx or fetch rejection registrationFailed (16f). Case 13 keeps
        //  the invalid page, and waits on the token-trade flow so an expired
        //  link is caught before a passkey is created.
      } finally {
        registerButton.disabled = false;
      }
    },
  });

  ref(view, "action").append(registerButton, message.el);
  optionalBreaks(ref(view, "success"));

  return view;
}
