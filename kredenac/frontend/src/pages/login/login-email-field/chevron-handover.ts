import { sleep } from "../../../lib/timing";

const CHEVRON_STAGGER_MS = 70;
const CORNER_SETTLE_MS = 340;

export async function collapseChevrons(trail: SVGSVGElement): Promise<void> {
  const chevrons = Array.from(trail.querySelectorAll("polyline"));
  const [surviving, ...rest] = chevrons;
  if (!surviving) return;

  const [slide, drop] = handoverOffset(trail, surviving);

  trail.style.transition = `transform ${CHEVRON_STAGGER_MS}ms linear`;

  for (let i = 0; i < rest.length; i++) {
    rest[rest.length - 1 - i].remove();
    const progress = (i + 1) / rest.length;
    const travelled = progress * progress;
    trail.style.transform =
      `translate(${(travelled * slide).toFixed(2)}px, ${(travelled * drop).toFixed(2)}px)`;
    await sleep(CHEVRON_STAGGER_MS);
  }

  surviving.classList.add("chevron-trail__corner");
  await sleep(CORNER_SETTLE_MS);
}

function handoverOffset(trail: SVGSVGElement, surviving: SVGPolylineElement): [number, number] {
  const field = trail.parentElement;
  const ctm = surviving.getScreenCTM();
  if (!field || !ctm) return [0, 0];

  const fieldRect = field.getBoundingClientRect();
  const fieldStyle = getComputedStyle(field);
  const mark = getComputedStyle(field, "::after");

  const inset = (side: "Right" | "Bottom"): number =>
    Number.parseFloat(fieldStyle[`border${side}Width`]) +
    Number.parseFloat(mark[side.toLowerCase() as "right" | "bottom"]) +
    Number.parseFloat(mark[`border${side}Width`]) / 2;

  const markRight = fieldRect.right - inset("Right");
  const markBottom = fieldRect.bottom - inset("Bottom");

  const points = Array.from(surviving.points);
  const tip = points.reduce((far, p) => (p.x > far.x ? p : far), points[0]);
  if (!tip) return [0, 0];

  const tipX = ctm.a * tip.x + ctm.c * tip.y + ctm.e;
  const tipY = ctm.b * tip.x + ctm.d * tip.y + ctm.f;

  const box = surviving.getBoundingClientRect();
  const cx = (box.left + box.right) / 2;
  const cy = (box.top + box.bottom) / 2;

  const dx = tipX - cx;
  const dy = tipY - cy;
  const restX = cx + (dx - dy) / Math.SQRT2;
  const restY = cy + (dx + dy) / Math.SQRT2;

  const slide = markRight - restX;
  const drop = markBottom - restY;
  return [Number.isFinite(slide) ? slide : 0, Number.isFinite(drop) ? drop : 0];
}
