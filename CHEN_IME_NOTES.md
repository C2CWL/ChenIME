# 尘输入法 / Chen IME 0.1.0-chen

Chen IME is a Trime fork at <https://github.com/C2CWL/trime>.
Both `applicationId` and Android `namespace` are `com.c2cwl.chenime`.
Debug builds install as `com.c2cwl.chenime.debug`. Kotlin/JNI class packages
remain `com.osfans.trime`; manifest components and IME settings use their actual
fully qualified class names. Build plugin IDs and JNI keep rules retain their
upstream names. Chen IME can be installed alongside Trime.

白霜拼音 is the only switchable bundled schema for a fresh installation.
Its melt_eng and rime_frost_aux dependencies remain bundled; the radical_pinyin
拆字 lookup and the chaifen OpenCC comment data were removed. Existing user schema selections are preserved. The bundled
New布局 theme is selectable in Settings. See `THIRD_PARTY_RIME_FROST.md`
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

## Custom font and I/l labels

路径：尘输入法设置 → 键盘样式 → 选择字体 / 恢复默认字体。
SAF 导入字体上限 50 MiB，先验证 Typeface，再存入 Rime 用户目录
`fonts/chen_custom.ttf` 或 `fonts/chen_custom.otf`；摘要显示该文件名。
AppPrefs 的 `chen_custom_font` 覆盖 ThemeFonts 的全部字体角色，并重建输入视图。
Android 29+ 使用 CustomFallbackBuilder 和系统 sans-serif 补缺字；更旧系统
使用普通 Typeface，缺字依赖 Android 的系统回退行为，需在设备上确认。
恢复默认会清空偏好并删除已导入文件，重新使用主题字体。

未启用自定义字体时，精确为 `I` / `l` 的主键标签、长按选项与按键预览
使用原创 Canvas 轮廓，按样本 A 绘制：I 上下等宽横杠，l 左上旗标和右下弯尾。
只改变显示，提交字符仍为 ASCII I/l；启用自定义字体后尊重字体自身字形。
没有新增捆绑字体，也没有第三方字体授权依赖。

## Custom phrases, candidate menu and key previews (v0.1.2)

路径：尘输入法设置 → 配置 → 自定义词组。支持按编码/词组搜索、新增；点击条目编辑或删除。
候选栏/展开候选长按 → 添加为自定义词组，自动填入候选文本与 Rime `getRawInput()`，
编码和权重均可编辑。原「忘记该词」保留；菜单为随键盘主题配色的横向胶囊按钮。

`rime_frost.schema.yaml` 已有 `table_translator@custom_phrase`，配置为
`user_dict: custom_phrase`、`db_class: stabledb`、`initial_quality: 99`，实际读取
Rime 用户目录 `custom_phrase.txt`，无需新增 schema patch。文件不存在时沿用捆绑文件
（含头部和示例）；保存使用原子写入，仅修改目标行或追加新行，保留其他词条、注释、头部。
保存时使用现有 `withUserDataClosed` 避免数据库覆写，然后调用 `deploy()` 应用/同步。

格式为 `text<TAB>code<TAB>weight`，权重默认 1（数值越大排序越靠前，并非绝对候选序号）。
已检查仓库内 librime `dict/tsv.cc` 和 `dict/table_db.cc`：按物理行及 Tab 分列，
没有换行转义解码。编辑器因此明确限制单行，不允许文本/编码包含 Tab、CR、LF；
字面 `\n` 会原样输出，不会变成换行。编码可含音节间空格。原文件未知格式行保持原样。
现有 ZIP 备份遍历整个 Rime 用户目录，无排除词组文件；新增测试验证 `custom_phrase.txt`
导出/导入后内容相同。

无可见角标/提示的主标签（含图标）忽略主题/键盘级 Y 偏移；显式单键偏移（包括 0）保留。
关闭角标/提示后对应主标签居中；按下时的 `key_press_offset_y` 仍保留。
按键放大预览此前因 `popupOnKeyPress` 默认 false 而关闭；现在默认 true，
独立标记 `keyboard_preview_defaults_v1` 一次修复旧 `show_key_popup` 值。
旧版本自动保存的 false 与用户主动 false 无法区分，所以该次升级会重置一次；此后尊重用户开关。
预览使用主题 `preview_back_color` / `preview_text_color`（缺失时回退 popup 配色），圆角和阴影，
保留字母大小写及 I/l 字形规则，长按选项键盘仍使用原逻辑。
退格上下滑分别显示独立的「清空」/「撤销清空」胶囊，关闭普通按键预览时仍显示；
回滑到起点范围立即隐藏，松手取消，不触发清空或退格。动作预览保留完整文字。
预览位置从窗口坐标转换至 popup root 坐标，并限制左右边界。

