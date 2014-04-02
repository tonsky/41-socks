(ns forty-one-socks
  (:require-macros
    [cljs.core.async.macros :refer [go]])
  (:require
    [clojure.string :as str]
    [om.core :as om :include-macros true]
    [om.dom :as dom :include-macros true]
    [cljs.core.async :refer [put! chan <! close!]]))

;; CSS sizes for bg alignment

(def ^:const sock-w 70)
(def ^:const sock-h 100)


;; Game rules constants

(def ^:const time-limit 60000)
(def ^:const field-size [6 7])


;; Data structures

(defrecord Sock [bg band heel toe])

(def world (atom {:state :starting
                  :timer [0]
                  :game {:field nil}}))


;; Utilites

(enable-console-print!)

(defn- indexed [coll]
  (map vector coll (range)))

(defn- different? [xs]
  (= (count (set xs)) (count xs)))

(defn permutations [colors]
  (for [bg    [0]
        band  (range 1 colors)
        heel  (range 1 colors)
        toe   (range 1 colors)
        :when (different? [band heel toe])]
    (Sock. bg band heel toe)))


;; Start screen

(defn start-view [_ owner]
  (reify
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:className "window"}
        (dom/h1 nil "41 socks")
        (dom/h2 nil "Match 20 sock pairs in 1 minute")
        (dom/button #js {:onClick (fn [_] (put! (:state-chan state) :playing))}
                    "Start!")))))


;; End screen

(defn gameover-view [world owner]
  (reify
    om/IRenderState
    (render-state [_ state]
      (let [socks (->> (get-in world [:game :field]) flatten)
            found (/ (->> socks (filter nil?) count) 2)
            left  (->> socks (remove nil?) count)]
        (dom/div #js {:className "window"}
          (dom/h1 nil (if (= 1 left) "You’ve done it!" "Time’s up!"))
          (dom/h2 nil (if (= 1 left)
                        (str "Matched " found " pairs with " (int (/ (get-in world [:timer 0]) 1000)) " seconds left")
                        (str "You matched " found " pairs out of 20")))
          (dom/button #js {:onClick (fn [_] (put! (:state-chan state) :playing))}
                      "Try again!"))))))

;; Timer component

(defn- format-secs [time]
  (let [sec (int (/ time 1000))]
    (if (> sec 9) (str sec) (str "0" sec))))

(defn- format-msec [time]
  (str "," (int (/ (mod time 1000) 100))))

(defn timer-view [time owner]
  (reify
    om/IWillMount
    (will-mount [_]
      (om/update! time [0] time-limit)
      (om/set-state! owner :timer
        (js/setInterval #(om/transact! time [0] (fn [t] (- t 100))) 100)))
    
    om/IWillUnmount
    (will-unmount [_]
      (js/clearInterval (om/get-state owner :timer)))
    
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:className "timer"}
        (dom/span #js {:className "sec"}
                  (format-secs (get time 0)))
        (dom/span #js {:className "msec"}
                  (format-msec (get time 0)))))))


;; Game field screen

(defn- bg-offset [i]
  (* -1 sock-w i))

(defn cell-view [sock owner]
  (reify
    om/IInitState
    (init-state [_]
      {:flipped? (> (rand-int 2) 0)})
    
    om/IRenderState
    (render-state [_ state]
      (let [{:keys [select-chan selected flipped? pos]} state]
        (if (nil? sock)
          (dom/span #js {:className "col deleted"})
          (dom/span #js {:className (str "col"
                                         " col" (second pos)
                                         (when flipped? " flipped")
                                         (when selected " selected"))
                         :onClick   (fn [_] (put! select-chan pos))}
            (dom/div #js {:className "sock"}
              (dom/div #js {:className "sock__bg"
                            :style #js { :background-position-x (bg-offset (:bg sock)) }})
              (dom/div #js {:className "sock__band"
                            :style #js { :background-position-x (bg-offset (:band sock)) }})
              (dom/div #js {:className "sock__heel"
                            :style #js { :background-position-x (bg-offset (:heel sock)) }})
              (dom/div #js {:className "sock__toe"
                            :style #js { :background-position-x (bg-offset (:toe sock)) }})
              (dom/div #js {:className "sock__stroke" })))
          )))))

(defn- gen-field [w h]
  (let [count (/ (* w h) 2)
        socks (take count (shuffle (permutations 6)))
        pairs (shuffle (interleave socks socks))
        pairs (next pairs)] ;; drop 1 sock
    (->>
      (partition-all w pairs)
      (map vec)
      vec)))
 
(defn field-view [game owner]
  (reify
    om/IInitState
    (init-state [_]
      {:select-chan (chan)
       :selected    nil})

    om/IWillUnmount
    (will-unmount [_]
      (close! (om/get-state owner :select-chan)))

    om/IWillMount
    (will-mount [_]
      (let [{:keys [select-chan]} (om/get-state owner)]
        (om/update! game :field (apply gen-field field-size))
        (go (loop []
          (when-let [clicked (<! select-chan)]
            (let [selected (om/get-state owner :selected)]
              (cond
                (= clicked selected)
                  (om/set-state! owner :selected nil)
                (= (get-in (:field @game) clicked)
                   (get-in (:field @game) selected))
                  (do
                    (om/set-state! owner :selected nil)
                    (om/transact! game :field #(-> %
                                                 (assoc-in clicked nil)
                                                 (assoc-in selected nil))))
                :else
                  (om/set-state! owner :selected clicked))
              (recur)))))))
 
    om/IRenderState
    (render-state [_ state]
      (apply dom/div #js {:className "field"}
        (for [[row row-idx] (indexed (:field game))]
          (apply dom/div #js {:className "row"}
            (for [[sock col-idx] (indexed row)]
              (om/build cell-view sock {:init-state { :select-chan (:select-chan state)
                                                      :pos         [row-idx col-idx] }
                                        :state      { :selected (= [row-idx col-idx] (:selected state)) }}))))))))

;; Puts together timer and field

(defn play-view [world owner]
  (reify
    om/IWillUpdate
    (will-update [_ props state]
      (when (or (<= (get-in props [:timer 0]) 0)
                (= 1 (->> (get-in props [:game :field]) flatten (remove nil?) count)))
        (put! (:state-chan state) :gameover)))
    
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:className "game"}
        (om/build field-view (:game world))
        (om/build timer-view (:timer world))))))


(defn screen-view [world owner]
  (reify
    om/IInitState
    (init-state [_]
      {:state-chan (chan)})
    
    om/IWillMount
    (will-mount [_]
      (let [state-chan (om/get-state owner :state-chan)]
        (go (loop []
          (when-let [state (<! state-chan)]
            (om/update! world :state state))
          (recur)))))
    
    om/IWillUnmount
    (will-unmount [_]
      (close! (om/get-state owner :state-chan)))
    
    om/IRenderState
    (render-state [_ state]
      (dom/div #js {:className "screen"}
        (case (:state world)
          :starting (om/build start-view    world {:init-state state})
          :playing  (om/build play-view     world  {:init-state state})
          :gameover (om/build gameover-view world {:init-state state}))))))

(om/root
  screen-view
  world
  {:target (. js/document (getElementById "app"))})
