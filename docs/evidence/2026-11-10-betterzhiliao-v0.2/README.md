# BetterZhiliao 0.2 phone validation

`phone-validation.json` records the installed production artifact, settings preservation, native UI checks, and explicit live coverage limits. `runtime-events.jsonl` contains only selected module events with process identity removed.

The final installed APK was compared byte for byte with the frozen 14:30 production build. All 48 old preference values were preserved; all three premium controls are OFF and both new ad controls are ON. The new module is enabled only for Zhihu/user0; the old package is absent. The phone ended securely locked with screen off.

Question filtering has a visible paid-versus-ordinary comparison. Browser collection removal and explicit paid-target preservation were observed. Recommendation paid-positive behavior and rare-ad causal suppression were not verified live. Native ad config was absent during bounded checks, and the user permitted skipping rare-ad reproduction. The temporary probe invoked no ad methods and was removed.

Private UI captures and temporary runtime files were deleted after the sanitized evidence was saved. Original APK and narrow module-preference/scope rollback exports remain private backups. See the premium investigation history for the separately established preview-only delivery evidence.
