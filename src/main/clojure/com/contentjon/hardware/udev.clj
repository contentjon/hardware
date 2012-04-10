(ns com.contentjon.hardware.udev
  (:require [com.contentjon.hardware.protocols :as protocols])
  (:use    [com.contentjon.hardware.udev.mappings])
  (:import [com.contentjon.hardware UDev]
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

(defn create-context-impl
  "Create a new udev context"
  []
  (UDevRef. (.udev_new library)))

(defn enum
  "Create a udev device enumeration object"
  [udev]
  (EnumerateRef. (.udev_enumerate_new library (:native udev)) udev))

(defn device
  "Create a new udev device from a context and a device path"
  [udev path]
  (DeviceRef. (.udev_device_new_from_syspath library (:native udev) path) udev))

(defn- udev-seq
  "Returns a lazy sequence for a udev list. The sequence includes
   a closure over the object that owns the udev list, so that it doesn't
   get collected before the final reference to the lazy seq has disappeared"
  [entry ref]
  (lazy-seq
    (when-not (= Pointer/NULL entry)
      (let [name  (.udev_list_entry_get_name  library entry)
            value (.udev_list_entry_get_value library entry)
            next  (.udev_list_entry_get_next  library entry)]
        (cons [name value] (udev-seq next ref))))))

(defn- enum-seq
  "Returns a lazy seq for an udev device enumeration"
  [enumeration]
  (udev-seq
    (.udev_enumerate_get_list_entry library (:native enumeration))
    enumeration))

(defn scan
  "Called with an enumeration object to scan the system for devices.
   Returns a seq of strings, which represent the sysfs device paths
   of the found devices"
  [enumeration]
  (.udev_enumerate_scan_devices library (:native enumeration))
  (map first (enum-seq enumeration)))

(defn in-class
  "Takes an enumeration and a number of device classes. The classes are
   added to the enumeration. Returns the modified enumeration"
  [enumeration & classes]
  (doseq [clazz classes]
    (.udev_enumerate_add_match_subsystem library (:native enumeration) clazz))
  enumeration)

(defn has-name
  "Takes an enumeration and a device name. The name is added to the enumeration.
   Returns the modified enumeration"
  [enumeration devname]
  (.udev_enumerate_add_match_sysname library (:native enumeration) devname)
  enumeration)

(defn has-attribute
  "Takes an enumration, an attribute name and an optional value. A query for
   the attribute (and psossibly its value) is added to the enumeration.
   Returns the modified enumeration"
  ([enumeration attribute]
     (has-attribute enumeration attribute Pointer/NULL))
  ([enumeration attribute value]
     (if-not (nil? value)
       (do
         (.udev_enumerate_add_match_sysattr library (:native enumeration) attribute value)
         enumeration)
       (has-attribute enumeration attribute))))

(defn has-property
  "Takes an enumration, an attribute name and a value. A query for
   the property value is added to the enumeration. Returns the
   modified enumeration"
  [enumeration property value]
  (if-not (nil? value)
    (do
      (.udev_enumerate_add_match_property library (:native enumeration) property value)
      enumeration)
  (throw
      (java.lang.NullPointerException. "Property value can not be nil"))))

(defmacro enumerate-devices
  "Takes a udev context and a number of query expressions. The expressions are
   threaded in such a way that a new enumeration object is inserted into the
   queries. As a final operation a scan for devices is added."
  [context & queries]
  `(-> (enum ~context)
       ~@queries
       (scan)))

(defn device-name
  "Get the system name of the device"
  [device]
  (.udev_device_get_sysname library (:native device)))

(defn- is-virtual?
  "Returns if it is a virtual device, i.e. the sysfs-path starts with
   /sys/devices/virtual, for example bridges, vlan-devices or loopback devices"
  [device]
  (let [sys-path (.udev_device_get_syspath library (:native device))]
    (.startsWith sys-path "/sys/devices/virtual/")))

(defn attribute
  "Returns a specific attribute of a device as a string"
  [device attribute]
  (.udev_device_get_sysattr_value library (:native device) attribute))

(defn property
  "Returns a specific property of a device as a string"
  [device property]
  (.udev_device_get_property_value library (:native device) property))

(defn properties
  "Returns a seq of all device properties"
  [device]
  (udev-seq
    (.udev_device_get_properties_list_entry library (:native device))
    device))

(defn classify-attribute
  "Return a type that classifies the attribute"
  [attribute]
  (when-not (or (nil? attribute) (empty? attribute))
    (if (every? #(Character/isUpperCase %)
                (filter #(Character/isLetter %) attribute))
    ::property
    ::attribute)))

(defmulti build-query
  "Adds a new query element to an enumeration"
  (fn [e [k v]] k))

(defmulti add-attribute
  "Adds an attribute query to an enumeration"
  (fn [e a v] (classify-attribute a)))

(defmulti make-attribute
  "Queries an attribute from a device"
  (fn [d [a v]] (classify-attribute v)))

(def class-property "SUBSYSTEM")

(defn device->map [device]
  (let [clazz      (property device class-property)
        attributes (class->attributes clazz)] 
    (into { :name (device-name device)
            :virtual (is-virtual? device)
            }
          (map #(make-attribute device %) attributes))))

(def make-device-map (comp device->map #(device %1 %2)))

(extend com.contentjon.hardware.udev.UDevRef
  protocols/Hardware
  { :devices (fn [this query]
               (let [enumeration (reduce build-query (enum this) query)]
                 (map #(make-device-map this %) (scan enumeration))))})

(defn add-class [enumeration value lookup]
  (if-let [clazz (lookup value)]
    (in-class enumeration clazz)
    (throw 
      (java.lang.IllegalArgumentException. 
        (str "Class " value " is not supported on udev")))))

(defmethod build-query :class
  [enumeration [_ value]]
  (add-class enumeration value class->udev))

(defmethod build-query :bus
  [enumeration [_ value]]
  (add-attribute enumeration bus-property (bus->udev value)))

(defmethod build-query :name
  [enumeration [_ value]]
  (has-name enumeration value))

(def add-query-map-attribute (comp add-attribute attribute->udev))

(defmethod build-query :attributes
  [enumeration [_ attributes]]
  (map (fn [[k v]] add-attribute enumeration (attribute->udev k) v) 
       attributes))

(defmethod add-attribute ::attribute [enumeration attribute value]
  (has-attribute enumeration attribute value))

(defmethod add-attribute ::property [enumeration attribute value]
  (has-property enumeration attribute value))

(defmethod make-attribute ::attribute [device [k v]]
  (vector k (attribute device v)))

(defmethod make-attribute ::property [device [k v]]
  (vector k (property device v)))
