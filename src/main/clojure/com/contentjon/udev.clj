(ns com.contentjon.udev
  (:import [com.contentjon UDev]
           [com.sun.jna Native Pointer]))

(def library (com.sun.jna.Native/loadLibrary "udev" UDev))

(Native/setProtected true)

(defmulti add-ref (fn [key obj] key))
(defmulti unref   (fn [key obj] key))

(defmethod add-ref :udev [key udev]
  (.udev_ref library udev))

(defmethod add-ref :enum [key enumeration]
  (.udev_enumerate_ref library enumeration))

(defmethod add-ref :device [key device]
  (.udev_device_ref library device))

(defmethod unref :udev [key udev]
  (.udev_unref library udev))

(defmethod unref :enum [key enumeration]
  (.udev_enumerate_unref library enumeration))

(defmethod unref :device [key device]
  (.udev_device_unref library device))

(defn garbage-collected-ref [key obj]
  (add-ref key obj)
  (reify Object
    (finalize [_] (unref key obj))))

(defmacro with-ref [key bind & forms]
  `(let ~bind
     (try 
       ~@forms
       (finally
         (unref ~key ~(bind 0))))))

(defn- udev-seq [entry collector]
  (lazy-seq
    (when-not (= Pointer/NULL entry)
      (let [name  (.udev_list_entry_get_name  library entry) 
            value (.udev_list_entry_get_value library entry)
            next  (.udev_list_entry_get_next  library entry)] 
        (cons [name value] (udev-seq next collector))))))

(defn- enum-seq [enumeration]
  (udev-seq
    (.udev_enumerate_get_list_entry library enumeration)
    (garbage-collected-ref :enum enumeration)))

(defn scan [enumeration]
  (.udev_enumerate_scan_devices library enumeration)
  (map first (enum-seq enumeration)))

(defn in-class [enumeration & classes]
  (doseq [clazz classes]
    (.udev_enumerate_add_match_subsystem library enumeration clazz))
  enumeration)
