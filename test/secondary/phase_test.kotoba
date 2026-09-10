(ns secondary.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:grading/finalize`/`:graduation/finalize` must NEVER be
  a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [secondary.phase :as phase]))

(deftest grading-finalize-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a real grading finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :grading/finalize))
          (str "phase " n " must not auto-commit :grading/finalize")))))

(deftest graduation-finalize-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real graduation finalization"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :graduation/finalize))
          (str "phase " n " must not auto-commit :graduation/finalize")))))

(deftest academic-integrity-screen-never-auto-at-any-phase
  (testing "screening carries no direct capital risk, but is still never auto-eligible, matching every sibling screening op in this fleet"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :academic-integrity/screen))
          (str "phase " n " must not auto-commit :academic-integrity/screen")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":student/intake carries no direct capital risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:student/intake} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :student/intake} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :grading/finalize} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :graduation/finalize} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :student/intake} :commit)))))
