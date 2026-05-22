-- Create syntax for TABLE '__saga'
CREATE TABLE `__saga`
    (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `saga_uuid`     varchar(64)  NOT NULL DEFAULT '' COMMENT 'SAGA uuid',
    `svc_name`      varchar(255) NOT NULL DEFAULT '' COMMENT '服务',
    `saga_type`     varchar(255) NOT NULL DEFAULT '' COMMENT 'SAGA类型',
    `param`         text COMMENT '参数',
    `param_type`    varchar(255) NOT NULL DEFAULT '' COMMENT '参数类型',
    `result`        text COMMENT '结果',
    `result_type`   varchar(255) NOT NULL DEFAULT '' COMMENT '结果类型',
    `exception`     text COMMENT '执行异常',
    `compensation_request_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿请求代码',
    `compensation_request_reason` text COMMENT '补偿请求原因',
    `compensation_requested_at` datetime DEFAULT NULL COMMENT '补偿请求时间',
    `compensation_requested_by` varchar(32) NOT NULL DEFAULT '' COMMENT '补偿请求来源',
    `compensation_source_process_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿触发流程代码',
    `expire_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '过期时间',
    `create_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `saga_state`    int          NOT NULL DEFAULT '0' COMMENT '执行状态@E=0:INIT:init|-1:EXECUTING_FORWARD:executing-forward|-2:CANCELLED:cancelled|-3:EXPIRED:expired|-4:EXHAUSTED:exhausted|-5:COMPENSATION_REQUESTED:compensation-requested|-6:COMPENSATING:compensating|-7:MANUAL_REPAIR_REQUIRED:manual-repair-required|-9:EXCEPTION:exception|1:EXECUTED:executed|2:COMPENSATED:compensated;@T=SagaState;',
    `last_try_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上次尝试时间',
    `next_try_time` datetime     NOT NULL DEFAULT '0001-01-01 00:00:00' COMMENT '下次尝试时间',
    `tried_times`   int(11) NOT NULL DEFAULT '0' COMMENT '已尝试次数',
    `try_times`     int(11) NOT NULL DEFAULT '0' COMMENT '尝试次数',
    `version`       int          NOT NULL DEFAULT '0',
    `db_created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `db_updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id` #, `db_created_at`),
    KEY             `idx_db_created_at` (`db_created_at`),
    KEY             `idx_db_updated_at` (`db_updated_at`),
    KEY             `idx_saga_uuid` (`saga_uuid`),
    KEY             `idx_saga_type` (`saga_type`,`svc_name`),
    KEY             `idx_create_at` (`create_at`),
    KEY             `idx_expire_at` (`expire_at`),
    KEY             `idx_next_try_time` (`next_try_time`)
    ) COMMENT='SAGA事务 support by cap4k\n@I;'
# partition by range(to_days(db_created_at))
# (partition p202201 values less than (to_days('2022-02-01')) ENGINE=InnoDB)
;

