create table soft_delete_evidence (
    bigint_default bigint not null default 0,
    varchar_default varchar(36) not null default '00000000-0000-0000-0000-000000000000',
    uuid_default uuid not null default '00000000-0000-0000-0000-000000000000'
);

create table "MixedCaseEvidence" (
    "MixedCaseDefault" bigint not null default 0
);
