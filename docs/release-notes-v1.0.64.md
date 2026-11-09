# Sleepy v1.0.64

> Liquid Glass controls, timetable editing, wide widgets, school collection and source metadata.

## What's New

### Appearance and navigation

<a id="en-ui-style"></a>

- **Liquid Glass across small controls**. Choose the app-wide UI style separately from light/dark mode, theme colors and bottom-navigation shape. Liquid Glass covers small icon actions, long action buttons, segmented choices, switches, sliders, menus, dialogs, date selectors, the import web toolbar and widget-setting controls. Existing glass-dock preferences retain their meaning. Large navigation rows and content cards keep their own page hierarchy.

  Evidence: PR #299, PR #366.

<a id="en-glass-switch"></a>

- **A floating switch lens**. The switch uses a complete transparent floating lens that grows beyond its track, refracts the scene and follows the finger. The background is sampled at the actual lens size, so raising the thumb does not merely enlarge a texture captured at track height. Track color, selected state, disabled state and release snapping remain distinct.

  Evidence: PR #299, PR #378.

<a id="en-glass-tabs"></a>

- **Raised tabs with magnified labels**. A pressed tab rises from a 42dp track into a 52dp lens, extending above and below the track; the resting raised lens is 16dp wider than its segment. Its independent content lens magnifies labels by 16%. Dragging follows the selected segment with elastic deformation, selection haptics and spring settling on release.

  Evidence: PR #299, PR #378.

<a id="en-glass-slider"></a>

- **Colored precision sliders**. The Liquid Glass slider has its own optical thumb: a colored 16×24dp vertical oval at rest and a transparent 28×40dp lens while held, above a continuous 4dp track. Slider and range-slider editing retains integer snapping, range limits, original-value ghost marks and separate overlapping endpoints. Glass sliders preserve keyboard steps, accessibility actions, right-to-left direction and disabled behavior. Dragging freezes conflicting parent scrolling; quick hue taps commit the latest color instead of an earlier captured value. Editing controls use the same value path as their optical display.

  Evidence: PR #299, PR #378.

<a id="en-navigation-icons"></a>

- **Distinct motion for each navigation icon**. Navigation icons animate their individual parts across the Classic floating dock, Glass dock, attached navigation bar and tablet rail. The timetable keeps its calendar frame still while six date cells pop in sequence; Today keeps the frame still while its center square pops; the management gear rotates its outer ring and pulses its core; Mine keeps the body still while the head sways and settles. Shared phase keeps the icon and its lens copy synchronized during rapid switching.

  Evidence: PR #333, PR #392.

### Courses, settings and reminders

<a id="en-period-card-collapse"></a>

- **Collapsible course-period cards**. Course-period cards are collapsed by default. Entering from a timetable occurrence expands the matching card automatically; a new course’s first card and newly added cards start expanded. Collapsed cards keep their period summary and validation errors visible.

  Evidence: PR #368.

<a id="en-occurrence-room"></a>

- **Change one occurrence’s room**. A single-occurrence adjustment can change the room for that occurrence only. An untouched room keeps its exact source text so compatible rows can merge again; edited room input is trimmed. A stale concurrent edit is refused instead of silently replacing newer data.

  Evidence: PR #328 (contributor @Kerry1020).

<a id="en-table-copy-name"></a>

- **Name a timetable before copying**. Copying a timetable opens an editable name suggestion before creating it. Empty or duplicate names are rejected, cancellation creates nothing and repeated submission is blocked. A successful copy gets separate course groups and remains undoable.

  Evidence: PR #329 (contributor @jim139129).

<a id="en-settings-map"></a>

- **Settings organized by purpose**. Mine groups timetable/period/holiday management, reminders/appearance/general/widgets, backup and restore, and About. Appearance separates UI style from color and navigation shape, and opens timetable display options on their own page. General groups language, high-refresh performance and undo/editing behavior; export stays in timetable management. Existing options keep their behavior and scope. Timetable display groups common options, week behavior, the grid, period headers, conflicts and holiday appearance. Its preview precedes detailed controls, and hiding time also hides time-alignment controls. Global widget settings and per-widget instance settings keep their separate scopes, including vertical text punctuation.

  Evidence: PR #320.

<a id="en-reminder-scope"></a>

- **Explicit reminder timetable selection**. Until you choose a manual set, reminders follow only the currently selected timetable. The first manual toggle saves the entire chosen set; switching timetables no longer overwrites it, disabling all stays disabled, and newly created/copied/imported tables are not added automatically. Before-class reminders, daily summaries and older alarm delivery use the same scope.

  Evidence: PR #320.

### Desktop widgets

<a id="en-widget-wide"></a>

- **A separate 4×2 grid widget**. The widget gallery adds a separate 4×2 grid entry with three display modes: the original complete grid, a vertical rolling grid and a horizontal three-day grid. Existing 2×2 and 4×4 widget entries remain available.

  Evidence: PR #295.

<a id="en-widget-vertical"></a>

- **A rolling seven-day period window**. The default wide-grid mode shows seven days without a weekday-header row and fits at least four complete periods. Its visible period window advances after each period ends and stops at the last complete window. A long course crossing the window boundary keeps the visible part and reflows its title and room into that space.

  Evidence: PR #295.

<a id="en-widget-horizontal"></a>

- **A horizontal three-day grid**. The horizontal wide-grid mode shows today, tomorrow and the day after, with time/period labels along the top and weekday labels selectable on the left or right. Conflict lanes, course aliases/colors, custom times, fixed dates and makeup-day assignments remain available in all three modes.

  Evidence: PR #295.

<a id="en-widget-previews"></a>

- **Inline instance controls and real previews**. The widget instance editor selects grid mode and label side inline and previews the table actually bound to that instance. Preview subscriptions cancel outdated work and stop when the page stops. The widget gallery groups five families into accordions, expands one family at a time, labels large/medium/small variants and supports pinning.

  Evidence: PR #295, PR #390.

### School selection and reported imports

<a id="en-jw-recent-schools"></a>

- **Recently imported schools**. Show up to three recently imported schools above the school suggestions, with the latest successful import first. Only successful new, append or manual-update imports enter the list. Searching hides it; removed catalog entries disappear.

  Evidence: PR #372 (contributor @YYiChen).

### Academic systems, collection and data

<a id="en-jw-manual-update-preview"></a>

- **Review school timetable updates**. Update an existing timetable from its academic system and review added, changed and removed courses before applying it. Updates are initiated manually; there is no automatic background synchronization.

  Evidence: PR #325 (contributor @Sunrongguo2008).

<a id="en-jw-manual-update-matching"></a>

- **Keep matched course customizations**. Keep matched course identities, aliases, notes, colors and custom times when refreshing school arrangements. Exact occurrence matches take priority; a name/day fallback must be unique in both directions. Removed or ambiguous courses do not guarantee preservation of local fields.

  Evidence: PR #325 (contributor @Sunrongguo2008).

<a id="en-jw-manual-update-groups"></a>

- **Keep timetable settings during updates**. Apply approved updates atomically with undo support, retain timetable settings and reuse a uniquely matched course group's alias and color for new rows. New rows do not inherit row-specific notes or custom times. Manual update has its own period-handling path and may extend a bound shared period table.

  Evidence: PR #325 (contributor @Sunrongguo2008).

<a id="en-jw-db15"></a>

- **Database version 15 source metadata**. Upgrading preserves existing timetables while adding empty-default source metadata in database version 15. Existing version-13 data passes through version 14, which stores the nullable reminder override, before the version-15 source-field migration.

  Evidence: PR #377.

<a id="en-jw-source-presence"></a>

- **Presence-aware SG source suggestions**. Keep SG source configuration and adapter identity separately from confirmed timetable values, preserving the difference between a missing field and an explicit null start-date suggestion. Integer/date values are validated. First-day-of-week is retained as metadata only.

  Evidence: PR #379.

<a id="en-jw-source-draft-backup"></a>

- **Source metadata in drafts and backups**. Carry source metadata through editable import drafts, timetable conversion and complete backup restore. Older drafts and backups without this field remain readable.

  Evidence: PR #379.

<a id="en-jw-source-json-share"></a>

- **Source metadata in JSON and sharing**. Carry importSourceJson through the wired WakeUP-compatible JSON export/import and share payload paths. This applies to the wired JSON and sharing paths, not every export format. Appending does not replace the target timetable’s provenance.

  Evidence: PR #380.

<a id="en-jw-source-native"></a>

- **Native source field and original CRC**. Export an escaped source field in Sleepy native text, restore it before course-text normalization and calculate its checksum from the original payload characters. Original source characters and CRC input are retained; reserialized metadata JSON need not preserve whitespace or key order byte for byte.

  Evidence: PR #380.

<a id="en-jw-sg-entry-choice"></a>

- **Choose a Shiguang adapter entry**. Offer all declared Shiguang entries, including entries sharing one script but using different URLs, and open the chosen import URL. Source order is retained. An obsolete saved ID does not silently select another entry.

  Evidence: PR #364, PR #382.

<a id="en-jw-sg-native-interactions"></a>

- **Shiguang native interactions**. Show script alerts, prompts and single-choice requests as native interactions and resume the matching request after the user's action. Validation accepts only a false result; returned error strings remain visible. Cancellation and stale session/request/attempt IDs stay isolated. showToast remains a no-op.

  Evidence: PR #381, PR #382.

<a id="en-jw-sg-finalization"></a>

- **Complete Shiguang imports once**. Stage Shiguang courses until notifyTaskCompletion and include the finalized period/configuration data in the single import result. Pending interactions, missing courses or earlier metadata errors do not become partial-success imports.

  Evidence: PR #382.

<a id="en-jw-sg-confirm-weeks"></a>

- **Confirm source semester weeks**. Edit total semester weeks during SG import confirmation and preserve the entered text in an existing recoverable draft. The confirmed value must cover the last course week and stay within 366. Invalid raw input such as 367 remains editable; only an existing draft is checkpointed, asynchronously.

  Evidence: PR #364, PR #387.

<a id="en-jw-sg-source-card"></a>

- **Review source suggestions**. Show the selected source entry and supplied class duration, break duration and first weekday below the timetable name, before the period editor. The order is semester start, total weeks, timetable name, source suggestions, then periods. The first-weekday suggestion does not change Monday-based calendar calculations.

  Evidence: PR #364, PR #387.

### Performance and local tools

<a id="en-performance-grid"></a>

- **Less repeated timetable work**. Today and timetable projections share recent-week results and invalidate them when their real inputs change. Entering a timetable prepares the current week first and prepares neighbors during a drag. Stable table keys, grouped course indexes, bounded font measurement, grid geometry, meal-break prefixes and preference/color caches reduce repeated parsing and recomposition. Parsed period rows, validation and display results are also reused while the source JSON is unchanged. These caches hold up to 16 entries each; inputs longer than 32768 characters are parsed without retention.

  Evidence: PR #307 (contributor @Kerry1020), PR #316, PR #317, PR #318, PR #332 (contributor @jim139129), PR #400.

<a id="en-performance-idle"></a>

- **Glass rendering sleeps when idle**. The Dock stops requesting animation frames when settled and resumes with a fresh frame time when motion starts. Timetable course/conflict leaves no longer register redundant glass scene sources, and holiday-gray rendering no longer triggers a second asynchronous page-turn update.

  Evidence: PR #318, PR #397.

<a id="en-performance-background"></a>

- **Background startup and import work**. Startup cleanup, widget-scheduler registration, file I/O, import parsing and holiday-gray computation run on the appropriate background paths. Startup still cancels old notifications before restoring reminders and initializes undo in the intended order. Import files are read on IO threads and parsed on background computation threads before returning to the import UI. No measured speed multiplier is claimed.

  Evidence: PR #319.

<a id="en-debug-delivery"></a>

- **Traceable official debug downloads**. Branch and pull-request debug builds can combine the requested source with the latest main branch and record the actual source revision separately from the workflow revision. Manual builds select architectures; the original build page exposes four official-signed ABI choices. Run-identity checks and a final signed-artifact recheck protect download traceability.

  Evidence: PR #290, PR #292, PR #306 (contributor @Kerry1020), PR #308 (contributor @Kerry1020), PR #313 (contributor @Kerry1020), PR #369.

### Sources and acknowledgments

<a id="en-attributions"></a>

- **Expanded in-app sources and contributor records**. About retains a complete contributor-history entry. Referenced projects are organized into collapsible source groups with full notices where supplied, and the academic-system research acknowledgments include the newly reviewed repositories. Attribution manifests and six language resource sets are checked together. Academic protocol research notices retain each repository’s own authorship and license evidence. PR #395 adds 49 identities and expands one existing record; the 50 reviewed identities include 38 forks.

  Evidence: PR #310, PR #311, PR #361, PR #395.

## Fixes

### Toolbar, held-button gestures and widget colors

<a id="en-glass-free-drag"></a>

- **Small glass buttons follow held drags in every direction**. In Liquid Glass mode, held icon buttons and long action buttons follow horizontal, vertical and diagonal drags with elastic deformation, including movement beyond their resting click bounds. Releasing the finger springs the pane back into place. Cancelling a drag does not activate the button. A disabled half of a shared capsule cannot lift the whole pane; an enabled half retains its optical feedback.

  Evidence: PR #403.

<a id="en-classic-toolbar-fill"></a>

- **Classic toolbar icons keep their filled circles**. Timetable toolbar icons in Classic mode retain their Material theme container-colored circles in light and dark themes, including compact layouts. The visual circle, icon and separate touch slot scale together as space narrows.

  Evidence: PR #404.

<a id="en-toolbar-responsive"></a>

- **Week navigation stays exactly centered on one row**. The week controls stay at the exact horizontal center of the whole toolbar, including right-to-left layouts. Space tightens in order: reduce gaps on both sides, then shrink side controls. Ordinary layouts retain three independent buttons: previous arrow, the full week label such as “Week 2”, and next arrow. Only extreme widths use the combined [left | 2 | right] capsule; each segment keeps its own action. Still narrower layouts scale the complete controls together, with fractional-pixel placement keeping the center exact. Both forms stay on one row without overlap. The accessibility description also includes the semester state.

  Evidence: PR #386, PR #393, PR #404.

<a id="en-widget-uniform-color"></a>

- **Grid widgets use the same uniform course color as the app**. Selecting a uniform course background gives regular grids, compact color bands and wide vertical or horizontal grids the same Material theme color as the in-app course cards. The normal fill is opaque; greyed courses use 60% opacity, matching the app. Instance-setting previews that share these renderers show the same result.

  Evidence: PR #405.

