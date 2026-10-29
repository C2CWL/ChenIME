# 尘输入法 / Chen IME 0.1.0-chen

Chen IME is a Trime fork at <https://github.com/C2CWL/trime>.
Both `applicationId` and Android `namespace` are `com.c2cwl.chenime`.
Debug builds install as `com.c2cwl.chenime.debug`. Kotlin/JNI class packages
remain `com.osfans.trime`; manifest components and IME settings use their actual
fully qualified class names. Build plugin IDs and JNI keep rules retain their
upstream names. Chen IME can be installed alongside Trime.

白霜拼音 is the only switchable bundled schema for a fresh installation.
Its melt_eng, radical_pinyin and rime_frost_aux dependencies remain bundled. Existing user schema selections are preserved. The bundled
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

## Bundled key sounds (CC0)

清脆、机械、轻柔 are original synthesized clicks, dedicated to the public domain
under CC0-1.0. No third-party recordings, keyboard samples, or downloaded sounds
are used. The generator and its WAV/YAML outputs are CC0-1.0:
`python3 tools/generate_key_sounds.py` regenerates all files deterministically
using only Python's standard library. Each mono PCM WAV is 22050 Hz, 16-bit,
and under 60 ms; space, enter, and backspace use a lower-pitched variant.

Assets in `app/src/main/assets/rime/soundeffect/` are included by the existing
`generateDataChecksums` build task and copied by `DataManager.sync()` to
`rime/soundeffect/` in app external files storage. `SoundEffectManager` scans
that directory for `*.sound.yaml` (also migrating the legacy `rime/sound/`
directory), displays each descriptor's `name`, and resolves audio via `folder`.
No sound defaults or existing user preferences are changed; sound remains off
by default.

启用路径：尘输入法设置 → 虚拟键盘 → 开启「按键声音」→ 开启「使用自定义按键音效」
→「自定义按键音效」→ 选择「清脆」「机械」或「轻柔」。将「按键音量」设为大于 0
的值（自定义音效在音量 0 时静音）。
