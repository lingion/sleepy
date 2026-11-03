# Third Party Notices

## HaoZai000/NexioSchedule

The progressive top-bar blur shaders in `glass/ui/NexioProgressiveBlur.kt` are
adapted from `ProgressiveBlurTopBar.kt` provided by
[@HaoZai000](https://github.com/HaoZai000), author of
[NexioSchedule](https://github.com/HaoZai000/NexioSchedule).

License: GNU Affero General Public License, Version 3 (AGPL-3.0), retained in
[`third-party/notices/NexioSchedule-AGPL-3.0.txt`](third-party/notices/NexioSchedule-AGPL-3.0.txt).
The reference repository was reviewed at commit
`618f29808f1d313ed8b4a437c66967cd38618cb5`. SleepDown's adaptation retains the
radial sampling and denoise passes, and uses its Backdrop 2 per-node shader cache,
full-resolution coordinates and shared glass surface entry point.

The optional Xiaomi notification workaround and focus-notification field layout
were independently implemented after reviewing NexioSchedule's Shizuku and
island notification approach at commit
`5e12725df66bb7a3a4a9ab346bdc93e6d1f84927`. No Nexio Shizuku or island
notification source file was copied into this project.

Course text color tuning and Xiaomi R8 boundaries were reviewed against NexioSchedule
commit `f2c4ab6def84b5be8a5969ce6e46dcabb2a1cac4`. SleepDown implements its own HSV
brightness floor and saturation adjustment, combined with local sampled soft shadows;
the source files and notification implementation were not copied.

The 2026-10-18 course-glass rendering review uses NexioSchedule commit
`99f14cbcadd6d2e6cbfba4e73c2d001f83673264` as a behavioral reference. Its root
license is AGPL-3.0; no separate license for the customized backdrop module was
found at that commit. This round independently modifies the existing Kyant-based
SleepDown implementation (shared filter/published nodes, lazy sampling and draw
clipping); it does not copy those Nexio customizations under an Apache label.
The unchanged rounded-rect shader source is also present in Apache-2.0 Kyant 2.0.0.

## RikkaApps/Shizuku-API

The GitHub distribution uses [Shizuku-API](https://github.com/RikkaApps/Shizuku-API)
version 13.1.5 for the optional Xiaomi notification workaround.

License: MIT License. The store distribution does not include this dependency.

## Kyant0/AndroidLiquidGlass

Portions of the liquid glass catalog component code are based on
[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass).

License: Apache License, Version 2.0.

The code has been modified for SleepDown-Schedule to preserve the app's
glass quality controls, fallback behavior, restrained tint values, and local
interaction styling.

## compose-miuix-ui/miuix

The settings and education-import page structure uses components from
[compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix), version 0.9.3.

License: Apache License, Version 2.0.

SleepDown-Schedule combines Miuix layout and interaction components with its
existing Kyant liquid-glass surfaces and controls. The Miuix blur module is not
included. The tracked Miuix patches expose surface modifiers, centered dialogs,
content clipping and popup lifecycle hooks used by those combined surfaces.
`patches/miuix-scaffold-underlay.patch` separates the Scaffold page underlay from
its popup host so backdrop consumers do not sample a producer that contains
themselves. The patch base, application order and scope are documented in
`patches/README.md`; the original package names and license headers are retained.

## ShiGuangSchedule/shiguang_warehouse

Education-system adapter indexes, YAML configuration and JavaScript resources
under `app/src/main/assets/shiguang_warehouse-main/` are based on
[ShiGuangSchedule/shiguang_warehouse](https://github.com/ShiGuangSchedule/shiguang_warehouse).

License: MIT License.

The auto-refresh adapter catalog and its selected scripts were reviewed against
upstream commit `fa7cbc2ea116e5ffd082a9fe8cb7bdf4407a8713`. The selection criteria,
source paths and verification scope are recorded in
[`docs/reports/2026-09-22-auto-refresh-beta9.md`](docs/reports/2026-09-22-auto-refresh-beta9.md).
Original script headers and the upstream license are retained.

## Square OkHttp / Okio

The WebDAV transport uses [OkHttp](https://github.com/square/okhttp) 4.12.0
and its [Okio](https://github.com/square/okio) dependency.

License: Apache License, Version 2.0. Upstream license texts are available in
[OkHttp LICENSE.txt](https://github.com/square/okhttp/blob/parent-4.12.0/LICENSE.txt)
and [Okio LICENSE.txt](https://github.com/square/okio/blob/master/LICENSE.txt).
