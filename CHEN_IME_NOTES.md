# 尘输入法 / Chen IME 0.1.0-chen

Chen IME is a Trime fork at <https://github.com/C2CWL/trime>.
Both `applicationId` and Android `namespace` are `com.c2cwl.chenime`.
Debug builds install as `com.c2cwl.chenime.debug`. Kotlin/JNI class packages
remain `com.osfans.trime`; manifest components and IME settings use their actual
fully qualified class names. Build plugin IDs and JNI keep rules retain their
upstream names. Chen IME can be installed alongside Trime.

白霜拼音 is the first schema for a fresh installation, with 明月拼音 and 笔画
fallbacks. Existing user schema selections are preserved. The bundled
讯飞深色 theme is selectable in Settings. See `THIRD_PARTY_RIME_FROST.md`
for the bundled subset and upstream license.

## Backup and restore

Settings → Configuration → Synchronization offers 导出用户数据 and 导入用户数据
(localized in English and Traditional Chinese). Export uses Android's document
picker to create `chenime-backup_YYYYMMDD.zip`. It contains the Rime user directory
and typed app SharedPreferences, including theme preferences. Rime is closed and
flushed while files are copied. ZIP backups are unencrypted.

Import validates the format, package family, preference types, entry paths,
entry count, and decompressed size before modifying live data. It stages a full
replacement and retains the previous directory until the file/preference update
succeeds. Invalid input does not change current user data. Import then deploys
Rime, reloads the theme, and recreates Settings. A deployment failure leaves the
restored files available for correction and redeployment.

Restore switches to application storage and disables periodic external sync.
SAF folder grants cannot be transferred between installations: select an external
folder again when ready. Backups can move between Chen IME debug and release
installations. Other IMEs' ZIP formats are not supported. Limits: 1 GiB of user
files, 100,000 entries, and 2 MiB of preference metadata. Clipboard/collection
databases outside the Rime user directory are not included. Abrupt process death
during the directory swap is not automatically recovered; a retained
`.chenime-previous-*` sibling contains the previous data if rollback was unable
to finish. Device testing of the SAF flow is still required.

## Signing and builds

Release builds use the existing `ProjectExtensions.signKey*` configuration from
root `keystore.properties`. `local.properties`, `keystore.properties`, and the
keystore are ignored and must stay local. No signing credentials are in source.
The `v0.1.0-chen` tag triggers `.github/workflows/release-ci.yml`.
GitHub release signing requires `SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, and
`KEY_PASSWORD` repository secrets; locally available signing files do not set
those secrets automatically.
When any signing secret is missing, the release workflow builds unsigned APKs
and labels the signing status in the GitHub Release. Those APKs require signing
before installation. The release job has permission to upload its APKs and notes.

Useful checks: `make style-lint`, `make check`, and `make release`.
Use `BUILD_ABI=arm64-v8a make release` for a single-ABI local APK.
