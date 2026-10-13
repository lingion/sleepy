# ECUPL course-import protocol audit (2026-10-13)

The school's directory entry uses classic_eams. Verified page shapes:

1. TaskActivity/index = D*unitCount+P plus table0.marshalTable and listTable metadata. Only script supplies weekday/period. Metadata alone is insufficient. Delegate to classic EAMS parser.
2. CourseTable(start date, minute pairs), eight-argument newActivity and addActivityByTime(activity,day,startMinute,endMinute). The seventh argument is date; eighth is decimal Long week bitmap. Match minutes to exact matrix pairs. Route this shape narrowly from classic_eams to ECUPL script parser.

## Fixed source evidence

LonelyMarch/OpenWakeUp commit 9c2d0436d74e1a8a9925c009bb80e1a15df4d854, EcuplParser.kt, SHA-256 932043438e938ca1d757a9f7d20c620aa3460abd0aea640faec74b1435dbcd62, AGPL-3.0: direct second-shape evidence. Current implementation independently encodes protocol facts.

Historical WakeUp APK smali archived in lingion/wakeup-mod commit 879f0d6781da19d1da9300d5eda6b0c2629b6a63: ECUPLParser.smali SHA-256 74867b1f4795c64004929a7f500ce4b8ef33b1adb7b40edd3a9ec7dd8c292111; parent OooOOO.smali SHA-256 24b917e0c3be058d8e51f87f899d198fd8a5f9ab3ed0af7360e6ab458bfd8901. Parent supplies TaskActivity/index coordinates; child reads listTable metadata. Original author license is unconfirmed. Do not attribute these smali files to YZune/WakeUpSchedule or claim Apache-2.0 coverage.

## Candidate verdict

zimo0o0omiz/wisedu-unified-login-api, only9464/HEU-Wisedu, CreamPig233/neu_wisedu2wakeup, wisedu/bh-mobile-sdk and ershiyidian/WakeUp_SHU do not directly establish ECUPL timetable coordinates. Retain them as research comparators while removing unsupported direct-protocol claims, star counts and five-repository consensus.

## Regression boundaries

Checked-in fixtures are synthetic representations of observed protocol shapes, not captured student data. Tests cover metadata-only rejection, multiple slots, decimal sparse bitmap/date offsets, exact minutes, overflow, separate scripts, and the production classic_eams route. Live authenticated page variants remain unverified.