### Appearance and navigation

<a id="en-glass-springs"></a>

- **Gradual lift, jelly motion and rainbow edges**. Switches, tabs and sliders share the Dock’s lift, follow, travel and settling springs. Movement stretches the lens and braking lets it recover elastically. A short tap completes the lift before the lens lowers; there is no fixed pause standing in for that motion. Track and content lenses retain rainbow edge dispersion.

  Evidence: PR #378.

<a id="en-glass-scene"></a>

- **Correct scene sampling and foreground layers**. Glass surfaces sample the ordinary scene separately from optical controls, including nested pages and popup windows. Dialogs and menus can sample their parent scene, and embedded Android views invalidate the sampled content when it changes. This keeps optical panes from sampling themselves and preserves their foreground content. Popup menus keep their placement, first option and foreground labels under the glass layer. Narrow language menus retain complete titles. Shared placement layers avoid clipping or dropping menu content, while half sheets keep the intended rounded outline and visible controls.

  Evidence: PR #299, PR #399.

<a id="en-glass-compatibility"></a>

- **Glass compatibility, Dock dragging and theme colors**. Older Android versions use the available backdrop, blur and edge fallback paths without unsupported content-shader uniforms or black blocks. Bottom-navigation color follows the selected app theme immediately, including light/dark and custom-theme changes. Android 13 and later retain the shader refraction path. The glass Dock follows continuous dragging without repeated stutters; its light-mode base and shadow layers are adjusted together.

  Evidence: PR #278 (contributor @jim139129), PR #283 (contributor @Kerry1020), PR #285 (contributor @Cold577), PR #294 (contributor @Kerry1020).

<a id="en-glass-control-layout"></a>

- **Room around resting glass controls**. Ordinary glass icon buttons separate a 36dp visual disc from their 48dp touch target. Course actions, confirmation buttons, destructive actions and dense text controls use explicit gaps instead of touching or overlapping at rest. The optical material remains separate from click semantics and callback ownership. Period/time editing, week selection, add/remove actions, conflict controls, course detail sheets, import details and timetable action cards receive consistent button slots and text spacing. Course-color actions and color dialogs retain enough room for their labels and optical panes. Theme previews, period-header controls, holiday actions, refresh actions, reminder banners and the widget gallery use consistent spacing. Settings cards allow a raised segmented lens to extend beyond its track with its horizontal edges intact.

  Evidence: PR #383, PR #384, PR #385, PR #390, PR #394, PR #398 (contributor @jim139129).

<a id="en-glass-plain-rows"></a>

- **Clear hierarchy for ordinary list entries**. Reminder permission-diagnostic rows and timetable-switcher rows use ordinary full-row click targets. The timetable-switcher dialog keeps its glass backing while each row retains clear selected-state color and text hierarchy.

  Evidence: PR #366.

<a id="en-navigation-transitions"></a>

- **Opaque page and tab transitions**. Theme-backed page scaling removes white edge gaps and parent/child text ghosting, including predictive return. Phone tabs and tablet detail panes stay opaque during transitions; quick reverse switches restore the correct page state.

  Evidence: PR #301 (contributor @Cold577), PR #332 (contributor @jim139129).

<a id="en-navigation-insets"></a>

- **Content clears system navigation and the dock**. Landscape dual-pane layouts respect safe drawing insets around three-button system navigation. Bottom content padding includes the floating dock’s full height, gap and system-navigation inset in both Classic and Glass styles, keeping lower entries such as About visible and clickable.

  Evidence: PR #293, PR #335 (contributor @jim139129).

### Courses, settings and reminders

<a id="en-period-card-actions"></a>

- **Period-card action order**. The period header reads title/summary, delete, then collapse chevron. The delete cross sits immediately to the left of the chevron on the right side; the last remaining period cannot be removed. Header controls share consistent size and spacing at narrow widths and large font scales.

  Evidence: PR #368, PR #384.

<a id="en-pinch-anchor"></a>

- **Pinch zoom stays under the fingers**. Timetable pinch zoom anchors to the midpoint of both fingers and adjusts scroll together with row height, including long breaks and weighted rows. Pinching the header keeps its top anchor, and starting a pinch stops the previous fling.

  Evidence: PR #287 (contributor @YYiChen).

<a id="en-course-drag"></a>

- **Continuous course dragging across weeks**. Lifted courses follow the finger continuously. Week paging checks deliberate edge entry and movement direction, and the drag gesture remains active across the actual week transition instead of restarting or turning a page accidentally.

  Evidence: PR #288.

<a id="en-course-lift-blur"></a>

- **A complete lifted-course backdrop**. A stationary lifted course restores a 20dp Gaussian backdrop blur with a light mask on Android 12 and later; older versions keep a darkened fallback. The effect covers the full rounded timetable and padding, disappears when dragging begins and fades on normal dismissal.

  Evidence: PR #315.

<a id="en-course-noop-drop"></a>

- **Dropping a course back in place**. Returning a course to the same week, weekday and period span ends the drag immediately without a database write, confirmation flash, undo entry or cleared redo history. Custom times, fixed-date occurrences and holiday semantics remain intact; real moves, resizing and cross-week changes still follow confirmation.

  Evidence: PR #322, PR #332 (contributor @jim139129).

<a id="en-settings-typography"></a>

- **Readable compact settings rows**. Settings use consistent 14sp text roles, aligned titles/values/actions, compact toggles and sliders, an 8dp caption gap and a 48dp collapsed row. Long text, narrow widths and large fonts can wrap or use vertical radio choices without leaving empty gaps or obscuring controls.

  Evidence: PR #344 (contributor @jim139129).

<a id="en-fluid-current-table"></a>

- **Live cards follow the selected timetable**. Fluid Cloud and live-card notifications use only the current selected timetable and reconcile immediately after switching. Creating or importing another table does not steal the current/default selection, except when there was no table before.

  Evidence: PR #291.

<a id="en-reminder-permissions"></a>

- **Independent permission diagnostics**. Reminder permission diagnostics remain available independently of Fluid Cloud. Granting permission does not enable reminders automatically. Permission and background-running status rows are compact while the whole row stays clickable.

  Evidence: PR #320, PR #324 (contributor @Cold577).

<a id="en-holiday-cache"></a>

- **Stable holiday-settings entry**. Holiday settings use the memory cache on the first frame, including a cached empty year, and reset loading state when the year changes. Disk reads and JSON parsing run off the UI thread; refresh keeps the existing list visible. Stable 48dp action slots and cached date/makeup selections keep the layout from jumping.

  Evidence: PR #398 (contributor @jim139129).

### School selection and reported imports

<a id="en-jw-catalog-bupt"></a>

- **BUPT entry grouping**. Place both BUPT new academic-system VPN entries under B while retaining their catalog identities.

  Evidence: PR #280 (contributor @jim139129).

<a id="en-jw-catalog-city"></a>

- **Nearby school order**. Sort same-city school recommendations by their pinyin initial and dictionary order.

  Evidence: PR #296.

<a id="en-jw-catalog-qdu"></a>

- **Qingdao University status**. Mark Qingdao University's existing Shiguang entry as supported following a successful import report dated November 5, 2026. This reuses the existing adapter and the reported import result.

  Evidence: PR #326 (contributor @Sunrongguo2008).

<a id="en-jw-catalog-ntit"></a>

- **NTIT cohort-specific status**. Mark the existing NTIT entry labelled “2023届及之后” as supported after a reported 42-course import. This applies only to that catalog entry.

  Evidence: PR #327 (contributor @Kerry1020).

<a id="en-jw-catalog-jxstnu"></a>

- **JXSTNU import status**. Mark the existing JXSTNU entry as supported after a 24-course import reported on November 7, 2026. The existing zf_new URL and capture setting are retained.

  Evidence: PR #331 (contributor @Kerry1020).

### Academic systems, collection and data

These entries describe changes to the declared parser and collection routes. Offline fixtures and script wiring do not certify a live school login. Only the separately listed Qingdao, NTIT and JXSTNU catalog updates rely on reported successful imports. Ambiguous names retain their source identifiers.

<a id="en-jw-period-clock-center"></a>

- **Centered period-time fields**. In timetable editing, compact start and end time fields center both the label and the clock value horizontally and vertically. Each label and time stays on one line.

  Evidence: PR #402 (contributor @jim139129).

<a id="en-jw-period-no-reorder"></a>

- **Scroll without reordering period rows**. In timetable editing, drag the page to scroll while period rows keep their current order. Long-press row reordering is disabled; adding or removing rows and changing their times remain available.

  Evidence: PR #402 (contributor @jim139129).

<a id="en-jw-period-validation"></a>

- **Validate period clocks**. Validate minute clocks, unique period numbers, end-after-start and adjacent period order before saving a period table. Clocks use HH:mm. The editor does not guess AM/PM, invent durations or introduce a cross-midnight model. Legitimate early times and existing edge-period identities remain valid.

  Evidence: PR #289 (contributor @Kerry1020).

<a id="en-jw-period-display"></a>

- **Keep invalid period data diagnosable**. Keep courses visible with their period labels when clock data needs correction, and show the reason in the period editor. Display cleanup does not rewrite stored clocks.

  Evidence: PR #289 (contributor @Kerry1020).

<a id="en-jw-today-all-courses"></a>

- **Show every course in today's lanes**. Show all courses sharing a Today lane and use the same validated clock interpretation for widget grouping, timing and drawing.

  Evidence: PR #289 (contributor @Kerry1020).

<a id="en-jw-backup-reminders"></a>

- **Preserve reminder choices in backups**. Preserve each timetable's reminder choices during full backup restore, and retain local reminder selection when merging a backup into existing data. Older backups remain readable. When merging into existing data, imported tables do not replace the local default or join its reminder selection automatically.

  Evidence: PR #320.

<a id="en-jw-period-edit-draft"></a>

- **Keep edits after holiday settings**. Keep unsaved timetable edits when visiting holiday settings, then recheck the shared-period decision before saving restored edits. Saving still requires the shared-period confirmation before database changes.

  Evidence: PR #320.

<a id="en-jw-source-period-rows"></a>

- **Keep the full source period table**. Keep unused trailing source periods in import confirmation and include every row needed by the highest course or source period. Missing source clocks remain blank for confirmation.

  Evidence: PR #330, PR #346.

<a id="en-jw-import-node-coverage"></a>

- **Keep high-period courses visible after import**. Check that every nominal course period has a corresponding period row during new-timetable and append confirmation and database writes. Actual-clock courses are exempt. Append checks accepted courses after conflict filtering and cannot silently extend a bound period table; manual update uses a separate path.

  Evidence: PR #349.

<a id="en-jw-declared-route"></a>

- **Respect declared school layouts**. Use the declared layout's row and week conventions when several school pages look alike. An empty declared result remains empty instead of switching to an unrelated parser.

  Evidence: PR #330.

<a id="en-jw-week-occurrences"></a>

- **Preserve actual teaching occurrences**. Keep gaps and odd/even patterns in explicit weeks and periods, while merging only arrangements with matching course fields. Each source keeps its documented defaults; missing fields do not share one universal policy.

  Evidence: PR #330.

<a id="en-jw-kingo"></a>

- **Kingo arrangement fields**. Keep compact-grid positions, multiple courses per cell, parenthesized names, separate teachers and rooms, credits and explicit high periods in the supported Kingo layouts.

  Evidence: PR #330, PR #336.

<a id="en-jw-classic-eams-fields"></a>

- **Quoted Classic EAMS fields**. Read single- or double-quoted course fields with commas, parentheses and escaped characters, decoding the source string once.

  Evidence: PR #330, PR #337.

<a id="en-jw-classic-eams-occurrences"></a>

- **Classic EAMS coordinates**. Keep an activity's separate weekdays, disconnected period groups and sparse week bitmap, including valid courses with an empty teacher.

  Evidence: PR #330.

<a id="en-jw-qz-layouts"></a>

- **QZ layout coordinates**. Use titled QZ fields and declared school row maps, retain sparse weeks and periods, and validate explicit range endpoints before importing arrangements. The declared maps include qz_single_node, qz_njust, qz_wit, qz_ecust, qz_ahut, qz_bjfu and qz_ustb. Invalid or overflowing endpoints do not become invented single-period courses.

  Evidence: PR #330, PR #348, PR #365.

<a id="en-jw-old-qz"></a>

- **Declared legacy QZ forms**. Read JLICT, CCUT and CCSU legacy QZ forms with their own arrangement boundaries and explicit period coordinates.

  Evidence: PR #330, PR #352.

<a id="en-jw-urp"></a>

- **Legacy URP physical rows**. Read short and long URP headers, rowspans and Chinese period labels; keep each postgraduate bracketed arrangement's weeks and parity. Compressed continuation rows inherit only evidenced metadata. Unknown weeks are not expanded into an assumed 16- or 20-week semester.

  Evidence: PR #330.

<a id="en-jw-new-urp"></a>

- **New URP bitmap and name fields**. Keep sparse New URP week bitmaps and field aliases, including the nested SHOU coureName fallback.

  Evidence: PR #330, PR #365.

<a id="en-jw-zf-old"></a>

- **Legacy ZF course fields**. Read the supported table, class-table, paragraph, font and tooltip layouts without mixing credits, enrollment notes or course properties into course names.

  Evidence: PR #330.

<a id="en-jw-zf-new"></a>

- **New ZF sparse weeks and high periods**. Retain New ZF's explicit high periods, sparse weeks and weekday coordinates, including second-font grid fields. A missing range end may use the documented single-period form; a supplied invalid end is not treated as missing.

  Evidence: PR #330, PR #352, PR #365.

<a id="en-jw-cf-weeks"></a>

- **CF requested weeks and occurrences**. Capture the page's explicit CF week choices, including sparse and late weeks, and retain each returned arrangement's own periods and clocks. Explicit week requests require one complete response bucket per requested week. Legacy response envelopes remain readable.

  Evidence: PR #330, PR #336.

<a id="en-jw-jinzhi-order"></a>

- **Jinzhi mixed-page selection**. Keep fields local to each Jinzhi layout and stop at the first productive source branch on pages containing several parallel representations. Parallel representations are not combined into duplicate schedules.

  Evidence: PR #330, PR #343.

<a id="en-jw-umooc"></a>

- **UMOOC excluded weeks and explicit nodes**. Subtract UMOOC excluded weeks from the occurrence set and keep explicit high periods declared by headers or cells. Gaps and parity remain intact; malformed text does not create default weeks.

  Evidence: PR #330, PR #354.

