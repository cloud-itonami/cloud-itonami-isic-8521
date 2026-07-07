# Business Model: General secondary education

## Classification

- Repository: `cloud-itonami-isic-8521`
- ISIC Rev.5: `8521`
- Activity: general secondary education -- academic secondary-school instruction for adolescents by licensed educators
- Social impact: education access, data sovereignty, transparent audit

## Customer

- independent secondary schools
- cooperative community schools
- homeschool-support collectives

## Offer

- student enrollment intake
- curriculum/placement proposal
- grading/graduation proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per school
- support: monthly retainer with SLA
- migration: import from an incumbent school-information system
- per-enrollment fee

## Trust Controls

- no grading or graduation decision is finalized without human sign-off
- a fabricated jurisdiction citation, incomplete evidence, insufficient
  attendance hours, an unresolved academic-integrity flag, or
  unsatisfied graduation requirements -- each forces a hold, not an
  override
- a student's grading or graduation cannot be finalized twice: a
  double-finalization attempt is held off this actor's own student
  facts alone, with no upstream comparison needed
- every intake, assessment, screening, grading and graduation path is
  auditable
- student data stays outside Git
- emergency manual override paths remain outside LLM control
