(ns secondary.governor-contract-test
  "The governor contract as executable tests -- the secondary-
  education analog of `cloud-itonami-isic-6512`'s `casualty.governor-
  contract-test`. The single invariant under test:

    SchoolOps-LLM never finalizes a grading or graduation the
    Curriculum Safeguarding Governor would reject, `:grading/
    finalize`/`:graduation/finalize` NEVER auto-commit at any phase,
    `:student/intake` (no direct capital risk) MAY auto-commit when
    clean, and every decision (commit OR hold) leaves exactly one
    ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [secondary.store :as store]
            [secondary.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :licensed-educator :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- assess!
  "Walks `subject` through assess -> approve, leaving an assessment on
  file. Uses distinct thread-ids per call site by suffixing
  `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-assess") {:op :jurisdiction/assess :subject subject} operator)
  (approve! actor (str tid-prefix "-assess")))

(defn- screen!
  "Walks `subject` through academic-integrity screening -> approve,
  leaving a screening on file. Only safe to call for a student whose
  integrity status has already resolved -- an unresolved flag HARD-
  holds the screen itself (see
  `academic-integrity-flag-is-held-and-unoverridable`)."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-screen") {:op :academic-integrity/screen :subject subject} operator)
  (approve! actor (str tid-prefix "-screen")))

(deftest clean-intake-auto-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :student/intake :subject "student-1"
                   :patch {:id "student-1" :student-name "Sakura Tanaka"}} operator)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= "Sakura Tanaka" (:student-name (store/student db "student-1"))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest jurisdiction-assess-always-needs-approval
  (testing "assess is never in any phase's :auto set -- always human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :jurisdiction/assess :subject "student-1"} operator)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (some? (store/assessment-of db "student-1")))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a jurisdiction/assess proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t3"
                    {:op :jurisdiction/assess :subject "student-1" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (nil? (store/assessment-of db "student-1")) "no assessment written"))))

(deftest grading-finalize-without-assessment-is-held
  (testing "grading/finalize before any jurisdiction assessment -> HOLD (evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t4" {:op :grading/finalize :subject "student-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest attendance-hours-insufficient-is-held
  (testing "a student whose attendance hours fall short of the required minimum -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t5pre" "student-3")
          res (exec-op actor "t5" {:op :grading/finalize :subject "student-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:attendance-hours-insufficient} (-> (store/ledger db) last :basis)))
      (is (empty? (store/grading-history db))))))

(deftest academic-integrity-flag-is-held-and-unoverridable
  (testing "an unresolved academic-integrity flag on a student -> HOLD, and never reaches request-approval -- exercised via :academic-integrity/screen DIRECTLY, not via the actuation op against an unscreened student (see this actor's governor ns docstring / parksafety's ADR-2607071922 Decision 5 / eldercare's, museum's, conservation's, salon's, entertainment's, casework's, hospital's, facility's, school's, association's, leasing's and behavioral's ADR-0001s)"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :academic-integrity/screen :subject "student-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])) "settles immediately, no interrupt")
      (is (not= :interrupted (:status res)))
      (is (some #{:academic-integrity-flag-unresolved} (-> (store/ledger db) first :basis)))
      (is (nil? (store/integrity-screen-of db "student-4")) "no clearance written"))))

(deftest graduation-requirements-unsatisfied-is-held
  (testing "a student whose completed credits don't cover every required credit -> HOLD"
    (let [[db actor] (fresh)
          _ (assess! actor "t7pre" "student-5")
          res (exec-op actor "t7" {:op :graduation/finalize :subject "student-5"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:graduation-requirements-unsatisfied} (-> (store/ledger db) last :basis)))
      (is (empty? (store/graduation-history db))))))

(deftest grading-finalize-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, attendance-sufficient student still ALWAYS interrupts for human approval -- actuation/finalize-grading is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t8pre" "student-1")
          _ (screen! actor "t8pre2" "student-1")
          r1 (exec-op actor "t8" {:op :grading/finalize :subject "student-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, grading record drafted"
        (let [r2 (approve! actor "t8")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:grading-finalized? (store/student db "student-1"))))
          (is (= 1 (count (store/grading-history db))) "one draft grading record"))))))

(deftest graduation-finalize-always-escalates-then-human-decides
  (testing "a clean, fully-assessed, requirements-satisfied student still ALWAYS interrupts for human approval -- actuation/finalize-graduation is never auto"
    (let [[db actor] (fresh)
          _ (assess! actor "t9pre" "student-1")
          r1 (exec-op actor "t9" {:op :graduation/finalize :subject "student-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, graduation record drafted"
        (let [r2 (approve! actor "t9")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:graduation-finalized? (store/student db "student-1"))))
          (is (= 1 (count (store/graduation-history db))) "one draft graduation record"))))))

(deftest grading-finalize-double-finalization-is-held
  (testing "finalizing the same student's grading twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t10pre" "student-1")
          _ (exec-op actor "t10a" {:op :grading/finalize :subject "student-1"} operator)
          _ (approve! actor "t10a")
          res (exec-op actor "t10" {:op :grading/finalize :subject "student-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-graded} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/grading-history db))) "still only the one earlier grading"))))

(deftest graduation-finalize-double-finalization-is-held
  (testing "finalizing the same student's graduation twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (assess! actor "t11pre" "student-1")
          _ (exec-op actor "t11a" {:op :graduation/finalize :subject "student-1"} operator)
          _ (approve! actor "t11a")
          res (exec-op actor "t11" {:op :graduation/finalize :subject "student-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-graduated} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/graduation-history db))) "still only the one earlier graduation"))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :student/intake :subject "student-1"
                          :patch {:id "student-1" :student-name "Sakura Tanaka"}} operator)
      (exec-op actor "b" {:op :jurisdiction/assess :subject "student-1" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
