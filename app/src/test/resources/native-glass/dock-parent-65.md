# Native Dock parent translation fixture

This bounded fixture retains all 362 actual native65 main-delivery input rows
and 541 actual parent carrier observations used by the frozen parent translation
candidate. It contains the 209-observation slow-forward training gesture and
214/57/61-observation slow-reverse/fast-forward/fast-reverse holdouts. These
holdouts were inspected for mechanism witnesses before fitting; this is not a
prospectively blind experiment. No parameters are refit by these tests.

- Native trace: `-_NativeReferenceTests_testSwiftUIEndpointDock_-B04001E3-8227-4310-A795-C10197078AFC`.
- Raw trace SHA-256: `44f6531414e8ccca7c788b2e08b60aff9f4b201baaced3184436ce4d61f572ee`.
- Native input export SHA-256: `be0295af09693b96d34bb514e9b03f41d49ab9a9a43f941fce394c222740ddf0`.
- Frozen model SHA-256: `84d0791892913ecadf95798c950dd93c79be58b78b688a9ddbd0f3e3076458af`.
- Input CSV SHA-256: `8d7d0b6c718e7f31c3101f3861c05dee407e3244eafa7ec473ca0b8bc07dc6d6`.
- Reference CSV SHA-256: `d71f875edda79dc82fd70854a16dc271cb75fb4bc99c7b40d734adac719f8411`.

The measured SwiftUI four-tab parent rests at 333 x 62pt. Translation is the
change in the public parent rectangle center, `x + width/2`, relative to that
rest rectangle. This measures the carrier, not the optical alpha contour.
`source_id` and one-based `raw_line` identify each original trace record. No
large raw trace is duplicated in the repository.

Input `sample_t` and `delivered_t` stay separate. Only actual `source=delivery`
rows drive the model; there is no sample-time substitution, interpolation,
fitted delay, or clock shift. Each gesture starts at x=0, v=0 at actual DOWN
receipt. Observation time is the midpoint of its complete recorded getter
bracket, wholly before actual UP. The retained `finger_x_pt` is the most recent
actual delivery before getter start; the continuous model still processes any
input delivered before the getter midpoint.

The frozen candidate is `u = 0.010287932530511721 * (fingerX - downX)` and
`x'' + 2*zeta*omega*x' + omega^2*x = omega^2*u`, with
omega=9.488283178142856/s and zeta=0.6099720458279732. The model follows all
physical displacements, including in-bounds movement and negative screen X.
Its numerical reproduction tolerance in Kotlin tests is 1e-4dp to accommodate
Float state and nanosecond rounding; that is not a native parity tolerance.

| Gesture | Split | Native residual RMSE | Max absolute | Last 150ms RMSE |
| --- | --- | ---: | ---: | ---: |
| Slow forward | train | .044699370pt | .156530395pt | .150339528pt |
| Slow reverse | holdout | .044642326pt | .156119182pt | .148561294pt |
| Fast forward | holdout | .291415316pt | .586557971pt | .212318315pt |
| Fast reverse | holdout | .294340323pt | .598668836pt | .249824567pt |

The tests retain these measured residuals, including the held-endpoint bias and
fast mismatch. All final native DOWN displacements have magnitude 293pt with
about 250ms endpoint holds. The native data does not establish a general static
width law, rapid-reversal behavior, interruption policy, or parent scale law.
Other Android four-tab widths explicitly extrapolate the same physical-dp law.
UP/CANCEL return via the existing Android PressSpring and interrupted DOWN
continuity are declared Android policies; they are not recovered native laws.

The reproducible standard-library exporter validates every input against the
raw trace/export and every observation against its raw frame, independently
replays the frozen analytic candidate, and checks the original prediction
within 1e-12pt. Its full source hashes and derivation are under task artifact
`artifacts/glass-optics-20261112/android-dock-parent-response-72/helper-reference/`
in `export_reference.py` and `manifest.json`.
