(ns secondary.registry-test
  (:require [clojure.test :refer [deftest is]]
            [secondary.registry :as r]))

;; ----------------------------- attendance-hours-insufficient? -----------------------------

(deftest not-insufficient-when-at-or-above-required
  (is (not (r/attendance-hours-insufficient? {:attendance-hours-completed 800 :attendance-hours-required 800})))
  (is (not (r/attendance-hours-insufficient? {:attendance-hours-completed 900 :attendance-hours-required 800}))))

(deftest insufficient-when-below-required
  (is (r/attendance-hours-insufficient? {:attendance-hours-completed 799 :attendance-hours-required 800}))
  (is (r/attendance-hours-insufficient? {:attendance-hours-completed 500 :attendance-hours-required 800})))

(deftest insufficient-is-false-on-missing-fields
  (is (not (r/attendance-hours-insufficient? {})))
  (is (not (r/attendance-hours-insufficient? {:attendance-hours-completed 500}))))

;; ----------------------------- graduation-requirements-unsatisfied? -----------------------------

(deftest not-unsatisfied-when-required-is-subset-of-earned
  (is (not (r/graduation-requirements-unsatisfied? {:credits-earned #{:math1 :science1 :history1 :english1}
                                                     :credits-required #{:math1 :science1}})))
  (is (not (r/graduation-requirements-unsatisfied? {:credits-earned #{:math1 :science1}
                                                     :credits-required #{:math1 :science1}}))))

(deftest unsatisfied-when-a-required-credit-is-missing
  (is (r/graduation-requirements-unsatisfied? {:credits-earned #{:math1 :science1}
                                                :credits-required #{:math1 :science1 :history1 :english1}})))

(deftest unsatisfied-with-no-earned-credits-against-a-real-requirement
  (is (r/graduation-requirements-unsatisfied? {:credits-required #{:math1}}))
  (is (not (r/graduation-requirements-unsatisfied? {}))
      "no requirements at all -> trivially satisfied, the same empty-subset-of-empty shape registrar's prerequisites-satisfied? and casework's eligibility-criteria-unsatisfied? already establish"))

;; ----------------------------- register-grading-finalization -----------------------------

(deftest grading-is-a-draft-not-a-real-grading
  (let [result (r/register-grading-finalization "student-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest grading-assigns-grading-number
  (let [result (r/register-grading-finalization "student-1" "JPN" 7)]
    (is (= (get result "grading_number") "JPN-GRD-000007"))
    (is (= (get-in result ["record" "student_id"]) "student-1"))
    (is (= (get-in result ["record" "kind"]) "grading-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest grading-validation-rules
  (is (thrown? Exception (r/register-grading-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-grading-finalization "student-1" "" 0)))
  (is (thrown? Exception (r/register-grading-finalization "student-1" "JPN" -1))))

;; ----------------------------- register-graduation-finalization -----------------------------

(deftest graduation-is-a-draft-not-a-real-graduation
  (let [result (r/register-graduation-finalization "student-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest graduation-assigns-graduation-number
  (let [result (r/register-graduation-finalization "student-1" "JPN" 3)]
    (is (= (get result "graduation_number") "JPN-GRA-000003"))
    (is (= (get-in result ["record" "student_id"]) "student-1"))
    (is (= (get-in result ["record" "kind"]) "graduation-finalization-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest graduation-validation-rules
  (is (thrown? Exception (r/register-graduation-finalization "" "JPN" 0)))
  (is (thrown? Exception (r/register-graduation-finalization "student-1" "" 0)))
  (is (thrown? Exception (r/register-graduation-finalization "student-1" "JPN" -1))))

(deftest history-is-append-only
  (let [c1 (r/register-grading-finalization "student-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-grading-finalization "student-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-GRD-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-GRD-000001" (get-in hist2 [1 "record_id"])))))
