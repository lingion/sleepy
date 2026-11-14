# Sleepy vendored Kyant Backdrop module

This module was imported through the customized source assets of
[xiaomanjun233/SleepDown-Schedule](https://github.com/xiaomanjun233/SleepDown-Schedule).
The original Kyant baseline retains Apache-2.0. SleepDown-owned changes have a
separate source-available non-commercial notice; they must not be described as
Kyant originals solely because they share its package name. The historical
SleepDown acquisition/import revision was not recorded. On 2026-11-14, all 42
Android/common runtime files and this module's original README at Sleepy import
`c2490785c90ea57f6a06ab14c14386786f19b9d0` matched
[SleepDown `d02898140327796946f2b140cb3ca87390595810`](https://github.com/xiaomanjun233/SleepDown-Schedule/tree/d02898140327796946f2b140cb3ca87390595810/third-party/kyant-backdrop)
byte-for-byte. The retained SleepDown license and third-party notices also match
that snapshot exactly. This establishes a reproducible equivalent source revision,
not the historical acquisition revision or authorization to redistribute its
customizations. Evidence hashes and the later Sleepy changes are recorded in
[UPSTREAM.json](UPSTREAM.json). See the [attribution inventory](../../ACKNOWLEDGEMENTS.md)
and the retained [SleepDown license](../../app/src/main/assets/third_party/notices/SleepDown-LICENSE.md)
and [upstream third-party notices](../../app/src/main/assets/third_party/notices/SleepDown-THIRD_PARTY_NOTICES.md).

The historical implementation and verification notes below came with the source
assets and describe SleepDown. They do not record tests performed on Sleepy.
Sleepy build wiring and dependency versions are defined by [build.gradle.kts](build.gradle.kts).

Original upstream: https://github.com/Kyant0/AndroidLiquidGlass

Baseline: tag `2.0.0`, commit `bebb11a91bd97bf1dabde479f3b332ad9898731f`.

Original upstream license: Apache-2.0; see LICENSE. Original `com.kyant.backdrop` packages are retained.

Against the pinned Kyant baseline, the current runtime has 24 original files with
only one appended newline, 10 modified original files and eight extension files;
none of the 34 baseline files is missing. `internal/Shaders.kt` is in the
newline-only group. Since the original import, Sleepy changed only
`BackdropEffectScope.kt` and `DrawBackdropModifier.kt` in commits `ff6672a7`
(separate preprocessing input) and `6faf5b7a` (current shape provider capture).
These content comparisons do not resolve the customization license review.

## Course rendering (2026-10-18)

Behavioral reference:
[HaoZai000/NexioSchedule@99f14cbc](https://github.com/HaoZai000/NexioSchedule/tree/99f14cbcadd6d2e6cbfba4e73c2d001f83673264).
That repository has an AGPL-3.0 root license and no separate license was found for its customized
backdrop files. This round independently changes SleepDown's existing Apache Kyant implementation;
it does not import Nexio's customized source or label those changes Apache-2.0.

- Wallpaper, shared filtering and ordinary course Lens buffers all use full resolution.
  The old 0.48 source/card scale and count-based course downsampling have been removed.
- `SharedBlurBackdrop` owns two nodes: `blurLayer` carries the prefix effect, `bakedLayer`
  records that node and publishes a source with no RenderEffect of its own. Source fingerprints,
  producer content versions, geometry, density and font scale invalidate the shared recording.
  Radius changes update the filter and published recording without replacing either layer.
- **Both layers record commands, not pixel snapshots.** Unlike the reference's Auto published
  layer, SleepDown explicitly sets this one shared node to Offscreen. HWUI then retains a shared
  raster surface and evaluates the inner filter when that surface is dirty, instead of forwarding
  each card's different clip to the filter. Android's RenderEffect image cache also keys on clip
  bounds, so an Auto wrapper alone cannot guarantee reuse. This is one shared allocation, not an
  extra Offscreen layer per card. No CPU readback is used. Device tracing is still needed to measure
  actual GPU work. See the upstream investigation at `docs/reports/2026-10-18-nexio-glass-parity.md` (not included in this checkout).
- Consumers use the source's full-resolution coordinate transform for both initial raw wallpaper
  and the published prefix. Once the prefix is ready, the local effect chain contains only Lens,
  followed by SleepDown's tint; it does not append another Blur.
- A sampling GraphicsLayer is created only for an actual RenderEffect or effect padding. Without
  either, backdrop drawing goes directly to the clipped canvas. Removing the local effect releases
  the layer; detach releases remaining ownership.
- Timetable consumers set `placementLayer=false`: their shape clips at draw time. Rounded clips
  reuse a Path keyed by the complete RoundRect, including asymmetric radii and origin.
- `BackdropViewport` skips fully invisible background sampling in window coordinates, including
  transforms. Viewport changes invalidate drawing, not composition. Pager and card nodes remain
  mounted; entering the viewport resumes the same local lens.
- Ordinary course samples follow one reference recording route. The former shared-position and
  live per-card sample caches/direct-downsample branch are gone. Frozen scene recording reuse
  remains available for existing retained underlays.
- The unchanged upstream rounded-rect AGSL source matches Nexio's shader source. At course scale 1,
  size, offset, corner radii, refraction height/amount, depth and dispersion uniforms follow the
  same pixel geometry. SleepDown retains `effectOffset` for editor/Morph compatibility.
- Existing shader builders share compiled effects on their drawing thread, keyed by name and
  source. Each native RenderEffect snapshots its own uniforms. Direct-paint highlights retain
  node ownership; the final effect consumer releases the shared program.

Ordinary course shadows now use Compose's cached mask drawing instead of two Kyant Offscreen
GraphicsLayers. Their color, opacity, blur radius and offsets are retained; translucent interiors
exclude the outer shadow. Tint and the neutral white/black fills are flattened, and tint brushes
are reused while only the sampled wallpaper position changes. The colored edge mesh, highlights
and text stay at layout resolution. Editor/Morph shells retain their existing decoration geometry.

## Compatibility extensions

Other consumers retain their existing APIs and defaults: `BackdropRenderOptions` pause/freeze,
fixed geometry and allocation padding; sample scales for editor/dialog surfaces; exported backdrops;
shape/outline observation; decoration recording reuse; and opt-in popup transform compensation.
No global change forces those consumers onto the course configuration.

`layerBackdrop(recordKey)` keeps completed producer recordings for non-null, complete pixel keys;
null keys remain live. Callers include asynchronous image content and animation changes.
Recording revisions are published with `Snapshot.withoutReadObservation`: producers must not
subscribe to their own version increments and continuously dirty a retained scene during Morph.
`ShapeProvider` keeps equivalent callback caches and observes dynamic outlines.

`BackdropDiagnostics` is optional. Layer/recording/pixel-area counters are ownership and CPU
recording evidence, not GPU allocations, resident memory, frame submissions or FPS.

## Patch and verification

The imported README referred to `patches/kyant-backdrop-2.0.0-sleepdown.patch`,
which is absent from this checkout. The
[pinned SleepDown patch](https://github.com/xiaomanjun233/SleepDown-Schedule/blob/d02898140327796946f2b140cb3ca87390595810/patches/kyant-backdrop-2.0.0-sleepdown.patch)
is 68,170 bytes (SHA-256
`dffae0ff919598b8dd6bfadd4e09c80edecc8398f2174e1f6902bb4c4d5ddab7`).
It predates the later Sleepy changes above. Do not apply it to this already-patched
module. For extraction, reconstruct the full current delta against clean Kyant
2.0.0 and compare every Android/common runtime source. The local Gradle adapter
is outside that runtime comparison.

`testAndroidHostTest` covers recording identity, geometry, dynamic outline invalidation and effect
geometry. Existing app glass tests exercise native host drawing and generic scene/Morph behavior.
At the user's request, this round stopped tests and proceeded to Release packaging and installation.
Host rendering does not establish device GPU performance or phone/tablet visual parity.
