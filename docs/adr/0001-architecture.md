# ADR-0001: cloud-itonami-isic-8521 -- SchoolOps-LLM as a contained intelligence node

- Status: Accepted (2026-07-08)
- Related: `cloud-itonami-isic-6511`/`6512`/`6621`/`6622`/`6629`/`6520`/
  `6530`/`6820`/`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/
  `7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311`/`8510`/`9412`/`6491`/`8720` ADR-0001s (the pattern this
  ADR ports); ADR-2607071250/ADR-2607071320/ADR-2607071351/
  ADR-2607071618/ADR-2607071640/ADR-2607071654/ADR-2607071717/
  ADR-2607071732/ADR-2607071752/ADR-2607071819/ADR-2607071849/
  ADR-2607071922/ADR-2607072715/ADR-2607072730/ADR-2607072745/
  ADR-2607072800/ADR-2607072815/ADR-2607072830/ADR-2607072845/
  ADR-2607072900/ADR-2607072915/ADR-2607080100/ADR-2607080200/
  ADR-2607080300 (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/
  `9200`/`7500`/`9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/
  `9000`/`8890`/`8610`/`9311`/`8510`/`9412`/`6491`/`8720`, the twenty-
  four verticals built outside ADR-2607032000's original insurance/
  real-estate batch -- this is the twenty-fifth); `cloud-itonami-
  isic-8510`'s ADR-0001 (`school.governor`/`school.schoolopsllm`,
  the SAME governor+advisor names this actor reuses, see Decision 1)
