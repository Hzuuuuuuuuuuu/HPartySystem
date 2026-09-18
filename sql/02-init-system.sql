-- =====================================================================
--  智慧党建管理系统 — 系统初始化脚本
--
--  ⚠️ 本文件是**可读的参考副本**，不参与执行。
--  数据库结构由 Flyway 管理，真正的迁移脚本在：
--    hparty-server/hparty-admin/src/main/resources/db/migration/
--  改结构请新增 V3__xxx.sql，不要改这里。
--  内容：发展阶段/步骤模板、角色、菜单、角色菜单关联、字典、管理员账号
--  执行顺序：01-schema.sql → 02-init-system.sql → 03-init-demo.sql
-- =====================================================================

USE `hparty`;
SET NAMES utf8mb4;

-- =====================================================================
--  一、发展党员阶段模板（5 个阶段）
-- =====================================================================
DELETE FROM `dev_stage`;
INSERT INTO `dev_stage` (`stage_code`, `stage_name`, `stage_order`, `description`) VALUES
('STAGE_1', '申请入党',                     1, '递交入党申请书、党组织派人谈话'),
('STAGE_2', '入党积极分子的确定和培养教育', 2, '推荐和确定入党积极分子、上级党委备案、指定培养联系人、培养教育考察'),
('STAGE_3', '发展对象的确定和考察',         3, '确定发展对象、报上级党委备案、确定入党介绍人、政治审查、集中培训'),
('STAGE_4', '预备党员的接收',               4, '支部委员会审查、上级党委预审、填写入党志愿书、支部大会讨论、上级党委派人谈话、上级党委审批、再上一级备案'),
('STAGE_5', '预备党员的教育考察和转正',     5, '编入党支部和党小组、入党宣誓、继续教育考察、提出转正申请、支部大会讨论、上级党委审批、材料归档');

-- =====================================================================
--  二、发展步骤模板（25 个步骤）
--  step_type    1=单次办理型  2=周期性考察型
--  handle_roles 办理角色编码，逗号分隔
--  rule_key     绑定 DevStepRule 策略实现，逗号分隔
-- =====================================================================
DELETE FROM `dev_step`;
INSERT INTO `dev_step`
(`step_code`, `step_name`, `stage_code`, `step_order`, `step_type`, `handle_roles`, `handle_org_type`,
 `need_vote`, `deadline_days`, `interval_days`, `interval_base_step`, `periodic_days`,
 `min_training_days`, `min_training_hours`, `rule_key`, `material_desc`, `description`, `is_branch`)
VALUES
-- ---------- 阶段一：申请入党 ----------
('STEP_01', '递交入党申请书', 'STAGE_1', 1, 1, 'APPLICANT', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'QUALIFICATION_RULE',
 '入党申请书（书面）',
 '条件：年满18周岁的中国公民；承认党的纲领和章程；愿意参加党的一个组织并在其中积极工作；愿意执行党的决议；按期交纳党费。要求：向工作、学习所在单位党组织提出；没有工作、学习单位或单位未建党组织的，向居住地党组织提出。注意：本人提出，书面申请。', 0),

('STEP_02', '党组织派人谈话', 'STAGE_1', 2, 1, 'BRANCH_SECRETARY,BRANCH_DEPUTY,ORG_COMMITTEE', 3,
 0, 30, NULL, NULL, NULL, NULL, NULL, 'DEADLINE_RULE',
 '谈话记录',
 '时间：收到入党申请书后1个月内。人员：党支部书记、副书记或组织委员。内容：了解入党申请人基本情况；介绍入党条件和程序；加强教育引导。', 0),

-- ---------- 阶段二：入党积极分子的确定和培养教育 ----------
('STEP_03', '推荐和确定入党积极分子', 'STAGE_2', 3, 1, 'BRANCH_COMMITTEE', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '推荐意见、支部委员会会议记录',
 '范围：已递交入党申请书且党组织已派人谈话的人员。方式：党员推荐、群团组织推优等方式。决定：支部委员会集体研究决定。注意：综合运用推荐结果，防止简单以票取人。', 0),

('STEP_04', '上级党委备案', 'STAGE_2', 4, 1, 'PARENT_ORG', 1,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '入党申请人基本情况、推荐和推优情况、支部委员会意见',
 '材料：入党申请人基本情况；推荐和推优情况；支部委员会意见等。要求：了解入党积极分子是否具备条件；手续是否齐全。', 0),

('STEP_05', '指定培养联系人', 'STAGE_2', 5, 1, 'BRANCH_COMMITTEE', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'QUALIFICATION_RULE',
 '培养联系人登记表',
 '数量：1-2名正式党员。任务：向入党积极分子介绍党的基本知识；了解入党积极分子的政治觉悟、道德品质、现实表现和家庭情况等，做好培养教育工作，引导入党积极分子端正入党动机；及时向党支部汇报入党积极分子情况；向党支部提出能否将入党积极分子列为发展对象的意见。', 0),

('STEP_06', '培养教育考察', 'STAGE_2', 6, 2, 'TRAINER,BRANCH_COMMITTEE', 3,
 0, NULL, 365, 'STEP_03', 180, NULL, NULL, 'PERIODIC_RULE,INTERVAL_RULE',
 '培养教育考察记录（每半年1次）',
 '方法：吸收入党积极分子听党课、参加党内有关活动、分配一定的社会工作、集中培训等。目的：使入党积极分子懂得党的性质、纲领、宗旨、组织原则、纪律、党员的义务和权利，端正入党动机，确立为共产主义事业奋斗终身的信念。要求：党支部每半年对入党积极分子进行1次考察。注意：入党积极分子工作、学习单位（居住地）发生变动，应及时报告原单位（居住地）党组织；原单位（居住地）党组织应及时转交材料；接收单位党组织认真审查材料、做好接续培养，培养教育时间可连续计算。', 0),

-- ---------- 阶段三：发展对象的确定和考察 ----------
('STEP_07', '确定发展对象', 'STAGE_3', 7, 1, 'BRANCH_COMMITTEE', 3,
 0, NULL, 365, 'STEP_03', NULL, NULL, NULL, 'INTERVAL_RULE',
 '支部委员会会议记录、听取意见记录',
 '条件：经过1年以上培养教育和考察；基本具备党员条件。要求：听取党小组、培养联系人、党员和群众意见。确定：支部委员会讨论同意，确定发展对象人选。', 0),

('STEP_08', '报上级党委备案', 'STAGE_3', 8, 1, 'PARENT_ORG', 1,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '发展对象备案表',
 '要求：认真审查，提出意见。注意：同意后列为发展对象。', 0),

('STEP_09', '确定入党介绍人', 'STAGE_3', 9, 1, 'BRANCH_COMMITTEE', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'QUALIFICATION_RULE',
 '入党介绍人登记表',
 '数量：2名正式党员。方式：一般由培养联系人担任，也可由党组织指定。要求：入党介绍人认真完成培养、教育任务。注意：受留党察看处分、尚未恢复党员权利的党员，不能作入党介绍人。', 0),

('STEP_10', '进行政治审查', 'STAGE_3', 10, 1, 'BRANCH_COMMITTEE', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '政治审查结论性材料',
 '内容：对党的理论和路线、方针、政策的态度；政治历史和在重大政治斗争中的表现；遵纪守法和遵守社会公德情况；直系亲属和与本人关系密切的主要社会关系的政治情况。方法：同本人谈话、查阅档案资料、找有关单位和人员了解情况以及必要的函调或外调。对流动人员中的发展对象还应征求户籍所在地和居住地基层党组织的意见。要求：政治审查必须严肃认真、实事求是，注重本人的一贯表现。审查情况形成结论性材料。注意：未经政治审查或政治审查不合格的，不能发展入党。', 0),

('STEP_11', '开展集中培训', 'STAGE_3', 11, 1, 'PARENT_ORG,COUNTY_ORG', 1,
 0, NULL, NULL, NULL, NULL, 3, 24, 'TRAINING_HOUR_RULE',
 '培训结业证明、签到表',
 '主体：基层党委或县级党委组织部门。时间：不少于3天或不少于24学时。注意：未经培训的，除个别特殊情况外，不能发展入党。', 0),

