-- =====================================================================
--  V7：党组织增加联系邮箱与所辖专业
--  用途：二级学院下的学生党支部需要登记「主要联系人 / 电话 / 邮箱 / 所辖专业」。
--        联系人沿用 leader，电话沿用 phone，这里只补两列。
-- =====================================================================
ALTER TABLE `sys_dept`
    ADD COLUMN `email`  VARCHAR(100) DEFAULT NULL COMMENT '联系邮箱' AFTER `phone`,
    ADD COLUMN `majors` VARCHAR(255) DEFAULT NULL COMMENT '所辖专业，多个用 / 分隔' AFTER `email`;
