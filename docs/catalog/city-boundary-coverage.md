# City boundary coverage

The school directory and factual city metadata cover more locations than the bundled
offline polygon snapshot. Keep their scopes separate. The 3,164 city records remain
unchanged; missing geometry must never delete a factual location.

The full city-name audit found 24 unmatched names. Two resolve at the existing
prefecture recommendation granularity: 临夏州 → 临夏回族自治州 (abbreviation),
昆山市 → 苏州市 (county-level city within the prefecture). `SchoolCityIndex` applies
these exact mappings at comparison time and retains source spellings.

The remaining 22 locations are explicitly declared in
`OfflineSchoolCityIndex.unsupportedCityReasons`:

- Taiwan: 南投縣、嘉義市、嘉義縣、基隆市、宜蘭縣、屏東縣、彰化縣、新北市、
  新竹市、新竹縣、桃園市、澎湖縣、臺中市、臺北市、臺南市、臺東縣、花蓮縣、
  苗栗縣、金門縣、雲林縣、高雄市. The snapshot only has a Taiwan-wide outline,
  so it cannot locate these 149 schools at city/county granularity. The runtime
  excludes that province outline from city results rather than treating it as a city.
- 新星市: the snapshot lacks its own polygon; 新星职业技术学院 retains its factual
  city and remains searchable. Accurate offline city geolocation there is unsupported;
  older neighboring polygons may cover parts of this area.

Hong Kong and Macau polygons are present and representative coordinate lookups pass.
The contract test requires every configured city to resolve to a normalized bundled
name or an exact documented exclusion, and rejects stale exclusions. Future unknown
names still fail the test. Province-outline, Hong Kong, Macau, and Kunshan coordinate
regressions guard the runtime behavior.

Maintenance: ✅保留事实城市·✅逐名审计边界缺口·✗省级轮廓冒充城市·✗按缺失列表自动放行