-- ---------- 阶段四：预备党员的接收 ----------
('STEP_12', '支部委员会审查', 'STAGE_4', 12, 1, 'BRANCH_COMMITTEE', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '审查意见、群众意见汇总',
 '要求：征求党员和群众的意见；对发展对象进行严格审查；集体讨论是否合格。', 0),

('STEP_13', '上级党委预审', 'STAGE_4', 13, 1, 'PARENT_ORG', 1,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'QUALIFICATION_RULE',
 '预审意见、入党志愿书（发放）',
 '方式：审查发展对象条件、培养教育情况等；根据需要，听取执法执纪等部门意见。要求：审查结果书面通知党支部；向审查合格的发展对象发放《中国共产党入党志愿书》。注意：发展对象未来3个月内将离开工作、学习单位的，一般不办理接收预备党员手续。', 0),

('STEP_14', '填写入党志愿书', 'STAGE_4', 14, 1, 'APPLICANT', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '《中国共产党入党志愿书》',
 '要求：在入党介绍人指导下，由本人按照要求如实填写。', 0),

('STEP_15', '支部大会讨论', 'STAGE_4', 15, 1, 'BRANCH_ASSEMBLY', 3,
 1, NULL, NULL, NULL, NULL, NULL, NULL, 'VOTE_RULE',
 '支部大会决议、表决记录、会议记录',
 '程序：(1)发展对象汇报个人情况；(2)入党介绍人介绍发展对象有关情况、表明意见；(3)支部委员会报告审查情况；(4)与会党员充分讨论、投票表决。注意：有表决权的到会人数必须超过应到会有表决权人数的半数，才能开会；赞成人数超过应到会有表决权的正式党员的半数，才能通过。讨论两个以上发展对象入党时，要逐个讨论和表决。', 0),

('STEP_16', '上级党委派人谈话', 'STAGE_4', 16, 1, 'PARENT_ORG', 1,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '谈话记录（填入入党志愿书）',
 '时间：党委审批前。人员：党委委员或组织员。目的：作进一步了解，并帮助发展对象提高对党的认识。要求：谈话人应当将谈话情况和自己对发展对象能否入党的意见，如实填写在《中国共产党入党志愿书》上，并向党委汇报。', 0),

('STEP_17', '上级党委审批', 'STAGE_4', 17, 1, 'PARENT_ORG', 1,
 0, 90, NULL, NULL, NULL, NULL, NULL, 'DEADLINE_RULE',
 '审批意见、党委会会议记录',
 '时间：3个月内。要求：审批结果及时通知党支部。支部书记应当同本人谈话，并将审批结果通知本人。注意：党员的党龄从预备期满转为正式党员之日算起。', 0),

('STEP_18', '再上一级党委组织部门备案', 'STAGE_4', 18, 1, 'GRANDPARENT_ORG', 1,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '预备党员备案表',
 '目的：掌握预备党员结构、分布、质量等情况，发现问题，及时解决。', 0),

-- ---------- 阶段五：预备党员的教育考察和转正 ----------
('STEP_19', '编入党支部和党小组', 'STAGE_5', 19, 1, 'BRANCH_COMMITTEE', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL,
 '党小组分配表',
 '要求：及时编入；继续进行教育和考察。', 0),

('STEP_20', '入党宣誓', 'STAGE_5', 20, 1, 'PARENT_ORG,BRANCH_COMMITTEE', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '宣誓活动记录、照片',
 '组织：基层党委或党支部（党总支）。程序：(1)奏《国际歌》；(2)党组织负责同志致辞；(3)预备党员宣誓；(4)参加宣誓的预备党员代表发言；(5)党组织负责同志讲话、提出要求。要求：在正式场合举行；严肃认真；庄重简朴；严密紧凑。', 0),

('STEP_21', '继续教育考察', 'STAGE_5', 21, 2, 'BRANCH_COMMITTEE', 3,
 0, NULL, 365, 'STEP_20', 90, NULL, NULL, 'PERIODIC_RULE,PROBATION_PERIOD_RULE',
 '继续教育考察记录',
 '方式：参加党的组织生活、听本人汇报、个别谈心、集中培训、实践锻炼等。时间：预备期为1年。', 0),

('STEP_22', '提出转正申请', 'STAGE_5', 22, 1, 'APPLICANT', 3,
 0, NULL, 365, 'STEP_20', NULL, NULL, NULL, 'PROBATION_PERIOD_RULE',
 '转正申请书',
 '要求：预备期满，书面提出申请。', 0),

('STEP_23', '支部大会讨论', 'STAGE_5', 23, 1, 'BRANCH_ASSEMBLY', 3,
 1, NULL, NULL, NULL, NULL, NULL, NULL, 'VOTE_RULE,BRANCH_RULE',
 '支部大会决议、表决记录',
 '准备：党小组提出意见；党支部征求党员和群众的意见；支部委员会审查。程序：参照接收预备党员的程序。结果：(1)认真履行党员义务、具备党员条件的，应当按期转为正式党员；(2)需要继续考察和教育的，可以延长1次预备期，延长不能少于半年，最长不超过1年；(3)不履行党员义务、不具备党员条件的，应当取消预备党员资格。', 1),

('STEP_24', '上级党委审批', 'STAGE_5', 24, 1, 'PARENT_ORG', 1,
 0, 90, NULL, NULL, NULL, NULL, NULL, 'DEADLINE_RULE',
 '审批意见',
 '时间：3个月内。要求：审批结果及时通知党支部。支部书记应当同本人谈话，并将审批结果通知本人。注意：党员的党龄从预备期满转为正式党员之日算起。', 0),

('STEP_25', '材料归档', 'STAGE_5', 25, 1, 'BRANCH_COMMITTEE,COUNTY_ORG', 3,
 0, NULL, NULL, NULL, NULL, NULL, NULL, 'MATERIAL_REQUIRED_RULE',
 '归档材料清单',
 '内容：《中国共产党入党志愿书》、入党申请书、政治审查材料、转正申请书、培养教育考察材料。要求：有人事档案的，存入本人人事档案；无人事档案的，建立党员档案，由所在党委或县级党委组织部门保存。', 0);

-- =====================================================================
--  三、角色
--  data_scope: 1=全部 2=本级 3=本级及以下 4=仅本人 5=自定义
-- =====================================================================
DELETE FROM `sys_role_menu`;
DELETE FROM `sys_role`;
INSERT INTO `sys_role` (`role_id`, `role_name`, `role_key`, `role_sort`, `data_scope`, `is_builtin`, `status`, `remark`) VALUES
(1,  '超级管理员', 'SUPER_ADMIN',      1, 1, 1, 1, '系统管理员，拥有全部权限'),
(2,  '党委书记',   'PARTY_SECRETARY',  2, 3, 1, 1, '可查看本级及下辖全部党组织数据'),
(3,  '支部书记',   'BRANCH_SECRETARY', 3, 2, 1, 1, '本支部全部数据'),
(4,  '支部副书记', 'BRANCH_DEPUTY',    4, 2, 1, 1, '本支部全部数据'),
(5,  '组织委员',   'ORG_COMMITTEE',    5, 2, 1, 1, '发展党员主要办理人'),
(6,  '宣传委员',   'PROP_COMMITTEE',   6, 2, 1, 1, '本支部全部数据'),
(7,  '纪检委员',   'DISC_COMMITTEE',   7, 2, 1, 1, '本支部全部数据'),
(8,  '党小组长',   'GROUP_LEADER',     8, 2, 1, 1, '本党小组数据'),
(9,  '普通党员',   'PARTY_MEMBER',     9, 4, 1, 1, '仅本人相关数据'),
(10, '入党申请人', 'APPLICANT',       10, 4, 1, 1, '发展党员模块的当事人，仅本人数据');

