(ns reverie.endpoints.blog-feed
  (:require [clojure.data.xml :as data.xml]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-uuid :as uuid]
            [ez-database.core :as db]
            [reverie.core :refer [defpage]]
            [reverie.time :as time]
            [yesql.core :refer [defqueries]]))



(defqueries "queries/blog/feed-queries.sql")

(defonce feed-content (atom {:title "reverie blog / Title"
                             :subtitle "reverie blog / subtitle"
                             :id-key nil
                             :url "fill-me-in"
                             :tagging-entity "reveriecms.org,2015-09-12"
                             :blog-url "fill-me-in"
                             :rights ""
                             :generator "reverie/blog"}))

(defn get-id-tag [id]
  (format "tag:%s:%s"
          (get-in @feed-content [:tagging-entity])
          (uuid/v5 (get-in @feed-content [:id-key]) id)))

(defn get-entry [blog-url {:keys [id title slug og_title og_description created updated author author_email]}]
  [:entry
   [:link {:href (str blog-url slug)
           :type "text/html"
           :rel "alternate"}]
   [:title (first (remove str/blank? [title og_title]))]
   [:id (get-id-tag id)]
   [:updated (f/unparse (f/formatters :date-time) updated)]
   [:published (f/unparse (f/formatters :date-time) created)]
   [:author
    [:name author]
    [:email author_email]]
   [:content og_description]])

(defn feed [request page params]
  (let [db (get-in request [:reverie :database])
        entries (db/query db sql-feed-entries)
        {:keys [title subtitle rights generator url blog-url id-key]} @feed-content
        updated (if-let [updated (->> entries first :updated)]
                  (f/unparse (f/formatters :date-time) updated))]
   (data.xml/emit-str
    (data.xml/sexp-as-element
     [:feed {:xmlns "http://www.w3.org/2005/Atom"}
      [:title {:type "text"} title]
      [:subtitle {:type "text"} subtitle]
      [:id (get-id-tag id-key)]
      (if updated
        [:updated updated])
      [:rights rights]
      [:link {:href (str url "/atom.feed")}]
      [:link {:rel "alternate"
              :type "text/html"
              :href blog-url}]
      [:generator {:uri "https://github.com/emil0r/reverie-blog"
                   :version "1.0"}
       generator]
      (map (partial get-entry blog-url) entries)]))))

(defpage "/feed.atom"
  {:headers {"Content-Type" "application/atom+xml; charset=utf-8;"}}
  [["/" {:any feed}]])
