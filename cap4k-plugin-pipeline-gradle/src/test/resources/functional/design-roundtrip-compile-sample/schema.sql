create table if not exists `order` (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    note varchar(255) not null
);
