(ns worker
  "The Monastic Immersion — one worker, one database, one page.
   Identity comes from per-member secret links: /m/<token>."
  (:require [clojure.string :as str]))

;; ---------------------------------------------------------------- config

(def slots 5)

(def meeting-time "7:30 pm")

(def artifact-types
  ["Text" "Image" "Audio" "Video" "Performance" "Object" "Place" "Person"
   "Event" "Idea" "Game" "Other"])

(def rules
  [["Fifteen minutes, at most" "If it runs longer, bring an excerpt."]
   ["Silence during the piece" "It's a shared experience — protect the silence so everyone can actually meet it."]
   ["Comment only if moved to" "Sitting quietly with something is a full and valid response."]
   ["You won't love everything" "Someone else's taste will sometimes miss you completely. That's the deal."]
   ["No pretension" "A meme is as welcome as a sonnet. \"I don't know why but I liked it\" is a perfectly good thing to say."]])

(def err-messages
  {"title"  "The artifact needs a title."
   "dup"    "You have already added an artifact to this session."
   "full"   "This session already has its five artifacts."
   "closed" "This session is not collecting artifacts right now."
   "date"   "That date didn't look right — pick it from the calendar."
   "phase"  "That action doesn't fit the session's current phase."})

;; ---------------------------------------------------------------- small utils

(defn blank? [s]
  (or (nil? s) (= "" (.trim (str s)))))

(defn esc [s]
  (-> (str (if (nil? s) "" s))
      (.replaceAll "&" "&amp;")
      (.replaceAll "<" "&lt;")
      (.replaceAll ">" "&gt;")
      (.replaceAll "\"" "&quot;")
      (.replaceAll "'" "&#39;")))

(def roman-pairs
  [[100 "C"] [90 "XC"] [50 "L"] [40 "XL"] [10 "X"] [9 "IX"] [5 "V"] [4 "IV"] [1 "I"]])

(defn roman [n]
  (loop [n n, acc "", pairs roman-pairs]
    (if (or (<= n 0) (zero? (count pairs)))
      acc
      (let [pair (nth pairs 0)
            v (nth pair 0)
            s (nth pair 1)]
        (if (>= n v)
          (recur (- n v) (str acc s) pairs)
          (recur n acc (.slice pairs 1)))))))

(defn date-label [iso]
  (let [d (js/Date. (str iso "T12:00:00Z"))]
    (.toLocaleDateString d "en-GB" {:weekday "long" :day "numeric" :month "long"})))

(defn title-pitch
  "Deterministic 0..8 'pitch' for a neume, from the artifact title."
  [title]
  (let [chars (.split (str title) "")]
    (mod (reduce (fn [acc c] (mod (+ (* 31 acc) (.charCodeAt c 0)) 9973)) 7 chars) 9)))

;; ---------------------------------------------------------------- hiccup

(def void-tags ["input" "br" "hr" "meta" "link" "img"])

(defn attr-str [attrs]
  (if (nil? attrs)
    ""
    (str/join ""
      (mapv (fn [entry]
              (let [k (nth entry 0) v (nth entry 1)]
                (cond
                  (or (nil? v) (false? v)) ""
                  (true? v) (str " " k)
                  :else (str " " k "=\"" (esc v) "\""))))
            (js/Object.entries attrs)))))

(defn h
  "Render hiccup-ish vectors (tags are strings, since squint keywords are strings)."
  [x]
  (cond
    (nil? x) ""
    (and (vector? x) (string? (nth x 0 nil)))
    (let [tag (nth x 0)
          body (.slice x 1)
          attrs (when (map? (nth body 0 nil)) (nth body 0))
          children (if attrs (.slice body 1) body)]
      (cond
        (= "raw" tag) (str/join "" children)
        (.includes void-tags tag) (str "<" tag (attr-str attrs) ">")
        :else (str "<" tag (attr-str attrs) ">"
                   (str/join "" (mapv h children))
                   "</" tag ">")))
    (vector? x) (str/join "" (mapv h x))
    :else (esc x)))

;; ---------------------------------------------------------------- db helpers

(defn bind* [stmt params]
  (if (and params (pos? (count params)))
    (.apply (.-bind stmt) stmt params)
    stmt))

(defn ^:async q-all [db sql params]
  (let [res (js-await (.all (bind* (.prepare db sql) params)))]
    (.-results res)))

