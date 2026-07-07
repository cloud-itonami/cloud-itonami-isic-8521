# cloud-itonami-isic-8521

Open Business Blueprint for **ISIC Rev.5 8521**: General secondary
education. This repository publishes a secondary-education actor --
student intake, jurisdiction assessment, academic-integrity screening,
grading finalization and graduation finalization -- as an OSS business
that any qualified, licensed secondary-school operator can fork,
deploy, run, improve and sell.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720)) --
a second education vertical alongside `8510`'s pre-primary/primary
school, but for adolescent secondary education (grading and
graduation rather than promotion and safeguarding-record
finalization). Here it is **SchoolOps-LLM ⊣ Curriculum Safeguarding
Governor** -- the SAME governor and advisor names `8510` uses, since
this blueprint's own template names both general-education verticals
identically even though they are separate repos with separate
namespaces (`secondary.*` here, `school.*` there) and separate
actuation shapes.

> **Why an actor layer at all?** An LLM is great at drafting a
> student-intake summary, normalizing records, and checking whether a
> student's own completed-credits set actually contains every credit
> a jurisdiction requires for graduation -- but it has **no notion of
> which jurisdiction's secondary-education requirements are official,
> no license to finalize a real grading or a real graduation decision,
> and no way to know on its own whether an academic-integrity concern
> against the student has actually stayed unresolved**. Letting it
> finalize a grading or graduation directly invites fabricated
> jurisdiction citations, a grading finalized on insufficient
> attendance, a graduation finalized on incomplete credits, and an
> unresolved academic-integrity concern being quietly overlooked --
> and liability, and academic-integrity risk, for whoever runs it.
> This project seals the SchoolOps-LLM into a single node and wraps it
> with an independent **Curriculum Safeguarding Governor**, a human
> **approval workflow**, and an immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers student intake through jurisdiction assessment,
academic-integrity screening, grading finalization and graduation
finalization. It does **not**, by itself, hold any license required to
operate a secondary school in a given jurisdiction, and it does not
claim to. It also does **not** model a full curriculum-design/
pedagogical-assessment engine -- no subject-by-subject grading rubric,
no standardized-testing/examination-board integration, no full
student-information-system feature set (see `secondary.facts`'s own
docstring for the honest simplification this makes: a starting
catalog of licensing requirements, not a survey of every
jurisdiction's curriculum standards). Whoever deploys and operates a
live instance (a licensed secondary-school operator) supplies any
jurisdiction-specific license, the real pedagogical/academic-integrity
expertise and the real school-information-system integrations, and
bears that jurisdiction's liability -- the software supplies the
governed, spec-cited, audited execution scaffold so that operator
does not have to build the compliance layer from scratch for every
new market.

### Actuation

**Finalizing a real grading or a real graduation is never autonomous,
at any phase, by construction.** Two independent layers enforce this
(`secondary.governor`'s `:actuation/finalize-grading`/`:actuation/
finalize-graduation` high-stakes gate and `secondary.phase`'s phase
table, which never puts `:grading/finalize`/`:graduation/finalize` in
any phase's `:auto` set) -- see `secondary.phase`'s docstring and
`test/secondary/phase_test.clj`'s `grading-finalize-never-auto-at-any-
phase`/`graduation-finalize-never-auto-at-any-phase`. The actor may
draft, check and recommend; a human licensed educator is always the
one who actually finalizes a grading or graduation. Like `6512`/
`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/
`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/`8720`, this actor has TWO
actuation events.

## The core contract

```
student intake + jurisdiction facts (secondary.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ SchoolOps-   │ ─────────────▶ │ Curriculum                   │  (independent system)
   │ LLM (sealed) │  + citations    │ Safeguarding Governor:        │
   └──────────────┘                 │ spec-basis · evidence-       │
                             commit ◀────┼──────────▶ hold │ incomplete ·
                                 │             │           │ attendance-hours-
                           record + ledger  escalate ─▶ human   insufficient (MINIMUM-
                                             (ALWAYS for         threshold, non-temporal) ·
                                              :grading/               academic-integrity-
                                              finalize /              flag-unresolved
                                              :graduation/            (unconditional) ·
                                              finalize)               graduation-requirements-
                                                                       unsatisfied (set-
                                                                       containment) ·
                                                                       already-finalized
```

**The SchoolOps-LLM never finalizes a grading or a graduation the
Curriculum Safeguarding Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated jurisdiction
requirements; unsupported evidence; insufficient attendance hours; an
unresolved academic-integrity flag; unsatisfied graduation
requirements; a double grading or graduation finalization) force
**hold** and *cannot* be approved past; a clean grading/graduation
proposal still always routes to a human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a classroom-safety
monitoring robot supports physical supervision during activities,
under the actor, gated by the independent **Curriculum Safeguarding
Governor**. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Curriculum Safeguarding Governor, grading-finalization + graduation-finalization draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`8521`). Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/
`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/
`9412`/`6491`/`8720`, this vertical's student records are practice-
specific rather than a shared cross-operator data contract, so
`secondary.*` runs on the generic identity/forms/dmn/bpmn/audit-ledger
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/secondary/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate grading-finalization/graduation-finalization history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded student, and the double-finalization guards check dedicated `:grading-finalized?`/`:graduation-finalized?` booleans rather than a `:status` value |
| `src/secondary/registry.cljc` | Grading-finalization + graduation-finalization draft records, plus `attendance-hours-insufficient?` (SECOND non-temporal instance of the MINIMUM-threshold sufficiency family, after `association`) and `graduation-requirements-unsatisfied?` (THIRD instance of the set-containment/subset family, after `registrar`/`casework`) |
| `src/secondary/facts.cljc` | Per-jurisdiction secondary-education licensing catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/secondary/schoolopsllm.cljc` | **SchoolOps-LLM Advisor** -- `mock-advisor` ‖ `llm-advisor`; intake/assessment/academic-integrity-screening/grading-finalization/graduation-finalization proposals |
| `src/secondary/governor.cljc` | **Curriculum Safeguarding Governor** -- 5 HARD checks (spec-basis · evidence-incomplete · attendance-hours-insufficient, pure ground-truth MINIMUM-threshold recompute · academic-integrity-flag-unresolved, unconditional evaluation, the TWENTY-THIRD grounding of this discipline and FIRST specifically for the academic-integrity-flag concept · graduation-requirements-unsatisfied, pure ground-truth set-containment recompute) + already-graded/already-graduated guards + 1 soft (confidence/actuation gate) |
| `src/secondary/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted assess → supervised (both grading and graduation finalization always human; student intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/secondary/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/secondary/sim.cljc` | demo driver |
| `test/secondary/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers student intake through jurisdiction assessment,
academic-integrity screening, grading finalization and graduation
finalization -- the core governed lifecycle this blueprint's own
`docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Student intake + per-jurisdiction secondary-education checklisting, HARD-gated on an official spec-basis citation (`:student/intake`/`:jurisdiction/assess`) | A full curriculum-design/pedagogical-assessment engine (subject-by-subject grading rubrics, standardized-testing/examination-board integration -- see `secondary.facts`'s docstring) |
| Academic-integrity screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:academic-integrity/screen`) | Real school-information-system integration, billing/tuition workflows |
| Grading finalization, HARD-gated on full evidence and attendance-hours sufficiency, plus a double-finalization guard (`:grading/finalize`) | Ongoing classroom-instruction workflows themselves |
| Graduation finalization, HARD-gated on full evidence and graduation-requirement completeness, plus a double-finalization guard (`:graduation/finalize`) | |
| Immutable audit ledger for every intake/assessment/screening/grading/graduation decision | |

Extending coverage is additive: add the next gate (e.g. a disciplinary-
suspension check) as its own governed op with its own HARD checks and
tests, following the SAME "an independent governor re-verifies against
the actor's own records before any real-world act" pattern this repo's
flagship op already establishes.

## Jurisdiction coverage (honest)

`secondary.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `secondary.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `secondary.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to make
coverage look bigger.

## Maturity

`:implemented` -- `SchoolOps-LLM` + `Curriculum Safeguarding Governor`
run as real, tested code (see `Run` above), promoted from the
originally-published `:blueprint`-tier scaffold, modeled closely on
the thirty-two prior actors' architecture. See `docs/adr/0001-
architecture.md` for the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
