create table if not exists video_post (
    id varchar(36) primary key comment '@IdStrategy=uuid7;',
    slug varchar(128) not null unique,
    title varchar(255) not null,
    published boolean default false
);

create table if not exists content (
    id varchar(36) primary key comment '@IdStrategy=uuid7;',
    author_id varchar(36) not null,
    media_processing_task_id varchar(36),
    title varchar(255) not null
);

create table if not exists media_processing_task (
    id varchar(36) primary key comment '@IdStrategy=uuid7;',
    status varchar(64) not null,
    result_snapshot varchar(2048) comment '@Type=MediaProcessingResultSnapshot;'
);

comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