(defn ^:async q-first [db sql params]
  (js-await (.first (bind* (.prepare db sql) params))))

(defn ^:async q-run [db sql params]
  (js-await (.run (bind* (.prepare db sql) params))))

(defn ^:async current-session [db]
  (js-await (q-first db "SELECT * FROM sessions WHERE status != 'done' ORDER BY number DESC LIMIT 1" nil)))

(defn ^:async member-by-token [db token]
  (when-not (blank? token)
    (let [row (js-await (q-first db "SELECT name FROM members WHERE token = ?" [token]))]
      (when row (:name row)))))

(defn ^:async load-data [db]
  (let [session (js-await (current-session db))
        members (js-await (q-all db "SELECT name FROM members ORDER BY rowid" nil))
        artifacts (js-await (q-all db "SELECT * FROM artifacts WHERE session_id = ? ORDER BY id" [(:id session)]))
        options (js-await (q-all db "SELECT * FROM date_options WHERE session_id = ? ORDER BY date" [(:id session)]))
        votes (js-await (q-all db "SELECT v.option_id, v.member, v.answer FROM votes v JOIN date_options o ON v.option_id = o.id WHERE o.session_id = ?" [(:id session)]))
        past (js-await (q-all db "SELECT s.number, s.locked_date, a.title, a.type, a.member FROM sessions s LEFT JOIN artifacts a ON a.session_id = s.id WHERE s.status = 'done' ORDER BY s.number DESC, a.id" nil))]
    {:session session
     :members (mapv (fn [m] (:name m)) members)
     :artifacts artifacts
     :options options
     :votes votes
     :past past}))

;; ---------------------------------------------------------------- css

