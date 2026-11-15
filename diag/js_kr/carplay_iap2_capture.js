/*
 * Passive CarPlay/iAP2 receive callback capture candidate for the JS head unit.
 *
 * Target evidence: preserved libiap2.so export
 *   _ZN7android25iAP2ClientTransportRecvCBEPvPhi
 * ARM Thumb address in that preserved file: +0x912c (symbol value +0x912d).
 * The callback receives (transport context, bytes, length) before packet parsing.
 * This is callback-level iAP2 stream data, not USB URB/token-level capture.
 *
 * The hook is inert until rpc.exports.startCapture(path) is called. It writes
 * a bounded queue to a new output file; stopCapture() detaches and closes it.
 * Use only after confirming the target process, loaded library identity, and
 * writable output path on the actual unit. No iAP2/CAN/ECU writes are made.
 */

'use strict';

const MODULE_NAME = 'libiap2.so';
const CALLBACK = '_ZN7android25iAP2ClientTransportRecvCBEPvPhi';
const READ_HOOK_MODULE = 'libc.so';
const EXPECTED_RVAS = [0x912c, 0x912d]; // ARM Thumb code or function-pointer bit.
const FILE_MAGIC = [0x43, 0x50, 0x52, 0x41, 0x57, 0x31, 0x0d, 0x0a]; // CPRAW1\r\n
const RECORD_MAGIC = [0x49, 0x32, 0x52, 0x58]; // I2RX
const FOOTER_MAGIC = [0x44, 0x52, 0x4f, 0x50]; // DROP
const MAX_RECORD_BYTES = 64 * 1024;
const MAX_QUEUE_BYTES = 4 * 1024 * 1024;
const MAX_CAPTURE_FILE_BYTES = 16 * 1024 * 1024;
const FOOTER_BYTES = 12;
const FLUSH_INTERVAL_MS = 100;
const MAX_CAPTURE_DURATION_MS = 45 * 60 * 1000;

let hooks = [];
let captureMode = 'callback';
let output = null;
let flushTimer = null;
let active = false;
let queue = [];
let queuedBytes = 0;
let sequence = 0;
let dropped = 0;
let written = 0;
let writeFailure = null;
let captureStartedAt = 0;
let stopReason = 'user_stop';

function putU32BE(a, p, v) {
  a[p] = (v >>> 24) & 255;
  a[p + 1] = (v >>> 16) & 255;
  a[p + 2] = (v >>> 8) & 255;
  a[p + 3] = v & 255;
}

function putU64BE(a, p, v) {
  const hi = Math.floor(v / 4294967296);
  const lo = v >>> 0;
  putU32BE(a, p, hi);
  putU32BE(a, p + 4, lo);
}

function encodeRecord(item) {
  const payloadLength = item.data.byteLength;
  const bytes = new Uint8Array(20 + payloadLength);
  bytes.set(item.source === 'read' ? [0x52, 0x44, 0x52, 0x58] : RECORD_MAGIC, 0); // RDRX / I2RX
  putU32BE(bytes, 4, item.sequence);
  putU64BE(bytes, 8, item.wallTimeMs);
  putU32BE(bytes, 16, payloadLength);
  bytes.set(new Uint8Array(item.data), 20);
  return bytes;
}

function writeBytes(bytes) {
  output.write(bytes.buffer);
  written += bytes.length;
}

function closeOutput() {
  if (!output) return;
  try {
    const footer = new Uint8Array(FOOTER_BYTES);
    footer.set(FOOTER_MAGIC, 0);
    putU32BE(footer, 4, dropped >>> 0);
    const stopCodes = { user_stop: 0, duration_limit: 1, file_limit: 2, write_failure: 3 };
    putU32BE(footer, 8, stopCodes[stopReason]);
    writeBytes(footer);
    output.flush();
    output.close();
  } catch (e) {
    writeFailure = writeFailure || String(e);
  }
  output = null;
}

function flushQueue() {
  if (!output || writeFailure) return;
  try {
    while (queue.length > 0) {
      const item = queue.shift();
      queuedBytes -= item.data.byteLength + 20;
      const record = encodeRecord(item);
      if (written + record.length + FOOTER_BYTES > MAX_CAPTURE_FILE_BYTES) {
        dropped += queue.length + 1;
        queue = [];
        queuedBytes = 0;
        active = false;
        stopReason = 'file_limit';
        detachHooks();
        if (flushTimer !== null) { clearInterval(flushTimer); flushTimer = null; }
        break;
      }
      writeBytes(record);
    }
    output.flush();
  } catch (e) {
    writeFailure = String(e);
    active = false;
    stopReason = 'write_failure';
    queue = [];
    queuedBytes = 0;
  }
  if (!active && output && stopReason === 'file_limit') closeOutput();
  if (!active && output && stopReason === 'write_failure') closeOutput();
}

