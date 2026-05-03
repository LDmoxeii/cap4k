create table video_post (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    version bigint not null comment '@Version;',
    deleted int not null comment '@Deleted;',
    title varchar(128) not null
);
comment on table video_post is '@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;';

create table audit_log (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    deleted int not null comment '@Deleted;',
    content varchar(128) not null
);
comment on table audit_log is '@AggregateRoot=true;';
