# Repository workflow

- The user authorizes pushing completed improvements to `Kelejun/XATUHelper-Android` on GitHub. Commit and push each completed improvement; preserve remote history and never force-push.
- Settings must show `versionName (short commit)` and a link to `https://github.com/Kelejun/XATUHelper-Android`. `BuildConfig.GIT_COMMIT` is derived from Git HEAD at build time; do not hard-code a previous hash into source.
- Bump `versionCode` for each delivered update and choose the corresponding semantic `versionName` for user-visible changes.
- Validate changes, commit the final source, then rebuild/package from that commit so the APK displays the commit actually pushed. If source changes during final validation, commit those changes and rebuild again before delivery.
- Run relevant unit tests and lint; use the isolated QA package for device tests. Do not reset the user's production app data.
- Keep APKs, generated reports, local SDK paths, credentials and signing keys out of Git. The remote intentionally removed historical validation documents; do not restore them during source synchronization.
