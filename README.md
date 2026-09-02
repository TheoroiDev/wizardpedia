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

## Building

```
gradlew build          # all three subprojects + sources jars
gradlew :wizardpedia-fabric:runClient    # or :wizardpedia-forge:runClient
```

Artifacts follow `{mod}-{loader}-{mc}-{version}.jar`, e.g.
`wizardpedia-fabric-1.20.1-0.1.0.jar`. Runs are pinned to the Java 17
toolchain; runServer auto-writes `eula.txt` + `online-mode=false` (dev).

## Datapack entries

Any datapack (or the mod jar itself) can contribute:

```json
// data/<ns>/wizardpedia/categories/<name>.json
{ "id": "wizardreal:wizardry", "name_key": "origin.wizardreal.wizardry",
  "icon": "wizardreal:staff_apprentice", "sort": 10 }

// data/<ns>/wizardpedia/entries/<name>.json
{ "id": "wizardreal:explosion", "category": "wizardreal:wizardry",
  "title_key": "spell.wizardreal:explosion.name", "locked": false,
  "icon": "wizardreal:spell_tome",
  "aliases": ["explosion", "explode", "爆裂"],
  "lines_key": ["wizardreal.desc.explosion.1"] }
```

- `icon` is an item id (rendered in the grid/detail page); `""` = none.
- `aliases` are free-form keywords (any language, matched by the UI search).
- `lines_key` are lang keys resolved client-side in the active language.
- Parsing is strict: a file that fails to decode is skipped **whole** with a
  warn log. `/reload` rescans.

## Provider integration (wire format v1 — FINAL)

Mod providers push a catalog over the S2C channel `wizardpedia:catalog`
using vanilla `FriendlyByteBuf`, **no compile-time dependency on wizardpedia
required** (hardcode the channel id + format; bump-with-rejection is the
compatibility mechanism). Packet layout:

```
byte  formatVersion = 1
byte  type          // 0 = FULL_SYNC (replace client datapack-source set)
                    // 1 = PROVIDER_PUSH (upsert by id into provider-source set)
varInt catCount
  { utf catId(≤128), utf nameKey(≤128), utf iconItem(≤128, ""=none), varInt sortIndex }
varInt entryCount
  { utf entryId(≤128), utf catId(≤128), utf titleKey(≤128), bool locked,
    utf iconItem(≤128, ""=none),
    varInt aliasCount { utf alias(≤96) },          // keywords, any language
    varInt lineCount  { utf lineKey(≤160) } }      // lang keys, client-resolved
```

Rules:

- `utf` = `FriendlyByteBuf.writeUtf/readUtf` with the given max length.
- The client merges per source; **provider entries win id conflicts** over
  datapack entries and may override `locked`.
- FULL_SYNC replaces the client's datapack-source set; PROVIDER_PUSH upserts.
- **Compatibility**: append-only fields. A breaking change bumps
  `formatVersion`; receivers that see a different leading byte reject the
  packet with a warn log (never desync).
- Registration (client side): Fabric — `ClientModInitializer`;
  Forge — `FMLClientSetupEvent` **on `Bus.MOD`** (the default FORGE bus
  silently never fires for mod-bus events).
