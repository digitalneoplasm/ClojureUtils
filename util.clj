(ns util
  (:import [java.util.concurrent Executors]))

(defn str->int
  "Attempts to convert str to an Integer. 
   If it fails, just returns str."
  [str]
  (try 
    (Integer/parseInt str)
    (catch Exception e str)))

;;; File processing utils
(defn basename
  "Return basename (no extensions) of file"
  [file]
  (let [f (if (instance? java.io.File file) (.getName file) (.getName (java.io.File. file)))]
    (subs f 0 (.lastIndexOf f (int \.)))))

(defn files-of-type
  "Return all files in directory dir with file extension ext"
  [ext dir]
  (let [filefilter (proxy [java.io.FilenameFilter] []
                     (accept [dir name]
                       (= ext (subs name (- count name) (count ext)))))]
    (apply sorted-set (.listFiles (java.io.File. dir) filefilter))))

(defn pmap-pool [f coll]
  "Like pmap, but instead of waiting until an entire set of tasks
   has completed, use a pool of threads which add a new task whenever
   one completes."
  (let [queue (ref coll)  ;; shared queue of work units
        nthreads  (+ 2 (.availableProcessors (Runtime/getRuntime)))
        pool  (Executors/newFixedThreadPool nthreads)
        tasks (map (fn [_] 
                     (fn [] ; one task per thread
                       (let [local-res (atom [])] ;; collect results per thread to minimize synchronization
                         (while (seq @queue)
                           ;; queue may be emptied between 'while'
                           ;; and 'dosync'.
                           (when-let [wu (dosync
                                                ;; grab work unit, update queue
                                                (when-let [w (first @queue)]
                                                  (alter queue next)
                                                  w))]
                             (swap! local-res conj (f wu))))
                         local-res)))
                   (range nthreads))
        results (doall (map #(deref (.get %)) ;; blocks until completion
                            (.invokeAll pool tasks))) ;; start all tasks
        results (reduce concat results)]
    (.shutdown pool)
    results))

;; Adapted from: http://stackoverflow.com/questions/3387155/difference-between-two-maps
(defn map-difference [m1 m2]
  "Returns a map which contains the k/v pairs in either m1 or m2
   but not in the other map."
  (loop [m (transient {})
         ks (concat (keys m1) (keys m2))]
    (if-let [k (first ks)]
      (let [e1 (find m1 k)
            e2 (find m2 k)]
        (cond (and e1 e2 (not= (e1 1) (e2 1))) (recur (assoc! m k (e1 1)) (next ks))
              (not e1) (recur (assoc! m k (e2 1)) (next ks))
              (not e2) (recur (assoc! m k (e1 1)) (next ks))
              :else    (recur m (next ks))))
      (persistent! m))))
