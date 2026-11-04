# 发版说明 / Release notes

每个版本一个文件，文件名与 tag 完全一致：`.github/release-notes/<tag>.md`（例如 `v0.3.9-chen.md`）。
tag 打包前先写好，Release CI 会把它原样作为 release 正文。

格式固定，模板与校验脚本在发布服务器 `/root/clma-signing/RELEASE_TEMPLATE.md`：

- 只写改了什么：新增 / 修复 / 删除 / 调整
- 中文段在上 `## 更新内容`，英文段在下 `## Changes`，不逐条混排
- 一条一行，动词开头：新增 Added / 修复 Fixed / 删除 Removed / 调整 Changed
- 两段条目数一致、顺序对应
- 不写验证结论、签名、SHA256、免责声明；不用 emoji、不写「我们」

示例：

```markdown
## 更新内容
- 修复符号面板长按误触发候选上屏

## Changes
- Fixed long-press on the symbol panel wrongly committing a candidate
```

文件缺失时，正文只保留未签名警告（若 CI 未配置签名 secrets）。
