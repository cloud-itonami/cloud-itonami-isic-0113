(ns vegops.facts
  "Reference facts for vegetable and root-crop growing operations
  coordination: supply category cost policy and crop classification. This
  namespace contains pure lookup functions for domain reference data -- the
  Governor and Advisor consult these instead of inventing thresholds.
  Mirrors `cerealops.facts` (cloud-itonami-isic-0111) in shape.")

(def supply-categories
  "Procurement categories this actor may propose orders for, and the
  default cost threshold above which an order proposal must escalate for
  human sign-off (farmer/ops-manager)."
  {"seed"
   {:id "seed" :name "種苗" :cost-threshold 500}

   "fertilizer"
   {:id "fertilizer" :name "肥料" :cost-threshold 500}

   "equipment"
   {:id "equipment" :name "設備" :cost-threshold 1000}})

(defn supply-category-by-id [id]
  (get supply-categories id))

(def default-cost-threshold
  "Fallback escalation threshold used when a supply-order proposal doesn't
  cite a known category (never invent a lower bar than this)."
  500)

(def vegetable-crops
  "Vegetable, melon, root and tuber crops this actor's field records may
  cover (ISIC 0113: growing of vegetables and melons, roots and tubers --
  cereals are ISIC 0111, rice is ISIC 0112, sugar beet is ISIC 0114, and
  grapes are ISIC 0121, all out of scope)."
  {"tomato"      {:id "tomato" :name "トマト"}
   "cucumber"    {:id "cucumber" :name "キュウリ"}
   "cabbage"     {:id "cabbage" :name "キャベツ"}
   "onion"       {:id "onion" :name "タマネギ"}
   "carrot"      {:id "carrot" :name "ニンジン"}
   "potato"      {:id "potato" :name "ジャガイモ"}
   "sweet-potato" {:id "sweet-potato" :name "サツマイモ"}
   "watermelon"  {:id "watermelon" :name "スイカ"}})

(defn vegetable-crop-by-id [id]
  (get vegetable-crops id))
