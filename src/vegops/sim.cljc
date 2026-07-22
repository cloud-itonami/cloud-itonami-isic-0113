(ns vegops.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a registered field
  through a clean phase-3 auto-commit, an always-escalate crop-health
  concern (human approves), a high-cost supply order (human rejects),
  and a hard-hold (unregistered field), then prints the resulting
  audit ledger. Mirrors `cerealops.sim` (cloud-itonami-isic-0111).

  Replaces the previous demo, which called the old `operation/build`'s
  bare closure (`(actor request context)`) -- that closure never
  touched `kotoba-lang/langgraph` at all; this one drives the REAL
  compiled StateGraph via `langgraph.graph/run*`, including a genuine
  checkpointed interrupt/resume for the escalated paths."
  (:require [langgraph.graph :as g]
            [vegops.operation :as operation]
            [vegops.store :as store]))

(def farmer {:actor-id "farmer-01" :role :farm-operator :phase :phase-3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "farmer-01"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "farmer-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a commit path, an
  escalate->approve->commit path, an escalate->reject->hold path, and
  a hard-hold path; print each result and the final audit ledger."
  []
  (let [st (store/mem-store
            {:initial-fields
             {"field-001"
              {:id "field-001"
               :name "Test Farm South Field"
               :crop "tomato"}}})
        actor (operation/build st)]

    (println "=== Vegetable and Root-Crop Growing Operations Coordinator Demo ===")

    (println "\n== log-field-record field-001 (phase-3, governor-clean -> commit) ==")
    (println (exec-op actor "t1"
                      {:op :log-field-record :field-id "field-001"
                       :acreage 12 :crop "tomato" :record-type "planting"}
                      farmer))

    (println "\n== flag-crop-health-concern field-001 (ALWAYS escalates -- farmer approves) ==")
    (let [r (exec-op actor "t2"
                     {:op :flag-crop-health-concern :field-id "field-001"
                      :concern "うどんこ病の疑い"}
                     farmer)]
      (println r)
      (println "-- farmer/agronomist approves --")
      (println (approve! actor "t2")))

    (println "\n== order-supplies field-001 over cost threshold (escalates -- farmer rejects) ==")
    (let [r (exec-op actor "t3"
                     {:op :order-supplies :field-id "field-001"
                      :category "seed" :cost 900}
                     farmer)]
      (println r)
      (println "-- farmer rejects --")
      (println (reject! actor "t3")))

    (println "\n== log-field-record field-999 (unregistered -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t4"
                      {:op :log-field-record :field-id "field-999"
                       :acreage 50 :crop "carrot"}
                      farmer))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger st)] (println f))

    {:ledger (store/ledger st)}))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
  )
