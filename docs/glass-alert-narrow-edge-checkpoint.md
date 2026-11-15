# Alert narrow edge checkpoint — 2026-11-15

Light Alert now keeps its original 8dp body blur and adds a separate 1.1dp contour-frost band. Refraction depth, lateral sampling, geometry, tint, highlights and foreground layouts stay unchanged. The existing half-dp stroke retains its opacity and horizontal gradient, but integrates coverage in the optical pass rather than drawing a second rasterized Path. Other roles and Material Design keep their existing paths; pre-API33 still paints the original stroke.

The preceding 6.5dp body-blur experiment was rejected after historical multi-background regressions. Its body changes are absent from this checkpoint.

## Verification

- 52 shader-contract and 23 style-reference tests pass; debug and androidTest APKs build.
- Installed APK SHA-256: `8a24eeb7755bb62692c06eb566051c8c80a25faea6327f024ef130b3d67849cb` (the authoritative hash is recorded below before commit).
- Four fixed calibration sources, each with clean-before / rest / rest-repeat / dismiss-after, were captured on API36 using the unchanged 750×1334 input map and 320×172dp Alert.
- The fixed native26.5/baseline/final comparison preserves all original endpoints, 118 samples per side and 40 rows per source. All 320 final raw source-side rows have unique 10/50/90 crossings. The 304 rows beyond the changed first two pixel rows retain the baseline profile.
- Mean 10–90 width at 0.25dp inset: native35.042dp, old13.332dp, final34.614dp. At0.75dp: native28.741dp, old15.243dp, final28.413dp. These widths describe image response and do not identify physical glass thickness.
- Full-profile L1 improves in all four conditions. Twelve changed side-rows improve and four reversed-polarity side-rows regress; the remaining304 are unchanged. No original failed gate is replaced or promoted to an overall pass.
- CPU-stroke-only ablation independently removed16 reproducible first-row dark pulses and four extra-crossing rows;312 other raw scan rows stayed exactly unchanged. The final analytic stroke preserves a border instead of adopting the ablation's border removal.

## Evidence and limits

The local evidence set is `glass-panel-thickness-20261115`: final build/source receipts `paired-slope-narrow-rim-r3-*`, acquisition `android-paired-slope-narrow-rim-r3-20261115`, and fixed-profile output `analysis-mapping-audit/paired-slope-narrow-rim-comparison-20261115/run3`. Earlier candidates, failures, display-only system-bar differences and source receipts are retained separately.

This is the accepted visual checkpoint for the current backing work. It does not establish universal pixel equivalence, independent-input acceptance, other sizes, dark optical equivalence or dynamic parity. The next requested scope is slider refraction and endpoint rail coverage; no management cards, import rows or other page layouts are changed.
