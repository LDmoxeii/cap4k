create table video_post (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    version bigint not null comment '@Managed=version;',
    created_by varchar(64) comment '@Managed=enrichment.audit-actor.created-by;',
    updated_by varchar(64) comment '@Managed=enrichment.audit-actor.updated-by;',
    title varchar(128) not null
);
