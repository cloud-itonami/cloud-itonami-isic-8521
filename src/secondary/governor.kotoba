(ns secondary.governor
  "Curriculum Safeguarding Governor -- the independent compliance
  layer that earns the SchoolOps-LLM the right to commit. The LLM has
  no notion of jurisdictional secondary-education licensing law,
  whether a student's own attendance hours have actually reached the
  jurisdiction's own required minimum, whether the student's own
  completed-credits set actually contains every required credit,
  whether an academic-integrity concern has actually stayed
  unresolved, or when an act stops being a draft and becomes a real-
  world grading finalization or graduation finalization, so this MUST
  be a separate system able to *reject* a proposal and fall back to
  HOLD -- the secondary-education analog of `cloud-itonami-isic-
  6512`'s CasualtyGovernor. This actor shares its governor and advisor
  NAMES with `school.governor`/`school.schoolopsllm` (`cloud-itonami-
  isic-8510`, pre-primary/primary education) -- the blueprint's own
  template names both verticals identically ('SchoolOps-LLM' /
  'Curriculum Safeguarding Governor') even though they are separate
  repos with separate namespaces (`secondary.*` here, `school.*`
  there) and separate actuation shapes (grading/graduation here,
  promotion/safeguarding-record there) -- the FIRST time two distinct
  verticals in this fleet share an identical governor+advisor name
  pair, reflecting the blueprint authors' own template reuse across
  two closely related general-education sub-domains rather than a
  naming collision introduced by this build.

  Seven checks, in priority order, ALL HARD violations: a human
  approver CANNOT override them (you don't get to approve your way
  past a fabricated jurisdiction spec-basis, incomplete evidence,
  insufficient attendance hours, an unresolved academic-integrity
  flag, unsatisfied graduation requirements, or a double grading/
  graduation finalization). The confidence/actuation gate is SOFT: it
  asks a human to look (low confidence / actuation), and the human may
  approve -- but see `secondary.phase`: for `:stake :actuation/
  finalize-grading`/`:actuation/finalize-graduation` (a real academic-
  record act) NO phase ever allows auto-commit either. Two independent
  layers agree that actuation is always a human call.

    1. Spec-basis                  -- did the jurisdiction proposal cite
                                       an OFFICIAL source (`secondary.
                                       facts`), or invent one?
    2. Evidence incomplete         -- for `:grading/finalize`/
                                       `:graduation/finalize`, has the
                                       jurisdiction actually been
                                       assessed with a full enrollment/
                                       curriculum-approval/attendance/
                                       transcript evidence checklist on
                                       file?
    3. Attendance hours
       insufficient                  -- for `:grading/finalize`,
                                       INDEPENDENTLY recompute whether
                                       the student's own attendance
                                       hours reach the jurisdiction's
                                       own recorded minimum
                                       (`secondary.registry/
                                       attendance-hours-insufficient?`)
                                       -- needs no proposal inspection
                                       or stored-verdict lookup at
                                       all. The SECOND non-temporal
                                       instance of this fleet's
                                       MINIMUM-threshold sufficiency
                                       family (`association.governor/
                                       continuing-education-
                                       insufficient-violations`
                                       established the first).
    4. Academic-integrity flag
       unresolved                     -- reported by THIS proposal
                                       itself (an `:academic-
                                       integrity/screen` that just
                                       found an unresolved concern), or
                                       already on file for the student
                                       (`:academic-integrity/screen`/
                                       `:grading/finalize`). Evaluated
                                       UNCONDITIONALLY (not scoped to a
                                       specific op), the SAME
                                       discipline `casualty.governor/
                                       sanctions-violations`/...
                                       (twenty-two prior siblings)...
                                       established -- the TWENTY-THIRD
                                       distinct application of this
                                       exact discipline, and the FIRST
                                       specifically for an academic-
                                       integrity-flag concept. Like the
                                       twelve most recent siblings'
                                       equivalent checks, this is
                                       exercised in tests/demo via
                                       `:academic-integrity/screen`
                                       DIRECTLY, not via an actuation
                                       op against an unscreened student
                                       -- see this ns's own test suite.
    5. Graduation requirements
       unsatisfied                    -- for `:graduation/finalize`,
                                       INDEPENDENTLY recompute whether
                                       the student's own completed-
                                       credits set actually contains
                                       every required credit
                                       (`secondary.registry/
                                       graduation-requirements-
                                       unsatisfied?`) -- needs no
                                       proposal inspection or stored-
                                       verdict lookup at all. The THIRD
                                       instance of this fleet's set-
                                       containment/subset family
                                       (`registrar.registry/
                                       prerequisites-satisfied?`
                                       established the first,
                                       `casework.registry/eligibility-
                                       criteria-unsatisfied?` the
                                       second).
    6. Confidence floor / actuation
       gate                          -- LLM confidence below threshold,
                                       OR the op is `:grading/
                                       finalize`/`:graduation/
                                       finalize` (REAL academic-record
                                       acts) -> escalate.

  Two more guards, double-grading/double-graduation prevention, are
  enforced but NOT listed as numbered HARD checks above because they
  need no upstream comparison at all -- `already-graded-violations`/
  `already-graduated-violations` refuse to finalize a grading/
  graduation for the SAME student twice, off dedicated `:grading-
  finalized?`/`:graduation-finalized?` facts (never a `:status`
  value) -- the SAME 'check a dedicated boolean, not status'
  discipline every prior sibling governor's guards establish, informed
  by `cloud-itonami-isic-6492`'s status-lifecycle bug
  (ADR-2607071320)."
  (:require [secondary.facts :as facts]
            [secondary.registry :as registry]
            [secondary.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Finalizing a real grading and finalizing a real graduation are the
  two real-world actuation events this actor performs -- a two-member
  set, matching every prior dual-actuation sibling's shape."
  #{:actuation/finalize-grading :actuation/finalize-graduation})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:jurisdiction/assess` (or `:grading/finalize`/`:graduation/
  finalize`) proposal with no spec-basis citation is a HARD violation
  -- never invent a jurisdiction's secondary-education licensing
  requirements."
  [{:keys [op]} proposal]
  (when (contains? #{:jurisdiction/assess :grading/finalize :graduation/finalize} op)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は法域要件として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:grading/finalize`/`:graduation/finalize`, the jurisdiction's
  required enrollment/curriculum-approval/attendance/transcript
  evidence must actually be satisfied -- do not trust the advisor's
  self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (contains? #{:grading/finalize :graduation/finalize} op)
    (let [s (store/student st subject)
          assessment (store/assessment-of st subject)]
      (when-not (and assessment
                     (facts/required-evidence-satisfied?
                      (:jurisdiction s) (:checklist assessment)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(生徒在籍記録/教育課程編成届/出席記録/成績・単位修得証明書等)が充足していない状態での提案"}]))))

(defn- attendance-hours-insufficient-violations
  "For `:grading/finalize`, INDEPENDENTLY recompute whether the
  student's own attendance hours reach the jurisdiction's own recorded
  minimum via `secondary.registry/attendance-hours-insufficient?` --
  needs no proposal inspection or stored-verdict lookup at all, since
  its input is a permanent ground-truth field already on the student."
  [{:keys [op subject]} st]
  (when (= op :grading/finalize)
    (let [s (store/student st subject)]
      (when (registry/attendance-hours-insufficient? s)
        [{:rule :attendance-hours-insufficient
          :detail (str subject " の出席時間(" (:attendance-hours-completed s)
                      ")が必要時間(" (:attendance-hours-required s) ")に満たない")}]))))

(defn- academic-integrity-flag-unresolved-violations
  "An unresolved academic-integrity flag -- reported by THIS proposal
  (e.g. an `:academic-integrity/screen` that itself just found one),
  or already on file in the store for the student (`:academic-
  integrity/screen`/`:grading/finalize`) -- is a HARD, un-overridable
  hold. Evaluated UNCONDITIONALLY (not scoped to a specific op) so the
  screening op itself can HARD-hold on its own finding."
  [{:keys [op subject]} proposal st]
  (let [hit-in-proposal? (= :unresolved (get-in proposal [:value :verdict]))
        student-id (when (contains? #{:academic-integrity/screen :grading/finalize} op) subject)
        hit-on-file? (and student-id (= :unresolved (:verdict (store/integrity-screen-of st student-id))))]
    (when (or hit-in-proposal? hit-on-file?)
      [{:rule :academic-integrity-flag-unresolved
        :detail "未解決の学業不正行為に関する懸念がある状態での成績確定提案は進められない"}])))

(defn- graduation-requirements-unsatisfied-violations
  "For `:graduation/finalize`, INDEPENDENTLY recompute whether the
  student's own completed-credits set actually contains every
  required credit via `secondary.registry/graduation-requirements-
  unsatisfied?` -- needs no proposal inspection or stored-verdict
  lookup at all, since its inputs are permanent ground-truth fields
  already on the student."
  [{:keys [op subject]} st]
  (when (= op :graduation/finalize)
    (let [s (store/student st subject)]
      (when (registry/graduation-requirements-unsatisfied? s)
        [{:rule :graduation-requirements-unsatisfied
          :detail (str subject " の修得単位(" (pr-str (:credits-earned s))
                      ")が卒業要件(" (pr-str (:credits-required s)) ")を充足していない")}]))))

(defn- already-graded-violations
  "For `:grading/finalize`, refuses to finalize a grading for the SAME
  student twice, off a dedicated `:grading-finalized?` fact (never a
  `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :grading/finalize)
    (when (store/student-already-graded? st subject)
      [{:rule :already-graded
        :detail (str subject " は既に成績確定済み")}])))

(defn- already-graduated-violations
  "For `:graduation/finalize`, refuses to finalize a graduation for
  the SAME student twice, off a dedicated `:graduation-finalized?`
  fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :graduation/finalize)
    (when (store/student-already-graduated? st subject)
      [{:rule :already-graduated
        :detail (str subject " は既に卒業確定済み")}])))

(defn check
  "Censors a SchoolOps-LLM proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (attendance-hours-insufficient-violations request st)
                           (academic-integrity-flag-unresolved-violations request proposal st)
                           (graduation-requirements-unsatisfied-violations request st)
                           (already-graded-violations request st)
                           (already-graduated-violations request st)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
