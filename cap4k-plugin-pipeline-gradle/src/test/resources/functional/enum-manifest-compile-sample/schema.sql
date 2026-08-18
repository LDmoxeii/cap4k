create table if not exists video_post (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    status int not null comment 'manifest-backed status @Type=Status;',
    title varchar(255) not null
);
