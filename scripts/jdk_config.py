"""Keep Gradle's bootstrap JDK URL consistent with .tool-versions."""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DAEMON_CONFIG = Path("gradle/gradle-daemon-jvm.properties")


def java_version(tool_versions: str) -> str:
    entries = [line.split("#", 1)[0].split() for line in tool_versions.splitlines()]
    java = [entry for entry in entries if entry and entry[0] == "java"]
    if len(java) != 1 or len(java[0]) != 2:
        raise ValueError(".tool-versions must contain exactly one Java version")
    match = re.fullmatch(r"temurin-(25\.\d+\.\d+(?:\.\d+)?\+\d+)", java[0][1])
    if not match:
        raise ValueError("Use a complete Temurin 25 GA version, e.g. java temurin-25.0.5+8")
    return match[1]


def daemon_config(version: str) -> str:
    return (
        "# Derived from .tool-versions; regenerate with python scripts/jdk_config.py.\n"
        "# Renovate updates both files in one PR; CI checks they remain consistent.\n"
        "# Older trusted PR workflows can bootstrap JDK 25 inside the isolated container.\n"
        "toolchainUrl.LINUX.X86_64=https\\://api.adoptium.net/v3/binary/version/"
        f"jdk-{version}/linux/x64/jdk/hotspot/normal/eclipse\n"
        "toolchainVersion=25\n"
    )


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="fail on drift without writing files")
    parser.add_argument("--root", type=Path, default=ROOT, help=argparse.SUPPRESS)
    args = parser.parse_args(argv)
    try:
        version = java_version((args.root / ".tool-versions").read_text(encoding="utf-8"))
        expected = daemon_config(version)
        target = args.root / DAEMON_CONFIG
        if args.check:
            actual = target.read_text(encoding="utf-8") if target.exists() else ""
            if actual != expected:
                print("JDK configuration drift: run python scripts/jdk_config.py and commit the result.", file=sys.stderr)
                return 1
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(expected, encoding="utf-8", newline="\n")
        print(f"Temurin {version}: Gradle bootstrap configuration {'verified' if args.check else 'updated'}")
        return 0
    except (OSError, ValueError) as error:
        print(f"JDK configuration error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
