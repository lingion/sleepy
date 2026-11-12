"""Validate host screenshot brackets against actual UIKit touch delivery."""
import argparse
import collections
import hashlib
import json
import math
import pathlib
import re
import shutil
import struct
import zlib


def safe_run(value):
    return re.sub(r"[^a-zA-Z0-9_.-]", "_", value)


def validate_png(image, expected_size):
    """Validate complete PNG chunks and filtered scanlines without rendering pixels."""
    if not image.startswith(b"\x89PNG\r\n\x1a\n"):
        raise ValueError("Missing PNG signature")
    position = 8
    header = None
    palette = None
    compressed = bytearray()
    saw_idat = closed_idat = saw_end = False
    while position < len(image):
        if len(image) - position < 12:
            raise ValueError("Truncated PNG chunk header or checksum")
        length = struct.unpack_from(">I", image, position)[0]
        kind = image[position + 4:position + 8]
        if length > 0x7fffffff or length > len(image) - position - 12:
            raise ValueError("Invalid or truncated PNG chunk length")
        if not all(65 <= value <= 90 or 97 <= value <= 122 for value in kind) or kind[2] & 32:
            raise ValueError("Invalid PNG chunk type")
        payload = image[position + 8:position + 8 + length]
        checksum = struct.unpack_from(">I", image, position + 8 + length)[0]
        if zlib.crc32(kind + payload) & 0xffffffff != checksum:
            raise ValueError("PNG checksum mismatch in " + kind.decode("ascii"))
        position += length + 12
        if header is None and kind != b"IHDR":
            raise ValueError("IHDR must be the first PNG chunk")
        if saw_idat and kind != b"IDAT":
            closed_idat = True
        if kind == b"IHDR":
            if header is not None or length != 13:
                raise ValueError("Duplicate or malformed PNG IHDR")
            header = struct.unpack(">IIBBBBB", payload)
            width, height, depth, color, compression, filtering, interlace = header
            depths = {0: (1, 2, 4, 8, 16), 2: (8, 16), 3: (1, 2, 4, 8),
                      4: (8, 16), 6: (8, 16)}
            if not (0 < width <= 0x7fffffff and 0 < height <= 0x7fffffff):
                raise ValueError("Invalid PNG dimensions")
            if (width, height) != expected_size:
                raise ValueError(f"PNG dimensions {(width, height)} differ from session screen {expected_size}")
            if color not in depths or depth not in depths[color]:
                raise ValueError("Invalid PNG color type or bit depth")
            if compression != 0 or filtering != 0 or interlace not in (0, 1):
                raise ValueError("Unsupported PNG compression, filtering, or interlace method")
        elif kind == b"PLTE":
            if palette is not None or saw_idat or color in (0, 4):
                raise ValueError("Invalid PNG palette placement")
            if not length or length % 3 or length > 768 or (color == 3 and length // 3 > 2 ** depth):
                raise ValueError("Invalid PNG palette size")
            palette = payload
        elif kind == b"IDAT":
            if closed_idat or (color == 3 and palette is None):
                raise ValueError("Invalid PNG image-data placement")
            saw_idat = True
            compressed.extend(payload)
        elif kind == b"IEND":
            if length or not saw_idat or position != len(image):
                raise ValueError("Malformed PNG end or trailing data")
            saw_end = True
            break
        elif not kind[0] & 32:
            raise ValueError("Unknown critical PNG chunk " + kind.decode("ascii"))
    if header is None or not saw_end:
        raise ValueError("PNG is missing IHDR, image data, or IEND")

    # Adam7 passes each have their own filtered rows. Packed sub-byte samples
    # and 16-bit samples still use one filter byte per encoded scanline.
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[color]
    passes = ((0, 0, 1, 1),) if interlace == 0 else (
        (0, 0, 8, 8), (4, 0, 8, 8), (0, 4, 4, 8), (2, 0, 4, 4),
        (0, 2, 2, 4), (1, 0, 2, 2), (0, 1, 1, 2))
    scanlines = []
    for x, y, dx, dy in passes:
        columns = max(0, (width - x + dx - 1) // dx)
        rows = max(0, (height - y + dy - 1) // dy)
        if columns and rows:
            scanlines.append((rows, (columns * channels * depth + 7) // 8))
    expected_bytes = sum(rows * (row_bytes + 1) for rows, row_bytes in scanlines)
    decoder = zlib.decompressobj()
    raw = decoder.decompress(compressed, expected_bytes + 1)
    if not decoder.eof or decoder.unused_data or decoder.unconsumed_tail or len(raw) != expected_bytes:
        raise ValueError("PNG zlib stream or decompressed scanline length is invalid")
    offset = 0
    for rows, row_bytes in scanlines:
        for _ in range(rows):
            if raw[offset] > 4:
                raise ValueError("Invalid PNG scanline filter")
            offset += row_bytes + 1
    return {"width": width, "height": height, "bitDepth": depth, "colorType": color,
            "interlace": interlace, "scanlineBytes": expected_bytes}


def read_session(source):
    metadata = json.loads((source / "session.json").read_text())
    rows = [json.loads(line) for line in (source / "trace.jsonl").read_text().splitlines()]
    touches = collections.defaultdict(list)
    for row in rows:
        if row.get("event") == "touch" and row.get("source") == "delivery":
            touches[row["touchID"]].append(row)
    intervals = []
    for touch_id, samples in touches.items():
        down = next((row for row in samples if row["phase"] == 0), None)
        end = next((row for row in reversed(samples) if row["phase"] in (3, 4)), None)
        if down and end:
            intervals.append((touch_id, down, end))
    configuration = next((row for row in rows if row.get("event") == "reference-configuration"), {})
    return metadata, intervals, configuration


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--reference", required=True, type=pathlib.Path)
    parser.add_argument("--captures", required=True, type=pathlib.Path)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    parser.add_argument("--since-unix-seconds", type=float, default=0)
    parser.add_argument("--report-name", default="held-verification.json")
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    observations, sessions, missing_runs = [], {}, set()
    for event in map(json.loads, args.captures.read_text().splitlines()):
        if event["requestedAtUnixSeconds"] < args.since_unix_seconds:
            continue
        run = safe_run(event["run"])
        source = args.reference / run
        destination = args.output / "traces" / run
        if not source.exists():
            source = destination
        if not source.exists():
            missing_runs.add(run)
            continue
        if run not in sessions:
            sessions[run] = read_session(source)
            if source != destination:
                shutil.copytree(source, destination, dirs_exist_ok=True)
        metadata, intervals, configuration = sessions[run]
        origin = metadata["originUnixSeconds"]
        begin = event["screenshotBeginUnixSeconds"] - origin
        end = event["screenshotEndUnixSeconds"] - origin
        point = event["point"]
        matching = [(touch_id, down, up) for touch_id, down, up in intervals
                    if down["deliveredT"] <= begin and end <= up["deliveredT"]
                    and math.hypot(down["x"] - point["x"], down["y"] - point["y"]) <= 1
                    and origin + down["deliveredT"] >= event["requestedAtUnixSeconds"]]
        path = pathlib.Path(event["path"]).resolve()
        # Observers may have recorded a path relative to their launch directory.
        # Keep copied evidence usable when verification runs from another checkout.
        if not path.is_file():
            sibling = args.captures.parent / pathlib.Path(event["path"]).name
            if sibling.is_file():
                path = sibling.resolve()
        image, png_validation, png_error = b"", None, ""
        try:
            image = path.read_bytes()
            expected_size = (round(metadata["screenWidthPt"] * metadata["screenScale"]),
                             round(metadata["screenHeightPt"] * metadata["screenScale"]))
            png_validation = validate_png(image, expected_size)
        except (OSError, ValueError, struct.error, zlib.error) as error:
            png_error = f"{type(error).__name__}: {error}"
        valid_png = png_validation is not None
        verified = len(matching) == 1 and event["exitCode"] == 0 and valid_png
        row = dict(event, captureBeginT=begin, captureEndT=end, verifiedHeld=verified,
                   configuration=configuration, validPNG=valid_png, pngValidation=png_validation,
                   pngValidationError=png_error, resolvedImagePath=str(path))
        if matching:
            touch_id, down, up = matching[0]
            row.update(touchID=touch_id, actualDownDeliveryT=down["deliveredT"],
                       actualEndDeliveryT=up["deliveredT"], actualEndPhase=up["phase"],
                       actualDownPointPt=[down["x"], down["y"]],
                       captureAfterDownMs=(begin - down["deliveredT"]) * 1000,
                       captureBeforeEndMs=(up["deliveredT"] - end) * 1000)
        row["imageSHA256"] = hashlib.sha256(image).hexdigest() if image else None
        observations.append(row)
    report = {
        "captures": len(observations),
        "verifiedHeld": sum(row["verifiedHeld"] for row in observations),
        "unverified": [row["name"] for row in observations if not row["verifiedHeld"]],
        "missingRuns": sorted(missing_runs),
        "runs": len(sessions),
        "sinceUnixSeconds": args.since_unix_seconds,
        "observations": observations,
        "limits": "Capture timestamps bracket the simctl screenshot subprocess, not individual scanout. "
                  "Verified requires PNG chunk CRCs, a complete zlib stream, IHDR dimensions matching the session "
                  "screen, valid filtered scanline lengths, and the entire subprocess during a recorded delivered touch "
                  "at the requested point (within 1 pt) after its request signal; "
                  "no material matching threshold is inferred.",
    }
    (args.output / args.report_name).write_text(json.dumps(report, indent=2))
    print(json.dumps({key: value for key, value in report.items() if key != "observations"}, indent=2))


if __name__ == "__main__":
    main()