- Context: Continuing the standing "pick a new ISIC blueprint
  vertical" direction past `8720`, this ADR deepens `cloud-itonami-
  isic-8521` (general secondary education) from `:blueprint` to
  `:implemented`, the thirty-ninth actor in this fleet -- a SECOND
  education vertical alongside `8510`'s pre-primary/primary school,
  but for adolescent secondary education (grading and graduation
  rather than promotion and safeguarding-record finalization).

## Problem

A secondary school's grading-finalization/graduation-finalization
workflow bundles several distinct concerns under one governed
workflow:

1. **Jurisdiction secondary-education licensing correctness** -- an
   official spec-basis citation from a real regulator (文部科学省/
   state Departments of Education under ESSA/Ofqual/state examination
   regulations), never fabricated.
2. **Attendance sufficiency** -- does a student's own accumulated
   attendance hours reach a jurisdiction's own recorded minimum before
   a final grade can be issued? The SECOND non-temporal instance of
   this fleet's MINIMUM-threshold sufficiency family (`association.
   registry/continuing-education-hours-insufficient?` established the
   first).
3. **Academic-integrity resolution verification** -- has an academic-
   integrity concern against the student actually stayed unresolved?
   The secondary-education-specific application of the unconditional-
   evaluation screening discipline this fleet's `casualty.governor/
   sanctions-violations` originally established -- a TWENTY-THIRD
   distinct grounding overall, and the FIRST specifically for an
   academic-integrity-flag concept.
4. **Graduation-requirement completeness** -- does a student's own
   completed-credits set actually contain every credit required for
   graduation? The THIRD instance of this fleet's set-containment/
   subset family (`registrar.registry/prerequisites-satisfied?`
   established the first, `casework.registry/eligibility-criteria-
   unsatisfied?` the second).
5. **Real, high-stakes actuation, twice** -- finalizing a real grading
   and finalizing a real graduation are two independently-gated real-
   world acts on the SAME entity (a student).

An LLM has no authority or grounding for any of these. The design
problem is therefore not "run a secondary school with an LLM" but
"seal the LLM inside a trust boundary and layer evidence-sufficiency,
attendance verification, academic-integrity-resolution verification,
graduation-requirement verification, audit and human-approval on top
of it, while structurally fixing both real actuation events as
human-only."

## Decision

### 1. This actor shares its governor+advisor names with `cloud-itonami-isic-8510`, by the blueprint's own design, not by accident

`cloud-itonami-isic-8521`'s own published blueprint names its governor
"Curriculum Safeguarding Governor" and its advisor "SchoolOps-LLM" --
the IDENTICAL names `cloud-itonami-isic-8510` (pre-primary/primary
education) uses. This is the FIRST time two distinct verticals in
this fleet share an identical governor+advisor name pair. Rather than
silently renaming this actor's governor/advisor to avoid the
collision, this build preserves the blueprint's own stated names
(this repo's own `secondary.governor`/`secondary.schoolopsllm`
namespaces are entirely separate from `8510`'s `school.governor`/
`school.schoolopsllm` -- no actual code collision exists, only a
shared human-readable name reflecting that both blueprints were
authored from the same general-education template).

### 2. SchoolOps-LLM is sealed into the bottom node; it never finalizes a grading or graduation directly

`secondary.schoolopsllm` returns exactly five kinds of proposal:
intake normalization, jurisdiction secondary-education checklist,
academic-integrity screening, grading-finalization draft, and
graduation-finalization draft. No proposal writes the SSoT or commits
a real grading/graduation finalization directly.

### 3. OperationActor = langgraph-clj StateGraph, 1 run = 1 secondary-education operation

`secondary.operation/build` is the SAME StateGraph shape as every
sibling actor's operation namespace, copied verbatim.

### 4. `attendance-hours-insufficient?` is the SECOND non-temporal instance of the MINIMUM-threshold sufficiency family

`association.registry/continuing-education-hours-insufficient?`
established the FIRST non-temporal check in this fleet's MINIMUM-
threshold sufficiency family, generalizing from elapsed-time
comparisons to a numeric-hours ground truth (completed continuing-
education hours vs. a required minimum). `attendance-hours-
insufficient?` is the SECOND instance, applying the SAME shape to a
genuinely different numeric-hours concept: a student's own
accumulated attendance hours against a jurisdiction's own minimum
requirement before a final grade can be issued.

### 5. `graduation-requirements-unsatisfied?` is the THIRD instance of the set-containment/subset family

`registrar.registry/prerequisites-satisfied?` established the FIRST
set-containment check in this fleet, `casework.registry/eligibility-
criteria-unsatisfied?` the SECOND. `graduation-requirements-
unsatisfied?` is the THIRD instance, reusing the identical `clojure.
set/subset?` shape for a genuinely different domain: a student's own
completed-credits set must be a superset of the school's own
required-credits set before graduation can be finalized.

### 6. Academic-integrity-flag screening reuses the unconditional-evaluation discipline for a twenty-third distinct grounding, and a first for this concept

`academic-integrity-flag-unresolved-violations` reuses `casualty.
governor/sanctions-violations`'s fix (evaluated unconditionally, not
scoped to a specific op, so the screening op itself can HARD-hold on
its own finding) for `:academic-integrity/screen` AND `:grading/
finalize` -- the TWENTY-THIRD distinct application of this exact
discipline in this fleet overall, and the FIRST specifically for an
academic-integrity-flag concept. This check gates only `:grading/
finalize`, not `:graduation/finalize`, since an academic-integrity
concern about a specific grading period is a distinct academic
question from whether the student's overall credit record satisfies
graduation requirements, the same domain-scoping discipline
`leasing`'s and `behavioral`'s ADR-0001s already established for their
own analogous decisions.

### 7. The unconditional-evaluation check is tested via the SCREENING op directly, per the lesson already recorded by `parksafety` and twelve later siblings

`academic-integrity-flag-is-held-and-unoverridable` calls `:academic-
integrity/screen` directly against `student-4` (an unresolved
concern), NOT `:grading/finalize` against an unscreened student --
because a failing screen is itself a HARD hold whose payload never
persists to the store, so the actuation op alone could never discover
the bad ground-truth flag through this check family without the
screening op having actually been run first. This build applied that
lesson PROACTIVELY for a thirteenth consecutive vertical (after
`eldercare`, `museum`, `conservation`, `salon`, `entertainment`,
`casework`, `hospital`, `facility`, `school`, `association`, `leasing`
and `behavioral`), further reinforcing that lessons recorded in this
fleet's ADRs transfer forward reliably.

### 8. Dual actuation, matching `6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/`8720`'s shape

