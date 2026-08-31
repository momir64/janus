import { messageHint } from "../../components/message-hint/message-hint";
import { loginEmailField } from "./login-email-field/login-email-field";
import { hatchMarks } from "../../components/decorations/decorations";
import { h, onResize, ref, template } from "../../lib/dom";
import { button } from "../../components/button/button";
import { retype, typeInto } from "../../lib/typewriter";
import { LOGIN_MESSAGES } from "../../lib/messages";
import { enableDevMode } from "../../lib/dev-mode";
import { setCsrfToken } from "../../lib/session";
import cabinet from "../../assets/cabinet.webp";
import { failure } from "../../lib/failure";
import { navigate } from "../../lib/router";
import markup from "./login-page.html?raw";
import { login } from "../../lib/webauthn";

const build = template(markup);

export async function loginPage(): Promise<Node> {
  const FOOTER_GAP = 12;
  const FOOTER_FLOOR = 8;

  function placeFooter(): void {
    footer.style.bottom = "";
    const line = message.el.getBoundingClientRect();
    if (line.height === 0) return;

    const overlap = line.bottom + FOOTER_GAP - footer.getBoundingClientRect().top;
    if (overlap <= 0) return;

    const resting = Number.parseFloat(getComputedStyle(footer).bottom);
    footer.style.bottom = `${Math.max(FOOTER_FLOOR, resting - overlap)}px`;
  }

  const message = messageHint({ onLayout: placeFooter });

  async function handleLogin(): Promise<void> {
    try {
      await login();
      navigate("/");
    } catch (error) {
      const { dom, status, code, offline } = failure(error);

      if (dom === "NotAllowedError") return;

      // TODO: `passkey_cloned` is the code the backend is to send when it
      //  revokes a cloned authenticator; until then this reads as a rejected
      //  passkey like any other 401.
      if (code === "passkey_cloned") message.show(LOGIN_MESSAGES.passkeyCloned);
      else if (dom) message.show(LOGIN_MESSAGES.browserBlocked);
      else if (offline) message.show(LOGIN_MESSAGES.noConnection);
      else if (status === 401) message.show(LOGIN_MESSAGES.passkeyRejected);
      else if (status === 400) message.show(LOGIN_MESSAGES.passkeyRetry);
      else if (status === 429) message.show(LOGIN_MESSAGES.tooManyLogins);
      else message.show(LOGIN_MESSAGES.serverError);
    }
  }

  let composing = false;

  // TODO: development shortcuts — typing one of these into the email field
  //  jumps straight to a page instead of sending a magic link. Remove these
  //  (and `onBeforeSubmit` in loginSlot) once the backend flow is wired up
  //  end to end.
  //    a -> logged-in view    b -> valid email page    c -> invalid email page
  const devShortcut = (email: string): boolean => {
    if (email === "a") {
      enableDevMode();
      setCsrfToken("dev");
      navigate("/");
      return true;
    }
    if (email === "b") {
      navigate("/verify/dev");
      return true;
    }
    if (email === "c") {
      navigate("/verify");
      return true;
    }
    return false;
  };

  let labelRun = 0;
  const setRegisterLabel = async (text: string, msPerChar: number) => {
    const run = ++labelRun;
    await retype(registerLabel, text, msPerChar);
    if (run !== labelRun) return;
  };

  const slot = loginEmailField({
    onLogin: handleLogin,
    onComposeChange: (isComposing) => {
      composing = isComposing;
      void setRegisterLabel(isComposing ? "VERIFY EMAIL" : "REGISTER", isComposing ? 55 : 26);
    },
    onSendingChange: (sending) => {
      registerButton.disabled = sending;
    },
    onMessage: (key) => message.show(LOGIN_MESSAGES[key]),
    onDismiss: () => message.dismiss(),
    onBeforeSubmit: devShortcut,
  });

  const registerButton = button({
    label: "REGISTER",
    variant: "arrow",
    onClick: () => (composing ? slot.submit() : slot.startCompose("register")),
  });
  const registerLabel = registerButton.querySelector<HTMLElement>(".btn__label")!;
  const lostLink = h("button", { type: "button", onclick: () => slot.startCompose("recovery") }, "Lost your passkey?");

  const view = build();
  ref<HTMLImageElement>(view, "cabinet").src = cabinet;
  ref(view, "content").before(hatchMarks());
  ref(view, "actions").append(slot.el, registerButton, message.el);

  const mark = ref(view, "mark");
  const footer = ref(view, "footer");
  footer.append(lostLink);
  onResize(footer, placeFooter);

  const heading = typeInto(mark, "KREDENAC", 120, 300);

  const firstClick = new Promise<void>((resolve) => {
    document.addEventListener("click", () => resolve(), { once: true, capture: true });
  });
  void Promise.all([heading.done, firstClick]).then(() => {
    heading.caret.classList.add("caret--done");
  });

  return view;
}
