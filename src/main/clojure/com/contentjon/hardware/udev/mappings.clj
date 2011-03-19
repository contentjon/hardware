(ns com.contentjon.hardware.udev.mappings)

(def class->udev 
  { :block    "block"
    :input    "input"
    :network  "net"
    :printer  "printer"
    :memory   "mem" })

(def bus-property "ID_BUS")

(def bus->udev
  { :i2c  "i2c"
    :pci  "pci"
    :scsi "scsi"
    :usb  "usb" })

(def general-properties
  { :type   "ID_TYPE"
    :vendor "ID_VENDOR"
    :model  "ID_MODEL"})

(def block-attributes
  { :size          "size" 
    :removable     "removable"
    :fs            "ID_FS_TYPE"
    :has-smart     "ID_ATA_FEATURE_SET_SMART"
    :smart-enabled "ID_ATA_FEATURE_SET_SMART_ENABLED" })

(def network-attributes
  { :address "address"
    :speed   "speed"
    :duplex  "duplex"
    :mtu     "mtu" })

(def attribute->udev
  (merge
    general-properties
    block-attributes
    network-attributes))

(def class->attributes
  { (class->udev :network) network-attributes
    (class->udev :block)   block-attributes })