-- =====================================================================
--  四、菜单
--  menu_type: M=目录 C=菜单 F=按钮
-- =====================================================================
DELETE FROM `sys_menu`;
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
-- ============ 顶层菜单 ============
(1,  0, '三会一课',       1, '/meeting',            NULL, 'M', 1, 1, NULL, 'TeamOutlined'),
(2,  0, '主题党日',       2, '/theme-party-day',    'partyday/index', 'C', 1, 1, 'partyday:list', 'FlagOutlined'),
(3,  0, '发展党员',       3, '/develop',            NULL, 'M', 1, 1, NULL, 'UserAddOutlined'),
(4,  0, '党组织换届',     4, '/org-election',       'election/index', 'C', 1, 1, 'election:list', 'SwapOutlined'),
(5,  0, '组织生活会',     5, '/org-life',           NULL, 'M', 1, 1, NULL, 'CommentOutlined'),
(6,  0, '党员教育管理',   6, '/member-education',   'education/index', 'C', 1, 1, 'education:list', 'ReadOutlined'),
(7,  0, '党组织基本情况', 7, '/org-info',           NULL, 'M', 1, 1, NULL, 'ApartmentOutlined'),
(8,  0, '党费收缴及使用', 8, '/party-dues',         'dues/index', 'C', 1, 1, 'dues:list', 'MoneyCollectOutlined'),
(9,  0, '党员服务',       9, '/member-service',     'service/index', 'C', 1, 1, 'service:list', 'HeartOutlined'),
(10, 0, '党纪学习教育板块', 10, '/discipline-study','discipline/index', 'C', 1, 1, 'discipline:list', 'SafetyOutlined'),
(11, 0, '先优评选',       11, '/excellent-selection','excellent/index', 'C', 1, 1, 'excellent:list', 'TrophyOutlined'),
(12, 0, '系统管理',       99, '/system',            NULL, 'M', 1, 1, NULL, 'SettingOutlined'),

-- ============ 三会一课 ============
-- 注意：component 必须是**纯组件路径**。动态路由按组件文件名解析，
-- 若在这里塞查询串（如 'meeting/index?type=X'）会找不到文件、路由不注册，菜单变死链。
-- 三会一课四个菜单共用 meeting/index，靠**路径末段**区分要展示的会议类型。
(101, 1, '党员大会',      1, 'member-assembly',  'meeting/index', 'C', 1, 1, 'meeting:list',  'UsergroupAddOutlined'),
(102, 1, '支部委员会',    2, 'branch-committee', 'meeting/index', 'C', 1, 1, 'meeting:list',  'SolutionOutlined'),
(103, 1, '党小组会',      3, 'party-group',      'meeting/index', 'C', 1, 1, 'meeting:list',  'ClusterOutlined'),
(104, 1, '党课',          4, 'party-lecture',    'meeting/index', 'C', 1, 1, 'meeting:list',  'BookOutlined'),
(105, 1, '活动任务通知',  5, 'task',             'meeting/task',  'C', 1, 1, 'task:list',     'NotificationOutlined'),

-- ============ 发展党员 ============
(301, 3, '发展党员',      1, 'applicant',   'develop/applicant',  'C', 1, 1, 'develop:applicant:list', 'IdcardOutlined'),
(302, 3, '阶段统计',      2, 'statistics',  'develop/statistics', 'C', 1, 1, 'develop:stat:list',      'BarChartOutlined'),
(303, 3, '流程配置',      3, 'step',        'develop/step',       'C', 1, 1, 'develop:step:list',      'PartitionOutlined'),

-- 发展党员-按钮
(3011, 301, '新增发展对象', 1, NULL, NULL, 'F', 1, 1, 'develop:applicant:add',    NULL),
(3012, 301, '编辑发展对象', 2, NULL, NULL, 'F', 1, 1, 'develop:applicant:edit',   NULL),
(3013, 301, '删除发展对象', 3, NULL, NULL, 'F', 1, 1, 'develop:applicant:remove', NULL),
(3014, 301, '办理步骤',     4, NULL, NULL, 'F', 1, 1, 'develop:applicant:handle', NULL),
(3015, 301, '查看详情',     5, NULL, NULL, 'F', 1, 1, 'develop:applicant:detail', NULL),
(3016, 301, '导出',         6, NULL, NULL, 'F', 1, 1, 'develop:applicant:export', NULL),

-- ============ 组织生活会 ============
(501, 5, '通知',           1, 'notice',         'orglife/index', 'C', 1, 1, 'orglife:list', 'BellOutlined'),
(502, 5, '会前学习',       2, 'pre-study',      'orglife/index', 'C', 1, 1, 'orglife:list', 'BookOutlined'),
(503, 5, '记录',           3, 'record',         'orglife/index', 'C', 1, 1, 'orglife:list', 'EditOutlined'),
(504, 5, '党员剖析材料',   4, 'analysis',       'orglife/index', 'C', 1, 1, 'orglife:list', 'FileTextOutlined'),
(505, 5, '党员自评材料',   5, 'self-eval',      'orglife/index', 'C', 1, 1, 'orglife:list', 'FileDoneOutlined'),
(506, 5, '其它内容',       6, 'other',          'orglife/index', 'C', 1, 1, 'orglife:list', 'AppstoreOutlined'),
(507, 5, '问题清单',       7, 'problem-list',   'orglife/index', 'C', 1, 1, 'orglife:list', 'QuestionCircleOutlined'),
(508, 5, '整改清单',       8, 'rectify-list',   'orglife/index', 'C', 1, 1, 'orglife:list', 'CalendarOutlined'),
(509, 5, '会议记录',       9, 'minutes',        'orglife/index', 'C', 1, 1, 'orglife:list', 'FileWordOutlined'),
(510, 5, '民主评议党员',  10, 'democratic-eval','orglife/index', 'C', 1, 1, 'orglife:list', 'AuditOutlined'),
(511, 5, '情况报告',      11, 'report',         'orglife/index', 'C', 1, 1, 'orglife:list', 'LineChartOutlined'),

-- ============ 党组织基本情况 ============
(701, 7, '组织架构',   1, 'tree',        'orginfo/tree',      'C', 1, 1, 'orginfo:tree',       'ApartmentOutlined'),
(702, 7, '党员名册',   2, 'member-list', 'orginfo/member',    'C', 1, 1, 'orginfo:member:list','TeamOutlined'),
(703, 7, '党组织名册', 3, 'org-list',    'orginfo/orglist',   'C', 1, 1, 'orginfo:org:list',   'BankOutlined'),

-- ============ 系统管理 ============
(1201, 12, '用户管理',   1, 'user',   'system/user/index',   'C', 1, 1, 'system:user:list',   'UserOutlined'),
(1202, 12, '角色管理',   2, 'role',   'system/role/index',   'C', 1, 1, 'system:role:list',   'TeamOutlined'),
(1203, 12, '菜单管理',   3, 'menu',   'system/menu/index',   'C', 1, 1, 'system:menu:list',   'MenuOutlined'),
(1204, 12, '党组织管理', 4, 'dept',   'system/dept/index',   'C', 1, 1, 'system:dept:list',   'ClusterOutlined'),
(1205, 12, '字典管理',   5, 'dict',   'system/dict/index',   'C', 1, 1, 'system:dict:list',   'BookOutlined'),
(1206, 12, '登录日志',   6, 'loginlog','system/log/login',   'C', 1, 1, 'system:loginlog:list','LoginOutlined'),
(1207, 12, '操作日志',   7, 'operlog', 'system/log/oper',    'C', 1, 1, 'system:operlog:list','FileSearchOutlined'),

-- 系统管理-按钮
(12011, 1201, '用户新增', 1, NULL, NULL, 'F', 1, 1, 'system:user:add',      NULL),
(12012, 1201, '用户修改', 2, NULL, NULL, 'F', 1, 1, 'system:user:edit',     NULL),
(12013, 1201, '用户删除', 3, NULL, NULL, 'F', 1, 1, 'system:user:remove',   NULL),
(12014, 1201, '重置密码', 4, NULL, NULL, 'F', 1, 1, 'system:user:resetPwd', NULL),
(12021, 1202, '角色新增', 1, NULL, NULL, 'F', 1, 1, 'system:role:add',      NULL),
(12022, 1202, '角色修改', 2, NULL, NULL, 'F', 1, 1, 'system:role:edit',     NULL),
(12023, 1202, '角色删除', 3, NULL, NULL, 'F', 1, 1, 'system:role:remove',   NULL),
(12031, 1203, '菜单新增', 1, NULL, NULL, 'F', 1, 1, 'system:menu:add',      NULL),
(12032, 1203, '菜单修改', 2, NULL, NULL, 'F', 1, 1, 'system:menu:edit',     NULL),
(12033, 1203, '菜单删除', 3, NULL, NULL, 'F', 1, 1, 'system:menu:remove',   NULL),
(12041, 1204, '组织新增', 1, NULL, NULL, 'F', 1, 1, 'system:dept:add',      NULL),
(12042, 1204, '组织修改', 2, NULL, NULL, 'F', 1, 1, 'system:dept:edit',     NULL),
(12043, 1204, '组织删除', 3, NULL, NULL, 'F', 1, 1, 'system:dept:remove',   NULL),