-- Create syntax for TABLE '__saga_process'
CREATE TABLE `__saga_process`
    (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `saga_id`       bigint       NOT NULL DEFAULT '0',
    `process_code`  varchar(255) NOT NULL DEFAULT '' COMMENT 'SAGA处理环节代码',
    `param`         text COMMENT '参数',
    `param_type`    varchar(255) NOT NULL DEFAULT '' COMMENT '参数类型',
    `result`        text COMMENT '结果',
    `result_type`   varchar(255) NOT NULL DEFAULT '' COMMENT '结果类型',
    `exception`     text COMMENT '执行异常',
    `executed_at`   datetime DEFAULT NULL COMMENT '前向流程成功时间',
    `compensation_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿流程代码',
    `compensation_param` text COMMENT '补偿参数',
    `compensation_param_type` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿参数类型',
    `compensation_result` text COMMENT '补偿结果',
    `compensation_result_type` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿结果类型',
    `compensation_exception` text COMMENT '补偿异常',
    `compensation_state` int NOT NULL DEFAULT '0' COMMENT '补偿状态@E=0:NONE:none|1:READY:ready|-1:COMPENSATING:compensating|-7:MANUAL_REPAIR_REQUIRED:manual-repair-required|-9:FAILED:failed|2:COMPENSATED:compensated;@T=SagaCompensationState;',
    `compensation_last_try_time` datetime DEFAULT NULL COMMENT '补偿上次尝试时间',
    `compensation_tried_times` int NOT NULL DEFAULT '0' COMMENT '补偿尝试次数',
    `compensated_at` datetime DEFAULT NULL COMMENT '补偿完成时间',
    `process_state` int          NOT NULL DEFAULT '0' COMMENT '执行状态@E=0:INIT:init|-1:EXECUTING:executing|-9:EXCEPTION:exception|1:EXECUTED:executed;@T=SagaProcessState;',
    `create_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_try_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上次尝试时间',
    `tried_times`   int          NOT NULL DEFAULT '0' COMMENT '尝试次数',
    `db_created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `db_updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id` #, `db_created_at`),
    KEY             `idx_db_created_at` (`db_created_at`),
    KEY             `idx_db_updated_at` (`db_updated_at`),
    KEY             `idx_saga_id` (`saga_id`)
    ) COMMENT='SAGA事务-子环节 support by cap4k\n@I;'
# partition by range(to_days(db_created_at))
# (partition p202201 values less than (to_days('2022-02-01')) ENGINE=InnoDB)
;

-- Create syntax for TABLE '__archived_saga'
CREATE TABLE `__archived_saga`
    (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `saga_uuid`     varchar(64)  NOT NULL DEFAULT '' COMMENT 'SAGA uuid',
    `svc_name`      varchar(255) NOT NULL DEFAULT '' COMMENT '服务',
    `saga_type`     varchar(255) NOT NULL DEFAULT '' COMMENT 'SAGA类型',
    `param`         text COMMENT '参数',
    `param_type`    varchar(255) NOT NULL DEFAULT '' COMMENT '参数类型',
    `result`        text COMMENT '结果',
    `result_type`   varchar(255) NOT NULL DEFAULT '' COMMENT '结果类型',
    `exception`     text COMMENT '执行异常',
    `compensation_request_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿请求代码',
    `compensation_request_reason` text COMMENT '补偿请求原因',
    `compensation_requested_at` datetime DEFAULT NULL COMMENT '补偿请求时间',
    `compensation_requested_by` varchar(32) NOT NULL DEFAULT '' COMMENT '补偿请求来源',
    `compensation_source_process_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿触发流程代码',
    `expire_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '过期时间',
    `create_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `saga_state`    int          NOT NULL DEFAULT '0' COMMENT '执行状态@E=0:INIT:init|-1:EXECUTING_FORWARD:executing-forward|-2:CANCELLED:cancelled|-3:EXPIRED:expired|-4:EXHAUSTED:exhausted|-5:COMPENSATION_REQUESTED:compensation-requested|-6:COMPENSATING:compensating|-7:MANUAL_REPAIR_REQUIRED:manual-repair-required|-9:EXCEPTION:exception|1:EXECUTED:executed|2:COMPENSATED:compensated;@T=SagaState;',
    `last_try_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上次尝试时间',
    `next_try_time` datetime     NOT NULL DEFAULT '0001-01-01 00:00:00' COMMENT '下次尝试时间',
    `tried_times`   int(11) NOT NULL DEFAULT '0' COMMENT '已尝试次数',
    `try_times`     int(11) NOT NULL DEFAULT '0' COMMENT '尝试次数',
    `version`       int          NOT NULL DEFAULT '0',
    `db_created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `db_updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id` #, `db_created_at`),
    KEY             `idx_db_created_at` (`db_created_at`),
    KEY             `idx_db_updated_at` (`db_updated_at`),
    KEY             `idx_saga_uuid` (`saga_uuid`),
    KEY             `idx_saga_type` (`saga_type`,`svc_name`),
    KEY             `idx_create_at` (`create_at`),
    KEY             `idx_expire_at` (`expire_at`),
    KEY             `idx_next_try_time` (`next_try_time`)
    ) COMMENT='SAGA事务(存档) support by cap4k\n@I;'
# partition by range(to_days(db_created_at))
# (partition p202201 values less than (to_days('2022-02-01')) ENGINE=InnoDB)
;

-- Create syntax for TABLE '__archived_saga_process'
CREATE TABLE `__archived_saga_process`
    (
    `id`            bigint       NOT NULL AUTO_INCREMENT,
    `saga_id`       bigint       NOT NULL DEFAULT '0',
    `process_code`  varchar(255) NOT NULL DEFAULT '' COMMENT 'SAGA处理环节代码',
    `param`         text COMMENT '参数',
    `param_type`    varchar(255) NOT NULL DEFAULT '' COMMENT '参数类型',
    `result`        text COMMENT '结果',
    `result_type`   varchar(255) NOT NULL DEFAULT '' COMMENT '结果类型',
    `exception`     text COMMENT '执行异常',
    `executed_at`   datetime DEFAULT NULL COMMENT '前向流程成功时间',
    `compensation_code` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿流程代码',
    `compensation_param` text COMMENT '补偿参数',
    `compensation_param_type` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿参数类型',
    `compensation_result` text COMMENT '补偿结果',
    `compensation_result_type` varchar(255) NOT NULL DEFAULT '' COMMENT '补偿结果类型',
    `compensation_exception` text COMMENT '补偿异常',
    `compensation_state` int NOT NULL DEFAULT '0' COMMENT '补偿状态@E=0:NONE:none|1:READY:ready|-1:COMPENSATING:compensating|-7:MANUAL_REPAIR_REQUIRED:manual-repair-required|-9:FAILED:failed|2:COMPENSATED:compensated;@T=SagaCompensationState;',
    `compensation_last_try_time` datetime DEFAULT NULL COMMENT '补偿上次尝试时间',
    `compensation_tried_times` int NOT NULL DEFAULT '0' COMMENT '补偿尝试次数',
    `compensated_at` datetime DEFAULT NULL COMMENT '补偿完成时间',
    `process_state` int          NOT NULL DEFAULT '0' COMMENT '执行状态@E=0:INIT:init|-1:EXECUTING:executing|-9:EXCEPTION:exception|1:EXECUTED:executed;@T=SagaProcessState;',
    `create_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_try_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上次尝试时间',
    `tried_times`   int          NOT NULL DEFAULT '0' COMMENT '尝试次数',
    `db_created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `db_updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id` #, `db_created_at`),
    KEY             `idx_db_created_at` (`db_created_at`),
    KEY             `idx_db_updated_at` (`db_updated_at`),
    KEY             `idx_saga_id` (`saga_id`)
    ) COMMENT='SAGA事务-子环节(存档) support by cap4k\n@I;'
# partition by range(to_days(db_created_at))
# (partition p202201 values less than (to_days('2022-02-01')) ENGINE=InnoDB)
;
