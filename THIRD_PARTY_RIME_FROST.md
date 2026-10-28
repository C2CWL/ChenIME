# 白霜拼音 / Rime Frost

Chen IME bundles a curated offline subset of
[gaboolic/rime-frost](https://github.com/gaboolic/rime-frost), revision
`7ced7f8139d6fd11a1a15d6c1412f5c14ca0da3f`.
Upstream authors include gaboolic, Dvel, and the contributors credited in the
individual files. The upstream distribution is licensed under GPL-3.0; its full
license is included at `app/src/main/assets/shared/rime_frost/LICENSE`.
Preserve the individual dictionary, Lua, and OpenCC copyright/license notices.

The subset includes the `8105`, `base`, `ext`, `others`, and `corrections`
dictionaries; English and mixed Chinese/English data; auxiliary and radical
lookup; the Lua modules referenced by the full pinyin schema; `symbols_v.yaml`;
the `zh-moqi.gram` grammar model; and the converters referenced by that schema.
It omits Tencent, optional extended character tables, cell dictionaries, and raw
corpora. These assets total approximately 32 MiB before APK compression.

Local adaptations:

- `rime_frost.dict.yaml` imports only the shipped dictionaries.
- `lua/corrector.lua` falls back to the shared data directory when looking up
  the bundled corrections dictionary.
- Tab-separated pinned candidates in the schema are quoted for YAML portability.
- The simplified-to-traditional converter and its binary dictionaries use the
  `frost_` prefix to coexist with Trime's generated OpenCC data.
- The curated OpenCC source files live at `app/data/rime-frost/opencc/`.
  `installOpenCCData` copies them into the generated shared OpenCC directory
  after installing Trime's converters, including after `clean`.

All other Frost files reside alongside the existing schemas under
`app/src/main/assets/shared/`, retaining the paths expected by upstream.
