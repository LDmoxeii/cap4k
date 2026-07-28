create table video_post (
    id bigint primary key comment '@IdStrategy=db_identity;',
    version bigint not null comment '@Managed=version;',
    deleted bigint not null default 0 comment '@Managed=deleted;',
    title varchar(128) not null
);

create table snowflake_long_record (
    id bigint primary key comment '@IdStrategy=snowflake;',
    deleted bigint not null default 0 comment '@Managed=deleted;',
    title varchar(128) not null
);

create table snowflake_string_record (
    id varchar(19) primary key comment '@IdStrategy=snowflake;',
    deleted varchar(19) not null default '0' comment '@Managed=deleted;',
    title varchar(128) not null
);

create table uuid_string_record (
    id varchar(36) primary key comment '@IdStrategy=uuid7;',
    deleted varchar(36) not null default '00000000-0000-0000-0000-000000000000' comment '@Managed=deleted;',
    title varchar(128) not null
);

create table uuid_native_record (
    id uuid primary key comment '@IdStrategy=uuid7;',
    deleted uuid not null default '00000000-0000-0000-0000-000000000000' comment '@Managed=deleted;',
    title varchar(128) not null
);
