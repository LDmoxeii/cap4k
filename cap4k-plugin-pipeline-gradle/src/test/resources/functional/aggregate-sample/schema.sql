create table if not exists video_post (
    id bigint primary key,
    slug varchar(128) not null,
    deleted bigint not null default 0,
    version bigint not null comment '@Version=true;',
    title varchar(255) not null,
    published boolean default false,
    constraint video_post_uk_v_slug unique (slug, deleted, version)
);

comment on table video_post is '@AggregateRoot=true;@SoftDeleteColumn=deleted;';
