import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, api } from "../http/api";

// Enough of XMLHttpRequest for the upload path: it records what was sent and lets
// the test drive progress, completion and failure by hand.
class FakeXhr {
  static last: FakeXhr;
  upload = { onprogress: null as ((e: ProgressEvent) => void) | null };
  withCredentials = false;
  headers: Record<string, string> = {};
  method = "";
  url = "";
  body: unknown = null;
  status = 0;
  statusText = "";
  response: string | null = null;
  onload: (() => void) | null = null;
  onerror: (() => void) | null = null;
  onabort: (() => void) | null = null;
  aborted = false;

  constructor() {
    FakeXhr.last = this;
  }
  open(method: string, url: string) {
    this.method = method;
    this.url = url;
  }
  setRequestHeader(name: string, value: string) {
    this.headers[name] = value;
  }
  send(body: unknown) {
    this.body = body;
  }
  abort() {
    this.aborted = true;
    this.onabort?.();
  }
  progress(loaded: number, total: number) {
    this.upload.onprogress?.({ lengthComputable: true, loaded, total } as ProgressEvent);
  }
  finish(status: number, response: string | null = null) {
    this.status = status;
    this.response = response;
    this.onload?.();
  }
}

const file = () => new File([new Uint8Array(10)], "holiday.png", { type: "image/png" });

beforeEach(() => vi.stubGlobal("XMLHttpRequest", FakeXhr));
afterEach(() => vi.unstubAllGlobals());

describe("upload", () => {
  it("declares the size before the file, which the server reads in order", async () => {
    const promise = api.files.upload(file());
    const form = FakeXhr.last.body as FormData;

    expect([...form.keys()]).toEqual(["size", "file"]);
    expect(form.get("size")).toBe("10");
    expect(FakeXhr.last.withCredentials).toBe(true);

    FakeXhr.last.finish(201);
    await promise;
  });

  it("reports progress but stops at 99 until the response arrives", async () => {
    const seen: number[] = [];
    const promise = api.files.upload(file(), undefined, (percent) => seen.push(percent));

    FakeXhr.last.progress(1, 100);
    FakeXhr.last.progress(50, 100);
    FakeXhr.last.progress(100, 100);

    expect(seen).toEqual([1, 50, 99]);

    FakeXhr.last.finish(201);
    await promise;
  });

  it("ignores progress it cannot measure", async () => {
    const seen: number[] = [];
    const promise = api.files.upload(file(), undefined, (percent) => seen.push(percent));

    FakeXhr.last.upload.onprogress?.({ lengthComputable: false, loaded: 5, total: 0 } as ProgressEvent);
    expect(seen).toEqual([]);

    FakeXhr.last.finish(201);
    await promise;
  });

  it("turns a refusal into the same ApiError the rest of the client throws", async () => {
    const promise = api.files.upload(file());
    FakeXhr.last.finish(413, JSON.stringify({ message: "File is larger than the 50 MB limit" }));

    const error = (await promise.catch((e: unknown) => e)) as ApiError;
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(413);
    expect(error.message).toBe("File is larger than the 50 MB limit");
  });

  it("aborts when the signal fires, and reports it as a cancellation", async () => {
    const controller = new AbortController();
    const promise = api.files.upload(file(), controller.signal);

    controller.abort();
    expect(FakeXhr.last.aborted).toBe(true);

    const error = (await promise.catch((e: unknown) => e)) as DOMException;
    expect(error).toBeInstanceOf(DOMException);
    expect(error.name).toBe("AbortError");
  });

  it("reports a dead connection as a network failure, which reads as offline", async () => {
    const promise = api.files.upload(file());
    FakeXhr.last.onerror?.();
    await expect(promise).rejects.toBeInstanceOf(TypeError);
  });
});