验证脚本：`/tmp/chen-v012-verify.sh`（独立 Kotlin 编译、测试、ktlint、XML/YAML 解析、diff 检查）。
未运行完整 Android APK 构建或设备 UI 测试；实际弹窗、输入连接及浅/深色外观仍需设备确认。

撤销/重做追加修复：两主题合并后的 `undo` / `redo` 预设已用测试确认存在，
工具栏与编辑键共用动作解析。旧 hook 只接受完全等于 `META_CTRL_ON` 的状态，
漏掉 Ctrl+Shift+Z 和带左右 Ctrl 标志的事件；默认关闭 hook 时还会经过 Rime。
现在 Ctrl+Z、Ctrl+Shift+Z、Ctrl+Y 在 Rime 前统一处理。
优先使用内存中的单次提交历史（最多 4096 UTF-16 单元），仅在光标前文本及前后上下文
仍匹配、无选区时删除该提交；重做按相同上下文重新提交。新提交替换历史，输入会话开始时清空。
此最小实现不记录删除、选区替换或任意应用编辑；上下文不匹配则丢弃历史。
无可用历史时保留原 `hookCtrlZY` 原生菜单偏好，再发送带通用及左侧 Ctrl/Shift 标志的
Ctrl+Z / Ctrl+Shift+Z。发送事件无法判断应用是否真正修改文字，因此不会与成功的历史操作叠加。
独立 Kotlin 编译、ktlint、49 项测试、XML/YAML 解析及 `git diff --check` 已通过；
未做设备测试，应用自身不支持快捷键且无可用提交历史时仍可能无法撤销。


Empty-editor restart follow-up (v0.1.2-chen): `InputView.startInput` previously
lost `restarting` at the broadcast boundary, so `KeyboardWindow` treated a same-field
restart as a new input and reselected a pad using the possibly changed inputType.
The restart flag now reaches the keyboard: restarts perform no panel attachment,
lock reset, or ASCII-mode write. This preserves letters or a deliberately selected
number/editor pad after Send, backspace, swipe clear, or select-all/delete.
Only genuinely new numeric/phone/datetime starts automatically select numbers.
The remaining `.ascii` fallback to `lastLockKeyboardId` is removed; missing
`ascii_keyboard` now keeps the current layout. Selection updates, clear/restore,
and `ClearedTextSlot` do not request panel or language changes; ASCII callbacks
only refresh keys/reset Shift. Theme reset-ASCII settings do not reset language
on focus/restart. `clearComposition` also snapshots the current language-tip label
before emitting its empty response, preventing an unchanged mode from replaying
a stale En tip. The existing quiet panel ASCII setter remains in use.
Decision tests cover new focus from both pads and Send/deletion/restore restarts
with changed input classes and both user language modes. Device/app callback
sequences and visible toast behavior still require device verification.

Committed-text swipe-clear reliability follow-up: the clear handler previously
returned without acting on null/partial ExtractedText, and trusted successful
selection/commit calls without checking remaining text. No cached ExtractedText
was used here; currentInputConnection is still obtained inside the action job.
Clear now validates a fresh full-range selection, re-queries after deletion,
then tries context-menu selectAll/empty commit and at most 256 surrounding-text
deletions (4096 UTF-16 units per side per call). No batch edit hides intermediate
verification. Known deleted text goes to ClearedTextSlot; unavailable text or
unverifiable progress is not invented for restoration. Composition still uses
Rime clearComposition and never enters the committed-text strategy.
The backspace vertical threshold is capped at 24px independent of swipe speed
and the 48px horizontal step. Vertical movement wins before horizontal deletion;
horizontal delete requires a dominant horizontal axis. Movement cancels pending
repeat, and a vertical swipe can take over an already-running repeat. Release
coordinates are classified even without a final MOVE. Parent interception is
disallowed for these keys; actual ACTION_CANCEL still cancels safely. Cancelling
repeat cannot asynchronously dismiss the new clear/restore capsule. Shared code
covers both themes and keeps return-to-origin cancellation.
Validation: /tmp/chen-v012-verify.sh passes (59 tests, standalone Kotlin compile,
ktlint, four YAML and three strings XML parses, git diff --check). Eight additional
ClearTextStrategyTest/DeleteSwipeDecisionTest cases pass separately. Actual app
InputConnections, physical gesture timing, and capsule rendering need device testing.


