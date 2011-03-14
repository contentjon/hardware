(ns com.contentjon.udev
  (:import [com.contentjon UDev]
           [com.sun.jna Native Pointer]))

(def library (com.sun.jna.Native/loadLibrary "udev" UDev))

(defprotocol ReferenceCounted
  (add-ref [_])
  (rem-ref [_]))

(defmacro defreftype
  ([name]      `(defreftype ~name "" []))
  ([name type] `(defreftype ~name ~type [~'udev-ref]))
  ([name type members]
     (let [ref-symbol   (symbol (str ".udev_" type "_ref"))
           unref-symbol (symbol (str ".udev_" type "_unref"))]
       `(defrecord ~name ~(vec (concat ['native] members))
          Object
          (finalize [~'this] (rem-ref ~'this))
          ReferenceCounted
          (add-ref [_]
                   (~ref-symbol library ~'native))
          (rem-ref [_]
                   (~unref-symbol library ~'native))))))

(defreftype UDevRef)
(defreftype EnumerateRef "enumerate")
(defreftype DeviceRef "device")

(defn udev-context []
  (UDevRef. (.udev_new library)))

(defn enum [udev]
  (EnumerateRef. (.udev_enumerate_new library (:native udev)) udev))

(defn device [udev path]
  (DeviceRef. (.udev_device_new_from_syspath library (:native udev) path) udev))

(defn- udev-seq [entry ref]
  (lazy-seq
    (when-not (= Pointer/NULL entry)
      (let [name  (.udev_list_entry_get_name  library entry)
            value (.udev_list_entry_get_value library entry)
            next  (.udev_list_entry_get_next  library entry)]
        (cons [name value] (udev-seq next ref))))))

(defn- enum-seq [enumeration]
  (udev-seq
    (.udev_enumerate_get_list_entry library (:native enumeration))
    enumeration))

(defn scan [enumeration]
  (.udev_enumerate_scan_devices library (:native enumeration))
  (map first (enum-seq enumeration)))

(defn in-class [enumeration & classes]
  (doseq [clazz classes]
    (.udev_enumerate_add_match_subsystem library (:native enumeration) clazz))
  enumeration)

(defn has-attribute
  ([enumeration attribute]
     (has-attribute enumeration attribute Pointer/NULL))
  ([enumeration attribute value]
     (.udev_enumerate_add_match_sysattr library (:native enumeration) attribute value)
     enumeration))

(defmacro enumerate-devices [context & queries]
  `(-> (enum ~context)
       ~@queries
       (scan)))

(defn get-attribute [device attribute]
  (.udev_device_get_sysattr_value library (:native device) attribute))
