import { h, mount } from "../lib/dom";
import { navigate } from "../lib/router";
import { registerWithToken } from "../lib/webauthn";
import { optionalBreaks } from "../lib/optionalBreaks";
import { button } from "../components/button";
import emailSuccess from "../assets/icons/email-success.svg";
import errorIcon from "../assets/icons/error.svg";

// Figma 153:306 / 153:746 — verbatim.
function showInvalid(view: HTMLElement): void {
  view.classList.add("verify-page--invalid");
  let message!: HTMLElement;
  mount(
    view,
    h("img", { class: "verify-page__illustration", src: errorIcon, alt: "" }),
    // Four lines on mobile (153:746), three on desktop, breaking at different
    // words — so both sets are explicit and CSS enables one. Nothing caps the
    // width here: a max-width could only add breaks on top of these.
    (message = h(
      "p",
      { class: "verify-page__message" },
      "Email verification link is invalid! The ",
      h("br", { class: "u-mobile-only" }),
      "link has most ",
      h("br", { class: "u-desktop-only" }),
      "likely expired or has ",
      h("br", { class: "u-mobile-only" }),
      "been already used. ",
      h("br", { class: "u-desktop-only" }),
      "Try sending a ",
      h("br", { class: "u-mobile-only" }),
      h("a", { onclick: () => navigate("/") }, "new verification email"),
      "."
    ))
  );

  optionalBreaks(message);
}

export async function verifyPage(params: URLSearchParams): Promise<Node> {
  const token = params.get("token");
  const view = h("div", { class: "verify-page" });

  if (!token) {
    showInvalid(view);
    return view;
  }

  const registerButton = button({
    label: "REGISTER NEW PASSKEY",
    variant: "framed",
    onClick: async () => {
      registerButton.disabled = true;
      try {
        await registerWithToken(token, "Kredenac account");
        navigate("/");
      } catch {
        showInvalid(view);
      } finally {
        registerButton.disabled = false;
      }
    },
  });

  // TODO: the backend resolves the magic-link token to an email server-side
  //  and never returns it, so there is no endpoint to read the verified
  //  address from. Falls back to the placeholder the design itself uses.
  const email = params.get("email") ?? "example@example.com";

  let successMessage!: HTMLElement;

  mount(
    view,
    h("img", { class: "verify-page__illustration", src: emailSuccess, alt: "" }),
    // Figma 153:269 — "Email <address> has been successfully verified.",
    // with only the address at Regular weight.
    // Figma breaks after "has" on both frames (153:269 / 153:738).
    (successMessage = h(
      "p",
      { class: "verify-page__message" },
      "Email ",
      h("span", { class: "verify-page__email" }, email),
      " has ",
      h("br", {}),
      "been successfully verified."
    )),
    h("div", { class: "verify-page__action" }, registerButton)
  );

  optionalBreaks(successMessage);

  return view;
}