<a id="en-jw-element"></a>

- **Element table periods**. Keep explicit high periods and per-course fields from Element timetable wrappers.

  Evidence: PR #330, PR #355.

<a id="en-jw-suda-post"></a>

- **SUDA postgraduate rowspans**. Keep the period span of SUDA postgraduate courses and preserve physical columns when a neighboring rowspan is invalid. Invalid spans reserve their physical columns but do not generate courses.

  Evidence: PR #330, PR #356.

<a id="en-jw-xbellsoft"></a>

- **Xbellsoft arrangements**. Keep Xbellsoft's explicit integer coordinates, parity, high periods and separate teacher/room fields; merge weeks only for identical arrangements.

  Evidence: PR #330, PR #342.

<a id="en-jw-ecupl-clocks"></a>

- **ECUPL per-period clocks**. Keep per-period source clocks from ECUPL annual tables and script matrices, with captured clocks taking priority. Partially supplied clock columns are not completed with invented times.

  Evidence: PR #330, PR #338.

<a id="en-jw-ecupl-diagnostics"></a>

- **ECUPL fallback diagnostics**. Retain the original ECUPL parse reason through fallback attempts so import diagnostics identify the failed layout.

  Evidence: PR #339.

<a id="en-jw-nwpu-post"></a>

- **NWPU postgraduate source context**. Keep the selected semester name and thirteen source periods for NWPU postgraduate imports, with campus clock defaults selected from the timetable's time cells. Each row contributes one campus time cell; ties choose Chang’an. Youyi uses the declared May–September summer defaults. Captured clocks take priority, and absent semester fields do not create a date or name.

  Evidence: PR #330, PR #345.

<a id="en-jw-scau"></a>

- **SCAU table variants**. Keep SCAU old and other declared layouts' packed periods, row offsets and explicit high nodes.

  Evidence: PR #330, PR #336, PR #353.

<a id="en-jw-nuist"></a>

- **NUIST two-period row labels**. Treat both halfwidth and fullwidth tilde row labels as two-period NUIST rows and keep later rows correctly positioned when a course omits its packed period code.

  Evidence: PR #330, PR #347.

<a id="en-jw-whut"></a>

- **WHUT dynamic period maps**. Keep high periods from WHUT's dynamic mapping and apply the mapping once to each course arrangement.

  Evidence: PR #330, PR #358.

<a id="en-jw-ucas"></a>

- **UCAS complete details and high periods**. Match UCAS detail pages by course identity, retain HTML/text high periods and import only a complete detail capture. All required detail pages must complete. There are no placeholder weeks or partial-success schedules; the existing 12-bit JSON contract remains separate.

  Evidence: PR #330, PR #359.

<a id="en-jw-ztvtit"></a>

- **ZTVTIT lesson coordinates**. Read ZTVTIT weekday attributes and lesson coordinates independently of DOM order, including explicit high periods.

  Evidence: PR #330, PR #357.

<a id="en-jw-stum"></a>

- **STUM explicit periods**. Keep explicitly declared high periods in the STUM mobile timetable layout. STUM is a layout identifier.

  Evidence: PR #330, PR #357.

<a id="en-jw-ahmu"></a>

- **AHMU explicit arrangement fields**. Read AHMU qsz/jsz/skcd fields as explicit arrangement coordinates and retain valid high periods. Fractional or overflowing coordinates are not silently converted.

  Evidence: PR #330, PR #340.

<a id="en-jw-aic"></a>

- **AIC semester arrangements**. Interpret AIC semester-table arrangements and retain their explicit high periods and week details. AIC is shared by several catalog entries. A missing weekDetail does not imply a full semester.

  Evidence: PR #330, PR #340.

<a id="en-jw-ruc"></a>

- **RUC metadata and sparse periods**. Keep RUC timetable metadata, sparse course periods and explicit high nodes.

  Evidence: PR #330, PR #341.

<a id="en-jw-sdu"></a>

- **SDU fixed-column arrangements**. Read SDU fixed-column arrangements with their own fields and explicit high periods.

  Evidence: PR #330, PR #341.

<a id="en-jw-hbmzu"></a>

- **HBMZU grid positions**. Keep HBMZU grid day and two-period row positions, including explicit high periods. HBMZU is retained as a route identifier; its current catalog usage includes 恩施职业技术学院.

  Evidence: PR #330, PR #341.

<a id="en-jw-hit"></a>

- **HIT blank teachers and high nodes**. Keep valid HIT courses with an empty teacher, the eight-column day layout and explicit high periods.

  Evidence: PR #330, PR #342.

<a id="en-jw-seig"></a>

- **SEIG course blocks**. Keep SEIG row order, the course name before the final arrangement parentheses and explicit high periods.

  Evidence: PR #330, PR #351.

<a id="en-jw-simc"></a>

- **SIMC timetable offsets**. Keep SIMC period offsets, repeated week bitmaps, remapped nodes and the declared extended/lunch clock intervals. The related institutional labels remain separate in the catalog.

  Evidence: PR #330.

<a id="en-jw-bfa"></a>

- **BFA six-column layout**. Read the declared BFA six-column timetable without shifting course weekdays.

  Evidence: PR #330.

<a id="en-jw-hust"></a>

- **HUST arrangement-local fields**. Keep each HUST arrangement's own teacher, room and target group.

  Evidence: PR #330.

<a id="en-jw-nua"></a>

- **NUA declared layout**. Keep the extra midday period in NUA tables: printed periods five and later shift after midday while each arrangement retains its own fields.

  Evidence: PR #330.

<a id="en-jw-gdei"></a>

- **GDEI per-course overrides**. Keep GDEI overrides local to their course and preserve the room suffix.

  Evidence: PR #330.

<a id="en-jw-jlu-post"></a>

- **JLU postgraduate arrangements**. Read every JLU postgraduate bracketed arrangement with its own room, weeks and parity.

  Evidence: PR #330.

<a id="en-jw-ccibe"></a>

- **CCIBE weekly response identity**. Bind CCIBE occurrences to the requested week and selected semester across the complete response set. Each requested week requires one response bucket, with the selected semester unchanged.

  Evidence: PR #330.

<a id="en-jw-cppu"></a>

- **CPPU absolute dates**. Keep CPPU source dates on Sleepy's Monday-based semester calendar.

  Evidence: PR #330.

<a id="en-jw-shcc"></a>

- **SHCC JSON course names**. Read the complete SHCC JSON array so semicolons inside course names remain part of the name.

  Evidence: PR #330.

<a id="en-jw-fstvc"></a>

- **FSTVC fields and source defaults**. Keep FSTVC serializer fields and notes, require complete pages and select source clock defaults only for the matching school and an unambiguous class batch. A mixed or missing class-batch identity does not select a guessed default timetable.

  Evidence: PR #330.

<a id="en-jw-gdbh"></a>

- **GDBH day and period pairs**. Keep GDBH weekdays and six period pairs distinct across pages, with the declared ten-row source clock table available for confirmation. The ten clock rows do not cap course coordinates at ten.

  Evidence: PR #330.

<a id="en-jw-gliet"></a>

- **GLIET independent course blocks**. Read every GLIET course block, including independent single courses in periods eleven and thirteen.

  Evidence: PR #330.

<a id="en-jw-cnu"></a>

- **CNU curriculum-item layout**. Keep each CNU curriculum item separate and account for its one- or two-column gutter layout. The current catalog CNU label is 长春师范大学.

  Evidence: PR #330.

<a id="en-jw-cdut"></a>

- **CDUT physical grid**. Keep the CDUT weekly grid's physical columns and merged empty-cell reservations without turning room-only text into a course.

  Evidence: PR #330.

<a id="en-jw-gzhu-post"></a>

- **GZHU postgraduate blocks**. Keep GZHU postgraduate blocks independent and preserve physical rowspans and colspans.

  Evidence: PR #330.

<a id="en-jw-dhu"></a>

- **DHU four-field blocks**. Keep DHU four-field arrangements and blank span reservations in their physical positions.

  Evidence: PR #330.

<a id="en-jw-ecjtu"></a>

- **ECJTU empty arrangement fields**. Keep ECJTU three-token arrangements when the teacher or room field is empty, without shifting neighboring fields.

  Evidence: PR #330.

<a id="en-jw-zptc"></a>

- **ZPTC header coordinates**. Use ZPTC header weekdays and period ranges, keeping metadata before the week text separate.

  Evidence: PR #330.

<a id="en-jw-ctgu"></a>

- **CTGU final teacher field**. Keep CTGU's final field as the teacher, including an empty teacher, and preserve separate arrangement containers. Explanatory fields do not create extra courses; ambiguous mixed containers are not imported as invented arrangements.

  Evidence: PR #330.

<a id="en-jw-nyist"></a>

- **NYIST clocks and week parity**. Keep NYIST explicit clocks and independent week parities. Missing clocks do not create week-nineteen courses.

  Evidence: PR #330.

<a id="en-jw-jxau"></a>

- **JXAU empty cells and separators**. Keep empty JXAU period cells and each dl's own fields, skip only the two declared separator columns and retain explicit high periods.

  Evidence: PR #330, PR #341.

<a id="en-jw-hniu"></a>

- **HNIU explicit course nodes**. Keep HNIU bracketed week/period fields and explicit high nodes without shifting teacher and room lines.

  Evidence: PR #330, PR #351.

<a id="en-jw-sustech"></a>

- **SUSTech bracketed arrangements**. Read SUSTech's five bracketed fields, preserving explicit period ranges, teaching weeks, parity and the source note.

  Evidence: PR #330, PR #351.

<a id="en-jw-uic"></a>

- **UIC adjacent single periods**. Keep UIC's adjacent single-period course rows and match teachers from the course lookup table. This declared layout retains its existing weeks 1–20 convention.

  Evidence: PR #330, PR #351.

<a id="en-jw-cityu"></a>

- **CityU rowspan occupancy**. Expire CityU rowspans at their source boundary and retain valid high-period rows without moving later courses to the wrong day.

  Evidence: PR #330, PR #351.

<a id="en-jw-stu"></a>

- **STU Sunday letters and credits**. Keep STU Sunday arrangements, letter-coded periods, credits and week parity. Letter mappings apply only to the declared source layout.

  Evidence: PR #330, PR #351.

<a id="en-jw-nfu"></a>

- **NFU multiple Sunday courses**. Keep multiple NFU courses sharing a Sunday cell and retain explicit high periods.

  Evidence: PR #330, PR #351.

<a id="en-jw-jnu"></a>

- **JNU captured document selection**. Use the captured JNU timetable document after the preceding document and preserve explicit high nodes. The preceding backup document is not merged into the selected timetable.

  Evidence: PR #330, PR #350.

<a id="en-jw-swust"></a>

- **SWUST final seven columns**. Use SWUST's final seven day columns and keep each lecture's week and place fields with its explicit row period. The pinned source has no separate teacher field.

  Evidence: PR #330, PR #350.

<a id="en-jw-jmucy"></a>

- **JMUCY star-separated courses**. Keep star-separated JMUCY course blocks independent, including courses at explicit high periods.

  Evidence: PR #330, PR #350.

<a id="en-jw-nuaajc"></a>

- **NUAAJC server headers**. Read NUAAJC server-rendered headers for course positions and preserve explicit high periods.

  Evidence: PR #330, PR #350.

<a id="en-jw-qmu"></a>

- **QMU course-cell week lists**. Read dot-separated QMU week lists from course cells without treating unrelated cells as courses.

  Evidence: PR #330, PR #350.

<a id="en-jw-javtc"></a>

- **JAVTC multiline titles**. Keep JAVTC multiline course titles and their explicit period coordinates.

  Evidence: PR #330, PR #350.

<a id="en-jw-hitsz"></a>

- **HITSZ laboratory and theory cards**. Read HITSZ laboratory and theory cards as separate arrangements and keep explicit high periods.

  Evidence: PR #330, PR #350.

<a id="en-jw-xhtd-xytc"></a>

- **XHTD and XYTC field groups**. Read all XHTD five-field course groups beyond fifteen blocks and preserve XYTC's declared three-, six-, seven-, eleven- and thirteen-token forms.

  Evidence: PR #330.

<a id="en-jw-cidp-sdpei"></a>

- **CIDP and SDPEI period labels**. Use CIDP circled period-step labels and SDPEI teaching-row labels to retain the source coordinates.

  Evidence: PR #330.

<a id="en-jw-shu"></a>

- **SHU course metadata and clocks**. Keep SHU parity, credits and source clock times with the corresponding course. This is separate from the SHU2024 default clock table.

  Evidence: PR #330.

<a id="en-jw-cqu-html"></a>

- **CQU printed timetable clocks**. Read CQU print-schedule-table HTML with actual course clocks across all seven weekdays. The print-layout HTML marker selects this path; the existing JSON route remains separate.

  Evidence: PR #330.

<a id="en-jw-jmpt"></a>

- **JMPT rowspans and parity**. Expire JMPT day-rowspans correctly and preserve its declared inverse parity semantics. The inverted parity interpretation is specific to this source.

  Evidence: PR #330.

<a id="en-jw-lngd-vatuu-yzzy"></a>

- **LNGD, VATUU and YZZY fields**. Keep LNGD decimal credits, VATUU header offsets and parenthesized teachers, and independent adjacent YZZY course names with their source period defaults. Source defaults apply only to matching declared types.

  Evidence: PR #330.

<a id="en-jw-huat-seu2017"></a>

- **HUAT and SEU2017 fields**. Keep HUAT's declared three-period fifth row and SEU2017 teacher/credit fields.

  Evidence: PR #330.

<a id="en-jw-tju-hqu-changzhou"></a>

- **TJU, HQU and Changzhou arrangements**. Use TJU headers and embedded room fields, HQU's longest unique plan name and transposed coordinates, and Changzhou slash-separated names and teachers. These changes apply to the declared layouts.

  Evidence: PR #330.

<a id="en-jw-shu2024-clocks"></a>

- **SHU2024 source clocks**. Offer the full SHU2024 source period clock table during import confirmation. Captured periods take priority.

  Evidence: PR #330.

<a id="en-jw-sicnu-sit-sysu"></a>

- **SICNU, SIT and SYSU grid semantics**. Limit SICNU name inheritance to valid continuation rows, keep SIT star parity and rowspans, and retain SYSU's Sunday-first colspan groups.

  Evidence: PR #330.

<a id="en-jw-pku"></a>

