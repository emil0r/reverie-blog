(ns reverie.apps.blog
  (:require [clj-time.core :as t]
            [clojure.string :as str]
            [ez-database.core :as db]
            [ez-web.uri :refer [join-uri]]
            [ez-web.paginator :as paginator]
            [reverie.core :refer [defapp]]
            [reverie.cache :as cache]
            [reverie.downstream :as downstream]
            [reverie.page :as page]
            [reverie.time :as time]
            [yesql.core :refer [defqueries]]))


(defonce blogger (atom nil))

(defprotocol IReverieBlogComments
  (get-comments [commentator page db post]))

(extend-type nil
  IReverieBlogComments
  (get-comments [_ _ _ _] nil))

(defqueries "queries/blog/app-queries.sql")

(defn category-link [page category]
  [:li [:a {:href (str (page/path page) "?category=" category)}
        category]])

(defn list-categories [page db params]
  [:ul.categories
   [:li [:h4 "Categories"]]
   [:li (if-not (contains? params :category)
          {:class :active})
    [:a {:href (page/path page)} "all"]]
   (for [{:keys [name]} (db/query db sql-list-categories)]
     [:li (if (= (:category params) name)
            {:class :active})
      [:a {:href (str (page/path page) "?category=" name)}
       name]])])

(defn list-latest [page db]
  (let [entries (db/query db sql-list-latest {:limit 5})]
    [:ul.latest
     [:li [:h4 "Latest"]]
     (map (fn [{:keys [title slug]}]
            [:li [:a {:href (join-uri (page/path page) slug)} title]])
          entries)]))


(defn list-entry [page {:keys [title slug created updated ingress category author]}]
  [:div.post.listing
   [:h2 [:a {:href (join-uri (page/path page) slug)} title]]
   [:div.header
    [:div.date (time/format created "dd MMM, YYYY")]
    [:div.author "by " author]]
   [:div.body ingress]
   [:div.footer
    [:a.btn.btn-primary.read-more {:href (join-uri (page/path page) slug)} "Read more"]]])

(defn list-entries
  ([page db offset limit]
     (map (partial list-entry page)
          (db/query db sql-list-entries {:offset offset :limit limit})))
  ([page db category offset limit]
     (map (partial list-entry page)
          (db/query db sql-list-entries {:category category
                                         :offset offset
                                         :limit limit}))))

(defn pagination
  ([rev-page db pp]
     (pagination rev-page db pp 1))
  ([rev-page db pp offset]
     (let [num-pages (->> sql-count-entries
                          (db/query db)
                          first :count)
           {:keys [page pages next prev]} (paginator/paginate num-pages pp offset)]
       [:ul.pagination
        [:li
         (if (nil? prev)
           "previous"
           [:a {:href (join-uri (page/path rev-page) (str prev))}
            "previous"])]
        [:li (format "%d of %d" page pages)]
        [:li
         (if (nil? next)
           "next"
           [:a {:href (join-uri (page/path rev-page) (str next))}
            "next"])]])))

(defn view-entry [page db {:keys [title slug post created category author
                                  og_description og_image og_title
                                  categories]
                           :as post}]
  (downstream/assoc! :blog/title title)
  (downstream/assoc! :blog/slug slug)
  (downstream/assoc! :blog.og/title og_title)
  (downstream/assoc! :blog.og/description og_description)
  (downstream/assoc! :blog.og/image og_image)
  [:div.post
   (if-not (str/blank? og_image)
     [:img {:src og_image
            :alt title
            :class (str "og-image " (get-in @blogger [:image :class]))}])

   [:h1 title]

   [:div.header
    [:div.date (time/format created "dd MMM, YYYY")]
    [:div.author "by " [:span author]]
    [:ul.categories (map (partial category-link page) categories)]]

   [:div.body post]

   [:div.comments
    (get-comments (get-in @blogger [:commentator]) page db post)]])

(defn index [request page properties {:keys [offset category] :as params}]
  (let [db (get-in request [:reverie :database])
        pp 20
        db-offset (* (- (or offset 1) 1) pp)]
    {:latest (list-latest page db)
     :categories (list-categories page db params)
     :entries (if (str/blank? category)
                (list-entries page db db-offset pp)
                (list-entries page db category db-offset pp))
     :pagination (pagination page db pp offset)}))

(defn post [request page properties {:keys [slug] :as params}]
  (let [db (get-in request [:reverie :database])]
    {:latest (list-latest page db)
     :categories (list-categories page db params)
     :entry (view-entry page db (->> {:slug slug}
                                     (db/query db sql-get-entry)
                                     first))}))

(defapp reverie-blog
  {}
  [["/" {:any index}]
   ["/:offset" {:offset #"\d+$"} {:offset Integer} {:any index}]
   ["/:slug" {:any post}]])
