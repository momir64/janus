interface ScrollbarOptions {
  /**
   * Where the bar sits. "outside" lays it in the margin beside the scroller,
   * which suits a column with a gutter next to it; "inside" tucks it against
   * the scroller's own right edge, for a scroller that already spans the
   * window and has no margin to lay it in.
   */
  placement?: "outside" | "inside";
  /**
   * How far the bar stops short of the scroller's bottom edge. Defaults to
   * the scroller's own bottom padding, which is the empty space held past
   * the last item — so the bar ends where the content does.
   */
  inset?: number;
}

/**
 * Draws a scrollbar for `scroller` beside it rather than inside it.
 *
 * The native one is no use here: Firefox exposes only its width and colour,
 * so sharp corners, a bottom inset and the absence of end arrows are all out
 * of reach, and the widths the two engines pick for `scrollbar-width: thin`
 * differ — which is what left the cards narrower in one of them. Hiding it
 * and drawing our own also keeps the scroller's content box at its full
 * width, so nothing shifts when a list becomes scrollable.
 *
 * `scroller` is wrapped in a positioned element; the bar is a sibling laid
 * alongside it. Returns that wrapper.
 */
export function attachScrollbar(scroller: HTMLElement, options: ScrollbarOptions = {}): HTMLElement {
  const area = document.createElement("div");
  area.className = "scroll-area";
  if (options.placement === "inside") area.classList.add("scroll-area--inside");
  scroller.replaceWith(area);
  area.append(scroller);

  const bar = document.createElement("span");
  bar.className = "scroll-area__bar";
  const thumb = document.createElement("span");
  thumb.className = "scroll-area__thumb";
  bar.append(thumb);
  area.append(bar);

  const insetFor = (): number => {
    if (options.inset !== undefined) return options.inset;
    const padding = Number.parseFloat(getComputedStyle(scroller).paddingBottom);
    return Number.isFinite(padding) ? padding : 0;
  };

  const update = (): void => {
    const inset = insetFor();
    bar.style.bottom = `${inset}px`;

    const overflow = scroller.scrollHeight - scroller.clientHeight;
    if (overflow <= 0.5) {
      area.classList.remove("scroll-area--scrollable");
      return;
    }
    area.classList.add("scroll-area--scrollable");

    // Measured from the bar rather than from the scroller, so a bar that CSS
    // has stretched past the scroller — the settings page runs its up to the
    // top of the window — fills the length it is actually given.
    const track = bar.clientHeight;
    if (track <= 0) return;

    // Proportional, but never so short it cannot be grabbed.
    const height = Math.max(24, (scroller.clientHeight / scroller.scrollHeight) * track);
    const travel = track - height;
    const progress = scroller.scrollTop / overflow;

    thumb.style.height = `${height}px`;
    thumb.style.transform = `translateY(${(progress * travel).toFixed(2)}px)`;
  };

  scroller.addEventListener("scroll", update, { passive: true });
  new ResizeObserver(update).observe(scroller);
  new MutationObserver(update).observe(scroller, { childList: true, subtree: true });

  // Dragging the thumb, and clicking the track to jump to a position.
  thumb.addEventListener("pointerdown", (event) => {
    event.preventDefault();
    thumb.setPointerCapture(event.pointerId);

    const startY = event.clientY;
    const startTop = scroller.scrollTop;
    const travel = bar.clientHeight - thumb.getBoundingClientRect().height;
    const overflow = scroller.scrollHeight - scroller.clientHeight;

    const move = (e: PointerEvent): void => {
      if (travel <= 0) return;
      scroller.scrollTop = startTop + ((e.clientY - startY) / travel) * overflow;
    };
    const stop = (): void => {
      thumb.removeEventListener("pointermove", move);
      thumb.removeEventListener("pointerup", stop);
      thumb.removeEventListener("pointercancel", stop);
    };
    thumb.addEventListener("pointermove", move);
    thumb.addEventListener("pointerup", stop);
    thumb.addEventListener("pointercancel", stop);
  });

  update();
  // Again once laid out: at construction the scroller is not in the document
  // yet and has nothing to measure.
  requestAnimationFrame(update);

  return area;
}