(def css "
:root {
  --paper: #EDEAE2;
  --ink: #2E2A24;
  --rubric: #7E2A1B;
  --gilt: #8A6D2F;
  --rule: #D6D0C2;
  --faint: #857D6D;
  --hush: #E4E0D4;
}
@media (prefers-color-scheme: dark) {
  :root {
    --paper: #211E19;
    --ink: #D8D1C0;
    --rubric: #C05B3A;
    --gilt: #B99A55;
    --rule: #3A362E;
    --faint: #8A8375;
    --hush: #2A2620;
  }
}
* { box-sizing: border-box; margin: 0; }
html { -webkit-text-size-adjust: 100%; }
body {
  background: var(--paper);
  color: var(--ink);
  font-family: 'Cardo', Georgia, serif;
  font-size: 1.125rem;
  line-height: 1.55;
}
main { max-width: 33rem; margin: 0 auto; padding: 3.5rem 1.25rem 5rem; }
a { color: inherit; }

.hedera { text-align: center; color: var(--rubric); font-size: 1.1rem; letter-spacing: .5em; }
h1 {
  text-align: center;
  font-weight: 400;
  font-size: 1.55rem;
  letter-spacing: .28em;
  text-transform: uppercase;
  margin-top: 1.1rem;
}
.session-line {
  text-align: center;
  color: var(--faint);
  font-style: italic;
  margin-top: .4rem;
}
.session-line .set-date { color: var(--gilt); font-style: normal; }

.identity {
  text-align: center;
  color: var(--faint);
  font-style: italic;
  font-size: .95rem;
  margin-top: 2rem;
}
.identity b { font-weight: 400; font-style: normal; color: var(--ink); }

.staff { display: block; margin: 2.4rem auto 0; width: 100%; max-width: 21rem; }

section { margin-top: 3rem; }
.section-label {
  color: var(--rubric);
  font-size: .8rem;
  letter-spacing: .24em;
  text-transform: uppercase;
  border-bottom: 1px solid var(--rule);
  padding-bottom: .35rem;
}

ol.artifacts { list-style: none; padding: 0; }
.artifact-row {
  display: flex; align-items: baseline; gap: .6rem;
  padding: .85rem 0; border-bottom: 1px solid var(--rule);
}
.artifact-row .a-title { font-style: italic; }
.artifact-row .a-type {
  color: var(--rubric); font-size: .78rem;
  letter-spacing: .16em; text-transform: uppercase; white-space: nowrap;
}
.artifact-row .a-member { margin-left: auto; color: var(--faint); font-size: .92rem; white-space: nowrap; }
.artifact-row.empty { color: var(--faint); font-style: italic; }

form { margin: 0; }
input[type=text], input[type=date], select {
  font: inherit; color: inherit;
  background: transparent;
  border: none; border-bottom: 1px solid var(--faint);
  padding: .25rem .1rem; border-radius: 0;
  max-width: 100%;
}
input:focus-visible, select:focus-visible, button:focus-visible, summary:focus-visible {
  outline: 2px solid var(--rubric); outline-offset: 2px;
}
button {
  font-family: inherit; font-size: .82rem;
  letter-spacing: .18em; text-transform: uppercase;
  color: var(--ink); background: transparent;
  border: 1px solid var(--faint);
  padding: .4rem .9rem; border-radius: 0;
  cursor: pointer;
}
button:hover { background: var(--ink); color: var(--paper); border-color: var(--ink); }
button.quiet { border: none; color: var(--faint); letter-spacing: .12em; padding: .2rem .3rem; }
button.quiet:hover { background: transparent; color: var(--rubric); }

.offer { margin-top: 1.4rem; }
.offer .row { display: flex; flex-wrap: wrap; gap: .9rem 1rem; align-items: baseline; margin-top: .9rem; }
.offer input[type=text] { flex: 1 1 12rem; }

.notice { color: var(--rubric); margin-top: 1rem; font-style: italic; }

.vote-row { padding: 1rem 0; border-bottom: 1px solid var(--rule); }
.vote-head { display: flex; align-items: baseline; gap: .8rem; flex-wrap: wrap; }
.vote-date { font-variant: small-caps; letter-spacing: .05em; }
.marks { margin-left: auto; display: flex; gap: .55rem; flex-wrap: wrap; justify-content: flex-end; }
.mark { font-size: .85rem; color: var(--faint); }
.mark[data-answer=yes] { color: var(--gilt); }
.mark[data-answer=no] { color: var(--faint); text-decoration: line-through; }
.mark[data-answer=maybe] { color: var(--ink); }
.vote-actions { display: flex; gap: .5rem; margin-top: .6rem; align-items: center; flex-wrap: wrap; }
.vote-actions button.current { background: var(--ink); color: var(--paper); border-color: var(--ink); }
.propose { display: flex; margin-top: 1.3rem; align-items: baseline; gap: .8rem; flex-wrap: wrap; }

.locked { margin-top: 1.4rem; }
.locked .day { color: var(--gilt); font-size: 1.25rem; }
.locked .after { display: flex; gap: 1rem; margin-top: 1.2rem; align-items: center; }

details { margin-top: 2.6rem; }
summary {
  cursor: pointer; color: var(--faint);
  font-size: .85rem; letter-spacing: .22em; text-transform: uppercase;
  list-style: none;
}
summary::before { content: '❧  '; color: var(--rubric); letter-spacing: normal; }
summary::-webkit-details-marker { display: none; }
details > *:not(summary) { margin-top: 1rem; }

ol.rule-list { padding-left: 1.4rem; }
ol.rule-list li { margin-top: .8rem; }
ol.rule-list li::marker { color: var(--rubric); }
.rule-why { color: var(--faint); font-size: .95rem; }

.past-session { margin-top: 1.6rem; }
.past-head { font-variant: small-caps; letter-spacing: .06em; }
.past-head .past-date { color: var(--faint); font-variant: normal; font-style: italic; }
ul.past-artifacts { list-style: none; padding: .3rem 0 0 1rem; color: var(--faint); }
ul.past-artifacts .a-title { font-style: italic; color: var(--ink); }

.closed-door { text-align: center; color: var(--faint); font-style: italic; margin-top: 2.5rem; }

footer { margin-top: 4.5rem; text-align: center; color: var(--faint); font-size: .85rem; font-style: italic; }
@media (prefers-reduced-motion: no-preference) {
  main { animation: settle .6s ease-out; }
  @keyframes settle { from { opacity: 0; transform: translateY(4px); } }
}
")

;; ---------------------------------------------------------------- page pieces

(defn head-block []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   [:meta {:name "robots" :content "noindex"}]
   [:title "The Monastic Immersion"]
   [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
   [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin true}]
   [:link {:rel "stylesheet"
           :href "https://fonts.googleapis.com/css2?family=Cardo:ital,wght@0,400;0,700;1,400&display=swap"}]
   [:link {:rel "icon" :href "data:image/svg+xml,<svg xmlns=%22http://www.w3.org/2000/svg%22 viewBox=%220 0 32 32%22><text y=%2224%22 font-size=%2224%22>❧</text></svg>"}]
   [:style ["raw" css]]])