-- ============ 三会一课 按钮 ============
(1111, 101, '会议新增', 1, NULL, NULL, 'F', 1, 1, 'meeting:add',    NULL),
(1112, 101, '会议修改', 2, NULL, NULL, 'F', 1, 1, 'meeting:edit',   NULL),
(1113, 101, '会议删除', 3, NULL, NULL, 'F', 1, 1, 'meeting:remove', NULL),
(1114, 105, '任务发布', 1, NULL, NULL, 'F', 1, 1, 'task:add',       NULL),
(1115, 105, '任务修改', 2, NULL, NULL, 'F', 1, 1, 'task:edit',      NULL),
(1116, 105, '任务删除', 3, NULL, NULL, 'F', 1, 1, 'task:remove',    NULL),
(1117, 105, '上传资料', 4, NULL, NULL, 'F', 1, 1, 'task:submit',    NULL),

-- ============ 主题党日 按钮 ============
(2011, 2, '新增', 1, NULL, NULL, 'F', 1, 1, 'partyday:add',    NULL),
(2012, 2, '修改', 2, NULL, NULL, 'F', 1, 1, 'partyday:edit',   NULL),
(2013, 2, '删除', 3, NULL, NULL, 'F', 1, 1, 'partyday:remove', NULL),

-- ============ 党组织换届 按钮 ============
(4011, 4, '新增换届', 1, NULL, NULL, 'F', 1, 1, 'election:add',    NULL),
(4012, 4, '修改换届', 2, NULL, NULL, 'F', 1, 1, 'election:edit',   NULL),
(4013, 4, '删除换届', 3, NULL, NULL, 'F', 1, 1, 'election:remove', NULL),

-- ============ 组织生活会 按钮 ============
(5111, 501, '新增材料', 1, NULL, NULL, 'F', 1, 1, 'orglife:add',    NULL),
(5112, 501, '修改材料', 2, NULL, NULL, 'F', 1, 1, 'orglife:edit',   NULL),
(5113, 501, '删除材料', 3, NULL, NULL, 'F', 1, 1, 'orglife:remove', NULL),

-- ============ 党员教育管理 按钮 ============
(6011, 6, '新增活动', 1, NULL, NULL, 'F', 1, 1, 'education:add',    NULL),
(6012, 6, '修改活动', 2, NULL, NULL, 'F', 1, 1, 'education:edit',   NULL),
(6013, 6, '删除活动', 3, NULL, NULL, 'F', 1, 1, 'education:remove', NULL),

-- ============ 党员名册 按钮 ============
(7021, 702, '新增人员', 1, NULL, NULL, 'F', 1, 1, 'system:person:add',    NULL),
(7022, 702, '修改人员', 2, NULL, NULL, 'F', 1, 1, 'system:person:edit',   NULL),
(7023, 702, '删除人员', 3, NULL, NULL, 'F', 1, 1, 'system:person:remove', NULL),
(7024, 702, '导出名册', 4, NULL, NULL, 'F', 1, 1, 'system:person:export', NULL),

-- ============ 党费 按钮 ============
(8011, 8, '新增缴纳记录', 1, NULL, NULL, 'F', 1, 1, 'dues:add',      NULL),
(8012, 8, '修改缴纳记录', 2, NULL, NULL, 'F', 1, 1, 'dues:edit',     NULL),
(8013, 8, '删除缴纳记录', 3, NULL, NULL, 'F', 1, 1, 'dues:remove',   NULL),
(8014, 8, '批量生成账单', 4, NULL, NULL, 'F', 1, 1, 'dues:generate', NULL),
(8021, 8, '新增使用记录', 5, NULL, NULL, 'F', 1, 1, 'dues:use:add',    NULL),
(8022, 8, '删除使用记录', 6, NULL, NULL, 'F', 1, 1, 'dues:use:remove', NULL),

-- ============ 党员服务 按钮 ============
(9011, 9, '新增服务', 1, NULL, NULL, 'F', 1, 1, 'service:add',    NULL),
(9012, 9, '修改服务', 2, NULL, NULL, 'F', 1, 1, 'service:edit',   NULL),
(9013, 9, '删除服务', 3, NULL, NULL, 'F', 1, 1, 'service:remove', NULL),

-- ============ 党纪学习教育 按钮 ============
(10011, 10, '新增学习', 1, NULL, NULL, 'F', 1, 1, 'discipline:add',    NULL),
(10012, 10, '修改学习', 2, NULL, NULL, 'F', 1, 1, 'discipline:edit',   NULL),
(10013, 10, '删除学习', 3, NULL, NULL, 'F', 1, 1, 'discipline:remove', NULL),

-- ============ 先优评选 按钮 ============
(11011, 11, '新增评选', 1, NULL, NULL, 'F', 1, 1, 'excellent:add',    NULL),
(11012, 11, '修改评选', 2, NULL, NULL, 'F', 1, 1, 'excellent:edit',   NULL),
(11013, 11, '删除评选', 3, NULL, NULL, 'F', 1, 1, 'excellent:remove', NULL),

-- ============ 字典管理 按钮（补齐，便于去掉代码里的 OR 回退） ============
(12051, 1205, '字典新增', 1, NULL, NULL, 'F', 1, 1, 'system:dict:add',    NULL),
(12052, 1205, '字典修改', 2, NULL, NULL, 'F', 1, 1, 'system:dict:edit',   NULL),
(12053, 1205, '字典删除', 3, NULL, NULL, 'F', 1, 1, 'system:dict:remove', NULL),

-- ============ 日志管理 按钮（日志属系统审计信息，仅超级管理员可见） ============
(12061, 1206, '删除登录日志', 1, NULL, NULL, 'F', 1, 1, 'system:loginlog:remove', NULL),
(12062, 1206, '清空登录日志', 2, NULL, NULL, 'F', 1, 1, 'system:loginlog:clean',  NULL),
(12071, 1207, '删除操作日志', 1, NULL, NULL, 'F', 1, 1, 'system:operlog:remove',  NULL),
(12072, 1207, '清空操作日志', 2, NULL, NULL, 'F', 1, 1, 'system:operlog:clean',   NULL);

-- =====================================================================
--  四之二、「我的待办」与「组织关系转接」菜单（追加，不改动上面的已有行）
--  菜单 ID 取 13 / 14 与 1411~1414，避开已用号段（1~12 / 101~12072）。
--  13/14 都小于 1200，因此「党委书记(2)」的 menu_id < 1200 规则自动覆盖；
--  「支部书记(3)/副书记(4)」的 NOT IN 规则同样自动覆盖，
--  只有组织委员(5) 及以下角色需要显式补授权（见下一节）。
-- =====================================================================
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
-- 我的待办：聚合四个业务域的待办，所有登录用户都能看自己的。
-- component 必须是纯组件路径（不带查询串），否则动态路由按文件名匹配不到、菜单变死链。
(13, 0, '我的待办',     12, '/my-todo',        'todo/index',     'C', 1, 1, NULL,           'BellOutlined'),
(14, 0, '组织关系转接', 13, '/party-transfer', 'transfer/index', 'C', 1, 1, 'transfer:list', 'SwapOutlined'),

-- 组织关系转接-按钮
(1411, 14, '发起转接', 1, NULL, NULL, 'F', 1, 1, 'transfer:add',    NULL),
(1412, 14, '修改转接', 2, NULL, NULL, 'F', 1, 1, 'transfer:edit',   NULL),
(1413, 14, '删除转接', 3, NULL, NULL, 'F', 1, 1, 'transfer:remove', NULL),
(1414, 14, '办理转接', 4, NULL, NULL, 'F', 1, 1, 'transfer:handle', NULL);

