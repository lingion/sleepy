# Findings

The original main parser inferred weekday/period from metadata rows and course-name hashing. Metadata rows lack those coordinates; synthetic placement was removed. The historical parent parser reads TaskActivity plus index = D*unitCount+P; ECUPL child enriches metadata. The current implementation delegates schedule extraction to JwClassicEamsParser only when TaskActivity, marshalTable and listTable are present.

OpenWakeUp's EcuplParser reads start date, a matrix of minute pairs, eight-argument activity, decimal Long week bitmap and addActivityByTime. The prior implementation treated minutes as lesson numbers and misread activity field order. The current parser uses exact matrix lookup and rejects invalid minute pairs or out-of-range weeks.
