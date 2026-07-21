create table video_post (
    id bigint primary key comment '@IdStrategy=db_identity;',
    version bigint not null comment '@Managed=version;',
    deleted int not null comment '@Managed=deleted;',
    title varchar(128) not null
);


create table audit_log (
    id bigint primary key comment '@IdStrategy=db_identity;',
    deleted int not null comment '@Managed=deleted;',
    content varchar(128) not null
);
