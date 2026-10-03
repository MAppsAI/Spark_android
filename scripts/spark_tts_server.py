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


def sanitize_text(text: str) -> str:
    """Strip emoji/symbol glyphs so Kokoro doesn't speak their names."""
    # Emoji blocks: pictographs, emoticons/symbols, transport, supplemental,
    # flags, plus variation selectors, ZWJ and keycaps.
    text = re.sub(
        r"[\U0001F000-\U0001FAFF\U00002600-\U000027BF\U0001F1E6-\U0001F1FF"
        r"\U0000FE00-\U0000FE0F\U0000200D\U000020E3\U00002B00-\U00002BFF"
        r"\U00002190-\U000021FF\U00002B50\U00003030\U0000303D\U000000A9\U000000AE]",
        "", text,
    )
    # Kaomoji / ASCII faces: :( :(  xD >:(  etc only when alone-ish — keep it
    # simple: strip common standalone emoticon tokens.
    text = re.sub(r"(?<!\w)[:;=8][\-^]?[DdPpXxOo3)(]\w?(?!\w)", "", text)
    # Collapse leftover whitespace
    text = re.sub(r"[ \t]{2,}", " ", text)
    text = re.sub(r" ?\n ?", "\n", text)
    return text.strip()


def sentence_split(text: str):
    # Split into speakable sentences; keep short fragments with neighbours.
    # (Old version double-spoke the trailing fragment — appended it AND merged
    # a copy into the previous chunk. Rewritten to emit each fragment once.)
    parts = [p.strip() for p in re.split(r"(?<=[.!?;:])\s+", text.strip()) if p.strip()]
    out, buf = [], ""
    for p in parts:
        buf = (buf + " " + p).strip() if buf else p
        if len(buf.split()) >= 6:
            out.append(buf)
            buf = ""
    if buf:
        if out and len(buf.split()) < 6:
            out[-1] = out[-1] + " " + buf  # merge short tail into last chunk
        else:
            out.append(buf)
    return out or ([text.strip()] if text.strip() else [])


def _smooth(audio, rate):
    """Fade edges to zero (~3ms) so back-to-back chunks don't click."""
    n = max(1, int(rate * 0.003))
    x = np.asarray(audio, dtype=np.float32).copy()
    if x.size >= 2 * n:
        ramp = np.linspace(0.0, 1.0, n, dtype=np.float32)
        x[:n] *= ramp
        x[-n:] *= ramp[::-1]
    return x


def _silence(n_frames):
    return b"\x00" * (n_frames * 2)


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
                {"ready": ready, "sample_rate": sr, "speakers": sp},
                separators=(",", ":")).encode())
            return

        if url.path == "/tts":
            q = parse_qs(url.query)
            text = sanitize_text(q.get("text", [""])[0] or "")
            sid = int(q.get("sid", ["0"])[0])
            speed = float(q.get("speed", ["1.0"])[0])
            if not text:
                # nothing speakable (e.g. emoji-only) — return valid silent wav
                # instead of 400 so the app doesn't fall back to another engine.
                self._send(200, "audio/wav", wav_header(0, _TTS.sample_rate if _TTS else 24000))
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
                        chunk = pcm16(_smooth(audio.samples, rate))
                        if not chunk:
                            continue
                        if not first:
                            # brief inter-sentence gap so chunk seams never click
                            gap = _silence(int(rate * 0.05))
                            self.wfile.write(b"%X\r\n" % len(gap))
                            self.wfile.write(gap)
                            self.wfile.write(b"\r\n")
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
