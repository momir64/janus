export function optionalBreaks(el: HTMLElement): void {
  let lastWidth = -1;

  const measure = (): void => {
    const width = el.clientWidth;
    if (width === lastWidth) return;
    lastWidth = width;

    el.classList.remove("u-no-breaks");

    const breaks = [...el.querySelectorAll("br")].filter(
      (br) => getComputedStyle(br).display !== "none"
    ).length;

    const lineHeight = Number.parseFloat(getComputedStyle(el).lineHeight);
    if (!Number.isFinite(lineHeight) || lineHeight <= 0) return;
    const lines = Math.round(el.clientHeight / lineHeight);

    if (lines > breaks + 1) el.classList.add("u-no-breaks");
  };

  measure();
  new ResizeObserver(measure).observe(el);
}
