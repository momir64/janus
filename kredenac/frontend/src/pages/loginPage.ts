import { h } from "../lib/dom";
import { retype, typeInto } from "../lib/typewriter";
import { navigate } from "../lib/router";
import { setCsrfToken } from "../lib/session";
import { enableDevMode } from "../lib/devMode";
import { login } from "../lib/webauthn";
import { loginSlot } from "../components/loginSlot";
import { messageLine } from "../components/messageLine";
import { LOGIN_MESSAGES } from "../lib/messages";
import { button } from "../components/button";
import { hatchMarks } from "../components/decorations";
import cabinet from "../assets/cabinet.webp";

export async function loginPage(): Promise<Node> {
  const mark = h("h1", { class: "login-page__mark" });
  const footer = h("div", { class: "login-page__footer" });

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

  const message = messageLine({ onLayout: placeFooter });

  async function handleLogin(): Promise<void> {
    try {
      await login();
      navigate("/");
    } catch {
      // TODO: wire the sign-in messages through message.show(), keyed by
      //  LOGIN_MESSAGES: a NotAllowedError stays silent (case 1), any other
      //  DOMException is 2, ApiError 401 is 3a and 400 is 3b, 429 is 5, 5xx
      //  is 6, and a fetch rejection is 4. Case 37 needs api.ts to keep the
      //  401 body and the backend to send a "passkey_cloned" code.
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
  //    s -> settings page
  //  "a" with a case number appended previews that message where it lives
  //  (a18, a26); dev mode is held in memory, so it has to be reached by
  //  navigating rather than by typing the URL.
  const devShortcut = (email: string): boolean => {
    const app = /^([as])([0-9]+[a-z]?)?$/.exec(email);
    if (app) {
      enableDevMode(); // otherwise the first 401 clears the session again
      setCsrfToken("dev");
      const path = app[1] === "s" ? "/settings" : "/";
      navigate(app[2] ? `${path}?m=${app[2]}` : path);
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

  const slot = loginSlot({
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
    onMessage: (caseNumber) => message.show(LOGIN_MESSAGES[caseNumber]),
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
  footer.append(lostLink);
  window.addEventListener("resize", placeFooter);

  const view = h(
    "div",
    { class: "login-page" },
    h("div", { class: "login-page__cabinet" }, h("img", { src: cabinet, alt: "" })),
    h("span", { class: "login-page__divider" }),
    h("span", { class: "login-page__rule login-page__rule--v" }),
    h("span", { class: "login-page__rule login-page__rule--h" }),
    h("span", { class: "login-page__corner login-page__corner--tl" }),
    h("span", { class: "login-page__corner login-page__corner--tr" }),
    h("span", { class: "login-page__corner login-page__corner--bl" }),
    h("span", { class: "login-page__corner login-page__corner--br" }),
    hatchMarks(),
    h(
      "div",
      { class: "login-page__content" },
      h("span", { class: "login-page__bracket" }),
      mark,
      h(
        "p",
        { class: "login-page__tagline" },
        "Secure. Simple. Passwordless.",
        h("br"),
        "Access your account with a passkey."
      ),
      // The message line is a child of the actions box so it can be placed
      // from the buttons' own edges, and absolute so it cannot move them.
      h("div", { class: "login-page__actions" }, slot.el, registerButton, message.el)
    ),
    // A sibling of the content, not a child: it is placed from the page's
    // edges, and the page is the box the content's own percentage padding is
    // measured against. Positioned inside the content instead, its insets
    // would resolve against the content's narrower box and the link would
    // sit off the buttons' centre.
    footer
  );

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
