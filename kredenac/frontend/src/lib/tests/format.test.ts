import { formatSize, formatTimestamp, truncateFilename } from "../strings/format";
import { describe, expect, it } from "vitest";

describe("formatSize", () => {
  it("keeps bytes whole and gives larger units two decimals", () => {
    expect(formatSize(0)).toBe("0 B");
    expect(formatSize(512)).toBe("512 B");
    expect(formatSize(1024)).toBe("1.00 KB");
    expect(formatSize(1536)).toBe("1.50 KB");
    expect(formatSize(50 * 1024 * 1024)).toBe("50.00 MB");
  });

  it("stops at the largest unit it knows", () => {
    expect(formatSize(1024 ** 4)).toBe("1.00 TB");
    expect(formatSize(1024 ** 5)).toBe("1024.00 TB");
  });
});

describe("formatTimestamp", () => {
  it("renders an iso timestamp in the design's format", () => {
    expect(formatTimestamp(new Date(2022, 11, 31, 12, 34).toISOString())).toBe("12:34 31.12.2022.");
  });

  it("pads single digits so the column stays aligned", () => {
    expect(formatTimestamp(new Date(2000, 2, 5, 9, 7).toISOString())).toBe("09:07 05.03.2000.");
  });

  it("passes through anything it cannot parse rather than showing NaN", () => {
    expect(formatTimestamp("not a date")).toBe("not a date");
  });
});

describe("truncateFilename", () => {
  it("leaves a short name alone", () => {
    expect(truncateFilename("photo.png", 24)).toBe("photo.png");
  });

  it("cuts with an ellipsis, and without one when asked", () => {
    const name = "a-very-long-holiday-photo-name.png";
    expect(truncateFilename(name, 10)).toBe("a-very-lo…");
    expect(truncateFilename(name, 10, false)).toBe("a-very-lon");
  });

  it("keeps at least one character before the ellipsis", () => {
    expect(truncateFilename("abcdef", 1)).toBe("a…");
  });
});
