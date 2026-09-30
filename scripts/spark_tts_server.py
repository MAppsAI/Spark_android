#!/usr/bin/env python3
"""
Spark TTS server — Kokoro neural voice over the LAN.

Runs sherpa-onnx Kokoro and streams WAV (PCM16 mono) back sentence-by-sentence
so playback starts while later sentences are still synthesizing.

Endpoints:
  GET /health                 -> {"ready":true,"sample_rate":24000,"speakers":54}
  GET /tts?text=...&sid=0&speed=1.0  -> audio/wav streamed in chunks

Usage:
  pip install sherpa-onnx
  python spark_tts_server.py --model-dir ./kokoro --port 8764

Model dir must contain: model.int8.onnx (or model.onnx), voices.bin,
tokens.txt, espeak-ng-data/  (contents of kokoro-int8-multi-lang-v1_0.tar.bz2)
"""
import argparse
import io
import json
import math
import re
import struct
import sys
import threading
import wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

import numpy as np
import sherpa_onnx

_lock = threading.Lock()
_TTS = None
_ARGS = None


def sentence_split(text: str):
    # Split into speakable sentences; keep short fragments with neighbors.
    parts = re.split(r"(?<=[.!?;:])\s+", text.strip())
    out, buf = [], ""
    for p in parts:
        buf = (buf + " " + p).strip() if buf else p.strip()
        if len(buf.split()) >= 6:
            out.append(buf)
            buf = ""
    if buf:
        (out.append(buf) if out else out.append(buf))
        if len(buf.split()) < 6 and len(out) > 0:
            out[-1] = out[-1] + " " + buf
            out.pop() if False else None
    return out or ([text.strip()] if text.strip() else [])


def wav_header(n_frames: int, rate: int) -> bytes:
    return struct.pack(
        "<4sI4s4sIHHIIHH4sI",
        b"RIFF", 36 + n_frames * 2, b"WAVE", b"fmt ", 16, 1, 1, rate,
        rate * 2, 2, 16, b"data", n_frames * 2,
    )


def pcm16(samples) -> bytes:
    x = np.clip(np.asarray(samples, dtype=np.float32), -1.0, 1.0)
    return (x * 32767.0).astype("<i2").tobytes()


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):  # noqa: A002
        sys.stderr.write("[%s] %s\n" % (self.address_string(), format % args))

    def _send(self, code, ctype, body: bytes):
        self.send_response(code)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/health":
            with _lock:
                ready = _TTS is not None
                sr = _TTS.sample_rate if ready else 0
                sp = _TTS.num_speakers if ready else 0
            self._send(200, "application/json", json.dumps(
                {"ready": ready, "sample_rate": sr, "speakers": sp}).encode())
            return

        if url.path == "/tts":
            q = parse_qs(url.query)
            text = (q.get("text", [""])[0] or "").strip()
            sid = int(q.get("sid", ["0"])[0])
            speed = float(q.get("speed", ["1.0"])[0])
            if not text:
                self._send(400, "text/plain", b"missing text")
                return
            with _lock:
                if _TTS is None:
                    self._send(503, "text/plain", b"model not loaded")
                    return
                rate = _TTS.sample_rate
                # Synthesize sentence-by-sentence and stream PCM16 chunks.
                self.send_response(200)
                self.send_header("Content-Type", "audio/wav")
                self.send_header("Transfer-Encoding", "chunked")
                self.end_headers()
                try:
                    first = True
                    for sent in sentence_split(text):
                        audio = _TTS.generate(sent, sid=sid, speed=speed)
                        chunk = pcm16(audio.samples)
                        if not chunk:
                            continue
                        if first:
                            # RIFF header rides with the first chunk; clients
                            # skip 44 bytes and stream the rest as raw PCM16.
                            chunk = wav_header(0, rate) + chunk
                            first = False
                        self.wfile.write(b"%X\r\n" % len(chunk))
                        self.wfile.write(chunk)
                        self.wfile.write(b"\r\n")
                        self.wfile.flush()
                    if first:
                        # nothing synthesized — still emit a valid (empty) wav
                        chunk = wav_header(0, rate)
                        self.wfile.write(b"%X\r\n" % len(chunk))
                        self.wfile.write(chunk)
                        self.wfile.write(b"\r\n")
                    self.wfile.write(b"0\r\n\r\n")
                    self.wfile.flush()
                except Exception as e:  # noqa: BLE001
                    self.close_connection = True
                    sys.stderr.write("tts error: %r\n" % e)
            return

        self._send(404, "text/plain", b"not found")


def main():
    global _TTS, _ARGS
    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", required=True)
    ap.add_argument("--port", type=int, default=8764)
    ap.add_argument("--threads", type=int, default=8)
    a = ap.parse_args()
    _ARGS = a

    import os
    d = os.path.abspath(a.model_dir)
    onnx = next(
        (os.path.join(d, n) for n in ("model.onnx", "model.int8.onnx")
         if os.path.exists(os.path.join(d, n))), None)
    if not onnx:
        sys.exit("model.onnx / model.int8.onnx not found in " + d)
    fsts = [os.path.join(d, f) for f in
            ("date-zh.fst", "number-zh.fst", "phone-zh.fst")
            if os.path.exists(os.path.join(d, f))]

    cfg = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                model=onnx,
                voices=os.path.join(d, "voices.bin"),
                tokens=os.path.join(d, "tokens.txt"),
                data_dir=os.path.join(d, "espeak-ng-data"),
                lang="en-us",
            ),
            num_threads=a.threads,
        ),
        rule_fsts=",".join(fsts) if fsts else None,
        max_num_sentences=1,
    )
    _TTS = sherpa_onnx.OfflineTts(cfg)
    # warm-up
    _TTS.generate("Hello, Spark voice server is ready.", sid=0, speed=1.0)
    srv = ThreadingHTTPServer(("0.0.0.0", a.port), Handler)
    print("Spark TTS server on :%d (sr=%d, speakers=%d)"
          % (a.port, _TTS.sample_rate, _TTS.num_speakers), flush=True)
    srv.serve_forever()


if __name__ == "__main__":
    main()
