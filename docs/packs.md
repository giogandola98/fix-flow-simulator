# Provider packs

A **provider pack** bundles everything the simulator needs to play one counterparty: FIX data dictionaries, data
sets, scenarios and FIX session templates. The engine imports, updates, exports and removes a pack as one unit, so a
pack can be versioned in its own repository and installed on any simulator with one upload.

The engine has no provider-specific code: a pack is plain files in the formats described here.

## Layout

A pack is a zip (or a folder, zipped before upload) with `pack.yaml` at its root and files directly inside four
folders:

```
pack.yaml
dictionaries/*.xml   QuickFIX/J data dictionaries
datasets/*.csv       data sets; the data set name is the file name without .csv
scenarios/*.yaml     scenarios in the YAML DSL (also .yml)
sessions/*.yaml      FIX sessions: a session request body written as YAML (also .yml)
```

Anything else is refused with the entry's name: other folders or files at the root, sub-folders, other extensions,
absolute paths and `..` segments. A zip whose entries all sit under one top folder (`demo/pack.yaml`, the way a
zipped folder usually comes out) is read as if that folder were the root. The uncompressed pack may be at most 64 MB.

## pack.yaml

```yaml
id: demo                    # [a-z0-9-]+, the pack's identity on the engine
name: Demo pack
version: 1.0.0
provider: Demo
engineMin: "0.8.0"          # optional: lowest engine version the pack runs on
description: Generic pack used by the engine's tests.
values:                     # template values the import form asks for
  - name: SENDER            # [A-Za-z_][A-Za-z0-9_]*
    description: SenderCompID of the simulator
  - name: TARGET
    description: TargetCompID of the system under test
  - name: PORT
    description: Port the acceptor listens on
    default: "9876"         # a value with a default is optional
```

`id`, `name` and `version` are required; unknown keys are refused (`400`), so a typo does not pass unnoticed.
`engineMin` is compared with the running engine on the numeric
`major.minor.patch` only, so a `0.8.0-beta` engine runs a pack that needs `0.8.0`; a higher `engineMin` is `400`.

A value without `default` is **required**: the import fails with `400` listing every missing name. A blank value
counts as missing.

## References by name

Inside a pack, files refer to each other by **name**, never by id, so the same pack installs on any engine:

| Where | Key | Refers to |
|---|---|---|
| scenario | `sessionRef: <session name>` | a session of `sessions/` |
| scenario | `datasetRef: <data set name>` | a data set of `datasets/` (file name without `.csv`) |
| `CALL_SCENARIO` / `PARALLEL` node `config` | `targetScenario: <scenario name>` | a scenario of `scenarios/` (its `name`) |
| session | `appDataDictionary`, `transportDataDictionary: <file name>` | a file of `dictionaries/` |

On import the engine writes the ids into what it stores: `sessionRef` and `datasetRef` become the session and data
set ids, `targetScenario` becomes `targetScenarioId` (at the same place in `config`), and dictionaries are stored as
`<packId>__<file name>` (so two packs can ship a file of the same name) with the session pointing at that name. On
export the ids are turned back into names.

A name that is not part of the pack is `400` naming the file and the reference, e.g.
`scenarios/demo-main.yaml: node 'call' targetScenario 'nope' is not a scenario of the pack`. The one exception is a
session dictionary: a name that is not in `dictionaries/` may name a dictionary already installed on the engine.

A node that still carries a raw `targetScenarioId` is imported unchanged, with a warning: that id will not exist on
another engine.

Scenario names must be unique within a pack, and so must session names. Data set names are unique on the whole
engine: a data set name already used outside the pack is `409`.

## Session templates

A session file is the body of `POST /api/v1/sessions` as YAML:

```yaml
name: demo-acceptor
mode: ACCEPTOR
fixVersion: FIXT_11
defaultApplVerID: "9"
senderCompID: ${SENDER}
targetCompID: ${TARGET}
host: 0.0.0.0
port: ${PORT}
heartbeatInterval: 30
resetOnLogon: true
resetOnLogout: true
appDataDictionary: FIX50SP2-DEMO.xml
autoConnect: false
```

`${NAME}` is replaced by the value `NAME` (the one typed in the import form, else its default). Replacement happens
inside string values only, after the YAML is parsed, so a value can never change the file's structure; `port:
${PORT}` still becomes a number. A `${NAME}` that `pack.yaml` does not declare is `400` naming the file. Unknown keys
are refused. `heartbeatInterval` defaults to 30, the booleans to `false`. Placeholders are only replaced in session
files.

## Import and update

`POST /api/v1/packs` (or **Packs → Import pack** in the UI) reads and checks **everything first** — manifest, engine
version, values, every CSV, dictionary, session and scenario, and every name reference — and writes only when all of
it is valid, so a refused pack leaves the engine as it was.

Importing a pack whose `id` is already installed **updates it in place**:

- every object is matched by `(packId, name)` and keeps its id, so execution history and references from your own
  scenarios still point at it;
- objects of the pack that are no longer in the zip are removed (a dropped dictionary is kept, with a warning, while
  a session outside the pack still uses it);
- objects outside the pack are never touched.

Refusals:

| Status | When |
|---|---|
| `400` | not a zip, bad `pack.yaml`, `engineMin` above the engine, missing values, a file that does not parse (the message names it, a CSV also the line), an unresolved name reference |
| `409` | an execution of one of the pack's scenarios is running, or any execution is running on one of the pack's sessions (the message lists the execution ids: stop them or wait); a data set name taken outside the pack |

Known gap of the `409` check: it sees top-level executions of the pack's scenarios and executions on the pack's
sessions. A pack scenario that runs **only as a child** (CALL_SCENARIO or PARALLEL) of a scenario outside the pack, on
a session outside the pack, is not seen: the import or delete goes ahead while that child runs. Stop such runs before
updating or removing the pack.

Sessions: a pack session that is **connected** and whose configuration changed is disconnected, saved and connected
again with the new configuration; an unchanged one is left connected and untouched. Import never connects a session
that was not connected; `autoConnect: true` takes effect at the next start of the simulator (or connect it from the
session panel).

Scenarios are registered for execution as soon as they are saved.

The values the pack was installed with are stored with it. The import form of an update starts from them.

## Export

`GET /api/v1/packs/{id}/export` writes the pack as it is **now**, edits made in the engine included: names instead of
ids, `<packId>__` taken off dictionary names, no scenario `id`. Session files carry the installed values as literal
fields, and `pack.yaml` gets those values as the `default`s, so the exported zip imports again without filling in the
form. Exporting and re-importing gives the same objects; file names come from object names (characters outside
`[A-Za-z0-9._-]` become `_`).

> **Warning.** An export carries the values the pack was installed with — CompIDs, hosts, ports and every other
> filled-in value — both as literals in the session files and as `default`s in `pack.yaml`. Never commit an exported
> pack with real environment values to a repository: replace them with placeholders (or drop the `default`s) first.

## Delete

`DELETE /api/v1/packs/{id}` removes the pack and everything it installed: its scenarios, data sets, sessions
(disconnected first) and dictionaries. The same `409` rule as for import applies.

## Editing pack objects in the engine

Pack objects stay editable. A scenario, data set or session edited through the UI or the API keeps its pack tag, so
the next import of the pack overwrites the edit, and an export includes it.

## API

See [API reference → Packs](api-reference.md#packs).
