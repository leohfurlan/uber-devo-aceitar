"""Measure offer-to-overlay display latency in the synthetic Android laboratory."""

import argparse
import json
import math
from pathlib import Path
import statistics
import subprocess
import time
import uuid


LAB = "br.com.devoaceitar.lab/.LabActivity"
SERVICE = "br.com.devoaceitar/br.com.devoaceitar.LabOfferService"
SCENARIOS = ("good", "zone", "wait", "missing", "rating") * 2
WIDTH, HEIGHT = 108, 240
OFFER_COLOR = (39, 61, 94)
OVERLAY_COLOR = (22, 68, 55)


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
        ["adb", "-s", device, "shell", "screenrecord", "--time-limit", "32", remote],
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


def band(frame, color):
    rows = []
    for y in range(HEIGHT // 2, HEIGHT):
        count = 0
        for x in (17, 21, 87, 91):
            offset = 3 * (y * WIDTH + x)
            if all(abs(frame[offset + index] - value) <= 16 for index, value in enumerate(color)):
                count += 1
        if count >= 2:
            rows.append(y)
    return (min(rows), max(rows)) if len(rows) >= 5 else None


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
    previous_overlay = False
    for index, frame in enumerate(frames):
        parts = pixels[index * stride:(index + 1) * stride]
        offer = band(parts, OFFER_COLOR)
        overlay = band(parts, OVERLAY_COLOR)
        timestamp = float(frame["best_effort_timestamp_time"])
        if offer and not previous_offer:
            offers.append({"offer_s": timestamp, "overlay_s": None, "carried_over": previous_overlay})
        if offer and overlay and overlay[1] < offer[0] and offers[-1]["overlay_s"] is None:
            offers[-1]["overlay_s"] = timestamp
        previous_offer = offer is not None
        previous_overlay = overlay is not None

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
    return 0 if result["offered"] == result["matched"] == len(SCENARIOS) else 1


if __name__ == "__main__":
    raise SystemExit(main())
