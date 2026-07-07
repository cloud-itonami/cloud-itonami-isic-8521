(ns secondary.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a configuration
  change, not a rewrite -- see `cloud-itonami-isic-6511`'s
  `underwriting.store-contract-test` for the same pattern on the sibling
  actor."
  (:require [clojure.test :refer [deftest is testing]]
            [secondary.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "Sakura Tanaka" (:student-name (store/student s "student-1"))))
      (is (= "JPN" (:jurisdiction (store/student s "student-1"))))
      (is (= 900 (:attendance-hours-completed (store/student s "student-1"))))
      (is (= 800 (:attendance-hours-required (store/student s "student-1"))))
      (is (= #{:math1 :science1 :history1 :english1} (:credits-earned (store/student s "student-1"))))
      (is (false? (:academic-integrity-flag? (store/student s "student-1"))))
      (is (= 500 (:attendance-hours-completed (store/student s "student-3"))))
      (is (true? (:academic-integrity-flag? (store/student s "student-4"))))
      (is (= #{:math1 :science1} (:credits-earned (store/student s "student-5"))))
      (is (false? (:grading-finalized? (store/student s "student-1"))))
      (is (false? (:graduation-finalized? (store/student s "student-1"))))
      (is (= ["student-1" "student-2" "student-3" "student-4" "student-5"]
             (mapv :id (store/all-students s))))
      (is (nil? (store/integrity-screen-of s "student-1")))
      (is (nil? (store/assessment-of s "student-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/grading-history s)))
      (is (= [] (store/graduation-history s)))
      (is (zero? (store/next-grading-sequence s "JPN")))
      (is (zero? (store/next-graduation-sequence s "JPN")))
      (is (false? (store/student-already-graded? s "student-1")))
      (is (false? (store/student-already-graduated? s "student-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "partial upsert merges, preserving untouched fields"
        (store/commit-record! s {:effect :student/upsert
                                 :value {:id "student-1" :student-name "Sakura Tanaka"}})
        (is (= "Sakura Tanaka" (:student-name (store/student s "student-1"))))
        (is (= 900 (:attendance-hours-completed (store/student s "student-1"))) "unrelated field preserved"))
      (testing "assessment / integrity-screen payloads commit and read back"
        (store/commit-record! s {:effect :assessment/set :path ["student-1"]
                                 :payload {:jurisdiction "JPN" :checklist ["a" "b"]}})
        (is (= {:jurisdiction "JPN" :checklist ["a" "b"]} (store/assessment-of s "student-1")))
        (store/commit-record! s {:effect :integrity-screen/set :path ["student-1"]
                                 :payload {:student-id "student-1" :verdict :resolved}})
        (is (= {:student-id "student-1" :verdict :resolved} (store/integrity-screen-of s "student-1"))))
      (testing "grading finalization drafts a grading record and advances the sequence"
        (store/commit-record! s {:effect :student/mark-graded :path ["student-1"]})
        (is (= "JPN-GRD-000000" (get (first (store/grading-history s)) "record_id")))
        (is (= "grading-finalization-draft" (get (first (store/grading-history s)) "kind")))
        (is (true? (:grading-finalized? (store/student s "student-1"))))
        (is (= 1 (count (store/grading-history s))))
        (is (= 1 (store/next-grading-sequence s "JPN")))
        (is (true? (store/student-already-graded? s "student-1")))
        (is (false? (store/student-already-graded? s "student-2"))))
      (testing "graduation finalization drafts a record and advances the sequence"
        (store/commit-record! s {:effect :student/mark-graduated :path ["student-1"]})
        (is (= "JPN-GRA-000000" (get (first (store/graduation-history s)) "record_id")))
        (is (= "graduation-finalization-draft" (get (first (store/graduation-history s)) "kind")))
        (is (true? (:graduation-finalized? (store/student s "student-1"))))
        (is (= 1 (count (store/graduation-history s))))
        (is (= 1 (store/next-graduation-sequence s "JPN")))
        (is (true? (store/student-already-graduated? s "student-1")))
        (is (false? (store/student-already-graduated? s "student-2"))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/student s "nope")))
    (is (= [] (store/all-students s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/grading-history s)))
    (is (= [] (store/graduation-history s)))
    (is (zero? (store/next-grading-sequence s "JPN")))
    (is (zero? (store/next-graduation-sequence s "JPN")))
    (store/with-students s {"x" {:id "x" :student-name "n" :attendance-hours-completed 900
                                 :attendance-hours-required 800
                                 :credits-earned #{:math1} :credits-required #{:math1}
                                 :academic-integrity-flag? false
                                 :grading-finalized? false :graduation-finalized? false
                                 :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:student-name (store/student s "x"))))))