function stopInternal(reason) {
  active = false;
  stopReason = reason || 'user_stop';
  detachHooks();
  if (flushTimer !== null) {
    clearInterval(flushTimer);
    flushTimer = null;
  }
  flushQueue();
  closeOutput();
  return {
    writtenBytes: written,
    recordsSeen: sequence,
    recordsDropped: dropped,
    writeFailure: writeFailure,
    stopReason: stopReason
  };
}

function detachHooks() {
  for (let i = 0; i < hooks.length; i++) {
    try { hooks[i].detach(); } catch (_) {}
  }
  hooks = [];
}

function enqueue(source, data) {
  const length = data.byteLength;
  if (length <= 0 || length > MAX_RECORD_BYTES) { dropped++; return; }
  const size = length + 20;
  sequence++;
  if (queuedBytes + size > MAX_QUEUE_BYTES) { dropped++; return; }
  queue.push({ sequence: sequence, wallTimeMs: Date.now(), source: source, data: data });
  queuedBytes += size;
}

rpc.exports = {
  startCapture(path, mode) {
    if (active || output) throw new Error('capture is already active');
    if (typeof path !== 'string' || path.length === 0 || path[0] !== '/')
      throw new Error('provide an absolute output path');
    if (path.split('/').indexOf('..') !== -1) throw new Error('parent path segments are not allowed');
    mode = mode || 'callback';
    if (['callback', 'read', 'both'].indexOf(mode) === -1) throw new Error('mode must be callback, read, or both');

    const module = Process.findModuleByName(MODULE_NAME);
    if (!module) throw new Error(MODULE_NAME + ' is not loaded in this process');
    const address = Module.findExportByName(MODULE_NAME, CALLBACK);
    if (!address) throw new Error('receive callback export is absent');
    const rva = address.sub(module.base).toUInt32();
    if (EXPECTED_RVAS.indexOf(rva) === -1)
      throw new Error('callback RVA mismatch: got 0x' + rva.toString(16));

    let readAddress = null;
    if (mode === 'read' || mode === 'both') {
      readAddress = Module.findExportByName(READ_HOOK_MODULE, 'read');
      if (!readAddress) throw new Error('libc read export is absent');
    }

    // Frida's File API opens only after all target checks pass.
    // C11 exclusive-create mode: fail instead of truncating a prior capture.
    output = new File(path, 'wbx');
    output.write(new Uint8Array(FILE_MAGIC).buffer);
    output.flush();
    sequence = 0;
    dropped = 0;
    written = FILE_MAGIC.length;
    writeFailure = null;
    stopReason = 'user_stop';
    queue = [];
    queuedBytes = 0;

    captureMode = mode;
    try {
      if (mode === 'callback' || mode === 'both') hooks.push(Interceptor.attach(address, {
        onEnter(args) {
          if (!active) return;
          try {
            const length = args[2].toInt32();
            if (length <= 0 || length > MAX_RECORD_BYTES) { dropped++; return; }
            enqueue('callback', args[1].readByteArray(length));
          } catch (e) { dropped++; }
        }
      }));
      if (mode === 'read' || mode === 'both') hooks.push(Interceptor.attach(readAddress, {
        onEnter(args) {
          this.capture = false;
          if (!active) return;
          const caller = this.returnAddress;
          if (caller.compare(module.base) < 0 || caller.compare(module.base.add(module.size)) >= 0) return;
          const requested = args[2].toUInt32();
          if (requested === 0 || requested > MAX_RECORD_BYTES) return;
          this.capture = true;
          this.buffer = args[1];
        },
        onLeave(result) {
          if (!active || !this.capture) return;
          try {
            const length = result.toInt32();
            if (length > 0 && length <= MAX_RECORD_BYTES) enqueue('read', this.buffer.readByteArray(length));
          } catch (e) { dropped++; }
        }
      }));
    } catch (e) { detachHooks(); output.close(); output = null; throw e; }
    captureStartedAt = Date.now();
    active = true;
    flushTimer = setInterval(function () {
      if (Date.now() - captureStartedAt >= MAX_CAPTURE_DURATION_MS) {
        stopInternal('duration_limit');
        return;
      }
      flushQueue();
    }, FLUSH_INTERVAL_MS);
    return {
      modulePath: module.path,
      moduleRva: '0x' + rva.toString(16),
      mode: captureMode,
      file: path,
      maxQueueBytes: MAX_QUEUE_BYTES,
      maxCaptureFileBytes: MAX_CAPTURE_FILE_BYTES,
      maxRecordBytes: MAX_RECORD_BYTES,
      maxDurationMs: MAX_CAPTURE_DURATION_MS
    };
  },

  stopCapture() {
    return stopInternal('user_stop');
  },

  captureStatus() {
    return {
      active: active,
      mode: captureMode,
      queuedBytes: queuedBytes,
      recordsSeen: sequence,
      recordsDropped: dropped,
      writtenBytes: written,
      writeFailure: writeFailure
    };
  }
};

Script.bindWeak(globalThis, function () {
  stopInternal();
});
