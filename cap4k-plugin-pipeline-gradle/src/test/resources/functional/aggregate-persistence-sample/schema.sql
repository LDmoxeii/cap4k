create table video_post (
    id bigint primary key comment '@GeneratedValue=IDENTITY;',
    version bigint not null comment '@Version;',
    created_by varchar(64) comment '@Insertable=false;',
    updated_by varchar(64) comment '@Updatable=false;',
    title varchar(128) not null
);

comment on table video_post is '@AggregateRoot=true;';
