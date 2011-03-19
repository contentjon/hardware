(ns com.contentjon.hardware)

(def os-property "os.name")

(def implementations
 { :Linux 'com.contentjon.hardware.udev })

(let [os (System/getProperty os-property)]
  (if-let [implementation (implementations (keyword os))]
    (require [implementation :as 'impl])
    (throw
      (Exception.
        (str "No implementation for operating system: " os)))))

(def classes #{:block :input :network :printer :memory})
(def busses  #{:ata :i2c :pci :scsi :usb})

(def create-context impl/create-context)

(defn query []
  {})

(defn in-class [query clazz]
  (if (classes clazz)
    (assoc query :class clazz)
    (throw (java.lang.IllegalArgumentException.
             "Provided unknown device class"))))

(defn on-bus [query bus]
  (if (busses bus)
    (assoc query :bus bus)
    (throw (java.lang.IllegalArgumentException.
             "Provided unknown bus type"))))

(defn has-name [query name]
  (assoc query :name name))

(defn has-attribute [query attribute value]
  (update-in query [:attributes] assoc attribute value))
