const imageSize = img => ({ w: img.naturalWidth || img.width, h: img.naturalHeight || img.height });

async function loadImage(file) {
  if (window.createImageBitmap) {
    try {
      return await createImageBitmap(file, { imageOrientation: "from-image" });
    } catch {
      return loadImageTag(file);
    }
  }
  return loadImageTag(file);
}

const loadImageTag = file => new Promise((resolve, reject) => {
  const url = URL.createObjectURL(file);
  const img = new Image();
  img.onload = () => {
    URL.revokeObjectURL(url);
    resolve(img);
  };
  img.onerror = () => {
    URL.revokeObjectURL(url);
    reject(new Error("image"));
  };
  img.src = url;
});

export async function compressImage(file, maxSide = 1600, quality = 0.8) {
  if (!file.type.startsWith("image/") || /gif|svg/.test(file.type) || file.size < 150 * 1024) return file;
  try {
    const img = await loadImage(file);
    const { w: iw, h: ih } = imageSize(img);
    const scale = Math.min(1, maxSide / Math.max(iw, ih));
    const w = Math.max(1, Math.round(iw * scale));
    const h = Math.max(1, Math.round(ih * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, w, h);
    ctx.drawImage(img, 0, 0, w, h);
    if (img.close) img.close();
    const blob = await new Promise(resolve => canvas.toBlob(resolve, "image/jpeg", quality));
    if (!blob || blob.size >= file.size) return file;
    const base = (file.name || "photo").replace(/\.[^.]+$/, "");
    return new File([blob], base + ".jpg", { type: "image/jpeg" });
  } catch {
    return file;
  }
}
