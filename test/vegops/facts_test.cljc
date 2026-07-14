(ns vegops.facts-test
  (:require [clojure.test :refer [deftest is are testing]]
            [vegops.facts :as facts]))

(deftest supply-category-lookup
  (testing "Lookup valid supply category"
    (let [c (facts/supply-category-by-id "seed")]
      (is (= "seed" (:id c)))
      (is (= "種苗" (:name c)))))

  (testing "Lookup invalid supply category"
    (is (nil? (facts/supply-category-by-id "unknown")))))

(deftest supply-category-cost-thresholds
  (testing "Category-specific cost thresholds"
    (are [id expected] (= expected (:cost-threshold (facts/supply-category-by-id id)))
      "seed"        500
      "fertilizer"  500
      "equipment"   1000)))

(deftest default-cost-threshold-value
  (testing "Default fallback threshold matches the conservative baseline"
    (is (= 500 facts/default-cost-threshold))))

(deftest vegetable-crop-lookup
  (testing "Lookup valid vegetable/root/tuber crop"
    (are [id expected-name] (= expected-name (:name (facts/vegetable-crop-by-id id)))
      "tomato"       "トマト"
      "cucumber"     "キュウリ"
      "cabbage"      "キャベツ"
      "onion"        "タマネギ"
      "carrot"       "ニンジン"
      "potato"       "ジャガイモ"
      "sweet-potato" "サツマイモ"
      "watermelon"   "スイカ"))

  (testing "Lookup invalid crop"
    (is (nil? (facts/vegetable-crop-by-id "unknown"))))

  (testing "Cereals are out of scope (ISIC 0111, not this actor)"
    (is (nil? (facts/vegetable-crop-by-id "wheat"))))

  (testing "Rice is out of scope (ISIC 0112, not this actor)"
    (is (nil? (facts/vegetable-crop-by-id "rice"))))

  (testing "Grapes are out of scope (ISIC 0121, not this actor)"
    (is (nil? (facts/vegetable-crop-by-id "grape")))))
