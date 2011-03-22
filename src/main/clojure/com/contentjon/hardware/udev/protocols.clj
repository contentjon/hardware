(ns com.contentjon.hardware.udev.protocols
  (:use     [com.contentjon.hardware.udev.mappings])
  (:require [com.contentjon.hardware.udev      :as udev]
            [com.contentjon.hardware.protocols :as protocols]))

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
  (let [clazz      (udev/property device class-property)
        attributes (class->attributes clazz)] 
    (into { :name (udev/device-name device) }
          (map #(make-attribute device %) attributes))))

(def make-device-map (comp device->map #(udev/device %1 %2)))

(extend com.contentjon.hardware.udev.UDevRef
  protocols/Hardware
  { :devices (fn [this query]
               (let [enumeration (reduce build-query (udev/enum this) query)]
                 (map #(make-device-map this %) (udev/scan enumeration))))})

(defn add-class [enumeration value lookup]
  (if-let [clazz (lookup value)]
    (udev/in-class enumeration clazz)
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
  (udev/has-name enumeration value))

(def add-query-map-attribute (comp add-attribute attribute->udev))

(defmethod build-query :attributes
  [enumeration [_ attributes]]
  (map (fn [[k v]] add-attribute enumeration (attribute->udev k) v) 
       attributes))

(defmethod add-attribute ::attribute [enumeration attribute value]
  (udev/has-attribute enumeration attribute value))

(defmethod add-attribute ::property [enumeration attribute value]
  (udev/has-property enumeration attribute value))

(defmethod make-attribute ::attribute [device [k v]]
  (vector k (udev/attribute device v)))

(defmethod make-attribute ::property [device [k v]]
  (vector k (udev/property device v)))