-- =====================================================================
--  五、角色-菜单关联
-- =====================================================================

-- 超级管理员：全部菜单
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) SELECT 1, `menu_id` FROM `sys_menu`;

-- 党委书记(2)：除系统管理外全部
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 2, `menu_id` FROM `sys_menu` WHERE `menu_id` < 1200 OR `menu_id` IN (1204);

-- 支部书记(3) / 支部副书记(4)：党建业务全部 + 组织管理，但不含系统管理（用户/角色/菜单/字典/日志）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 3, `menu_id` FROM `sys_menu`
WHERE `menu_id` NOT IN (12, 1201, 1202, 1203, 1205, 1206, 1207, 12051, 12052, 12053)
UNION ALL
SELECT 4, `menu_id` FROM `sys_menu`
WHERE `menu_id` NOT IN (12, 1201, 1202, 1203, 1205, 1206, 1207, 12051, 12052, 12053);

-- 组织委员(5)：发展党员全权限 + 三会一课 + 组织生活会
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(5, 1), (5, 101), (5, 102), (5, 103), (5, 104), (5, 105),
(5, 3), (5, 301), (5, 302), (5, 303),
(5, 3011), (5, 3012), (5, 3013), (5, 3014), (5, 3015), (5, 3016),
(5, 5), (5, 501), (5, 502), (5, 503), (5, 504), (5, 505), (5, 506),
(5, 507), (5, 508), (5, 509), (5, 510), (5, 511),
(5, 7), (5, 701), (5, 702), (5, 703), (5, 2), (5, 8), (5, 9), (5, 10), (5, 11), (5, 4), (5, 6);

-- 宣传委员(6) / 纪检委员(7)：只读为主
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(6, 1), (6, 101), (6, 102), (6, 103), (6, 104), (6, 105),
(6, 2), (6, 3), (6, 301), (6, 302), (6, 4), (6, 5),
(6, 501), (6, 502), (6, 503), (6, 506), (6, 509), (6, 510), (6, 511),
(6, 6), (6, 7), (6, 701), (6, 702), (6, 703), (6, 8), (6, 9), (6, 10), (6, 11),
(7, 1), (7, 101), (7, 102), (7, 103), (7, 104), (7, 105),
(7, 2), (7, 3), (7, 301), (7, 4), (7, 5),
(7, 501), (7, 502), (7, 503), (7, 504), (7, 505), (7, 507), (7, 508),
(7, 509), (7, 510), (7, 511),
(7, 6), (7, 7), (7, 701), (7, 702), (7, 8), (7, 9), (7, 10), (7, 11);

-- 党小组长(8)：三会一课 + 组织生活会 + 组织信息
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(8, 1), (8, 101), (8, 103), (8, 104), (8, 105),
(8, 2), (8, 3), (8, 301), (8, 5), (8, 503), (8, 509), (8, 510),
(8, 7), (8, 701), (8, 702), (8, 9);

-- 普通党员(9) / 入党申请人(10)：仅本人相关，只读
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(9, 1), (9, 101), (9, 103), (9, 104), (9, 2), (9, 3), (9, 301), (9, 7), (9, 701), (9, 9),
(10, 3), (10, 301);

-- 我的待办(13)：**所有登录角色**都要能看到自己的待办，一个都不能漏。
-- 角色 1/2/3/4 已被上面的通用规则覆盖（1=全量、2=menu_id<1200、3/4=NOT IN 黑名单），
-- 这里补齐 5~10，避免出现「普通党员没有待办入口」这种漏授。
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(5, 13), (6, 13), (7, 13), (8, 13), (9, 13), (10, 13);

-- 组织关系转接(14)：组织委员也参与办理转接，补授权（3/4 已被 NOT IN 规则覆盖）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(5, 14), (5, 1411), (5, 1412), (5, 1413), (5, 1414);

-- =====================================================================
--  六、字典
-- =====================================================================
DELETE FROM `sys_dict_data`;
DELETE FROM `sys_dict_type`;
INSERT INTO `sys_dict_type` (`dict_id`, `dict_name`, `dict_type`, `status`, `remark`) VALUES
(1,  '用户性别',     'sys_user_sex',       1, '用户性别列表'),
(2,  '系统开关',     'sys_normal_disable', 1, '系统开关列表'),
(3,  '党组织类型',   'party_org_type',     1, '党委/党总支/党支部/党小组'),
(4,  '人员状态',     'party_member_status',1, '人员发展阶段状态'),
(5,  '政治面貌',     'party_political',    1, '政治面貌列表'),
(6,  '党内职务',     'party_position',     1, '党内职务列表'),
(7,  '会议类型',     'am_meeting_type',    1, '三会一课及组织生活会议类型'),
(8,  '会议材料分类', 'am_material_category',1,'组织生活会材料分类'),
(9,  '办理结论',     'dev_step_result',    1, '发展党员步骤办理结论'),
(10, '材料类型',     'dev_material_type',  1, '发展党员材料类型'),
(11, '学历',         'sys_education',      1, '学历列表'),
(12, '民族',         'sys_nation',         1, '民族列表');

INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `list_class`, `is_default`) VALUES
-- 用户性别
(1, '男', '1', 'sys_user_sex', 'primary', 1),
(2, '女', '2', 'sys_user_sex', 'danger',  0),
-- 系统开关
(1, '正常', '1', 'sys_normal_disable', 'primary', 1),
(2, '停用', '0', 'sys_normal_disable', 'danger',  0),
-- 党组织类型
(1, '党委',   '1', 'party_org_type', 'danger',  0),
(2, '党总支', '2', 'party_org_type', 'warning', 0),
(3, '党支部', '3', 'party_org_type', 'primary', 1),
(4, '党小组', '4', 'party_org_type', 'info',    0),
-- 人员状态
(1, '群众',       '0', 'party_member_status', 'info',    0),
(2, '入党申请人', '1', 'party_member_status', 'warning', 0),
(3, '入党积极分子','2','party_member_status', 'warning', 0),
(4, '发展对象',   '3', 'party_member_status', 'primary', 0),
(5, '预备党员',   '4', 'party_member_status', 'primary', 0),
(6, '正式党员',   '5', 'party_member_status', 'success', 1),
(7, '流动党员',   '6', 'party_member_status', 'info',    0),
-- 政治面貌
(1, '中共党员',     '01', 'party_political', 'danger',  0),
(2, '中共预备党员', '02', 'party_political', 'warning', 0),
(3, '共青团员',     '03', 'party_political', 'primary', 0),
(4, '民主党派',     '04', 'party_political', 'info',    0),
(5, '群众',         '05', 'party_political', 'info',    1),
-- 党内职务
(1, '书记',     'SECRETARY',      'party_position', 'danger',  0),
(2, '副书记',   'DEPUTY',         'party_position', 'warning', 0),
(3, '组织委员', 'ORG_COMMITTEE',  'party_position', 'primary', 0),
(4, '宣传委员', 'PROP_COMMITTEE', 'party_position', 'primary', 0),
(5, '纪检委员', 'DISC_COMMITTEE', 'party_position', 'primary', 0),
(6, '党小组长', 'GROUP_LEADER',   'party_position', 'info',    0),
-- 会议类型
(1, '党员大会',   'MEMBER_ASSEMBLY',  'am_meeting_type', 'danger',  0),
(2, '支部委员会', 'BRANCH_COMMITTEE', 'am_meeting_type', 'warning', 0),
(3, '党小组会',   'PARTY_GROUP',      'am_meeting_type', 'primary', 0),
(4, '党课',       'PARTY_LECTURE',    'am_meeting_type', 'success', 0),
(5, '主题党日',   'THEME_PARTY_DAY',  'am_meeting_type', 'info',    0),
(6, '组织生活会', 'ORG_LIFE',         'am_meeting_type', 'danger',  0),
-- 会议材料分类
(1,  '通知',         'NOTICE',           'am_material_category', 'primary', 0),
(2,  '会前学习',     'PRE_STUDY',        'am_material_category', 'info',    0),
(3,  '记录',         'RECORD',           'am_material_category', 'info',    0),
(4,  '党员剖析材料', 'ANALYSIS',         'am_material_category', 'warning', 0),
(5,  '党员自评材料', 'SELF_EVAL',        'am_material_category', 'warning', 0),
(6,  '其它内容',     'OTHER',            'am_material_category', 'info',    0),
(7,  '问题清单',     'PROBLEM_LIST',     'am_material_category', 'danger',  0),
(8,  '整改清单',     'RECTIFY_LIST',     'am_material_category', 'danger',  0),
(9,  '会议记录',     'MEETING_MINUTES',  'am_material_category', 'info',    0),
(10, '民主评议党员', 'DEMOCRATIC_EVAL',  'am_material_category', 'success', 0),
(11, '情况报告',     'SITUATION_REPORT', 'am_material_category', 'primary', 0),
-- 办理结论
(1, '通过',       '1', 'dev_step_result', 'success', 0),
(2, '驳回',       '2', 'dev_step_result', 'warning', 0),
(3, '不通过',     '3', 'dev_step_result', 'danger',  0),
(4, '延长预备期', '4', 'dev_step_result', 'warning', 0),
(5, '取消资格',   '5', 'dev_step_result', 'danger',  0),
-- 材料类型
(1, '入党申请书',   'APPLY_BOOK',       'dev_material_type', 'danger',  0),
(2, '思想汇报',     'THOUGHT_REPORT',   'dev_material_type', 'primary', 0),
(3, '政治审查材料', 'POLITICAL_REVIEW', 'dev_material_type', 'warning', 0),
(4, '入党志愿书',   'VOLUNTEER_BOOK',   'dev_material_type', 'danger',  0),
(5, '转正申请书',   'REGULAR_APPLY',    'dev_material_type', 'warning', 0),
(6, '培训证明',     'TRAINING_CERT',    'dev_material_type', 'success', 0),
(7, '考察记录',     'INSPECT_RECORD',   'dev_material_type', 'info',    0),
(8, '其它',         'OTHER',            'dev_material_type', 'info',    0),
-- 学历
(1, '研究生', '1', 'sys_education', 'info',    0),
(2, '本科',   '2', 'sys_education', 'primary', 0),
(3, '大专',   '3', 'sys_education', 'primary', 0),
(4, '中专',   '4', 'sys_education', 'info',    0),
(5, '高中',   '5', 'sys_education', 'info',    0),
(6, '初中及以下', '6', 'sys_education', 'info', 0);

