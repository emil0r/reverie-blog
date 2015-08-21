--name: sql-list-categories

SELECT
        *
FROM
        blog_category
ORDER BY
      name;


--name: sql-list-entries

SELECT
        p.*, u.full_name AS author, c.name AS category
FROM
        blog_post p
        INNER JOIN blog_category c ON p.category_id = c.id
        INNER JOIN auth_user u ON p.author_id = u.id
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

SELECT
        p.*, u.full_name AS author, c.name AS category
FROM
        blog_post p
        INNER JOIN blog_category c ON p.category_id = c.id
        INNER JOIN auth_user u ON p.author_id = u.id
WHERE
        p.slug = :slug;
