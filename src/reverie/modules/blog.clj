(ns reverie.modules.blog
  (:require [clojure.edn :as edn]
            [ez-database.core :as db]
            [ez-database.query :refer [optional clean swap]]
            [honeysql.core :as sql]
            [honeysql.helpers :as sql.helpers]
            [reverie.auth :as auth]
            [reverie.core :refer [defmodule]]
            [reverie.modules.default :refer [get-order-query]]
            [yesql.core :refer [defqueries]]
            [vlad.core :as vlad]))

(defqueries "queries/blog/module-queries.sql")

(defn published?-fn
  "Is the post published?"
  [module entity id]
  (->> {:select [:%count.*]
        :from [:blog_post]
        :where [:= :id id]}
       (db/query (:database module))
       first :count zero? false?))

(defn- shift-version
  "Shift public version into the history tables"
  [module entity id]
  (let [db (:database module)
        history-id (->> (db/query db
                                  sql-blog-add-post-history<!
                                  {:id id})
                        :id)]
    (db/query db
              sql-blog-add-post-categories-history!
              {:history_id history-id
               :id id})))

(defn publish-fn
  "Publish draft to post while keeping history"
  [module entity id]
  (let [db (:database module)
        published? (published?-fn module entity id)]
    (if published?
      ;; if the entity has been published -> version the entity
      (do (shift-version module entity id)
          (db/query db sql-blog-update-post! {:id id}))
      ;; if not, add it
      (db/query db sql-blog-add-post! {:id id}))
    ;; delete old categories
    (db/query! db {:delete-from :blog_post_categories
                   :where [:= :blog_id id]})
    ;; add new
    (db/query db sql-blog-add-post-categories! {:id id})))

(defn unpublish-fn
  "Shift version and then delete post"
  [module entity id]
  (let [db (:database module)]
    (shift-version module entity id)
    (db/query! db {:delete-from :blog_post_categories
                   :where [:= :blog_id id]})
    (db/query! db {:delete-from :blog_post
                   :where [:= :id id]})))

(defn delete-fn
  "Delete public post and history"
  [module entity id]
  (let [db (:database module)
        history-ids (->> {:select [:id]
                          :from [:blog_post_history]
                          :where [:= :draft_id id]}
                         (db/query db)
                         (map :id))]
    (when-not (empty? history-ids)
      (db/query! db {:delete-from :blog_post_categories_history
                     :where [:in :history_id history-ids]}))
    (db/query! db {:delete-from :blog_post_categories
                   :where [:= :blog_id id]})
    (db/query! db {:delete-from :blog_post
                   :where [:= :id id]})
    (db/query! db {:delete-from :blog_post_history
                   :where [:= :draft_id id]})))

(defn get-authors [{db :database}]
  (->> (db/query db {:select [:u.id :u.full_name]
                     :modifiers [:distinct]
                     :from [[:auth_user :u]]
                     :join [[:auth_user_role :ur] [:= :ur.user_id :u.id]
                            [:auth_role :r] [:= :ur.role_id :r.id]]
                     :where [:in :r.name ["admin" "staff"]]
                     :order-by [:full_name]})
       (map (fn [{:keys [id full_name]}]
              [full_name id]))))

(defn query-post [{:keys [database database-name limit offset interface] :as params}]
  (let [[title author source] (->> [:title :author :source]
                                   (map params)
                                   (map edn/read-string))
        order (get-order-query interface params)]
    (->> (-> {:select [:d.* [:a.full_name :author]]
              :from [[:blog_draft :d]]
              :join [[:auth_user :a] [:= :d.author_id :a.id]]
              :offset offset
              :limit limit
              :order-by [order]}
             (swap (or title author source)
                   (sql.helpers/where [:and
                                       (optional title (sql/raw (format "d.title ilike '%%%s%%'" title)))
                                       (optional author [:= :d.author_id author])
                                       (optional source (sql/raw (format "d.source ilike '%%%s%%'" source)))]))
             (clean))
         (db/query database database-name))))

(def filter-post [{:name :title
                   :type :text}
                  {:name :author
                   :type :dropdown
                   :options (fn [data]
                              (let [authors (get-authors data)]
                                (into [["" ""]]
                                      (map (fn [[full-name id]] [id full-name]) authors))))}
                  {:name :source
                   :type :text}])

(defmodule reverie-blog
  {:name "Blog"
   :interface? true
   :migration {:path "resources/migrations/modules/blog/"
               :automatic? true
               :table "migrations_module_reverie_blog"}
   :roles #{:author}
   :actions #{:edit}
   :required-roles {:edit #{:admin :author}}
   :template :admin/main
   :entities
   {:category {:name "Category"
               :table :blog_category
               :interface {:display {:name {:name "Name"
                                            :link? true
                                            :sort :n
                                            :sort-name :id}}
                           :default-order :id}
               :fields {:name {:name "Name"
                               :type :text
                               :validation (vlad/attr [:name] (vlad/present))}}
               :sections [{:fields [:name]}]}
    :post {:name "Blog post"
           :table :blog_draft
           :interface {:display {:title {:name "Title"
                                         :link? true
                                         :sort :t
                                         :sort-name :id}
                                 :author {:name "Author"
                                          :sort :a}
                                 :source {:name "Source"
                                          :sort :s}}
                       :default-order [:id :desc]
                       :filter filter-post
                       :query query-post}
           :publishing {:publish? true
                        :publish-fn publish-fn
                        :unpublish-fn unpublish-fn
                        :delete-fn delete-fn
                        :published?-fn published?-fn}
           :fields {:title {:name "Title"
                            :type :text
                            :initial ""
                            :validation (vlad/attr [:title] (vlad/present))}
                    :slug {:name "Slug"
                           :type :slug
                           :initial ""
                           :for :title
                           :validation (vlad/attr [:slug] (vlad/present))}
                    :ingress {:name "Ingress"
                              :type :richtext
                              :initial ""
                              :inline? true
                              :validation (vlad/attr [:ingress] (vlad/present))
                              :help "Ingress is what will show up in the blog listings and is supposed to be a a short summary of the entire blog post"}
                    :post {:name "Post"
                           :type :richtext
                           :initial ""
                           :inline? true
                           :validation (vlad/attr [:post] (vlad/present))
                           :help "Post is the main body of the blog post. This is where the entire post goes"}
                    :discussion_p {:name "Discussion?"
                                   :type :boolean
                                   :initial true
                                   :help "Allow discussion field?"}
                    :og_title {:name "Open Graph title"
                               :type :text
                               :initial ""
                               :help "Leave blank for title to be used"}
                    :og_image {:name "Open Graph image"
                               :type :image
                               :initial ""
                               :help "Minimum size is 600x315 px. Recommended size is 1200x630 px. Aspect ratio should be 1.91:1"}
                    :og_description {:name "Open Graph description"
                                     :type :textarea
                                     :initial ""
                                     :help "One or two sentences describing the post."}
                    :categories {:name "Categories"
                                 :type :m2m
                                 :cast :int
                                 :table :blog_category
                                 :options [:id :name]
                                 :order :name
                                 :m2m {:table :blog_draft_categories
                                       :joining [:draft_id :category_id]}}
                    :author_id {:name "Author"
                                :type :dropdown
                                :cast :int
                                :options get-authors
                                :validation (vlad/attr [:author_id] (vlad/present))}
                    :source {:name "Source"
                             :type :text
                             :initial ""
                             :help "Use this to override the author"}}
           :sections [{:fields [:title :slug :ingress :post :categories :author_id :source]}
                      {:name "Meta" :fields [:discussion_p :og_title :og_image :og_description]}]}}})