-- =====================================================================
--  六之二、发展党员材料模板（依据《广西发展党员工作手册》）
--
--  编号体系与手册附件一致：1-x 对应阶段一，2-x 对应阶段二，依此类推。
--  文件随 jar 打包在 hparty-admin/src/main/resources/material-templates/ 下。
--
--  submit_role 表示该材料由谁出具/填写：
--    APPLICANT=本人  BRANCH=党支部  TRAINER=培养联系人/入党介绍人  PARENT_ORG=上级党委
--  is_roster=1 的为组织台账/名册，按组织归档，不随个人流程流转。
-- =====================================================================
DELETE FROM `dev_material_template`;
INSERT INTO `dev_material_template`
(`template_code`, `template_name`, `stage_code`, `step_code`, `material_type`, `is_required`, `is_roster`,
 `submit_role`, `blank_file`, `sample_file`, `fill_note`, `order_num`) VALUES

-- ---------- 阶段一：申请入党 ----------
('1-1', '入党申请书', 'STAGE_1', 'STEP_01', 'APPLY_BOOK', 1, 0, 'APPLICANT', '1-1.doc', NULL,
 '本人亲笔书写（打印件须本人签名），内容含：对党的认识、入党动机、本人基本情况、家庭主要成员及主要社会关系情况、今后努力方向。要求年满18周岁。', 1),
('1-2', '同入党申请人的谈话记录', 'STAGE_1', 'STEP_02', 'TALK_RECORD', 1, 0, 'BRANCH', '1-2.doc', '1-2.doc',
 '收到入党申请书后1个月内完成。谈话人须为支部书记、副书记或组织委员，谈话情况如实记录并由双方签字。', 2),
('1-2-1', '入党申请人有关事项报告表', 'STAGE_1', 'STEP_02', 'OTHER', 1, 0, 'APPLICANT', '1-2-1.doc', '1-2-1.doc',
 '由申请人如实填报本人及家庭主要成员、主要社会关系的有关情况。', 3),
('1-2-2', '入党申请人情况调查登记表', 'STAGE_1', 'STEP_02', 'OTHER', 1, 0, 'BRANCH', '1-2-2.doc', '1-2-2.doc',
 '党组织对申请人基本情况的调查核实记录。', 4),
('1-3', '发展党员工作全程纪实表', 'STAGE_1', NULL, 'OTHER', 1, 0, 'BRANCH', '1-3.doc', '1-3.doc',
 '从递交入党申请书起全程纪实，25个步骤的办理时间、经办人、结论均需登记。阶段通用，贯穿全流程。', 5),
('1-4', '年度入党申请人名册', 'STAGE_1', NULL, 'OTHER', 0, 1, 'BRANCH', '1-4.xls', NULL,
 '支部年度台账，按年度汇总本支部入党申请人。', 6),

-- ---------- 阶段二：入党积极分子的确定和培养教育 ----------
('2-1', '推荐入党积极分子人选登记表', 'STAGE_2', 'STEP_03', 'OTHER', 1, 0, 'BRANCH', '2-1.doc', '2-1.doc',
 '党员推荐或群团组织推优情况登记。注意综合运用推荐结果，防止简单以票取人。', 7),
('2-2', '支部委员会（支部大会）确定入党积极分子会议记录', 'STAGE_2', 'STEP_03', 'MEETING_MINUTES', 1, 0, 'BRANCH', '2-2.doc', NULL,
 '须由支部委员会集体研究决定，会议记录须完整记载讨论过程与表决结果。', 8),
('2-3', '入党积极分子备案登记表', 'STAGE_2', 'STEP_04', 'OTHER', 1, 0, 'BRANCH', '2-3.doc', '2-3.doc',
 '报上级党委备案用。上级党委审查材料是否齐全、是否具备条件。', 9),
('2-4', '关于将×××等同志进行入党积极分子备案的报告（附名册）', 'STAGE_2', 'STEP_04', 'OTHER', 1, 0, 'BRANCH', '2-4.doc', NULL,
 '支部向上级党委行文备案，附入党积极分子名册。', 10),
('2-5', '思想汇报', 'STAGE_2', 'STEP_06', 'THOUGHT_REPORT', 1, 0, 'APPLICANT', '2-5.doc', NULL,
 '入党积极分子每季度至少提交一次，内容为近期思想、学习、工作情况和认识体会。培养教育期内持续提交。', 11),
('2-6', '入党积极分子培养考察登记表', 'STAGE_2', 'STEP_06', 'INSPECT_RECORD', 1, 0, 'TRAINER', '2-6.doc', '2-6.doc',
 '由培养联系人填写，每半年考察一次，重点记录政治觉悟、道德品质、现实表现和家庭情况。培养教育须满1年。', 12),
('2-7', '入党积极分子培训班学员名册', 'STAGE_2', NULL, 'OTHER', 0, 1, 'BRANCH', '2-7.xls', NULL,
 '参加入党积极分子培训班的学员台账。', 13),

-- ---------- 阶段三：发展对象的确定和考察 ----------
('3-1', '发展对象人选备案登记表', 'STAGE_3', 'STEP_08', 'OTHER', 1, 0, 'BRANCH', '3-1.docx', '3-1.docx',
 '报上级党委备案用，上级党委审查后提出意见，同意后方可列为发展对象。', 14),
('3-2', '支部委员会（支部大会）讨论发展对象人选会议记录', 'STAGE_3', 'STEP_07', 'MEETING_MINUTES', 1, 0, 'BRANCH', '3-2.doc', NULL,
 '须听取党小组、培养联系人、党员和群众意见后，由支部委员会讨论同意。', 15),
