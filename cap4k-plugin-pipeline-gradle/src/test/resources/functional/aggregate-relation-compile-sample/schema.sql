create table video_post (
    id bigint primary key,
    author_id bigint not null comment '@Reference=user_profile;@Relation=ManyToOne;@Lazy=true;',
    cover_profile_id bigint null comment '@Reference=user_profile;@Relation=OneToOne;',
    title varchar(255) not null
);

create table video_post_item (
    id bigint primary key,
    video_post_id bigint not null comment '@Reference=video_post;',
    label varchar(128) not null
);

create table user_profile (
    id bigint primary key,
    nickname varchar(128) not null
);

comment on table video_post is '@AggregateRoot=true;';
comment on table video_post_item is '@Parent=video_post;@VO;';
comment on table user_profile is '@AggregateRoot=true;';
