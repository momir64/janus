const UNITS = ["B", "KB", "MB", "GB", "TB"] as const;

export function formatSize(bytes: number): string {
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < UNITS.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${unit === 0 ? value : value.toFixed(2)} ${UNITS[unit]}`;
}

export function formatTimestamp(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;

  const pad = (part: number): string => String(part).padStart(2, "0");
  return (
    `${pad(date.getHours())}:${pad(date.getMinutes())} ` +
    `${pad(date.getDate())}.${pad(date.getMonth() + 1)}.${date.getFullYear()}.`
  );
}

export function truncateFilename(name: string, max: number, ellipsis = true): string {
  if (name.length <= max) return name;
  return ellipsis ? `${name.slice(0, Math.max(1, max - 1))}\u2026` : name.slice(0, max);
}
