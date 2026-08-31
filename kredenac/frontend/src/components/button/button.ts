import { h } from "../../lib/dom";
import { chevronTrail, cornerHatch } from "../decorations/decorations";
import { icon, type IconName } from "../icon/icon";

interface ButtonOptions {
  label: string;
  onClick?: (e: MouseEvent) => void;
  variant?: "arrow" | "framed" | "dialog" | "corners" | "icon" | "nav";
  danger?: boolean;
  icon?: IconName;
  hatch?: "tl" | "br";
  type?: "button" | "submit";
  disabled?: boolean;
}

export function button(opts: ButtonOptions): HTMLButtonElement {
  const classes = ["btn", `btn--${opts.variant ?? "framed"}`];
  if (opts.danger) classes.push("btn--danger");

  const children: (Node | string)[] = [];
  if (opts.icon && opts.variant !== "icon") children.push(icon(opts.icon));

  const label = h("span", { class: "btn__label" }, opts.label);
  children.push(label);

  if (opts.icon && opts.variant === "icon") children.push(icon(opts.icon));

  const el = h(
    "button",
    {
      type: opts.type ?? "button",
      class: classes.join(" "),
      onclick: opts.onClick,
      disabled: opts.disabled,
    },
    ...children
  );

  if (opts.variant === "arrow") el.append(chevronTrail());
  if (opts.variant === "dialog") {
    el.append(cornerHatch(opts.hatch ?? "tl"));
  }

  return el;
}
