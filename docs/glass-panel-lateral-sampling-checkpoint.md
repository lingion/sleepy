# Panel lateral sampling checkpoint — 2026-11-15

Alert and Sheet now add a panel-only horizontal source displacement. The previous normal-only field left vertical stripes straight across much of the top and bottom rim. The added reverse sampling field reads inward on each horizontal half, making the displayed stripes bow outward. Dock and ordinary control materials retain a zero gain.

The shipped AGSL expression is `-clamp(x / max(halfWidth, 1e-3), -1, 1) * depth * normalY² * gain`. It shares the existing source shift across clear, dispersed and blurred samples. It does not change contour normals, coverage or rim lighting. The host rejects nonfinite gain and clamps finite values to 0–0.5; the panel factories use the engineering candidate 0.45. This coefficient is not an identified Apple thickness or a unique reconstruction of its lens.

## Measured scope

The actual Android GPU matrix contains four backgrounds × light/dark × Alert/Sheet: 16 captures at the native reference coordinates. Captures were not resized or warped for measurement. All eight background-window comparisons are unchanged. The historical backgrounds used here are calibration evidence, not an independent holdout.

A supplemental fixed-window RGB first-harmonic fit describes the light striped Alert. At y=520, left/right apparent shifts were −14.394/+12.928 px in the native reference, approximately zero in the old Android result, and −14.731/+13.757 px with gain 0.45. The inner rows and bottom edge still differ: native left shifts become positive at y=540–560 while the candidate remains nonpositive; the right bottom displacement is excessive. These rows and channel fit residuals must remain with the comparison. A strict earlier stripe correspondence detector did not recover sufficient continuous support; its curvature and tilt results remain unavailable. Harmonic phase is descriptive and does not establish point correspondence, local invertibility, or full 2D optical equivalence.

The frozen 0.45 plan contains a stale limit sentence referring to 0.025. Its bytes and hash are retained; the actual captured candidate is 0.45. Four high-amplitude changed pixels occur at corner glints in the light striped Alert window and are retained in the full difference report.

## Verification and continuation

- 50 shader, 23 style and 3 press-motion checks passed. Both debug and instrumentation APK builds succeeded.
- Two 16-capture matrices were collected for gains 0.025 and 0.45. The final matrix verifies the installed APK identity and restores the phone's original size and density override 476.
- Source review found no new blocking integration issue. Tests constrain the implementation and its material isolation; they do not prove native visual parity.

The public companion study is maintained in `sleepy-liquid-glass/docs/panel-lateral-sampling-study.md` with frozen plans, comparisons, complete phase rows, negative results and source receipts. This is an implemented experimental improvement. Interior response, bottom edge, independent inputs, other sizes and dynamic optical behavior remain open; the complete alignment goal stays active.