- **PKU block coordinates**. Read valid positive PKU coordinates without the old arbitrary period cap and reset arrangement state for every block.

  Evidence: PR #330.

<a id="en-jw-sues"></a>

- **SUES room groups and clocks**. Keep SUES room-group arrangements and the actual clock assigned to the fourth period, across the complete API capture.

  Evidence: PR #330.

<a id="en-jw-hfu-aliases"></a>

- **HFU declared field aliases**. Use the three declared HFU-compatible aliases with the corresponding source fields and clock interpretation.

  Evidence: PR #330.

<a id="en-jw-school-table"></a>

- **HBUT, HNJM, LZ, NAU, NJU and CQUPT layouts**. Keep HBUT multiple courses and sparse weeks, HNJM weekdays and source clocks, both LZ forms, NAU dynamic headers and places, NJU card clocks and course identities, and CQUPT week masks and duration.

  Evidence: PR #330, PR #342.

<a id="en-jw-school-grids"></a>

- **Declared school grid row conventions**. Use each GXIC, GXNU, JXNU and NNUTC declared grid's own day and period conventions.

  Evidence: PR #330.

<a id="en-jw-postgrad-layouts"></a>

- **Declared postgraduate source layouts**. Read the declared postgraduate JSON, HTML, CSV and legacy Treewise forms with their own arrangement fields and source period tables, including explicit high periods in ZJU postgraduate tables. Declared routes include xjtu_post, uestc_post, bfa_post, gdbyxy, cupl_post, ysu_post, whu_post, hrbeu_post, shtu_post, ustc_post, xauat_post, zju_post, csv and shuwei_old.

  Evidence: PR #330, PR #359.

<a id="en-jw-shtu2024"></a>

- **SHTU2024 postgraduate periods**. Keep the SHTU2024 postgraduate layout's source course fields and full declared period clocks.

  Evidence: PR #330.

<a id="en-jw-bnuz-fallback"></a>

- **BNUZ declared empty results**. Keep a BNUZ declared empty result within its own layout so an unrelated fallback cannot generate negative-period courses.

  Evidence: PR #330.

<a id="en-jw-chaoxing-share"></a>

- **Chaoxing shared timetable capture**. Recognize the Chaoxing shared-timetable URL route, collect its lesson responses and deduplicate repeated weekly copies of the same occurrence. Actual clocks and sparse weeks remain; arrangements with different teachers or rooms stay separate.

  Evidence: PR #330.

<a id="en-jw-chaoxing-groups"></a>

- **Chaoxing arrangement groups**. Keep each Chaoxing row's course groups with their own teachers, rooms, weeks and nodes. Fields are not combined across unrelated course groups.

  Evidence: PR #330.

<a id="en-jw-chaoxing-legacy-term"></a>

- **Selected legacy Chaoxing semester**. Capture the explicitly selected legacy Chaoxing semester and student/campus context from the stable top document and accessible iframe. The selected semester is not replaced by the largest semester ID. Document and selection checks must agree before and after the request.

  Evidence: PR #370.

<a id="en-jw-treewise-capture"></a>

- **Treewise selected-semester capture**. Fetch complete JSON for the six declared Treewise capture types using the selected semester and the matching deployment prefix. The three deployment prefixes remain distinct; a portal without an explicit semester does not cause an invented endpoint request.

  Evidence: PR #330.

<a id="en-jw-thu"></a>

- **THU semester and rescheduled occurrences**. Keep THU's explicit semester calendar, course identities and notes, actual clocks and date-specific rescheduling, suppressing the replaced holiday occurrence. Full-semester week counts require an explicit source marker. Skipped incomplete courses remain counted in the warning, and course-identifier digits are not used to guess credits.

  Evidence: PR #330.

<a id="en-jw-buaa"></a>

- **BUAA teacher-week associations**. Keep BUAA teachers associated with their own weeks, including source credits, notes and actual clocks. Request identity and page context are checked together.

  Evidence: PR #330.

<a id="en-jw-json-school-capture"></a>

- **Complete school API captures**. Capture declared JSON/API and paged routes only when their selected semester, page and response identity remain consistent and all required pages complete. This includes JSON routes sustech, shsmu, ygu and zju_post; API routes ccibe, cppu, jlu_post and sues; and paged routes fstvc and gdbh.

  Evidence: PR #330.

<a id="en-jw-shsmu-calendar"></a>

- **SHSMU absolute course dates**. Keep SHSMU source dates tied to a confirmed semester origin and rebase teaching weeks when the user changes that origin. The selected week is not assumed to be week one; older marked payloads remain readable.

  Evidence: PR #330.

<a id="en-jw-ustc-parser"></a>

- **USTC source units and nested fields**. Keep USTC explicit units or clock-only course times, nested teacher names and custom places; preserve equivalent EAMS5 nested fields. lessonCode is not interpreted as a period number.

  Evidence: PR #371.

<a id="en-jw-ustc-capture"></a>

- **USTC live-page capture route**. After manual login, use USTC's selected semester and returned student identity to request its singular studentTableVm timetable endpoint. Requests remain restricted to jw.ustc.edu.cn and the expected response envelope.

  Evidence: PR #373.

<a id="en-jw-capture-budget"></a>

- **Wait for real capture completion**. Keep asynchronous capture active until its bridge result, isolate stale callbacks and pause the execution budget while a native interaction awaits the user. Navigation, refresh, back, user-agent changes and disposal cancel capture. The JavaScript evaluation callback alone does not signal completion.

  Evidence: PR #373, PR #382.

<a id="en-jw-fdu-parser"></a>

- **Fudan undergraduate and postgraduate fields**. Keep Fudan undergraduate print-data Sunday occurrences on the correct teaching week and parse postgraduate ZCBH week numbers and KSJCDM single periods with the source semantics. Only Sunday uses weekIndex minus one. The old HTML form remains available, without invented credits or notes.

  Evidence: PR #330, PR #374.

<a id="en-jw-fdu-capture"></a>

- **Fudan capture and Monday semester dates**. Connect Fudan undergraduate and postgraduate capture routes after manual login and translate the source Sunday semester origin to Sleepy's following Monday. A source semester date is required; this change does not promote the school catalog status or capture flag.

  Evidence: PR #375.

<a id="en-jw-ahu-term"></a>

- **AHU selected-semester capture**. Capture AHU's explicit selected semester instead of the largest available semester ID, then recheck live controls after reading the response body. A page or selection change during the request prevents delivery of a mixed result.

  Evidence: PR #376.

<a id="en-jw-ahu-periods"></a>

- **AHU full source periods**. Keep AHU's full thirteen-period source clock table, including unused trailing periods, without renumbering malformed columns. Captured clocks take priority; defaults remain restricted to the matching school and declared type.

  Evidence: PR #376.

<a id="en-jw-sg-periods"></a>

- **Validate Shiguang base periods**. Read a complete consecutively numbered Shiguang base period table and merge only configuration keys the source actually supplies. Period numbers run consecutively within 1–366, with strict non-overlapping clocks. Suggested durations do not rebuild explicit clocks.

  Evidence: PR #382.

<a id="en-jw-hnust"></a>

- **HNUST independent course blocks**. Keep each HNUST same-cell course block with its own teacher, room, metadata and sparse weeks. The current protocol does not filter courses by CSS visibility; explicit legacy modes remain separate.

  Evidence: PR #330.

<a id="en-jw-bjtu"></a>

- **BJTU teacher-aware course identity**. Keep BJTU arrangements with different teachers separate and preserve the declared legacy-table fallback.

  Evidence: PR #330.

<a id="en-jw-eams5-arrangements"></a>

- **EAMS5 independent arrangements**. Keep EAMS5 arrangements and sparse week bitmaps separate when teachers or rooms differ.

  Evidence: PR #330.

<a id="en-jw-sg-course-fields"></a>

- **Shiguang clocks, credits and sparse weeks**. Keep Shiguang custom course clocks, credits and sparse week sets through course parsing and the editable draft. Odd/even compression is used only when it exactly matches the source week set.

  Evidence: PR #330, PR #379.

<a id="en-jw-source-table-name"></a>

- **Source timetable name suggestions**. Seed fresh SHU2024 and matching WIST import confirmations with their source timetable names. The suggestion initializes a fresh confirmation and does not overwrite an edited draft name.

  Evidence: PR #330.

<a id="en-jw-frame-capture"></a>

- **Declared-layout frame capture**. Recognize additional source table containers and select reachable frames using the declared school parser when a generic table ID is absent. Cross-origin inaccessible frames remain inaccessible. New ZF /kbcx/ routing remains in the final capture route.

  Evidence: PR #330.

<a id="en-jw-source-calendar-fields"></a>

- **Source clocks and semester fields through import**. Carry explicit source clocks, semester dates and term length through course parsing, editable drafts and entity conversion. Only evidenced source values are carried. Defaults stay scoped to their school/protocol, and captured clocks take priority.

  Evidence: PR #330.

### Performance and local tools

<a id="en-console-unlock"></a>

- **Local debug-console unlocking**. Seven taps on the version entry open a local secret-entry dialog. Local and HTTP unlocking share validation and retry backoff; closing and reopening the console keeps its route/token state and banner consistent. Windows path handling and temporary request-file cleanup are corrected.

  Evidence: PR #277 (contributor @jim139129), PR #307 (contributor @Kerry1020).

## Known Limitations

<a id="en-limit-android"></a>

- **Android rendering differences**. Full shader refraction requires Android 13 or later. Android 12 can use platform blur; earlier versions use their fallback material. These paths do not promise identical optical output. Touch haptics also depend on the device and system settings.

  Evidence: PR #278 (contributor @jim139129), PR #283 (contributor @Kerry1020), PR #294 (contributor @Kerry1020), PR #299, PR #378.

<a id="en-limit-widget"></a>

- **Widget placement and refresh**. The wide grid requires at least 300×160dp. Actual launcher sizing and exact refresh timing depend on the launcher, alarm permission and Android background restrictions; a requested period-boundary refresh is not a guarantee of exact delivery time.

  Evidence: PR #295.

<a id="en-limit-attribution"></a>

- **Unresolved source-license questions**. The recorded SleepDown import-revision/license compatibility and DataV permission questions remain unconfirmed; the expanded acknowledgment list does not imply that these questions have been resolved. Among the 50 reviewed academic-source identities, 30 have license statements, 18 remain unknown and two record conflicts. Research acknowledgment does not establish source integration or settle permission questions.

  Evidence: PR #311, PR #395.

<a id="en-jw-sg-combo-boundary"></a>

- **Date-switching import boundary**. Automatic date-based or summer/winter switching of period tables is not connected to the app. The internal date resolver has no production caller in imports, persistence, UI, widgets, reminders or Do Not Disturb; Shiguang combination saving remains unsupported. Keep using one confirmed base period table.

  Evidence: PR #382, PR #389, PR #391.

<a id="en-source-metadata-boundary"></a>

- Source suggestions stay separate from confirmed timetable values. First-day-of-week is stored and displayed only; calendar/week calculations still begin on Monday. JSON/sharing/native-text metadata support does not imply support in ICS or HTML exports. The 366 bounds protect allocation and validation; they are not a school’s actual maximum period count.

## Verification

- Version: **1.0.64 / 10064**.
- The published tag identifies the release source; production files match the verified integration candidate.
- Tests: **659 classes, 4,943 tests, 0 failures, 0 errors, 2 skipped**.
- Lint: **590 warnings, 40 hints, 0 errors, 0 fatal issues**.
- Builds: **Debug and AndroidTest APKs built successfully**.
- Android 13 rendering: 14 checks cover centered single-row toolbars, Classic fills, full press outlines and free dragging; 33 captured frames were reviewed.
- The two skipped tests require explicit WakeUP live-server inputs; offline tests do not establish new authenticated school-login results.
- Official APKs are produced by the tag-triggered `release.yml` workflow and signed with the project release key.
- APK SHA-256 source: the attached `sha256sums.txt`. The manifest lists SHA-256 for all four APK architectures.

<a id="en-engineering-maintenance"></a>

- **Build and compatibility maintenance**. Build dependencies and Android APIs are updated; Windows CRLF-sensitive tests use platform-safe expectations, screenshot work separates UI-thread window capture from background PNG encoding, Gradle downloads use the configured verified mirrors, and the collector distributions are rebuilt with the recorded toolchain. The collector updates cdproto from 0.158.2 to 0.158.6 and Go from 1.27.1 to 1.27.3, with refreshed Linux amd64, macOS amd64/arm64 and Windows amd64 programs and checksum manifest. This is tool maintenance, without an additional school-login claim. Rendering checks wait for settled springs and foreground recomposition/drawing before capturing color samples; toolbar source contracts follow the single-row layout and shared side-control scaling. Rendering checks cover the complete marker area and sample outside the toolbar while retaining exact color assertions. CI checks pull requests and merged main commits, and supports a manual run for the selected release source before tagging.

  Evidence: PR #282, PR #314, PR #334, PR #362 (contributor @jim139129), PR #367, PR #388 (contributor @jim139129), PR #402 (contributor @jim139129), PR #403, PR #404, PR #406, PR #408, PR #409.

<a id="en-engineering-docs"></a>

- **Documentation and unused-code maintenance**. Public documentation, published-version FAQ boundaries, numbering and obsolete helper/test cleanup are maintained without adding user-facing capabilities.

  Evidence: PR #281 (contributor @jim139129), PR #297, PR #298, PR #323 (contributor @Sunrongguo2008), PR #360 (contributor @jim139129), PR #363 (contributor @jim139129), PR #388 (contributor @jim139129).


### Contributors this release

Thank you to the five external authors behind these 32 pull requests. Each PR keeps its recorded author attribution.

<a id="en-thanks-jim139129"></a>

- @jim139129: Android glass compatibility, console handling, course/table editing, settings, safe insets, collector maintenance, centered period-time fields, fixed period-row order, rendering tests and documentation. PR #277, PR #278, PR #280, PR #281, PR #329, PR #332, PR #335, PR #344, PR #360, PR #362, PR #363, PR #388, PR #398, PR #402.

<a id="en-thanks-yyichen"></a>

- @YYiChen: pinch-zoom anchoring and recently imported schools. PR #287, PR #372.

<a id="en-thanks-kerry1020"></a>

- @Kerry1020: period-time validation, single-occurrence rooms, school catalog status, Android glass compatibility and traceable debug delivery. PR #283, PR #289, PR #294, PR #306, PR #307, PR #308, PR #313, PR #327, PR #328, PR #331.

<a id="en-thanks-sunrongguo2008"></a>

