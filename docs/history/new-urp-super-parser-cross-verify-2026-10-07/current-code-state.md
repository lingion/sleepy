# New URP flat-array implementation state (2026-10-13)

Parser: JwNewUrpSuperParser.kt accepts flat JSON arrays embedded in HTML or passed directly; JwNewUrpParser falls back when its nested dateList shape is absent. Both parsers reject malformed week bitmaps and preserve two-week runs. JwParserRegistry registers new_urp_super; FrameTraversalTree identifies valid embedded arrays, including all-zero-week empty terms. Tests include 256 exhaustive eight-week bitmaps for each variant and frame/registry routes.

No verified universal fetch endpoint exists. If school data is only in an uncaptured API response, a real endpoint specimen is needed before adding network retrieval. Run targeted and full Gradle checks on the rebased branch.
