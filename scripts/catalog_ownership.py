"""Fail-closed URL ownership decisions, separate from authenticated parser support."""
import base64
import hashlib
import ipaddress
import re
from html.parser import HTMLParser
from urllib.parse import urljoin, urlsplit


class EvidenceHTMLParser(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.text = []
        self.refreshes = []
        self.hidden = 0
        self.unsafe = False

    def handle_starttag(self, tag, attrs):
        if tag in {"script", "style"}:
            self.hidden += 1
        attributes = dict(attrs)
        if tag == "script" or (tag == "input" and attributes.get("value")):
            self.unsafe = True
        if tag == "meta" and (attributes.get("http-equiv") or "").lower() == "refresh":
            self.refreshes.append(attributes.get("content") or "")

    def handle_endtag(self, tag):
        if tag in {"script", "style"}:
            self.hidden = max(0, self.hidden - 1)

    def handle_data(self, data):
        if not self.hidden:
            self.text.append(data)


def raw_capture(capture):
    """Parse only hash-checked raw bodies and response headers."""
    try:
        if not capture.get("checkedAt") or not valid_url(capture.get("url")):
            return None
        decoded = {}
        for field in ("headers", "body"):
            value = base64.b64decode(capture[field + "Base64"], validate=True)
            if hashlib.sha256(value).hexdigest() != capture.get(field + "Sha256"):
                return None
            decoded[field] = value
        lines = decoded["headers"].decode("iso-8859-1").splitlines()
        match = re.fullmatch(r"HTTP/\d(?:\.\d)? (\d{3})(?: .*)?", lines[0])
        if not match:
            return None
        headers = {}
        for line in lines[1:]:
            if not line:
                continue
            if line[0].isspace() or ":" not in line:
                return None
            key, value = line.split(":", 1)
            if key.lower() not in {"location", "content-type"}:
                return None
            headers.setdefault(key.lower(), []).append(value.strip())
        page = EvidenceHTMLParser()
        page.feed(decoded["body"].decode(capture.get("encoding", "utf-8")))
        page.close()
        if page.unsafe:
            return None
        return int(match[1]), headers, page
    except (KeyError, ValueError, TypeError, IndexError, UnicodeError, LookupError):
        return None


def official_meta_chain_proves(source, name, target_url):
    """An official published root needs a literal, complete captured chain.

    Meta refresh targets come from hashed HTML; Location values come from
    hashed headers. Researcher-supplied 'to' fields are never proof.
    """
    chain = source.get("metaChain") or {}
    captures = chain.get("responses", [])
    publication = chain.get("publication") or {}
    parsed = raw_capture(publication)
    root = chain.get("publishedRoot", "")
    quote = chain.get("publicationQuote", "")
    identity = identity_name(name).split(" - ")
    if (not chain.get("archive") or not 2 <= len(captures) <= 10
            or not parsed or parsed[0] != 200
            or not link_matches(publication.get("url"), source.get("url"))
            or not endpoint_key(root)):
        return False
    text = "".join(parsed[2].text)
    address = urlsplit(root)
    if (not quote or quote not in text or not all(part in text for part in identity)
            or address.path not in {"", "/"} or address.query or address.fragment):
        return False
    root_pattern = (r"(?<![A-Za-z0-9._:/@-])(?:" + re.escape(address.scheme + "://")
                    + r")?" + re.escape(address.netloc) + r"/?(?![A-Za-z0-9._:/?#@%=-])")
    if not re.search(root_pattern, quote):
        return False
    current = root
    for index, capture in enumerate(captures):
        parsed = raw_capture(capture)
        if not parsed or not link_matches(capture.get("url"), current):
            return False
        status, headers, page = parsed
        if index == len(captures) - 1:
            return (status == 200 and not page.refreshes and link_matches(current, target_url)
                    and all(part in "".join(page.text) for part in identity))
        if status in {301, 302, 303, 307, 308}:
            locations = headers.get("location", [])
            if len(locations) != 1 or not locations[0]:
                return False
            destination = locations[0]
        elif status == 200 and len(page.refreshes) == 1:
            refresh = re.fullmatch(r"\s*\d+(?:\.\d+)?\s*;\s*url\s*=\s*(.+?)\s*", page.refreshes[0], re.I)
            if not refresh:
                return False
            destination = refresh[1]
            if destination[:1] in {"'", '"'}:
                if len(destination) < 3 or destination[-1] != destination[0]:
                    return False
                destination = destination[1:-1]
        else:
            return False
        current = urljoin(current, destination)
        if not endpoint_key(current) or endpoint_key(current)[:3] != endpoint_key(root)[:3]:
            return False
    return False

def identity_name(name):
    # Only UI entry variants may be dropped; campus/graduate identities stay exact.
    return re.sub(r"（超星）|（新版）|（旧版）", "", name).strip()


def valid_url(url):
    try:
        parsed = urlsplit(url or "")
        return parsed.scheme in {"http", "https"} and bool(parsed.hostname) and not parsed.username and not parsed.password
    except ValueError:
        return False


def public_host(url):
    """Domain names are assumed reachable; IP literals must be globally routable."""
    try:
        host = urlsplit(url).hostname
    except ValueError:
        return False
    if not host:
        return False
    try:
        address = ipaddress.ip_address(host)
    except ValueError:
        return True
    return address.is_global


def wakeup_declared_owner(proof, url):
    """WakeUp's shipped admission list is a third-party ownership declaration.

    Retain it only when independent liveness and safety evidence agrees: the
    batch probe reached this exact operational URL with HTTP 200, the host is
    public, and the URL is well-formed without credentials or redaction.
    Protocol routing still requires the row type to be a registered parser
    (enforced before admission by generate_school_catalog).
    """
    return (
        proof.get("source") == "wakeup-batch-probe"
        and proof.get("http") == 200
        and "REDACTED" not in url
        and valid_url(url)
        and public_host(url)
    )


def shiguang_declared_owner(proof, url):
    """ShiGuang (shiguang_warehouse) entries are likewise third-party claims.

    Same fail-closed bar as the WakeUp declaration: an independent probe that
    reached this exact operational URL with HTTP 200 over a public host. Rows
    retained this way stay unverified — no authenticated import was tested.
    """
    return (
        proof.get("source") == "shiguang-batch-probe"
        and proof.get("http") == 200
        and "REDACTED" not in url
        and valid_url(url)
        and public_host(url)
    )


def shiguang_adapters_declared_owner(proof, url):
    """Full-absorption admission: the warehouse's adapter list is the claim.

    User ruling 2026-10-27: absorb every ShiGuang school unconditionally,
    marked unverified. A failed or missing liveness probe (timeout, 403, 302)
    is not a rejection — a locally unreachable page today is not evidence the
    declaration is wrong. The safety bar stays: valid http(s) URL, no
    credentials, no redaction, public host. Retained rows land unverified with
    enableFetch False, exactly like every other admission.
    """
    return (
        proof.get("source") == "shiguang-adapters"
        and "REDACTED" not in url
        and valid_url(url)
        and public_host(url)
    )


def endpoint_key(url):
    """Only a missing root slash/default port is equivalent without evidence."""
    if not valid_url(url):
        return None
    try:
        parsed = urlsplit(url)
        return (parsed.scheme.lower(), parsed.hostname.lower(),
                parsed.port or (443 if parsed.scheme == "https" else 80),
                parsed.path or "/", parsed.query, parsed.fragment)
    except ValueError:
        return None


def link_matches(link, target_url, source_url=None):
    left, right = endpoint_key(link), endpoint_key(target_url)
    return left is not None and left == right


def archived_redirect_proves(source, linked_url, target_url):
    """Accept only a captured directed redirect to this exact endpoint.

    Two URLs ending at a generic SSO page do not prove tenant equivalence.
    Capture metadata cannot replace a continuous observed HTTP redirect chain.
    """
    for capture in source.get("endpointRedirects", []):
        if (not capture.get("checkedAt") or not capture.get("archive")
                or not re.fullmatch(r"[0-9a-f]{64}", capture.get("bodySha256", ""))
                or not link_matches(capture.get("requestedUrl"), linked_url)):
            continue
        current = linked_url
        for hop in capture.get("redirects", []):
            if hop.get("http") not in {301, 302, 303, 307, 308} or not link_matches(hop.get("from"), current):
                break
            current = hop.get("to")
            if not valid_url(current):
                break
            if link_matches(current, target_url):
                return True
    return False


def published_urls(source):
    # Do not remove arbitrary whitespace or perform substring matching: both
    # can silently turn a different port, tenant, or query into the target.
    text = source.get("observedText") or ""
    urls = re.findall(r'https?://[A-Za-z0-9._~:/?#@!$&()*+,;=%-]+', text)
    return [url.rstrip(".,);]}") for url in urls]


def source_proves(source, name, url, renames=None):
    """Validate the archived observation, not merely a researcher-supplied verdict."""
    if not valid_url(url) or not valid_url(source.get("url")) or not source.get("checkedAt"):
        return False
    kind = source.get("kind")
    if kind == "official-school-meta-chain":
        return official_meta_chain_proves(source, name, url)
    identity = identity_name(name)
    if kind == "renamed-school-page":
        mapping = (renames or {}).get(identity, {})
        if not mapping.get("evidence") or source.get("identityName") != mapping.get("newName"):
            return False
        identity = mapping["newName"]
    observed = (source.get("title") or "") + " " + (source.get("observedText") or "")
    parts = identity.split(" - ")
    if not all(part in observed for part in parts):
        return False
    if kind in {"entry-page", "renamed-school-page"}:
        return link_matches(source["url"], url)
    if kind == "official-school-link":
        links = source.get("linkedUrls", [])
    elif kind == "official-school-published-address":
        links = published_urls(source)
    else:
        return False
    return any(link_matches(link, url) or archived_redirect_proves(source, link, url) for link in links)


def row_key(row):
    return row.get("catalogId") or row["sortKey"] + "_" + row["name"]


def _parsed(url):
    """Mirror of SchoolDomainMatch.parsed: http(s), no userinfo, non-blank host."""
    if not isinstance(url, str) or not url.strip():
        return None
    try:
        parts = urlsplit(url.strip())
    except ValueError:
        return None
    host = parts.hostname
    if parts.scheme.lower() not in {"http", "https"} or not host:
        return None
    if parts.username is not None or parts.password is not None or ("@" in (parts.netloc or "")):
        return None
    return parts


def _same_entry(a, b):
    """Mirror of SchoolDomainMatch.sameEntry: scheme + host + effective port + path/query/fragment."""
    def port(uri):
        try:
            explicit = uri.port
        except ValueError:
            return None
        if explicit is not None:
            return explicit
        return 443 if uri.scheme.lower() == "https" else 80
    pa, pb = port(a), port(b)
    if pa is None or pb is None:
        return False
    return (a.scheme.lower() == b.scheme.lower()
            and a.hostname == b.hostname
            and pa == pb
            and (a.path or "/") == (b.path or "/")
            and a.query == b.query
            and a.fragment == b.fragment)


_MULTI_SUFFIX_SECOND = {"edu", "ac", "gov", "org"}
_MULTI_SUFFIX_TLD = {"cn", "hk", "uk", "tw", "jp"}


def _registrable_domain(host):
    """Mirror of SchoolDomainMatch.registrableDomain."""
    h = (host or "").lower().strip().rstrip(".")
    if not h:
        return ""
    parts = h.split(".")
    if len(parts) <= 2:
        return h
    if parts[-1] in _MULTI_SUFFIX_TLD and parts[-2] in _MULTI_SUFFIX_SECOND:
        return ".".join(parts[-3:])
    return ".".join(parts[-2:])


SUPPORTED_STATUSES = {"supported", "grad_supported"}


def _row_supported(row):
    """Mirror JwSchoolInfo: null/missing status defaults to STATUS_SUPPORTED."""
    st = row.get("status")
    if st is None:
        return True
    return st in SUPPORTED_STATUSES


def _is_vpn(host):
    """Mirror of SchoolDomainMatch.isVpnRewrite (host never contains '/')."""
    h = (host or "").lower().strip().rstrip(".")
    return bool(h) and (".webvpn." in h or h.startswith("webvpn."))


def _raw_port(uri):
    """java.net.URI.getPort(): explicit port or -1 (tier 2 never treats
    an explicit port as equal to a scheme default)."""
    try:
        return uri.port if uri.port is not None else -1
    except ValueError:
        return None


def match_school_rows(target, entries):
    """Faithful port of SchoolDomainMatch.matchSchool over pre-parsed entries.

    entries: [(row, parsed_uri_or_None)]; rows without a parseable URL are
    inert there, exactly like Kotlin's mapNotNull. Returns the resolved row
    (supported and importable) or None.
    """
    if target is None:
        return None
    host = target.hostname or ""
    if _is_vpn(host):
        return None

    def supported_single(matches):
        if len(matches) != 1:
            return None
        r = matches[0][0]
        return r if _row_supported(r) else None

    exact = [(r, p) for (r, p) in entries if p is not None and _same_entry(p, target)]
    if exact:
        return supported_single(exact)
    same_host = [(r, p) for (r, p) in entries
                 if p is not None and p.hostname == host and _raw_port(p) == _raw_port(target)]
    if same_host:
        return supported_single(same_host)
    domain = _registrable_domain(host)
    if not domain:
        return None
    return supported_single([(r, p) for (r, p) in entries
                             if p is not None and _registrable_domain(p.hostname) == domain])


def _row_entries(rowset):
    out = []
    for r in rowset:
        u = r.get("url") or ""
        out.append((r, _parsed(u) if u else None))
    return out


def _candidate_poisons(rows_by_key, head_rows, candidate_keys):
    """Withhold promotions that regress SchoolDomainMatch fail-closed lookups.

    A candidate row C (newly operational this run, or given a new corrected
    URL) is a collision poison iff C's operational URL maps to a unique
    supported row in the committed catalog (head_rows) but resolves to
    nothing — no row, or a different row — once every promotion lands.
    Candidates sharing a host/domain with a committed row are never allowed
    to create an ambiguous lookup: process in row order and exclude
    already-withheld candidates from the generated view, so on a host claimed
    by a HEAD row every colliding candidate is withheld.
    """
    head_entries = _row_entries(head_rows)
    withheld = set()

    def regresses(target):
        head_res = match_school_rows(target, head_entries)
        if head_res is None:
            return False
        generated = [r for k, r in rows_by_key.items() if r.get("url") and k not in withheld]
        new_res = match_school_rows(target, _row_entries(generated))
        return new_res is None or row_key(new_res) != row_key(head_res)

    for key in candidate_keys:
        row = rows_by_key.get(key)
        if row is None or not row.get("url"):
            continue
        target = _parsed(row["url"])
        if target is None:
            continue
        host = target.hostname or ""
        is_poison = False
        if not _is_vpn(host):
            is_poison = regresses(target)
        if not is_poison:
            # The row participates as a domain-tier ENTRY for every host of its
            # registrable domain (a VPN host is only exempt as a lookup input),
            # so probe the registrable domain of the candidate host too.
            domain = _registrable_domain(host)
            if domain and domain != host:
                probe = _parsed("https://probe." + domain + "/")
                if probe is not None:
                    is_poison = regresses(probe)
        if is_poison:
            withheld.add(key)
    return withheld


def apply_ownership(rows, evidence, baseline_names, renames=None, domain_poison=None, head_rows=None):
    entries = evidence["entries"]
    indexed = {e["catalogId"]: e for e in entries}
    if len(indexed) != len(entries):
        raise ValueError("Duplicate ownership evidence ID")
    active = {r.get("catalogId") or r["sortKey"] + "_" + r["name"] for r in rows if r.get("url")}
    if set(indexed) != active:
        raise ValueError("Ownership evidence must cover every operational input exactly once")

    def outcome(row, proof):
        status = proof["status"]
        if status == "contradicted":
            correction = proof.get("proposedCorrection") or {}
            sources = correction.get("sources", [])
            url = correction.get("url", "")
            official = any(s.get("kind") in {"official-school-published-address", "official-school-link", "official-school-meta-chain"} and source_proves(s, row["name"], url, renames) for s in sources)
            entry = any(s.get("kind") == "entry-page" and source_proves(s, row["name"], url, renames) for s in sources)
            if correction.get("status") == "verified" and official and entry and valid_url(url) and correction.get("parserFamily") == row.get("type"):
                return "corrected-official-entry", url
            return "blocked-contradicted-owner", ""
        if status == "unknown" and row["name"] not in baseline_names:
            if wakeup_declared_owner(proof, row["url"]):
                return "retained-wakeup-declared-owner", row["url"]
            if shiguang_declared_owner(proof, row["url"]):
                return "retained-shiguang-declared-owner", row["url"]
            if shiguang_adapters_declared_owner(proof, row["url"]):
                return "retained-shiguang-adapters-owner", row["url"]
            return "withheld-new-unproven-owner", ""
        if status == "unknown":
            return "retained-existing-config-ownership-unresolved", row["url"]
        return "ownership-confirmed", row["url"]

    predicted = {}
    predicted_rows = {}
    candidate_keys = []
    for row in rows:
        if not row.get("url"):
            continue
        key = row_key(row)
        proof = indexed[key]
        if proof["name"] != row["name"] or proof["url"] != row["url"]:
            raise ValueError("Stale ownership evidence: " + key)
        if proof["status"] not in {"verified", "unknown", "contradicted"}:
            raise ValueError("Invalid ownership verdict: " + key)
        if proof["status"] == "verified" and not any(source_proves(s, row["name"], row["url"], renames) for s in proof.get("sources", [])):
            raise ValueError("Verified ownership requires matching recorded identity and target evidence: " + key)
        action, final_url = outcome(row, proof)
        predicted[key] = (action, final_url)
        if final_url:
            snapshot = dict(row)
            snapshot["url"] = final_url
            predicted_rows[key] = snapshot
        if action in {"retained-wakeup-declared-owner", "retained-shiguang-declared-owner",
                      "retained-shiguang-adapters-owner",
                      "corrected-official-entry"}:
            candidate_keys.append(key)

    if domain_poison is None:
        domain_poison = set()
        if head_rows is not None and candidate_keys:
            domain_poison = _candidate_poisons(predicted_rows, head_rows, candidate_keys)

    changes = []
    for row in rows:
        if not row.get("url"):
            continue
        key = row_key(row)
        proof = indexed[key]
        action, final_url = predicted[key]
        if key in domain_poison:
            block(row)
            action = "withheld-domain-collision"
        elif action == "corrected-official-entry":
            row["url"] = final_url
            row["status"] = "grad_unverified" if row.get("status", "").startswith("grad_") else "unverified"
            row["enableFetch"] = False
        elif action == "blocked-contradicted-owner" or action == "withheld-new-unproven-owner":
            block(row)
        changes.append({"catalogId": key, "name": row["name"], "ownershipStatus": proof["status"], "action": action, "operationalUrl": row["url"], "authenticatedImportTested": proof.get("authenticatedImportTested", False)})
    return changes

def block(row):
    row.update(url="", type=None, enableFetch=False, status="grad_pending" if row.get("status", "").startswith("grad_") else "pending")