(defn staff-svg
  "Four-line chant staff; each artifact is a neume, pitch drawn from its title."
  [artifacts scheduled?]
  (let [line-ys [16 28 40 52]
        neume (fn [i a]
                (let [x (+ 46 (* i 66))
                      pitch (title-pitch (:title a))
                      y (- 54 (* pitch 5))]
                  [:rect {:x x :y (- y 5) :width 10 :height 10
                          :fill (if scheduled? "var(--gilt)" "var(--ink)")}]))
        ghost (fn [i]
                [:rect {:x (+ 46 (* i 66)) :y 29 :width 10 :height 10
                        :fill "none" :stroke "var(--rule)" :stroke-width 1}])]
    [:svg {:class "staff" :viewBox "0 0 320 68" :role "img"
           :aria-label (str (count artifacts) " of " slots " artifacts gathered")}
     (mapv (fn [y] [:line {:x1 8 :y1 y :x2 312 :y2 y
                           :stroke "var(--rule)" :stroke-width 1}])
           line-ys)
     [:path {:d "M14 14 v40 M18 14 v40" :stroke "var(--rubric)" :stroke-width 2 :fill "none"}]
     (mapv (fn [i]
             (if (< i (count artifacts))
               (neume i (nth artifacts i))
               (ghost i)))
           [0 1 2 3])
     (when (>= (count artifacts) slots)
       [:path {:d "M300 30 l8 8 l-8 8" :stroke (if scheduled? "var(--gilt)" "var(--ink)")
               :stroke-width 1.5 :fill "none"}])]))

(defn action [token path]
  (str "/m/" token "/" path))

(defn artifact-section [session artifacts err member token]
  (let [n (count artifacts)
        collecting? (= "collecting" (:status session))
        offered? (pos? (count (filterv (fn [a] (= member (:member a))) artifacts)))]
    [:section
     [:div {:class "section-label"} (str "Artifacts · " n " of " slots)]
     (when err [:p {:class "notice"} err])
     [:ol {:class "artifacts"}
      (mapv (fn [a]
              [:li {:class "artifact-row"}
               [:span {:class "a-title"} (:title a)]
               [:span {:class "a-type"} (:type a)]
               [:span {:class "a-member"} (:member a)]
               (when (and collecting? (= member (:member a)))
                 [:form {:method "post" :action (action token "artifact/delete")}
                  [:input {:type "hidden" :name "id" :value (:id a)}]
                  [:button {:class "quiet" :aria-label "Withdraw this artifact"} "withdraw"]])])
            artifacts)
      (when collecting?
        (mapv (fn [_] [:li {:class "artifact-row empty"} "awaiting an offering"])
              (vec (.fill (js/Array. (- slots n)) 0))))]
     (when (and collecting? (not offered?))
       [:form {:class "offer" :method "post" :action (action token "artifact")}
        [:div {:class "row"}
         [:input {:type "text" :name "title" :placeholder "Title" :required true
                  :maxlength 120 :aria-label "Artifact title"}]
         [:select {:name "type" :aria-label "Artifact type"}
          (mapv (fn [t] [:option {:value t} t]) artifact-types)]
         [:button {} "Add your artifact"]]])]))

(defn find-vote [votes option-id member]
  (reduce (fn [acc v]
            (if (and (nil? acc) (= (:option_id v) option-id) (= (:member v) member))
              v acc))
          nil votes))

(defn vote-marks [option members votes]
  [:div {:class "marks"}
   (mapv (fn [m]
           (let [v (find-vote votes (:id option) m)
                 answer (when v (:answer v))
                 glyph (case answer "yes" "✓" "maybe" "~" "no" "✕" "·")]
             [:span {:class "mark" :data-answer answer
                     :title (str m (if answer (str " — " answer) " — no answer yet"))}
              (str m " " glyph)]))
         members)])

(defn vote-button [token option-id answer label current?]
  [:form {:method "post" :action (action token "vote") :style "display:inline"}
   [:input {:type "hidden" :name "option_id" :value option-id}]
   [:input {:type "hidden" :name "answer" :value answer}]
   [:button {:class (when current? "current")} label]])

