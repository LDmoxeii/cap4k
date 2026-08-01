create table if not exists `order` (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    reason varchar(255) not null
);
