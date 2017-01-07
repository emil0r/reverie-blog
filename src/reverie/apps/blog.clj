(ns reverie.apps.blog
  (:require [clj-time.core :as t]
            [clojure.string :as str]
            [ez-database.core :as db]
            [ez-web.uri :refer [join-uri]]
            [ez-web.paginator :as paginator]
            [reverie.core :refer [defapp defrenderer]]
            [reverie.cache :as cache]
            [reverie.downstream :as downstream]
            [reverie.i18n :refer [t]]
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
  [:li [:a {:href (str (page/path page) "?category=" category)} category]])

(defn list-categories [page categories params]
  [:ul.categories
   [:li [:h4 (t :reverie.blog/categories)]]
   [:li (if-not (contains? params :category)
          {:class :active})
    [:a {:href (page/path page)} (t :reverie.blog/all-categories)]]
   (for [{:keys [name]} categories]
     [:li (if (= (:category params) name)
            {:class :active})
      [:a {:href (str (page/path page) "?category=" name)}
       name]])])

(defn list-latest [page latest]
  [:ul.latest
   [:li [:h4 (t :reverie.blog/latest)]]
   (map (fn [{:keys [title slug]}]
          [:li [:a {:href (join-uri (page/path page) slug)} title]])
        latest)])

(defn list-entry [page css {:keys [title slug created updated ingress category author]}]
  [:div.post.listing
   [:h2 [:a {:href (join-uri (page/path page) slug)} title]]
   [:div.header
    [:div.date (time/format created (t :reverie.blog/date-fmt))]
    [:div.author (t :reverie.blog/by-author) author]]
   [:div.body ingress]
   [:div.footer
    [:a {:class css
         :href (join-uri (page/path page) slug)} (t :reverie.blog/read-more)]]])

(defn list-entries [page css entries]
  (map #(list-entry page css %) entries))

(defn pagination [page {p :page pages :pages next :next prev :prev :as paginated}]
  [:ul.pagination
   [:li
    (if (nil? prev)
      (t :reverie.blog.pagination/previous)
      [:a {:href (join-uri (page/path page) (str prev))}
       (t :reverie.blog.pagination/previous)])]
   [:li (t :reverie.blog.pagination/x-of-n p pages)]
   [:li
    (if (nil? next)
      (t :reverie.blog.pagination/next)
      [:a {:href (join-uri (page/path page) (str next))}
       (t :reverie.blog.pagination/next)])]])

(defn view-entry [page {:keys [title slug post created category
                               og_description og_image og_title
                               author source categories]
                        :as post} comments]
  (downstream/assoc! :blog/title title)
  (downstream/assoc! :blog/slug slug)
  (downstream/assoc! :blog.og/title og_title)
  (downstream/assoc! :blog.og/description og_description)
  (downstream/assoc! :blog.og/image og_image)
  [:div.post
   (if-not (str/blank? og_image)
     [:img {:src og_image
            :alt title
            :class (get-in @blogger [:css :image])}])

   [:h1 title]

   [:div.header
    [:div.date (time/format created (t :reverie.blog/date-fmt))]
    [:div.author (t :reverie.blog/by-author) [:span (->> [source author] (remove str/blank?) first)]]
    [:ul.categories
     [:li.categories (t :reverie.blog/categories)]
     (map (partial category-link page) categories)]]

   [:div.body post]

   [:div.comments comments]])

(defn present-index [{:keys [page latest categories params entries category css paginated]}]
  {:latest (list-latest page latest)
   :categories (list-categories page categories params)
   :entries (if (str/blank? category)
              (list-entries page css entries)
              (list-entries page css entries))
   :pagination (pagination page paginated)})

(defn index [request page properties {:keys [offset category] :as params}]
  (let [db (get-in request [:reverie :database])
        pp 2
        db-offset (* (- (or offset 1) 1) pp)
        categories (db/query db sql-list-categories)
        num-pages (->> sql-count-entries
                       (db/query db)
                       first :count)
        paginated (paginator/paginate num-pages pp offset)
        latest (db/query db sql-list-latest {:limit 5})
        css (get-in @blogger [:css :read-more] :read-more)]
    {:page page
     :latest latest
     :categories categories
     :params params
     :entries (if (str/blank? category)
                (db/query db sql-list-entries {:offset db-offset :limit pp})
                (db/query db sql-list-entries {:category category, :offset db-offset, :limit pp}))
     :category category
     :css css
     :paginated paginated}))

(defn present-post [{:keys [page params categories post comments latest]}]
  {:latest (list-latest page latest)
   :categories (list-categories page categories params)
   :entry (view-entry page post comments)})

(defn post [request page properties {:keys [slug] :as params}]
  (let [db (get-in request [:reverie :database])
        categories (db/query db sql-list-categories)
        post (->> {:slug slug}
                  (db/query db sql-get-entry)
                  first)
        comments (get-comments (get-in @blogger [:commentator]) page db post)
        latest (db/query db sql-list-latest {:limit 5})]
    {:page page
     :params params
     :categories categories
     :post post
     :comments comments
     :latest latest}))

(defrenderer ::renderer {:render-fn :hiccup} {::slug {:any present-post}
                                              ::offset {:any present-index}
                                              ::index {:any present-index}})
(defapp reverie-blog
  {:i18n "i18n/blog/tconfig.edn"
   :renderer ::renderer}

  [["/"        ^:meta {:name ::index}  {:any index}                                    ]
   ["/:offset" ^:meta {:name ::offset} {:offset #"\d+$"} {:offset Integer} {:any index}]
   ["/:slug"   ^:meta {:name ::slug}   {:any post}                                     ]])
