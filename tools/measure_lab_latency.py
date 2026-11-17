"""Measure offer-to-overlay display latency in the synthetic Android laboratory."""

import argparse
from itertools import groupby
import json
import math
from pathlib import Path
import statistics
import subprocess
import time
import uuid


LAB = "br.com.devoaceitar.lab/.LabActivity"
SERVICE = "br.com.devoaceitar/br.com.devoaceitar.LabOfferService"
SCENARIOS = ("delivery", "batch", "split", "good", "zone", "wait", "missing", "rating") * 2
WIDTH, HEIGHT = 108, 240
LEGACY_OFFER_COLOR = (39, 61, 94)
OFFER_SURFACE = (255, 255, 255)
ACCEPT_COLORS = ((0, 108, 57), (46, 137, 99))
OVERLAY_COLORS = ((8, 76, 48), (255, 212, 118), (139, 30, 43), (22, 68, 55))


def adb(device, *args):
    return subprocess.check_output(["adb", "-s", device, *args], timeout=30).decode().strip()


def capture(device, destination):
    enabled = adb(device, "shell", "settings", "get", "secure", "enabled_accessibility_services")
    if SERVICE not in enabled.split(":"):
        raise RuntimeError("Ative o modo laboratório em Acessibilidade antes de medir.")
    adb(device, "shell", "am", "force-stop", "br.com.devoaceitar.lab")
    adb(device, "shell", "am", "start", "-W", "-n", LAB)
    remote = f"/sdcard/devoaceitar-lab-{uuid.uuid4().hex}.mp4"
    recorder = subprocess.Popen(
        ["adb", "-s", device, "shell", "screenrecord", "--time-limit", "40", remote],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    try:
        time.sleep(1.5)
        for scenario in SCENARIOS:
            if recorder.poll() is not None:
                raise RuntimeError("A gravação terminou antes da última oferta.")
            adb(device, "shell", "am", "start", "-W", "-n", LAB, "--es", "scenario", scenario,
                "--ei", "ttl_ms", "1100")
            time.sleep(1.55)
        _, error = recorder.communicate(timeout=35)
        if recorder.returncode:
            raise RuntimeError(error.decode(errors="replace"))
        adb(device, "pull", remote, str(destination))
    finally:
        if recorder.poll() is None:
            recorder.communicate(timeout=38)
        adb(device, "shell", "rm", "-f", remote)


def band(frame, colors, *, start=HEIGHT // 3, tolerance=16, minimum=5,
         columns=(17, 21, 87, 91), contiguous=False):
    rows = []
    for y in range(start, HEIGHT):
        count = 0
        for x in columns:
            offset = 3 * (y * WIDTH + x)
            if any(all(abs(frame[offset + index] - value) <= tolerance
                       for index, value in enumerate(color)) for color in colors):
                count += 1
        if count >= 2:
            rows.append(y)
    if contiguous:
        runs = ([row for _, row in run]
                for _, run in groupby(enumerate(rows), lambda entry: entry[1] - entry[0]))
        rows = max(runs, key=len, default=[])
    return (rows[0], rows[-1]) if len(rows) >= minimum else None


def offer_band(frame):
    # The white card is identified only when its full-width green button is visible.
    if band(frame, ACCEPT_COLORS, start=HEIGHT * 3 // 4, tolerance=12):
        card = band(frame, (OFFER_SURFACE,), tolerance=8, minimum=23,
                    columns=(7, 8, WIDTH - 9, WIDTH - 8), contiguous=True)
        if card:
            return card
    return band(frame, (LEGACY_OFFER_COLOR,), start=HEIGHT // 2)


def analyze(video):
    info = subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
        "frame=best_effort_timestamp_time", "-of", "json", str(video),
    ])
    frames = json.loads(info)["frames"]
    pixels = subprocess.check_output([
        "ffmpeg", "-v", "fatal", "-i", str(video), "-vf", f"scale={WIDTH}:{HEIGHT}",
        "-fps_mode", "passthrough", "-pix_fmt", "rgb24", "-f", "rawvideo", "-",
    ])
    stride = WIDTH * HEIGHT * 3
    if len(pixels) != len(frames) * stride:
        raise RuntimeError("O número de quadros não corresponde aos tempos do vídeo.")

    offers = []
    previous_offer = False
    overlay_belongs_to_offer = False
    for index, frame in enumerate(frames):
        parts = pixels[index * stride:(index + 1) * stride]
        offer = offer_band(parts)
        overlay = band(parts, OVERLAY_COLORS)
        timestamp = float(frame["best_effort_timestamp_time"])
        if not overlay:
            overlay_belongs_to_offer = False
        if offer and not previous_offer:
            offers.append({"offer_s": timestamp, "overlay_s": None, "carried_over": overlay_belongs_to_offer})
        if offer and overlay and overlay[1] < offer[0] and offers[-1]["overlay_s"] is None:
            offers[-1]["overlay_s"] = timestamp
            overlay_belongs_to_offer = True
        previous_offer = offer is not None

    times = [
        round((entry["overlay_s"] - entry["offer_s"]) * 1000, 1)
        for entry in offers if entry["overlay_s"] is not None and not entry["carried_over"]
    ]
    return {
        "offered": len(offers),
        "matched": len(times),
        "episodes": offers,
        "latencies_ms": times,
        "median_ms": round(statistics.median(times), 1) if times else None,
        "p95_ms": sorted(times)[math.ceil(len(times) * 0.95) - 1] if times else None,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--device", help="Dispositivo exibido em adb devices")
    source.add_argument("--video", type=Path, help="Gravação existente do Laboratório")
    parser.add_argument("--output", type=Path, default=Path("output/lab"))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    video = args.video or args.output / "latencia-galaxy.mp4"
    if args.device:
        capture(args.device, video)
    result = analyze(video)
    if args.device:
        result["attempted"] = len(SCENARIOS)
    (args.output / (video.stem + ".json")).write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    expected = len(SCENARIOS) if args.device else result["offered"]
    return 0 if expected > 0 and result["offered"] == result["matched"] == expected else 1


if __name__ == "__main__":
    raise SystemExit(main())
