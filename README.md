# cloud-itonami-isic-0113

Open Occupation Blueprint for **ISIC Rev. 4 0113**: Growing of vegetables and
melons, roots and tubers.

This repository implements a forkable OSS **vegetable and root-crop growing
operations coordinator**: a field-management and record-keeping robot manages
planting/yield logging, field-operation scheduling, and supply procurement
under a governor-gated actor, so a vegetable/root-crop farm keeps its own
operational records and maintains full transparency over decisions.

**Maturity: `:implemented`.** `src/vegops/` implements the
`VegOpsAdvisor` (`vegops.advisor`) and the independent
`FieldOperationsGovernor` (`vegops.governor`), composed by
`vegops.operation` into a REAL, compiled
[`kotoba-lang/langgraph`](https://github.com/kotoba-lang/langgraph)
`StateGraph` (per ADR-2607011000):

```text
:intake -> :advise -> :govern -> :decide -+-> :commit                        (:hard? false, :escalate? false)
                                           +-> :request-approval -> :commit    (:escalate? true, interrupt-before)
                                           +-> :hold                          (:hard? true)
```

An earlier version of this repo's `operation/build` returned a bare
Clojure closure over the synchronous flow and never touched
`kotoba-lang/langgraph` at all (its own docstring called it a "Stub
for building a langgraph-clj StateGraph"), `deps.edn` had an empty
top-level `:deps {}` with langgraph only reachable via `:dev`
override-deps (nothing to actually override), and no audit ledger
existed anywhere in the repo. All three gaps are now closed:
`interrupt-before #{:request-approval}` + an in-memory checkpointer
give escalated proposals a GENUINE human-in-the-loop pause/resume, and
BOTH `:commit` and `:hold` durably append to the real audit ledger
(`vegops.store/append-ledger!`, `MemStore` + a `DatomicStore` via
[`kotoba-lang/langchain-store`](https://github.com/kotoba-lang/langchain-store)).
36 tests / 133 assertions green (`clojure -M:dev:test`), including a
new `test/vegops/operation_test.cljc` that runs the REAL compiled
graph end to end through commit / hard-hold / escalate→approve /
escalate→reject, and a `clojure -M:dev:run` demo runner that produces
4 distinct real ledger entries.

## What this does NOT do

This actor coordinates **back-office logistics only**. It explicitly does **NOT**:

- **Direct field-equipment operation** — remains the farmer's exclusive authority
- **Pesticide-application decisions** — remains the agronomist/farmer authority
- **Agronomic decision authority** (what/when/how much to plant, spray, or harvest) —
  remains human authority; this actor only coordinates the logistics around those
  decisions
- **Direct execution of any kind** — any proposal for direct field-equipment control
  or finalizing a pesticide-application decision is a hard block

## HARD invariants (always hold, never overridable)

1. **field-not-registered** — the request's `field-id` must resolve to a
   registered field in the Store before any proposal can proceed
2. **no-execution** — every proposal's `:effect` must be `:propose` (the governor
   never directly operates field equipment, never finalizes a
   pesticide-application decision)
3. **equipment-or-pesticide-decision-blocked** — `:operate-field-equipment` and
   `:finalize-pesticide-application` proposals are unconditionally, permanently
   blocked
4. **op-not-allowed** — any op outside the closed allowlist below is rejected
5. **field-record-invalid** — `:log-field-record` with a non-positive acreage is
   rejected

## Always-escalate operations (human sign-off, regardless of confidence)

- `:flag-crop-health-concern` — any pest/disease/frost-damage concern →
  automatic escalation
- `:order-supplies` over its category cost threshold (default 500 currency
  units; see `vegops.facts/supply-categories`)
- Any proposal with confidence below the Governor's floor (0.7)

## Operational requests (closed allowlist, all `:effect :propose`)

```text
:log-field-record
  — record planting/harvest-yield/soil-test data
  — requires a registered field; non-positive acreage is rejected

:schedule-field-operation
  — propose a planting/spraying/irrigation/harvest scheduling operation
  — does NOT make agronomic decisions

:flag-crop-health-concern
  — surface a pest, disease, or frost-damage concern
  — ALWAYS escalates for human review

:order-supplies
  — procurement for seed, fertilizer, equipment
  — escalates if cost exceeds its category threshold
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs the
physical domain work**. Here a field-management robot handles:

- Field record logging and entry
- Field-operation scheduling and reminders
- Supply inventory and ordering
- Audit ledger maintenance

The **FieldOperationsGovernor** is the independent safety layer that gates all
proposals before a robot action is executed. The governor never dispatches
hardware directly; `:high`/`:safety-critical` actions (such as escalated
crop-health concerns or high-cost supply orders) require human sign-off.

## Core Contract

```text
operational request (log, schedule, concern, order)
        |
        v
VegOpsAdvisor -> FieldOperationsGovernor -> phase gate -> commit, or escalate for human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated operation can dispatch a robot action the governor refuses, suppress an
operating record, or hide a crop-health concern without governor approval and audit
evidence.

## Module structure

Mirrors `cloud-itonami-isic-0111` (`cerealops.*`) module-for-module:

- `vegops.facts` — reference data: supply-category cost thresholds, vegetable/root/tuber crops
- `vegops.registry` — pure independent verification functions (cost/acreage/confidence)
- `vegops.store` — `Store` protocol + `MemStore` + `DatomicStore` (via
  [`kotoba-lang/langchain-store`](https://github.com/kotoba-lang/langchain-store),
  no hand-rolled EDN-blob codec): field registration lookup + the
  append-only audit ledger (`ledger`/`append-ledger!`). Both backends
  pass the same contract (`test/vegops/store_contract_test.cljc`).
- `vegops.advisor` — `Advisor` protocol + `MockAdvisor` (the sealed LLM/decision node)
- `vegops.governor` — `FieldOperationsGovernor`: hard invariants + escalation gates
- `vegops.phase` — 0→3 rollout phase gate
- `vegops.operation` — `build`: the REAL `langgraph.graph` StateGraph
  wiring (`state-graph`/`add-node`/`add-edge`/`add-conditional-edges`/
  `compile-graph`), advisor → governor → phase-gate → commit/hold, with
  BOTH `:commit` and `:hold` durably appending to the real audit ledger
- `vegops.sim` — demo runner (`clojure -M:run`) driving the REAL compiled
  StateGraph via `langgraph.graph/run*`, including checkpointed interrupt/resume

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC Rev. 4 `0113`). Required capabilities:

- :robotics
- :identity
- :forms
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Testing

```bash
clojure -M:dev:test   # 36 tests / 133 assertions, green
clojure -M:lint       # clj-kondo, 0 errors / 0 warnings
clojure -M:dev:run     # demo runner, real compiled StateGraph end-to-end
```

## License

AGPL-3.0-or-later.
