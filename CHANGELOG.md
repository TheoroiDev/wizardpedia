# Changelog — Wizardpedia

English primary; Chinese mirror: [CHANGELOG.zh.md](CHANGELOG.zh.md) (keep both in sync, English wins on conflict).

## 0.1.0 — unreleased

### Features

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
- Fabric dev runs bundle a Carpet testing mod (Forge port blocked; voicecast#38)
