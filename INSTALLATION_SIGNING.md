> **Актуально на 1.0.0-beta4:** пакет `app.niflaot.peleshaon` (QA — `app.niflaot.peleshaon.qa`), телефон `versionCode=7`, Wear OS `versionCode=100007`. Сборки для Google Play подписываются отдельным ключом загрузки — см. [PLAY_RELEASE.md](PLAY_RELEASE.md). Ниже — история проблем с установкой тестовых APK.

# PeleShaon installation and signing compatibility

**Issue confirmed in release history:** the permanent debug signing keystore was
added on **2026-11-16** in commit `097fc2c38d`. Older GitHub Actions debug
builds may have been signed by disposable runner keys. Android will not upgrade
an installed package signed with a different signing certificate.
All checked post-fix APKs use the same signing certificate fingerprint:
`fd6c1ce923deb8f31a558e52d15fb96816e7ebcf06c07e9558675ea1e383c268`.
The signing certificate **on the user's installed APK remains unknown**.

Previous Beta3 APK had `versionCode=2`; current `versionCode=5` can upgrade
older same-signature builds with version codes 1–4. It still cannot bypass
Android signing checks.

There are now **two phone APKs**:

1. **Regular update** `app-debug.apk`, package
   `app.mychitas.peleshaon`. Preserves existing app data when the installed
   package is signed by the same key and version code is not higher than 5.
2. **Safe parallel QA** `app-qa.apk`, package
   `app.mychitas.peleshaon.qa`, launcher name **PeleShaon QA**. It can be
   installed alongside the existing app and **will not delete its data**.
   This QA install has **separate preferences, notifications, alarm schedules
   and widgets**; it is not an in-place upgrade.

**Do not uninstall old app to fix this before backing up data.**
If QA fails as well, capture the full Android installer error or Android
PackageInstaller/PackageManager error from ADB:
`adb install -r app-qa.apk`.
The diagnostic code distinguishes update signature mismatch, version downgrade,
unknown-source device policy and device-specific APK failures.

The Wear OS APK remains in the standard Wear package and must be installed on
a compatible Wear OS device, not on the phone. It shares the same release
versionCode 5.

The app does not request INTERNET permission. The private debug keystore stays
in the repository and is used only for debug/testing; a production release
requires a separately secured release signing key.
