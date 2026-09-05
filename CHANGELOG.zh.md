# 更新日志 — Wizardpedia

中文对照版；英文为主：[CHANGELOG.md](CHANGELOG.md)（两份保持同步，冲突以英文为准）。

## 0.1.0 — 未发布

### Features

- breaking: 目录线格式 v2 —— 条目别名/吟唱行按语言分桶（en/zh/ja/ko，`""` = 每页都显示的语言中立桶）；v1 包将被拒收（wizardpedia#7）
- 书内语言子页：分类书签下新增语言页签行（"通用" + 每语言一页）；默认页 = 游戏语言，无匹配用英语，再退到第一页；详情页按所选语言页渲染关键词/吟唱行
- 书 UI 重绘（纯代码皮肤）：程序化羊皮纸/皮革纹理、燕尾丝带书签、书脊阴影、内阴影条目格 + 悬停亮边、锁定条目挂锁角标、格角丝带、150ms 翻页滑动（docs/plans/wizardpedia_ui_effects.md 方案 B）

### Changes

- breaking: datapack 条目 schema v2 —— `aliases`/`lines_key` 改为语言键对象；不再解析旧的平铺数组；自带演示条目已随新 schema 更新
- `pedia_catalog.json` 导出升到 format 2：aliases/lines 按语言分组，行值由客户端按当前语言解析

### Modding/API

- breaking: provider 线契约为 v2（见 README "Provider integration"）；吟唱行接受 lang key 或字面文本（translatable 缺键回退原值）

- 图鉴书物品 + 创造模式标签；HOMM 风格三级分页目录界面（wizardpedia#4）
- 目录同步：provider 推送数据包条目、S2C 全量同步到客户端、客户端合并态（wizardpedia#2）
- 每次状态变更导出合并视图 `pedia_catalog.json`（wizardpedia#5）

### Bugfixes

- 线格式截断修复；书签分类图标；manifest 依赖声明

### Modding/API

- 目录线格式契约 v1 定稿：格式版本 + 类型化记录；兼容规则只允许追加字段（破坏性变更必须 bump FORMAT_VERSION）（wizardpedia#3）

### Infrastructure

- 多加载器仓库引导 + M0 骨架（wizardpedia#1）
- 联测工具：devFatJar / `-PjointTest` / `-PquickPlay`（wizardpedia#6）
- 开发运行 `runServer`/`runClient` 目录分离；语音模型硬链接预置进运行目录
- Modrinth maven 仓库；联测 jar 对齐 voicecast/wizardreal 0.3.1 → 0.3.2
- 纯开发测试 mod 移出 gradle 依赖：release jar 预下载到工作区 `resources/devmods/<loader>/`，由 `manifest.txt` 驱动接线（fabric 硬链接进 run mods 目录；forge 作为文件依赖由 Loom 重映射；Carpet 的 Forge 移植仍受阻，voicecast#38）；语音模型事实源移至 `resources/models/`
