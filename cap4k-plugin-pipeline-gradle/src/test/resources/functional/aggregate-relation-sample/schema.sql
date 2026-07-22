create table video_post (
    id bigint primary key,
    author_id bigint not null comment '@RefAggregate=UserProfile;',
    cover_profile_id bigint null comment '@RefAggregate=UserProfile;',
    title varchar(255) not null
);

create table video_post_item (
    id bigint primary key,
    video_post_id bigint not null comment '@ParentRef;',
    label varchar(128) not null
);

create table video_post_file (
    id bigint primary key,
    video_post_id bigint not null comment '@ParentRef;',
    storage_key varchar(128) not null,
    constraint uk_video_post_file_parent unique (video_post_id)
);

create table user_profile (
    id bigint primary key,
    nickname varchar(128) not null
);

comment on table video_post_item is '@Parent=video_post;';
comment on table video_post_file is '@Parent=video_post;';
