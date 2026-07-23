(ns secondary.store
  "SSoT for the secondary-education actor, behind a `Store` protocol
  so the backend is a swap, not a rewrite -- the same seam every prior
  `cloud-itonami-isic-*` actor in this fleet uses:

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/secondary/store_contract_test.clj), which is the whole point:
  the actor, the Curriculum Safeguarding Governor and the audit ledger
  never know which SSoT they run on.

  Like `school.store`'s dual promotion/safeguarding-record history and
  every other dual-actuation sibling before it, this actor has TWO
  actuation events (finalizing a grading, finalizing a graduation)
  acting on the SAME entity (a student), each with its OWN history
  collection, sequence counter and dedicated double-actuation-guard
  boolean (`:grading-finalized?`/`:graduation-finalized?`, never a
  `:status` value) -- the same discipline every prior sibling
  governor's guards establish, informed by `cloud-itonami-isic-6492`'s
  status-lifecycle bug (ADR-2607071320).

  The ledger stays append-only on every backend: 'which student was
  screened for an unresolved academic-integrity flag, which grading
  was finalized, which graduation was finalized, on what
  jurisdictional basis, approved by whom' is always a query over an
  immutable log -- the audit trail a student/family trusting a school
  needs, and the evidence an operator needs if a grading or graduation
  decision is later disputed."
  (:require [secondary.registry :as registry]
            [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (student [s id])
  (all-students [s])
  (integrity-screen-of [s student-id] "committed academic-integrity screening verdict for a student, or nil")
  (assessment-of [s student-id] "committed jurisdiction assessment, or nil")
  (ledger [s])
  (grading-history [s] "the append-only grading-finalization history (secondary.registry drafts)")
  (graduation-history [s] "the append-only graduation-finalization history (secondary.registry drafts)")
  (next-grading-sequence [s jurisdiction] "next grading-number sequence for a jurisdiction")
  (next-graduation-sequence [s jurisdiction] "next graduation-number sequence for a jurisdiction")
  (student-already-graded? [s student-id] "has this student's grading already been finalized?")
  (student-already-graduated? [s student-id] "has this student's graduation already been finalized?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-students [s students] "replace/seed the student directory (map id->student)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained student set covering both actuation
  lifecycles (finalizing a grading, finalizing a graduation) so the
  actor + tests run offline."
  []
  {:students
   {"student-1" {:id "student-1" :student-name "Sakura Tanaka"
                 :attendance-hours-completed 900 :attendance-hours-required 800
                 :credits-earned #{:math1 :science1 :history1 :english1}
                 :credits-required #{:math1 :science1 :history1 :english1}
                 :academic-integrity-flag? false
                 :grading-finalized? false :graduation-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "student-2" {:id "student-2" :student-name "Atlantis Doe"
                 :attendance-hours-completed 900 :attendance-hours-required 800
                 :credits-earned #{:math1 :science1 :history1 :english1}
                 :credits-required #{:math1 :science1 :history1 :english1}
                 :academic-integrity-flag? false
                 :grading-finalized? false :graduation-finalized? false
                 :jurisdiction "ATL" :status :intake}
    "student-3" {:id "student-3" :student-name "鈴木一郎"
                 :attendance-hours-completed 500 :attendance-hours-required 800
                 :credits-earned #{:math1 :science1 :history1 :english1}
                 :credits-required #{:math1 :science1 :history1 :english1}
                 :academic-integrity-flag? false
                 :grading-finalized? false :graduation-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "student-4" {:id "student-4" :student-name "田中花子"
                 :attendance-hours-completed 900 :attendance-hours-required 800
                 :credits-earned #{:math1 :science1 :history1 :english1}
                 :credits-required #{:math1 :science1 :history1 :english1}
                 :academic-integrity-flag? true
                 :grading-finalized? false :graduation-finalized? false
                 :jurisdiction "JPN" :status :intake}
    "student-5" {:id "student-5" :student-name "佐藤次郎"
                 :attendance-hours-completed 900 :attendance-hours-required 800
                 :credits-earned #{:math1 :science1}
                 :credits-required #{:math1 :science1 :history1 :english1}
                 :academic-integrity-flag? false
                 :grading-finalized? false :graduation-finalized? false
                 :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- finalize-grading!
  "Backend-agnostic `:student/mark-graded` -- looks up the student via
  the protocol and drafts the grading-finalization record, and returns
  {:result .. :student-patch ..} for the caller to persist."
  [s student-id]
  (let [st (student s student-id)
        seq-n (next-grading-sequence s (:jurisdiction st))
        result (registry/register-grading-finalization student-id (:jurisdiction st) seq-n)]
    {:result result
     :student-patch {:grading-finalized? true
                     :grading-number (get result "grading_number")}}))

(defn- finalize-graduation!
  "Backend-agnostic `:student/mark-graduated` -- looks up the student
  via the protocol and drafts the graduation-finalization record, and
  returns {:result .. :student-patch ..} for the caller to persist."
  [s student-id]
  (let [st (student s student-id)
        seq-n (next-graduation-sequence s (:jurisdiction st))
        result (registry/register-graduation-finalization student-id (:jurisdiction st) seq-n)]
    {:result result
     :student-patch {:graduation-finalized? true
                     :graduation-number (get result "graduation_number")}}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (student [_ id] (get-in @a [:students id]))
  (all-students [_] (sort-by :id (vals (:students @a))))
  (integrity-screen-of [_ id] (get-in @a [:integrity-screens id]))
  (assessment-of [_ student-id] (get-in @a [:assessments student-id]))
  (ledger [_] (:ledger @a))
  (grading-history [_] (:gradings @a))
  (graduation-history [_] (:graduations @a))
  (next-grading-sequence [_ jurisdiction] (get-in @a [:grading-sequences jurisdiction] 0))
  (next-graduation-sequence [_ jurisdiction] (get-in @a [:graduation-sequences jurisdiction] 0))
  (student-already-graded? [_ student-id] (boolean (get-in @a [:students student-id :grading-finalized?])))
  (student-already-graduated? [_ student-id] (boolean (get-in @a [:students student-id :graduation-finalized?])))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :student/upsert
      (swap! a update-in [:students (:id value)] merge value)

      :assessment/set
      (swap! a assoc-in [:assessments (first path)] payload)

      :integrity-screen/set
      (swap! a assoc-in [:integrity-screens (first path)] payload)

      :student/mark-graded
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-grading! s student-id)
            jurisdiction (:jurisdiction (student s student-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:grading-sequences jurisdiction] (fnil inc 0))
                       (update-in [:students student-id] merge student-patch)
                       (update :gradings registry/append result))))
        result)

      :student/mark-graduated
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-graduation! s student-id)
            jurisdiction (:jurisdiction (student s student-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:graduation-sequences jurisdiction] (fnil inc 0))
                       (update-in [:students student-id] merge student-patch)
                       (update :graduations registry/append result))))
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-students [s students] (when (seq students) (swap! a assoc :students students)) s))