- @Sunrongguo2008: reviewable school timetable updates, Qingdao catalog status and documentation numbering. PR #323, PR #325, PR #326.

<a id="en-thanks-cold577"></a>

- @Cold577: glass theme updates, page-transition compositing and independent permission diagnostics. PR #285, PR #301, PR #324.

---

# Sleepy v1.0.64

> 液态玻璃控件、课表编辑、宽网格小组件、教务采集及来源元数据。

## 新增功能

### 界面与导航

<a id="zh-ui-style"></a>

- **液态玻璃扩展到全应用小控件**. 应用界面风格可独立于明暗模式、主题颜色和底栏形态选择。液态玻璃覆盖图标小按钮、长操作按钮、分段选择、开关、滑杆、菜单、对话框、日期选择器、导入网页顶栏和小组件设置控件。旧的玻璃底栏偏好保留原意；大型导航行和内容卡片保留页面原有层次。

  依据：PR #299, PR #366.

<a id="zh-glass-switch"></a>

- **悬起的开关透镜**. 开关按住后升起完整的透明玻璃透镜，尺寸超过轨道，跟手移动并折射背景。背景按镜片实际尺寸采样，放大滑块不会只拉伸轨道高度的旧纹理。轨道颜色、选中状态、禁用状态和松手落位保持各自语义。

  依据：PR #299, PR #378.

<a id="zh-glass-tabs"></a>

- **标签分段的悬起镜片与文字放大**. 标签分段按住后，镜片从42dp轨道升为52dp，上下边界都越过轨道，悬起时的基准宽度比原分段增加16dp。独立内容透镜将标签放大16%。拖动时镜片弹性跟随，切换分段有触觉反馈，松手后由弹簧落位。

  依据：PR #299, PR #378.

<a id="zh-glass-slider"></a>

- **有色精细滑杆**. 液态玻璃滑杆使用独立的光学滑块：静止时为有色16×24dp竖椭圆，按住后升为28×40dp透明透镜，下方是连续4dp轨道。普通和双端滑杆保留整数吸附、取值边界、原始值幽灵标记和重合端点的独立选择。玻璃滑杆保留键盘步进、无障碍操作、从右向左方向和禁用行为。拖动期间暂停冲突的父级滚动；快速点击色相时提交最新颜色，不再使用旧的捕获值。编辑输入与镜片显示使用同一条数值更新路径。

  依据：PR #299, PR #378.

<a id="zh-navigation-icons"></a>

- **四个导航图标各自的部件动效**. 经典悬浮Dock、玻璃Dock、贴底导航栏和平板Rail都使用图标部件动效。课表日历外框静止，六个日期格依次弹起；今日外框静止，中间日期方块弹起；管理齿轮外圈旋转、中心轻跳；我的图标身体静止，头部摆动后回稳。共享动画进度让图标与透镜中的副本在快速切换时保持一致。

  依据：PR #333, PR #392.

### 课程、设置与提醒

<a id="zh-period-card-collapse"></a>

- **课程时段卡整卡折叠**. 课程时段卡默认整卡折叠；从课表中某次课程进入时，自动展开对应卡片。新课程的首张卡和新添加的卡默认展开。折叠状态仍显示时段摘要与校验错误。

  依据：PR #368.

<a id="zh-occurrence-room"></a>

- **单次调课可修改地点**. 单次调课可只修改本次课程的上课地点。未改动的地点保留来源原文，使兼容课程行可以重新合并；用户修改后的地点会清理首尾空白。并发产生的过期编辑会被阻止，避免覆盖新数据。

  依据：PR #328 (contributor @Kerry1020).

<a id="zh-table-copy-name"></a>

- **复制课表前先确认名称**. 复制课表前先显示可编辑的名称建议。空名称和重复名称不能确认，取消不会创建副本，并阻止重复提交。复制成功后使用独立课程组，并支持撤销。

  依据：PR #329 (contributor @jim139129).

<a id="zh-settings-map"></a>

- **按用途整理设置入口**. 我的页面按课表、作息和节假日管理，提醒、外观、通用和小组件，备份恢复，以及关于分组。外观将界面风格与颜色、导航形态分开，课表显示使用独立子页面。通用包含语言、高刷新率与撤回编辑设置；导出保留在课表管理。原有选项维持行为和作用范围。课表显示按通用、周次、网格、节次表头、冲突和节假日外观整理；预览在详细选项之前，隐藏时间后同步隐藏时间对齐设置。全局小组件设置与单个实例设置维持各自范围，竖排标点归入小组件设置。

  依据：PR #320.

<a id="zh-reminder-scope"></a>

- **明确选择提醒使用的课表**. 手动选择课表集合前，提醒只跟随当前选中课表。首次手动切换保存完整选择集合，切换当前课表不再覆盖它；全部关闭后保持关闭，新建、复制和导入课表不会自动加入。课前提醒、每日摘要和旧版闹钟投递使用同一范围。

  依据：PR #320.

### 桌面小组件

<a id="zh-widget-wide"></a>

- **独立的4×2宽网格小组件**. 小组件库新增独立4×2宽网格入口，提供原完整网格、纵向滚动网格和横向三天网格三种形态。原有2×2和4×4入口继续保留。

  依据：PR #295.

<a id="zh-widget-vertical"></a>

- **七天纵向滚动节次窗口**. 宽网格默认纵向形态显示七天，不占用星期标题行，至少容纳四个完整节次。每节结束后推进可见节次窗口，到最后一个完整窗口停止。跨越窗口边界的长课程保留可见部分，并在其中重新安排课名和地点。

  依据：PR #295.

<a id="zh-widget-horizontal"></a>

- **横向三天网格**. 横向宽网格显示今天、明天和后天，顶部为时间或节次，星期标签可选在左侧或右侧。三种形态均保留冲突分栏、课程别名与颜色、自定义时间、固定日期及调休安排。

  依据：PR #295.

<a id="zh-widget-previews"></a>

- **实例内直接选择与真实课表预览**. 小组件实例编辑页可直接选择网格形态与标签侧边，使用该实例实际绑定的课表预览。预览订阅取消过期工作，页面停止时停止订阅。小组件库以折叠组展示五类小组件，同一时间展开一组，标明大、中、小尺寸并支持固定到桌面。

  依据：PR #295, PR #390.

### 选校与回传导入结果

<a id="zh-jw-recent-schools"></a>

- **最近导入的学校**. 选校页顶部显示最近成功导入的学校，最多三所，最近一次排在最前。只有新建、追加或手动更新成功才记录；搜索时隐藏卡片，已下架目录条目不再显示。

  依据：PR #372 (contributor @YYiChen).

### 教务、采集与数据

<a id="zh-jw-manual-update-preview"></a>

- **审阅教务课表更新**. 可从教务系统手动更新已有课表，应用前查看新增、变更和移除的课程。更新由用户手动发起，没有自动后台同步。

  依据：PR #325 (contributor @Sunrongguo2008).

<a id="zh-jw-manual-update-matching"></a>

- **保留匹配课程的本地设置**. 更新教务安排时，为匹配课程保留原标识、别名、备注、颜色和自定义时间。优先精确匹配课程发生记录，名称与星期回退须双向唯一；被移除或存在歧义的课程不保证保留本地字段。

  依据：PR #325 (contributor @Sunrongguo2008).

<a id="zh-jw-manual-update-groups"></a>

- **更新时保留课表设置**. 确认后以事务更新课程并支持撤销，保留课表设置；新增课程可继承唯一匹配课程组的别名和颜色。新增行不继承单行备注或自定义时间；手动更新独立处理作息，可能扩展绑定的共享作息表。

  依据：PR #325 (contributor @Sunrongguo2008).

<a id="zh-jw-db15"></a>

- **数据库十五版来源元数据**. 升级保留已有课表，数据库十五版新增默认空值的来源元数据。原十三版数据先经十四版的可空提醒覆盖字段迁移，再增加十五版来源字段。

  依据：PR #377.

<a id="zh-jw-source-presence"></a>

- **按字段存在性保存 SG 来源建议**. SG 来源配置与适配入口身份独立保存，区分字段缺失与显式空开学日期，不覆盖用户确认值。整数和日期须合法，每周第一天目前只作为元数据保留。

  依据：PR #379.

<a id="zh-jw-source-draft-backup"></a>

- **草稿与备份中的来源元数据**. 来源元数据贯穿可编辑导入草稿、课表转换和完整备份恢复。缺少此字段的旧草稿与备份继续可读。

  依据：PR #379.

<a id="zh-jw-source-json-share"></a>

- **JSON 与分享保留来源元数据**. 已接线的 WakeUP 兼容 JSON 导出导入及分享载荷保留 importSourceJson。范围限于已接线的 JSON 与分享路径，不扩大到全部导出格式；追加不替换目标课表来源。

  依据：PR #380.

<a id="zh-jw-source-native"></a>

- **原生来源字段与原文校验**. Sleepy 原生文本导出转义后的来源字段，导入先保留该字段原文再规范化课程文本，校验依据原载荷字符。保留来源原文字符和校验输入，不承诺重序列化元数据 JSON 的空白与键顺序逐字节相同。

  依据：PR #380.

<a id="zh-jw-sg-entry-choice"></a>

- **选择拾光适配入口**. 拾光展示全部声明入口，包括同脚本不同地址的入口，按用户选择打开导入地址。保留来源顺序，失效保存标识不悄悄换成其他入口。

  依据：PR #364, PR #382.

<a id="zh-jw-sg-native-interactions"></a>

- **拾光原生交互**. 脚本提示、输入和单选请求使用原生交互，用户操作后恢复对应请求。校验函数返回 false 才接受，返回的错误字符串继续显示；取消与过期会话、请求、尝试标识分别隔离。showToast 仍无操作。

  依据：PR #381, PR #382.

<a id="zh-jw-sg-finalization"></a>

- **拾光导入统一完成一次**. 拾光课程先暂存，notifyTaskCompletion 后连同最终作息和配置一次性交付导入结果。未完成交互、无课程或此前元数据错误不会成为部分成功导入。

  依据：PR #382.

<a id="zh-jw-sg-confirm-weeks"></a>

- **确认来源学期周数**. SG 导入确认可编辑学期总周数，已有可恢复草稿保留输入原文。确认值须覆盖课程末周且不超过三百六十六；367 等非法原文仍可编辑，只对已有草稿异步保存检查点。

  依据：PR #364, PR #387.

<a id="zh-jw-sg-source-card"></a>

- **审阅来源建议**. 课表名称下方、作息编辑器之前显示所选来源入口及提供的上课时长、课间时长和每周第一天建议。顺序为开学日期、总周数、课表名称、来源建议、作息；每周第一天建议不改变周一基准的日期算法。

  依据：PR #364, PR #387.

### 性能与本地工具

<a id="zh-performance-grid"></a>

- **减少课表重复计算**. 今日与课表投影复用近期周次结果，在真实输入变化时失效。进入课表先准备当前周，拖动期间准备相邻周。稳定课表键、课程组索引、有界字体测量、网格几何、用餐间隔前缀及偏好和颜色缓存减少重复解析与重组。来源 JSON 未变化时也复用作息解析、校验和显示结果；各缓存最多保留十六项，超过三万二千七百六十八字符的输入只解析、不常驻。

  依据：PR #307 (contributor @Kerry1020), PR #316, PR #317, PR #318, PR #332 (contributor @jim139129), PR #400.

<a id="zh-performance-idle"></a>

- **玻璃渲染在静止时停止空跑**. Dock回稳后停止请求动画帧，再次运动时使用新的帧时间。课表课程与冲突卡叶子节点不再重复登记玻璃场景源，节假日灰显不再触发第二次异步切页更新。

  依据：PR #318, PR #397.

<a id="zh-performance-background"></a>

- **启动与导入工作移至后台**. 启动清理、小组件调度注册、文件读写、导入解析和节假日灰显计算在对应后台路径执行。启动仍按顺序先取消旧通知、再恢复提醒，并按既定顺序初始化撤回。导入文件在 IO 线程读取，解析在后台计算线程完成后返回导入界面；没有实测速度倍数结论。

  依据：PR #319.

<a id="zh-debug-delivery"></a>

- **可追溯的官方调试包**. 分支和拉取请求调试构建可将指定源码与最新主线组合，并分别记录实际源码版本与工作流版本。手动构建可选架构，原构建页提供四种官方签名架构选择。运行身份校验和清理前的签名资产复核保留下载追溯信息。

  依据：PR #290, PR #292, PR #306 (contributor @Kerry1020), PR #308 (contributor @Kerry1020), PR #313 (contributor @Kerry1020), PR #369.

### 来源与致谢

<a id="zh-attributions"></a>

- **扩充应用内来源与贡献记录**. 关于页面保留完整贡献历史入口。引用项目按可折叠来源组整理，已提供的许可附完整原文；教务研究致谢补齐本轮新增复核仓库。归属清单与六组语言资源一起校验。教务协议研究条目分别保留各仓库署名与许可依据。PR #395 新增四十九个身份并补强一条既有记录，五十个复核身份中包含三十八个 fork。

  依据：PR #310, PR #311, PR #361, PR #395.

## 修复

### 顶栏、按钮按住拖动与小组件颜色

<a id="zh-glass-free-drag"></a>

- **玻璃小按钮按住后可向任意方向拖动**. 液态玻璃模式下，图标小按钮和长操作按钮按住后可向水平、垂直及斜向拖动，镜片弹性形变并跟随手指，越过静止点击范围仍继续跟手。松手后弹性归位，取消拖动不触发点击。共享胶囊的禁用半区不会升起整块镜片，启用半区保留光学反馈。

  依据：PR #403.

<a id="zh-classic-toolbar-fill"></a>

- **经典模式顶栏图标保留圆形底色**. 经典模式的课表顶栏图标保留 Material 主题容器色圆片，覆盖浅色、深色及紧凑布局。空间收窄时，圆片、图标和独立点击槽按同一比例缩小。

  依据：PR #404.

<a id="zh-toolbar-responsive"></a>

- **周导航绝对居中并始终保持单行**. 周导航始终位于整条顶栏的绝对水平中心，从右向左布局也遵守同一规则。空间不足时先缩两侧间距，再缩两侧控件。普通状态保留左箭头、“第2周”、右箭头三个独立按钮；仅极端宽度合为[左 | 2 | 右]胶囊，各段仍有独立操作。更窄时整组控件继续等比缩小，分数像素定位保持精确中心。两种形态始终单行、不重叠，无障碍描述保留学期状态。

  依据：PR #386, PR #393, PR #404.

