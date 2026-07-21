create table if not exists video_post (
    id bigint primary key,
    status int not null comment 'shared status @Type=Status;',
    visibility int not null comment '@Type=VideoPostVisibility;',
    title varchar(255) not null
);
