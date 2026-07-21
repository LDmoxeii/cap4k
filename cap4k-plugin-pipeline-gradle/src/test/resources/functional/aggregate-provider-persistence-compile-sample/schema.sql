create table video_post (
    id bigint primary key comment '@IdStrategy=db_identity;',
    version bigint not null comment '@Managed=version;',
    deleted bigint not null default 0 comment '@Managed=deleted;',
    title varchar(128) not null
);


create table audit_log (
    id bigint primary key comment '@IdStrategy=db_identity;',
    deleted bigint not null default 0 comment '@Managed=deleted;',
    content varchar(128) not null
);
