create table video_post (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    version bigint not null comment '@Version=true;',
    deleted int not null,
    title varchar(128) not null
);
comment on table video_post is '@AggregateRoot=true;@DynamicInsert=true;@DynamicUpdate=true;@SoftDeleteColumn=deleted;';

create table audit_log (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    deleted int not null,
    content varchar(128) not null
);
comment on table audit_log is '@AggregateRoot=true;@SoftDeleteColumn=deleted;';
