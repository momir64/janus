import { chevronTrail, closeGlyph } from "../../../components/decorations/decorations";
import { retypePlaceholder } from "../../../lib/typewriter";
import type { LoginMessage } from "../../../lib/messages";
import { collapseChevrons } from "./chevron-handover";
import { interceptBack } from "../../../lib/router";
import { isDesktop } from "../../../lib/breakpoint";
import { failure } from "../../../lib/failure";
import { api } from "../../../lib/api";
import { h } from "../../../lib/dom";

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
  // TODO: development only - returns true to skip the magic-link request, so
  //  the shortcuts in login-page can jump straight to a screen. Goes with them.
  onBeforeSubmit?: (email: string) => boolean;
}

export type Intent = "register" | "recovery";

export interface LoginEmailFieldHandle {
  el: HTMLElement;
  startCompose: (intent?: Intent) => void;
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
  let intent: Intent = "register";

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

  async function startCompose(opened: Intent): Promise<void> {
    intent = opened;
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
      onMessage(intent === "recovery" ? "recoverySent" : "registrationSent");
    } catch (error) {
      const { status, offline } = failure(error);
      if (offline) onMessage("noConnection");
      else if (status === 429) onMessage("tooManyEmails");
      else onMessage("emailNotSent");
      if (field.isConnected) input.disabled = false;
    } finally {
      sending = false;
      onSendingChange(false);
    }
  }

  return {
    el: root,
    startCompose: (opened = "register") => void startCompose(opened),
    submit: () => {
      const input = root.querySelector<HTMLInputElement>("input");
      if (input) void submit(input.value);
    },
  };
}
