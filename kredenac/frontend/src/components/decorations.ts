import { svg } from "../lib/dom";

// Geometry of the "arrow" frame on the LOGIN / REGISTER buttons, measured
// from Figma desktop node 51:29 — 131 x 22, eight chevrons. The SVG is
// authored at that exact ratio and scaled per breakpoint via CSS height.
const TRAIL_WIDTH = 131;
const TRAIL_HEIGHT = 22;
const CHEVRON_COUNT = 8;
const CHEVRON_WIDTH = 11;
const STEP = (TRAIL_WIDTH - CHEVRON_WIDTH) / (CHEVRON_COUNT - 1);

// Hatch marks (Figma "Group 5", desktop 81:231 / mobile 153:991). Sixteen
// discrete strokes, each rising 22px over a 12.70px run. The x offsets are
// taken verbatim from the export — a repeating gradient can't be used here
// because it clips the first and last stroke at the box edges instead of
// drawing 16 whole ones.
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
    // Figma exports this group with preserveAspectRatio="none" and scales it
    // differently per breakpoint (152.4x22.5 desktop, 96.9x14.5 mobile).
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

// Corner hatching (the "lines" frames, e.g. 157:205 / 119:1202). Strokes run
// corner to corner of nested squares anchored at the box's corner, so each
// one begins on one edge and ends on the other.
//
// They sit at a uniform 4px pitch. Figma's layers read 4, 6, 10, 14... but
// all but the innermost also carry a 0.98px offset, which puts their real
// intercepts at 4, 8, 12, 16... — reading the sizes alone leaves the first
// gap half-width.
//
// The box decides how many fit — 26.98 on a mobile card, 30.98 on desktop,
// 16.94 on a dialog button — so this is redrawn per size rather than being a
// fixed viewBox the browser stretches, which is what compressed the pitch at
// every size but one.
const HATCH_PITCH = 4;

function hatchIntercepts(size: number): number[] {
  // The pitch divides the box rather than being laid off from the corner, so
  // the outermost stroke is the box's own diagonal — running corner to
  // corner — instead of overshooting and needing to be cut back, which left
  // it ending short of both edges. 4px is the nominal spacing; dividing
  // 30.975 into eight puts it within a tenth of that.
  const count = Math.round(size / HATCH_PITCH);
  if (count < 1) return [];

  const pitch = size / count;
  return Array.from({ length: count }, (_, i) => (i + 1) * pitch);
}

/**
 * Sizes the drawing to the box CSS gave it. The viewBox doubles as the memo:
 * while it matches the rendered width the strokes are already right, so a
 * resize pass over every hatch costs one measurement each.
 */
function drawHatch(root: SVGSVGElement): void {
  // The computed width, not the client rect: the rect carries any transform
  // on an ancestor, and the dialogs scale as they open, which would fix the
  // drawing to a size the box only had mid-animation.
  const size = Number.parseFloat(getComputedStyle(root).width);
  if (!Number.isFinite(size) || size <= 0) return;

  const drawn = root.viewBox.baseVal?.width ?? 0;
  if (Math.abs(size - drawn) < 0.01) return;

  // Matching the viewBox to the rendered size keeps the drawing at 1:1, so
  // the 4px pitch and the 1px stroke are those lengths on screen.
  root.setAttribute("viewBox", `0 0 ${size} ${size}`);
  root.replaceChildren(
    ...hatchIntercepts(size).map((c) => svg("line", { x1: 0, y1: c, x2: c, y2: 0 }))
  );
}

// One listener for every hatch on the page. ResizeObserver would be the
// natural fit but does not fire for <svg> elements, and the size only ever
// changes at a breakpoint, so a window resize is trigger enough.
let hatchResizeBound = false;

function bindHatchResize(): void {
  if (hatchResizeBound) return;
  hatchResizeBound = true;
  window.addEventListener("resize", () => {
    document.querySelectorAll<SVGSVGElement>(".corner-hatch").forEach(drawHatch);
  });
}

/** `corner` picks which square corner the triangle is anchored to. */
export function cornerHatch(corner: "tl" | "br" = "tl"): SVGSVGElement {
  const root = svg("svg", {
    class: `corner-hatch corner-hatch--${corner}`,
    "aria-hidden": "true",
  }) as SVGSVGElement;

  bindHatchResize();

  requestAnimationFrame(() => {
    drawHatch(root);
    // Watches the host rather than the svg: ResizeObserver does not fire for
    // <svg> elements, and the host is also what reports a box again after the
    // hatch is built inside a hidden tab column, where there was no width to
    // measure the first time.
    const host = root.parentElement;
    if (host) new ResizeObserver(() => drawHatch(root)).observe(host);
  });

  return root;
}

/**
 * The close "X" (Figma 136:1345), inline rather than an <img> so its strokes
 * can take `currentColor` and respond to hover.
 */
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

/**
 * The repeating chevron trail (">>>>>>>>") built as one inline SVG rather
 * than eight near-identical exported assets.
 */
export function chevronTrail(): SVGSVGElement {
  const root = svg("svg", {
    class: "chevron-trail",
    viewBox: `0 0 ${TRAIL_WIDTH} ${TRAIL_HEIGHT}`,
    preserveAspectRatio: "xMaxYMid meet",
    "aria-hidden": "true",
  }) as SVGSVGElement;

  // Right-to-left, so index 0 is the rightmost — the one that survives the
  // collapse animation and rotates into a corner bracket.
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