Theme follow-up: the display name is now New布局 (including palette/layout
names); the resource and preference ID remains `iflytek.trime`, and fallback
selection is unchanged. Settings uses the loaded YAML name. The `selected_theme_summary` string in
values, zh-rCN, and zh-rTW now lists New布局 and 标准.

标准 declared `default` and `letter` without `lock`, which defaults to false;
its number panel alone was locked. New布局 explicitly locked `default`.
After visiting numbers, implicit empty-target returns could therefore resurrect
numbers even after returning to 标准 letters. Both 标准 letter layouts now lock.
Neither theme inherits preset keyboards from trime.yaml; neither default has an
ASCII target, and 标准 explicitly declares ascii_mode: 0. Map order is not used
for default/ASCII selection. Loaded-theme tests exercise focus and empty-field
restarts plus implicit/ASCII returns with a stale number lock in both modes.

Uppercase follow-up: Shift had a composing override sending apostrophe in both
themes, so pressing Shift again inside uppercase composition inserted a separator.
Remove that override. Shifted Chinese-mode letters also took KeyAction.getText ->
onText -> simulateKeySequence, losing Shift and bypassing normal key fallback;
now letters consistently use onKey's uppercase keysym plus Shift in both modes.
Explicit separator actions and literal popup commits remain available. No
post-commit space preference or trimming is added. Device verification of the
reported CL sequence is still required; static tests cannot prove app text/toasts.

Follow-up validation: `/tmp/chen-v012-verify.sh` passed: ktlint, standalone Kotlin
compilation, 59/59 tests, four YAML and three locale XML parses, and
`git diff --check`. No APK/device validation, commit, or push was performed.


### v0.1.2 拼音纠错回归修复

- 菜单：尘输入法设置 → 配置 → 拼音纠错。默认关闭，仅控制雾凇拼音原生纠错。
- cd64b752 捆绑的 `rime_frost.custom.yaml` 将 `translator/enable_correction`
  设为 true。`script_translator.cc` 据此加载 corrector；当前
  `corrector.cc` 实际返回 `NearSearchCorrector`，通用编辑距离实现被 `#if 0`
  禁用。邻键表包含 s↔d，syllabifier 搜索容差为 5，纠错 credibility 固定
  为 log(0.01)，没有可配置的严格排序保证。词频、用户词和造句仍参与选择，
  因此该惩罚不足以保护合法原拼写。本次未在设备上复现所列候选次序。
- schema 中模糊音规则和一部分 derive 被注释，但 ü/v 兼容、字母换位等多条
  derive 仍生效；不能笼统说 spelling algebra 全部关闭。这些规则保留。
- 原生 syllabifier 先构建不含 native correction 的图；能完整解析则直接使用，
  不让 native correction 参与排名。不能完整解析时，也只在没有原拼写前缀的
  位置纠错，因此不替换已有合法音节。这比降低纠错词权重更强；它也意味着
  拼成另一个合法音节的错字不会由这个开关纠正。
- `DataManager.sync()` 按校验和更新 shared 资源，不保证覆盖用户目录中的 custom。
  每次 `startRime()` 在资源同步后，按持久化开关值修补用户目录的
  `rime_frost.custom.yaml`，只修改自有的扁平 patch key，其他 patch 保留
  （librime 序列化会规范化格式/注释）。首次升级没有新开关值即 false；已明确
  开启则保留 true。写入采用临时文件再 rename；解析失败不覆盖。配置变化
  强制 fullCheck，重新生成 build，而不是仅更新 APK 的 shared 文件。
  设置切换会调用 deploy，外部目录同步在补丁之前执行。
- JVM 回归断言捆绑 YAML 默认 false；native 回归覆盖合法输入不含纠错边、
  无效拼写仍能纠错、true→false 迁移、保留无关 patch、幂等及明确开启。
- 验证：`bash /tmp/chen-v012-verify.sh` 通过，59/59 测试成功，包含 ktlint、
  Kotlin 编译、YAML/XML 解析和 diff whitespace 检查。
  Native 测试尚未执行：本机没有 host g++，尝试 NDK clang 的 host CMake
  检查失败，缺少 host `-lstdc++`。没有运行 Android 全量构建或设备候选验证。
- 待设备人工回归（默认关闭、开启后各一次，重新部署）：
  `sihuadu` → 丝滑度，`nihao` → 你好，`shijian` → 时间，
  `dianhua` → 电话，`women` → 我们。另从 v0.1.1 用户 custom=true 和已有
  build 升级，确认开关默认关且部署后为 false；自定义无关 patch 应仍存在。
  丝滑度不是捆绑词库中的独立词条（丝滑存在），还需验证造句效果。
