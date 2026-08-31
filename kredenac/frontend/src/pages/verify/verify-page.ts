import { mount, ref, template } from "../../lib/dom";
import { navigate } from "../../lib/router";
import { registerWithToken } from "../../lib/webauthn";
import { optionalBreaks } from "../../lib/optional-breaks";
import { button } from "../../components/button/button";
import { failure } from "../../lib/failure";
import { VERIFY_MESSAGES } from "../../lib/messages";
import { messageHint } from "../../components/message-hint/message-hint";
import markup from "./verify-page.html?raw";
import invalidMarkup from "./verify-invalid.html?raw";
import emailSuccess from "../../assets/icons/email-success.svg";
import errorIcon from "../../assets/icons/error.svg";

const BY_DOM: Record<string, keyof typeof VERIFY_MESSAGES> = {
  NotAllowedError: "registrationCancelled",
  InvalidStateError: "passkeyExists",
  ConstraintError: "deviceCannotStore",
  NotSupportedError: "deviceUnsupported",
  SecurityError: "invalidDomain",
  UnknownError: "browserBlocked",
  AbortError: "browserBlocked",
};

const build = template(markup);
const buildInvalid = template(invalidMarkup);

function showInvalid(view: HTMLElement): void {
  view.classList.add("verify-page--invalid");

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

  const message = messageHint();

  const registerButton = button({
    label: "REGISTER NEW PASSKEY",
    variant: "framed",
    onClick: async () => {
      registerButton.disabled = true;
      try {
        await registerWithToken(token, "Kredenac account");
        navigate("/");
      } catch (error) {
        const { dom, status } = failure(error);

        // TODO: a rejected token only surfaces here, after a passkey has been
        //  created and left orphaned on the device. The planned trade - token
        //  for email, up front - moves this ahead of the ceremony.
        if (status === 401) {
          showInvalid(view);
          return;
        }

        const key =
          (dom ? BY_DOM[dom] : undefined) ??
          (status === 429 ? "tooManyAttempts" : "registrationFailed");
        message.show(VERIFY_MESSAGES[key]);
      } finally {
        registerButton.disabled = false;
      }
    },
  });

  ref(view, "action").append(registerButton, message.el);
  optionalBreaks(ref(view, "success"));

  return view;
}
