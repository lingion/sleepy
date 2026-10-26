# SCAU parser scope

This PR handles three HTML shapes, each selected by its actual container: the pinned greyovo Element UI timetable (`.el-table__body-wrapper table.el-table__body`), WakeUp's `table[border=1]` course grid, and WakeUp's older `table[border=1][bordercolor=#000000]` row-pair grid. It registers `scau` in the production parser registry. A captured DOM or already supplied HTML is the input; no universal endpoint is asserted.

The primary external sample is greyovo/ScheduleXParser_SCAU at the pinned blob `953a4bf5c5954da08640a3fa2dd129fb0c0c8ec4` (`src/main/resources/schedule_sample_scau.html`). The Java parser's pinned tree is `365c249ac65659396775e84a056c284b6a256adb`. WakeUp evidence was read from local decompiled `SCAUParser.smali` and `o00OOO00.smali`; reverse-engineered material is used as protocol evidence, not assigned a license by inference.
