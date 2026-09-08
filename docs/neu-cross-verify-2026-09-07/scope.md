# Northeast University cross-verification scope

- Date: 2026-09-07
- School: Northeastern University (东北大学), NEU
- Change type: repair of existing NEU JW import and ICS section mapping
- Parser family: Wisedu mobile JSON (`/jwapp/`), plus NEU-specific ICS export
- User request: reproduce all bug issues, fix them, then cross-validate
- Current parser entry: `app/src/main/java/com/lingion/sleepy/data/jw/JwNeuParser.kt`
- WebView entry: `app/src/main/java/com/lingion/sleepy/ui/screen/imports/JwWebViewLoginScreen.kt`
- Current finding: JSON parser and registry exist; WebView capture dispatch lacks a `TYPE_NEU` fetch branch.