('3-3', '关于确定×××等同志为发展对象人选的公示', 'STAGE_3', 'STEP_07', 'OTHER', 1, 0, 'BRANCH', '3-3.doc', NULL,
 '在本单位范围内公示，公示期一般不少于5个工作日。', 16),
('3-4', '发展对象人选公示情况登记表', 'STAGE_3', 'STEP_07', 'OTHER', 1, 0, 'BRANCH', '3-4.doc', '3-4.doc',
 '记录公示时间、范围、群众反映及处理情况。', 17),
('3-5', '关于将×××等同志进行发展对象人选备案的报告（附名册）', 'STAGE_3', 'STEP_08', 'OTHER', 1, 0, 'BRANCH', '3-5.doc', NULL,
 '支部向上级党委行文备案发展对象人选，附名册。', 18),
('3-6', '自传', 'STAGE_3', 'STEP_10', 'OTHER', 1, 0, 'APPLICANT', '3-6.doc', NULL,
 '本人撰写，内容含个人成长经历、家庭主要成员和主要社会关系情况、对重大政治历史问题的认识。', 19),
('3-7', '发展对象政治审查函', 'STAGE_3', 'STEP_10', 'POLITICAL_REVIEW', 1, 0, 'BRANCH', '3-7.doc', NULL,
 '向发展对象户籍所在地或原单位党组织发出的政审函。对流动人员中的发展对象还应征求户籍所在地和居住地党组织意见。', 20),
('3-8', '发展对象政治审查回函', 'STAGE_3', 'STEP_10', 'POLITICAL_REVIEW', 1, 0, 'PARENT_ORG', '3-8.doc', NULL,
 '由收到政审函的党组织回函，须加盖党组织印章。', 21),
('3-9', '关于×××同志政治审查情况的报告', 'STAGE_3', 'STEP_10', 'POLITICAL_REVIEW', 1, 0, 'BRANCH', '3-9.doc', NULL,
 '审查情况须形成结论性材料。未经政治审查或政治审查不合格的，不能发展入党。', 22),
('3-10', '发展对象培训班学员名册', 'STAGE_3', 'STEP_11', 'OTHER', 0, 1, 'PARENT_ORG', '3-10.xls', NULL,
 '参加发展对象集中培训的学员台账。', 23),
('3-11', '发展对象短期集中培训情况登记表', 'STAGE_3', 'STEP_11', 'TRAINING_CERT', 1, 0, 'PARENT_ORG', '3-11.doc', '3-11.doc',
 '培训时间不少于3天（或不少于24学时）。未经培训的，除个别特殊情况外，不能发展入党。', 24),

-- ---------- 阶段四：预备党员的接收 ----------
('4-1a', '关于对×××等同志进行预审的请示（二级学院党委的党支部使用）', 'STAGE_4', 'STEP_13', 'OTHER', 1, 0, 'BRANCH', '4-1a.doc', NULL,
 '党支部向上级党委行文请示预审。二级学院党委下设的党支部使用此版本。', 25),
('4-1b', '关于对×××等同志进行预审的请示（党总支使用）', 'STAGE_4', 'STEP_13', 'OTHER', 0, 0, 'BRANCH', '4-1b.doc', NULL,
 '党总支向党委行文请示预审。与 4-1a 按本单位组织架构择一使用。', 26),
('4-1-1', '发展党员预审名册', 'STAGE_4', 'STEP_13', 'OTHER', 0, 1, 'BRANCH', '4-1-1.xls', NULL,
 '随预审请示报送的名册（半年一期）。', 27),
('4-2', '对×××等同志预审的意见', 'STAGE_4', 'STEP_13', 'OTHER', 1, 0, 'PARENT_ORG', '4-2.doc', NULL,
 '上级党委审查发展对象条件、培养教育情况后出具。审查结果书面通知党支部，并向合格者发放入党志愿书。', 28),
('4-2a', '关于对×××等同志预审的意见（附名册）', 'STAGE_4', 'STEP_13', 'OTHER', 0, 0, 'PARENT_ORG', '4-2a.doc', NULL,
 '批量预审时使用（二级学院党委）。与 4-2 择一使用。', 29),
('4-3', '入党志愿书', 'STAGE_4', 'STEP_14', 'VOLUNTEER_BOOK', 1, 0, 'APPLICANT', '4-3.doc', '4-3.doc',
 '在入党介绍人指导下由本人如实填写。注意：发展对象未来3个月内将离开工作、学习单位的，一般不办理接收手续。', 30),
('4-4', '支部大会讨论接收预备党员会议记录', 'STAGE_4', 'STEP_15', 'MEETING_MINUTES', 1, 0, 'BRANCH', '4-4.doc', NULL,
 '有表决权的到会人数必须超过应到会有表决权人数的半数才能开会；赞成人数超过应到会有表决权的正式党员的半数才能通过。', 31),
('4-5', '关于审批×××等同志为中共预备党员的请示', 'STAGE_4', 'STEP_17', 'OTHER', 1, 0, 'BRANCH', '4-5.doc', NULL,
 '支部大会通过后报上级党委审批。上级党委须在3个月内审批。', 32),
('4-6', '关于同意接收×××等同志为中共预备党员的批复', 'STAGE_4', 'STEP_17', 'OTHER', 1, 0, 'PARENT_ORG', '4-6.doc', NULL,
 '上级党委批复。审批结果及时通知党支部，支部书记应同本人谈话。党龄从预备期满转为正式党员之日算起。', 33),
('4-7', '关于将×××等×名同志进行预备党员备案的报告', 'STAGE_4', 'STEP_18', 'OTHER', 1, 0, 'PARENT_ORG', '4-7.doc', NULL,
 '报再上一级党委组织部门备案，用于掌握预备党员结构、分布、质量等情况。', 34),
('4-8', '年度新发展党员名册', 'STAGE_4', NULL, 'OTHER', 0, 1, 'BRANCH', '4-8.xls', NULL,
 '支部年度台账，汇总本年度新发展党员。', 35),

-- ---------- 阶段五：预备党员的教育考察和转正 ----------
('5-0', '预备党员培训班学员名册', 'STAGE_5', 'STEP_21', 'OTHER', 0, 1, 'PARENT_ORG', '5-0.xls', NULL,
 '参加预备党员培训班的学员台账。', 36),
('5-1', '预备党员转正审查登记表', 'STAGE_5', 'STEP_23', 'OTHER', 1, 0, 'BRANCH', '5-1.doc', '5-1.doc',
 '支部委员会审查用。讨论转正前应征求党员和群众意见、听取党小组意见。', 37),
('5-1-1', '预备党员转正前情况调查登记表', 'STAGE_5', 'STEP_21', 'OTHER', 1, 0, 'BRANCH', '5-1-1.doc', '5-1-1.doc',
 '预备期满前对本人情况、群众反映的调查登记。', 38),
('5-2', '转正申请书', 'STAGE_5', 'STEP_22', 'REGULAR_APPLY', 1, 0, 'APPLICANT', '5-2.doc', NULL,
 '预备期满时由本人书面提出。预备期为1年，从支部大会通过其为预备党员之日算起。', 39),
('5-3', '关于拟将×××等同志转为中共正式党员的公示', 'STAGE_5', 'STEP_23', 'OTHER', 1, 0, 'BRANCH', '5-3.doc', NULL,
 '支部大会讨论前公示，公示期一般不少于5个工作日。', 40),
('5-4', '预备党员转正公示情况登记表', 'STAGE_5', 'STEP_23', 'OTHER', 1, 0, 'BRANCH', '5-4.doc', '5-4.doc',
 '记录公示时间、范围、群众反映及处理情况。', 41),
('5-5', '支部大会讨论预备党员转正会议记录', 'STAGE_5', 'STEP_23', 'MEETING_MINUTES', 1, 0, 'BRANCH', '5-5.doc', NULL,
 '程序参照接收预备党员。表决规则同上：两个半数的分母都是「应到」人数。', 42),
('5-6', '预备党员转正审议名册', 'STAGE_5', 'STEP_23', 'OTHER', 0, 1, 'BRANCH', '5-6.xls', NULL,
 '批量审议转正时使用的名册。', 43),
