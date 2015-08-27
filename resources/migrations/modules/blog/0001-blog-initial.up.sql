CREATE TABLE blog_settings (
       k text not null default '',
       v text not null default ''
);

CREATE TABLE blog_category (
       id serial primary key not null,
       name text not null default ''
);

CREATE TABLE blog_draft (
       id serial primary key not null,
       og_image text not null default '',
       og_title text not null default '',
       og_description text not null default '',
       title text not null default '',
       slug text not null default '',
       ingress text not null default '',
       post text not null default '',
       discussion_p boolean not null default true,
       author_id integer not null references auth_user(id)
);

CREATE TABLE blog_post (
       id integer primary key not null,
       og_image text not null default '',
       og_title text not null default '',
       og_description text not null default '',
       title text not null default '',
       slug text not null default '',
       ingress text not null default '',
       post text not null default '',
       discussion_p boolean not null default true,
       author_id integer not null references auth_user(id),
       created timestamp with time zone not null default now(),
       updated timestamp with time zone null
);

CREATE TABLE blog_post_history (
       id serial primary key not null,
       draft_id integer not null references blog_draft(id),
       og_image text not null default '',
       og_title text not null default '',
       og_description text not null default '',
       title text not null default '',
       slug text not null default '',
       ingress text not null default '',
       post text not null default '',
       discussion_p boolean not null default true,
       author_id integer not null references auth_user(id),
       created timestamp with time zone not null default now(),
       updated timestamp with time zone null,
       archived timestamp with time zone not null default now()
);

CREATE TABLE blog_draft_categories (
       draft_id integer not null references blog_draft(id),
       category_id integer not null references blog_category(id)
);

CREATE TABLE blog_post_categories (
       blog_id integer not null references blog_post(id),
       category_id integer not null references blog_category(id)
);

CREATE TABLE blog_post_categories_history (
       history_id integer not null references blog_post_history(id),
       category_id integer not null references blog_category(id)
);

ALTER TABLE blog_settings ADD CONSTRAINT blog_settings_unique_k UNIQUE(k);
ALTER TABLE blog_category ADD CONSTRAINT blog_category_unique_name UNIQUE(name);
CREATE INDEX blog_settings_index_k ON blog_settings (k);