<a id="zh-widget-uniform-color"></a>

- **网格小组件统一底色与应用内课程卡一致**. 选择课程统一底色后，普通网格、紧凑色带及宽版纵向和横向网格使用与应用内课程卡相同的 Material 主题颜色。普通课程底色不透明，灰显课程使用与应用相同的60%不透明度；共用这些渲染器的实例设置预览同步显示相同结果。

  依据：PR #405.

### 界面与导航

<a id="zh-glass-springs"></a>

- **渐进升降、果冻形变和彩虹边缘**. 开关、标签分段和滑杆沿用Dock的升起、跟随、换位与回落弹簧。移动时镜片伸展，减速时弹性恢复。短点按先完成升起再落下，不用固定停留时间代替动作；轨道透镜和内容透镜保留边缘彩虹色散。

  依据：PR #378.

<a id="zh-glass-scene"></a>

- **场景采样与前景图层**. 玻璃表面将普通场景与光学控件分开采样，覆盖嵌套页面和弹出窗口。对话框、菜单可采样父窗口场景，内嵌Android视图变化会更新采样内容，避免镜片回采自身并保留前景内容。弹出菜单保留定位、首个选项和玻璃层上的前景标签，窄屏语言菜单保留完整标题。共享定位图层避免裁切或丢失菜单内容，半屏面板保留预期圆角和可见控件。

  依据：PR #299, PR #399.

<a id="zh-glass-compatibility"></a>

- **玻璃兼容、底栏拖动与主题颜色**. 旧版Android使用可用的背景、模糊和边缘回退效果，不再传入不支持的内容着色器参数或出现黑块。底栏颜色及时跟随应用明暗模式和自定义主题；Android13及以上保留着色器折射路径。 连续拖动玻璃底栏时，镜片连续跟手，避免反复顿挫；浅色底盘与阴影层次一并调整。

  依据：PR #278 (contributor @jim139129), PR #283 (contributor @Kerry1020), PR #285 (contributor @Cold577), PR #294 (contributor @Kerry1020).

<a id="zh-glass-control-layout"></a>

- **静止玻璃控件的尺寸与留白**. 普通玻璃图标按钮将36dp视觉圆盘与48dp点击范围分开。课程操作、确认按钮、删除操作和紧凑文字按钮使用明确间距，避免静止时相贴或重叠。光学材质与原有点击语义、回调归属分开。节次和时间编辑、周次选择、添加与移除、冲突处理、课程详情、导入详情和课表操作卡片统一按钮占位和文字间距。课程配色操作及颜色对话框为文字与玻璃盘面留出空间。主题预览、节次表头、节假日操作、刷新按钮、提醒横幅和小组件库统一间距。设置卡片允许悬起的分段镜片越过轨道，保留横向拖动时的完整边缘。

  依据：PR #383, PR #384, PR #385, PR #390, PR #394, PR #398 (contributor @jim139129).

<a id="zh-glass-plain-rows"></a>

- **普通列表入口保留清楚层次**. 提醒权限诊断行与课表切换行使用普通整行点击。课表切换对话框保留玻璃底层，各行维持清楚的选中底色和文字层次。

  依据：PR #366.

<a id="zh-navigation-transitions"></a>

- **页面与标签转场保持完整底色**. 页面缩放由主题底色承接，消除白边和父子页文字重影，覆盖预测性返回。手机标签页和平板详情窗格在转场期间保持不透明，快速反向切换恢复正确页面状态。

  依据：PR #301 (contributor @Cold577), PR #332 (contributor @jim139129).

<a id="zh-navigation-insets"></a>

- **内容避开系统导航与悬浮Dock**. 横屏双栏布局遵守安全绘制边距，避开系统三键导航。经典与玻璃形态的底部内容留白都包含Dock完整高度、间隔和系统导航边距，使关于等下方入口可见且可点击。

  依据：PR #293, PR #335 (contributor @jim139129).

### 课程、设置与提醒

<a id="zh-period-card-actions"></a>

- **时段卡删除与折叠按钮顺序**. 时段卡标题栏顺序为标题或摘要、删除、折叠箭头。删除叉位于右侧，紧贴折叠箭头左边；仅剩一个时段时不可删除。窄屏和大字体下统一这些按钮的尺寸与间距。

  依据：PR #368, PR #384.

<a id="zh-pinch-anchor"></a>

- **双指缩放以两指中点为锚点**. 课表双指缩放以两指中点为锚点，节次行高变化时同步修正滚动，覆盖长课间和加权行。捏合表头时保持顶部锚点，开始捏合会停止之前的惯性滚动。

  依据：PR #287 (contributor @YYiChen).

<a id="zh-course-drag"></a>

- **课程拖动与跨周手势连续**. 悬起课程持续跟随手指。翻周检查明确的周缘进入意图与移动方向，真正跨周后仍保持同一次拖动手势，避免拖课误翻页或中断。

  依据：PR #288.

<a id="zh-course-lift-blur"></a>

- **长按课程的完整模糊背景**. Android12及以上，长按悬停课程恢复20dp高斯背景模糊与浅色蒙层；旧版保留压暗回退。效果覆盖整个课表圆角和内边距，开始拖动立即移除，正常关闭时渐退。

  依据：PR #315.

<a id="zh-course-noop-drop"></a>

- **课程原位放回不产生变更**. 课程放回同一周、同一天及同一节次范围时立即结束拖动，不写数据库、不闪确认窗、不新增撤回记录，也不清空重做历史。保留自定义时间、固定日期和节假日语义；真正移动、调整长度或跨周仍走确认流程。

  依据：PR #322, PR #332 (contributor @jim139129).

<a id="zh-settings-typography"></a>

- **清楚而紧凑的设置行**. 设置统一14sp文字角色，标题、数值和操作对齐；开关与滑杆保持紧凑，说明间隔为8dp，折叠行高为48dp。长文字、窄屏和大字体可换行或改为纵向单选，不留多余空白，也不遮住控件。

  依据：PR #344 (contributor @jim139129).

<a id="zh-fluid-current-table"></a>

- **流体云与实况卡片跟随当前课表**. 流体云与实况卡片通知只使用当前选中课表，切换后立即重新核对。新建或导入另一课表不会抢走当前或默认选择，原来没有课表时除外。

  依据：PR #291.

<a id="zh-reminder-permissions"></a>

- **独立的提醒权限诊断**. 提醒权限诊断独立于流体云功能可用。授予权限不会自动启用提醒。权限与后台运行状态行去掉多余空行，同时保留整行点击。

  依据：PR #320, PR #324 (contributor @Cold577).

<a id="zh-holiday-cache"></a>

- **稳定的节假日设置首屏**. 节假日设置首帧先使用内存缓存，已缓存的空年份也有效；切换年份时重新设置加载状态。磁盘读取和JSON解析在后台运行，刷新时保留已有列表。固定48dp操作占位与已缓存的日期、调休选择减少布局跳动。

  依据：PR #398 (contributor @jim139129).

### 选校与回传导入结果

<a id="zh-jw-catalog-bupt"></a>

- **北邮入口分组**. 将北京邮电大学新教务的 VPN 和 WebVPN 两个入口归入 B 分组，保留原目录标识。

  依据：PR #280 (contributor @jim139129).

<a id="zh-jw-catalog-city"></a>

- **同城学校排序**. 同城学校推荐按拼音首字母和字典序排列。

  依据：PR #296.

<a id="zh-jw-catalog-qdu"></a>

- **青岛大学状态**. 依据 2026 年 11 月 5 日的成功导入报告，将青岛大学既有拾光入口标记为已支持。沿用既有适配器及回传导入结果。

  依据：PR #326 (contributor @Sunrongguo2008).

<a id="zh-jw-catalog-ntit"></a>

- **南通理工特定年级入口状态**. 依据四十二门课程的成功导入报告，将南通理工学院“2023届及之后”既有入口标记为已支持。范围仅限该目录入口。

  依据：PR #327 (contributor @Kerry1020).

<a id="zh-jw-catalog-jxstnu"></a>

- **江西科技师范大学状态**. 依据 2026 年 11 月 7 日回传的 24 门课程导入结果，将江西科技师范大学既有入口标记为已支持。沿用既有 zf_new 地址及抓取设置。

  依据：PR #331 (contributor @Kerry1020).

### 教务、采集与数据

以下条目描述声明的解析与采集通路。离线样例和脚本接线不证明真实学校登录成功；只有另列的青岛大学、南通理工学院及江西科技师范大学目录更新依据回传成功导入结果。存在歧义的名称保留来源标识。

<a id="zh-jw-period-clock-center"></a>

- **作息时间字段居中**. 编辑课表时，紧凑的开始、结束时间字段将标签和钟表时间水平、垂直居中；标签与时间各自保持单行。

  依据：PR #402 (contributor @jim139129).

<a id="zh-jw-period-no-reorder"></a>

- **滚动作息列表时保持节次顺序**. 编辑课表的作息时，拖动页面滚动，节次行保持当前顺序。该页面关闭长按拖拽排序，仍可添加、删除节次或修改时间。

  依据：PR #402 (contributor @jim139129).

<a id="zh-jw-period-validation"></a>

- **严格校验作息时间**. 保存作息前校验分钟时间、节次编号唯一性、起止顺序和相邻节次的时间顺序。时间采用 HH:mm；不猜测上午下午、不补造时长，也未新增跨午夜模型。合法早时段与既有边缘节次标识仍可使用。

  依据：PR #289 (contributor @Kerry1020).

<a id="zh-jw-period-display"></a>

- **保留无效作息的排查信息**. 作息时间需要修正时仍保留课程及节次标签，编辑器显示具体原因。显示时的格式整理不改写存储时间。

  依据：PR #289 (contributor @Kerry1020).

<a id="zh-jw-today-all-courses"></a>

- **完整显示今日同栏课程**. 今日页面完整显示同栏内的所有课程，小组件分组、计时和绘制采用同一套校验后的时间解释。

  依据：PR #289 (contributor @Kerry1020).

<a id="zh-jw-backup-reminders"></a>

- **备份保留提醒选择**. 完整备份覆盖恢复时保留各课表提醒选择；向已有数据合并备份时保留本机提醒名单。旧备份继续可读；合并到已有数据时，导入课表不替换本机默认课表，也不自动加入本机提醒名单。

  依据：PR #320.

<a id="zh-jw-period-edit-draft"></a>

- **调休返回后保留课表草稿**. 从课表编辑进入调休再返回时保留未保存内容，恢复后保存前重新核对共享作息方案。写入数据库前仍须完成共享作息确认。

  依据：PR #320.

<a id="zh-jw-source-period-rows"></a>

- **保留完整来源作息**. 导入确认保留来源作息末尾尚未用到的空节，行数同时覆盖最高课程节次和最高来源节次。来源缺少的时间留空待确认。

  依据：PR #330, PR #346.

<a id="zh-jw-import-node-coverage"></a>

- **避免高节次课程导入后隐藏**. 新建与追加课表在确认及落库时检查每个课程节次都有对应作息行，避免高节次课程被短作息隐藏。实际时钟课程豁免；追加先筛除冲突课程，不能悄悄扩展已绑定作息。手动更新使用独立路径。

  依据：PR #349.

<a id="zh-jw-declared-route"></a>

- **遵守声明的学校布局**. 页面外观相似时，仍按声明学校布局解释行位置和周次。声明布局的空结果保持为空，不交给无关解析器生成课程。

  依据：PR #330.

<a id="zh-jw-week-occurrences"></a>

- **保留真实课程发生集合**. 保留显式周次与节次中的缺口和单双周，只合并课程字段一致的安排。各来源保留自身默认规则，缺失字段没有统一的推断规则。

  依据：PR #330.

<a id="zh-jw-kingo"></a>

- **青果课程安排字段**. 青果相应布局保留紧凑网格位置、同格多课、带括号课程名、独立教师和教室、学分及明确高节次。

  依据：PR #330, PR #336.

<a id="zh-jw-classic-eams-fields"></a>

- **经典 EAMS 引号字段**. 经典 EAMS 正确读取单引号或双引号包围的字段，保留逗号、括号和转义字符，按来源解码一次。

  依据：PR #330, PR #337.

<a id="zh-jw-classic-eams-occurrences"></a>

- **经典 EAMS 课程坐标**. 经典 EAMS 保留同一活动的不同星期、离散节次和稀疏周次位图，也保留教师为空的有效课程。

  依据：PR #330.

<a id="zh-jw-qz-layouts"></a>

- **强智布局坐标**. 强智按标题字段与学校声明行映射读取课程，保留离散周次和节次，导入前校验显式范围终点。声明映射包括 qz_single_node、qz_njust、qz_wit、qz_ecust、qz_ahut、qz_bjfu、qz_ustb；非法或溢出终点不转成虚构单节课程。

  依据：PR #330, PR #348, PR #365.

<a id="zh-jw-old-qz"></a>

- **声明的旧强智形态**. 旧强智 JLICT、CCUT、CCSU 形态分别按自身安排边界和显式节次解析。

  依据：PR #330, PR #352.

<a id="zh-jw-urp"></a>

- **旧 URP 物理行**. 旧 URP 识别长短表头、跨行和中文大小节标签，研究生括号安排分别保留自身周次与单双周。压缩续行只继承有依据的元数据，未知周次不补成假定的十六周或二十周学期。

  依据：PR #330.

<a id="zh-jw-new-urp"></a>

- **新版 URP 位图与名称字段**. 新版 URP 保留稀疏周次位图与字段别名，补齐 SHOU 的嵌套 coureName 名称回退。

  依据：PR #330, PR #365.

<a id="zh-jw-zf-old"></a>

- **旧正方课程字段**. 旧正方相应表格、班级课表、段落、字体和提示布局分别读取字段，避免把学分、选课备注和课程属性混入课程名。

  依据：PR #330.

<a id="zh-jw-zf-new"></a>

- **新版正方离散周次与高节次**. 新版正方保留明确高节次、离散周次和星期坐标，补齐网格第二个字体字段的读取。缺少范围终点时可采用来源已有的单节形态，已提供的非法终点不能当作缺失。

  依据：PR #330, PR #352, PR #365.

<a id="zh-jw-cf-weeks"></a>

- **青果 CF 请求周次与课程集合**. 青果 CF 按页面明确周次采集，覆盖离散和较晚周次，保留每个返回安排的节次和时钟。显式周次请求要求每个请求周恰有一个完整响应桶，旧响应包装继续兼容。

  依据：PR #330, PR #336.

