import { iconButton } from "../../../components/icon/icon";
import { formatTimestamp } from "../../../lib/format";
import { h, ref, template } from "../../../lib/dom";
import type { PasskeyDto } from "../../../types";
import markup from "./passkey-card.html?raw";

interface PasskeyCardOptions {
  passkey: PasskeyDto;
  onDelete: () => void;
}

type InfoLine = [label: string, value: string];

const build = template(markup);

function fillInfo(block: HTMLElement, lines: (InfoLine | null)[]): void {
  const present = lines.filter((line): line is InfoLine => line !== null);
  if (present.length === 0) {
    block.remove();
    return;
  }
  block.append(...present.map(([label, value]) => h("span", {}, `${label} `, h("b", {}, value))));
}

export function passkeyCard({ passkey, onDelete }: PasskeyCardOptions): HTMLElement {
  const { lastUsedIp, lastUsedLocation, lastUsedAt, createdAt } = passkey;
  const root = build();

  ref(root, "name").textContent = passkey.deviceName ?? "Unknown passkey device";

  if (!passkey.currentSession) ref(root, "session").remove();

  fillInfo(ref(root, "desktop-info"), [
    lastUsedIp
      ? ["Last used from:", lastUsedLocation ? `${lastUsedIp} (${lastUsedLocation})` : lastUsedIp]
      : null,
    lastUsedAt ? ["Last time used:", formatTimestamp(lastUsedAt)] : null,
    createdAt ? ["Created at:", formatTimestamp(createdAt)] : null,
  ]);

  fillInfo(ref(root, "mobile-info"), [
    lastUsedIp ? ["Last used ip:", lastUsedIp] : null,
    lastUsedLocation ? ["Last used from:", lastUsedLocation] : null,
    lastUsedAt ? ["Last used at:", formatTimestamp(lastUsedAt)] : null,
    createdAt ? ["Created at:", formatTimestamp(createdAt)] : null,
  ]);

  iconButton(ref(root, "delete"), "delete", onDelete);

  return root;
}
