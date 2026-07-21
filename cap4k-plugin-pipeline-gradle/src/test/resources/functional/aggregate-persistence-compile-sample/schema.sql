create table video_post (
    id bigint primary key comment '@IdStrategy=db_identity;',
    version bigint not null comment '@Managed=version;',
    created_by varchar(64) comment '@Managed=system;@Inherited;',
    updated_by varchar(64) comment '@Managed=system;@Inherited;',
    title varchar(128) not null
);
