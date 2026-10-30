# SleepDown Kyant Backdrop source dependency

Upstream: https://github.com/Kyant0/AndroidLiquidGlass

Baseline: tag `2.0.0`, commit `bebb11a91bd97bf1dabde479f3b332ad9898731f`.

License: Apache-2.0; see LICENSE. Original `com.kyant.backdrop` packages are retained.
This Android-only module uses the application's Compose 1.11.2 dependencies.

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
  actual GPU work. See the [investigation](../../docs/reports/2026-10-18-nexio-glass-parity.md).
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

`patches/kyant-backdrop-2.0.0-sleepdown.patch` contains the complete production source delta
against the exact upstream baseline, including SleepDown extension files. Do not apply it to this
already-patched module. Regenerate it from the source tree, then apply it to a clean upstream copy
and compare every Android/common runtime source. The Android-only Gradle adapter remains local.

`testAndroidHostTest` covers recording identity, geometry, dynamic outline invalidation and effect
geometry. Existing app glass tests exercise native host drawing and generic scene/Morph behavior.
At the user's request, this round stopped tests and proceeded to Release packaging and installation.
Host rendering does not establish device GPU performance or phone/tablet visual parity.
