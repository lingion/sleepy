# Native Slider endpoint fixture

This fixture contains actual iOS 26.5/23F77 public Slider presentation geometry and
current UITouch delivery from native endpoint run 65. It contains no Android
observations, fitted deformation output, synthetic expected geometry, or time shift.

- Native run: `-_NativeReferenceTests_testSwiftUIEndpointSlider_-CB1FE48F-2302-4409-9BC6-293452D7F253`.
- Native trace SHA-256: `c2be83b9fc7eb36f9558b9d914b17723ab1e9c3a4d32178b199e5cb4dda4dbc1`.
- CSV SHA-256: `567390bfffae09c9c677447b9a0210966af4ee8c8dc6b47083df0b55ca477c77`.
- Native device geometry: 375x667 points, 2x, light, no reduced motion/transparency.
- Locked training gestures: 0 forward slow, 2 forward fast.
- Complete held-out gestures: 1 reverse slow, 3 reverse fast. They were not used
  to select the model, its search bounds, gains, timing, or coefficient ordering.
- Window: DOWN receipt -100ms through UP receipt +1.2s, all phases retained.
- 397 actual main-delivery input rows and 907 actual native frame rows.

`time_s` is actual deliveredT relative to the gesture's actual DOWN delivery for
input rows. `sample_time_s` remains separate; current delivery coordinates and
phases are retained. Frame `time_s` is the midpoint of the recorded complete
`capture_start_s..capture_end_s` getter bracket. Native observations are never
interpolated. Rows at negative time are prepress observations.

The input has a fixed independently measured mapping: a one-time 10pt slop,
then finger dx/335 for value. `target_value` is that mapped/clamped native target;
it matches native held callbacks within 5.2e-8. `visual_input_pt` is its change
from the initial value multiplied by native visual center travel 335-37=298pt.
Input holds its last target after UP. Native post-UP value callbacks are outputs
and are not used as an extra forcing signal. This mapping preserves all original
gesture offsets and uses no optimized scale, translation, or time offset.

`scale_x`/`scale_y` are actual public presentation diagonal transforms of the
outer `_UILiquidLensView`; `bounds_width_pt`/`bounds_height_pt` contain the measured
isotropic lift. All 907 frames have identity root transforms, zero off-axis lens
terms, and available presentation geometry. Carrier rectangles satisfy
width=bounds_width*scale_x and height=bounds_height*scale_y. These are carrier
geometries, not alpha contours or complete material support.

`Spec.Slider` is an independent empirical approximation with normalized unit mass.
It reuses the cascade equation form but no Segment/Dock coefficients. Native-only
forward training used three independent starts; complete reverse gestures were
evaluated after train-only selection. One damping reaches the broad numerical
search bound 3. The spring stages can be exchanged with gain rescaling in the
continuous equation, so physical coefficients are not uniquely recovered. The
declared ordering must remain unchanged for the existing numerical integrator.

At max step 1/240s with current semi-implicit updates, stop thresholds and existing
horizontal +/-0.22 clamp, native-frame-partition carrier width/height RMSE given
measured native lift is: train slow .079/.052pt, train fast .384/.250pt, held-out
slow .105/.068pt, held-out fast .461/.300pt. Isotropic held-out baselines are
.227/.148pt slow and 3.191/2.075pt fast. These are deformation residuals, not
production center/lift/optical parity. Full held-out 60/120Hz phase and declared
dropped-frame diagnostics remain better than those baselines; zero-order held
draw output under the worst declared drop gives .672/.435pt fast RMSE.

The four native gestures never reach the clamp. Synthetic full-travel steps can
reach it and remain bounded. Native extreme velocity, track-tap saturation,
rapid reversals, RTL and actual Android input/frame differences are not exact
parity claims. RangeSlider keeps its previous isotropic geometry.

Original audit/export scripts and full source manifest are in the task artifact
`artifacts/glass-optics-20261112/android-native-replay-66/independent-slider-gap-review/`:
`analyze_slider.py`, `fit_native_slider.py`, `check_fit_numerics.py`,
`check_partition_sensitivity.py`, and `export_production_fixture.py`.
The older schema-1 SliderGrid run was not used for fitting: its fast reverse
begins outside the resting lens, and it lacks complete getter brackets.
