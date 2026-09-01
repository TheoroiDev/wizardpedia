# Wizardpedia

A standalone, zero-dependency in-game catalog/compendium mod for Minecraft
1.20.1 (Fabric + Forge, Architectury). HOMM-style paginated book UI with
category bookmarks, an entry grid (search + locked greying) and detail pages.

- **Mod id**: `wizardpedia` · group `com.theo.wizardpedia` · version `0.1.0`
- **Zero cross-project dependencies** — only architectury / loader / fabric-api.
- **Data sources** (merged on the client, provider wins id conflicts):
  1. Server datapack entries: `data/<ns>/wizardpedia/categories/*.json` and
     `data/<ns>/wizardpedia/entries/*.json` — full S2C sync on join/reload.
  2. Wire-format push: any provider mod can feed a catalog over the S2C
     `wizardpedia:catalog` channel with zero compile-time dependency
     (wizardreal is the first consumer).
- **JSON export**: the merged catalog view is written to
  `<game-dir>/wizardpedia/pedia_catalog.json` (external tooling data source).

Status: under construction (see the workspace `docs/wizardpedia.md` for the
design contract: wire format, datapack schema, export schema, milestones).