(defn seed-db
  "A MemStore seeded with the demo student set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :assessments {} :integrity-screens {} :ledger [] :grading-sequences {}
                           :gradings [] :graduation-sequences {} :graduations []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (assessment/integrity-screen payloads, ledger
  facts, grading/graduation records, credit sets) are stored as EDN
  strings so `langchain.db` doesn't expand them into sub-entities --
  the same convention every sibling actor's store uses."
  {:student/id                      {:db/unique :db.unique/identity}
   :assessment/student-id           {:db/unique :db.unique/identity}
   :integrity-screen/student-id     {:db/unique :db.unique/identity}
   :ledger/seq                      {:db/unique :db.unique/identity}
   :grading/seq                     {:db/unique :db.unique/identity}
   :graduation/seq                  {:db/unique :db.unique/identity}
   :grading-sequence/jurisdiction   {:db/unique :db.unique/identity}
   :graduation-sequence/jurisdiction {:db/unique :db.unique/identity}})

(defn- student->tx [{:keys [id student-name attendance-hours-completed attendance-hours-required
                            credits-earned credits-required academic-integrity-flag?
                            grading-finalized? graduation-finalized?
                            jurisdiction status grading-number graduation-number]}]
  (cond-> {:student/id id}
    student-name                          (assoc :student/student-name student-name)
    attendance-hours-completed             (assoc :student/attendance-hours-completed attendance-hours-completed)
    attendance-hours-required              (assoc :student/attendance-hours-required attendance-hours-required)
    credits-earned                        (assoc :student/credits-earned (ls/enc credits-earned))
    credits-required                      (assoc :student/credits-required (ls/enc credits-required))
    (some? academic-integrity-flag?)      (assoc :student/academic-integrity-flag? academic-integrity-flag?)
    (some? grading-finalized?)            (assoc :student/grading-finalized? grading-finalized?)
    (some? graduation-finalized?)          (assoc :student/graduation-finalized? graduation-finalized?)
    jurisdiction                          (assoc :student/jurisdiction jurisdiction)
    status                                (assoc :student/status status)
    grading-number                        (assoc :student/grading-number grading-number)
    graduation-number                     (assoc :student/graduation-number graduation-number)))

