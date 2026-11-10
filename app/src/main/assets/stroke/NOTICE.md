# cnchar simplified stroke data

Source: https://github.com/theajack/cnchar
Pinned commit: b8397db3e08e88ebbf8acf92b5cbab1a7e4c1550
Copyright (c) 2017 - present theajack <theajack@qq.com>
License: MIT (included in LICENSE).

The JSON inputs are unchanged copies from:
- src/cnchar/plugin/order/dict/stroke-order-jian.json
- src/cnchar/plugin/order/dict/stroke-table.json
- src/cnchar/plugin/trad/dict/trad-simple.json

Run `python3 tools/generate_stroke_orders.py` to regenerate the APK's
`app/src/main/assets/stroke/orders.tsv`. This derived data remains under MIT.
Only simplified/common-form characters in the upstream simplified table are
included; mapped traditional-only variants are excluded. This is not a claim
that the table covers every character in the national standard character list.

Five classes: horizontal (including rising), vertical (including vertical hook),
left-falling, dot/right-falling, and fold (other bends/hooks).
No JavaScript runtime, web page, handwriting model, or network service is used.
