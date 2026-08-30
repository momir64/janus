import { h } from "../lib/dom";
import { icon } from "./icon";
import type { Passkey } from "../types";

interface PasskeyCardOptions {
  passkey: Passkey;
  onDelete: () => void;
}

type MetaLine = [label: string, value: string];

function metaBlock(modifier: string, lines: (MetaLine | null)[]): HTMLElement | null {
  const present = lines.filter((line): line is MetaLine => line !== null);
  if (present.length === 0) return null;
  return h(
    "div",
    { class: `passkey-card__meta passkey-card__meta--${modifier}` },
    // Figma sets the label Light and only the value Regular (139:135).
    ...present.map(([label, value]) => h("span", {}, `${label} `, h("b", {}, value)))
  );
}

export function passkeyCard({ passkey, onDelete }: PasskeyCardOptions): HTMLElement {
  const { lastUsedIp, lastUsedLocation, lastUsedAt, createdAt } = passkey;

  // The two frames word this differently and break it over a different
  // number of lines: desktop (139:135) puts the address and city together
  // on one line over three, mobile (2030:17) splits them over four.
  const desktopMeta = metaBlock("desktop", [
    lastUsedIp
      ? ["Last used from:", lastUsedLocation ? `${lastUsedIp} (${lastUsedLocation})` : lastUsedIp]
      : null,
    lastUsedAt ? ["Last time used:", lastUsedAt] : null,
    createdAt ? ["Created at:", createdAt] : null,
  ]);

  const mobileMeta = metaBlock("mobile", [
    lastUsedIp ? ["Last used ip:", lastUsedIp] : null,
    lastUsedLocation ? ["Last used from:", lastUsedLocation] : null,
    lastUsedAt ? ["Last used at:", lastUsedAt] : null,
    createdAt ? ["Created at:", createdAt] : null,
  ]);

  return h(
    "div",
    { class: "passkey-card" },
    h(
      "div",
      { class: "passkey-card__head" },
      h("span", { class: "passkey-card__name" }, passkey.deviceName ?? `Passkey (${passkey.algorithm})`),
      // Desktop 147:178 says "used for this session"; mobile 2030:18 just "this session".
      passkey.currentSession
        ? h(
            "span",
            { class: "passkey-card__session" },
            h("span", { class: "u-desktop-only" }, "used for this session"),
            h("span", { class: "u-mobile-only" }, "this session")
          )
        : null
    ),
    desktopMeta,
    mobileMeta,
    h(
      "button",
      { class: "icon-btn passkey-card__delete", "aria-label": "Delete passkey", onclick: onDelete },
      icon("delete")
    )
  );
}