<a id="zh-jw-jinzhi-order"></a>

- **金智混合页面选择**. 金智各布局分别读取局部字段；页面有多套并行表示时按来源顺序采用首个有效分支。并行表示不合成重复课表。

  依据：PR #330, PR #343.

<a id="zh-jw-umooc"></a>

- **UMOOC 排除周与显式节次**. UMOOC 从发生集合中扣除排除周，保留表头或单元格明确声明的高节次。保留周次缺口与单双周，错误文本不补造默认周次。

  依据：PR #330, PR #354.

<a id="zh-jw-element"></a>

- **Element 课表节次**. Element 课表包装结构保留明确高节次和各课程独立字段。

  依据：PR #330, PR #355.

<a id="zh-jw-suda-post"></a>

- **苏大研究生跨行课次**. 苏大研究生课程保留跨行节次，相邻非法跨度仍占据物理列，避免后续课程错列。非法跨度保留物理列占位，但不生成课程。

  依据：PR #330, PR #356.

<a id="zh-jw-xbellsoft"></a>

- **Xbellsoft 课程安排**. Xbellsoft 保留显式整数坐标、单双周、高节次和独立教师教室，仅为相同安排合并周次。

  依据：PR #330, PR #342.

<a id="zh-jw-ecupl-clocks"></a>

- **华东政法逐节时钟**. 华东政法年表和脚本矩阵保留逐节来源时钟，实际抓取的时钟优先。不为不完整时钟列补造时间。

  依据：PR #330, PR #338.

<a id="zh-jw-ecupl-diagnostics"></a>

- **华东政法回退诊断**. 华东政法降级解析保留原始失败原因，排查信息能指出失败布局。

  依据：PR #339.

<a id="zh-jw-nwpu-post"></a>

- **西工大研究生来源上下文**. 西工大研究生导入保留所选学期名称及十三节来源作息，按课表时间格选择校区默认时钟。每行时间格只计一次，平票采用长安校区；友谊校区五月至九月采用来源夏季默认时间。抓取时钟优先，缺少学期字段时不补造日期或名称。

  依据：PR #330, PR #345.

<a id="zh-jw-scau"></a>

- **华南农大表格变体**. 华南农大旧表及其他声明布局保留压缩节次、行偏移和明确高节次。

  依据：PR #330, PR #336, PR #353.

<a id="zh-jw-nuist"></a>

- **南京信息双节行标签**. 南京信息工程大学半角与全角波浪号行标签均按双节解释，课程未写压缩节次码时后续行仍对应正确节次。

  依据：PR #330, PR #347.

<a id="zh-jw-whut"></a>

- **武汉理工动态节次映射**. 武汉理工动态映射保留高节次，每个课程安排只转换一次。

  依据：PR #330, PR #358.

<a id="zh-jw-ucas"></a>

- **国科大完整详情与高节次**. 国科大按课程标识匹配详情页，保留 HTML 和文本中的高节次，完整取得所需详情后再导入。所需详情页必须完整取得；不使用占位周次或部分结果冒充完整课表，既有十二位 JSON 约定单独保留。

  依据：PR #330, PR #359.

<a id="zh-jw-ztvtit"></a>

- **浙江交通课次坐标**. 浙江交通职业技术学院按星期属性与课次坐标读取课程，避免依赖 DOM 顺序，并保留明确高节次。

  依据：PR #330, PR #357.

<a id="zh-jw-stum"></a>

- **STUM 显式节次**. STUM 移动教务布局保留明确声明的高节次。STUM 为布局标识。

  依据：PR #330, PR #357.

<a id="zh-jw-ahmu"></a>

- **AHMU 显式安排字段**. AHMU 按 qsz、jsz、skcd 显式字段读取课程坐标，保留合法高节次。不悄悄转换小数或溢出坐标。

  依据：PR #330, PR #340.

<a id="zh-jw-aic"></a>

- **AIC 学期课程安排**. AIC 按学期课表安排解释课程，保留明确高节次与周次详情。AIC 对应多个目录入口，缺少 weekDetail 不代表整学期。

  依据：PR #330, PR #340.

<a id="zh-jw-ruc"></a>

- **人大元数据与离散节次**. 人大课表保留来源元数据、离散节次和明确高节次。

  依据：PR #330, PR #341.

<a id="zh-jw-sdu"></a>

- **山大固定列课程安排**. 山大按固定列读取各安排自身字段，保留明确高节次。

  依据：PR #330, PR #341.

<a id="zh-jw-hbmzu"></a>

- **HBMZU 网格位置**. HBMZU 网格保留星期和双节行位置，覆盖明确高节次。HBMZU 保留为协议标识，当前目录用例包含恩施职业技术学院。

  依据：PR #330, PR #341.

<a id="zh-jw-hit"></a>

- **HIT 空教师与高节次**. HIT 保留教师为空的有效课程、八列星期布局和明确高节次。

  依据：PR #330, PR #342.

<a id="zh-jw-seig"></a>

- **SEIG 课程块**. SEIG 保留行顺序、最后一组安排括号前的课程名和明确高节次。

  依据：PR #330, PR #351.

<a id="zh-jw-simc"></a>

- **SIMC 课表偏移**. SIMC 保留节次偏移、重复周次位图、节点映射及声明的扩展和午间时钟。目录中的相关机构名称分别保留。

  依据：PR #330.

<a id="zh-jw-bfa"></a>

- **BFA 六列布局**. BFA 声明的六列课表按自身布局读取，避免星期错位。

  依据：PR #330.

<a id="zh-jw-hust"></a>

- **HUST 安排独立字段**. HUST 每个课程安排分别保留自己的教师、教室和授课对象。

  依据：PR #330.

<a id="zh-jw-nua"></a>

- **NUA 声明布局**. NUA 表格保留额外午间节次，印刷第五节及后续节次在午间后顺移，各安排字段分别读取。

  依据：PR #330.

<a id="zh-jw-gdei"></a>

- **GDEI 逐课覆盖字段**. GDEI 的覆盖字段只作用于对应课程，保留教室后缀。

  依据：PR #330.

<a id="zh-jw-jlu-post"></a>

- **吉大研究生课程安排**. 吉大研究生逐一读取括号安排，分别保留教室、周次和单双周。

  依据：PR #330.

<a id="zh-jw-ccibe"></a>

- **CCIBE 逐周响应身份**. CCIBE 将课程发生记录绑定到请求周次和所选学期，核对完整响应集合。每个请求周恰有一个响应桶，所选学期须保持一致。

  依据：PR #330.

<a id="zh-jw-cppu"></a>

- **CPPU 绝对日期**. CPPU 来源绝对日期按 Sleepy 的周一学期基准保存。

  依据：PR #330.

<a id="zh-jw-shcc"></a>

- **SHCC JSON 课程名**. SHCC 读取完整 JSON 数组，课程名内部的分号仍属于名称。

  依据：PR #330.

<a id="zh-jw-fstvc"></a>

- **FSTVC 字段与来源默认作息**. FSTVC 保留序列化字段和备注，完整采集分页，仅在学校匹配且班级批次明确时采用来源默认作息。班级批次混合或依据不足时不猜默认作息。

  依据：PR #330.

<a id="zh-jw-gdbh"></a>

- **GDBH 星期与节次对**. GDBH 分页保留星期及六组节次各自的周次，确认页可使用声明的十节来源时钟。十条来源时钟不能作为课程最高十节的限制。

  依据：PR #330.

<a id="zh-jw-gliet"></a>

- **GLIET 独立课程块**. GLIET 读取全部课程块，保留第十一节、十三节的独立单节课程。

  依据：PR #330.

<a id="zh-jw-cnu"></a>

- **CNU curriculum-item 布局**. CNU 每个 curriculum-item 分别读取，按一列或两列间隔定位星期。当前目录中的 CNU 指长春师范大学。

  依据：PR #330.

<a id="zh-jw-cdut"></a>

- **CDUT 物理网格**. CDUT 周表按物理列和空合并单元格占位定位，避免将只有教室的文字当课程。

  依据：PR #330.

<a id="zh-jw-gzhu-post"></a>

- **广大研究生课程块**. 广大研究生课表各块独立读取，保留物理跨行和跨列位置。

  依据：PR #330.

<a id="zh-jw-dhu"></a>

- **东华四字段课程块**. 东华四字段课程安排与空跨度占位按物理位置保留。

  依据：PR #330.

<a id="zh-jw-ecjtu"></a>

- **华东交大空安排字段**. 华东交大三字段安排中教师或教室为空时仍保留位置，避免相邻字段错位。

  依据：PR #330.

<a id="zh-jw-zptc"></a>

- **浙江邮电表头坐标**. 浙江邮电按表头星期和节次范围定位，周次之前的元数据单独读取。

  依据：PR #330.

<a id="zh-jw-ctgu"></a>

- **CTGU 末字段教师**. CTGU 始终将最后字段作为教师，包括空教师，分别保留各安排容器。说明字段不生成额外课程，有歧义的混合容器不导入为虚构安排。

  依据：PR #330.

<a id="zh-jw-nyist"></a>

- **南阳理工时钟与单双周**. 南阳理工保留显式时钟及各安排独立单双周。缺少时间不补造第十九周课程。

  依据：PR #330.

<a id="zh-jw-jxau"></a>

- **江西农大空格与分隔列**. 江西农大保留空节次格及各 dl 自身字段，只跳过声明的两列分隔列，保留明确高节次。

  依据：PR #330, PR #341.

<a id="zh-jw-hniu"></a>

- **HNIU 显式课程节次**. HNIU 保留括号中的周次节次及明确高节次，教师与教室行分别读取。

  依据：PR #330, PR #351.

<a id="zh-jw-sustech"></a>

- **SUSTech 括号安排**. SUSTech 按五个括号字段读取安排，保留明确节次范围、周次、单双周与来源备注。

  依据：PR #330, PR #351.

<a id="zh-jw-uic"></a>

- **UIC 相邻单节课程**. UIC 保留相邻单节课程行，从课程对照表匹配教师。该声明布局保留既有第一至二十周约定。

  依据：PR #330, PR #351.

<a id="zh-jw-cityu"></a>

- **CityU 跨行占位**. CityU 跨行占位在来源边界结束，保留有效高节次，避免后续课程移到错误星期。

  依据：PR #330, PR #351.

<a id="zh-jw-stu"></a>

- **汕大周日字母节次与学分**. 汕大保留周日课程、字母编码节次、学分和单双周。字母映射仅适用于声明来源布局。

  依据：PR #330, PR #351.

<a id="zh-jw-nfu"></a>

- **NFU 周日多课**. NFU 同一周日单元格内的多门课程分别保留，保留明确高节次。

  依据：PR #330, PR #351.

<a id="zh-jw-jnu"></a>

- **JNU 采集文档选择**. JNU 使用前置文档之后的实际采集课表文档，保留明确高节次。不把前置旧备份文档混入所选课表。

  依据：PR #330, PR #350.

<a id="zh-jw-swust"></a>

- **SWUST 末七列课表**. SWUST 按末七列星期读取课表，各 lecture 保留自身周次、地点和明确行节次。固定来源没有独立教师字段。

  依据：PR #330, PR #350.

<a id="zh-jw-jmucy"></a>

- **JMUCY 星号分隔课程**. JMUCY 星号分隔课程块分别读取，保留明确高节次。

  依据：PR #330, PR #350.

<a id="zh-jw-nuaajc"></a>

- **NUAAJC 服务端表头**. NUAAJC 按服务端表头定位课程，保留明确高节次。

  依据：PR #330, PR #350.

<a id="zh-jw-qmu"></a>

- **QMU 课程格周次列表**. QMU 只在课程格读取点分周次列表，避免将无关单元格识别为课程。

  依据：PR #330, PR #350.

<a id="zh-jw-javtc"></a>

- **JAVTC 多行标题**. JAVTC 保留多行课程标题与显式节次坐标。

  依据：PR #330, PR #350.

<a id="zh-jw-hitsz"></a>

- **HITSZ 实验与理论卡片**. HITSZ 实验与理论卡片分别读取安排，保留明确高节次。

  依据：PR #330, PR #350.

<a id="zh-jw-xhtd-xytc"></a>

- **XHTD 与 XYTC 字段组**. XHTD 超过十五块的五字段课程组全部读取，XYTC 保留声明的三、六、七、十一、十三字段形态。

  依据：PR #330.

<a id="zh-jw-cidp-sdpei"></a>

- **CIDP 与 SDPEI 节次标签**. CIDP 采用圈码节次跨度，SDPEI 采用教学行标签，保留来源坐标。

  依据：PR #330.

<a id="zh-jw-shu"></a>

- **SHU 课程元数据与时钟**. SHU 将单双周、学分及来源时钟保留在对应课程中。此项与 SHU2024 默认作息表分别处理。

  依据：PR #330.

<a id="zh-jw-cqu-html"></a>

- **CQU 打印课表时钟**. CQU 的 print-schedule-table HTML 保留实际课程时钟及一周七天安排。按打印布局 HTML 标记选择通路，与既有 JSON 通路分别处理。

  依据：PR #330.

<a id="zh-jw-jmpt"></a>

- **JMPT 跨行与单双周**. JMPT 正确结束星期跨行占位，保留来源声明的反向单双周语义。反向单双周解释只用于该来源。

  依据：PR #330.

<a id="zh-jw-lngd-vatuu-yzzy"></a>

- **LNGD、VATUU 与 YZZY 字段**. LNGD 保留小数学分，VATUU 保留表头偏移和括号教师，YZZY 相邻课程名分别读取，并采用对应来源作息。默认作息仅用于匹配的声明类型。

  依据：PR #330.

<a id="zh-jw-huat-seu2017"></a>

- **HUAT 与 SEU2017 字段**. HUAT 保留第五行声明的三节安排，SEU2017 保留教师与学分字段。

  依据：PR #330.

<a id="zh-jw-tju-hqu-changzhou"></a>

- **TJU、HQU 与常州安排**. TJU 按表头和内嵌教室字段读取，HQU 采用最长唯一计划名称及转置坐标，常州形态分别读取斜杠名称、教师和单周次。改动范围为各自声明布局。

  依据：PR #330.

<a id="zh-jw-shu2024-clocks"></a>

- **SHU2024 来源时钟**. SHU2024 导入确认提供完整来源作息时间表。实际抓取作息优先。

  依据：PR #330.

<a id="zh-jw-sicnu-sit-sysu"></a>

