import { h, ref, template } from "../../../lib/dom";
import { icon } from "../../../components/icon/icon";
import markup from "./passkey-card.html?raw";
import type { Passkey } from "../../../types";

interface PasskeyCardOptions {
  passkey: Passkey;
  onDelete: () => void;
}

type InfoLine = [label: string, value: string];

const build = template(markup);

/**
 * Fills one of the two detail blocks, or removes it: a card with nothing to
 * say in that column should not leave an empty box behind.
 */
function fillInfo(block: HTMLElement, lines: (InfoLine | null)[]): void {
  const present = lines.filter((line): line is InfoLine => line !== null);
  if (present.length === 0) {
    block.remove();
    return;
  }
  // Figma sets the label Light and only the value Regular (139:135).
  block.append(...present.map(([label, value]) => h("span", {}, `${label} `, h("b", {}, value))));
}

export function passkeyCard({ passkey, onDelete }: PasskeyCardOptions): HTMLElement {
  const { lastUsedIp, lastUsedLocation, lastUsedAt, createdAt } = passkey;
  const root = build();

  ref(root, "name").textContent = passkey.deviceName ?? `Passkey (${passkey.algorithm})`;

  if (!passkey.currentSession) ref(root, "session").remove();

  fillInfo(ref(root, "desktop-info"), [
    lastUsedIp
      ? ["Last used from:", lastUsedLocation ? `${lastUsedIp} (${lastUsedLocation})` : lastUsedIp]
      : null,
    lastUsedAt ? ["Last time used:", lastUsedAt] : null,
    createdAt ? ["Created at:", createdAt] : null,
  ]);

  fillInfo(ref(root, "mobile-info"), [
    lastUsedIp ? ["Last used ip:", lastUsedIp] : null,
    lastUsedLocation ? ["Last used from:", lastUsedLocation] : null,
    lastUsedAt ? ["Last used at:", lastUsedAt] : null,
    createdAt ? ["Created at:", createdAt] : null,
  ]);

  const remove = ref(root, "delete");
  remove.append(icon("delete"));
  remove.addEventListener("click", onDelete);

  return root;
}
