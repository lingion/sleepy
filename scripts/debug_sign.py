"""官方 debug 重签：仅加载本脚本同目录的可信工具，不执行构建产物中的代码。"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import time
import zipfile

_spec = importlib.util.spec_from_file_location("debug_ci", Path(__file__).with_name("debug_ci.py"))
ci = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(ci)

REPOSITORY = "lingion/sleepy"
MAX_ARCHIVE = 512 * 1024 * 1024
MAX_APK = 256 * 1024 * 1024
IDENTITY_FIELDS = ("run_id", "run_number", "run_attempt", "label", "source_sha", "source_repository",
                   "requested_ref", "workflow_sha", "workflow_ref", "pr_head_sha", "main_sha",
                   "merge_sha", "base_repository")


def validate_run(run):
    active = "expected_plan" in run
    valid_status = (run["status"] == "in_progress" and run["conclusion"] is None) if active else (
        run["status"] == "completed" and run["conclusion"] == "success")
    if (run["repository"]["full_name"] != REPOSITORY or not valid_status
            or run["path"] != ".github/workflows/debug-apk.yml"
            or run["event"] not in ("pull_request", "pull_request_target", "push", "workflow_dispatch")):
        raise ValueError("Only successful official Debug APK Build runs can be signed")
    for field in ("id", "run_number", "run_attempt"):
        if type(run[field]) is not int or run[field] <= 0:
            raise ValueError(f"Invalid workflow run {field}")
    if not re.fullmatch(r"[0-9a-f]{40}", run["head_sha"]):
        raise ValueError("Invalid workflow head SHA")
    if run["event"] == "push" and run["head_branch"] != "main":
        raise ValueError("Only main push builds can be officially signed")
    return run


def extract_bundle(archive, destination):
    """拒绝路径穿越、符号链接、重复条目及脚本；绝不使用 extractall。"""
    destination = Path(destination)
    with zipfile.ZipFile(archive) as bundle:
        entries = bundle.infolist()
        names = [entry.filename for entry in entries]
        if len(entries) > 20 or len(names) != len(set(names)):
            raise ValueError("Unexpected or duplicate archive entries")
        if sum(entry.file_size for entry in entries) > 4 * MAX_APK:
            raise ValueError("Signing input is too large")
        for entry in entries:
            name = entry.filename
            mode = stat.S_IFMT(entry.external_attr >> 16)
            if entry.is_dir():
                if name not in {f"{abi}/" for abi in ci.ABIS} or mode not in (0, stat.S_IFDIR):
                    raise ValueError("Unexpected archive directory")
                continue
            parts = name.split("/")
            allowed = name == "summary-info.json" or (
                len(parts) == 2 and parts[0] in ci.ABIS and (
                    parts[1] in ("build-info.json", "sha256sums.txt")
                    or re.fullmatch(r"sleepy-debug-[A-Za-z0-9_.-]+\.apk", parts[1])))
            limit = MAX_APK if name.endswith(".apk") else 64 * 1024
            if not allowed or mode not in (0, stat.S_IFREG) or entry.file_size > limit:
                raise ValueError(f"Unexpected signing input: {name!r}")
        destination.mkdir(parents=True, exist_ok=False)
        for entry in entries:
            if not entry.is_dir():
                target = destination / entry.filename
                target.parent.mkdir(parents=True, exist_ok=True)
                with bundle.open(entry) as source, target.open("xb") as output:
                    shutil.copyfileobj(source, output)


def api(path):
    return json.loads(subprocess.check_output(["gh", "api", path], text=True))


def current_build(event, context):
    """仅从可信 runner 事件恢复本次计划；不使用产物内自报的 PR head。"""
    if (context["repository"] != REPOSITORY
            or context["workflow_ref"] != f"{REPOSITORY}/.github/workflows/debug-apk.yml@refs/heads/main"
            or context["event_name"] not in ("pull_request_target", "push", "workflow_dispatch")):
        raise ValueError("Official signing requires the main workflow context")
    run_id, attempt = int(context["run_id"]), int(context["run_attempt"])
    plan = ci.make_plan(event, context)
    # target 事件的 GITHUB_SHA 是基线提交，运行 API 的 head_sha 则是 PR head。
    head_sha = plan["pr_head_sha"] if context["event_name"] == "pull_request_target" else context["sha"]
    run = dict(api(f"repos/{REPOSITORY}/actions/runs/{run_id}"))
    if any(run[field] != value for field, value in (
            ("id", run_id), ("run_attempt", attempt), ("run_number", int(context["run_number"])),
            ("head_sha", head_sha), ("event", context["event_name"]))):
        raise ValueError("Current run identity differs from the trusted event")
    run["expected_plan"] = plan
    validate_run(run)
    jobs = json.loads(subprocess.check_output([
        "gh", "api", "--paginate", "--slurp",
        f"repos/{REPOSITORY}/actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100"], text=True))
    builds = [job for page in jobs for job in page["jobs"] if job["name"] == "assemble-debug"]
    if len(builds) != 1 or builds[0]["conclusion"] != "success":
        raise ValueError("This attempt has no successful assemble-debug job")
    return run


def artifacts(run_id):
    pages = json.loads(subprocess.check_output([
        "gh", "api", "--paginate", "--slurp", "-H", "Cache-Control: no-cache",
        f"repos/{REPOSITORY}/actions/runs/{run_id}/artifacts?per_page=100"], text=True, stderr=subprocess.PIPE))
    return [item for page in pages for item in page["artifacts"]]


def download_bundle(event, destination, context=None):
    if context is not None:
        run = current_build(event, context)
    else:
        # 兼容旧完成事件的离线验签工具；线上入口只走当前运行。
        expected = validate_run(event["workflow_run"])
        run = validate_run(api(f"repos/{REPOSITORY}/actions/runs/{expected['id']}"))
        if any(run[field] != expected[field] for field in ("id", "run_attempt", "head_sha", "event")):
            raise ValueError("Build run changed since the completion event; refusing stale artifacts")
    name = f"debug-signing-input-{run['id']}-{run['run_attempt']}"
    matches = [item for item in artifacts(run["id"]) if item["name"] == name]
    if len(matches) != 1:
        raise ValueError("Expected exactly one signing input for this build attempt")
    artifact = matches[0]
    if (artifact["expired"] or not 0 < artifact["size_in_bytes"] <= MAX_ARCHIVE
            or artifact["workflow_run"]["id"] != run["id"]
            or artifact["workflow_run"]["head_sha"] != run["head_sha"]
            or not re.fullmatch(r"sha256:[0-9a-f]{64}", artifact.get("digest", ""))):
        raise ValueError("Invalid artifact identity or digest")
    artifact_id = artifact["id"]
    if type(artifact_id) is not int or artifact_id <= 0:
        raise ValueError("Invalid artifact ID")
    archive = destination.with_suffix(".zip")
    with archive.open("xb") as output:
        subprocess.run(["gh", "api", f"repos/{REPOSITORY}/actions/artifacts/{artifact_id}/zip"],
                       stdout=output, check=True)
    if archive.stat().st_size > MAX_ARCHIVE or "sha256:" + ci.sha256(archive) != artifact["digest"]:
        raise ValueError("Downloaded artifact digest does not match GitHub")
    extract_bundle(archive, destination)
    return run


def resign_apk(source, destination, keystore):
    aligned = destination.with_suffix(".aligned.apk")
    subprocess.run([ci.find_build_tool("zipalign"), "-P", "16", "-f", "4", str(source), str(aligned)], check=True)
    subprocess.run([ci.find_build_tool("apksigner"), "sign", "--ks", str(keystore),
                    "--ks-key-alias", "androiddebugkey", "--ks-pass", "pass:android",
                    "--key-pass", "pass:android", "--v4-signing-enabled", "false",
                    "--out", str(destination), str(aligned)], check=True)
    aligned.unlink()
    subprocess.run([ci.find_build_tool("zipalign"), "-c", "-P", "16", "4", str(destination)], check=True)


def sign_bundle(source, destination, run, keystore, certificate, *, inspector=ci.inspect_apk, signer=resign_apk):
    validate_run(run)
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise ValueError("Invalid official signing certificate")
    source, destination = Path(source), Path(destination)
    if any(path.is_symlink() for path in source.rglob("*")):
        raise ValueError("Signing input cannot contain symlinks")
    info = json.loads((source / "summary-info.json").read_text())
    if "expected_plan" in run:
        expected = run["expected_plan"]
        for field in (*IDENTITY_FIELDS, "abi"):
            if field in expected and info.get(field) != expected[field]:
                raise ValueError(f"Input differs from trusted event plan: {field}")
    for field in IDENTITY_FIELDS:
        if field in info and (not isinstance(info[field], str) or ci.single_line(info[field]) != info[field]):
            raise ValueError(f"Invalid build identity: {field}")
    for field, expected in (("run_id", run["id"]), ("run_number", run["run_number"]),
                            ("run_attempt", run["run_attempt"])):
        if info[field] != str(expected):
            raise ValueError(f"Build identity does not match triggering run: {field}")
    if not re.fullmatch(r"[0-9a-f]{40}", info["source_sha"]):
        raise ValueError("Invalid source SHA")
    if run["event"] in ("pull_request", "pull_request_target"):
        head = run["expected_plan"]["pr_head_sha"] if run["event"] == "pull_request_target" else run["head_sha"]
        if (info.get("pr_head_sha") != head or info.get("merge_sha") != info["source_sha"]
                or not re.fullmatch(r"[0-9a-f]{40}", info.get("main_sha", ""))
                or info.get("base_repository") != REPOSITORY):
            raise ValueError("PR signing requires the event head merged into official main")
    elif run["event"] == "push" and info["source_sha"] != run["head_sha"]:
        raise ValueError("Push build source differs from event SHA")
    abis = ci.selected_abis(info["abi"])
    if {path.name for path in source.iterdir()} != {"summary-info.json", *abis}:
        raise ValueError("Signing input ABI inventory differs from build plan")
    inventory = {}
    for abi in abis:
        directory = source / abi
        metadata = json.loads((directory / "build-info.json").read_text())
        if any(metadata.get(field) != info.get(field) for field in IDENTITY_FIELDS) or metadata["abi"] != abi:
            raise ValueError("ABI build identity differs from bundle")
        filename = metadata["apk"]
        if not re.fullmatch(r"sleepy-debug-[A-Za-z0-9_.-]+\.apk", filename):
            raise ValueError("Unexpected input APK filename")
        if {path.name for path in directory.iterdir()} != {filename, "build-info.json", "sha256sums.txt"}:
            raise ValueError("Unexpected files in ABI directory")
        apk = directory / filename
        if ci.sha256(apk) != metadata["apk_sha256"]:
            raise ValueError("APK digest differs from build metadata")
        identity = inspector(apk)
        allowed = set(ci.ABIS[:-1]) | {"x86"} if abi == "universal" else {abi}
        if (not identity["debuggable"] or identity["application_id"] not in (
                "com.lingion.sleepy", "com.lingion.sleepy.debug")
                or not set(identity["native_abis"]).issubset(allowed)
                or any(identity[field] != metadata[field] for field in (
                    "certificate_sha256", "application_id", "version_name"))):
            raise ValueError("Input APK does not match Sleepy debug identity")
        inventory[abi] = (apk, identity)

    outputs = {}
    for abi, (apk, before) in inventory.items():
        stem = (f"sleepy-debug-official-{ci.safe_label(info['label'])}-{info['source_sha'][:12]}"
                f"-{abi}-r{run['run_number']}-a{run['run_attempt']}")
        directory = destination / abi
        directory.mkdir(parents=True, exist_ok=False)
        target = directory / f"{stem}.apk"
        signer(apk, target, keystore)
        after = inspector(target)
        if after["certificate_sha256"] != certificate or any(after[field] != before[field] for field in (
                "application_id", "version_name", "debuggable", "native_abis")):
            raise ValueError("Official APK certificate or identity verification failed")
        metadata = {field: info[field] for field in IDENTITY_FIELDS if field in info}
        metadata.update(after, abi=abi, signing="project", apk=target.name, apk_sha256=ci.sha256(target),
                        run_url=f"https://github.com/{REPOSITORY}/actions/runs/{run['id']}")
        (directory / "build-info.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")
        (directory / "sha256sums.txt").write_text("".join(
            f"{ci.sha256(directory / name)}  {name}\n" for name in (target.name, "build-info.json")))
        outputs[abi] = {"artifact": stem, "apk": target.name}
    # 全部签名和校验成功后才创建发布摘要，工作流才允许上传。
    (destination / "summary-info.json").write_text(json.dumps(dict(metadata, abi=info["abi"])))
    return outputs


def cleanup_artifacts(destination, context):
    """全部最终包已在本次运行可见后，才清除中间包及旧 attempt 包。"""
    if context["repository"] != REPOSITORY:
        raise ValueError("Unexpected cleanup repository")
    info = json.loads((destination / "summary-info.json").read_text())
    for field in ("run_id", "run_attempt"):
        if info[field] != context[field]:
            raise ValueError("Cleanup identity differs from current attempt")
    expected = {}
    for abi in ci.selected_abis(info["abi"]):
        metadata = json.loads((destination / abi / "build-info.json").read_text())
        name = Path(metadata["apk"]).stem
        if name in expected:
            raise ValueError("Duplicate official ABI metadata; retaining signing input")
        expected[name] = abi
    run_id = int(context["run_id"])
    delays = (1, 2, 4, 8)

    def diagnostic(check, status, **fields):
        # Artifact names and upstream error bodies may contain private or multiline text.
        print("Official artifact cleanup: " + json.dumps(dict(
            check=check, max_checks=len(delays) + 1, status=status,
            expected_abis=list(expected.values()), **fields)), file=sys.stderr)

    for check in range(1, len(delays) + 2):
        try:
            items = artifacts(run_id)
        except (subprocess.CalledProcessError, OSError, ValueError, KeyError, TypeError) as error:
            diagnostic(check, "api-error", error_type=type(error).__name__)
            raise ValueError("Artifact listing failed; retaining signing input") from None
        # Validate every entry before any DELETE, including later cleanup candidates.
        valid = isinstance(items, list) and all(
            isinstance(item, dict) and type(item.get("id")) is int and item["id"] > 0
            and isinstance(item.get("name"), str) and type(item.get("expired")) is bool
            and isinstance(item.get("workflow_run"), dict)
            and type(item["workflow_run"].get("id")) is int and item["workflow_run"]["id"] == run_id
            for item in items)
        if not valid or len({item["id"] for item in items}) != len(items):
            diagnostic(check, "integrity-error")
            raise ValueError("Invalid artifact inventory or run identity; retaining signing input")
        matched = [item for item in items if item["name"] in expected]
        observed = [{"id": item["id"], "name_sha256": hashlib.sha256(
            item["name"].encode("utf-8", errors="replace")).hexdigest(),
            "abi": expected.get(item["name"]), "expired": item["expired"]} for item in items
            if item["name"] in expected or item["name"].startswith(("sleepy-debug-official-", "debug-signing-input-"))]
        if len({item["name"] for item in matched}) != len(matched) or any(item["expired"] for item in matched):
            diagnostic(check, "integrity-error", observed=observed)
            raise ValueError("Duplicate or expired official ABI artifacts; retaining signing input")
        missing = [abi for name, abi in expected.items() if not any(item["name"] == name for item in matched)]
        diagnostic(check, "incomplete" if missing else "complete", missing_abis=missing, observed=observed)
        if not missing:
            break
        if check > len(delays):
            raise ValueError("Not all official ABI artifacts were published; retaining signing input")
        time.sleep(delays[check - 1])
    for item in items:
        if item["name"] not in expected and (
                item["name"].startswith(f"debug-signing-input-{context['run_id']}-")
                or item["name"].startswith("sleepy-debug-official-")):
            artifact_id = item["id"]
            if type(artifact_id) is not int or artifact_id <= 0:
                raise ValueError("Invalid cleanup artifact ID")
            subprocess.run(["gh", "api", "--method", "DELETE",
                            f"repos/{REPOSITORY}/actions/artifacts/{artifact_id}"], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("download", "sign", "summary", "cleanup"))
    parser.add_argument("--source", type=Path)
    parser.add_argument("--destination", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "download":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        run = download_bundle(event, args.destination, ci.context_from_env())
        args.destination.with_suffix(".run.json").write_text(json.dumps(run))
    elif args.command == "sign":
        keystore = Path(os.environ["SLEEPY_DEBUG_KEYSTORE"])
        certificate = hashlib.sha256(subprocess.check_output([
            "keytool", "-exportcert", "-keystore", str(keystore), "-storepass", "android",
            "-alias", "androiddebugkey"])).hexdigest()
        run = json.loads(args.source.with_suffix(".run.json").read_text())
        outputs = sign_bundle(args.source, args.destination, run, keystore, certificate)
        for abi, item in outputs.items():
            ci.write_outputs({f"artifact_{abi.replace('-', '_')}": item["artifact"]})
        info = json.loads((args.destination / "summary-info.json").read_text())
        ci.write_outputs({"abi": info["abi"], "certificate_sha256": certificate})
    elif args.command == "cleanup":
        cleanup_artifacts(args.destination, ci.context_from_env())
    else:
        info_path = args.destination / "summary-info.json"
        info = json.loads(info_path.read_text()) if info_path.is_file() else {}
        uploads = {abi: {"outcome": os.environ.get(f"UPLOAD_{abi.replace('-', '_').upper()}_OUTCOME", ""),
                         "url": os.environ.get(f"UPLOAD_{abi.replace('-', '_').upper()}_URL", "")}
                   for abi in ci.ABIS}
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
            summary.write("## Official debug signing\n\n")
            summary.write(f"Build run: {ci.markdown(info.get('run_url', 'unavailable'))}\n\n")
            summary.write(f"Certificate SHA-256: {ci.markdown(info.get('certificate_sha256', 'unavailable'))}\n\n")
            summary.write(ci.render_summary(info, uploads, os.environ.get("JOB_STATUS", "unknown")))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        print(f"Official debug signing failed: {error}", file=sys.stderr)
        sys.exit(1)
