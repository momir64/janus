/**
 * Figma renders file sizes as "123.23 MB" (2032:44 / 2035:114) and
 * timestamps as "12:34 31.12.2022." (2032:45 / 2035:115).
 */

const UNITS = ["B", "KB", "MB", "GB", "TB"] as const;

export function formatSize(bytes: number): string {
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit += 1;
  }
  // Whole bytes have no fraction to show; everything else takes the design's
  // two decimals.
  return `${unit === 0 ? value : value.toFixed(2)} ${UNITS[unit]}`;
}

export function formatTimestamp(value: string): string {
  const date = new Date(value);
  // Anything the Date constructor cannot read is passed through untouched,
  // which covers a backend that already sends a formatted string.
  if (Number.isNaN(date.getTime())) return value;

  const pad = (part: number): string => String(part).padStart(2, "0");
  return (
    `${pad(date.getHours())}:${pad(date.getMinutes())} ` +
    `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}.`
  );
}

/**
 * Shortens a filename to `max` characters for use inside a sentence. The
 * extension is not preserved: it is part of the name and cut with the rest.
 *
 * `ellipsis` marks the cut, and is dropped where the name is already
 * followed by dots of its own - the upload label's, which would otherwise
 * read as one run of six.
 */
export function truncateFilename(name: string, max: number, ellipsis = true): string {
  if (name.length <= max) return name;
  return ellipsis ? `${name.slice(0, Math.max(1, max - 1))}\u2026` : name.slice(0, max);
}
