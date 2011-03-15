(ns com.contentjon.hardware)

(def implementations
 { :Linux 'com.contentjon.hardware.udev })

(defn require-os-implementation []
  (let [os (System/getProperty "os.name")]
    (if-let [implementation (implementations (keyword os))]
      (require implementation)
      (throw
       (Exception.
        (str "No implementation for operating system: " os))))))
