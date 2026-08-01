create table video_post (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    version bigint not null comment '@Managed=version;',
    author_id varchar(36) not null comment '@RefAggregate=UserProfile;',
    cover_profile_id varchar(36) null comment '@RefAggregate=UserProfile;',
    title varchar(255) not null
);

create table video_post_item (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    version bigint not null comment '@Managed=version;',
    video_post_id bigint not null comment '@ParentRef;',
    label varchar(128) not null
);

create table video_post_file (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    version bigint not null comment '@Managed=version;',
    video_post_id bigint not null comment '@ParentRef;',
    storage_key varchar(128) not null,
    constraint uk_video_post_file_parent unique (video_post_id)
);

create table video_post_file_variant (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    version bigint not null comment '@Managed=version;',
    video_post_file_id bigint not null comment '@ParentRef;',
    variant_key varchar(128) not null
);

create table user_profile (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    nickname varchar(128) not null
);

create table content (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    author_id varchar(36) not null,
    media_processing_task_id varchar(36),
    title varchar(255) not null
);

create table media_processing_task (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    status varchar(64) not null
);

comment on table video_post_item is '@Parent=video_post;';
comment on table video_post_file is '@Parent=video_post;';
comment on table video_post_file_variant is '@Parent=video_post_file;';
comment on column content.author_id is '@RefId=AuthorId;';
comment on column content.media_processing_task_id is '@RefAggregate=MediaProcessingTask;';
