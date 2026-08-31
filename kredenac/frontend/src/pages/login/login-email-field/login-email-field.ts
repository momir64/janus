import { h } from "../../../lib/dom";
import { retypePlaceholder } from "../../../lib/typewriter";
import { chevronTrail, closeGlyph } from "../../../components/decorations/decorations";
import { api } from "../../../lib/api";
import { interceptBack } from "../../../lib/router";
import type { LoginMessage } from "../../../lib/messages";
import { isDesktop } from "../../../lib/breakpoint";
import { collapseChevrons } from "./chevron-handover";

const EMAIL_PLACEHOLDER = "john.smith@example.com";
const PLACEHOLDER_SPEED_MS = 55;
const LOGIN_LABEL = "LOGIN";

const EMAIL_PATTERN = /^[^\s@]+@[a-zA-Z0-9][a-zA-Z0-9-]*(\.[a-zA-Z0-9][a-zA-Z0-9-]*)*\.[a-zA-Z]{2,}$/;

interface LoginEmailFieldOptions {
  onLogin: () => void | Promise<void>;
  onComposeChange: (composing: boolean) => void;
  onDismiss?: () => void;
  onSendingChange: (sending: boolean) => void;
  onMessage: (key: LoginMessage) => void;
  /**
   * Given the submitted address, returns true to skip the normal
   * magic-link request. TODO: only used by the development shortcuts in
   * loginPage — remove both together.
   */
  onBeforeSubmit?: (email: string) => boolean;
}

export interface LoginEmailFieldHandle {
  el: HTMLElement;
  startCompose: () => void;
  submit: () => void;
}

export function loginEmailField({
  onLogin,
  onComposeChange,
  onSendingChange,
  onMessage,
  onDismiss,
  onBeforeSubmit,
}: LoginEmailFieldOptions): LoginEmailFieldHandle {
  let root: HTMLElement = idleButton();

  let hasEntry = false;
  let dropping = false;
  let sending = false;

  function claimBack(): void {
    hasEntry = true;
    history.pushState({ composing: true }, "", location.href);
    interceptBack(onBack);
  }

  function onBack(): boolean {
    if (!hasEntry) return false;
    hasEntry = false;
    interceptBack(null);

    if (dropping) {
      dropping = false;
      return true;
    }
    if (!root.isConnected) return false;

    revert();
    onDismiss?.();
    return true;
  }

  function releaseBack(): void {
    if (!hasEntry || !root.isConnected) return;
    dropping = true;
    history.back();
  }

  function idleButton(): HTMLButtonElement {
    return h(
      "button",
      { type: "button", class: "btn btn--arrow", onclick: () => onLogin() },
      h("span", { class: "btn__label" }, LOGIN_LABEL),
      chevronTrail()
    );
  }

  async function startCompose(): Promise<void> {
    const current = root;
    if (!(current instanceof HTMLButtonElement)) return;

    onComposeChange(true);

    const trail = current.querySelector<SVGSVGElement>(".chevron-trail")!;
    const field = composeField();
    const input = field.querySelector<HTMLInputElement>("input")!;
    field.prepend(trail);
    current.replaceWith(field);
    root = field;
    claimBack();
    if (isDesktop()) input.focus();

    const placeholder = retypePlaceholder(input, LOGIN_LABEL, EMAIL_PLACEHOLDER, PLACEHOLDER_SPEED_MS, () =>
      field.classList.add("login-email-field__field--typing")
    );

    await collapseChevrons(trail);
    if (!field.isConnected) return;
    trail.remove();
    field.classList.add("login-email-field__field--settled");
    await placeholder;

    if (field.isConnected) field.classList.add("login-email-field__field--caret");

    if (!isDesktop() && field.isConnected) input.focus();
  }

  function composeField(): HTMLElement {
    const input = h("input", {
      type: "email",
      class: "login-email-field__input",
      required: true,
      "aria-label": "Email address",
      onkeydown: (e: KeyboardEvent) => {
        if (e.key === "Enter") {
          e.preventDefault();
          void submit(input.value);
        }
      },
    });

    const closeBtn = h(
      "button",
      {
        type: "button",
        class: "login-email-field__close",
        "aria-label": "Cancel",
        onclick: () => {
          revert();
          onDismiss?.();
        },
      },
      closeGlyph()
    );

    return h(
      "div",
      {
        class: "btn btn--arrow login-email-field__field",
        onclick: (e: MouseEvent) => {
          if ((e.target as Element).closest(".login-email-field__close")) return;
          input.closest(".login-email-field__field")?.classList.add("login-email-field__field--caret");
          input.focus();
        },
      },
      input,
      closeBtn
    );
  }

  function revert(): void {
    releaseBack();
    const fresh = idleButton();
    root.replaceWith(fresh);
    root = fresh;
    onComposeChange(false);
  }

  async function submit(email: string): Promise<void> {
    const field = root;
    const input = field.querySelector<HTMLInputElement>("input");
    if (!input || sending) return;

    const address = email.trim();

    if (onBeforeSubmit?.(address)) return;

    if (!address) {
      onMessage("emailMissing");
      return;
    }

    if (!EMAIL_PATTERN.test(address)) {
      onMessage("emailInvalid");
      return;
    }

    sending = true;
    input.disabled = true;
    onSendingChange(true);
    try {
      await api.auth.requestMagicLink(address);
      if (field.isConnected) revert();
    } catch {
      // TODO: wire the magic-link messages — 5xx is case 8, 429 is 9, a
      //  fetch rejection is 10 — and on success 11a or 11b depending on
      //  whether the field was opened by REGISTER or "Lost your passkey?",
      //  which the slot does not track yet.
      if (field.isConnected) input.disabled = false;
    } finally {
      sending = false;
      onSendingChange(false);
    }
  }

  return {
    el: root,
    startCompose: () => void startCompose(),
    submit: () => {
      const input = root.querySelector<HTMLInputElement>("input");
      if (input) void submit(input.value);
    },
  };
}
