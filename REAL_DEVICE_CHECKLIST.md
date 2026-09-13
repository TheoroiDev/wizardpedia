# wizardpedia 真机测试 Checklist（catalog v3 双页书）

> **如何使用**：① 执行环境——JDK 21 跑 Gradle，`gradlew :wizardpedia-<fabric|forge>:runClient / runServer`；run 目录自动分离、runClient 用户名固定 `dev`、模型按 `resources/models/manifest.txt` 硬链接预置；联测可配 `-PjointTest=true`（forge 客户端拉兄弟仓 jar）与 `-PquickPlay=host:port`（自动进服）。② 判定记录回写本文件末"判定记录"表；**截图与日志一律写 `wizardpedia/test/logs/`**（本清单的核心闸门就是截图留证）。③ 联测历史判定与契约口径见 `docs/ref/wizardpedia.md` §7，本清单不重复。
> 分级：P0 = 挡 TRL 8 闸门（builder plan："catalog 真机截图/E2E 挂起"）；P1 = 发布前必过；P2 = 质量加固。v2 时代 fabric 联测已于 2026-09-02 通过，v3 双页书全部需复测。

## 0. 环境前置

- ☐ 联测形态：同一 runServer 装 wizardreal + wizardpedia（v3 双向就绪：wizardreal Unreleased 的 catalog v3 推送 + wizardpedia 0.1.0 接收端）。
- ☐ standalone 形态：仅 wizardpedia（demo 条目）——WP-P2-2 用。
- ☐ fabric 双终端先跑通，forge 走 WP-P1-5 通道。

## 1. P0 — TRL 8 闸门

### WP-P0-1 · 双页书真机截图（builder plan"catalog 真机截图"闸门）

- 场景：HOMM 双页书 UI 真机渲染与可读性留证。
- 前置：联测形态进服，catalog 数据已推送。
- 步骤：① 打开百科书，截主界面（左详情页 + 右图鉴网格）② 网格滚轮翻页后截图 ③ 详情页正文滚动 + 底角 stage-cycle 控件截图 ④ 左书签栏（章节）+ 右标签栏（过滤）展开截图。
- 判定：四类截图齐且存在 `wizardpedia/test/logs/`；书签/角标/锁徽章/页签渲染正确无错位；`latest.log` 零 missing texture、零 lang key 缺失 ERROR。
- 证据：`wizardpedia/test/logs/shots-<日期>/`（截图 4+ 张）。
- 状态：☐

### WP-P0-2 · 联测数据链：wizardreal → catalog v3 合并

- 场景：v3 线格式端到端——75 法术家族 + datapack 条目合并、locked 语义。
- 前置：WP-P0-1 环境。
- 步骤：① 进服打开书，数条目 ② 对照 `/wr spells` 输出 ③ 用 `/wr learn` 把某法术练过门槛后重开书 ④ 施法成功后查 learned 翻转。
- 判定：① 合并条目数与 wizardreal catalog 一致（75 家族 + datapack 条目）；② 分类与排序正确；③ locked 徽章随 learned 派生翻转（未学锁、学会开）。
- 证据：`wizardpedia/test/logs/joint-v3-<日期>.log` + 条目数记录。
- 状态：☐（上轮已过 v2 / 15 法术时代：2026-09-02，需在 v3 / 75 家族复测）

### WP-P0-3 · v3 新字段渲染

- 场景：v3 契约新增内容在双页书的呈现。
- 步骤：① 实体条目详情页看 3D 实时预览 ② 右栏按 school 标签过滤 ③ 查看效果摘要（`wizardreal.effect.<type>` 22 类）④ 嵌套咏唱变体切换 ⑤ 阶梯法术底角 stage-cycle 控件逐级切换。
- 判定：五项均渲染正确；语言键无裸 key 显示（en/zh 各切一遍）；stage 切换同步更新正文与效果摘要。
- 证据：`wizardpedia/test/logs/shots-v3fields-<日期>/`（每项 1 截图）。
- 状态：☐

## 2. P1 — 发布前必过

### WP-P1-1 · 皮肤切换（5 套 GUI 皮肤）

- 步骤：`config/wizardpedia/client.json` 的 `[client] uiSkin` 依次设 vanilla / homm / tome / flat / manuscript，各打开书一次。
- 判定：五套均加载且文字/图标可读（无同色叠字）；切换后无需重启。证据：`wizardpedia/test/logs/shots-skins-<日期>/`（5 截图）。状态：☐

### WP-P1-2 · 交互行为

- 步骤：① ESC 与背包键各关一次书 ② 关闭前翻到某分类/标签/语言/页码/stage 后重开 ③ 书内右键 ④ 滚轮在正文/网格/书签栏三处滑动。
- 判定：① 均可关闭；② 重开恢复最后状态（分类/标签/语言/选中/阶段全保持）；③ 右键返回上级；④ 滚动/翻页/滑动各自生效不串。证据：`wizardpedia/test/logs/interact-<日期>.log`。状态：☐

### WP-P1-3 · 语言子页

- 步骤：① 游戏语言 en/zh 各开一次书 ② 切语言页签（All languages + 各语言页）③ 查看触发词与咒文行随页变化。
- 判定：默认页 = 游戏语言（否则 English，否则首个）；触发词/咒文按所选语言页渲染；`""` 中性桶每页可见。证据：`wizardpedia/test/logs/lang-pages-<日期>/`。状态：☐

### WP-P1-4 · 双导出文件落盘

- 步骤：进服一次后检查 `<游戏目录>/wizardreal/spell_catalog.json` 与 `<游戏目录>/wizardpedia/pedia_catalog.json`。
- 判定：两文件均 format 3；UTF-8 CJK 完好（`爆裂` 等直接可读）；字段与 `docs/ref/wizardpedia.md` §6 一致。证据：文件拷贝归档 `wizardpedia/test/logs/`。状态：☐

### WP-P1-5 · forge 联测通道复验

- 步骤：forge 服务端（历史 ✅）+ forge 客户端经 `devFatJar` / `-PjointTest` 通道跑 WP-P0-2 主干。
- 判定：与 fabric 同判；若映射鸿沟仍阻塞客户端，记录阻塞现象并维持"forge 服务端过 + 客户端待 M8 maven 化"口径（非代码缺陷）。证据：`wizardpedia/test/logs/joint-forge-<日期>.log`。状态：☐

## 3. P2 — 质量加固

### WP-P2-1 · v1/v2 包拒收回归

- 步骤：旧 provider（若有 v2 jar）推送一次；无则确认单测覆盖即可作真机回归结论。
- 判定：v1/v2 包被拒且客户端状态不脏。证据：`wizardpedia/test/logs/wire-reject-<日期>.log`。状态：☐

### WP-P2-2 · standalone demo 条目

- 步骤：仅 wizardpedia 进世界开书。
- 判定：demo 条目可见、模型/lang 零资源错误（上轮 2026-09-02 已过，回归确认）。证据：`wizardpedia/test/logs/standalone-<日期>.log`。状态：☐

### WP-P2-3 · fileMode/pushMode 三态实机切换

- 步骤：三态逐一切换并进服各一次。
- 判定：解析 + 过滤行为与配置一致（低风险纯配置过滤，§7 遗留的"日常验证"项）。证据：`wizardpedia/test/logs/filemode-<日期>.log`。状态：☐

## 4. 判定记录

| 日期 | 项 | 结果（过/挂 + 关键发现） | 日志/截图位置 |
|---|---|---|---|
| | | | |
