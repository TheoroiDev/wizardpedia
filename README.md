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
gradlew build          # all three subprojects + sources jars + unit tests
gradlew :wizardpedia-fabric:runClient    # or :wizardpedia-forge:runClient
```

Artifacts follow `{mod}-{loader}-{mc}-{version}.jar`, e.g.
`wizardpedia-fabric-1.20.1-0.1.0.jar`. Runs are pinned to the Java 17
toolchain; runServer auto-writes `eula.txt` + `online-mode=false` (dev).

## Development notes

- `-PquickPlay=host:port` on `runClient` joins a server on launch (MC 1.20+
  quick play) — used by joint testing.
- Forge dev-testing helpers (see workspace `docs/ref/wizardpedia.md` §7):
  `gradlew :wizardpedia-forge:devFatJar` builds a named-mappings fat jar for
  dropping into a *dev* server's `run/mods/` (production SRG jars fail in a
  mojmap dev runtime); `-PjointTest=true` on `runClient` pulls sibling-repo
  jars (wizardreal + voicecast) via Loom-remapped `modLocalRuntime`.
- Provider push is verified end-to-end against wizardreal (fabric E2E; the
  forge dev-runtime mapping gap is documented in the same doc section).

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
  "aliases": { "": ["explosion", "explode"], "zh": ["爆裂"] },
  "lines_key": { "": ["wizardreal.desc.explosion.1"] } }
```

- `icon` is an item id (rendered in the grid/detail page); `""` = none.
- `aliases`/`lines_key` are **language-keyed objects** (two-letter code → list);
  `""` is the language-neutral bucket, shown on every language page. The UI
  adds a language sub-tab row when any entry carries a language bucket.
- `lines_key` values are lang keys resolved client-side in the active language
  (missing keys fall back to the raw value, so literal text renders too).
- Parsing is strict: a file that fails to decode is skipped **whole** with a
  warn log. `/reload` rescans.

## Provider integration (wire format v3 — FINAL)

Mod providers push a catalog over the S2C channel `wizardpedia:catalog`
using vanilla `FriendlyByteBuf`, **no compile-time dependency on wizardpedia
required** (hardcode the channel id + format; bump-with-rejection is the
compatibility mechanism). Packet layout:

```
byte  formatVersion = 3
byte  type          // 0 = FULL_SYNC (replace client datapack-source set)
                    // 1 = PROVIDER_PUSH (upsert by id into provider-source set)
varInt catCount
  { utf catId(≤128), utf nameKey(≤128), utf iconItem(≤128, ""=none), varInt sortIndex }
varInt entryCount {
  utf entryId(≤128), utf catId(≤128), utf titleKey(≤128), bool locked,
  float learning(-1=unknown), varInt manaCost(-1=unknown),
  float cooldownSeconds(-1), float difficulty(-1),
  utf iconItem(≤128), utf entityId(≤128, ""=none),
  varInt tagCount { utf tag(≤32) },
  varInt aliasLangCount { utf lang(≤8), varInt n { utf alias(≤96) } },
  varInt descLangCount  { utf lang(≤8), varInt n { utf line(≤160) } },
  varInt chantLangCount { utf lang(≤8), varInt variantCount {
      varInt lineCount { utf line(≤160) } } },
  varInt stageCount { varInt afterLines, float mastery, varInt manaCost(-1=inherit),
      float cooldownSeconds(-1=inherit), varInt descLangCount {...} } }
```

Rules:

- `utf` = `FriendlyByteBuf.writeUtf/readUtf` with the given max length.
- The client merges per source; **provider entries win id conflicts** over
  datapack entries and may override `locked`.
- FULL_SYNC replaces the client's datapack-source set; PROVIDER_PUSH upserts.
- `lang` is a two-letter language code (`en`/`zh`/…); `""` is the
  language-neutral bucket (shown on every language page). desc/alias values
  are lang keys **or literal text** — the client resolves them via the
  translatable missing-key fallback, so both render.
- v3 content model: `entityId` renders a live entity preview on the detail
  page (mob entries); `tags` feed the right bookmark rail filter (spell
  schools etc.); `chants` nest variants per language (the detail page has a
  variant switcher); `stages` is the entry's ascending tier ladder (gated by
  completed lines and mastery; -1 cost/cooldown inherits the base values).
  Chant-stage reference: wizardreal's `chant_stages` (magic_eco 03).
- **Compatibility**: a format change bumps `formatVersion`; receivers that
  see a different leading byte reject the packet with a warn log (never
  desync). v1/v2 packets are rejected like any other version mismatch.
- Registration (client side): Fabric — `ClientModInitializer`;
  Forge — `FMLClientSetupEvent` **on `Bus.MOD`** (the default FORGE bus
  silently never fires for mod-bus events).
