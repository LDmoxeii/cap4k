create table if not exists video_post (
    id bigint primary key,
    slug varchar(128) not null unique,
    title varchar(255) not null,
    published boolean default false
);
