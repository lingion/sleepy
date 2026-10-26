# Current code state

JwEcuplParser requires real TaskActivity and marshalTable before delegating; JwEcuplScriptParser pairs each activity only with following slots in its own script, preserves multiple slots, maps exact minute boundaries, and compacts sparse weeks without filling holes. JwEcuplSuperParser merges only identical JwCourse records. JwImportViewModel routes classic_eams to script parser only when complete ECUPL markers appear.

The focused ECUPL and attribution test suite, complete debug unit tests, lintDebug and assembleDebug passed offline before integration with main 5b69372b. Full post-merge verification is required.
