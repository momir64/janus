import { h } from "../lib/dom";
import { retypePlaceholder } from "../lib/typewriter";
import { chevronTrail, closeGlyph } from "./decorations";
import { api } from "../lib/api";
import { interceptBack } from "../lib/router";

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));
const EMAIL_PLACEHOLDER = "john.smith@example.com";
const LOGIN_LABEL = "LOGIN";

const CHEVRON_STAGGER_MS = 70;
// Must outlast the 0.3s transform transition on .chevron-trail polyline: the
// trail is swapped for the field's corner mark the moment this elapses, and
// cutting it short hands over a chevron that is still rotating, which reads
// as a snap.
const CORNER_SETTLE_MS = 340;
const PLACEHOLDER_SPEED_MS = 55;

/**
 * Removes the chevrons one at a time from the left, the way text backspaces
 * away, while easing the whole trail downwards. The rightmost chevron is the
 * one left standing, so by the time it rotates it is already sitting where
 * the field's corner bracket goes and the swap is invisible.
 */
async function collapseChevrons(trail: SVGSVGElement): Promise<void> {
  const chevrons = Array.from(trail.querySelectorAll("polyline"));
  const [surviving, ...rest] = chevrons;
  if (!surviving) return;

  const [slide, drop] = handoverOffset(trail, surviving);

  // Glides between the steps instead of jumping, so the run reads as one
  // movement rather than as the seven it is made of.
  trail.style.transition = `transform ${CHEVRON_STAGGER_MS}ms linear`;

  for (let i = 0; i < rest.length; i++) {
    rest[rest.length - 1 - i].remove(); // leftmost first
    // Squared, so the steps start close together and lengthen towards the
    // corner: the trail sets off slowly and gathers pace into the handover.
    const progress = (i + 1) / rest.length;
    const travelled = progress * progress;
    trail.style.transform =
      `translate(${(travelled * slide).toFixed(2)}px, ${(travelled * drop).toFixed(2)}px)`;
    await sleep(CHEVRON_STAGGER_MS);
  }

  surviving.classList.add("chevron-trail__corner");
  await sleep(CORNER_SETTLE_MS);
}

/**
 * How far the trail has to travel for the surviving chevron to come to rest
 * exactly on the corner mark it hands over to — the field's ::after.
 *
 * Measured rather than tuned, and from the chevron's own point rather than
 * its box: a rotated element's client rect is the rotated *rectangle*, whose
 * corner sits well past the tip of the V inside it. So the tip is read from
 * the polyline, then turned 45° about the box's centre the way the CSS will
 * turn it.
 */
function handoverOffset(trail: SVGSVGElement, surviving: SVGPolylineElement): [number, number] {
  const field = trail.parentElement;
  const ctm = surviving.getScreenCTM();
  if (!field || !ctm) return [0, 0];

  const fieldRect = field.getBoundingClientRect();
  const fieldStyle = getComputedStyle(field);
  const mark = getComputedStyle(field, "::after");

  // The mark is an L of some thickness, and the chevron a V drawn down the
  // middle of its stroke — so the tip belongs on the centre line of the
  // mark's corner, half a border in from its outer edge.
  const inset = (side: "Right" | "Bottom"): number =>
    Number.parseFloat(fieldStyle[`border${side}Width`]) +
    Number.parseFloat(mark[side.toLowerCase() as "right" | "bottom"]) +
    Number.parseFloat(mark[`border${side}Width`]) / 2;

  const markRight = fieldRect.right - inset("Right");
  const markBottom = fieldRect.bottom - inset("Bottom");

  // The tip is the point furthest along the trail's own direction of travel.
  const points = Array.from(surviving.points);
  const tip = points.reduce((far, p) => (p.x > far.x ? p : far), points[0]);
  if (!tip) return [0, 0];

  const tipX = ctm.a * tip.x + ctm.c * tip.y + ctm.e;
  const tipY = ctm.b * tip.x + ctm.d * tip.y + ctm.f;

  const box = surviving.getBoundingClientRect();
  const cx = (box.left + box.right) / 2;
  const cy = (box.top + box.bottom) / 2;

  // Where that tip ends up once the CSS turns the chevron 45° clockwise
  // about its centre.
  const dx = tipX - cx;
  const dy = tipY - cy;
  const restX = cx + (dx - dy) / Math.SQRT2;
  const restY = cy + (dx + dy) / Math.SQRT2;

  const slide = markRight - restX;
  const drop = markBottom - restY;
  return [Number.isFinite(slide) ? slide : 0, Number.isFinite(drop) ? drop : 0];
}

