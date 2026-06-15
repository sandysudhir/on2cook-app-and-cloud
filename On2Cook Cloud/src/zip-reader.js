const textDecoder = new TextDecoder();

function readUint16(view, offset) {
  return view.getUint16(offset, true);
}

function readUint32(view, offset) {
  return view.getUint32(offset, true);
}

function bytesToBase64(bytes) {
  let binary = "";
  const chunkSize = 0x8000;
  for (let index = 0; index < bytes.length; index += chunkSize) {
    const slice = bytes.subarray(index, index + chunkSize);
    binary += String.fromCharCode(...slice);
  }
  return btoa(binary);
}

async function inflateRaw(bytes) {
  if (!("DecompressionStream" in window)) {
    throw new Error("This browser cannot import compressed ZIP files.");
  }
  const stream = new Response(bytes).body.pipeThrough(new DecompressionStream("deflate-raw"));
  const buffer = await new Response(stream).arrayBuffer();
  return new Uint8Array(buffer);
}

async function unpackEntry(buffer, entry) {
  const view = new DataView(buffer);
  if (readUint32(view, entry.localHeaderOffset) !== 0x04034b50) {
    throw new Error(`Invalid local header for ${entry.name}`);
  }
  const fileNameLength = readUint16(view, entry.localHeaderOffset + 26);
  const extraLength = readUint16(view, entry.localHeaderOffset + 28);
  const dataOffset = entry.localHeaderOffset + 30 + fileNameLength + extraLength;
  const raw = new Uint8Array(buffer.slice(dataOffset, dataOffset + entry.compressedSize));
  if (entry.compressionMethod === 0) {
    return raw;
  }
  if (entry.compressionMethod === 8) {
    return inflateRaw(raw);
  }
  throw new Error(`Unsupported ZIP compression method: ${entry.compressionMethod}`);
}

export async function readZipEntries(buffer) {
  const view = new DataView(buffer);
  let eocdOffset = -1;
  for (let offset = buffer.byteLength - 22; offset >= Math.max(0, buffer.byteLength - 66000); offset -= 1) {
    if (readUint32(view, offset) === 0x06054b50) {
      eocdOffset = offset;
      break;
    }
  }
  if (eocdOffset === -1) {
    throw new Error("ZIP end-of-central-directory not found.");
  }

  const centralDirectorySize = readUint32(view, eocdOffset + 12);
  const centralDirectoryOffset = readUint32(view, eocdOffset + 16);
  const entries = [];
  let cursor = centralDirectoryOffset;
  const end = centralDirectoryOffset + centralDirectorySize;

  while (cursor < end) {
    if (readUint32(view, cursor) !== 0x02014b50) {
      throw new Error("Invalid ZIP central directory entry.");
    }
    const compressionMethod = readUint16(view, cursor + 10);
    const compressedSize = readUint32(view, cursor + 20);
    const uncompressedSize = readUint32(view, cursor + 24);
    const fileNameLength = readUint16(view, cursor + 28);
    const extraLength = readUint16(view, cursor + 30);
    const commentLength = readUint16(view, cursor + 32);
    const localHeaderOffset = readUint32(view, cursor + 42);
    const fileNameBytes = new Uint8Array(buffer.slice(cursor + 46, cursor + 46 + fileNameLength));
    const name = textDecoder.decode(fileNameBytes);
    entries.push({
      name,
      compressionMethod,
      compressedSize,
      uncompressedSize,
      localHeaderOffset
    });
    cursor += 46 + fileNameLength + extraLength + commentLength;
  }

  const unpacked = [];
  for (const entry of entries) {
    unpacked.push({
      ...entry,
      bytes: await unpackEntry(buffer, entry)
    });
  }
  return unpacked;
}

function detectMimeType(fileName) {
  const lower = fileName.toLowerCase();
  if (lower.endsWith(".png")) return "image/png";
  if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
  if (lower.endsWith(".webp")) return "image/webp";
  if (lower.endsWith(".gif")) return "image/gif";
  return "application/octet-stream";
}

export async function importRecipeZipArrayBuffer(buffer, sourceName = "recipe.zip") {
  const entries = await readZipEntries(buffer);
  const jsonEntry = entries.find((entry) => /\.(json|txt)$/i.test(entry.name));
  if (!jsonEntry) {
    throw new Error(`No recipe JSON or TXT file found in ${sourceName}`);
  }
  const imageEntry = entries.find((entry) => /\.(png|jpe?g|webp|gif)$/i.test(entry.name));
  const recipeText = textDecoder.decode(jsonEntry.bytes);
  const recipeJson = JSON.parse(recipeText);
  let imageDataUrl = "";
  if (imageEntry) {
    imageDataUrl = `data:${detectMimeType(imageEntry.name)};base64,${bytesToBase64(imageEntry.bytes)}`;
  }
  return {
    recipeJson,
    recipeText,
    recipeTextEntryName: jsonEntry.name,
    imageDataUrl,
    sourceName,
    entries: entries.map((entry) => ({
      name: entry.name,
      size: entry.uncompressedSize
    }))
  };
}

export async function importRecipeZipFile(file) {
  const buffer = await file.arrayBuffer();
  return importRecipeZipArrayBuffer(buffer, file.name);
}

export async function importRecipeZipUrl(url) {
  const cleanUrl = String(url || "").trim();
  if (!cleanUrl || cleanUrl === "undefined" || cleanUrl === "null") {
    console.warn("[On2Cook] Skipping recipe ZIP fetch because URL is missing.", { url });
    throw new Error("Recipe ZIP URL is missing.");
  }
  const response = await fetch(cleanUrl);
  if (!response.ok) {
    throw new Error(`Unable to fetch ZIP from ${cleanUrl}`);
  }
  const buffer = await response.arrayBuffer();
  return importRecipeZipArrayBuffer(buffer, cleanUrl.split("/").pop() || "recipe.zip");
}
