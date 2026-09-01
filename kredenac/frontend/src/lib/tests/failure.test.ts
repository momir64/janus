import { describe, expect, it } from "vitest";
import { failure } from "../http/failure";
import { ApiError } from "../http/api";

describe("failure", () => {
  it("reports a cancelled or blocked ceremony by its DOMException name", () => {
    const { dom, status, offline } = failure(new DOMException("cancelled", "NotAllowedError"));
    expect(dom).toBe("NotAllowedError");
    expect(status).toBeUndefined();
    expect(offline).toBe(false);
  });

  it("reports the status and code the server sent", () => {
    const { dom, status, code, offline } = failure(new ApiError(401, "nope", "passkey_cloned"));
    expect(dom).toBeUndefined();
    expect(status).toBe(401);
    expect(code).toBe("passkey_cloned");
    expect(offline).toBe(false);
  });

  it("treats a failed fetch as offline, since that is what a TypeError means here", () => {
    expect(failure(new TypeError("Failed to fetch")).offline).toBe(true);
  });

  it("says nothing it cannot tell about anything else", () => {
    expect(failure(new Error("boom"))).toEqual({ offline: false });
    expect(failure("boom")).toEqual({ offline: false });
  });
});
