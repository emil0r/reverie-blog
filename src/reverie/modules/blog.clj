(ns reverie.modules.blog
  (:require [ez-database.core :as db]
            [reverie.auth :as auth]
            [reverie.core :refer [defmodule]]
            [yesql.core :refer [defqueries]]
            vlad))

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

(defn get-authors [{db :database initial :initial}]
  (->> (db/query db {:select [:id :full_name]
                     :from [:auth_user]
                     :order-by [:full_name]})
       (map (fn [{:keys [id full_name]}]
              [full_name id]))))

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
               :order :id
               :table :blog_category
               :display [:name]
               :fields {:name {:name "Name"
                               :type :text
                               :validation (vlad/present [:name])}}
               :sections [{:fields [:name]}]}
    :post {:name "Blog post"
           :order :id
           :table :blog_draft
           :publishing {:publish? true
                        :publish-fn publish-fn
                        :unpublish-fn unpublish-fn
                        :delete-fn delete-fn
                        :published?-fn published?-fn}
           :display [:title]
           :fields {:title {:name "Title"
                            :type :text
                            :validation (vlad/present [:title])}
                    :slug {:name "Slug"
                           :type :slug
                           :for :title
                           :validation (vlad/present [:slug])}
                    :ingress {:name "Ingress"
                              :type :richtext
                              :inline? true
                              :validation (vlad/present [:ingress])
                              :help "Ingress is what will show up in the blog listings and is supposed to be a a short summary of the entire blog post"}
                    :post {:name "Post"
                           :type :richtext
                           :inline? true
                           :validation (vlad/present [:post])
                           :help "Post is the main body of the blog post. This is where the entire post goes"}
                    :discussion_p {:name "Discussion?"
                                   :type :boolean
                                   :initial true
                                   :help "Allow discussion field?"}
                    :og_title {:name "Open Graph title"
                               :type :text
                               :help "Leave blank for title to be used"}
                    :og_image {:name "Open Graph image"
                               :type :image
                               :help "Minimum size is 600x315 px. Recommended size is 1200x630 px. Aspect ratio should be 1.91:1"}
                    :og_description {:name "Open Graph description"
                                     :type :textarea
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
                                :validation (vlad/present [:author_id])}}
           :sections [{:fields [:title :slug :ingress :post :categories :author_id]}
                      {:name "Meta" :fields [:discussion_p :og_title :og_image :og_description]}]}}})