(def ^:private student-pull
  [:student/id :student/student-name :student/attendance-hours-completed :student/attendance-hours-required
   :student/credits-earned :student/credits-required :student/academic-integrity-flag?
   :student/grading-finalized? :student/graduation-finalized?
   :student/jurisdiction :student/status :student/grading-number :student/graduation-number])

(defn- pull->student [m]
  (when (:student/id m)
    {:id (:student/id m) :student-name (:student/student-name m)
     :attendance-hours-completed (:student/attendance-hours-completed m)
     :attendance-hours-required (:student/attendance-hours-required m)
     :credits-earned (or (ls/dec* (:student/credits-earned m)) #{})
     :credits-required (or (ls/dec* (:student/credits-required m)) #{})
     :academic-integrity-flag? (boolean (:student/academic-integrity-flag? m))
     :grading-finalized? (boolean (:student/grading-finalized? m))
     :graduation-finalized? (boolean (:student/graduation-finalized? m))
     :jurisdiction (:student/jurisdiction m) :status (:student/status m)
     :grading-number (:student/grading-number m) :graduation-number (:student/graduation-number m)}))

(defrecord DatomicStore [conn]
  Store
  (student [_ id]
    (pull->student (d/pull (d/db conn) student-pull [:student/id id])))
  (all-students [_]
    (->> (d/q '[:find [?id ...] :where [?e :student/id ?id]] (d/db conn))
         (map #(pull->student (d/pull (d/db conn) student-pull [:student/id %])))
         (sort-by :id)))
  (integrity-screen-of [_ id]
    (ls/dec* (d/q '[:find ?p . :in $ ?sid
                :where [?k :integrity-screen/student-id ?sid] [?k :integrity-screen/payload ?p]]
              (d/db conn) id)))
  (assessment-of [_ student-id]
    (ls/dec* (d/q '[:find ?p . :in $ ?sid
                :where [?a :assessment/student-id ?sid] [?a :assessment/payload ?p]]
              (d/db conn) student-id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (grading-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :grading/seq ?s] [?e :grading/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (graduation-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :graduation/seq ?s] [?e :graduation/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp ls/dec* second))))
  (next-grading-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :grading-sequence/jurisdiction ?j] [?e :grading-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-graduation-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :graduation-sequence/jurisdiction ?j] [?e :graduation-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (student-already-graded? [s student-id]
    (boolean (:grading-finalized? (student s student-id))))
  (student-already-graduated? [s student-id]
    (boolean (:graduation-finalized? (student s student-id))))
  (commit-record! [s {:keys [effect path value payload]}]
    (case effect
      :student/upsert
      (d/transact! conn [(student->tx value)])

      :assessment/set
      (d/transact! conn [{:assessment/student-id (first path) :assessment/payload (ls/enc payload)}])

      :integrity-screen/set
      (d/transact! conn [{:integrity-screen/student-id (first path) :integrity-screen/payload (ls/enc payload)}])

      :student/mark-graded
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-grading! s student-id)
            jurisdiction (:jurisdiction (student s student-id))
            next-n (inc (next-grading-sequence s jurisdiction))]
        (d/transact! conn
                     [(student->tx (assoc student-patch :id student-id))
                      {:grading-sequence/jurisdiction jurisdiction :grading-sequence/next next-n}
                      {:grading/seq (count (grading-history s)) :grading/record (ls/enc (get result "record"))}])
        result)

      :student/mark-graduated
      (let [student-id (first path)
            {:keys [result student-patch]} (finalize-graduation! s student-id)
            jurisdiction (:jurisdiction (student s student-id))
            next-n (inc (next-graduation-sequence s jurisdiction))]
        (d/transact! conn
                     [(student->tx (assoc student-patch :id student-id))
                      {:graduation-sequence/jurisdiction jurisdiction :graduation-sequence/next next-n}
                      {:graduation/seq (count (graduation-history s)) :graduation/record (ls/enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (ls/enc fact)}])
    fact)
  (with-students [s students]
    (when (seq students) (d/transact! conn (mapv student->tx (vals students)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:students ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [students]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-students s students))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo student set -- the Datomic-
  backed analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