(defn scheduling-section [session options votes members member token]
  (let [scheduled? (= "scheduled" (:status session))]
    [:section
     [:div {:class "section-label"} "Finding a day"]
     (if scheduled?
       [:div {:class "locked"}
        [:p "The four artifacts are gathered. We meet on"]
        [:p {:class "day"} (str (date-label (:locked_date session)) " · " meeting-time)]
        [:div {:class "after"}
         [:form {:method "post" :action (action token "done")}
          [:button {} "The session happened"]]
         [:form {:method "post" :action (action token "unlock")}
          [:button {:class "quiet"} "reopen the vote"]]]]
       [[:p {:style "margin-top:1rem"}
         (str "The four artifacts are gathered. Propose days; mark the ones you can make. We meet at " meeting-time ".")]
        (if (zero? (count options))
          [:p {:class "notice"} "No days proposed yet — be the first."]
          (mapv (fn [o]
                  (let [my-answer (:answer (or (find-vote votes (:id o) member) {}))
                        yes-count (count (filterv (fn [v] (and (= (:option_id v) (:id o))
                                                               (= "yes" (:answer v))))
                                                  votes))]
                    [:div {:class "vote-row"}
                     [:div {:class "vote-head"}
                      [:span {:class "vote-date"} (date-label (:date o))]
                      (vote-marks o members votes)]
                     [:div {:class "vote-actions"}
                      (vote-button token (:id o) "yes" "Can" (= "yes" my-answer))
                      (vote-button token (:id o) "maybe" "Maybe" (= "maybe" my-answer))
                      (vote-button token (:id o) "no" "Can't" (= "no" my-answer))
                      [:form {:method "post" :action (action token "lock") :style "margin-left:auto"}
                       [:input {:type "hidden" :name "option_id" :value (:id o)}]
                       [:button {:class "quiet"} (str "set this day · " yes-count " can")]]
                      [:form {:method "post" :action (action token "dates/delete")}
                       [:input {:type "hidden" :name "option_id" :value (:id o)}]
                       [:button {:class "quiet" :aria-label "Remove this day"} "✕"]]]]))
                options))
        [:form {:class "propose" :method "post" :action (action token "dates")}
         [:label {:for "new-date"} "Propose a day"]
         [:input {:type "date" :id "new-date" :name "date" :required true}]
         [:button {} "Propose"]]])]))

(defn rules-section []
  [:details
   [:summary "The Rule"]
   [:ol {:class "rule-list"}
    (mapv (fn [pair]
            [:li [:span (nth pair 0) ". "]
             [:span {:class "rule-why"} (nth pair 1)]])
          rules)]])

(defn past-sessions [past]
  (when (pos? (count past))
    (let [by-number (reduce (fn [acc row]
                              (let [k (str (:number row))]
                                (if (aget acc k)
                                  (do (.push (aget acc k) row) acc)
                                  (do (aset acc k [row]) acc))))
                            {} past)
          numbers (.sort (js/Object.keys by-number)
                         (fn [a b] (- (js/parseInt b 10) (js/parseInt a 10))))]
      [:details
       [:summary "Past sessions"]
       (mapv (fn [k]
               (let [rows (aget by-number k)
                     first-row (nth rows 0)]
                 [:div {:class "past-session"}
                  [:div {:class "past-head"}
                   (str "Session " (roman (js/parseInt k 10)) " ")
                   [:span {:class "past-date"}
                    (when (:locked_date first-row) (date-label (:locked_date first-row)))]]
                  [:ul {:class "past-artifacts"}
                   (mapv (fn [r]
                           (when (:title r)
                             [:li [:span {:class "a-title"} (:title r)]
                              (str " — " (:type r) ", " (:member r))]))
                         rows)]]))
             numbers)])))

(defn status-line [session artifacts]
  (let [status (:status session)
        n (count artifacts)]
    [:p {:class "session-line"}
     (str "Session " (roman (:number session)) " · ")
     (case status
       "collecting" (str "gathering, " n " of " slots)
       "scheduling" "finding a day"
       "scheduled" [:span {:class "set-date"}
                    (str "meets " (date-label (:locked_date session)) ", " meeting-time)]
       "")]))

