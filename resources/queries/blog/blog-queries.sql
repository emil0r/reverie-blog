--name: sql-blog-add-post!
INSERT INTO blog_post
       SELECT
                *, now() AS created, now() AS updated
       FROM
                blog_draft
       WHERE
                id = :id;

--name: sql-blog-update-post!
UPDATE blog_post SET
       og_image = draft.og_image,
       og_description = draft.og_description,
       og_title = draft.og_title,
       title = draft.title,
       slug = draft.slug,
       ingress = draft.ingress,
       post = draft.post,
       discussion_p = draft.discussion_p,
       author_id = draft.author_id,
       updated = now()
FROM
       (SELECT
                *
       FROM
                blog_draft
       WHERE
                id = :id) draft
WHERE
        blog_post.id = :id;

--name: sql-blog-add-post-categories!
INSERT INTO blog_post_categories (category_id, blog_id)
       SELECT category_id, :id AS blog_id
       FROM blog_draft_categories
       WHERE draft_id = :id;


--name: sql-blog-add-post-history<!
INSERT INTO blog_post_history
            (draft_id, og_image, og_title, og_description,
             title, slug, ingress, post,
             discussion_p, author_id,
             created, updated)
       SELECT
                id AS draft_id,
                og_image, og_title, og_description,
                title, slug, ingress, post,
                discussion_p, author_id,
                created, updated
       FROM
                blog_post
       WHERE
                id = :id;

--name: sql-blog-add-post-categories-history!
INSERT INTO blog_post_history_categories (category_id, history_id)
       SELECT category_id, :history_id AS history_id
       FROM blog_post_categories
       WHERE blog_id = :id;
