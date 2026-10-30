import fs from "node:fs";

/**
 * Rewrite an MP4 so the `moov` atom precedes `mdat` ("faststart").
 *
 * Without faststart a browser must download the entire file before it can
 * decode a single frame — a 13.9 MB hero video therefore renders black for
 * seconds on a slow connection.
 *
 * Moving `moov` shifts every byte of `mdat`, so the absolute chunk offsets
 * stored in `stco` (32-bit) and `co64` (64-bit) must be patched by the same
 * delta.
 */

const path = process.argv[2];
const buf = fs.readFileSync(path);

const TYPE = (off) => buf.slice(off + 4, off + 8).toString("latin1");

/** Read the top-level box at `off`; returns {type, size, headerSize, start, end}. */
function boxAt(off) {
  let size = buf.readUInt32BE(off);
  const type = TYPE(off);
  let headerSize = 8;
  if (size === 1) {
    size = Number(buf.readBigUInt64BE(off + 8));
    headerSize = 16;
  } else if (size === 0) {
    size = buf.length - off;
  }
  return { type, size, headerSize, start: off, end: off + size };
}

const boxes = [];
let off = 0;
while (off < buf.length) {
  const b = boxAt(off);
  boxes.push(b);
  off = b.end;
}

const moov = boxes.find((b) => b.type === "moov");
const mdat = boxes.find((b) => b.type === "mdat");

if (!moov || !mdat) throw new Error("missing moov or mdat");
if (moov.start < mdat.start) {
  console.log("already faststart — nothing to do");
  process.exit(0);
}

// Delta applied to mdat bytes when moov is hoisted in front of it.
const delta = moov.size;

const out = Buffer.alloc(buf.length);
let w = 0;

// Copy everything up to (but excluding) mdat, except moov which we defer.
let cursor = 0;
for (const b of boxes) {
  if (b.type === "mdat") break;
  if (b.type === "moov") {
    cursor = b.end;
    continue;
  }
  buf.copy(out, w, b.start, b.end);
  w += b.size;
  cursor = b.end;
}

// Hoisted moov, with its internal chunk offsets patched.
const moovCopy = Buffer.from(buf.slice(moov.start, moov.end));
patchChunkOffsets(moovCopy, delta);

moovCopy.copy(out, w);
w += moovCopy.length;

// The media data itself, shifted by `delta`.
buf.copy(out, w, mdat.start, mdat.end);
w += mdat.size;

if (w !== buf.length) throw new Error(`size mismatch: wrote ${w}, expected ${buf.length}`);

/**
 * Walk the `moov` tree and add `delta` to every absolute file offset stored
 * in `stco` (32-bit) and `co64` (64-bit) chunk-offset tables.
 */
function patchChunkOffsets(moovBuf, delta) {
  const containerTypes = new Set(["moov", "trak", "mdia", "minf", "stbl", "edts", "udta"]);
  const walk = (start, end) => {
    let o = start;
    while (o + 8 <= end) {
      let size = moovBuf.readUInt32BE(o);
      const type = moovBuf.slice(o + 4, o + 8).toString("latin1");
      let headerSize = 8;
      if (size === 1) {
        size = Number(moovBuf.readBigUInt64BE(o + 8));
        headerSize = 16;
      } else if (size === 0) {
        size = end - o;
      }
      if (size < 8) break;

      if (type === "stco") {
        const count = moovBuf.readUInt32BE(o + headerSize + 4);
        const table = o + headerSize + 8;
        for (let i = 0; i < count; i++) {
          const p = table + i * 4;
          moovBuf.writeUInt32BE(moovBuf.readUInt32BE(p) + delta, p);
        }
      } else if (type === "co64") {
        const count = moovBuf.readUInt32BE(o + headerSize + 4);
        const table = o + headerSize + 8;
        for (let i = 0; i < count; i++) {
          const p = table + i * 8;
          moovBuf.writeBigUInt64BE(moovBuf.readBigUInt64BE(p) + BigInt(delta), p);
        }
      } else if (containerTypes.has(type)) {
        walk(o + headerSize, o + size);
      }

      o += size;
    }
  };
  walk(0, moovBuf.length);
}

fs.writeFileSync(path, out);
console.log(`faststart applied to ${path}`);
console.log(`  moov: ${moov.start} -> 0`);
console.log(`  patched chunk offsets by +${delta}`);
console.log(`  size: ${(buf.length / 1048576).toFixed(2)} MB`);
