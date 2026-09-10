(ns secondary.facts
  "Per-jurisdiction general-secondary-education licensing catalog --
  the G2-style spec-basis table the Curriculum Safeguarding Governor
  checks every jurisdiction/assess proposal against ('did the advisor
  cite an OFFICIAL public source for this jurisdiction's secondary-
  school grading/graduation-certification requirements, or did it
  invent one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.

  Seed values are drawn from each jurisdiction's official secondary-
  education/qualifications regulator (see `:provenance`); they are a
  STARTING catalog, not a from-scratch survey of all ~194
  jurisdictions. Extending coverage is additive: add one map to
  `catalog`, cite a real source, done -- never invent a jurisdiction's
  requirements to make coverage look bigger.

  This vertical shares its overarching legal framework with `school.
  facts`'s pre-primary/primary catalog in every seeded jurisdiction
  (the same national education act typically governs both levels),
  but cites the MORE SPECIFIC secondary-grading/graduation-
  certification national-spec rather than `school.facts`'s
  enrollment/safeguarding-oriented one -- the same 'cite the most
  domain-specific real regulator available' discipline this fleet's
  federated-jurisdiction catalogs follow. The GBR entry is the
  sharpest example: it cites Ofqual (the Office of Qualifications and
  Examinations Regulation), the REAL UK statutory body specifically
  responsible for regulating GCSE/A-level secondary qualifications --
  a genuinely DIFFERENT regulator from `school.facts`'s Ofsted
  citation (which handles broader school inspection, not
  qualifications regulation).")

(def catalog
  "iso3 -> requirement map. `:required-evidence` mirrors the generic
  student-enrollment/curriculum-approval/attendance-record/academic-
  transcript evidence set submitted in some form; `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any :jurisdiction/assess proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "文部科学省 (Ministry of Education, Culture, Sports, Science and Technology, MEXT)"
          :legal-basis "学校教育法 (School Education Act)"
          :national-spec "中学校・高等学校設置基準及び卒業認定・単位認定基準"
          :provenance "https://www.mext.go.jp/"
          :required-evidence ["生徒在籍記録 (student-enrollment record)"
                              "教育課程編成届 (curriculum-approval certificate)"
                              "出席記録 (attendance record)"
                              "成績・単位修得証明書 (academic-transcript document)"]}
   "USA" {:name "United States"
          :owner-authority "State Departments of Education (secondary-graduation-requirement authority)"
          :legal-basis "State secondary-school graduation-requirement and Carnegie-unit credit-hour statutes under the Every Student Succeeds Act (ESSA) framework"
          :national-spec "State credit-hour, attendance and graduation-requirement standards"
          :provenance "https://www.ed.gov/laws-and-policy/every-student-succeeds-act"
          :required-evidence ["Student-enrollment record"
                              "Curriculum-approval certificate"
                              "Attendance record"
                              "Academic-transcript document"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Office of Qualifications and Examinations Regulation (Ofqual)"
          :legal-basis "Apprenticeships, Skills, Children and Learning Act 2009 (Ofqual's regulatory framework)"
          :national-spec "GCSE/A-level qualifications-regulation and grading standards"
          :provenance "https://www.gov.uk/government/organisations/ofqual"
          :required-evidence ["Student-enrollment record"
                              "Curriculum-approval certificate"
                              "Attendance record"
                              "Academic-transcript document"]}
   "DEU" {:name "Germany"
          :owner-authority "Kultusministerien der Länder (state ministries of education and cultural affairs)"
          :legal-basis "Abitur-/Mittlerer-Schulabschluss-Prüfungsordnungen der Länder (state examination regulations)"
          :national-spec "Versetzungs-, Abschluss- und Notengebungsvorgaben der Länder"
          :provenance "https://www.kmk.org/"
          :required-evidence ["Schülereinschreibung (student-enrollment record)"
                              "Lehrplangenehmigung (curriculum-approval certificate)"
                              "Anwesenheitsprotokoll (attendance record)"
                              "Zeugnis/Notennachweis (academic-transcript document)"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to finalize a
  grading or graduation decision on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions actually
  have a spec-basis entry. Never report a missing jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-8521 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `secondary.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
