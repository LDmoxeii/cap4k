create table if not exists `order` (
    id bigint primary key comment '@Managed=identifier.database-identity;',
    note varchar(255)
);
