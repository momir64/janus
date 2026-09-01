import { messageHint } from "../../components/message-hint/message-hint";
import { loginEmailField } from "./login-email-field/login-email-field";
import { LOGIN_MESSAGES, type LoginMessage } from "../../lib/messages";
import { hatchMarks } from "../../components/decorations/decorations";
import { h, onResize, ref, template } from "../../lib/dom";
import { button } from "../../components/button/button";
import { retype, typeInto } from "../../lib/typewriter";
import cabinet from "../../assets/cabinet.webp";
import { failure } from "../../lib/failure";
import { navigate } from "../../lib/router";
import markup from "./login-page.html?raw";
import { login } from "../../lib/webauthn";

const build = template(markup);

const NOTICES = new Set<LoginMessage>(["registrationSent", "recoverySent"]);

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
    onMessage: (key) => message.show(LOGIN_MESSAGES[key], NOTICES.has(key) ? "notice" : undefined),
    onDismiss: () => message.dismiss(),
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
