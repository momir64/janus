export function closeCutEdge(list: HTMLElement, items: HTMLElement = list): () => void {
  const update = (): void => {
    const top = list.getBoundingClientRect().top;
    let cut: HTMLElement | null = null;

    for (const child of Array.from(items.children)) {
      const box = child.getBoundingClientRect();
      if (box.top < top - 0.5 && box.bottom > top + 0.5) {
        cut = child as HTMLElement;
        break;
      }
    }

    if (cut) {
      const style = getComputedStyle(cut);
      list.style.setProperty("--cut-width", style.borderTopWidth);
      list.style.setProperty("--cut-color", style.borderTopColor);
    }
    list.classList.toggle("is-cut", cut !== null);
  };

  list.addEventListener("scroll", update, { passive: true });
  new ResizeObserver(update).observe(list);
  new MutationObserver(update).observe(items, { childList: true });

  update();
  return update;
}