('5-7', '关于审批×××等同志转为中共正式党员的请示', 'STAGE_5', 'STEP_24', 'OTHER', 1, 0, 'BRANCH', '5-7.doc', NULL,
 '支部大会通过后报上级党委审批。上级党委须在3个月内审批。', 44),
('5-8', '关于取消×××同志预备党员资格的请示', 'STAGE_5', 'STEP_23', 'OTHER', 0, 0, 'BRANCH', '5-8.doc', NULL,
 '【分支材料】不履行党员义务、不具备党员条件的，应取消预备党员资格时使用。', 45),
('5-9', '关于审批延长×××同志预备期的请示', 'STAGE_5', 'STEP_23', 'OTHER', 0, 0, 'BRANCH', '5-9.doc', NULL,
 '【分支材料】需要继续考察和教育的，可延长1次预备期，延长不能少于半年、最长不超过1年时使用。', 46),
('5-10', '关于同意×××等同志转为中共正式党员的批复', 'STAGE_5', 'STEP_24', 'OTHER', 1, 0, 'PARENT_ORG', '5-10.doc', NULL,
 '上级党委批复。审批结果及时通知党支部，支部书记应同本人谈话。', 47),
('5-11', '关于同意延长×××同志预备期（或取消×××同志预备党员资格）的批复', 'STAGE_5', 'STEP_24', 'OTHER', 0, 0, 'PARENT_ORG', '5-11.doc', NULL,
 '【分支材料】支部大会作出延长预备期或取消资格决定后，上级党委的批复。', 48),
('5-12', '教职工党员档案完整性的审核意见', 'STAGE_5', 'STEP_25', 'OTHER', 1, 0, 'BRANCH', '5-12.doc', NULL,
 '归档前对档案材料完整性的审核。', 49),
('5-13', '党员档案材料清单', 'STAGE_5', 'STEP_25', 'OTHER', 1, 0, 'BRANCH', '5-13.doc', NULL,
 '归档材料清单。有人事档案的存入本人人事档案；无人事档案的建立党员档案，由所在党委或县级党委组织部门保存。', 50);

-- 补充材料类型字典（谈话记录、会议记录）
INSERT INTO `sys_dict_data` (`dict_sort`, `dict_label`, `dict_value`, `dict_type`, `list_class`, `is_default`) VALUES
(9,  '谈话记录', 'TALK_RECORD',     'dev_material_type', 'info', 0),
(10, '会议记录', 'MEETING_MINUTES', 'dev_material_type', 'info', 0);

-- =====================================================================
--  七、管理员账号
--  密码：123456（BCrypt，强度 10）
-- =====================================================================
DELETE FROM `sys_user_role`;
DELETE FROM `sys_user` WHERE `user_id` = 1;
INSERT INTO `sys_user` (`user_id`, `username`, `password`, `nick_name`, `org_id`, `phone`, `email`, `sex`, `status`, `remark`) VALUES
(1, 'admin', '$2a$10$0trYBSe/GBXmyi4zeLS6CeeNtib65bM8S04HUsbuZ4HJ9TmymrPl2', '系统管理员', NULL, '13800000000', 'admin@hparty.gov.cn', 1, 1, '超级管理员，初始密码 123456');

INSERT INTO `sys_user_role` (`user_id`, `role_id`) VALUES (1, 1);

-- =====================================================================
--  初始化完成
--    阶段模板 5 条 / 步骤模板 25 条 / 角色 10 个 / 菜单 60 项 / 字典 12 类 62 项
--    管理员：admin / 123456
-- =====================================================================
SELECT
    (SELECT COUNT(*) FROM dev_stage)  AS 阶段数,
    (SELECT COUNT(*) FROM dev_step)   AS 步骤数,
    (SELECT COUNT(*) FROM sys_role)   AS 角色数,
    (SELECT COUNT(*) FROM sys_menu)   AS 菜单数,
    (SELECT COUNT(*) FROM sys_dict_data) AS 字典项数;

-- =====================================================================
--  追加：发展党员 - 材料模板菜单
--  《广西发展党员工作手册》50 份表格的只读浏览与下载页。
--  component 必须是纯组件路径（develop/material），带查询串会导致路由注册不上。
--  权限沿用 develop:applicant:list，不新增权限标识。
-- =====================================================================
DELETE FROM `sys_menu` WHERE `menu_id` = 304;
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
(304, 3, '材料模板', 4, 'material', 'develop/material', 'C', 1, 1, 'develop:applicant:list', 'FileWordOutlined');

-- 有发展党员模块权限的角色（超管/党委书记/支部书记/副书记/组织委员）一并授权
DELETE FROM `sys_role_menu` WHERE `menu_id` = 304;
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(1, 304), (2, 304), (3, 304), (4, 304), (5, 304);

-- =====================================================================
--  八、新增模块菜单（追加，不改动上方已有行）
--    1300+ 号段留给「民主评议党员」与「发展党员-年度计划」，
--    按钮统一走 1310+，与「组织关系转接」的 14xx 号段错开。
--    注意 component 必须是**纯组件路径**，带查询串会匹配不到文件、菜单变死链。
-- =====================================================================
INSERT INTO `sys_menu` (`menu_id`, `parent_id`, `menu_name`, `order_num`, `path`, `component`, `menu_type`, `visible`, `status`, `perms`, `icon`) VALUES
-- 民主评议党员（一级菜单）
(1300, 0,    '民主评议党员', 12, '/party-review', 'review/index', 'C', 1, 1, 'review:list', 'AuditOutlined'),
-- 发展党员 → 年度计划（挂在菜单 3 发展党员下）
(1301, 3,    '年度计划',      4, 'plan',          'develop/plan', 'C', 1, 1, 'develop:plan:list', 'CalendarOutlined'),

-- 民主评议党员-按钮
(1310, 1300, '新增评议批次', 1, NULL, NULL, 'F', 1, 1, 'review:add',    NULL),
(1311, 1300, '修改评议批次', 2, NULL, NULL, 'F', 1, 1, 'review:edit',   NULL),
(1312, 1300, '删除评议批次', 3, NULL, NULL, 'F', 1, 1, 'review:remove', NULL),
(1313, 1300, '组织评定',     4, NULL, NULL, 'F', 1, 1, 'review:judge',  NULL),
(1314, 1300, '导出评议结果', 5, NULL, NULL, 'F', 1, 1, 'report:export', NULL),

-- 年度计划-按钮
(1315, 1301, '下达计划', 1, NULL, NULL, 'F', 1, 1, 'develop:plan:add',    NULL),
(1316, 1301, '修改计划', 2, NULL, NULL, 'F', 1, 1, 'develop:plan:edit',   NULL),
(1317, 1301, '删除计划', 3, NULL, NULL, 'F', 1, 1, 'develop:plan:remove', NULL);

-- 角色-菜单关联（新增部分单独插入，不改动上面的语句）
-- 党委书记(2) / 支部书记(3) / 支部副书记(4) / 组织委员(5)：完整权限
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(2, 1300), (2, 1301), (2, 1310), (2, 1311), (2, 1312), (2, 1313), (2, 1314), (2, 1315), (2, 1316), (2, 1317),
(3, 1300), (3, 1301), (3, 1310), (3, 1311), (3, 1312), (3, 1313), (3, 1314), (3, 1315), (3, 1316), (3, 1317),
(4, 1300), (4, 1301), (4, 1310), (4, 1311), (4, 1312), (4, 1313), (4, 1314), (4, 1315), (4, 1316), (4, 1317),
(5, 1300), (5, 1301), (5, 1310), (5, 1311), (5, 1312), (5, 1313), (5, 1314), (5, 1315), (5, 1316), (5, 1317);

-- 宣传委员(6) / 纪检委员(7) / 党小组长(8)：只读查看
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES
(6, 1300), (6, 1301),
(7, 1300), (7, 1301),
(8, 1300);

-- 普通党员(9)：需要进入评议页面提交自评/互评（自评互评接口本身不加功能权限）
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`) VALUES (9, 1300);

-- 超级管理员(1)：全部新增菜单
INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT 1, `menu_id` FROM `sys_menu` WHERE `menu_id` BETWEEN 1300 AND 1399;
