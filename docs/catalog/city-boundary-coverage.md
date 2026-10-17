# City boundary coverage

City metadata and polygon coverage are separate contracts. Unknown institution
addresses must not be inferred from their names, and missing geometry must not
remove a factual city from the metadata.

Two source spellings resolve at the existing prefecture recommendation granularity:
临夏州 → 临夏回族自治州 (abbreviation), 昆山市 → 苏州市 (county-level city).
`SchoolCityIndex` applies these exact mappings at comparison time.

The bundled snapshot now replaces the Taiwan-wide outline with 22 county/city
boundaries from the National Land Surveying and Mapping Center dataset (1140318).
Its pinned CRS84 mirror, checksum, license, transformation limitations, and full
county names are recorded in `scripts/data/boundaries/taiwan-counties-1140318.source.json`
and the generated `school_city_boundaries.source.json`. The original SHP download
returned HTTP 403 during collection; the mirror conversion has not been independently
compared with the SHP vertices. City/county results include disconnected islands;
supplemental county polygons take precedence over coarse mainland outlines that
include offshore islands. Smaller county exteriors take precedence over enclosing
ones because the mirror omits the Taipei hole from New Taipei. The runtime continues rejecting `台湾省` if an older province-only asset is supplied.

The remaining explicit limitation is 新星市: the mainland snapshot lacks its own
polygon. 新星职业技术学院 retains its factual city and remains searchable, but
accurate offline city geolocation there is unsupported. Older neighboring polygons
may cover portions of that area; an official boundary is needed to correct this.

The contract test requires every configured city to resolve to a normalized bundled
name or an exact documented exclusion, and rejects stale exclusions. Coordinate
regressions cover Taiwan municipalities/counties/islands, Hong Kong, Macau, Kunshan,
and a synthetic old province-only asset that must never produce a city.

Maintenance: ✅保留事实城市·✅逐名审计边界缺口·✗省级轮廓冒充城市·✗按缺失列表自动放行

## 2026-10-15 official-preview follow-up

The TianDiTu public administrative-division preview now contains 新星市 (156659011)
in its Xinjiang province-level FeatureCollection. Its direct city preview is empty.
The saved official interface states that the September 2025 data are only for map
visualization; the inspected primary-source pages do not state the coordinate CRS.
The preview therefore remains research-only and is not relabelled WGS84 or bundled
for offline point-in-polygon geolocation. This narrows the gap to a compatible,
authorized analytical source rather than claiming no public polygon exists.
Hashes and exact endpoints are frozen in
research/school-expansion-20261014/boundary-resume/official-xinxing-preview-review.json
under the local research root.
