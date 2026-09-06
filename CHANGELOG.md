# Changelog — Wizardpedia

English primary; Chinese mirror: [CHANGELOG.zh.md](CHANGELOG.zh.md) (keep both in sync, English wins on conflict).

## 0.1.0 — unreleased

### Features

- breaking: catalog wire format v3 — the full content model per entry: entity id (live 3D preview on the detail page), tags (color-coded right-rail school filter), effect descriptions, per-language nested chant variants, and a stage ladder (gated tiers); v1/v2 packets are rejected (wizardpedia#8)
- Book UI redesigned to a two-page HOMM layout (wizardpedia#8): left page = detail with stats, scrollable body (trigger words / effect summary / chant paragraphs with variant switcher / stage effects), and a stage-cycle control in the bottom corners; right page = the compact compendium grid with a localized name under each icon; left bookmark rail = chapters, right rail = tag filters
- Switchable book skins: 5 shipped GUI skins generated on the SDXL pixel pipeline and reviewed for readability (vanilla / homm / tome / flat / manuscript); select via `[client] uiSkin` in `config/wizardpedia/client.json` (docs/plans/wizardpedia_homm_layout.md)
- Behavior: ESC and the inventory key both close the book; the book reopens on the last page (category/tag/language/selection/stage kept); right-click goes back; the mouse wheel scrolls the body, flips grid pages and slides the bookmark rails

### Changes

- breaking: datapack entry schema gains optional `entity`/`tags`/`chants`/`stages`/`learning`/`mana_cost`/`cooldown_seconds`/`difficulty` keys; `pedia_catalog.json` export bumps to format 3
### Features

- breaking: catalog wire format v2 — entry aliases/chant lines are keyed by language (en/zh/ja/ko, `""` = neutral bucket shown on every page); v1 packets are rejected (wizardpedia#7)
- Language sub-pages in the book: a tab row under the category (All languages + one page per language); default page = game language, else English, else first; detail page renders keywords/lines for the selected page
- Book UI rework (code-drawn skin): procedural parchment/leather textures, ribbon bookmarks with notched ends, spine shading, inset entry cells with hover highlight, padlock badge on locked entries, corner ribbons, 150 ms page-turn slide (docs/plans/wizardpedia_ui_effects.md option B)

### Changes

- breaking: datapack entry schema v2 — `aliases`/`lines_key` become language-keyed objects; flat arrays are no longer parsed; bundled demo entries follow the new schema
- `pedia_catalog.json` export bumps to format 2: aliases/lines grouped per language, line values resolved client-side

### Modding/API

- breaking: provider wire contract is v2 (see README "Provider integration"); lines accept lang keys or literal chant text via the translatable missing-key fallback

- Encyclopedia book item + creative tab; HOMM-style three-level paginated catalog screen (wizardpedia#4)
- Catalog sync: datapack entries pushed by providers, S2C full sync to clients, client-side merged state (wizardpedia#2)
- Merged-view `pedia_catalog.json` exported on every state change (wizardpedia#5)

### Bugfixes

- Wire-format truncation fix; bookmark category icons; manifest dependency declarations

### Modding/API

- Catalog wire contract v1 finalized: format version + typed records; compatibility rule is additive-only (breaking changes must bump FORMAT_VERSION) (wizardpedia#3)

### Infrastructure

- Multi-loader repo bootstrap + M0 skeleton (wizardpedia#1)
- Joint-test tooling: devFatJar / `-PjointTest` / `-PquickPlay` (wizardpedia#6)
- Dev `runServer`/`runClient` run directories split; voice models seeded into run dirs as hard links
- Modrinth maven repo; joint-test jars aligned to voicecast/wizardreal 0.3.1 → 0.3.2
- Dev-only testing mods moved out of gradle: release jars are pre-downloaded under workspace `resources/devmods/<loader>/` and wired from `manifest.txt` (fabric: hardlinked into the run mods folder; forge: file dependency so Loom remaps the SRG jar; Forge port of Carpet stays blocked, voicecast#38); voice-model fact source moved to `resources/models/`
