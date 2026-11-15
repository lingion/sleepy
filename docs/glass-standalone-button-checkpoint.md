# Standalone glass buttons checkpoint — 2026-11-15

Ordinary Liquid Glass buttons now own a glass body independent of Material button rendering. Their default body is a capsule with a 33.5dp minimum height, centered inside an at-least-48dp activation layout. Full-width and explicit-height callers retain their layout constraints without stretching the glass body vertically. Eight existing iOS 26.5 rest accessibility captures report 33.5pt for ordinary text buttons; this is an initial carrier-height reference, not a measured optical contour. The 12/6dp padding is an engineering starting point. Android font rendering and the complete native animation remain distinct validation items.

Prominent buttons reuse the existing theme primary/onPrimary colors. Regular buttons use a clear glass material and the theme primary foreground. Explicit semantic shapes, colors, borders and padding remain supported. Material Design and missing-host paths delegate to their existing official Material components; Alert footer actions retain their separate scope and layout.

## Entry points

The paste-text preview button releases the Material fixed body height, shape and label style in Glass mode. Import method rows retain their original row layout. Course details, backup/WebDAV, Glass date-calendar navigation and 25 standard shape overrides across 13 editor/settings screens use the matching glass path. Material rows and callbacks remain unchanged. Deliberate custom shapes, including course color groups and remote backup items, are retained.

## Actual pointer fix and verification

A real 56dp tall button could activate from its surrounding touch padding but its lens fell early when the held pointer left the smaller visible body. The outer activation layout now observes that pointer lifetime; the body shares its motion and converts the glow origin into body-local coordinates. Drag normalization uses the visible body's shorter dimension. Releasing, disabling and cancellation retain their reset paths. This observed defect was specific to the tested tall target; the default 48dp case was already covered by Compose touch expansion.

- Three final real-device tests passed: body/hit-target separation and full width, Material/disabled activation, and holding/dragging out of 56dp padding until release without accidental activation.
- The initial run had two failures: the actual pointer defect and an invalid test assumption that the Material layout itself must be 48dp tall. The latter was corrected to preserve official Material behavior, not treated as a product bug.
- Eighteen additional existing tests passed: six official Material/button cases, six date transparency cases and six date focus/geometry transition cases.
- The installed build's real create-table and expanded paste-text pages were captured and visually inspected for layout/cropping. This was a targeted page check, not complete application or native animation acceptance.

The standalone wrapper belongs to the Sleepy app. The public asset library receives only its mapped runtime primitives and App validation evidence; it does not yet distribute this entire button, GlassHost or DatePicker implementation as a standalone package.

## Scope correction after user review

The broad conversion of management cards, appearance/widget/WebDAV navigation and conflict rows into full-width glass buttons was rejected by the user and reverted. Those deliberate page layouts remain as before that expansion. The uncommitted follow-up import/header expansion was discarded, and import method rows were also restored. Future work is restricted to Alert/Sheet backing optics; replacing deliberate list/card layouts is not authorized.
