import { svg } from "../../lib/render/dom";

const TRAIL_WIDTH = 131;
const TRAIL_HEIGHT = 22;
const CHEVRON_COUNT = 8;
const CHEVRON_WIDTH = 11;
const STEP = (TRAIL_WIDTH - CHEVRON_WIDTH) / (CHEVRON_COUNT - 1);

const HATCH_W = 152.438;
const HATCH_H = 22.5;
const HATCH_RUN = 12.7017;
const HATCH_X = [
  0.433013, 9.74759, 19.0622, 28.3768, 37.6913, 47.0059, 56.3205, 64.7883,
  74.1029, 83.4175, 92.7321, 102.047, 111.361, 120.676, 129.99, 139.303,
];

export function hatchMarks(): SVGSVGElement {
  const root = svg("svg", {
    class: "hatch-marks",
    viewBox: `0 0 ${HATCH_W} ${HATCH_H}`,
    preserveAspectRatio: "none",
    "aria-hidden": "true",
  }) as SVGSVGElement;

  for (const x of HATCH_X) {
    root.append(
      svg("line", {
        x1: x,
        y1: HATCH_H - 0.25,
        x2: x + HATCH_RUN,
        y2: 0.25,
        "vector-effect": "non-scaling-stroke",
      })
    );
  }

  return root;
}

const HATCH_PITCH = 4;

function hatchIntercepts(size: number): number[] {
  const count = Math.round(size / HATCH_PITCH);
  if (count < 1) return [];

  const pitch = size / count;
  return Array.from({ length: count }, (_, i) => (i + 1) * pitch);
}

function drawHatch(root: SVGSVGElement): void {
  const size = Number.parseFloat(getComputedStyle(root).width);
  if (!Number.isFinite(size) || size <= 0) return;

  const drawn = root.viewBox.baseVal?.width ?? 0;
  if (Math.abs(size - drawn) < 0.01) return;

  root.setAttribute("viewBox", `0 0 ${size} ${size}`);
  root.replaceChildren(
    ...hatchIntercepts(size).map((c) => svg("line", { x1: 0, y1: c, x2: c, y2: 0 }))
  );
}

let hatchResizeBound = false;

function bindHatchResize(): void {
  if (hatchResizeBound) return;
  hatchResizeBound = true;
  window.addEventListener("resize", () => {
    document.querySelectorAll<SVGSVGElement>(".corner-hatch").forEach(drawHatch);
  });
}

export function cornerHatch(corner: "tl" | "br" = "tl"): SVGSVGElement {
  const root = svg("svg", {
    class: `corner-hatch corner-hatch--${corner}`,
    "aria-hidden": "true",
  }) as SVGSVGElement;

  bindHatchResize();

  requestAnimationFrame(() => {
    drawHatch(root);
    const host = root.parentElement;
    if (host) new ResizeObserver(() => drawHatch(root)).observe(host);
  });

  return root;
}

export function closeGlyph(): SVGSVGElement {
  const root = svg("svg", {
    class: "close-glyph",
    viewBox: "0 0 24.6477 24.0417",
    "aria-hidden": "true",
  }) as SVGSVGElement;

  root.append(
    svg("line", { x1: 23.3345, y1: 23.3345, x2: 0.707107, y2: 0.707128 }),
    svg("line", { x1: 23.9406, y1: 0.707107, x2: 1.31315, y2: 23.3345 })
  );

  return root;
}

export function chevronTrail(): SVGSVGElement {
  const root = svg("svg", {
    class: "chevron-trail",
    viewBox: `0 0 ${TRAIL_WIDTH} ${TRAIL_HEIGHT}`,
    preserveAspectRatio: "xMaxYMid meet",
    "aria-hidden": "true",
  }) as SVGSVGElement;

  for (let i = 0; i < CHEVRON_COUNT; i++) {
    const tipX = TRAIL_WIDTH - i * STEP;
    root.append(
      svg("polyline", {
        points: `${tipX - CHEVRON_WIDTH},1 ${tipX},${TRAIL_HEIGHT / 2} ${tipX - CHEVRON_WIDTH},${TRAIL_HEIGHT - 1}`,
      })
    );
  }

  return root;
}