`secondary.governor`'s `high-stakes` set has exactly two members
(`:actuation/finalize-grading`, `:actuation/finalize-graduation`),
each acting on the SAME student entity, each with its OWN history
collection (`grading-history`/`graduation-history`), sequence counter
and dedicated double-actuation-guard boolean.

### 9. Double-finalization guards check dedicated booleans, not `:status`

`already-graded-violations`/`already-graduated-violations` check
`:grading-finalized?`/`:graduation-finalized?`, dedicated booleans set
once and never cleared, rather than a `:status` value that could
legitimately advance past a checked state (the exact trap `cloud-
itonami-isic-6492`'s ADR-0001 documents in detail, explicitly avoided
BY DESIGN in every sibling actor's equivalent guard since). This
actor's `:status` never needs to encode "has this actuation already
happened" at all -- a deliberate architectural choice applied here for
a twenty-fourth consecutive time.

### 10. No bespoke capability lib

Like `6920`/`7120`/`8620`/`8530`/`9200`/`7500`/`9603`/`9521`/`9321`/
`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/`8610`/`9311`/`8510`/`9412`/
`6491`/`8720`, and unlike most other actors in this fleet, this
vertical's student records are practice-specific rather than a shared
cross-operator data contract -- `secondary.*` runs on the generic
identity/forms/dmn/bpmn/audit-ledger stack only, per the blueprint's
own explicit statement.

## Consequences

- (+) Secondary education gets the same governed, auditable-actor
  treatment as the thirty-two prior actors, and this fleet now has a
  TWENTY-FIFTH concrete precedent for extending past ADR-2607032000's
  original scope, deepening education coverage alongside `8510`'s
  pre-primary/primary school with a genuinely different academic-
  record shape (grading/graduation vs. promotion/safeguarding).
- (+) `attendance-hours-insufficient?` and `graduation-requirements-
  unsatisfied?` are both genuine structural contributions: the second
  instance of the MINIMUM-threshold sufficiency family and the third
  instance of the set-containment/subset family respectively, further
  validating both families' generality across distinct domains.
- (+) `academic-integrity-flag-unresolved-violations` is a genuine
  domain-modeling contribution: the first unconditional-evaluation
  grounding for an academic-integrity-flag concept, deliberately
  scoped to one of two actuations by the same domain-reasoning
  discipline `leasing`'s and `behavioral`'s ADR-0001s established.
- (+) The actuation invariant (governor + phase, two layers) is
  regression-tested by `test/secondary/phase_test.clj`'s `grading-
  finalize-never-auto-at-any-phase`/`graduation-finalize-never-auto-
  at-any-phase`.
- (+) `MemStore` ‖ `DatomicStore` parity is proven by `test/secondary/
  store_contract_test.clj`, the same `:db-api`-driven swap pattern
  every sibling actor uses, including EDN-string-encoded credit SETS
  (a new value shape for this store-parity pattern, following the same
  `enc`/`dec*` convention every compound-value attr in this fleet's
  stores uses).
- (+) The academic-integrity-flag test/demo correctly applied the
  established SCREENING-op-directly pattern for a thirteenth
  consecutive vertical -- further evidence that lessons recorded in
  this fleet's ADRs continue to transfer forward reliably.
- (-) This R0 seeds only 4 jurisdictions (JPN, USA, GBR, DEU) with an
  official spec-basis, out of ~194 worldwide; `secondary.facts/
  coverage` reports this honestly rather than claiming broader
  coverage.
- (-) `attendance-hours-insufficient?`/`graduation-requirements-
  unsatisfied?` model only a single representative attendance/credit-
  set concern, not a full curriculum-design/pedagogical-assessment
  engine -- see `secondary.facts`'s own docstring and README coverage
  table for the full honest-scope accounting.
- A test-writing bug (an incorrect expectation for `graduation-
  requirements-unsatisfied?` on an entirely EMPTY student map, in
  `test/secondary/registry_test.clj`) was caught and fixed during this
  build's lint/test pass -- the empty-subset-of-empty case is
  correctly "satisfied" (no requirements at all is trivially met), the
  same shape `registrar`'s and `casework`'s own set-containment checks
  already establish; this was a test-authoring mistake, not an
  implementation bug.
- 40 tests / 185 assertions, lint clean.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Add this as an addendum to `cloud-itonami-isic-8510`'s ADR | ❌ | `8510`'s ADR-0001's title and scope are explicitly pre-primary/primary education; `8521` is a distinct ISIC class with a distinct actuation shape (grading/graduation vs. promotion/safeguarding), even though the blueprint shares the SAME governor/advisor names |
| Rename this actor's governor/advisor to avoid colliding with `8510`'s names | ❌ | The blueprint itself explicitly publishes "SchoolOps-LLM" / "Curriculum Safeguarding Governor" as this vertical's own governor+advisor names -- silently renaming them to avoid a fleet-internal naming collision would misrepresent the published blueprint; since the two actors live in entirely separate repos with separate namespaces (`secondary.*` vs `school.*`), no actual code collision exists to force a rename |
| Keep `cloud-itonami-isic-8521` at `:blueprint` only | ❌ | The standing direction continues past `8720`; secondary education is a natural, well-precedented next domain, deepening this fleet's education coverage alongside `8510`'s pre-primary/primary school |
| Model `attendance-hours-insufficient?`/`graduation-requirements-unsatisfied?` as new, unrelated check families | ❌ | The actual comparison shapes (a minimum numeric threshold, a set-containment/subset test) are identical to the established MINIMUM-threshold and set-containment families respectively; honestly framing these as further instances, not new families, keeps the fleet's check-family taxonomy accurate |
| Test `academic-integrity-flag-unresolved-violations` via an actuation op against an unscreened student (the shape `parksafety`'s ORIGINAL, buggy test used) | ❌ | Already proven wrong by `parksafety`'s own ADR-2607071922 Decision 5 and reconfirmed by twelve later siblings' ADR-0001s -- a failing screen never persists its payload to the store, so the actuation op alone cannot discover the bad ground-truth flag through this check family; this build tested the SCREENING op directly from the start |
| Reference a capability lib (e.g. a hypothetical `kotoba-lang/secondary-school`) for consistency with most prior actors | ❌ | The blueprint itself explicitly states this vertical's records are practice-specific, not a shared cross-operator contract -- inventing a capability lib reference where the blueprint says none exists would misrepresent the domain, the same reasoning established by every "no bespoke capability lib" sibling's ADR |

## References

- ADR-2607071250/ADR-2607071320/ADR-2607071351/ADR-2607071618/
  ADR-2607071640/ADR-2607071654/ADR-2607071717/ADR-2607071732/
  ADR-2607071752/ADR-2607071819/ADR-2607071849/ADR-2607071922/
  ADR-2607072715/ADR-2607072730/ADR-2607072745/ADR-2607072800/
  ADR-2607072815/ADR-2607072830/ADR-2607072845/ADR-2607072900/
  ADR-2607072915/ADR-2607080100/ADR-2607080200/ADR-2607080300
  (`6612`/`6492`/`6920`/`6611`/`7120`/`8620`/`8530`/`9200`/`7500`/
  `9603`/`9521`/`9321`/`8730`/`9102`/`9103`/`9602`/`9000`/`8890`/
  `8610`/`9311`/`8510`/`9412`/`6491`/`8720`, first twenty-four post-
  batch verticals)
- ADR-2607032000 (original insurance/real-estate batch, Addenda 1-7)
- `cloud-itonami-isic-8510/docs/adr/0001-architecture.md` (the
  sibling ADR this actor's governor/advisor naming echoes)
- `cloud-itonami-isic-8521/docs/adr/0001-architecture.md` (this ADR)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn`
  (fleet-wide maturity registry)
