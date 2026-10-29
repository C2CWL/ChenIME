# Liquid keyboard label and responsiveness audit

Compared working sources with `cd64b752` (v0.1.1), `5b2cf3a0`, and the locally available `upstream/develop` ref. No network fetch was performed.

## Findings and changes

- 标准 (`tongwenfeng.trime.yaml:887`): the six bottom actions already existed at v0.1.1. Upstream also uses a six-action, content-width row. `Return2` now has `ic@keyboard_return`; `LiquidLayout` still assigned it directly as text. The long literal plus six content-width keys can overflow; the return action was configured, not deleted. Changed to four equal-width actions: 返回 / 空格 / 退格 / 回车.
- New布局 (`iflytek.trime.yaml:500`): v0.1.1 used a left bar with 返回 / 空格 / 退格 / Return. HEAD moved it to the bottom, added lock, moved 返回 to the end and removed Return. Restored the same four-action bottom row as 标准. This also removes the theme's lock-dependent resize/rebind path and automatic return-after-symbol behavior; symbols stay open until 返回 is pressed.
- `LiquidLayout.kt`: horizontal actions now have constrained equal widths, a full-width container and a minimum 40dp row height, with 18sp labels. Icon labels render using an ImageSpan, centered with text-sized bounds and current text tint; missing icons use readable fallback text. Recoloring rebuilds the tinted span.
- `LiquidItemUi.kt`: ordinary TextView replaces this panel's custom AutoScaleTextView. Framework text layouts support emoji spans and icon spans. Unchanged labels skip assignment; recycled holders reuse their views. `LiquidAdapter` additionally warms a bounded 512-label PrecomputedTextCompat cache on Default once at resident-view creation.
- `LiquidKeyboard.kt`: tags and tab-menu entries are prepared once with the loaded theme; switching tabs returns existing key lists rather than rebuilding arrays/lists.
- `LiquidWindow.kt`, `LiquidTabsUi.kt`: retain ListAdapter diffing, stable adapter instances, view recycling, fixed-size RecyclerViews and disabled item animations. Guard the initial selected-tab index; custom lock layouts use range updates instead of whole-adapter invalidation. Color-scheme changes intentionally rebind colors.
- `BoardWindowManager.kt`, `InputView.kt`: already cache/register LiquidWindow, precreate resident roots after the first keyboard frame, and attach with visibility changes without animation, consistent with `5b2cf3a0`; unchanged. ThemeManager loads YAML through Dispatchers.IO before publication. Liquid open/tab selection performs no YAML/file decoding or Rime calls. SymbolHistory already loads once on IO; history selection awaits its cached snapshot. Rime work in symbol input is click-time work, not panel opening.

## Rendering audit

| Place checked | Result |
| --- | --- |
| All bundled theme YAML: `trime.yaml`, `tongwenfeng.trime.yaml`, `iflytek.trime.yaml`; all other asset YAML searched | `ic@` occurs only in the latter two themes, in toolbar styles and keyboard/action labels. No other bundled theme has an icon label. |
| Liquid fixed actions | Raw text bug fixed through shared parser/render helper. Applies to every theme, including `trime.yaml`. |
| Liquid tabs and TABS menu/grid, emoji, mathematical and other symbol pages | Shared helper used for tabs and all liquid cells; underlying committed symbol text stays literal. |
| Number keyboard and `Keyboard_editor` text-editing keyboard | Built through the regular keyboard/KeyView path, which already resolves icons. No changes to that path. |
| Clipboard window title/tabs/actions and clipboard context menu | Resource text and Android drawable resources; clipboard content is user data, not theme labels. No theme icon string consumer found. |
| Switch/option window and More ⋯ panel (`SwitchOptionWindow`, `SwitchOptionEntryUi`) | Static resource labels/icons and Rime schema switch-state text, not theme preset-key labels/styles. No bundled `ic@` source reaches these text fields. |
| Candidate bar actions (`ToolButton`, `ButtonsBarUi`, `AlwaysUi`, `TabUi`) | ToolButton already resolves icon styles; added safe failure handling and shared parser fallback, including when its action label is itself `ic@`. Native icons remain native. Titles/candidates are ordinary text. |
| Popup/long-press keyboard (`PopupKeyboardUi`) | Already detects icon labels and renders IconicsDrawable; no raw-label bug found. |
| Key preview and capsule (`PopupEntryUi`, `PopupDelegate`) | PopupEntryUi already detects icon labels and uses IconicsDrawable. Read only, as requested. |
| Other candidate, composition, segment and clipboard content views | Render Rime/user content rather than theme action labels; intentionally remain literal. |

## Verification limits

`git diff --check` passes. Pure parser tests added in `ThemeLabelTest.kt` for ordinary strings, prefix-only detection, namespace conversion, missing/empty icon fallbacks and preventing icon syntax in fallback text. Gradle/Spotless/unit tests were not run, per sandbox constraint. Device layout, icon appearance, narrow/landscape widths and scroll frame times still require device validation; smoothness is not benchmarked here. No changes to the prohibited per-key drawing/touch/preview/font/feedback/history paths. No commits or tags created.
