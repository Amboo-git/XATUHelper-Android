# Repository workflow

- The user authorizes pushing completed improvements to `Kelejun/XATUHelper-Android` on GitHub. Commit and push each completed improvement; preserve remote history and never force-push.
- Do not automatically create or publish GitHub Releases, upload release assets, or update existing Releases. Release publication requires an explicit user request; automatic source commits and pushes remain authorized.
- Settings must show `versionName (short commit)` and a link to `https://github.com/Kelejun/XATUHelper-Android`. `BuildConfig.GIT_COMMIT` is derived from Git HEAD at build time; do not hard-code a previous hash into source.
- Bump `versionCode` for each delivered update. Ask the user before changing `versionName`; keep the current version name unless the user explicitly chooses a new one.
- When delivering APKs, validate changes, commit the final source, then rebuild/package from that commit so the APK displays the commit actually pushed. If source changes during final validation, commit those changes and rebuild again before delivery.
- Run relevant unit tests and lint; use the isolated QA package for device tests. Do not reset the user's production app data.
- Keep APKs, generated reports, local SDK paths, credentials and signing keys out of Git. The remote intentionally removed historical validation documents; do not restore them during source synchronization.

- When the user explicitly requests a Release, build and publish four separate APKs for `arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`; do not publish a universal APK. Use `scripts/package-release.py` and its generated `release-notes.md` for GitHub Releases. Include this bold sentence, substituting the actual version for `xxxxx`: **对于大多数用户，如果你不知道该下哪个，请下载xatune-xxxxx-arm64_v8a-devsigned.apk**. Development-signed filenames are lowercase `xatune-<version>-<abi>-devsigned.apk`, with ABI hyphens replaced by underscores.
