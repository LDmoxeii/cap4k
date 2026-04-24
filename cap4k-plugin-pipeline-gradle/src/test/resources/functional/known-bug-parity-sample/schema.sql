create table if not exists user_message (
    id bigint primary key,
    message_key varchar(128) not null unique,
    room_id varchar(64) not null,
    content varchar(255) not null,
    published boolean default false
);
