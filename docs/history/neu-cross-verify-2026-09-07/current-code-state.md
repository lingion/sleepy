# NEU Current Code State

## Entry points

- Protocol constant and registry: `app/src/main/java/com/lingion/sleepy/data/jw/JwProtocol.kt`, `JwParserRegistry.kt`
- JSON parser: `app/src/main/java/com/lingion/sleepy/data/jw/JwNeuParser.kt`
- WebView capture: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt`
- ICS parser/exporter: `ScheduleParser.kt`, `ScheduleExporter.kt`

## Data flow

1. The user logs into NEU in the WebView.
2. The `TYPE_NEU` capture branch calls `NEU_FETCH_JS`.
3. The script fetches the schedule-detail endpoint with the WebView session cookie.
4. The raw JSON is returned through the existing Wisedu bridge envelope.
5. Kotlin routes the payload to `JwNeuParser`, which reads `datas.arrangedList`.
6. Parsed courses are persisted through the existing import path.

## Reproduced and fixed

- Issue #27: generic Wisedu capture previously ran for NEU; the dedicated dispatch branch and endpoint contract are now covered by `JwNeuWebViewContractTest`.
- Issue #28: NEU ICS section inference and export closure are covered by `NeuRealFixtureImportTest` and `NeuRoundTripIcsTest`.
- Issue #22: row identity and edit isolation are covered by `RowKeyDifferTest`; the save path uses `RowKeyDiffer` and `ScheduleRepository.applyDiff`.
- Issue #23: irregular nodes, custom durations, anchors, and edge-node cleanup are covered by `TimeTableUtilsEdgeNodeTest` and `IrregularValidationTest`.

## Verification boundary

The automated checks validate source contracts, parser behavior, and persistence-diff semantics. A live NEU login/API capture was not performed in this offline verification pass; online success still depends on a valid NEU session and reachable service.
