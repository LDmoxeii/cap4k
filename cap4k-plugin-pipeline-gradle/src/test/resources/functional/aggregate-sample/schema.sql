create table if not exists video_post (
    id varchar(36) primary key comment '@IdStrategy=uuid7;',
    slug varchar(128) not null unique,
    title varchar(255) not null,
    published boolean default false
);
