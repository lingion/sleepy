"""Small, dependency-free planning and delivery helpers for debug-apk.yml.

Tooling is checked out separately from the source revision being built.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile

ABIS = ("arm64-v8a", "armeabi-v7a", "x86_64", "universal")


def selected_abis(abi):
    if abi not in ("all", *ABIS):
        raise ValueError(f"Unsupported ABI: {abi!r}; choose all or {', '.join(ABIS)}")
    return ABIS if abi == "all" else (abi,)


def single_line(value):
    value = str(value)
    if any(ord(char) < 32 or ord(char) == 127 for char in value):
        raise ValueError("Context and ref values must not contain control characters")
    return value


def safe_label(value):
    return re.sub(r"[^a-zA-Z0-9._-]+", "-", value).strip(".-")[:64] or "source"


def make_plan(event, context):
    kind = context["event_name"]
    repo = single_line(context["repository"])
    ref = single_line(context["ref"])
    inputs = event.get("inputs") or {}
    abi = (inputs.get("abi") or "all") if kind == "workflow_dispatch" else "all"
    selected_abis(abi)
    requested = single_line(inputs.get("ref") or "") if kind == "workflow_dispatch" else ""
    if requested.startswith("-"):
        raise ValueError("A source ref cannot begin with '-'")
    enabled = True
    source_repo = repo
    source_ref = context["sha"]
    expected_sha = context["sha"]
    if kind in ("pull_request", "pull_request_target"):
        pr = event["pull_request"]
        enabled = not pr.get("draft", False)
        source_repo = pr["head"]["repo"]["full_name"]
        source_ref = pr["head"]["sha"]
        expected_sha = source_ref
        requested = pr["head"]["ref"]
        label = f"pr{int(pr['number'])}"
        identity = [kind, repo, str(pr["number"])]
    elif kind == "push":
        enabled = ref.startswith("refs/heads/") and not event.get("deleted", False) and (
            event["repository"].get("fork", False) or ref == "refs/heads/main")
        requested = ref
        label = "push-" + safe_label(ref.removeprefix("refs/heads/"))
        identity = [kind, repo, ref]
    elif kind == "workflow_dispatch":
        effective_ref = requested or ref
        source_ref = requested or context["sha"]
        expected_sha = "" if requested else context["sha"]
        requested = effective_ref
        label = "manual-" + safe_label(effective_ref.removeprefix("refs/heads/"))
        identity = [kind, repo, effective_ref, abi]
    else:
        raise ValueError(f"Unsupported event: {kind}")
    plan = {
        "enabled": enabled, "source_repository": single_line(source_repo),
        "source_ref": single_line(source_ref), "expected_sha": single_line(expected_sha),
        "requested_ref": single_line(requested), "abi": abi, "label": label,
        "concurrency_key": hashlib.sha256(json.dumps(identity).encode()).hexdigest(),
        "workflow_sha": context["workflow_sha"], "workflow_ref": context["workflow_ref"],
        "run_id": str(context["run_id"]), "run_number": str(context["run_number"]),
        "run_attempt": str(context["run_attempt"]),
        "run_url": f"{context['server_url']}/{repo}/actions/runs/{context['run_id']}",
        "delivery": "signing-input", "official_signing": repo == "lingion/sleepy",
    }
    if kind in ("pull_request", "pull_request_target"):
        # pull_request 在目标仓库运行；不能使用 fork 的 origin/main。
        plan.update(pr_head_sha=source_ref, base_repository=repo,
                    base_url=f"{context['server_url']}/{repo}.git")
    for value in plan.values():
        single_line(value)
    return plan


def context_from_env():
    return {key: os.environ[f"GITHUB_{key.upper()}"] for key in (
        "event_name", "repository", "ref", "sha", "workflow_sha", "workflow_ref", "run_id",
        "run_number", "run_attempt", "server_url")}


def write_outputs(values):
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key, value in values.items():
            if isinstance(value, bool):
                value = str(value).lower()
            output.write(f"{key}={single_line(value)}\n")


def find_build_tool(name):
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if sdk:
        tools = list((Path(sdk) / "build-tools").glob(f"*/{name}"))
        tools.sort(key=lambda p: tuple(int(n) for n in re.findall(r"\d+", p.parent.name)), reverse=True)
        if tools:
            return str(tools[0])
    tool = shutil.which(name)
    if tool:
        return tool
    raise ValueError(f"Android SDK build tool {name} was not found")


def inspect_apk(path):
    badging = subprocess.check_output([find_build_tool("aapt"), "dump", "badging", str(path)], text=True)
    package = re.search(r"^package: name='([^']+)' .*?versionName='([^']*)'", badging, re.MULTILINE)
    if not package:
        raise ValueError(f"Cannot read APK package identity: {path.name}")
    certificates = subprocess.check_output([
        find_build_tool("apksigner"), "verify", "--print-certs", str(path)], text=True)
    fingerprints = {value.lower() for value in re.findall(
        r"certificate SHA-256 digest: ([0-9a-fA-F]{64})(?![0-9a-fA-F])", certificates)}
    if len(fingerprints) != 1:
        raise ValueError(f"Cannot verify APK signing certificate: {path.name}; public apksigner output: {certificates.strip()!r}")
    with zipfile.ZipFile(path) as apk:
        native_abis = sorted({name.split('/')[1] for name in apk.namelist()
                              if name.startswith('lib/') and name.endswith('.so') and len(name.split('/')) >= 3})
    return {"application_id": package[1], "version_name": package[2],
            "debuggable": "application-debuggable" in badging, "native_abis": native_abis,
            "certificate_sha256": fingerprints.pop()}


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def verify_revision(actual, recorded):
    if not re.fullmatch(r"[0-9a-f]{40}", recorded) or actual != recorded:
        raise ValueError("Source HEAD changed after the pre-build checkout snapshot")
    return actual


def prepare_source(source, plan):
    """固定事件 head，并在每次 PR 构建时重新取目标仓库 main。"""
    def git(*args):
        try:
            return subprocess.check_output(["git", "-C", str(source), *args], text=True).strip()
        except subprocess.CalledProcessError as error:
            # merge 的冲突文件列表写在 stdout，不能只留下退出码。
            print(error.output, file=sys.stderr)
            raise

    revision = git("rev-parse", "HEAD")
    if plan.get("expected_sha"):
        verify_revision(revision, plan["expected_sha"])
    prepared = dict(plan)
    if plan.get("pr_head_sha"):
        verify_revision(revision, plan["pr_head_sha"])
        # versionFromGit reads tags from this checkout. Replace the fork's tag
        # namespace with the target's, including pruning fork-only version tags.
        git("fetch", "--atomic", "--prune", "--no-tags", "--", plan["base_url"],
            "+refs/heads/main:refs/remotes/debug-base/main", "+refs/tags/*:refs/tags/*")
        main_sha = git("rev-parse", "refs/remotes/debug-base/main")
        git("checkout", "--detach", main_sha)
        # 仅在 runner 创建临时提交，不推送或修改 PR 分支。
        git("-c", "user.name=github-actions[bot]",
            "-c", "user.email=41898282+github-actions[bot]@users.noreply.github.com",
            "-c", "commit.gpgsign=false", "merge", "--no-ff", "--no-edit",
            "-m", "Merge PR head into latest main for debug APK", revision)
        revision = git("rev-parse", "HEAD")
        prepared.update(main_sha=main_sha, merge_sha=revision)
    prepared["source_sha"] = revision
    return prepared


def package_apks(source, destination, plan, source_sha, signing, inspector=inspect_apk, *, expected_certificate):
    if not re.fullmatch(r"[0-9a-f]{40}", source_sha):
        raise ValueError("Expected full source commit SHA")
    if plan.get("pr_head_sha"):
        # PR head 是合并输入，最终 APK 必须对应已记录的合并结果。
        for field in ("pr_head_sha", "main_sha", "merge_sha"):
            if not re.fullmatch(r"[0-9a-f]{40}", plan.get(field, "")):
                raise ValueError(f"Missing or invalid PR merge identity: {field}")
        verify_revision(source_sha, plan["merge_sha"])
    elif plan.get("expected_sha") and plan["expected_sha"] != source_sha:
        raise ValueError("Checked-out source does not match the event commit")
    if signing not in ("project", "ephemeral"):
        raise ValueError("Unknown signing mode")
    source = Path(source)
    destination = Path(destination)
    metadata_path = source / "output-metadata.json"
    if metadata_path.is_symlink():
        raise ValueError("APK metadata cannot be a symlink")
    metadata = json.loads(metadata_path.read_text())
    if metadata.get("variantName") != "debug":
        raise ValueError("Expected the debug variant")
    expected = selected_abis(plan["abi"])
    inventory = {}
    for element in metadata["elements"]:
        filters = element.get("filters", [])
        if any(item.get("filterType") != "ABI" for item in filters) or len(filters) > 1:
            raise ValueError("Unexpected APK filters")
        abi = filters[0]["value"] if filters else "universal"
        filename = element["outputFile"]
        if Path(filename).name != filename or not filename.endswith(".apk"):
            raise ValueError("APK output must be a plain .apk filename")
        apk = source / filename
        if apk.is_symlink() or not apk.is_file():
            raise ValueError(f"Missing APK or symlink output: {filename}")
        if abi in inventory or abi not in expected:
            raise ValueError(f"Unexpected or duplicate ABI: {abi}")
        inventory[abi] = apk
    if set(inventory) != set(expected) or set(source.glob("*.apk")) != set(inventory.values()):
        raise ValueError("APK inventory does not match selected ABIs")

    # Inspect everything before creating any downloadable staging directory.
    identities = {abi: inspector(apk) for abi, apk in inventory.items()}
    for abi, identity in identities.items():
        if not identity["debuggable"] or identity["application_id"] != metadata["applicationId"]:
            raise ValueError(f"APK is not debuggable or package metadata disagrees: {abi}")
        # AGP's all-mode universal split also retains legacy x86 libraries from dependencies.
        allowed_native_abis = set(ABIS[:-1]) | {"x86"} if abi == "universal" else {abi}
        if not re.fullmatch(r"[0-9a-f]{64}", expected_certificate) or identity["certificate_sha256"] != expected_certificate:
            raise ValueError(f"APK certificate does not match the selected debug keystore: {abi}")
        if not set(identity.get("native_abis", [])).issubset(allowed_native_abis):
            raise ValueError(f"APK native libraries disagree with selected ABI: {abi}")
    if len({item["certificate_sha256"] for item in identities.values()}) != 1:
        raise ValueError("Selected APKs do not share a signing identity")

    outputs = {}
    suffix = f"r{int(plan['run_number'])}-a{int(plan['run_attempt'])}"
    for abi in expected:
        stem = f"sleepy-debug-{safe_label(plan['label'])}-{source_sha[:12]}-{abi}-{suffix}"
        directory = destination / abi
        directory.mkdir(parents=True, exist_ok=False)
        apk_name = stem + ".apk"
        shutil.copyfile(inventory[abi], directory / apk_name)
        info = {**plan, **identities[abi], "source_sha": source_sha, "abi": abi,
                "signing": signing, "apk": apk_name, "apk_sha256": sha256(directory / apk_name)}
        (directory / "build-info.json").write_text(json.dumps(info, ensure_ascii=False, indent=2) + "\n")
        (directory / "sha256sums.txt").write_text("".join(
            f"{sha256(directory / name)}  {name}\n" for name in (apk_name, "build-info.json")))
        outputs[abi] = {"artifact": stem, "apk": apk_name}
    return outputs


def markdown(value):
    # Untrusted branch/package strings must not inject job-summary links or markup.
    import html
    return html.escape(str(value)).replace("`", "&#96;").replace("|", "&#124;")


def render_summary(info, uploads, status):
    lines = ["## Debug APK build", "", f"Job status: {markdown(status)}", "",
             f"Source repository: {markdown(info.get('source_repository', 'unavailable'))}",
             f"Source ref: {markdown(info.get('requested_ref', 'unavailable'))}",
             f"Source SHA: {markdown(info.get('source_sha', 'unavailable'))}",
             f"Workflow revision: {markdown(info.get('workflow_sha', 'unavailable'))}",
             f"Application ID: {markdown(info.get('application_id', 'not verified'))}",
             f"Signing: {markdown(info.get('signing', 'not verified'))}", "",
             ("Signing input expires after 1 day; it is not an official install package."
              if info.get("delivery") == "signing-input" else
              "Artifacts expire after 7 days. Sign in to GitHub to download the ZIP, then extract the APK."),
             "APK build success is independent of the PR test and lint results.", ""]
    if info.get("pr_head_sha"):
        lines += [f"PR head SHA: {markdown(info['pr_head_sha'])}",
                  f"Main repository: {markdown(info.get('base_repository', 'unavailable'))}",
                  f"Main SHA (fetched at build time): {markdown(info.get('main_sha', 'unavailable'))}",
                  f"Merge SHA: {markdown(info.get('merge_sha', 'unavailable'))}", ""]
    if info.get("delivery") == "signing-input":
        if info.get("official_signing"):
            lines += ["The sign-debug job publishes official APKs to this run's Artifacts section. "
                      "Wait for signing to finish; each ABI has its own download. "
                      "The signing input is removed after successful publication."]
        else:
            lines += ["Fork builds are build-only. Open a PR to lingion/sleepy for official signed APKs."]
        return "\n".join(lines) + "\n"
    if info.get("signing") == "ephemeral":
        lines += ["Ephemeral signing: packages from different runs may not support replacement installation.", ""]
    if info.get("application_id") and not info["application_id"].endswith(".debug"):
        lines += ["Warning: this historical target does not use the .debug package ID; it cannot coexist with the stable app using the same ID.", ""]
    lines += ["| ABI | Upload status | Download |", "| --- | --- | --- |"]
    for abi in selected_abis(info.get("abi", "all")):
        item = uploads.get(abi, {})
        outcome = item.get("outcome") or "not uploaded"
        url = item.get("url", "")
        link = f"[Artifact]({url})" if outcome == "success" and re.fullmatch(r"https://[^\s<>\[\]()]+", url) else "unavailable"
        lines.append(f"| {abi} | {markdown(outcome)} | {link} |")
    return "\n".join(lines) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("plan", "prepare", "package", "summary"))
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--destination", type=Path)
    args = parser.parse_args()
    if args.command == "plan":
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text())
        plan = make_plan(event, context_from_env())
        args.plan.write_text(json.dumps(plan, ensure_ascii=False, indent=2))
        write_outputs(plan)
    elif args.command == "prepare":
        if args.source is None:
            raise ValueError("prepare requires --source")
        plan = prepare_source(args.source, json.loads(args.plan.read_text()))
        args.plan.write_text(json.dumps(plan, ensure_ascii=False, indent=2))
        write_outputs({"sha": plan["source_sha"]})
    elif args.command == "package":
        plan = json.loads(args.plan.read_text())
        revision = subprocess.check_output(["git", "-C", str(args.source), "rev-parse", "HEAD"], text=True).strip()
        verify_revision(revision, os.environ["SOURCE_SHA"])
        result = package_apks(args.source / "app/build/outputs/apk/debug", args.destination,
                              plan, revision, os.environ["SIGNING_MODE"],
                              expected_certificate=os.environ["EXPECTED_CERTIFICATE_SHA256"])
        for abi, item in result.items():
            write_outputs({f"artifact_{abi.replace('-', '_')}": item["artifact"]})
        first = args.destination / selected_abis(plan["abi"])[0] / "build-info.json"
        summary_info = {**json.loads(first.read_text()), "abi": plan["abi"]}
        (args.destination / "summary-info.json").write_text(json.dumps(summary_info))
    else:
        info = json.loads(args.plan.read_text()) if args.plan.is_file() else {}
        summary_info = args.destination / "summary-info.json"
        if summary_info.is_file():
            # Keep the selected ABI set rather than one artifact's concrete ABI.
            info = {**json.loads(summary_info.read_text()), "abi": info["abi"]}
        uploads = {abi: {"outcome": os.environ.get(f"UPLOAD_{abi.replace('-', '_').upper()}_OUTCOME", ""),
                         "url": os.environ.get(f"UPLOAD_{abi.replace('-', '_').upper()}_URL", "")}
                   for abi in ABIS}
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
            summary.write(render_summary(info, uploads, os.environ.get("JOB_STATUS", "unknown")))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print(f"Debug CI failed: {error}", file=sys.stderr)
        sys.exit(1)
