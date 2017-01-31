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

(defn- get-url [url add]
  (let [a? (str/ends-with? url "/")
        b? (str/starts-with? add "/")]
   (cond (and a? b?)
         (str url (str/replace add #"^/" ""))

         (and (not a?)
              (not b?))
         (str url "/" add)

         :else
         (str url add))))

(defn get-id-tag [id]
  (format "tag:%s:%s"
          (get-in @feed-content [:tagging-entity])
          (uuid/v5 (get-in @feed-content [:id-key]) id)))

(defn get-entry [url blog-url {:keys [id title slug og_title og_description og_image created updated author author_email source]}]
  [:entry
   [:link {:href (str blog-url slug)
           :type "text/html"
           :rel "alternate"}]
   [:title (first (remove str/blank? [title og_title]))]
   [:id (get-id-tag id)]
   [:updated (f/unparse (f/formatters :date-time) updated)]
   [:published (f/unparse (f/formatters :date-time) created)]
   (if-not (str/blank? og_image)
     [:media:thumbnail {:xmlns:media "http://search.yahoo.com/mrss/"
                        :url (get-url url og_image)}])
   (if (str/blank? source)
     [:author
      [:name author]
      [:email author_email]]
     [:author
      [:name source]])
   [:content og_description]])

(defn- strip-uri [blog-url]
  ;; split by the uri and just give back the first part of the url
  ;; ie, the domain + the protocol
  (-> (str/split blog-url #"(/[\w]+?/?){0,}$")
      first))

(defn feed [request page params]
  (let [db (get-in request [:reverie :database])
        entries (db/query db sql-feed-entries)
        {:keys [title subtitle rights generator url blog-url id-key]} @feed-content
        updated (if-let [updated (->> entries first :updated)]
                  (f/unparse (f/formatters :date-time) updated))
        url (strip-uri blog-url)]
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
      (map #(get-entry url blog-url %) entries)]))))

(defpage "/feed.atom"
  {:headers {"Content-Type" "application/atom+xml; charset=utf-8;"}}
  [["/" {:any feed}]])
