(ns com.contentjon.hardware.protocols)

(defprotocol Hardware
  "Implementations of this prototocol support querying the
   system for hardware."
  (devices [_ query-map] "Query the system for hardware using a query map"))
