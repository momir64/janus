import { startRegistration, type RegistrationHandle } from "../../lib/webauthn/webauthn";
import { messageHint } from "../../components/message-hint/message-hint";
import { optionalBreaks } from "../../lib/render/optional-breaks";
import emailSuccess from "../../assets/icons/email-success.svg";
import { VERIFY_MESSAGES } from "../../lib/strings/messages";
import { mount, ref, template } from "../../lib/render/dom";
import { button } from "../../components/button/button";
import invalidMarkup from "./verify-invalid.html?raw";
import errorIcon from "../../assets/icons/error.svg";
import { navigate } from "../../lib/state/router";
import { failure } from "../../lib/http/failure";
import markup from "./verify-page.html?raw";

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
  const view = build();

  if (!token) {
    showInvalid(view);
    return view;
  }

  let registration: RegistrationHandle;
  try {
    registration = await startRegistration(token);
  } catch {
    showInvalid(view);
    return view;
  }

  ref<HTMLImageElement>(view, "illustration").src = emailSuccess;
  ref(view, "email").textContent = registration.email;

  const message = messageHint();

  const registerButton = button({
    label: "REGISTER NEW PASSKEY",
    variant: "framed",
    onClick: async () => {
      registerButton.disabled = true;
      try {
        await registration.complete();
        navigate("/");
      } catch (error) {
        const { dom, status } = failure(error);

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
