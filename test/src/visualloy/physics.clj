(ns visualloy.physics
  (:require [seesaw.color :refer [color]]
            [seesaw.graphics :refer [draw style]]
            [visualloy.alloy :refer [get-neighbors set-temperature]]
            [visualloy.graphics :refer [pixel]]
            [visualloy.util :refer [area dimensions midpoint
                                    safe-add safe-multiply]]))

(declare update-cell temp-from-neighbors)

(defn update-alloy
  "Updates the entire alloy to its next state, taking initial state from
  input and writing the next state to output"
  ([input output top-corner-temp bot-corner-temp
    thermal-constants transform threshold]
     (let [[height width] (dimensions input)
           top-corner-index [0 0]
           bot-corner-index [(dec height) (dec width)]]
       (fn [canvas graphics]
         (apply draw graphics
;                (apply concat
                       (update-alloy input output
                                     0 height 0 width
                                     top-corner-index top-corner-temp
                                     bot-corner-index bot-corner-temp
                                     thermal-constants transform threshold)))))
  ([input output first-row last-row first-col last-col
    top-corner-index top-corner-temp bot-corner-index bot-corner-temp
    thermal-constants transform]
     (for [row (range first-row last-row)]
       (for [col (range first-col last-col)
             :let [index [row col]
                   temp (cond (= index top-corner-index) top-corner-temp
                              (= index bot-corner-index) bot-corner-temp
                              :else (update-cell input output row col
;                                                 top-corner-temp bot-corner-temp
                                                 thermal-constants))]]
  ;           :when (and (not= [row col] top-corner)
   ;                     (not= [row col] bot-corner))
         ; try improving this by passing in the top-left and bottom-right
         ; temperatures and then use a 3-way cond
         ;; (let [temp (if (and (not= [row col] top-corner-index)
         ;;                     (not= [row col] bot-corner-index))
         ;;              (update-cell input output row col
         ;;                           top-corner-temp bot-corner-temp
         ;;                           thermal-constants)
         ;;              (:temp (aget input row col)))]
;           (println "r:" row "c:" col "t:" temp)
;           (System/exit 0)
           [(pixel row col)
            (style :foreground (apply color (transform temp)))])))
  ([input output first-row last-row first-col last-col
    top-corner-index top-corner-temp bot-corner-index bot-corner-temp
    thermal-constants transform threshold]
     (if (<= (area first-row last-row first-col last-col) threshold)
       (update-alloy input output first-row last-row first-col last-col
                     top-corner-index top-corner-temp
                     bot-corner-index bot-corner-temp
                     thermal-constants transform)
       ; here's the problem, you are subdividing into 4 sections, yet you are
       ; only using 2 of those sections (2nd and 4th quadrants). Fix this!!!
       (let [mid-row (midpoint first-row last-row)
             mid-col (midpoint first-col last-col)
             top-left
             (future (update-alloy input output
                                   first-row mid-row
                                   first-col mid-col
                                   top-corner-index top-corner-temp
                                   bot-corner-index bot-corner-temp
                                   thermal-constants transform threshold))
             top-right
             (future (update-alloy input output
                                   first-row mid-row
                                   mid-col last-col
                                   top-corner-index top-corner-temp
                                   bot-corner-index bot-corner-temp
                                   thermal-constants transform threshold))
             bot-left
             (future (update-alloy input output
                                   mid-row last-row
                                   first-col mid-col
                                   top-corner-index top-corner-temp
                                   bot-corner-index bot-corner-temp
                                   thermal-constants transform threshold))
             bot-right
             (update-alloy input output
                           mid-row last-row
                           mid-col last-col
                           top-corner-index top-corner-temp
                           bot-corner-index bot-corner-temp
                           thermal-constants transform threshold)]
;         (println (str "top:\n" (vec @top) "\nbot:\n" (vec @bot)))
;         (let [n (fn [coll typ] (count (filter #(isa? (type %) typ) coll)))
;               coll (flatten (concat @top @bot))
;               [dubs style] (map type (take 2 coll))]
;           (println (str "dubs: " (n coll dubs)
;                         "style: " (n coll style))))
;         (println (flatten (concat @top @bot)))
;         (System/exit 0)))))
         (flatten (concat @top-left @top-right @bot-left bot-right))))))

(defn update-cell
  "Updates the temperature of the cell at the given index in the array.
  All writing is done on output, and reading is done from input."
  [input output row col thermal-constants]
  (let [neighbors (get-neighbors input row col)
        temp (temp-from-neighbors neighbors thermal-constants)]
    (set-temperature output row col temp)))

(defn temp-from-neighbors
  "Returns the temperature of a cell with the given neighbors.
  Maximum temperature is Long/MAX_VALUE, which is assured by dividing by
  the number of neighbors as early as possible, "
  [neighbors thermal-constants]
  (let [neighbor-divisor (/ (count neighbors))]
    (apply safe-add
           (for [m (range (count thermal-constants))
                 :let [C_m (nth thermal-constants m)]]
             (long
              (safe-multiply C_m
                             (apply safe-add (for [cell neighbors
                                            :let [T (:temp cell)
                                                  p_m (nth (:comp cell) m)]]
                                        (long
                                         (* T p_m neighbor-divisor))))))))))
