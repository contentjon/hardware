(ns com.contentjon.hardware
  (:require [com.contentjon.hardware.protocols :as protocols]))

(def os-property "os.name")

(def implementations
  { :Linux ['com.contentjon.hardware.udev] })

(let [os (System/getProperty os-property)]
  (if-let [implementation (implementations (keyword os))]
    (doseq [namespace implementation]
      (use namespace))
    (throw
      (Exception.
        (str "No implementation for operating system: " os)))))

(def create-context create-context-impl)
(def devices protocols/devices)
