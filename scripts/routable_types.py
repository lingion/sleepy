#!/usr/bin/env python3
"""Routable protocol types, derived from the Android sources.

A catalog entry may only carry a `type` that JwParserRegistry can route to a
parser; SchoolsJsonConsistencyTest enforces this on the app side. Deriving the
allowlist by parsing JwParserRegistry.kt / JwProtocol.kt keeps the generation
pipeline and the app in lockstep without a hand-maintained list: a newly
registered parser automatically admits future admissions of that type.
"""
import re
from functools import lru_cache
from pathlib import Path

JW = Path(__file__).resolve().parents[1] / 'app/src/main/java/com/lingion/sleepy/data/jw'


@lru_cache(maxsize=1)
def routable_types():
    consts = dict(re.findall(r'const val (TYPE_\w+) = "([^"]+)"',
                             (JW / 'JwProtocol.kt').read_text()))
    body = (JW / 'JwParserRegistry.kt').read_text()
    match = re.search(r'FACTORIES: Map<String, \(String\) -> JwParser> = linkedMapOf\((.*?)\n    \)',
                      body, re.S)
    if not match:
        raise RuntimeError('JwParserRegistry.FACTORIES not found; refusing to guess routable types')
    types = set()
    for symbol in re.findall(r'JwProtocol\.(TYPE_\w+)', match.group(1)):
        if symbol not in consts:
            raise RuntimeError(f'FACTORIES references undeclared constant {symbol}')
        types.add(consts[symbol])
    return frozenset(types)


if __name__ == '__main__':
    print(sorted(routable_types()))