interface LoginSlotOptions {
  /** Called when the LOGIN button (idle state) is pressed to sign in. */
  onLogin: () => void | Promise<void>;
  /** Called when the email field opens or closes, so sibling controls can react. */
  onComposeChange: (composing: boolean) => void;
  /**
   * Called while a magic-link request is in flight, so the sibling button
   * that submits the field can lock for the duration.
   */
  onSendingChange: (sending: boolean) => void;
  /**
   * Given the submitted address, returns true to skip the normal
   * magic-link request. TODO: only used by the development shortcuts in
   * loginPage — remove both together.
   */
  onBeforeSubmit?: (email: string) => boolean;
}

export interface LoginSlotHandle {
  el: HTMLElement;
  /** Morphs the LOGIN button into the email-capture field. */
  startCompose: () => void;
  /** Submits whatever is currently in the email field, if it is open. */
  submit: () => void;
}

// Matches $breakpoint-desktop in styles/_tokens.scss.
const DESKTOP_MIN_WIDTH = 900;

const isDesktop = (): boolean => window.matchMedia(`(min-width: ${DESKTOP_MIN_WIDTH}px)`).matches;

export function loginSlot({ onLogin, onComposeChange, onSendingChange, onBeforeSubmit }: LoginSlotOptions): LoginSlotHandle {
  let root: HTMLElement = idleButton();

  // The open field is a step of its own as far as the user is concerned, so
  // it takes a history entry and Back closes it exactly as the X does. The
  // entry duplicates the current URL, so nothing in the address bar moves.
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

    // Our own entry being unwound after the field was closed some other way.
    if (dropping) {
      dropping = false;
      return true;
    }
    // The page has moved on and this slot is stale — let the router have it.
    if (!root.isConnected) return false;

    revert();
    return true;
  }

  function releaseBack(): void {
    // Left in place when the slot is gone: unwinding then would take the user
    // off a page they have since navigated to.
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
    if (!(current instanceof HTMLButtonElement)) return; // already composing

    onComposeChange(true);

    // The field replaces the button straight away — it is live and focused
    // while the chevrons are still draining — rather than after the
    // animation. The trail is carried across so it can finish in place.
    const trail = current.querySelector<SVGSVGElement>(".chevron-trail")!;
    const field = composeField();
    const input = field.querySelector<HTMLInputElement>("input")!;
    field.prepend(trail);
    current.replaceWith(field);
    root = field;
    claimBack();
    // On a phone the focus brings the on-screen keyboard up with it, which
    // resizes the viewport and drags the page about while the chevrons are
    // still draining — so there the field is focused once the transform has
    // finished instead (see the end of this function).
    if (isDesktop()) input.focus();

    // Label -> placeholder starts now, in parallel with the chevrons. The
    // type size drops in the gap between the two passes, while the field is
    // empty, so the change is never visible mid-word.
    const placeholder = retypePlaceholder(input, LOGIN_LABEL, EMAIL_PLACEHOLDER, PLACEHOLDER_SPEED_MS, () =>
      field.classList.add("login-slot__field--typing")
    );

    await collapseChevrons(trail);
    if (!field.isConnected) return; // cancelled mid-animation
    trail.remove();
    field.classList.add("login-slot__field--settled");
    await placeholder;

    // The caret waits for the placeholder to finish writing itself, so it
    // never appears to be sitting inside text the reader did not type. A
    // click brings it earlier — see composeField.
    if (field.isConnected) field.classList.add("login-slot__field--caret");

    if (!isDesktop() && field.isConnected) input.focus();
  }

  function composeField(): HTMLElement {
    const input = h("input", {
      type: "email",
      class: "login-slot__input",
      required: true,
      onkeydown: (e: KeyboardEvent) => {
        if (e.key === "Enter") {
          e.preventDefault();
          void submit(input.value);
        }
      },
    });

    const closeBtn = h(
      "button",
      { type: "button", class: "login-slot__close", "aria-label": "Cancel", onclick: () => revert() },
      closeGlyph()
    );

    return h(
      "div",
      {
        class: "btn btn--arrow login-slot__field",
        // The input stretches to the field's height, but the field's own
        // padding is still dead space — clicking there should focus it too.
        onclick: (e: MouseEvent) => {
          if ((e.target as Element).closest(".login-slot__close")) return;
          // Reaching for the field is a request to type in it, so the caret
          // appears now rather than waiting for the placeholder to land.
          input.closest(".login-slot__field")?.classList.add("login-slot__field--caret");
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

    if (onBeforeSubmit?.(email)) return;

    if (!input.checkValidity()) {
      input.reportValidity();
      return;
    }

    // Both halves of the control lock the moment it is pressed, so a second
    // press while the request is in flight cannot send a second email.
    sending = true;
    input.disabled = true;
    onSendingChange(true);
    try {
      await api.auth.requestMagicLink(email);
      // The sent state is the field simply closing again, as if the X had
      // been pressed — there is no confirmation badge.
      if (field.isConnected) revert();
    } catch {
      // TODO: report the failure to the user; today the field just unlocks.
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
