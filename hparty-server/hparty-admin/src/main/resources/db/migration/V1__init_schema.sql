-- =====================================================================
--  V1 — 初始化数据库结构（49 张表）
--
--  由 sql/01-schema.sql 转换而来。二者内容一致，但本文件由 Flyway 管理，
--  **不要再手工编辑**；结构变更请新增 V3__xxx.sql、V4__xxx.sql。
--  sql/01-schema.sql 仅作为可读的参考副本保留。
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;




SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
--  一、系统域
-- =====================================================================

-- ---------------------------------------------------------------------
-- 党组织（树形：党委 → 党总支 → 党支部 → 党小组）
-- org_path 为物化路径，形如 /1/3/7/ ，用于数据权限前缀匹配
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept` (
    `org_id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '组织ID',
    `parent_id`       BIGINT       NOT NULL DEFAULT 0      COMMENT '父组织ID，0=根',
    `ancestors`       VARCHAR(500) NOT NULL DEFAULT ''     COMMENT '祖级列表，逗号分隔：0,1,3',
    `org_path`        VARCHAR(500) NOT NULL DEFAULT '/'    COMMENT '物化路径：/1/3/7/',
    `org_name`        VARCHAR(100) NOT NULL                COMMENT '组织名称',
    `org_short_name`  VARCHAR(50)  DEFAULT NULL            COMMENT '组织简称',
    `org_code`        VARCHAR(64)  DEFAULT NULL            COMMENT '组织编码',
    `org_type`        TINYINT      NOT NULL                COMMENT '组织类型：1=党委 2=党总支 3=党支部 4=党小组',
    `org_level`       TINYINT      NOT NULL DEFAULT 1      COMMENT '层级：1=党委 2=党总支 3=党支部 4=党小组',
    `secretary_id`    BIGINT       DEFAULT NULL            COMMENT '书记（party_person.person_id）',
    `deputy_id`       BIGINT       DEFAULT NULL            COMMENT '副书记',
    `org_committee_id` BIGINT      DEFAULT NULL            COMMENT '组织委员',
    `prop_committee_id` BIGINT     DEFAULT NULL            COMMENT '宣传委员',
    `disc_committee_id` BIGINT     DEFAULT NULL            COMMENT '纪检委员',
    `founded_date`    DATE         DEFAULT NULL            COMMENT '成立日期',
    `member_count`    INT          NOT NULL DEFAULT 0      COMMENT '党员数（冗余）',
    `order_num`       INT          NOT NULL DEFAULT 0      COMMENT '显示排序',
    `leader`          VARCHAR(50)  DEFAULT NULL            COMMENT '负责人姓名（冗余）',
    `phone`           VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
    `address`         VARCHAR(255) DEFAULT NULL            COMMENT '办公地址',
    `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=正常',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志：0=存在 1=删除',
    `create_by`       VARCHAR(64)  DEFAULT NULL            COMMENT '创建者',
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`       VARCHAR(64)  DEFAULT NULL            COMMENT '更新者',
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`org_id`),
    KEY `idx_dept_parent` (`parent_id`),
    KEY `idx_dept_path`   (`org_path`(191)),
    KEY `idx_dept_type`   (`org_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党组织表';

-- ---------------------------------------------------------------------
-- 账号
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `user_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`      VARCHAR(64)  NOT NULL                COMMENT '登录账号',
    `password`      VARCHAR(128) NOT NULL                COMMENT '密码（BCrypt）',
    `nick_name`     VARCHAR(64)  DEFAULT NULL            COMMENT '昵称',
    `person_id`     BIGINT       DEFAULT NULL            COMMENT '关联 party_person.person_id',
    `org_id`        BIGINT       DEFAULT NULL            COMMENT '所属党组织',
    `phone`         VARCHAR(20)  DEFAULT NULL            COMMENT '手机号',
    `email`         VARCHAR(100) DEFAULT NULL            COMMENT '邮箱',
    `avatar`        VARCHAR(500) DEFAULT NULL            COMMENT '头像URL',
    `sex`           TINYINT      NOT NULL DEFAULT 0      COMMENT '性别：0=未知 1=男 2=女',
    `status`        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=正常',
    `login_ip`      VARCHAR(64)  DEFAULT NULL            COMMENT '最后登录IP',
    `login_date`    DATETIME     DEFAULT NULL            COMMENT '最后登录时间',
    `pwd_update_date` DATETIME   DEFAULT NULL            COMMENT '密码最后更新时间',
    `remark`        VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志：0=存在 1=删除',
    `create_by`     VARCHAR(64)  DEFAULT NULL,
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`     VARCHAR(64)  DEFAULT NULL,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    -- 唯一约束只作用于「存活行」：逻辑删除后该生成列变 NULL，
    -- 墓碑行便不再占用唯一键，删除账号后可重新创建同名账号。
    -- MySQL 的唯一索引对多个 NULL 是放行的，所以墓碑可以无限多。
    `username_alive` VARCHAR(64) GENERATED ALWAYS AS (IF(`del_flag` = 0, `username`, NULL)) VIRTUAL
                     COMMENT '仅存活行的账号，用于唯一约束',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_user_username_alive` (`username_alive`),
    KEY `idx_user_org`    (`org_id`),
    KEY `idx_user_person` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ---------------------------------------------------------------------
-- 角色
-- data_scope：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `role_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name`   VARCHAR(64)  NOT NULL                COMMENT '角色名称',
    `role_key`    VARCHAR(64)  NOT NULL                COMMENT '角色权限字符串',
    `role_sort`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    `data_scope`  TINYINT      NOT NULL DEFAULT 3      COMMENT '数据范围：1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义',
    `is_builtin`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否内置：0=否 1=是（内置角色不可删除）',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=正常',
    `remark`      VARCHAR(500) DEFAULT NULL,
    `del_flag`    TINYINT      NOT NULL DEFAULT 0,
    `create_by`   VARCHAR(64)  DEFAULT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`   VARCHAR(64)  DEFAULT NULL,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `role_key_alive` VARCHAR(64) GENERATED ALWAYS AS (IF(`del_flag` = 0, `role_key`, NULL)) VIRTUAL
                     COMMENT '仅存活行的角色标识，用于唯一约束',
    PRIMARY KEY (`role_id`),
    UNIQUE KEY `uk_role_key_alive` (`role_key_alive`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-角色关联表';

DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `org_id`  BIGINT NOT NULL COMMENT '组织ID',
    PRIMARY KEY (`role_id`, `org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-组织关联表（数据范围=自定义时使用）';

-- ---------------------------------------------------------------------
-- 菜单 / 权限
-- menu_type：M=目录 C=菜单 F=按钮
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
    `menu_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单ID',
    `menu_name`   VARCHAR(64)  NOT NULL                COMMENT '菜单名称',
    `order_num`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    `path`        VARCHAR(200) DEFAULT NULL            COMMENT '路由地址',
    `component`   VARCHAR(255) DEFAULT NULL            COMMENT '组件路径',
    `query`       VARCHAR(255) DEFAULT NULL            COMMENT '路由参数',
    `is_frame`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否外链：0=否 1=是',
    `is_cache`    TINYINT      NOT NULL DEFAULT 1      COMMENT '是否缓存：0=否 1=是',
    `menu_type`   CHAR(1)      NOT NULL                COMMENT '菜单类型：M=目录 C=菜单 F=按钮',
    `visible`     TINYINT      NOT NULL DEFAULT 1      COMMENT '显示状态：0=隐藏 1=显示',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '菜单状态：0=停用 1=正常',
    `perms`       VARCHAR(128) DEFAULT NULL            COMMENT '权限标识：develop:applicant:list',
    `icon`        VARCHAR(100) DEFAULT NULL            COMMENT '菜单图标',
    `remark`      VARCHAR(500) DEFAULT NULL,
    `create_by`   VARCHAR(64)  DEFAULT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`   VARCHAR(64)  DEFAULT NULL,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`menu_id`),
    KEY `idx_menu_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `menu_id` BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-菜单关联表';

-- ---------------------------------------------------------------------
-- 字典
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type` (
    `dict_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典主键',
    `dict_name`   VARCHAR(100) NOT NULL                COMMENT '字典名称',
    `dict_type`   VARCHAR(100) NOT NULL                COMMENT '字典类型',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=正常',
    `remark`      VARCHAR(500) DEFAULT NULL,
    `create_by`   VARCHAR(64)  DEFAULT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`   VARCHAR(64)  DEFAULT NULL,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`dict_id`),
    UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data` (
    `dict_code`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '字典编码',
    `dict_sort`   INT          NOT NULL DEFAULT 0      COMMENT '字典排序',
    `dict_label`  VARCHAR(100) NOT NULL                COMMENT '字典标签',
    `dict_value`  VARCHAR(100) NOT NULL                COMMENT '字典键值',
    `dict_type`   VARCHAR(100) NOT NULL                COMMENT '字典类型',
    `css_class`   VARCHAR(100) DEFAULT NULL            COMMENT '样式属性',
    `list_class`  VARCHAR(100) DEFAULT NULL            COMMENT '表格回显样式',
    `is_default`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认：0=否 1=是',
    `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=正常',
    `remark`      VARCHAR(500) DEFAULT NULL,
    `create_by`   VARCHAR(64)  DEFAULT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`   VARCHAR(64)  DEFAULT NULL,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`dict_code`),
    -- 同一字典类型下键值唯一：Service 层同样有「先查后插」校验，并发下会漏，
    -- 缺了索引就会出现同键重复行，前端下拉框出现重复项。
    UNIQUE KEY `uk_dict_type_value` (`dict_type`, `dict_value`),
    KEY `idx_dict_data_type` (`dict_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据表';

-- ---------------------------------------------------------------------
-- 日志
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log` (
    `info_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '访问ID',
    `username`       VARCHAR(64)  DEFAULT NULL            COMMENT '用户账号',
    `ipaddr`         VARCHAR(64)  DEFAULT NULL            COMMENT '登录IP',
    `login_location` VARCHAR(255) DEFAULT NULL            COMMENT '登录地点',
    `browser`        VARCHAR(64)  DEFAULT NULL            COMMENT '浏览器',
    `os`             VARCHAR(64)  DEFAULT NULL            COMMENT '操作系统',
    `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=失败 1=成功',
    `msg`            VARCHAR(255) DEFAULT NULL            COMMENT '提示消息',
    `login_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (`info_id`),
    KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志表';

DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log` (
    `oper_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志主键',
    `title`          VARCHAR(50)  DEFAULT NULL            COMMENT '模块标题',
    `business_type`  TINYINT      NOT NULL DEFAULT 0      COMMENT '业务类型：0=其它 1=新增 2=修改 3=删除 4=审批 5=导出 6=上传',
    `method`         VARCHAR(200) DEFAULT NULL            COMMENT '方法名称',
    `request_method` VARCHAR(10)  DEFAULT NULL            COMMENT '请求方式',
    `oper_name`      VARCHAR(64)  DEFAULT NULL            COMMENT '操作人员',
    `oper_url`       VARCHAR(255) DEFAULT NULL            COMMENT '请求URL',
    `oper_ip`        VARCHAR(64)  DEFAULT NULL            COMMENT '主机地址',
    `oper_param`     TEXT                                 COMMENT '请求参数',
    `json_result`    TEXT                                 COMMENT '返回参数',
    `status`         TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=异常 1=正常',
    `error_msg`      TEXT                                 COMMENT '错误消息',
    `cost_time`      BIGINT       NOT NULL DEFAULT 0      COMMENT '消耗时间(毫秒)',
    `oper_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (`oper_id`),
    KEY `idx_oper_time` (`oper_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ---------------------------------------------------------------------
-- 文件（本地磁盘存储，库中存相对路径）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `sys_file`;
CREATE TABLE `sys_file` (
    `file_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文件ID',
    `file_name`     VARCHAR(255) NOT NULL                COMMENT '原始文件名',
    `file_path`     VARCHAR(500) NOT NULL                COMMENT '存储相对路径',
    `file_url`      VARCHAR(500) DEFAULT NULL            COMMENT '访问URL',
    `file_suffix`   VARCHAR(20)  DEFAULT NULL            COMMENT '文件后缀',
    `file_size`     BIGINT       NOT NULL DEFAULT 0      COMMENT '文件大小(字节)',
    `content_type`  VARCHAR(128) DEFAULT NULL            COMMENT 'MIME类型',
    `storage_type`  VARCHAR(20)  NOT NULL DEFAULT 'local' COMMENT '存储类型：local/minio/oss',
    `biz_type`      VARCHAR(50)  DEFAULT NULL            COMMENT '业务类型：dev_material/meeting/avatar',
    `biz_id`        BIGINT       DEFAULT NULL            COMMENT '业务ID',
    `org_id`        BIGINT       DEFAULT NULL            COMMENT '所属组织',
    `upload_by`     BIGINT       DEFAULT NULL            COMMENT '上传人',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`file_id`),
    KEY `idx_file_biz` (`biz_type`, `biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件表';

-- =====================================================================
--  二、人员域
-- =====================================================================

-- ---------------------------------------------------------------------
-- 人员统一档案
-- 一张表打通「群众 → 申请人 → 积极分子 → 发展对象 → 预备党员 → 正式党员」全生命周期
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `party_person`;
CREATE TABLE `party_person` (
    `person_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '人员ID',
    `person_no`       VARCHAR(64)  DEFAULT NULL            COMMENT '人员编号',
    `name`            VARCHAR(64)  NOT NULL                COMMENT '姓名',
    `sex`             TINYINT      NOT NULL DEFAULT 1      COMMENT '性别：1=男 2=女',
    `id_card`         VARCHAR(32)  DEFAULT NULL            COMMENT '身份证号',
    `birth_date`      DATE         DEFAULT NULL            COMMENT '出生日期',
    `age`             INT          DEFAULT NULL            COMMENT '年龄（冗余计算）',
    `nation`          VARCHAR(20)  DEFAULT '汉族'          COMMENT '民族',
    `native_place`    VARCHAR(100) DEFAULT NULL            COMMENT '籍贯',
    `phone`           VARCHAR(20)  DEFAULT NULL            COMMENT '联系电话',
    `education`       VARCHAR(30)  DEFAULT NULL            COMMENT '学历',
    `work_unit`       VARCHAR(150) DEFAULT NULL            COMMENT '工作单位',
    `job_title`       VARCHAR(100) DEFAULT NULL            COMMENT '职务',
    `org_id`          BIGINT       NOT NULL                COMMENT '所属党组织',
    `group_id`        BIGINT       DEFAULT NULL            COMMENT '所属党小组',
    `avatar`          VARCHAR(500) DEFAULT NULL            COMMENT '头像URL',
    `member_status`   TINYINT      NOT NULL DEFAULT 0      COMMENT '人员状态：0=群众 1=入党申请人 2=入党积极分子 3=发展对象 4=预备党员 5=正式党员 6=流动党员',
    `political_status` VARCHAR(20) DEFAULT NULL            COMMENT '政治面貌',
    `is_member`       TINYINT      NOT NULL DEFAULT 0      COMMENT '是否党员：0=否 1=是',
    `apply_date`      DATE         DEFAULT NULL            COMMENT '递交入党申请书日期',
    `activist_date`   DATE         DEFAULT NULL            COMMENT '确定为入党积极分子日期',
    `candidate_date`  DATE         DEFAULT NULL            COMMENT '确定为发展对象日期',
    `probationary_date` DATE       DEFAULT NULL            COMMENT '成为预备党员日期',
    `full_member_date`  DATE       DEFAULT NULL            COMMENT '转为正式党员日期',
    `party_age`       INT          DEFAULT NULL            COMMENT '党龄（年，冗余计算）',
    `status`          TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0=停用 1=正常',
    `remark`          VARCHAR(500) DEFAULT NULL,
    `del_flag`        TINYINT      NOT NULL DEFAULT 0,
    `create_by`       VARCHAR(64)  DEFAULT NULL,
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`       VARCHAR(64)  DEFAULT NULL,
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`person_id`),
    -- 身份证号全局唯一：Service 层已有「先查后插」校验，但并发下两个请求可能同时通过检查，
    -- 必须由数据库唯一索引兜底，否则同一人会生成两条存活档案（跑两条发展流程、收两份党费）。
    -- 注意：id_card 允许为 NULL（不填身份证的场景），MySQL 唯一索引对多个 NULL 是放行的，
    -- 因此该索引不影响「不填身份证」的人员录入。
    -- 身份证唯一约束只作用于存活行：删除某人后用同一身份证重新录入不会被墓碑挡住。
    -- 该列对多个 NULL 放行，因此「不填身份证」的场景不受影响。
    `id_card_alive` VARCHAR(32) GENERATED ALWAYS AS (IF(`del_flag` = 0, `id_card`, NULL)) VIRTUAL
                    COMMENT '仅存活行的身份证号，用于唯一约束',
    UNIQUE KEY `uk_person_id_card_alive` (`id_card_alive`),
    KEY `idx_person_org`    (`org_id`),
    KEY `idx_person_status` (`member_status`),
    KEY `idx_person_name`   (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人员统一档案表';

-- ---------------------------------------------------------------------
-- 党员扩展信息（member_status >= 4 时有效）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `party_member_profile`;
CREATE TABLE `party_member_profile` (
    `profile_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `person_id`         BIGINT       NOT NULL                COMMENT '人员ID',
    `branch_secretary`  VARCHAR(64)  DEFAULT NULL            COMMENT '支部书记姓名（冗余）',
    `introducer1_id`    BIGINT       DEFAULT NULL            COMMENT '入党介绍人1',
    `introducer2_id`    BIGINT       DEFAULT NULL            COMMENT '入党介绍人2',
    `trainer1_id`       BIGINT       DEFAULT NULL            COMMENT '培养联系人1',
    `trainer2_id`       BIGINT       DEFAULT NULL            COMMENT '培养联系人2',
    `volunteer_book_no` VARCHAR(64)  DEFAULT NULL            COMMENT '入党志愿书编号',
    `dues_base`         DECIMAL(10,2) DEFAULT NULL           COMMENT '党费缴纳基数（元）',
    `dues_standard`     DECIMAL(10,2) DEFAULT NULL           COMMENT '每月应缴党费',
    `party_position`    VARCHAR(100) DEFAULT NULL            COMMENT '党内职务',
    `is_flow`           TINYINT      NOT NULL DEFAULT 0      COMMENT '是否流动党员：0=否 1=是',
    `flow_location`     VARCHAR(255) DEFAULT NULL            COMMENT '流动去向',
    `archive_location`  VARCHAR(255) DEFAULT NULL            COMMENT '档案存放地',
    `create_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`profile_id`),
    UNIQUE KEY `uk_profile_person` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员扩展信息表';

-- ---------------------------------------------------------------------
-- 党内职务任职记录
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `party_position`;
CREATE TABLE `party_position` (
    `position_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `person_id`     BIGINT       NOT NULL                COMMENT '人员ID',
    `org_id`        BIGINT       NOT NULL                COMMENT '组织ID',
    `position_code` VARCHAR(50)  NOT NULL                COMMENT '职务编码：SECRETARY/DEPUTY/ORG_COMMITTEE/PROP_COMMITTEE/DISC_COMMITTEE/GROUP_LEADER',
    `position_name` VARCHAR(50)  NOT NULL                COMMENT '职务名称：书记/副书记/组织委员/宣传委员/纪检委员/党小组长',
    `start_date`    DATE         DEFAULT NULL            COMMENT '任职开始',
    `end_date`      DATE         DEFAULT NULL            COMMENT '任职结束（NULL=在任）',
    `is_current`    TINYINT      NOT NULL DEFAULT 1      COMMENT '是否现任：0=否 1=是',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`position_id`),
    KEY `idx_position_person` (`person_id`),
    KEY `idx_position_org`    (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党内职务任职记录表';

-- ---------------------------------------------------------------------
-- 党小组
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `party_group`;
CREATE TABLE `party_group` (
    `group_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '党小组ID',
    `group_name`  VARCHAR(100) NOT NULL                COMMENT '党小组名称',
    `org_id`      BIGINT       NOT NULL                COMMENT '所属党支部',
    `leader_id`   BIGINT       DEFAULT NULL            COMMENT '党小组长',
    `member_count` INT         NOT NULL DEFAULT 0      COMMENT '人数',
    `order_num`   INT          NOT NULL DEFAULT 0,
    `status`      TINYINT      NOT NULL DEFAULT 1,
    `del_flag`    TINYINT      NOT NULL DEFAULT 0,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`group_id`),
    KEY `idx_group_org` (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党小组表';

-- =====================================================================
--  三、发展党员域（5 阶段 25 步流程引擎）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 阶段模板（5 条，静态）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_stage`;
CREATE TABLE `dev_stage` (
    `stage_id`    BIGINT      NOT NULL AUTO_INCREMENT COMMENT '阶段ID',
    `stage_code`  VARCHAR(20) NOT NULL                COMMENT '阶段编码：STAGE_1..STAGE_5',
    `stage_name`  VARCHAR(50) NOT NULL                COMMENT '阶段名称',
    `stage_order` INT         NOT NULL                COMMENT '阶段顺序：1..5',
    `description` VARCHAR(500) DEFAULT NULL           COMMENT '阶段说明',
    PRIMARY KEY (`stage_id`),
    UNIQUE KEY `uk_stage_code` (`stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展阶段模板表';

-- ---------------------------------------------------------------------
-- 步骤模板（25 条，静态）
-- step_type：1=单次办理型 2=周期性考察型
-- rule_key：多个规则用逗号分隔，对应 DevStepRule 策略实现
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_step`;
CREATE TABLE `dev_step` (
    `step_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '步骤ID',
    `step_code`      VARCHAR(20)  NOT NULL                COMMENT '步骤编码：STEP_01..STEP_25',
    `step_name`      VARCHAR(100) NOT NULL                COMMENT '步骤名称',
    `stage_code`     VARCHAR(20)  NOT NULL                COMMENT '所属阶段编码',
    `step_order`     INT          NOT NULL                COMMENT '全流程顺序：1..25',
    `step_type`      TINYINT      NOT NULL DEFAULT 1      COMMENT '步骤类型：1=单次办理 2=周期性考察',
    `handle_roles`   VARCHAR(255) DEFAULT NULL            COMMENT '办理角色，逗号分隔',
    `handle_org_type` TINYINT     DEFAULT NULL            COMMENT '办理组织类型：1=党委 2=党总支 3=党支部(支部委员会/支部大会) 4=党小组',
    `need_vote`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否需要表决：0=否 1=是',
    `deadline_days`  INT          DEFAULT NULL            COMMENT '办结期限（天），NULL=无限制',
    `interval_days`  INT          DEFAULT NULL            COMMENT '距基准步骤须满天数（如培养教育满1年=365）',
    `interval_base_step` VARCHAR(20) DEFAULT NULL         COMMENT '间隔基准步骤编码',
    `periodic_days`  INT          DEFAULT NULL            COMMENT '周期性考察间隔天数（180=半年, 90=一季度）',
    `min_training_days` INT       DEFAULT NULL            COMMENT '集中培训最少天数（3）',
    `min_training_hours` INT      DEFAULT NULL            COMMENT '集中培训最少学时（24）',
    `rule_key`       VARCHAR(255) DEFAULT NULL            COMMENT '绑定的规则策略，逗号分隔',
    `material_desc`  VARCHAR(500) DEFAULT NULL            COMMENT '所需材料说明',
    `description`    TEXT                                 COMMENT '步骤说明（来自流程图原文）',
    `is_branch`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否分支节点：0=否 1=是（如STEP_23三出口）',
    PRIMARY KEY (`step_id`),
    UNIQUE KEY `uk_step_code` (`step_code`),
    KEY `idx_step_stage` (`stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展步骤模板表';

-- ---------------------------------------------------------------------
-- 申请人实例（一人一条，记录当前走到哪一步）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_applicant`;
CREATE TABLE `dev_applicant` (
    `applicant_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '申请人实例ID',
    `person_id`        BIGINT       NOT NULL                COMMENT '人员ID',
    `org_id`           BIGINT       NOT NULL                COMMENT '所属党组织',
    `current_stage`    VARCHAR(20)  NOT NULL DEFAULT 'STAGE_1' COMMENT '当前阶段编码',
    `current_step`     VARCHAR(20)  NOT NULL DEFAULT 'STEP_01' COMMENT '当前步骤编码',
    `status`           TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1=进行中 2=已完成(转为正式党员) 3=已终止(取消资格) 4=已中止(离开单位等)',
    `progress`         INT          NOT NULL DEFAULT 0      COMMENT '进度百分比 0-100',
    `apply_date`       DATE         DEFAULT NULL            COMMENT '递交申请书日期',
    `activist_date`    DATE         DEFAULT NULL            COMMENT '确定为积极分子日期',
    `candidate_date`   DATE         DEFAULT NULL            COMMENT '确定为发展对象日期',
    `probationary_date` DATE        DEFAULT NULL            COMMENT '成为预备党员日期',
    `full_member_date` DATE         DEFAULT NULL            COMMENT '转为正式党员日期',
    `probation_end_date` DATE       DEFAULT NULL            COMMENT '预备期满日（=预备党员日期+1年，每次延长预备期后顺延）',
    `probation_extend_count` TINYINT NOT NULL DEFAULT 0     COMMENT '已延长预备期次数（最多1次）',
    `probation_extend_months` INT   NOT NULL DEFAULT 0      COMMENT '累计延长月数',
    `branch_secretary_id` BIGINT    DEFAULT NULL            COMMENT '支部书记',
    `trainer_ids`      VARCHAR(64)  DEFAULT NULL            COMMENT '培养联系人ID，逗号分隔（1-2名）',
    `introducer_ids`   VARCHAR(64)  DEFAULT NULL            COMMENT '入党介绍人ID，逗号分隔（2名）',
    `volunteer_book_no` VARCHAR(64) DEFAULT NULL            COMMENT '入党志愿书编号',
    `finish_time`      DATETIME     DEFAULT NULL            COMMENT '流程完成时间',
    `terminate_reason` VARCHAR(500) DEFAULT NULL            COMMENT '终止原因',
    `remark`           VARCHAR(500) DEFAULT NULL,
    `del_flag`         TINYINT      NOT NULL DEFAULT 0,
    `create_by`        VARCHAR(64)  DEFAULT NULL,
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`        VARCHAR(64)  DEFAULT NULL,
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`applicant_id`),
    UNIQUE KEY `uk_applicant_person` (`person_id`),
    KEY `idx_applicant_org`    (`org_id`),
    KEY `idx_applicant_step`   (`current_step`),
    KEY `idx_applicant_stage`  (`current_stage`),
    KEY `idx_applicant_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展党员申请人实例表';

-- ---------------------------------------------------------------------
-- 步骤办理记录（动态，每人每步一行；周期性步骤多行）
-- result：1=通过 2=驳回(退回上一步) 3=不通过(终止) 4=延长 5=取消资格
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_step_record`;
CREATE TABLE `dev_step_record` (
    `record_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `applicant_id`  BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `person_id`     BIGINT       NOT NULL                COMMENT '人员ID（冗余，便于按人查）',
    `org_id`        BIGINT       NOT NULL                COMMENT '组织ID（冗余，便于数据权限）',
    `step_code`     VARCHAR(20)  NOT NULL                COMMENT '步骤编码',
    `stage_code`    VARCHAR(20)  NOT NULL                COMMENT '阶段编码（冗余）',
    `seq_no`        INT          NOT NULL DEFAULT 1      COMMENT '同一步骤的第几次办理（周期性步骤用）',
    `result`        TINYINT      DEFAULT NULL            COMMENT '结论：1=通过 2=驳回 3=不通过 4=延长预备期 5=取消资格',
    `opinion`       VARCHAR(1000) DEFAULT NULL           COMMENT '办理意见',
    `content`       TEXT                                 COMMENT '考察记录/谈话记录内容（周期性步骤用）',
    `handle_user_id` BIGINT      DEFAULT NULL            COMMENT '办理人 user_id',
    `handle_person_id` BIGINT    DEFAULT NULL            COMMENT '办理人 person_id',
    `handle_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '办理人姓名（冗余）',
    `handle_org_id` BIGINT       DEFAULT NULL            COMMENT '办理组织ID',
    `handle_time`   DATETIME     DEFAULT NULL            COMMENT '办理时间',
    `deadline_time` DATETIME     DEFAULT NULL            COMMENT '应办结时间（办理时限预警用）',
    `is_overdue`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否超期：0=否 1=是',
    `next_step_code` VARCHAR(20) DEFAULT NULL            COMMENT '流转到的下一步骤',
    `status`        TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1=已办结 2=待办 3=已作废',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`record_id`),
    KEY `idx_record_applicant` (`applicant_id`),
    KEY `idx_record_step`      (`applicant_id`, `step_code`),
    KEY `idx_record_org`       (`org_id`),
    KEY `idx_record_status`    (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='步骤办理记录表';

-- ---------------------------------------------------------------------
-- 材料档案（入党申请书、思想汇报、政审材料、志愿书、转正申请…）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_material`;
CREATE TABLE `dev_material` (
    `material_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '材料ID',
    `applicant_id`  BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `person_id`     BIGINT       NOT NULL                COMMENT '人员ID',
    `step_code`     VARCHAR(20)  DEFAULT NULL            COMMENT '关联步骤编码',
    `material_type` VARCHAR(50)  NOT NULL                COMMENT '材料类型：APPLY_BOOK=入党申请书 THOUGHT_REPORT=思想汇报 POLITICAL_REVIEW=政治审查材料 VOLUNTEER_BOOK=入党志愿书 REGULAR_APPLY=转正申请书 TRAINING_CERT=培训证明 OTHER=其它',
    `template_code` VARCHAR(32)  DEFAULT NULL            COMMENT '对应 dev_material_template.template_code，如 1-1、4-3。用于精确判定「这份材料 fulfills 哪份模板」；留空时退化为按 material_type + step_code 宽松匹配',
    `material_name` VARCHAR(255) NOT NULL                COMMENT '材料名称',
    `file_id`       BIGINT       DEFAULT NULL            COMMENT '文件ID（sys_file）',
    `file_url`      VARCHAR(500) DEFAULT NULL            COMMENT '文件URL',
    `content`       TEXT                                 COMMENT '文本内容（如思想汇报正文）',
    `submit_date`   DATE         DEFAULT NULL            COMMENT '提交日期',
    `is_required`   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否必备材料',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0,
    `create_by`     VARCHAR(64)  DEFAULT NULL,
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`material_id`),
    KEY `idx_material_applicant` (`applicant_id`),
    KEY `idx_material_type`      (`material_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展党员材料档案表';

-- ---------------------------------------------------------------------
-- 谈话记录（STEP_02 党组织派人谈话 / STEP_16 上级党委派人谈话）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_talk`;
CREATE TABLE `dev_talk` (
    `talk_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '谈话ID',
    `applicant_id`  BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `step_code`     VARCHAR(20)  NOT NULL                COMMENT '步骤编码：STEP_02 / STEP_16',
    `talk_type`     TINYINT      NOT NULL                COMMENT '谈话类型：1=党组织派人谈话 2=上级党委派人谈话',
    `talk_date`     DATE         DEFAULT NULL            COMMENT '谈话日期',
    `talk_place`    VARCHAR(255) DEFAULT NULL            COMMENT '谈话地点',
    `talker_id`     BIGINT       DEFAULT NULL            COMMENT '谈话人 person_id',
    `talker_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '谈话人姓名',
    `talker_position` VARCHAR(50) DEFAULT NULL           COMMENT '谈话人职务',
    `content`       TEXT                                 COMMENT '谈话内容',
    `conclusion`    VARCHAR(1000) DEFAULT NULL           COMMENT '谈话结论/对能否入党的意见',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`talk_id`),
    KEY `idx_talk_applicant` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='谈话记录表';

-- ---------------------------------------------------------------------
-- 表决记录（STEP_15 接收预备党员 / STEP_23 转正讨论）
-- 票数规则：到会有表决权人数 > 应到会有表决权人数的半数
--           赞成票 > 应到会有表决权的正式党员的半数
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_vote`;
CREATE TABLE `dev_vote` (
    `vote_id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '表决ID',
    `applicant_id`       BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `step_code`          VARCHAR(20)  NOT NULL                COMMENT '步骤编码：STEP_15 / STEP_23',
    `meeting_date`       DATE         DEFAULT NULL            COMMENT '会议日期',
    `meeting_place`      VARCHAR(255) DEFAULT NULL            COMMENT '会议地点',
    `should_attend`      INT          NOT NULL DEFAULT 0      COMMENT '应到会有表决权的正式党员数',
    `actual_attend`      INT          NOT NULL DEFAULT 0      COMMENT '实到会有表决权人数',
    `quorum_required`    INT          NOT NULL DEFAULT 0      COMMENT '开会法定人数（应到半数，向下取整+1）',
    `is_quorum_met`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否达到开会法定人数：0=否 1=是',
    `agree_count`        INT          NOT NULL DEFAULT 0      COMMENT '赞成票',
    `oppose_count`       INT          NOT NULL DEFAULT 0      COMMENT '反对票',
    `abstain_count`      INT          NOT NULL DEFAULT 0      COMMENT '弃权票',
    `pass_required`      INT          NOT NULL DEFAULT 0      COMMENT '通过所需赞成票数（应到半数，向下取整+1）',
    `is_passed`          TINYINT      NOT NULL DEFAULT 0      COMMENT '是否通过：0=否 1=是',
    `result_type`        TINYINT      DEFAULT NULL            COMMENT '结果类型（STEP_23用）：1=按期转正 2=延长预备期 3=取消预备党员资格',
    `extend_months`      INT          DEFAULT NULL            COMMENT '延长预备期月数（>=6 且 <=12）',
    `host_id`            BIGINT       DEFAULT NULL            COMMENT '主持人 person_id',
    `host_name`          VARCHAR(64)  DEFAULT NULL            COMMENT '主持人姓名',
    `recorder_name`      VARCHAR(64)  DEFAULT NULL            COMMENT '记录人',
    `content`            TEXT                                 COMMENT '会议记录',
    `create_time`        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`vote_id`),
    KEY `idx_vote_applicant` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支部大会表决记录表';

-- ---------------------------------------------------------------------
-- 政治审查（STEP_10，一人一条）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_political_review`;
CREATE TABLE `dev_political_review` (
    `review_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '政审ID',
    `applicant_id`   BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `review_date`    DATE         DEFAULT NULL            COMMENT '审查日期',
    `attitude`       TEXT                                 COMMENT '对党的理论和路线、方针、政策的态度',
    `history`        TEXT                                 COMMENT '政治历史和在重大政治斗争中的表现',
    `law_abide`      TEXT                                 COMMENT '遵纪守法和遵守社会公德情况',
    `relatives`      TEXT                                 COMMENT '直系亲属和主要社会关系的政治情况',
    `method`         VARCHAR(500) DEFAULT NULL            COMMENT '审查方法：同本人谈话/查阅档案/函调/外调',
    `conclusion`     TEXT                                 COMMENT '政治审查结论性材料',
    `review_result`  TINYINT      NOT NULL DEFAULT 0      COMMENT '审查结果：1=合格 2=不合格',
    `reviewer_id`    BIGINT       DEFAULT NULL            COMMENT '审查人',
    `reviewer_name`  VARCHAR(64)  DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`review_id`),
    KEY `idx_review_applicant` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='政治审查表';

-- ---------------------------------------------------------------------
-- 集中培训（STEP_11，一人可多次）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_training`;
CREATE TABLE `dev_training` (
    `training_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '培训ID',
    `applicant_id`   BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `training_name`  VARCHAR(255) NOT NULL                COMMENT '培训名称',
    `organizer`      VARCHAR(150) DEFAULT NULL            COMMENT '主办单位：基层党委/县级党委组织部门',
    `start_date`     DATE         DEFAULT NULL            COMMENT '开始日期',
    `end_date`       DATE         DEFAULT NULL            COMMENT '结束日期',
    `train_days`     DECIMAL(5,1) DEFAULT NULL            COMMENT '培训天数（须>=3）',
    `train_hours`    DECIMAL(5,1) DEFAULT NULL            COMMENT '培训学时（须>=24）',
    `is_qualified`   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否合格：0=否 1=是',
    `cert_file_id`   BIGINT       DEFAULT NULL            COMMENT '结业证明文件ID',
    `remark`         VARCHAR(500) DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`training_id`),
    KEY `idx_training_applicant` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集中培训记录表';

-- ---------------------------------------------------------------------
-- 上级审批 / 备案（STEP_04 / 08 / 13 / 17 / 18 / 24）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_approval`;
CREATE TABLE `dev_approval` (
    `approval_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '审批ID',
    `applicant_id`   BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `step_code`      VARCHAR(20)  NOT NULL                COMMENT '步骤编码：STEP_04/08/13/17/18/24',
    `approval_type`  TINYINT      NOT NULL                COMMENT '类型：1=备案 2=预审 3=审批',
    `apply_org_id`   BIGINT       DEFAULT NULL            COMMENT '申请组织（党支部）',
    `approve_org_id` BIGINT       DEFAULT NULL            COMMENT '审批组织（上级党委/再上一级）',
    `submit_date`    DATE         DEFAULT NULL            COMMENT '提交日期',
    `deadline_date`  DATE         DEFAULT NULL            COMMENT '应办结日期（3个月内）',
    `approve_date`   DATE         DEFAULT NULL            COMMENT '审批日期',
    `result`         TINYINT      DEFAULT NULL            COMMENT '结果：1=同意 2=不同意',
    `opinion`        VARCHAR(1000) DEFAULT NULL           COMMENT '审批意见',
    `approver_id`    BIGINT       DEFAULT NULL            COMMENT '审批人 person_id',
    `approver_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '审批人姓名',
    `is_overdue`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否超期',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`approval_id`),
    KEY `idx_approval_applicant` (`applicant_id`),
    KEY `idx_approval_step`      (`step_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上级审批备案记录表';

-- ---------------------------------------------------------------------
-- 材料归档（STEP_25）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `dev_archive`;
CREATE TABLE `dev_archive` (
    `archive_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '归档ID',
    `applicant_id`    BIGINT       NOT NULL                COMMENT '申请人实例ID',
    `person_id`       BIGINT       NOT NULL                COMMENT '人员ID',
    `archive_type`    TINYINT      NOT NULL                COMMENT '归档方式：1=存入人事档案 2=建立党员档案',
    `archive_location` VARCHAR(255) DEFAULT NULL           COMMENT '存放地点（所在党委/县级党委组织部门）',
    `archive_date`    DATE         DEFAULT NULL            COMMENT '归档日期',
    `items`           TEXT                                 COMMENT '归档材料清单（JSON数组）',
    `keeper_name`     VARCHAR(64)  DEFAULT NULL            COMMENT '保管人',
    `remark`          VARCHAR(500) DEFAULT NULL,
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`archive_id`),
    KEY `idx_archive_applicant` (`applicant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展党员材料归档表';

-- =====================================================================
--  四、三会一课 / 组织生活域
-- =====================================================================

-- ---------------------------------------------------------------------
-- 会议（三会一课 + 主题党日 + 组织生活会 统一表，用 meeting_type 区分）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `am_meeting`;
CREATE TABLE `am_meeting` (
    `meeting_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会议ID',
    `meeting_type`   VARCHAR(30)  NOT NULL                COMMENT '会议类型：MEMBER_ASSEMBLY=党员大会 BRANCH_COMMITTEE=支部委员会 PARTY_GROUP=党小组会 PARTY_LECTURE=党课 THEME_PARTY_DAY=主题党日 ORG_LIFE=组织生活会',
    `title`          VARCHAR(255) NOT NULL                COMMENT '会议标题',
    `org_id`         BIGINT       NOT NULL                COMMENT '主办党组织',
    `group_id`       BIGINT       DEFAULT NULL            COMMENT '党小组（党小组会时）',
    `content`        TEXT                                 COMMENT '会议内容',
    `meeting_date`   DATE         DEFAULT NULL            COMMENT '会议日期',
    `start_time`     VARCHAR(20)  DEFAULT NULL            COMMENT '开始时间',
    `end_time`       VARCHAR(20)  DEFAULT NULL            COMMENT '结束时间',
    `place`          VARCHAR(255) DEFAULT NULL            COMMENT '会议地点',
    `host_id`        BIGINT       DEFAULT NULL            COMMENT '主持人',
    `host_name`      VARCHAR(64)  DEFAULT NULL,
    `recorder_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '记录人',
    `should_attend`  INT          NOT NULL DEFAULT 0      COMMENT '应到人数',
    `actual_attend`  INT          NOT NULL DEFAULT 0      COMMENT '实到人数',
    `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=草稿 1=待召开 2=进行中 3=已结束 4=已归档',
    `cover_file_id`  BIGINT       DEFAULT NULL            COMMENT '封面图文件ID',
    `create_by`      VARCHAR(64)  DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`      VARCHAR(64)  DEFAULT NULL,
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`meeting_id`),
    KEY `idx_meeting_type` (`meeting_type`),
    KEY `idx_meeting_org`  (`org_id`),
    KEY `idx_meeting_date` (`meeting_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议表（三会一课/主题党日/组织生活会）';

-- ---------------------------------------------------------------------
-- 参会人员
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `am_attendee`;
CREATE TABLE `am_attendee` (
    `attendee_id`  BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    `meeting_id`   BIGINT      NOT NULL                COMMENT '会议ID',
    `person_id`    BIGINT      NOT NULL                COMMENT '人员ID',
    `person_name`  VARCHAR(64) DEFAULT NULL            COMMENT '姓名（冗余）',
    `attend_status` TINYINT    NOT NULL DEFAULT 0      COMMENT '出席情况：0=未签到 1=已签到 2=请假 3=缺席',
    `sign_time`    DATETIME    DEFAULT NULL            COMMENT '签到时间',
    `leave_reason` VARCHAR(255) DEFAULT NULL           COMMENT '请假事由',
    PRIMARY KEY (`attendee_id`),
    KEY `idx_attendee_meeting` (`meeting_id`),
    KEY `idx_attendee_person`  (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议参会人员表';

-- ---------------------------------------------------------------------
-- 会议材料（对应图2 的九宫格：通知/会前学习/记录/党员剖析材料/党员自评材料/
--           其它内容/问题清单/整改清单/会议记录/民主评议党员/情况报告）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `am_material`;
CREATE TABLE `am_material` (
    `material_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '材料ID',
    `meeting_id`    BIGINT       DEFAULT NULL            COMMENT '会议ID',
    `org_id`        BIGINT       NOT NULL                COMMENT '组织ID',
    `category`      VARCHAR(30)  NOT NULL                COMMENT '材料分类：NOTICE=通知 PRE_STUDY=会前学习 RECORD=记录 ANALYSIS=党员剖析材料 SELF_EVAL=党员自评材料 OTHER=其它内容 PROBLEM_LIST=问题清单 RECTIFY_LIST=整改清单 MEETING_MINUTES=会议记录 DEMOCRATIC_EVAL=民主评议党员 SITUATION_REPORT=情况报告',
    `title`         VARCHAR(255) NOT NULL                COMMENT '材料标题',
    `content`       TEXT                                 COMMENT '文本内容',
    `file_id`       BIGINT       DEFAULT NULL            COMMENT '文件ID',
    `file_url`      VARCHAR(500) DEFAULT NULL            COMMENT '文件URL',
    `upload_by`     BIGINT       DEFAULT NULL            COMMENT '上传人 person_id',
    `upload_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '上传人姓名',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`material_id`),
    KEY `idx_am_material_meeting`  (`meeting_id`),
    KEY `idx_am_material_category` (`category`),
    KEY `idx_am_material_org`      (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会议材料表';

-- ---------------------------------------------------------------------
-- 活动任务通知（对应图1 底部「活动任务通知」卡片）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `am_task`;
CREATE TABLE `am_task` (
    `task_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `title`          VARCHAR(255) NOT NULL                COMMENT '任务标题',
    `task_type`      VARCHAR(30)  NOT NULL                COMMENT '任务类型：同 meeting_type',
    `publish_org_id` BIGINT       NOT NULL                COMMENT '发布单位组织ID',
    `publish_org_name` VARCHAR(100) DEFAULT NULL          COMMENT '发布单位名称（冗余）',
    `activity_name`  VARCHAR(255) DEFAULT NULL            COMMENT '活动名称',
    `content`        TEXT                                 COMMENT '活动内容',
    `start_date`     DATE         DEFAULT NULL            COMMENT '活动开始日期',
    `end_date`       DATE         DEFAULT NULL            COMMENT '活动结束日期',
    `deadline`       DATE         DEFAULT NULL            COMMENT '材料上传截止日期',
    `receive_org_ids` VARCHAR(500) DEFAULT NULL           COMMENT '接收组织ID，逗号分隔（NULL=全部下辖）',
    `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=草稿 1=已发布 2=已截止 3=已归档',
    `publish_by`     VARCHAR(64)  DEFAULT NULL,
    `publish_time`   DATETIME     DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag`       TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (`task_id`),
    KEY `idx_task_org`    (`publish_org_id`),
    KEY `idx_task_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动任务通知表';

-- ---------------------------------------------------------------------
-- 任务提交记录（支部上传资料）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `am_task_submit`;
CREATE TABLE `am_task_submit` (
    `submit_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `task_id`     BIGINT       NOT NULL                COMMENT '任务ID',
    `org_id`      BIGINT       NOT NULL                COMMENT '提交组织',
    `file_id`     BIGINT       DEFAULT NULL            COMMENT '文件ID',
    `file_url`    VARCHAR(500) DEFAULT NULL,
    `remark`      VARCHAR(500) DEFAULT NULL,
    `submit_by`   VARCHAR(64)  DEFAULT NULL,
    `submit_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`submit_id`),
    KEY `idx_submit_task` (`task_id`),
    KEY `idx_submit_org`  (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动任务提交记录表';

-- =====================================================================
--  五、党组织换届
-- =====================================================================

-- ---------------------------------------------------------------------
-- 换届选举记录
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `org_election`;
CREATE TABLE `org_election` (
    `election_id`      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '换届ID',
    `org_id`           BIGINT       NOT NULL                COMMENT '换届组织',
    `election_type`    TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1=换届选举 2=补选 3=委员调整',
    `term_no`          INT          DEFAULT NULL            COMMENT '届次，如 5 表示第五届',
    `title`            VARCHAR(255) NOT NULL                COMMENT '换届名称',
    `reason`           VARCHAR(500) DEFAULT NULL            COMMENT '换届事由（任期届满/委员缺额等）',
    `plan_date`        DATE         DEFAULT NULL            COMMENT '计划换届日期',
    `election_date`    DATE         DEFAULT NULL            COMMENT '实际选举日期',
    `place`            VARCHAR(255) DEFAULT NULL            COMMENT '会议地点',
    `host_id`          BIGINT       DEFAULT NULL            COMMENT '主持人 person_id',
    `host_name`        VARCHAR(64)  DEFAULT NULL            COMMENT '主持人姓名',
    `recorder_name`    VARCHAR(64)  DEFAULT NULL            COMMENT '记录人',
    `should_attend`    INT          NOT NULL DEFAULT 0      COMMENT '应到有选举权党员数',
    `actual_attend`    INT          NOT NULL DEFAULT 0      COMMENT '实到有选举权党员数',
    `quorum_required`  INT          NOT NULL DEFAULT 0      COMMENT '开会法定人数',
    `is_quorum_met`    TINYINT      NOT NULL DEFAULT 0      COMMENT '是否达到法定人数：0=否 1=是',
    `status`           TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=筹备中 1=进行中 2=已完成 3=已终止',
    `result_summary`   TEXT                                 COMMENT '选举结果摘要',
    `approve_org_id`   BIGINT       DEFAULT NULL            COMMENT '批准组织（上级党委）',
    `approve_date`     DATE         DEFAULT NULL            COMMENT '批复日期',
    `file_id`          BIGINT       DEFAULT NULL            COMMENT '请示/批复文件ID',
    `file_url`         VARCHAR(500) DEFAULT NULL            COMMENT '文件URL',
    `remark`           VARCHAR(500) DEFAULT NULL,
    `del_flag`         TINYINT      NOT NULL DEFAULT 0,
    `create_by`        VARCHAR(64)  DEFAULT NULL,
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`        VARCHAR(64)  DEFAULT NULL,
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`election_id`),
    KEY `idx_election_org`    (`org_id`),
    KEY `idx_election_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党组织换届选举表';

-- ---------------------------------------------------------------------
-- 换届候选人与当选情况
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `org_election_candidate`;
CREATE TABLE `org_election_candidate` (
    `candidate_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `election_id`    BIGINT       NOT NULL                COMMENT '换届ID',
    `person_id`      BIGINT       NOT NULL                COMMENT '人员ID',
    `person_name`    VARCHAR(64)  DEFAULT NULL            COMMENT '姓名（冗余）',
    `position_code`  VARCHAR(50)  NOT NULL                COMMENT '候选职务编码：SECRETARY/DEPUTY/ORG_COMMITTEE/PROP_COMMITTEE/DISC_COMMITTEE',
    `position_name`  VARCHAR(50)  NOT NULL                COMMENT '候选职务名称',
    `is_incumbent`   TINYINT      NOT NULL DEFAULT 0      COMMENT '是否现任：0=否 1=是',
    `votes`          INT          NOT NULL DEFAULT 0      COMMENT '得票数',
    `is_elected`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否当选：0=否 1=是',
    `remark`         VARCHAR(500) DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`candidate_id`),
    KEY `idx_candidate_election` (`election_id`),
    KEY `idx_candidate_person`   (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='换届候选人表';

-- =====================================================================
--  六、党员教育管理
-- =====================================================================

DROP TABLE IF EXISTS `edu_activity`;
CREATE TABLE `edu_activity` (
    `activity_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '教育活动ID',
    `title`          VARCHAR(255) NOT NULL                COMMENT '活动名称',
    `activity_type`  TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1=党课 2=专题培训 3=在线学习 4=实践锻炼 5=集中轮训',
    `org_id`         BIGINT       NOT NULL                COMMENT '主办党组织',
    `organizer`      VARCHAR(150) DEFAULT NULL            COMMENT '主办单位',
    `start_date`     DATE         DEFAULT NULL            COMMENT '开始日期',
    `end_date`       DATE         DEFAULT NULL            COMMENT '结束日期',
    `study_hours`    DECIMAL(5,1) DEFAULT NULL            COMMENT '学时',
    `place`          VARCHAR(255) DEFAULT NULL            COMMENT '活动地点',
    `teacher`        VARCHAR(100) DEFAULT NULL            COMMENT '主讲人',
    `content`        TEXT                                 COMMENT '活动内容',
    `should_attend`  INT          NOT NULL DEFAULT 0      COMMENT '应到人数',
    `actual_attend`  INT          NOT NULL DEFAULT 0      COMMENT '实到人数',
    `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=草稿 1=报名中 2=进行中 3=已结束',
    `file_id`        BIGINT       DEFAULT NULL            COMMENT '材料文件ID',
    `file_url`       VARCHAR(500) DEFAULT NULL            COMMENT '材料URL',
    `remark`         VARCHAR(500) DEFAULT NULL,
    `del_flag`       TINYINT      NOT NULL DEFAULT 0,
    `create_by`      VARCHAR(64)  DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`      VARCHAR(64)  DEFAULT NULL,
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`activity_id`),
    KEY `idx_edu_org`    (`org_id`),
    KEY `idx_edu_status` (`status`),
    KEY `idx_edu_date`   (`start_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员教育活动表';

DROP TABLE IF EXISTS `edu_participant`;
CREATE TABLE `edu_participant` (
    `participant_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `activity_id`    BIGINT       NOT NULL                COMMENT '教育活动ID',
    `person_id`      BIGINT       NOT NULL                COMMENT '人员ID',
    `person_name`    VARCHAR(64)  DEFAULT NULL            COMMENT '姓名（冗余）',
    `org_id`         BIGINT       DEFAULT NULL            COMMENT '所属组织',
    `attend_status`  TINYINT      NOT NULL DEFAULT 0      COMMENT '参与情况：0=未签到 1=已参加 2=请假 3=缺席',
    `study_hours`    DECIMAL(5,1) DEFAULT NULL            COMMENT '实际学时',
    `score`          DECIMAL(5,1) DEFAULT NULL            COMMENT '考核成绩',
    `is_passed`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否合格：0=否 1=是',
    `remark`         VARCHAR(500) DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`participant_id`),
    KEY `idx_edu_participant_activity` (`activity_id`),
    KEY `idx_edu_participant_person`   (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员教育参与记录表';

-- =====================================================================
--  七、党纪学习教育
-- =====================================================================

DROP TABLE IF EXISTS `discipline_study`;
CREATE TABLE `discipline_study` (
    `study_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '党纪学习ID',
    `title`            VARCHAR(255) NOT NULL                COMMENT '学习主题',
    `study_type`       TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1=条例学习 2=警示教育 3=专题党课 4=知识测试 5=案例研讨',
    `org_id`           BIGINT       NOT NULL                COMMENT '组织ID',
    `study_date`       DATE         DEFAULT NULL            COMMENT '学习日期',
    `place`            VARCHAR(255) DEFAULT NULL            COMMENT '学习地点',
    `teacher`          VARCHAR(100) DEFAULT NULL            COMMENT '主讲人',
    `content`          TEXT                                 COMMENT '学习内容',
    `participant_count` INT         NOT NULL DEFAULT 0      COMMENT '参加人数',
    `pass_count`       INT          NOT NULL DEFAULT 0      COMMENT '测试通过人数',
    `status`           TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=草稿 1=进行中 2=已结束',
    `file_id`          BIGINT       DEFAULT NULL            COMMENT '学习材料文件ID',
    `file_url`         VARCHAR(500) DEFAULT NULL,
    `remark`           VARCHAR(500) DEFAULT NULL,
    `del_flag`         TINYINT      NOT NULL DEFAULT 0,
    `create_by`        VARCHAR(64)  DEFAULT NULL,
    `create_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`        VARCHAR(64)  DEFAULT NULL,
    `update_time`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`study_id`),
    KEY `idx_disc_org`  (`org_id`),
    KEY `idx_disc_date` (`study_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党纪学习教育表';

DROP TABLE IF EXISTS `discipline_participant`;
CREATE TABLE `discipline_participant` (
    `participant_id` BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `study_id`       BIGINT       NOT NULL                COMMENT '党纪学习ID',
    `person_id`      BIGINT       NOT NULL                COMMENT '人员ID',
    `person_name`    VARCHAR(64)  DEFAULT NULL            COMMENT '姓名（冗余）',
    `attend_status`  TINYINT      NOT NULL DEFAULT 0      COMMENT '参与情况：0=未签到 1=已参加 2=请假 3=缺席',
    `score`          DECIMAL(5,1) DEFAULT NULL            COMMENT '测试成绩',
    `is_passed`      TINYINT      NOT NULL DEFAULT 0      COMMENT '是否通过：0=否 1=是',
    `remark`         VARCHAR(500) DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`participant_id`),
    KEY `idx_disc_participant_study`  (`study_id`),
    KEY `idx_disc_participant_person` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党纪学习参与记录表';

-- =====================================================================
--  八、党费收缴及使用
-- =====================================================================

-- ---------------------------------------------------------------------
-- 党费缴纳记录（一人一月一条）
-- 党费标准按月工资收入分档：3000元以下0.5%、3000-5000元1%、
-- 5000-10000元1.5%、10000元以上2%；离退休党员按0.5%
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `party_dues_record`;
CREATE TABLE `party_dues_record` (
    `dues_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '党费ID',
    `person_id`      BIGINT       NOT NULL                COMMENT '人员ID',
    `person_name`    VARCHAR(64)  DEFAULT NULL            COMMENT '姓名（冗余）',
    `org_id`         BIGINT       NOT NULL                COMMENT '所属党组织',
    `dues_year`      INT          NOT NULL                COMMENT '年份',
    `dues_month`     INT          NOT NULL                COMMENT '月份 1-12',
    `dues_base`      DECIMAL(10,2) DEFAULT NULL           COMMENT '缴纳基数（月工资收入）',
    `dues_standard`  DECIMAL(10,2) DEFAULT NULL           COMMENT '应缴金额',
    `dues_paid`      DECIMAL(10,2) DEFAULT NULL           COMMENT '实缴金额',
    `pay_date`       DATE         DEFAULT NULL            COMMENT '缴纳日期',
    `pay_type`       TINYINT      DEFAULT NULL            COMMENT '缴纳方式：1=现金 2=银行代扣 3=微信 4=支付宝 5=其它',
    `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=未缴 1=已缴 2=免缴 3=补缴',
    `is_overdue`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否欠缴：0=否 1=是',
    `remark`         VARCHAR(500) DEFAULT NULL,
    `del_flag`       TINYINT      NOT NULL DEFAULT 0,
    `create_by`      VARCHAR(64)  DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`      VARCHAR(64)  DEFAULT NULL,
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`dues_id`),
    -- 一人一月一条的约束同样只作用于存活行，否则删掉某月账单后无法再生成
    `dues_key_alive` VARCHAR(64) GENERATED ALWAYS AS (
        IF(`del_flag` = 0, CONCAT(`person_id`, '-', `dues_year`, '-', `dues_month`), NULL)) VIRTUAL
                     COMMENT '仅存活行的「人-年-月」组合，用于唯一约束',
    UNIQUE KEY `uk_dues_person_month_alive` (`dues_key_alive`),
    KEY `idx_dues_org`    (`org_id`),
    KEY `idx_dues_period` (`dues_year`, `dues_month`),
    KEY `idx_dues_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党费缴纳记录表';

-- ---------------------------------------------------------------------
-- 党费使用记录
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `party_dues_use`;
CREATE TABLE `party_dues_use` (
    `use_id`        BIGINT       NOT NULL AUTO_INCREMENT COMMENT '使用ID',
    `org_id`        BIGINT       NOT NULL                COMMENT '使用组织',
    `use_year`      INT          NOT NULL                COMMENT '年份',
    `use_month`     INT          DEFAULT NULL            COMMENT '月份',
    `amount`        DECIMAL(12,2) NOT NULL               COMMENT '使用金额',
    `use_category`  TINYINT      NOT NULL DEFAULT 1      COMMENT '用途分类：1=党员教育 2=表彰奖励 3=困难帮扶 4=阵地建设 5=订阅报刊 6=其它',
    `purpose`       VARCHAR(500) NOT NULL                COMMENT '具体用途',
    `use_date`      DATE         DEFAULT NULL            COMMENT '使用日期',
    `approver`      VARCHAR(64)  DEFAULT NULL            COMMENT '审批人',
    `file_id`       BIGINT       DEFAULT NULL            COMMENT '凭证文件ID',
    `file_url`      VARCHAR(500) DEFAULT NULL,
    `remark`        VARCHAR(500) DEFAULT NULL,
    `del_flag`      TINYINT      NOT NULL DEFAULT 0,
    `create_by`     VARCHAR(64)  DEFAULT NULL,
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`     VARCHAR(64)  DEFAULT NULL,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`use_id`),
    KEY `idx_dues_use_org`    (`org_id`),
    KEY `idx_dues_use_period` (`use_year`, `use_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党费使用记录表';

-- =====================================================================
--  九、党员服务
-- =====================================================================

DROP TABLE IF EXISTS `member_service`;
CREATE TABLE `member_service` (
    `service_id`     BIGINT       NOT NULL AUTO_INCREMENT COMMENT '服务ID',
    `title`          VARCHAR(255) NOT NULL                COMMENT '服务事项',
    `service_type`   TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1=困难帮扶 2=志愿服务 3=走访慰问 4=权益维护 5=就业帮扶 6=其它',
    `person_id`      BIGINT       DEFAULT NULL            COMMENT '服务对象 person_id',
    `person_name`    VARCHAR(64)  DEFAULT NULL            COMMENT '服务对象姓名',
    `org_id`         BIGINT       NOT NULL                COMMENT '受理组织',
    `service_date`   DATE         DEFAULT NULL            COMMENT '服务日期',
    `content`        TEXT                                 COMMENT '服务内容',
    `amount`         DECIMAL(12,2) DEFAULT NULL           COMMENT '帮扶金额',
    `handler_id`     BIGINT       DEFAULT NULL            COMMENT '经办人 person_id',
    `handler_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '经办人姓名',
    `status`         TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=待处理 1=处理中 2=已完成 3=已取消',
    `result`         VARCHAR(1000) DEFAULT NULL           COMMENT '办理结果',
    `file_id`        BIGINT       DEFAULT NULL            COMMENT '附件ID',
    `file_url`       VARCHAR(500) DEFAULT NULL,
    `remark`         VARCHAR(500) DEFAULT NULL,
    `del_flag`       TINYINT      NOT NULL DEFAULT 0,
    `create_by`      VARCHAR(64)  DEFAULT NULL,
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`      VARCHAR(64)  DEFAULT NULL,
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`service_id`),
    KEY `idx_service_org`    (`org_id`),
    KEY `idx_service_person` (`person_id`),
    KEY `idx_service_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='党员服务记录表';

-- =====================================================================
--  十、先优评选
-- =====================================================================

DROP TABLE IF EXISTS `excellent_selection`;
CREATE TABLE `excellent_selection` (
    `selection_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评选ID',
    `title`           VARCHAR(255) NOT NULL                COMMENT '评选活动名称',
    `selection_type`  TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1=优秀共产党员 2=优秀党务工作者 3=先进基层党组织',
    `org_id`          BIGINT       NOT NULL                COMMENT '主办组织',
    `selection_year`  INT          NOT NULL                COMMENT '评选年度',
    `start_date`      DATE         DEFAULT NULL            COMMENT '推荐开始日期',
    `end_date`        DATE         DEFAULT NULL            COMMENT '推荐截止日期',
    `quota`           INT          DEFAULT NULL            COMMENT '表彰名额',
    `status`          TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=草稿 1=推荐中 2=评审中 3=已公示 4=已表彰',
    `description`     TEXT                                 COMMENT '评选条件与说明',
    `file_id`         BIGINT       DEFAULT NULL            COMMENT '通知文件ID',
    `file_url`        VARCHAR(500) DEFAULT NULL,
    `remark`          VARCHAR(500) DEFAULT NULL,
    `del_flag`        TINYINT      NOT NULL DEFAULT 0,
    `create_by`       VARCHAR(64)  DEFAULT NULL,
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`       VARCHAR(64)  DEFAULT NULL,
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`selection_id`),
    KEY `idx_selection_org`    (`org_id`),
    KEY `idx_selection_year`   (`selection_year`),
    KEY `idx_selection_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='先优评选活动表';

DROP TABLE IF EXISTS `excellent_candidate`;
CREATE TABLE `excellent_candidate` (
    `candidate_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `selection_id`    BIGINT       NOT NULL                COMMENT '评选ID',
    `person_id`       BIGINT       DEFAULT NULL            COMMENT '候选人 person_id（个人类评选）',
    `person_name`     VARCHAR(64)  DEFAULT NULL            COMMENT '候选人姓名',
    `org_id`          BIGINT       DEFAULT NULL            COMMENT '候选组织ID（组织类评选）',
    `org_name`        VARCHAR(150) DEFAULT NULL            COMMENT '候选组织名称',
    `recommend_org_id` BIGINT      DEFAULT NULL            COMMENT '推荐组织',
    `deeds`           TEXT                                 COMMENT '主要事迹',
    `votes`           INT          NOT NULL DEFAULT 0      COMMENT '得票数',
    `rank_no`         INT          DEFAULT NULL            COMMENT '排名',
    `result`          TINYINT      NOT NULL DEFAULT 0      COMMENT '结果：0=待评审 1=已推荐 2=已获奖 3=未获奖',
    `remark`          VARCHAR(500) DEFAULT NULL,
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`candidate_id`),
    KEY `idx_exc_candidate_selection` (`selection_id`),
    KEY `idx_exc_candidate_person`    (`person_id`),
    KEY `idx_exc_candidate_result`    (`result`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='先优评选候选人表';

-- =====================================================================
--  十一、发展党员材料模板
--  依据《广西发展党员工作手册》要求的 50 份表格，按编号映射到 25 个步骤。
--  模板文件随 jar 打包在 hparty-admin/src/main/resources/material-templates/ 下。
-- =====================================================================
DROP TABLE IF EXISTS `dev_material_template`;
CREATE TABLE `dev_material_template` (
    `template_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '模板ID',
    `template_code` VARCHAR(32)  NOT NULL                COMMENT '模板编号，如 1-1、4-1a（与手册附件编号一致）',
    `template_name` VARCHAR(255) NOT NULL                COMMENT '材料名称',
    `stage_code`    VARCHAR(20)  NOT NULL                COMMENT '所属阶段：STAGE_1..STAGE_5',
    `step_code`     VARCHAR(20)  DEFAULT NULL            COMMENT '关联步骤；NULL 表示阶段通用或全程通用',
    `material_type` VARCHAR(50)  DEFAULT NULL            COMMENT '材料类型，对应 dev_material.material_type',
    `is_required`   TINYINT      NOT NULL DEFAULT 1      COMMENT '是否必备：0=否 1=是（必备材料缺失时流程规则会提示）',
    `is_roster`     TINYINT      NOT NULL DEFAULT 0      COMMENT '是否组织台账/名册：1=按组织归档，不随个人流程流转',
    `submit_role`   VARCHAR(50)  DEFAULT NULL            COMMENT '提交/出具方：APPLICANT=本人 BRANCH=党支部 TRAINER=培养联系人 PARENT_ORG=上级党委',
    `blank_file`    VARCHAR(255) DEFAULT NULL            COMMENT '空白模板文件名（material-templates/blank/ 下）',
    `sample_file`   VARCHAR(255) DEFAULT NULL            COMMENT '填写样例文件名（material-templates/sample/ 下）',
    `fill_note`     VARCHAR(1000) DEFAULT NULL           COMMENT '填写说明',
    `order_num`     INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
    `remark`        VARCHAR(500) DEFAULT NULL,
    `create_by`     VARCHAR(64)  DEFAULT NULL,
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`     VARCHAR(64)  DEFAULT NULL,
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`template_id`),
    UNIQUE KEY `uk_template_code` (`template_code`),
    KEY `idx_template_stage` (`stage_code`),
    KEY `idx_template_step`  (`step_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展党员材料模板表';

-- =====================================================================
--  十二、民主评议党员（P0-2）
--  主表 = 评议批次，子表 = 一人一条的评议明细。
--  主表有 org_id + review_year 的业务唯一约束且需要逻辑删除，
--  因此按 docs/03-数据库设计.md 2.2 的**生成列模式**建唯一索引：
--  逻辑删除后生成列变 NULL，多个 NULL 在唯一索引下互不冲突。
-- =====================================================================
DROP TABLE IF EXISTS `party_review`;
CREATE TABLE `party_review` (
    `review_id`       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评议批次ID',
    `title`           VARCHAR(255) NOT NULL                COMMENT '标题，如：2026年度民主评议党员',
    `org_id`          BIGINT       NOT NULL                COMMENT '组织ID',
    `review_year`     INT          NOT NULL                COMMENT '评议年度',
    `start_date`      DATE         DEFAULT NULL            COMMENT '自评开始日期',
    `end_date`        DATE         DEFAULT NULL            COMMENT '评议截止日期',
    `status`          TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=草稿 1=自评中 2=互评中 3=组织评定中 4=已公示 5=已完成',
    `excellent_quota` INT          DEFAULT NULL            COMMENT '优秀名额（一般不超过党员总数30%）',
    `description`     TEXT                                 COMMENT '评议说明',
    `file_id`         BIGINT       DEFAULT NULL            COMMENT '评议结果材料文件ID',
    `file_url`        VARCHAR(500) DEFAULT NULL            COMMENT '评议结果材料URL',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志：0=存在 1=删除',
    `create_by`       VARCHAR(64)  DEFAULT NULL,
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`       VARCHAR(64)  DEFAULT NULL,
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `org_year_alive`  VARCHAR(64) GENERATED ALWAYS AS (
        IF(`del_flag` = 0, CONCAT(`org_id`, '-', `review_year`), NULL)) VIRTUAL
        COMMENT '生成列：仅存活行参与唯一约束',
    PRIMARY KEY (`review_id`),
    UNIQUE KEY `uk_review_org_year_alive` (`org_year_alive`),
    KEY `idx_review_org`    (`org_id`),
    KEY `idx_review_year`   (`review_year`),
    KEY `idx_review_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='民主评议党员批次表';

DROP TABLE IF EXISTS `party_review_detail`;
CREATE TABLE `party_review_detail` (
    `detail_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    `review_id`    BIGINT       NOT NULL                COMMENT '评议批次ID',
    `person_id`    BIGINT       NOT NULL                COMMENT '人员ID',
    `person_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '姓名（冗余）',
    `org_id`       BIGINT       NOT NULL                COMMENT '组织ID',
    `self_score`   DECIMAL(5,1) DEFAULT NULL            COMMENT '自评得分',
    `self_comment` VARCHAR(1000) DEFAULT NULL           COMMENT '自评意见',
    `self_time`    DATETIME     DEFAULT NULL            COMMENT '自评提交时间',
    `peer_score`   DECIMAL(5,1) DEFAULT NULL            COMMENT '互评平均分',
    `peer_count`   INT          NOT NULL DEFAULT 0      COMMENT '参与互评人数',
    `mass_score`   DECIMAL(5,1) DEFAULT NULL            COMMENT '群众评议得分',
    `org_score`    DECIMAL(5,1) DEFAULT NULL            COMMENT '组织评定得分',
    `total_score`  DECIMAL(5,1) DEFAULT NULL            COMMENT '综合得分',
    `grade`        TINYINT      DEFAULT NULL            COMMENT '等次：1=优秀 2=合格 3=基本合格 4=不合格',
    `org_comment`  VARCHAR(1000) DEFAULT NULL           COMMENT '组织评定意见',
    `dispose`      VARCHAR(500) DEFAULT NULL            COMMENT '对不合格党员的处置意见',
    `create_time`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`detail_id`),
    UNIQUE KEY `uk_review_detail_person` (`review_id`, `person_id`),
    KEY `idx_review_detail_review` (`review_id`),
    KEY `idx_review_detail_person` (`person_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='民主评议党员明细表';

-- =====================================================================
--  十三、发展党员年度计划与指标（P1-4）
--  主表有 org_id + plan_year 的业务唯一约束且需要逻辑删除 → 同样用生成列模式。
-- =====================================================================
DROP TABLE IF EXISTS `dev_plan`;
CREATE TABLE `dev_plan` (
    `plan_id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '计划ID',
    `org_id`          BIGINT       NOT NULL                COMMENT '计划所属组织',
    `plan_year`       INT          NOT NULL                COMMENT '计划年度',
    `plan_count`      INT          NOT NULL DEFAULT 0      COMMENT '计划发展党员数',
    `activist_target` INT          DEFAULT NULL            COMMENT '入党积极分子培养目标数',
    `status`          TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=草稿 1=已下达 2=执行中 3=已完成',
    `issue_org_id`    BIGINT       DEFAULT NULL            COMMENT '下达组织（上级党委）',
    `issue_date`      DATE         DEFAULT NULL            COMMENT '下达日期',
    `description`     TEXT                                 COMMENT '计划说明',
    `del_flag`        TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志：0=存在 1=删除',
    `create_by`       VARCHAR(64)  DEFAULT NULL,
    `create_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `update_by`       VARCHAR(64)  DEFAULT NULL,
    `update_time`     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `org_year_alive`  VARCHAR(64) GENERATED ALWAYS AS (
        IF(`del_flag` = 0, CONCAT(`org_id`, '-', `plan_year`), NULL)) VIRTUAL
        COMMENT '生成列：仅存活行参与唯一约束',
    PRIMARY KEY (`plan_id`),
    UNIQUE KEY `uk_plan_org_year_alive` (`org_year_alive`),
    KEY `idx_plan_org`    (`org_id`),
    KEY `idx_plan_year`   (`plan_year`),
    KEY `idx_plan_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展党员年度计划表';

DROP TABLE IF EXISTS `dev_plan_quota`;
CREATE TABLE `dev_plan_quota` (
    `quota_id`    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '指标ID',
    `plan_id`     BIGINT       NOT NULL                COMMENT '计划ID',
    `org_id`      BIGINT       NOT NULL                COMMENT '被分配的组织',
    `quota_count` INT          NOT NULL DEFAULT 0      COMMENT '分配名额',
    `remark`      VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`quota_id`),
    KEY `idx_quota_plan` (`plan_id`),
    KEY `idx_quota_org`  (`org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发展党员计划指标分解表';

-- =====================================================================
--  十一、组织关系转接
-- =====================================================================

-- ---------------------------------------------------------------------
-- 组织关系转接单
-- 流程：发起(0=待提交) → 开具介绍信(1=已开具待接收) → 接收方接收(2=已接收)
--       接收方拒绝(3=已拒绝，党员仍在原组织) / 介绍信超期(4=已超期) / 发起方撤销(5=已撤销)
-- 注意：**开具介绍信时不改动 party_person.org_id**，只有「接收」才真正变更组织关系。
-- 审计列 5 个齐全，实体继承 BaseEntity。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS `party_transfer`;
CREATE TABLE `party_transfer` (
    `transfer_id`   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '转接ID',
    `transfer_no`   VARCHAR(64)  NOT NULL                COMMENT '转接单号，如 ZZ-2026-0001',
    `transfer_type` TINYINT      NOT NULL                COMMENT '类型：1=转出 2=转入 3=内部调整',
    `person_id`     BIGINT       NOT NULL                COMMENT '党员 person_id',
    `person_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '姓名（冗余）',
    `from_org_id`   BIGINT       DEFAULT NULL            COMMENT '原党组织（转出时必填）',
    `from_org_name` VARCHAR(150) DEFAULT NULL            COMMENT '原党组织名称（冗余）',
    `to_org_id`     BIGINT       DEFAULT NULL            COMMENT '目标党组织（转入时必填）',
    `to_org_name`   VARCHAR(150) DEFAULT NULL            COMMENT '目标党组织名称（冗余）',
    `reason`        VARCHAR(500) DEFAULT NULL            COMMENT '转接事由',
    `letter_no`     VARCHAR(64)  DEFAULT NULL            COMMENT '介绍信号',
    `letter_date`   DATE         DEFAULT NULL            COMMENT '介绍信开具日期',
    `valid_days`    INT          NOT NULL DEFAULT 90     COMMENT '有效期天数',
    `expire_date`   DATE         DEFAULT NULL            COMMENT '失效日期 = 开具日期 + 有效期',
    `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '状态：0=待提交 1=已开具(待接收) 2=已接收 3=已拒绝 4=已超期 5=已撤销',
    `transfer_date` DATE         DEFAULT NULL            COMMENT '实际转接完成日期',
    `handler_id`    BIGINT       DEFAULT NULL            COMMENT '经办人 person_id',
    `handler_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '经办人姓名（冗余）',
    `reject_reason` VARCHAR(500) DEFAULT NULL            COMMENT '拒绝原因',
    `file_id`       BIGINT       DEFAULT NULL            COMMENT '介绍信扫描件',
    `file_url`      VARCHAR(500) DEFAULT NULL            COMMENT '介绍信扫描件URL',
    `remark`        VARCHAR(500) DEFAULT NULL            COMMENT '备注',
    `del_flag`      TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志：0=存在 1=删除',
    `create_by`     VARCHAR(64)  DEFAULT NULL            COMMENT '创建者',
    `create_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by`     VARCHAR(64)  DEFAULT NULL            COMMENT '更新者',
    `update_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    -- 唯一约束只作用于存活行（见 03-数据库设计.md 2.2 方案 A）：
    -- 逻辑删除后生成列变 NULL，MySQL 唯一索引对多个 NULL 放行，墓碑行不占索引，
    -- 因此删掉一张转接单后仍可用同一个单号重建。
    `transfer_no_alive` VARCHAR(64) GENERATED ALWAYS AS (IF(`del_flag` = 0, `transfer_no`, NULL)) VIRTUAL
                        COMMENT '仅存活行的转接单号，用于唯一约束',
    PRIMARY KEY (`transfer_id`),
    UNIQUE KEY `uk_transfer_no_alive` (`transfer_no_alive`),
    KEY `idx_transfer_person`   (`person_id`),
    KEY `idx_transfer_status`   (`status`),
    KEY `idx_transfer_expire`   (`expire_date`),
    KEY `idx_transfer_from_org` (`from_org_id`),
    KEY `idx_transfer_to_org`   (`to_org_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='组织关系转接表';

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
--  建表完成，共 50 张表
--    系统域 12：sys_dept / sys_user / sys_role / sys_user_role / sys_role_dept /
--               sys_menu / sys_role_menu / sys_dict_type / sys_dict_data /
--               sys_login_log / sys_oper_log / sys_file
--    人员域  4：party_person / party_member_profile / party_position / party_group
--    发展域 14：dev_stage / dev_step / dev_applicant / dev_step_record / dev_material /
--               dev_talk / dev_vote / dev_political_review / dev_training /
--               dev_approval / dev_archive / dev_material_template /
--               dev_plan / dev_plan_quota
--    会议域  5：am_meeting / am_attendee / am_material / am_task / am_task_submit
--    换届域  2：org_election / org_election_candidate
--    教育域  2：edu_activity / edu_participant
--    党纪域  2：discipline_study / discipline_participant
--    党费域  2：party_dues_record / party_dues_use
--    服务域  1：member_service
--    评选域  2：excellent_selection / excellent_candidate
--    评议域  2：party_review / party_review_detail
--    转接域  1：party_transfer
-- =====================================================================
