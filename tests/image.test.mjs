import test from "node:test";
import assert from "node:assert/strict";
import { compressImage } from "../src/lib/image.ts";

test("receipt decoder falls back to HTML image when bitmap decoding fails", async (t) => {
  let drawn = false;
  let revoked = 0;
  const original = { Image: globalThis.Image, document: globalThis.document, createImageBitmap: globalThis.createImageBitmap };
  const revoke = URL.revokeObjectURL;
  t.after(() => { Object.assign(globalThis, original); URL.revokeObjectURL = revoke; });
  globalThis.createImageBitmap = async () => { throw new DOMException("The source image could not be decoded.", "InvalidStateError"); };
  globalThis.Image = class {
    naturalWidth = 1080;
    naturalHeight = 2400;
    set src(value) { queueMicrotask(() => this.onload()); }
  };
  URL.revokeObjectURL = (url) => { revoked++; revoke(url); };
  const canvas = {
    width: 0, height: 0,
    getContext: () => ({ fillRect() {}, drawImage() { drawn = true; } }),
    toBlob(callback, type) { callback(new Blob(["jpeg"], { type })); }
  };
  globalThis.document = { createElement: () => canvas };
  const result = await compressImage(new File(["test"], "receipt.png", { type: "image/png" }));
  assert.equal(result.type, "image/jpeg");
  assert.equal(drawn, true);
  assert.equal(canvas.width, 720);
  assert.equal(canvas.height, 1600);
  assert.equal(revoked, 1);
});

test("bitmap resources are closed when canvas processing fails", async (t) => {
  const original = { document: globalThis.document, createImageBitmap: globalThis.createImageBitmap };
  t.after(() => Object.assign(globalThis, original));
  let closed = false;
  globalThis.createImageBitmap = async () => ({ width: 100, height: 200, close() { closed = true; } });
  globalThis.document = { createElement: () => ({ getContext: () => null }) };
  await assert.rejects(compressImage(new File(["test"], "receipt.jpg", { type: "image/jpeg" })), /Could not process/);
  assert.equal(closed, true);
});

test("unsupported file types are rejected", async () => {
  await assert.rejects(compressImage(new File(["test"], "receipt.pdf", { type: "application/pdf" })), /Choose a JPG/);
});
