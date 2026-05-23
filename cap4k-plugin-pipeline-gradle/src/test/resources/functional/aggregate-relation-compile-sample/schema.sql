create table video_post (
    id varchar(36) primary key,
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
    id varchar(36) primary key,
    nickname varchar(128) not null
);

create table content (
    id varchar(36) primary key,
    author_id varchar(36) not null,
    media_processing_task_id varchar(36),
    title varchar(255) not null
);

create table media_processing_task (
    id varchar(36) primary key,
    status varchar(64) not null
);

comment on table video_post is '@AggregateRoot=true;';
comment on column video_post.id is '@Id;';
comment on table video_post_item is '@Parent=video_post;@VO;';
comment on table user_profile is '@AggregateRoot=true;';
comment on column user_profile.id is '@Id;';
comment on table content is '@AggregateRoot=true;';
comment on column content.id is '@Id;';
comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
comment on table media_processing_task is '@AggregateRoot=true;';
comment on column media_processing_task.id is '@Id;';
