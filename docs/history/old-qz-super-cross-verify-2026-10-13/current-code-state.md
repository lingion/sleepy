# Current implementation

Before: unchecked adjacent indexing, toInt overflow, mandatory dash and descendant div enumeration could crash or duplicate courses; the new class was not reachable from qz_old. Original generic compat fallback could fabricate weeks.

After: JwOldQzParser delegates to JwOldQzSuperParser; registry and declared qz_old route unchanged. Source-backed structural parsing, bounds, sparse ranges, one-node records, hidden ancestors and leaf block processing; blank teacher/room retained. Headerless incomplete grids are rejected to prevent weekday invention. Source fixtures and malformed-row tests lock these boundaries. FrameTraversalTree recognizes otherwise-unanchored JLICT and fixed-cell forms only after a valid course parses.

Validation is recorded in the PR after execution. No live login was attempted.
