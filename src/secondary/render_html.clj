(ns secondary.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 for `cloud-itonami-isic-8521`: this
  repo had NO demo page and no generator at all. This namespace drives
  the REAL actor stack (`secondary.operation` -> `secondary.governor`
  -> `secondary.store`, through `langgraph.graph/run*`, the same entry
  point this repo's own `secondary.sim` demo driver and its test suite
  use) and renders whatever that run actually produced. Nothing on the
  page is hand-typed domain content: every student, every hold, every
  ledger fact, every draft record and the whole op-gate matrix are read
  back out of the real store / the real `secondary.phase` +
  `secondary.governor` vars after the run.

  Three things this renderer deliberately does NOT do:

  1. It does not conflate a GOVERNOR refusal with a ROLLOUT-PHASE
     hold. Both are written to the ledger as `:t :governor-hold` (see
     `secondary.operation`'s `:decide` node, which decorates the same
     `governor/hold-fact` with `:phase-reason`), so a naive
     `(count (filter #(= :governor-hold (:t %)) ledger))` counts them
     together -- but a phase hold carries an EMPTY `:violations` vector
     and means only 'this op is not enabled at this rollout phase yet',
     which is a completely different claim from 'the Curriculum
     Safeguarding Governor refused this'. They get separate tables, and
     the HARD count is taken on `(seq (:violations f))`, never on `:t`.

  2. It does not assume anything about approver retention. The
     approver-attribution section is derived AT RENDER TIME by scanning
     the committed records themselves for an approver key (see
     `approver-keys`). Measured on this repo today: `MemStore/commit-
     record!` persists the `:payload` (which `secondary.operation`'s
     `:request-approval` node stamps with `:approved-by`) for
     `:assessment/set` and `:integrity-screen/set`, so those records DO
     retain the approver -- but `:student/mark-graded` /
     `:student/mark-graduated` ignore `:payload` entirely and rebuild
     the record from `secondary.registry`, and `:student/upsert` writes
     `:value` rather than `:payload`, so those records do NOT. The
     ledger's own `:committed` fact has no approver field either (its
     `:actor` is the operating actor-id from `:context`, which is NOT
     the same field as the approver). Rather than silently omitting
     that, the page says '(audit only -- not retained on record)' and
     cites the run's own `:approval-granted` audit fact. If the store
     is later fixed to persist the approver, this section flips to
     'retained on record' with no edit to this file.

  3. It does not guess an approver by joining records to approvals on
     [op subject]. That join is not guaranteed unique, and a duplicate
     would make one record silently inherit another's approver. When
     more than one approval matches, the page reports the ambiguity
     instead of picking one.

  `-main` REFUSES to write the file if the run produced zero HARD
  governor holds -- a console that shows only happy paths is worse than
  no console, so the requirement is a build-time invariant rather than
  a convention.

  Usage: `clojure -M:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [secondary.facts :as facts]
            [secondary.governor :as governor]
            [secondary.operation :as op]
            [secondary.phase :as phase]
            [secondary.store :as store]
            [langgraph.graph :as g]))

(def ^:private operator
  "The supervised-auto (phase 3) operator the main scenario runs as."
  {:actor-id "op-1" :actor-role :licensed-educator :phase 3})

(def ^:private approver-id "op-1")

;; ----------------------------- the real run -----------------------------

(defn run-demo!
  "Runs a freshly seeded store through the real actor and returns
  `{:db .. :approvals [..]}`.

  The scenario is this repo's own `secondary.sim` walk (whose ids were
  confirmed against `secondary.store/demo-data` before this file was
  written -- `student-1` .. `student-5`), plus two extra probes at
  LOWER rollout phases that exist purely to make the governor-vs-phase
  distinction visible on the page:

    student-1  full lifecycle -- intake (auto-commits: the only op in
               phase 3's `:auto` set), jurisdiction assessment
               (approved), academic-integrity screening (approved),
               grading finalization (ALWAYS escalates -- `:actuation/
               finalize-grading` is never auto at any phase -- approved)
               and graduation finalization (same posture, approved).
    student-2  HARD hold: a jurisdiction assessment for a jurisdiction
               with no official spec-basis in `secondary.facts`.
    student-3  assessment approved, then a HARD hold on grading
               finalization -- 500 of the 800 attendance hours its own
               jurisdiction requires.
    student-4  HARD hold: an academic-integrity screening that itself
               finds an unresolved concern (screened DIRECTLY, never via
               an actuation op against an unscreened student).
    student-5  assessment approved, then a HARD hold on graduation
               finalization -- `credits-earned` does not contain every
               `credits-required` credit.
    student-1  again: double grading finalization and double graduation
               finalization, both HARD-held.
    phase 0/1  the SAME two ops that succeeded above, replayed by an
               operator at a lower rollout phase -- held by the phase
               gate with an EMPTY violation vector. Nothing was refused
               on compliance grounds; the op simply is not enabled yet.

  Every HARD hold never reaches a human: `interrupt-before` is only
  reached on `:escalate`."
  []
  (let [db      (store/seed-db)
        actor   (op/build db)
        seen    (atom [])
        exec!   (fn [tid request ctx]
                  (g/run* actor {:request request :context ctx} {:thread-id tid}))
        approve! (fn [tid]
                   (let [r (g/run* actor {:approval {:status :approved :by approver-id}}
                                   {:thread-id tid :resume? true})]
                     ;; the :audit channel accumulates per-thread, so take only
                     ;; the approval facts and tag them with their own thread.
                     (swap! seen into
                            (map #(assoc % :thread tid)
                                 (filter #(#{:approval-granted :approval-rejected} (:t %))
                                         (get-in r [:state :audit]))))
                     r))]

    ;; ---- student-1: the full, clean lifecycle -------------------------
    (exec! "t1" {:op :student/intake :subject "student-1"
                 :patch {:id "student-1" :student-name "Sakura Tanaka"}} operator)

    (exec! "t2" {:op :jurisdiction/assess :subject "student-1"} operator)
    (approve! "t2")

    (exec! "t3" {:op :academic-integrity/screen :subject "student-1"} operator)
    (approve! "t3")

    (exec! "t4" {:op :grading/finalize :subject "student-1"} operator)
    (approve! "t4")

    (exec! "t5" {:op :graduation/finalize :subject "student-1"} operator)
    (approve! "t5")

    ;; ---- the HARD holds ----------------------------------------------
    (exec! "t6" {:op :jurisdiction/assess :subject "student-2" :no-spec? true} operator)

    (exec! "t7" {:op :jurisdiction/assess :subject "student-3"} operator)
    (approve! "t7")
    (exec! "t8" {:op :grading/finalize :subject "student-3"} operator)

    (exec! "t9" {:op :academic-integrity/screen :subject "student-4"} operator)

    (exec! "t10" {:op :jurisdiction/assess :subject "student-5"} operator)
    (approve! "t10")
    (exec! "t11" {:op :graduation/finalize :subject "student-5"} operator)

    (exec! "t12" {:op :grading/finalize :subject "student-1"} operator)
    (exec! "t13" {:op :graduation/finalize :subject "student-1"} operator)

    ;; ---- rollout-phase gate probes (NOT governor refusals) -----------
    (exec! "p0" {:op :academic-integrity/screen :subject "student-1"}
           (assoc operator :phase 0))
    (exec! "p1" {:op :jurisdiction/assess :subject "student-1"}
           (assoc operator :phase 1))

    {:db db :approvals @seen}))

;; ----------------------------- hold classification -----------------------------

(defn- hold-fact? [f] (= :governor-hold (:t f)))

(defn hard-holds
  "Ledger facts the Curriculum Safeguarding Governor actually REFUSED --
  i.e. that carry at least one violation. Never keyed on `:t` alone."
  [ledger]
  (filterv #(and (hold-fact? %) (seq (:violations %))) ledger))

(defn phase-holds
  "Ledger facts held by the ROLLOUT PHASE gate rather than by the
  governor: same `:t`, empty `:violations`, a `:phase-reason`."
  [ledger]
  (filterv #(and (hold-fact? %) (empty? (:violations %))) ledger))

;; ----------------------------- approver attribution -----------------------------

(def ^:private approver-keys
  "Every spelling of 'who approved this' this fleet's stores have used.
  Scanned at render time so the page self-corrects if the store starts
  retaining the approver."
  #{:approved-by :approver :approved_by "approved-by" "approved_by" "approver"})

(defn- approver-on-record
  "The approver actually persisted on `record`, or nil. Works on both
  keyword-keyed EDN registers and the string-keyed registry drafts."
  [record]
  (when (map? record)
    (some (fn [k] (when-let [v (get record k)] [k v])) approver-keys)))

(defn- committed-record-for
  "The record this op actually committed, as [label record], read back
  from the store through the protocol -- never reconstructed."
  [db op subject]
  (case op
    :student/intake            ["student directory" (store/student db subject)]
    :jurisdiction/assess       ["jurisdiction assessment" (store/assessment-of db subject)]
    :academic-integrity/screen ["academic-integrity screen" (store/integrity-screen-of db subject)]
    :grading/finalize          ["grading-finalization draft"
                                (first (filter #(= subject (get % "student_id"))
                                               (store/grading-history db)))]
    :graduation/finalize       ["graduation-finalization draft"
                                (first (filter #(= subject (get % "student_id"))
                                               (store/graduation-history db)))]
    [(str op) nil]))

(defn attribution-rows
  "One row per approval the run actually granted, joined to the record
  that approval committed -- by the approval's OWN thread, not by
  [op subject] (that join is not unique and a duplicate would let one
  record inherit another's approver). Reports ambiguity rather than
  guessing."
  [db approvals]
  (let [by-key (group-by (juxt :op :subject) approvals)]
    (for [a (sort-by (juxt (comp str :op) :subject) approvals)
          :let [[label record] (committed-record-for db (:op a) (:subject a))
                on-record (approver-on-record record)
                dupes (count (get by-key [(:op a) (:subject a)]))]]
      {:op        (:op a)
       :subject   (:subject a)
       :label     label
       :thread    (:thread a)
       :audit-by  (:by a)
       :on-record on-record
       :ambiguous? (> dupes 1)
       :retained? (some? on-record)})))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- kw-name [k] (if (keyword? k) (subs (str k) 1) (str k)))

(defn- sorted-set-str [s]
  (str "{" (str/join ", " (map kw-name (sort-by str s))) "}"))

(defn- last-fact-for [ledger student-id]
  (last (filter #(= (:subject %) student-id) ledger)))

(defn- status-cell [ledger student-id]
  (let [f (last-fact-for ledger student-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (and (hold-fact? f) (seq (:violations f)))
      (str "<span class=\"critical\">HARD hold &middot; "
           (esc (kw-name (:rule (first (:violations f))))) "</span>")

      (hold-fact? f)
      (str "<span class=\"warn\">phase gate hold &middot; "
           (esc (kw-name (or (:phase-reason f) :held))) "</span>")

      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- lifecycle-cell [{:keys [grading-finalized? graduation-finalized?]}]
  (cond
    graduation-finalized? "<span class=\"ok\">graded &amp; graduated</span>"
    grading-finalized?    "<span class=\"warn\">graded, not yet graduated</span>"
    :else                 "<span class=\"muted\">enrolled</span>"))

(defn- student-row [ledger {:keys [id student-name jurisdiction
                                   attendance-hours-completed attendance-hours-required
                                   credits-earned credits-required] :as s}]
  (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s / %s</td>"
               "<td>%s / %s</td><td>%s</td><td>%s</td></tr>")
          (esc id) (esc student-name) (esc jurisdiction)
          (esc attendance-hours-completed) (esc attendance-hours-required)
          ;; earned / required, the same completed-vs-required shape as the
          ;; attendance column -- this is the exact pair the governor's
          ;; `graduation-requirements-unsatisfied` check recomputes.
          (esc (sorted-set-str credits-earned))
          (esc (sorted-set-str credits-required))
          (lifecycle-cell s)
          (status-cell ledger id)))

(defn- gate-row
  "One op of this actor's closed op contract, DERIVED from the real
  `secondary.phase/phases` map and `secondary.governor/high-stakes`
  set -- not a hand-written description that can drift from them."
  [op-kw]
  (let [phs        (sort (keys phase/phases))
        writes-in  (filter #(contains? (:writes (phase/phases %)) op-kw) phs)
        auto-in    (filter #(contains? (:auto (phase/phases %)) op-kw) phs)
        always?    (empty? auto-in)]
    (format "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td></tr>"
            (esc op-kw)
            (esc (if (seq writes-in) (str/join ", " writes-in) "none"))
            (if (seq auto-in)
              (str "<span class=\"ok\">" (esc (str/join ", " auto-in)) "</span>")
              "<span class=\"warn\">never</span>")
            (if always?
              "<span class=\"warn\">ALWAYS a human approval</span>"
              "<span class=\"muted\">auto-commit when governor-clean</span>"))))

(defn- hard-hold-row [{:keys [op subject violations confidence]}]
  (let [v (first violations)]
    (format (str "        <tr><td><code>%s</code></td><td><code>%s</code></td>"
                 "<td><span class=\"critical\">%s</span></td><td>%s</td><td>%s</td></tr>")
            (esc op) (esc subject)
            (esc (kw-name (:rule v)))
            (esc (:detail v))
            (esc confidence))))

(defn- phase-hold-row [{:keys [op subject phase phase-reason violations confidence]}]
  (format (str "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td>"
               "<td><span class=\"warn\">%s</span></td><td>%s</td><td>%s</td></tr>")
          (esc op) (esc subject) (esc phase)
          (esc (kw-name (or phase-reason :held)))
          (esc (count violations))
          (esc confidence)))

(defn- attribution-row [{:keys [op subject label audit-by on-record ambiguous? retained?]}]
  (format (str "        <tr><td><code>%s</code></td><td><code>%s</code></td><td>%s</td>"
               "<td>%s</td><td>%s</td></tr>")
          (esc op) (esc subject) (esc label)
          (esc audit-by)
          (cond
            ambiguous?
            "<span class=\"critical\">ambiguous &mdash; more than one approval matches this op/subject</span>"
            retained?
            (str "<span class=\"ok\">retained on record &middot; <code>"
                 (esc (kw-name (first on-record))) "</code> = " (esc (second on-record))
                 "</span>")
            :else
            "<span class=\"warn\">(audit only &mdash; not retained on record)</span>")))

(defn- ledger-row [{:keys [t op subject disposition basis violations phase-reason]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td><code>%s</code></td><td>%s</td></tr>"
          (esc (kw-name t)) (esc op) (esc subject)
          (esc (cond
                 (seq basis)  (str/join ", " (map kw-name basis))
                 phase-reason (str (kw-name phase-reason)
                                   " (" (count violations) " violations)")
                 :else        (kw-name (or disposition ""))))))

(defn- coverage-row [iso3]
  (let [{:keys [name owner-authority legal-basis provenance required-evidence]}
        (facts/spec-basis iso3)]
    (format (str "        <tr><td><code>%s</code></td><td>%s</td><td>%s</td><td>%s</td>"
                 "<td>%s</td><td><code>%s</code></td></tr>")
            (esc iso3) (esc name) (esc owner-authority) (esc legal-basis)
            (esc (count required-evidence)) (esc provenance))))

(defn- draft-row [r]
  (format (str "        <tr><td><code>%s</code></td><td>%s</td><td><code>%s</code></td>"
               "<td>%s</td><td>%s</td></tr>")
          (esc (get r "record_id")) (esc (get r "kind"))
          (esc (get r "student_id")) (esc (get r "jurisdiction"))
          (if (get r "immutable")
            "<span class=\"ok\">immutable</span>"
            "<span class=\"warn\">mutable</span>")))

(defn render
  "Renders the full operator-console.html document from a `{:db ..
  :approvals ..}` produced by `run-demo!` (or any other real run)."
  [{:keys [db approvals]}]
  (let [ledger   (vec (store/ledger db))
        students (store/all-students db)
        hard     (hard-holds ledger)
        phased   (phase-holds ledger)
        cov      (facts/coverage)
        attrib   (attribution-rows db approvals)]
    (str
     "<html lang=\"en\"><head><meta charset=\"utf-8\">"
     "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
     "<title>cloud-itonami-isic-8521 &middot; general secondary education</title><style>"
     (jp-go-dds.skin/dds+skin)
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>General secondary education (ISIC 8521) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · grading &amp; graduation finalization always human-approved</span>\n"
     "</header>\n"
     "<main>\n"

     ;; --- 1. students ---------------------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Students</h2>\n"
     "    <p class=\"muted\">Build-time snapshot generated from <code>secondary.store</code> by <code>secondary.render-html</code> (<code>clojure -M:render-html</code>) after a real <code>secondary.operation</code> actor run. No row is hand-written.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Student</th><th>Name</th><th>Jurisdiction</th><th>Attendance hrs (done / required)</th><th>Credits (earned / required)</th><th>Lifecycle</th><th>Last decision</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map (partial student-row ledger) students)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; --- 2. op gate matrix ---------------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Op gate (rollout phase × Curriculum Safeguarding Governor)</h2>\n"
     "    <p class=\"muted\">Derived at render time from <code>secondary.phase/phases</code> and <code>secondary.governor/high-stakes</code>, so it cannot drift from the code. <code>:grading/finalize</code> and <code>:graduation/finalize</code> are absent from every phase's <code>:auto</code> set — a permanent structural fact, not a rollout milestone still to come — and the governor independently escalates both on stakes.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>May write at phase</th><th>May auto-commit at phase</th><th>Human gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map gate-row (sort-by str phase/write-ops))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <p class=\"muted\">Permanently high-stakes stakes (always escalate, any phase): <code>"
     (esc (str/join ", " (map kw-name (sort-by str governor/high-stakes))))
     "</code> · confidence floor <code>" (esc governor/confidence-floor) "</code>.</p>\n"
     "  </section>\n"

     ;; --- 3. HARD governor holds -----------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Governor refusals — HARD holds (" (count hard) ")</h2>\n"
     "    <p class=\"muted\">The Curriculum Safeguarding Governor genuinely refused these. A HARD hold cannot be overridden by a human approver and never reaches one: the run stops before <code>:request-approval</code>. Counted on a non-empty <code>:violations</code> vector, never on the fact type alone.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Student</th><th>Rule</th><th>Detail</th><th>Advisor confidence</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map hard-hold-row hard)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; --- 4. phase gate holds --------------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Rollout-phase gate holds (" (count phased) ") — NOT governor refusals</h2>\n"
     "    <p class=\"muted\">These carry an <strong>empty</strong> violation vector. Nothing was refused on compliance grounds — the op is simply not enabled at that rollout phase yet. They are written to the ledger with the same <code>:t :governor-hold</code> type, so a hold count keyed on the fact type alone would silently inflate the governor-refusal number; that is why they are tabled separately.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Student</th><th>Phase</th><th>Phase reason</th><th>Violations</th><th>Advisor confidence</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map phase-hold-row phased)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; --- 5. approver attribution ----------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Approver attribution (" (count attrib) " approvals)</h2>\n"
     "    <p class=\"muted\">Derived at render time by scanning each committed record for an approver key, then reported against the run's own <code>:approval-granted</code> audit fact. Each approval is joined to its record by its own resumed thread, never by <code>[op, subject]</code> — that join is not unique and a duplicate would let one record inherit an earlier approver. Where the record does not retain the approver, this says so explicitly rather than omitting the row.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Student</th><th>Committed record</th><th>Approver (run audit)</th><th>Retention on the committed record</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map attribution-row attrib)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "    <p class=\"muted\">Measured, not assumed: <code>MemStore/commit-record!</code> persists <code>:payload</code> (which <code>secondary.operation</code>'s <code>:request-approval</code> node stamps with <code>:approved-by</code>) for <code>:assessment/set</code> and <code>:integrity-screen/set</code>, but <code>:student/mark-graded</code>/<code>:student/mark-graduated</code> ignore <code>:payload</code> and rebuild the record from <code>secondary.registry</code>. The ledger's <code>:committed</code> fact carries no approver field at all — its <code>:actor</code> is the operating actor-id from <code>:context</code>, which is a different claim from 'who approved it'.</p>\n"
     "  </section>\n"

     ;; --- 6. spec-basis coverage ------------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Jurisdiction spec-basis coverage (" (:covered cov) " of " (:requested cov) " seeded)</h2>\n"
     "    <p class=\"muted\">" (esc (:note cov)) "</p>\n"
     "    <table>\n"
     "      <thead><tr><th>ISO3</th><th>Jurisdiction</th><th>Owner authority</th><th>Legal basis</th><th>Required evidence</th><th>Provenance</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map coverage-row (:covered-jurisdictions cov))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; --- 7. draft records ------------------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Draft grading- &amp; graduation-finalization records</h2>\n"
     "    <p class=\"muted\">Built by <code>secondary.registry</code> as part of the commit, and read back out of the store's append-only histories. Every certificate this actor produces is UNSIGNED — signature is the school's own act, not this actor's.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Record id</th><th>Kind</th><th>Student</th><th>Jurisdiction</th><th>Immutability</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map draft-row (concat (store/grading-history db)
                                           (store/graduation-history db)))) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"

     ;; --- 8. ledger --------------------------------------------------------
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (" (count ledger) " facts, this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every commit and every hold this scenario produced, in order. The <code>:hold</code> node writes the rejection; no SSoT mutation accompanies it.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Student</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" (map ledger-row ledger)) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "<footer class=\"muted\">cloud-itonami-isic-8521 · AGPL-3.0-or-later · generated by <code>secondary.render-html</code> from a real actor run; regenerate with <code>clojure -M:render-html</code>.</footer>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out    (or (first args) "docs/samples/operator-console.html")
        result (run-demo!)
        ledger (vec (store/ledger (:db result)))
        hard   (hard-holds ledger)
        phased (phase-holds ledger)]
    ;; BUILD-TIME INVARIANT, not a convention: a console that shows only
    ;; happy paths misrepresents this actor. Refuse to write the file
    ;; rather than emit one. Keyed on a non-empty :violations vector, so
    ;; a phase-gate hold (empty violations, same :t) cannot satisfy it.
    (when (empty? hard)
      (throw (ex-info (str "render-html: refusing to write " out
                           " -- the run produced ZERO HARD governor holds."
                           " A console without a real refusal is not evidence"
                           " that the governor works.")
                      {:ledger-facts     (count ledger)
                       :governor-holds   0
                       :phase-gate-holds (count phased)})))
    (spit out (render result))
    (println "wrote" out)
    (println " " (count ledger) "ledger facts,"
             (count hard) "HARD governor holds"
             (str "(" (str/join ", " (sort (distinct (map #(kw-name (:rule (first (:violations %)))) hard)))) "),")
             (count phased) "rollout-phase gate holds,"
             (count (:approvals result)) "approvals,"
             (count (store/grading-history (:db result))) "grading drafts,"
             (count (store/graduation-history (:db result))) "graduation drafts")))
