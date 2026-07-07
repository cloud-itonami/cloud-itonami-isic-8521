(ns secondary.registry
  "Pure-function grading-finalization + graduation-finalization record
  construction -- an append-only secondary-school book-of-record
  draft.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a grading-finalization or
  graduation-finalization reference number -- every school/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest, non-
  fabricating discipline `secondary.facts` uses.

  `attendance-hours-insufficient?` is the SECOND non-temporal instance
  of this fleet's MINIMUM-threshold sufficiency family
  (`association.registry/continuing-education-hours-insufficient?`
  established the first, generalizing from elapsed-time comparisons to
  a numeric-hours ground truth) -- applied here to a student's own
  accumulated attendance hours against a jurisdiction's own minimum
  requirement before a final grade can be issued.

  `graduation-requirements-unsatisfied?` reuses `registrar.registry/
  prerequisites-satisfied?`'s SET-CONTAINMENT/subset shape (the FIRST
  instance in this fleet, later reused by `casework.registry/
  eligibility-criteria-unsatisfied?` as the SECOND) for a THIRD domain:
  a student's own completed-credits set must be a superset of the
  school's own required-credits set before graduation can be
  finalized.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real school-information system. It builds the RECORD a
  school would keep, not the act of finalizing the grading or
  graduation decision itself (that is `secondary.operation`'s
  `:grading/finalize`/`:graduation/finalize`, always human-gated --
  see README `Actuation`)."
  (:require [clojure.set :as set]
            [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  school's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn attendance-hours-insufficient?
  "Does `student`'s own `:attendance-hours-completed` fall short of the
  jurisdiction's own recorded `:attendance-hours-required` minimum? A
  pure ground-truth check against the student's own permanent fields
  -- no upstream comparison needed. The SECOND non-temporal instance
  of this fleet's MINIMUM-threshold sufficiency family (see ns
  docstring)."
  [{:keys [attendance-hours-completed attendance-hours-required]}]
  (and (number? attendance-hours-completed) (number? attendance-hours-required)
       (< attendance-hours-completed attendance-hours-required)))

(defn graduation-requirements-unsatisfied?
  "Does `student`'s own `:credits-earned` set fail to contain EVERY
  credit in its own `:credits-required` set? A pure ground-truth
  SET-CONTAINMENT check against the student's own permanent fields --
  no upstream comparison needed. The THIRD instance of this fleet's
  set-containment/subset family (see ns docstring)."
  [{:keys [credits-earned credits-required]}]
  (not (set/subset? (set credits-required) (set credits-earned))))

(defn register-grading-finalization
  "Validate + construct the GRADING-FINALIZATION registration DRAFT --
  the school's own legal act of finalizing a real student's course
  grades. Pure function -- does not touch any real school-information
  system; it builds the RECORD a school would keep. `secondary.
  governor` independently re-verifies the student's own attendance
  sufficiency and unresolved academic-integrity status, and blocks a
  double-finalization of the same student's grading, before this is
  ever allowed to commit."
  [student-id jurisdiction sequence]
  (when-not (and student-id (not= student-id ""))
    (throw (ex-info "grading-finalization: student_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "grading-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "grading-finalization: sequence must be >= 0" {})))
  (let [grading-number (str (str/upper-case jurisdiction) "-GRD-" (zero-pad sequence 6))
        record {"record_id" grading-number
                "kind" "grading-finalization-draft"
                "student_id" student-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "grading_number" grading-number
     "certificate" (unsigned-certificate "GradingFinalization" grading-number grading-number)}))

(defn register-graduation-finalization
  "Validate + construct the GRADUATION-FINALIZATION registration
  DRAFT -- the school's own legal act of finalizing a real student's
  graduation. Pure function -- does not touch any real school-
  information system; it builds the RECORD a school would keep.
  `secondary.governor` independently re-verifies the student's own
  graduation-requirement completeness, and blocks a double-
  finalization of the same student's graduation, before this is ever
  allowed to commit."
  [student-id jurisdiction sequence]
  (when-not (and student-id (not= student-id ""))
    (throw (ex-info "graduation-finalization: student_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "graduation-finalization: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "graduation-finalization: sequence must be >= 0" {})))
  (let [graduation-number (str (str/upper-case jurisdiction) "-GRA-" (zero-pad sequence 6))
        record {"record_id" graduation-number
                "kind" "graduation-finalization-draft"
                "student_id" student-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "graduation_number" graduation-number
     "certificate" (unsigned-certificate "GraduationFinalization" graduation-number graduation-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