(defn page [data err member token]
  (let [session (:session data)
        artifacts (:artifacts data)
        scheduled? (= "scheduled" (:status session))]
    (str
     "<!doctype html>"
     (h [:html {:lang "en"}
         (head-block)
         [:body
          [:main
           [:div {:class "hedera" :aria-hidden "true"} "❧"]
           [:h1 "The Monastic Immersion"]
           (status-line session artifacts)
           [:p {:class "identity"} "Reading as " [:b member]]
           (staff-svg artifacts scheduled?)
           (artifact-section session artifacts err member token)
           (when (or (= "scheduling" (:status session)) scheduled?)
             (scheduling-section session (:options data) (:votes data) (:members data) member token))
           (rules-section)
           (past-sessions (:past data))
           [:footer "We consume cultural artifacts together — in the same place, at the same time."]]]]))))

(defn closed-door
  "What the root URL — or an unrecognised token — shows."
  [unknown-token?]
  (str
   "<!doctype html>"
   (h [:html {:lang "en"}
       (head-block)
       [:body
        [:main
         [:div {:class "hedera" :aria-hidden "true"} "❧"]
         [:h1 "The Monastic Immersion"]
         [:p {:class "closed-door"}
          (if unknown-token?
            "This link isn't recognised. Ask the keeper of the list for a fresh one."
            "A private circle. If you belong to it, use your personal link.")]]]])))

;; ---------------------------------------------------------------- responses

(defn html-response
  ([body] (html-response body 200))
  ([body status]
   (js/Response. body {:status status
                       :headers {"content-type" "text/html;charset=utf-8"}})))

(defn redirect
  ([url token] (redirect url token nil))
  ([url token err]
   (js/Response.redirect
    (str (.-origin url) "/m/" token (when err (str "?err=" err))) 303)))

;; ---------------------------------------------------------------- actions
;; Every action already knows who is acting: `member` was resolved from the
;; secret token in the URL. Form fields are never trusted for identity.

(defn ^:async post-artifact [req env url member token]
  (let [db (:DB env)
        fd (js-await (.formData req))
        title (.trim (str (or (.get fd "title") "")))
        type* (or (.get fd "type") "Other")
        session (js-await (current-session db))]
    (cond
      (not= "collecting" (:status session)) (redirect url token "closed")
      (blank? title) (redirect url token "title")
      :else
      (let [dup (js-await (q-first db "SELECT id FROM artifacts WHERE session_id = ? AND member = ?"
                                   [(:id session) member]))
            n-row (js-await (q-first db "SELECT COUNT(*) AS n FROM artifacts WHERE session_id = ?"
                                     [(:id session)]))
            n (:n n-row)]
        (cond
          dup (redirect url token "dup")
          (>= n slots) (redirect url token "full")
          :else
          (do
            (js-await (q-run db "INSERT INTO artifacts (session_id, member, title, type) VALUES (?, ?, ?, ?)"
                             [(:id session) member title type*]))
            (when (= (inc n) slots)
              (js-await (q-run db "UPDATE sessions SET status = 'scheduling' WHERE id = ?"
                               [(:id session)])))
            (redirect url token)))))))

(defn ^:async post-artifact-delete [req env url member token]
  (let [db (:DB env)
        fd (js-await (.formData req))
        id (.get fd "id")
        session (js-await (current-session db))]
    (if (or (= "scheduled" (:status session)) (= "done" (:status session)))
      (redirect url token "phase")
      (do
        (js-await (q-run db "DELETE FROM artifacts WHERE id = ? AND member = ? AND session_id = ?"
                         [id member (:id session)]))
        (let [n-row (js-await (q-first db "SELECT COUNT(*) AS n FROM artifacts WHERE session_id = ?"
                                       [(:id session)]))]
          (when (< (:n n-row) slots)
            (js-await (q-run db "UPDATE sessions SET status = 'collecting' WHERE id = ?"
                             [(:id session)]))))
        (redirect url token)))))

