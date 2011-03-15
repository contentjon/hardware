(ns com.contentjon.core)

(def implementations
 { :Linux 'com.contentjon.udev })

(defn require-os-implementation []
  (let [os (System/getProperty "os.name")]
    (if-let [implementation (implementations (keyword os))]
      (require implementation)
      (throw
       (Exception.
        (str "No implementation for operating system: " os))))))
