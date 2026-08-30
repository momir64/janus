/**
 * Closes the open top of a card that a scrolling list has cut through.
 *
 * A list that scrolls inside itself clips whatever card straddles its top
 * edge, and that card's own top border goes with it — leaving a box with two
 * sides and no lid. This draws a rule across the container's top edge in its
 * place, at the same weight and colour as the border it stands in for, and
 * only while a card is actually cut: scrolled to the top, or stopped in the
 * gap between two cards, there is nothing to close.
 */
export function closeCutEdge(list: HTMLElement): () => void {
  const update = (): void => {
    const top = list.getBoundingClientRect().top;
    let cut: HTMLElement | null = null;

    for (const child of Array.from(list.children)) {
      const box = child.getBoundingClientRect();
      // Straddling the edge, rather than merely touching it — the half-pixel
      // keeps a card that ends exactly on the edge from counting.
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
    list.classList.toggle("app-page__list--cut", cut !== null);
  };

  list.addEventListener("scroll", update, { passive: true });
  // The list resizing, and the cards being replaced by a re-render, both
  // change which card meets the edge.
  new ResizeObserver(update).observe(list);
  new MutationObserver(update).observe(list, { childList: true });

  update();
  return update;
}
