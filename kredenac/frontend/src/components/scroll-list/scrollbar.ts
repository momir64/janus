interface ScrollbarOptions {
  placement?: "outside" | "inside";
  inset?: number;
}

export function attachScrollbar(scroller: HTMLElement, options: ScrollbarOptions = {}): HTMLElement {
  const area = document.createElement("div");
  area.className = "scroll-list";
  if (options.placement === "inside") area.classList.add("scroll-list--inside");
  scroller.replaceWith(area);
  area.append(scroller);

  const bar = document.createElement("span");
  bar.className = "scroll-list__bar";
  const thumb = document.createElement("span");
  thumb.className = "scroll-list__thumb";
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
      area.classList.remove("scroll-list--scrollable");
      return;
    }
    area.classList.add("scroll-list--scrollable");

    const track = bar.clientHeight;
    if (track <= 0) return;

    const height = Math.max(24, (scroller.clientHeight / scroller.scrollHeight) * track);
    const travel = track - height;
    const progress = scroller.scrollTop / overflow;

    thumb.style.height = `${height}px`;
    thumb.style.transform = `translateY(${(progress * travel).toFixed(2)}px)`;
  };

  scroller.addEventListener("scroll", update, { passive: true });
  new ResizeObserver(update).observe(scroller);
  new MutationObserver(update).observe(scroller, { childList: true, subtree: true });

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
  requestAnimationFrame(update);

  return area;
}