(defn ^:async post-dates [req env url member token]
  (let [db (:DB env)
        fd (js-await (.formData req))
        date (str (or (.get fd "date") ""))
        session (js-await (current-session db))]
    (cond
      (not= "scheduling" (:status session)) (redirect url token "phase")
      (not (.test #"^\d{4}-\d{2}-\d{2}$" date)) (redirect url token "date")
      :else
      (do
        (js-await (q-run db "INSERT OR IGNORE INTO date_options (session_id, date, proposed_by) VALUES (?, ?, ?)"
                         [(:id session) date member]))
        (redirect url token)))))

(defn ^:async post-dates-delete [req env url member token]
  (let [db (:DB env)
        fd (js-await (.formData req))
        option-id (.get fd "option_id")
        session (js-await (current-session db))]
    (if (not= "scheduling" (:status session))
      (redirect url token "phase")
      (do
        (js-await (q-run db "DELETE FROM votes WHERE option_id = ?" [option-id]))
        (js-await (q-run db "DELETE FROM date_options WHERE id = ? AND session_id = ?"
                         [option-id (:id session)]))
        (redirect url token)))))

(defn ^:async post-vote [req env url member token]
  (let [db (:DB env)
        fd (js-await (.formData req))
        option-id (.get fd "option_id")
        answer (.get fd "answer")
        session (js-await (current-session db))]
    (cond
      (not= "scheduling" (:status session)) (redirect url token "phase")
      (not (.includes ["yes" "maybe" "no"] answer)) (redirect url token "phase")
      :else
      (let [opt (js-await (q-first db "SELECT id FROM date_options WHERE id = ? AND session_id = ?"
                                   [option-id (:id session)]))]
        (if (nil? opt)
          (redirect url token "phase")
          (do
            (js-await (q-run db "INSERT INTO votes (option_id, member, answer) VALUES (?, ?, ?) ON CONFLICT(option_id, member) DO UPDATE SET answer = excluded.answer"
                             [option-id member answer]))
            (redirect url token)))))))

(defn ^:async post-lock [req env url member token]
  (let [db (:DB env)
        fd (js-await (.formData req))
        option-id (.get fd "option_id")
        session (js-await (current-session db))
        opt (js-await (q-first db "SELECT * FROM date_options WHERE id = ? AND session_id = ?"
                               [option-id (:id session)]))]
    (if (or (not= "scheduling" (:status session)) (nil? opt))
      (redirect url token "phase")
      (do
        (js-await (q-run db "UPDATE sessions SET status = 'scheduled', locked_date = ? WHERE id = ?"
                         [(:date opt) (:id session)]))
        (redirect url token)))))

(defn ^:async post-unlock [req env url member token]
  (let [db (:DB env)
        session (js-await (current-session db))]
    (if (not= "scheduled" (:status session))
      (redirect url token "phase")
      (do
        (js-await (q-run db "UPDATE sessions SET status = 'scheduling', locked_date = NULL WHERE id = ?"
                         [(:id session)]))
        (redirect url token)))))

(defn ^:async post-done [req env url member token]
  (let [db (:DB env)
        session (js-await (current-session db))]
    (if (not= "scheduled" (:status session))
      (redirect url token "phase")
      (do
        (js-await (q-run db "UPDATE sessions SET status = 'done' WHERE id = ?" [(:id session)]))
        (js-await (q-run db "INSERT INTO sessions (number, status) VALUES (?, 'collecting')"
                         [(inc (:number session))]))
        (redirect url token)))))

;; ---------------------------------------------------------------- router

(defn ^:async handle [req env]
  (let [url (js/URL. (.-url req))
        path (.-pathname url)
        method (.-method req)
        segs (filterv (fn [s] (not= "" s)) (.split path "/"))]
    (cond
      (and (= "GET" method) (= "/" path))
      (html-response (closed-door false))

      (= "m" (nth segs 0 nil))
      (let [db (:DB env)
            token (nth segs 1 nil)
            member (js-await (member-by-token db token))]
        (cond
          (nil? member)
          (html-response (closed-door true) 404)

          (and (= "GET" method) (= 2 (count segs)))
          (let [data (js-await (load-data db))
                err-code (.get (.-searchParams url) "err")
                err (when err-code (get err-messages err-code))]
            (html-response (page data err member token)))

          (= "POST" method)
          (case (str/join "/" (.slice segs 2))
            "artifact" (js-await (post-artifact req env url member token))
            "artifact/delete" (js-await (post-artifact-delete req env url member token))
            "dates" (js-await (post-dates req env url member token))
            "dates/delete" (js-await (post-dates-delete req env url member token))
            "vote" (js-await (post-vote req env url member token))
            "lock" (js-await (post-lock req env url member token))
            "unlock" (js-await (post-unlock req env url member token))
            "done" (js-await (post-done req env url member token))
            (js/Response. "Not found" {:status 404}))

          :else (js/Response. "Not found" {:status 404})))

      :else (js/Response. "Not found" {:status 404}))))

(defn ^:async handler [req env]
  (try
    (js-await (handle req env))
    (catch :default e
      (js/Response. (str "Something went wrong: " (.-message e)) {:status 500}))))
