type DecodedImage = {
  source: CanvasImageSource;
  width: number;
  height: number;
  dispose: () => void;
};

async function decodeImage(file: File): Promise<DecodedImage> {
  // Some mobile browsers can render an image in <img> but reject it in
  // createImageBitmap. Keep a separate object URL for the fallback.
  if (typeof createImageBitmap === "function") {
    try {
      const bitmap = await createImageBitmap(file);
      return {
        source: bitmap, width: bitmap.width, height: bitmap.height,
        dispose: () => bitmap.close()
      };
    } catch {
      // Try the same decoder used by the receipt preview.
    }
  }

  const url = URL.createObjectURL(file);
  try {
    const image = new Image();
    await new Promise<void>((resolve, reject) => {
      image.onload = () => resolve();
      image.onerror = () => reject(new Error("Could not read this image. Try a JPG, PNG or WebP screenshot."));
      image.src = url;
    });
    return {
      source: image, width: image.naturalWidth, height: image.naturalHeight,
      dispose: () => URL.revokeObjectURL(url)
    };
  } catch (error) {
    URL.revokeObjectURL(url);
    throw error;
  }
}

/** Shrinks phone screenshots/photos to a JPEG under ~1600px so uploads are fast on mobile data. */
export async function compressImage(file: File, maxSide = 1600, quality = 0.82): Promise<Blob> {
  if (!/^image\/(jpeg|png|webp)$/i.test(file.type)) throw new Error("Choose a JPG, PNG or WebP image.");
  const image = await decodeImage(file);
  try {
    if (!image.width || !image.height) throw new Error("Could not read the image dimensions.");
    const scale = Math.min(1, maxSide / Math.max(image.width, image.height));
    const w = Math.max(1, Math.round(image.width * scale));
    const h = Math.max(1, Math.round(image.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Could not process the image.");
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, w, h);
    ctx.drawImage(image.source, 0, 0, w, h);
    const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res, "image/jpeg", quality));
    if (!blob || !blob.size || blob.type !== "image/jpeg") throw new Error("Could not process the image.");
    return blob;
  } finally {
    image.dispose();
  }
}
