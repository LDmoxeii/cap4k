create table if not exists video_post (
    id varchar(36) primary key,
    slug varchar(128) not null unique,
    title varchar(255) not null,
    published boolean default false
);

create table if not exists content (
    id varchar(36) primary key,
    author_id varchar(36) not null,
    media_processing_task_id varchar(36),
    title varchar(255) not null
);

create table if not exists media_processing_task (
    id varchar(36) primary key,
    status varchar(64) not null,
    result_snapshot varchar(2048) comment '@T=MediaProcessingResultSnapshot;'
);

comment on table video_post is '@AggregateRoot=true;';
comment on column video_post.id is '@Id;';
comment on table content is '@AggregateRoot=true;';
comment on column content.id is '@Id;';
comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
comment on table media_processing_task is '@AggregateRoot=true;';
comment on column media_processing_task.id is '@Id;';
