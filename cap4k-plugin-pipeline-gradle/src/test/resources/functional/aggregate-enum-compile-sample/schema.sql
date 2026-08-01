create table if not exists video_post (
    id varchar(36) primary key comment '@Managed=identifier.uuid7;',
    status int not null comment 'shared status @Type=Status;',
    visibility int not null comment '@Type=VideoPostVisibility;',
    title varchar(255) not null
);
