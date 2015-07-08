(ns memento.misc.convert)

(defn str->sql
  "Loads a file, and returns a collection of SQL lines necessary to insert
  them into the database"
  [filename]
  (let [contents (slurp filename)
        lines    (clojure.string/split-lines contents)]
    (println lines)
    (->> lines
         (map read-string)
         (map #(format "INSERT INTO thoughts (created, username, thought) values ('%s', '%s', '%s');"
                       (:date %)
                       (:username %)
                       (clojure.string/replace (:thought %) "'" "''")
                       ))
         )
    ))