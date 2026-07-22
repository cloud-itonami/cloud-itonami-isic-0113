(ns vegops.operation-test
  "Integration tests for `vegops.operation/build` -- builds the REAL
  compiled `langgraph.graph` StateGraph and runs it end-to-end via
  `langgraph.graph/run*` through all three terminal routes (commit /
  escalate-then-approve / hard-hold). This namespace did not exist
  before: `build` previously returned a bare closure over the
  synchronous `run-operation` flow and never touched
  `kotoba-lang/langgraph` at all (its own docstring called it a
  \"Stub for building a langgraph-clj StateGraph\"). These tests prove
  the compiled graph is real and that the audit ledger
  (`vegops.store/append-ledger!`) is genuinely wired into the
  `:commit`/`:hold` nodes."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [vegops.operation :as operation]
            [vegops.store :as store]))

(def ^:private farmer {:actor-id "farmer-01" :role :farm-operator :phase :phase-3})

(defn- fresh-store []
  (store/mem-store
   {:initial-fields
    {"field-001" {:id "field-001" :name "Test Farm South Field" :crop "tomato"}}}))

(deftest commit-path-clean-proposal
  (testing "a clean, phase-3, high-confidence field-operation schedule
            commits through the real compiled graph and durably
            appends to the store's audit ledger"
    (let [st (fresh-store)
          actor (operation/build st)
          result (g/run* actor
                         {:request {:op :schedule-field-operation :field-id "field-001"
                                    :operation-type "planting" :requested-date "2026-08-01"}
                          :context farmer}
                         {:thread-id "t-commit"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:disposition state)))
      (is (false? (:hard? (:verdict state))))
      (is (false? (:escalate? (:verdict state))))
      (testing "the ledger (not just transient :audit) has the committed fact"
        (let [ledger (store/ledger st)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :schedule-field-operation (:op (first ledger))))
          (is (= "field-001" (:subject (first ledger)))))))))

(deftest hard-hold-path-unregistered-field
  (testing "an unregistered field is a HARD violation -- the real graph
            routes straight to :hold, no interrupt, and durably
            records the hold fact"
    (let [st (fresh-store)
          actor (operation/build st)
          result (g/run* actor
                         {:request {:op :log-field-record :field-id "field-999"
                                    :acreage 10 :crop "onion"}
                          :context farmer}
                         {:thread-id "t-hold"})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger st)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (seq (:violations (first ledger))))))))

(deftest escalate-then-approve-commits
  (testing "flag-crop-health-concern ALWAYS escalates -- the real graph
            GENUINELY interrupts (checkpointed) at :request-approval;
            a human approve! resumes the SAME compiled graph and
            commits via the graph's own :request-approval -> :commit
            edge, durably appending to the ledger"
    (let [st (fresh-store)
          actor (operation/build st)
          held (g/run* actor
                       {:request {:op :flag-crop-health-concern :field-id "field-001"
                                  :concern "うどんこ病の疑い"}
                        :context farmer}
                       {:thread-id "t-escalate"})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger st)) "not yet committed -- awaiting human sign-off")
      (let [approved (g/run* actor {:approval {:status :approved :by "farmer-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:disposition approved-state)))
        (let [ledger (store/ledger st)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :flag-crop-health-concern (:op (first ledger)))))))))

(deftest escalate-then-reject-holds
  (testing "a human rejecting an escalated request routes to :hold via
            the :request-approval node's own disposition, and durably
            records the rejection -- not a hand-rolled parallel path"
    (let [st (fresh-store)
          actor (operation/build st)
          _held (g/run* actor
                        {:request {:op :order-supplies :field-id "field-001"
                                   :category "seed" :cost 900}
                         :context farmer}
                        {:thread-id "t-reject"})
          rejected (g/run* actor {:approval {:status :rejected :by "farmer-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger st)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))))))
