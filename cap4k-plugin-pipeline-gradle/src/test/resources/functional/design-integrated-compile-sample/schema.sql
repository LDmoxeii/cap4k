create table if not exists `order` (
    id bigint primary key comment '@IdStrategy=db_identity;',
    note varchar(255)
);
