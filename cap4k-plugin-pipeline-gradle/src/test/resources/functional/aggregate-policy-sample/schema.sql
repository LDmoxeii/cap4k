create table if not exists video_post (
  id bigint primary key,
  title varchar(128) not null
);

create table if not exists audit_log (
  tenant_id bigint not null,
  event_id varchar(64) not null,
  payload varchar(255),
  constraint pk_audit_log primary key (tenant_id, event_id)
);