- **SICNU、SIT 与 SYSU 网格语义**. SICNU 只为有效续行继承名称，SIT 保留星号单双周及跨行，SYSU 保留周日在前的跨列分组。

  依据：PR #330.

<a id="zh-jw-pku"></a>

- **北大课程块坐标**. 北大有效正坐标不再受旧的任意节次上限裁切，每个课程块分别重置安排状态。

  依据：PR #330.

<a id="zh-jw-sues"></a>

- **SUES 教室分组与时钟**. SUES 完整 API 采集保留教室分组安排及第四节实际时钟。

  依据：PR #330.

<a id="zh-jw-hfu-aliases"></a>

- **HFU 声明字段别名**. 三种声明的 HFU 兼容别名采用对应来源字段与时钟解释。

  依据：PR #330.

<a id="zh-jw-school-table"></a>

- **六类学校表格布局**. HBUT 保留多课和离散周次，HNJM 保留星期与来源时钟，LZ 保留两种形态，NAU 保留动态表头和多地点，NJU 保留卡片时钟与课程身份，CQUPT 保留周次掩码与时长。

  依据：PR #330, PR #342.

<a id="zh-jw-school-grids"></a>

- **声明学校网格行约定**. GXIC、GXNU、JXNU、NNUTC 声明网格分别采用自身星期和节次约定。

  依据：PR #330.

<a id="zh-jw-postgrad-layouts"></a>

- **声明的研究生来源布局**. 声明的研究生 JSON、HTML、CSV 与旧树维形态按自身安排字段及来源作息读取，浙大研究生课表保留明确高节次。声明通路包括 xjtu_post、uestc_post、bfa_post、gdbyxy、cupl_post、ysu_post、whu_post、hrbeu_post、shtu_post、ustc_post、xauat_post、zju_post、csv、shuwei_old。

  依据：PR #330, PR #359.

<a id="zh-jw-shtu2024"></a>

- **SHTU2024 研究生节次**. SHTU2024 研究生布局保留来源课程字段与完整声明作息。

  依据：PR #330.

<a id="zh-jw-bnuz-fallback"></a>

- **北师珠声明空结果**. 北师珠声明布局的空结果保留在本类型内，避免无关回退生成负节次假课。

  依据：PR #330.

<a id="zh-jw-chaoxing-share"></a>

- **超星分享课表采集**. 识别超星分享课表地址通路，采集课程响应，将逐周重复的相同发生记录去重。保留实际时钟和离散周次，教师或地点不同的安排分别保留。

  依据：PR #330.

<a id="zh-jw-chaoxing-groups"></a>

- **超星课程安排分组**. 超星每行课程组分别保留自己的教师、教室、周次和节次。不在无关课程组之间组合字段。

  依据：PR #330.

<a id="zh-jw-chaoxing-legacy-term"></a>

- **超星旧版所选学期**. 超星旧版按明确选择的学期、学生和校区采集，读取稳定顶层文档与可访问 iframe。不以最大编号代替所选学期，请求前后文档与选择须一致。

  依据：PR #370.

<a id="zh-jw-treewise-capture"></a>

- **树维所选学期采集**. 树维六种声明采集类型按所选学期与对应部署前缀读取完整 JSON。三种部署前缀分别保留，门户页没有明确学期时不猜测请求接口。

  依据：PR #330.

<a id="zh-jw-thu"></a>

- **清华学期与调课发生记录**. 清华保留明确学期日历、课程身份、备注和实际时钟，按具体日期保存调课，抑制被替代的节假日原安排。整学期周数须有明确来源标记，不完整课程的跳过数量保留提示；不从课程编号末位猜测学分。

  依据：PR #330.

<a id="zh-jw-buaa"></a>

- **北航教师与周次对应**. 北航各教师只关联自身周次，保留来源学分、备注和实际时钟。同时核对请求身份与页面上下文。

  依据：PR #330.

<a id="zh-jw-json-school-capture"></a>

- **完整学校 API 采集**. 声明的 JSON、API 与分页通路在所选学期、页面和响应身份一致且所需分页完整时交付课表。包括 sustech、shsmu、ygu、zju_post 等 JSON 通路，ccibe、cppu、jlu_post、sues 等 API 通路，以及 fstvc、gdbh 分页通路。

  依据：PR #330.

<a id="zh-jw-shsmu-calendar"></a>

- **SHSMU 课程绝对日期**. SHSMU 来源日期绑定经确认的学期起点，用户修改起点时重新换算周次并保留实际日期。不把当前所选周当作第一周，旧标记载荷继续兼容。

  依据：PR #330.

<a id="zh-jw-ustc-parser"></a>

- **中科大来源节次与嵌套字段**. 中科大保留明确节次或仅有时钟的课程时间、嵌套教师姓名和自定义地点，EAMS5 对应嵌套字段一并保留。lessonCode 不作为节次编号。

  依据：PR #371.

<a id="zh-jw-ustc-capture"></a>

- **中科大页面采集通路**. 手动登录后，中科大按所选学期及返回的学生身份请求 studentTableVm 单对象课表接口。请求限定于 jw.ustc.edu.cn 及相应响应结构。

  依据：PR #373.

<a id="zh-jw-capture-budget"></a>

- **等待真实采集完成**. 异步采集以桥结果为完成依据，隔离过期回调，等待原生交互时暂停执行预算。导航、刷新、返回、切换 UA 与销毁均取消当前采集，JavaScript 求值回调不单独代表完成。

  依据：PR #373, PR #382.

<a id="zh-jw-fdu-parser"></a>

- **复旦本科与研究生字段**. 复旦本科 print-data 的周日课程采用正确教学周，研究生 ZCBH 周次与 KSJCDM 单节按来源语义读取。仅周日采用 weekIndex 减一；旧 HTML 形态继续保留，不补造学分或备注。

  依据：PR #330, PR #374.

<a id="zh-jw-fdu-capture"></a>

- **复旦采集与周一开学基准**. 手动登录后接入复旦本科及研究生采集，将来源周日学期起点换算为 Sleepy 的次日周一。须有来源学期日期依据；此项不提升目录状态或抓取标记。

  依据：PR #375.

<a id="zh-jw-ahu-term"></a>

- **安大所选学期采集**. 安徽大学按明确选择的学期采集，不以最大编号代替选择；响应正文读取后再次核对页面控件。请求期间页面或选择变化时不交付混合结果。

  依据：PR #376.

<a id="zh-jw-ahu-periods"></a>

- **安大完整来源节次**. 安徽大学保留完整十三节来源作息及末尾空节，不为错误列重新编号。抓取时钟优先，默认值只用于匹配的学校和声明类型。

  依据：PR #376.

<a id="zh-jw-sg-periods"></a>

- **校验拾光基础作息**. 拾光读取编号连续完整的基础作息，只合并来源实际提供的配置键。节次在一至三百六十六内连续编号，时钟严格且不重叠；建议时长不重建明确时钟。

  依据：PR #382.

<a id="zh-jw-hnust"></a>

- **湖南科大独立课程块**. 湖南科大同格课程块分别保留教师、教室、元数据和离散周次。当前协议不按 CSS 可见性筛课，明确旧模式分别处理。

  依据：PR #330.

<a id="zh-jw-bjtu"></a>

- **北交教师参与课程身份**. 北交不同教师的安排分别保留，并支持声明的旧表回退。

  依据：PR #330.

<a id="zh-jw-eams5-arrangements"></a>

- **EAMS5 独立安排**. EAMS5 教师或教室不同的安排分别保留，周次位图中的缺口继续保留。

  依据：PR #330.

<a id="zh-jw-sg-course-fields"></a>

- **拾光时钟、学分与离散周次**. 拾光自定义课程时钟、学分与离散周次贯穿课程解析和可编辑草稿。仅在单双周压缩与来源周次集合完全一致时采用该表示。

  依据：PR #330, PR #379.

<a id="zh-jw-source-table-name"></a>

- **来源课表名称建议**. 新的 SHU2024 与匹配 WIST 导入确认采用来源课表名称建议。建议仅初始化新的确认页，不覆盖已编辑草稿名称。

  依据：PR #330.

<a id="zh-jw-frame-capture"></a>

- **声明布局页面采集**. 识别新增来源表格容器，无通用表格标识时用声明学校解析器选择可访问 frame。跨域不可访问 frame 仍不可访问，新版正方 /kbcx/ 仍由最终采集路由处理。

  依据：PR #330.

<a id="zh-jw-source-calendar-fields"></a>

- **来源时钟与学期字段贯穿导入**. 明确来源时钟、学期日期与学期长度贯穿课程解析、可编辑草稿和实体转换。只保留有依据的来源值，默认值限定于对应学校或协议，抓取时钟优先。

  依据：PR #330.

### 性能与本地工具

<a id="zh-console-unlock"></a>

- **本地调试控制台解锁**. 连续点击版本入口七次可打开本地口令输入框。本地与HTTP解锁共用校验和重试退避；关闭、重开控制台时，路由、令牌状态与横幅保持一致。修正Windows路径处理和请求临时文件清理。

  依据：PR #277 (contributor @jim139129), PR #307 (contributor @Kerry1020).

## 已知限制

<a id="zh-limit-android"></a>

- **Android渲染差异**. 完整着色器折射需要Android13及以上。Android12可使用系统模糊，更早版本采用回退材质，不承诺各路径的光学效果完全相同。触觉反馈还取决于设备与系统设置。

  依据：PR #278 (contributor @jim139129), PR #283 (contributor @Kerry1020), PR #294 (contributor @Kerry1020), PR #299, PR #378.

<a id="zh-limit-widget"></a>

- **小组件放置与刷新**. 宽网格至少需要300×160dp。实际桌面尺寸与精确刷新时机受桌面应用、闹钟权限和Android后台限制影响，节次边界刷新请求不代表系统必定准时投递。

  依据：PR #295.

<a id="zh-limit-attribution"></a>

- **仍待确认的来源许可问题**. 已记录的SleepDown导入版本与许可兼容性，以及DataV授权问题仍待确认；补全致谢不代表这些问题已经解决。五十个教务来源复核身份中，三十个有许可声明、十八个许可未知、两个记录许可冲突；研究致谢不代表已集成代码，也不代表授权问题已解决。

  依据：PR #311, PR #395.

<a id="zh-jw-sg-combo-boundary"></a>

- **按日期切换作息的导入边界**. 尚未接入按日期或冬夏时间自动切换作息。内部日期解析器没有导入、存储、界面、小组件、提醒或勿扰的生产消费者，拾光组合作息仍不支持完整保存；请继续使用一套经确认的基础作息。

  依据：PR #382, PR #389, PR #391.

<a id="zh-source-metadata-boundary"></a>

- 来源建议与已确认课表值分别保存。每周第一天目前只保存和显示，日期与周次算法仍以周一为基准。JSON、分享和原生文本保留元数据，不代表 ICS、HTML 导出也保留。三百六十六的边界用于资源保护和校验，不代表学校实际最高节次。

## 验证

- 版本：**1.0.64 / 10064**。
- 发布tag标识本版源码，生产文件与已验证的整合候选一致。
- 测试：**659类，4943项，零失败、零错误，2项跳过**。
- Lint：**590个Warning、40个Hint、零Error、零Fatal**。
- 构建：**Debug与AndroidTest APK构建成功**。
- Android 13渲染：14项检查覆盖居中单行顶栏、经典底色、完整按压轮廓和自由拖动；已查看33张截图。
- 两项跳过测试需要显式提供WakeUP活体服务输入；离线测试不代表新增真实学校认证登录结论。
- 正式APK由tag触发的 `release.yml` 工作流构建，使用项目发布密钥签名。
- APK SHA-256来源：随附的 `sha256sums.txt`。该清单记录四种架构APK的SHA-256。

<a id="zh-engineering-maintenance"></a>

- **构建与兼容性维护**. 更新构建依赖与Android接口；Windows的CRLF敏感测试使用跨平台预期，截图将界面线程窗口采集与后台PNG编码分开，Gradle通过已校验的既有镜像下载，采集器按记录的工具链重建。采集器 cdproto 从 0.158.2 更新至 0.158.6，Go 从 1.27.1 更新至 1.27.3，更新 Linux amd64、macOS amd64 和 arm64、Windows amd64 程序及校验清单。这些是工具维护，不新增学校登录结论。 渲染检查等待弹簧归位及前景重组、绘制完成后采样颜色；顶栏源码契约同步单行布局和两侧控件共同缩放规则。 渲染检查覆盖完整标记区域，在顶栏外采样并保留精确颜色断言。CI检查PR与合并后的主线提交，也支持手动检查指定发布源码，供打tag前核对。

  依据：PR #282, PR #314, PR #334, PR #362 (contributor @jim139129), PR #367, PR #388 (contributor @jim139129), PR #402 (contributor @jim139129), PR #403, PR #404, PR #406, PR #408, PR #409.

<a id="zh-engineering-docs"></a>

- **文档与无用代码维护**. 同步公开文档、已发布版本的问答边界，修正编号并清理过时辅助代码和测试；这些维护不作为新增用户功能。

  依据：PR #281 (contributor @jim139129), PR #297, PR #298, PR #323 (contributor @Sunrongguo2008), PR #360 (contributor @jim139129), PR #363 (contributor @jim139129), PR #388 (contributor @jim139129).


### 本版贡献者

感谢五位外部作者贡献以下三十二个 PR，各 PR 保留记录中的作者归属。

<a id="zh-thanks-jim139129"></a>

- @jim139129: Android 玻璃兼容、控制台、课程与课表编辑、设置、安全边距、采集器维护、作息时间居中、固定节次顺序、渲染测试及文档。 PR #277, PR #278, PR #280, PR #281, PR #329, PR #332, PR #335, PR #344, PR #360, PR #362, PR #363, PR #388, PR #398, PR #402.

<a id="zh-thanks-yyichen"></a>

- @YYiChen: 双指缩放锚点及最近导入学校. PR #287, PR #372.

<a id="zh-thanks-kerry1020"></a>

- @Kerry1020: 作息时间校验、单次课程地点、学校目录状态、Android 玻璃兼容及可追溯调试包. PR #283, PR #289, PR #294, PR #306, PR #307, PR #308, PR #313, PR #327, PR #328, PR #331.

<a id="zh-thanks-sunrongguo2008"></a>

- @Sunrongguo2008: 可审阅教务课表更新、青岛大学目录状态及文档编号. PR #323, PR #325, PR #326.

<a id="zh-thanks-cold577"></a>

- @Cold577: 玻璃主题更新、页面转场底色及独立权限诊断. PR #285, PR #301, PR #324.
