(ns com.contentjon.hardware.query)

(def classes #{:block :input :network :printer :memory})
(def busses  #{:ata :i2c :pci :scsi :usb})

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
