create table if not exists video_post (
    id bigint primary key,
    status int not null comment 'shared status @T=Status;',
    visibility int not null comment '@T=VideoPostVisibility;@E=0:HIDDEN:Hidden|1:PUBLIC:Public;',
    title varchar(255) not null
);
