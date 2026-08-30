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
