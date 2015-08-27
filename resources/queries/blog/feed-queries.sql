--name: sql-feed-entries

WITH categories AS (
     SELECT id, name
     FROM blog_category
)
SELECT
        p.*, u.full_name AS author, u.email AS author_email, array_agg(categories.name) AS categories
FROM
        blog_post p
        INNER JOIN blog_post_categories c ON p.id = c.blog_id
        INNER JOIN categories ON c.category_id = categories.id
        INNER JOIN auth_user u ON p.author_id = u.id
GROUP BY
      p.id, u.full_name, u.email
ORDER BY
      p.created;
