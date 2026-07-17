# Operator quickstart: General secondary education

**Blueprint:** `cloud-itonami-isic-8521`  
**ISIC Rev.5:** 8521  
**Maturity:** implemented  
**License:** AGPL-3.0-or-later  

## Prerequisites

- **Clojure 1.11+** (`clojure` CLI installed)
- **Git** to clone/fork this repository
- If running inside the monorepo, `langgraph-clj` and `langchain-clj` resolve via `:local/root`. In a standalone fork, override `deps.edn` with git coordinates as noted in the `deps.edn` comment.

## Quick start (5 minutes)

### 1. Clone and enter the repo

```bash
git clone https://github.com/cloud-itonami/cloud-itonami-isic-8521
cd cloud-itonami-isic-8521
```

### 2. Run the demo

Walk through one clean dual-actuation lifecycle (student intake → jurisdiction assessment → academic-integrity screening → grading finalization → graduation finalization) plus five hard-hold cases that the Governor blocks:

```bash
clojure -M:dev:run
```

This calls `secondary.sim/main` and prints:
- One successful student lifecycle with all five operations (intake, assessment, screening, grading, graduation)
- Five separate demo cases where the Curriculum Safeguarding Governor enforces a hard hold on violations (fabricated spec-basis, incomplete evidence, insufficient attendance hours, unresolved academic-integrity flag, unsatisfied graduation requirements)
- All decisions are audit-logged

### 3. Run tests

Verify the governor contract, phase invariants, store parity, registry conformance and facts coverage:

```bash
clojure -M:dev:test
```

Tests confirm:
- Governor never allows finalization of a grading or graduation without human sign-off
- Hard violations (spec-basis fabrication, incomplete evidence, insufficient attendance, unresolved academic-integrity, unsatisfied graduation requirements) always force hold
- A student cannot be graded or graduated twice
- All store implementations (MemStore, DatomicStore) maintain parity
- Jurisdiction facts are spec-backed and coverage is honestly reported

### 4. Run linter

Static analysis via clj-kondo (errors fail in CI):

```bash
clojure -M:lint
```

## Source layout

| File | Role |
|---|---|
| `src/secondary/governor.cljc` | **Curriculum Safeguarding Governor** — 5 hard-hold checks (spec-basis, evidence-incomplete, attendance-hours-insufficient, academic-integrity-flag-unresolved, graduation-requirements-unsatisfied) + already-graded/already-graduated guards |
| `src/secondary/phase.cljc` | **Phase table** (0→3) — read-only → assisted intake → assisted assess → supervised. Both grading and graduation finalization always require human sign-off. |
| `src/secondary/schoolopsllm.cljc` | **SchoolOps-LLM Advisor** — drafts intake/assessment/screening/grading/graduation proposals (mock or real LLM) |
| `src/secondary/operation.cljc` | **OperationActor** — langgraph-clj StateGraph orchestrating the full actor |
| `src/secondary/store.cljc` | **Store protocol** — MemStore ‖ DatomicStore with append-only audit ledger |
| `src/secondary/registry.cljc` | Grading/graduation draft records and sufficiency checks (attendance-hours, graduation-requirements) |
| `src/secondary/facts.cljc` | Per-jurisdiction secondary-education licensing catalog (spec-backed, honest coverage) |
| `src/secondary/sim.cljc` | Demo driver — runs one clean lifecycle and five hard-hold cases |

## Next steps

1. **Understand the governance model:** Read `docs/adr/0001-architecture.md` for rationale and design history.
2. **Explore business model:** See `docs/business-model.md` for customer, offer and revenue.
3. **Plan first deployment:** Follow `docs/operator-guide.md` for registration, import, validation and configuration.
4. **Integrate with your school system:** Override the mock LLM advisor in `src/secondary/schoolopsllm.cljc` with your jurisdiction's real student-information-system and curriculum data.
5. **Customize Governor policy:** Adjust hold/escalation rules in `src/secondary/governor.cljc` for your jurisdiction's specific requirements.

## Trust and transparency

- **No finalization without human sign-off:** Both the Governor and the phase table enforce this at all times.
- **Immutable audit ledger:** Every intake, assessment, screening, grading and graduation action is logged and queryable.
- **No fabricated citations:** All jurisdiction requirements must cite an official spec-basis; the facts catalog reports honest coverage.
- **Double-finalization guard:** A student cannot be graded or graduated twice; attempted duplicates are blocked on the actor's own student facts alone.
- **Override remains manual:** Emergency override paths are outside the actor, keeping LLM out of the critical path.

## Support

- Report issues: https://github.com/cloud-itonami/cloud-itonami-isic-8521/issues
- See CONTRIBUTING.md for development guidelines
- Security concerns: See SECURITY.md
