(ns reverie.blog.core
  (:require [com.stuartsierra.component :as component]))

(defrecord BlogInitializer [database]
  component/Lifecycle
  (start [this]
    this)
  (stop [this]
    this))
