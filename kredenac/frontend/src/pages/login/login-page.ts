import { h, ref, template } from "../../lib/dom";
import { retype, typeInto } from "../../lib/typewriter";
import { navigate } from "../../lib/router";
import { setCsrfToken } from "../../lib/session";
import { enableDevMode } from "../../lib/dev-mode";
import { login } from "../../lib/webauthn";
import { loginEmailField } from "./login-email-field/login-email-field";
import { messageHint } from "../../components/message-hint/message-hint";
import { LOGIN_MESSAGES } from "../../lib/messages";
import { button } from "../../components/button/button";
import { hatchMarks } from "../../components/decorations/decorations";
import cabinet from "../../assets/cabinet.webp";
import markup from "./login-page.html?raw";

const build = template(markup);

export async function loginPage(): Promise<Node> {

  // The message hangs below the buttons and the link is placed from the page
  // bottom, so on a short window the two can meet. The link gives way: it
  // drops by however much they overlap, down to a floor that keeps it on the
  // page, and returns to its measured position once the message clears.
  const FOOTER_GAP = 12;
  const FOOTER_FLOOR = 8;

  function placeFooter(): void {
    if (!footer.isConnected) return; // a stale page's listener still firing
    footer.style.bottom = ""; // measure from the position the design gives it
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
    } catch {
      // TODO: wire the sign-in messages through message.show(), keyed by
      //  LOGIN_MESSAGES: a NotAllowedError stays silent (case 1), any other
      //  DOMException is browserBlocked, an ApiError 401 passkeyRejected and
      //  400 passkeyRetry, 429 tooManyLogins, 5xx serverError, and a fetch
      //  rejection noConnection. passkeyCloned needs api.ts to keep the 401
      //  body and the backend to send a "passkey_cloned" code.
    }
  }

  // While the email field is open, REGISTER becomes the field's submit
  // button; both it and "Lost your passkey?" stay enabled throughout.
  let composing = false;

  // TODO: development shortcuts — typing one of these into the email field
  //  jumps straight to a page instead of sending a magic link. Remove these
  //  (and `onBeforeSubmit` in loginSlot) once the backend flow is wired up
  //  end to end.
  //    a -> logged-in view    b -> valid email page    c -> invalid email page
  const devShortcut = (email: string): boolean => {
    if (email === "a") {
      enableDevMode(); // otherwise the first 401 clears the session again
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

  // The label backspaces and retypes rather than swapping, matching the
  // LOGIN -> placeholder transition. The counter drops any run that a newer
  // toggle has superseded, so rapid clicks can't interleave two animations.
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
      // Opening pairs with the LOGIN -> placeholder animation, so it runs at
      // that pace; dismissing via the X snaps back quicker.
      void setRegisterLabel(isComposing ? "VERIFY EMAIL" : "REGISTER", isComposing ? 55 : 26);
    },
    // While the magic link is being requested the button is the only way to
    // fire a second one, so it locks until the server answers.
    onSendingChange: (sending) => {
      registerButton.disabled = sending;
    },
    onMessage: (key) => message.show(LOGIN_MESSAGES[key]),
    // Closing the field takes any message it produced with it.
    onDismiss: () => message.dismiss(),
    onBeforeSubmit: devShortcut,
  });

  const registerButton = button({
    label: "REGISTER",
    variant: "arrow",
    onClick: () => (composing ? slot.submit() : slot.startCompose()),
  });
  const registerLabel = registerButton.querySelector<HTMLElement>(".btn__label")!;
  const lostLink = h("button", { type: "button", onclick: () => slot.startCompose() }, "Lost your passkey?");
  window.addEventListener("resize", placeFooter);

  const view = build();
  ref<HTMLImageElement>(view, "cabinet").src = cabinet;
  // Drawn rather than written: the marks are computed geometry.
  ref(view, "content").before(hatchMarks());
  ref(view, "actions").append(slot.el, registerButton, message.el);

  const mark = ref(view, "mark");
  const footer = ref(view, "footer");
  footer.append(lostLink);

  const heading = typeInto(mark, "KREDENAC", 120, 300);

  // The caret blinks until the page is first clicked, however long that
  // takes. Waiting on both means a click during the typing retires it the
  // moment the word lands rather than cutting the animation short. Captured
  // so a handler that stops propagation cannot swallow it, and hidden rather
  // than removed so the heading cannot shift as it goes.
  const firstClick = new Promise<void>((resolve) => {
    document.addEventListener("click", () => resolve(), { once: true, capture: true });
  });
  void Promise.all([heading.done, firstClick]).then(() => {
    heading.caret.classList.add("caret--done");
  });

  return view;
}
