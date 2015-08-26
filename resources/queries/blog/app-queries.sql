--name: sql-list-categories

SELECT
        *
FROM
        blog_category
ORDER BY
      name;


--name: sql-list-entries

WITH categories AS (
     SELECT id, name
     FROM blog_category
)
SELECT
        p.*, u.full_name AS author, array_agg(categories.name) AS categories
FROM
        blog_post p
        INNER JOIN blog_post_categories c ON p.id = c.blog_id
        INNER JOIN categories ON c.category_id = categories.id
        INNER JOIN auth_user u ON p.author_id = u.id
GROUP BY
      p.id, u.full_name
ORDER BY
      p.created
OFFSET
       :offset
LIMIT
      :limit;

--name: sql-list-entries-by-category

WITH categories AS (
     SELECT id, name
     FROM blog_category
)
SELECT
        p.*, u.full_name AS author, array_agg(categories.name) AS categories
FROM
        blog_post p
        INNER JOIN blog_post_categories c ON p.id = c.blog_id
        INNER JOIN categories ON c.category_id = categories.id
        INNER JOIN auth_user u ON p.author_id = u.id
WHERE
        categories.name = :category
GROUP BY
      p.id, u.full_name
ORDER BY
      p.created
OFFSET
       :offset
LIMIT
      :limit;

--name: sql-count-entries

SELECT
        COUNT(*)
FROM
        blog_post;

--name: sql-list-latest
SELECT
        *
FROM
        blog_post
ORDER BY
      created DESC
LIMIT
        :limit;


--name: sql-get-entry

WITH categories AS (
     SELECT id, name
     FROM blog_category
)
SELECT
        p.*, u.full_name AS author, array_agg(categories.name) AS categories
FROM
        blog_post p
        INNER JOIN blog_post_categories c ON p.id = c.blog_id
        INNER JOIN categories ON c.category_id = categories.id
        INNER JOIN auth_user u ON p.author_id = u.id
WHERE
        p.slug = :slug
GROUP BY
      p.id, u.full_name;
