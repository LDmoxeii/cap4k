create table if not exists `order` (
    id bigint primary key comment '@IdStrategy=db_identity;',
    reason varchar(255) not null
);
