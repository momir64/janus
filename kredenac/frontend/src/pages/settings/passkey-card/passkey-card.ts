import { iconButton } from "../../../components/icon/icon";
import { formatTimestamp } from "../../../lib/format";
import { h, ref, template } from "../../../lib/dom";
import type { PasskeyDto } from "../../../types";
import markup from "./passkey-card.html?raw";

interface PasskeyCardOptions {
  passkey: PasskeyDto;
  onDelete: () => void;
}

type InfoLine = [label: string, value: string] | string;

const build = template(markup);

function fillInfo(block: HTMLElement, lines: (InfoLine | null)[]): void {
  const present = lines.filter((line): line is InfoLine => line !== null);
  if (present.length === 0) {
    block.remove();
    return;
  }
  block.append(
    ...present.map((line) =>
      typeof line === "string" ?
        h("span", { class: "passkey-card__unused" }, line) :
        h("span", {}, `${line[0]} `, h("b", {}, line[1]))
    )
  );
}

export function passkeyCard({ passkey, onDelete }: PasskeyCardOptions): HTMLElement {
  const { lastUsedIp, lastUsedLocation, lastUsedAt, createdAt } = passkey;
  const root = build();

  ref(root, "name").textContent = passkey.deviceName ?? "Unknown passkey device";
  if (!passkey.currentSession) ref(root, "session").remove();

  const neverUsed: InfoLine[] = ["This passkey hasn't yet been used."];

  fillInfo(ref(root, "desktop-info"), [
    ...(lastUsedAt ? [lastUsedIp ?
      (["Last used from:", lastUsedLocation ? `${lastUsedIp} (${lastUsedLocation})` : lastUsedIp] as InfoLine) : null,
      ["Last time used:", formatTimestamp(lastUsedAt)] as InfoLine,
    ] : neverUsed),
    createdAt ? ["Created at:", formatTimestamp(createdAt)] : null,
  ]);

  fillInfo(ref(root, "mobile-info"), [
    ...(lastUsedAt ? [lastUsedIp ? (["Last used ip:", lastUsedIp] as InfoLine) : null,
      lastUsedLocation ? (["Last used from:", lastUsedLocation] as InfoLine) : null,
      ["Last used at:", formatTimestamp(lastUsedAt)] as InfoLine,
    ] : neverUsed),
    createdAt ? ["Created at:", formatTimestamp(createdAt)] : null,
  ]);

  iconButton(ref(root, "delete"), "delete", onDelete);

  return root;
}
