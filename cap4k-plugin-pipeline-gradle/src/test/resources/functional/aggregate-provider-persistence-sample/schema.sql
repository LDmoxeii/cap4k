create table video_post (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    version bigint not null comment '@Managed=version;',
    deleted bigint not null default 0 comment '@Managed=soft-delete;',
    db_updated_at timestamp default current_timestamp comment '@Managed=database.generated-always;',
    title varchar(128) not null
);

create table uuid_string_record (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    deleted varchar(36) not null default '00000000-0000-0000-0000-000000000000' comment '@Managed=soft-delete;',
    title varchar(128) not null
);

create table uuid_native_record (
    id uuid primary key comment '@Managed=identifier.uuid7;',
    deleted uuid not null default '00000000-0000-0000-0000-000000000000' comment '@Managed=soft-delete;',
    title varchar(128) not null
);