- 注意：librime 是 git submodule，`.gitmodules` 设置 `ignore = dirty`。
  本次原生改动在其 `src/rime/algo/syllabifier.cc`、`test/syllabifier_test.cc`、
  `test/config_test.cc`，须用 `git -C app/src/main/jni/librime diff` 审阅。


### 顶栏语音输入

- 两套主题复用现有 `VOICE_ASSIST` 动作，麦克风位于隐藏按钮左侧；标准布局保留中英、全半角等额外按钮。New布局顺序为 ⋯、↶、↷、编辑、剪贴、表情、语音、隐藏。
- 两套按钮宽 34dp、高 40dp，间距 2dp。New布局总宽 284dp，标准总宽 356dp，可容纳于 1080px / 3× = 360dp；隐藏按钮继续由独立右侧约束固定，候选区右侧展开箭头行为不变。
- 复用首选语音 IME 设置，仅从已启用 IME 的已启用 subtype（含隐式启用）查找 voice；API 28+ 使用 `InputMethodService.switchInputMethod(id, subtype)`，旧版本使用带窗口 token 的 guarded fallback。
- 无语音 IME 或切换失败时，通过不导出的透明 `VoiceInputActivity` 启动系统 `ACTION_RECOGNIZE_SPEECH`，结果提交至启动时的 InputConnection；取消不提交，无可用识别器时提示「未找到可用的语音输入」。未增加 `RECORD_AUDIO` 权限。
- JVM 测试覆盖两套工具栏顺序、麦克风样式及 360dp 宽度预算；实际语音 IME 切换、识别结果回填、取消、无提供器和候选展开仍需设备验证。
- 本次验证：`bash /tmp/chen-v012-verify.sh` 通过，59/59 测试成功，含 Kotlin 编译、ktlint、YAML/字符串 XML 解析；另行解析 `app/src/main/AndroidManifest.xml` 通过，`git diff --check` 通过。未执行完整 APK 构建或设备验证。


### Panel smoothness and permanent M symbols follow-up

- More and clipboard now keep their views/adapters for the InputView lifetime.
  Attach refreshes option snapshots and Room Paging flows; detach cancels collectors.
  InputView replacement/destroy releases resident windows and cached paging jobs.
- More builds option entries off Main using suspending RimeApi calls, cancelling
  obsolete snapshots. More/liquid use DiffUtil; clipboard retains Paging DiffUtil.
  Rows restyle only when the ThemeScope color revision changes, including retained
  off-screen panels. Clipboard pages share a recycled pool and prefetch rows.
- Image paths, bitmap decode and source nine-patch conversion run on IO; cached
  ConstantState creates independent drawables. Theme images may appear after the
  initial async load; completion refreshes colors. Theme replacement clears caches.
- Symbol history uses a shared in-memory copy, one IO load and serialized debounced
  IO writes, with no file access on panel open or symbol tap.
- IME show/configure/panel attach requests the highest same-resolution display mode
  with preferredDisplayModeId/preferredRefreshRate (API 23+). API 35+ also uses the
  ordinary View frame-rate hint; this IME owns no SurfaceView on which to call the
  API 30 Surface.setFrameRate API. Hiding releases the preference. Device policy can
  still override the request; actual 120Hz and frame pacing need device profiling.
- Panel transitions target only window roots (180ms); outgoing alpha fade is removed.
  Same-window attach returns immediately. No persistent hardware layers are added.
  KeyView already invalidates only the pressed key and reuses Paint/Rect drawing state.
- This supersedes the earlier uppercase/M separator notes for New布局: M always
  shows 、' and offers M, m, 、 and ' in its long-press popup, in both modes.
  Symbols commit literally; popup letters retain the earlier Rime routing while
  composing. M has no composing separator action or swipe symbol. Shift becomes
  分词 while composing. 标准 retains its existing M question-mark popup and Shift.
- Letter/number swipe symbol output remains disabled. Backspace swipe-up clear and
  swipe-down restore are unchanged. Flexbox nesting remains a profiling candidate.
- Validation: `/tmp/chen-v012-verify.sh` passed with standalone Kotlin compilation,
  ktlint, 59/59 tests, four YAML files, three locale strings XML files and
  `git diff --check`. AndroidManifest.xml also parses. No full APK build or device
  frame-time measurement was performed; no commit/push or secret-file reads.
