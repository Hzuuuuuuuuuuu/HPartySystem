package com.hparty.framework.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.hparty.common.constant.Constants;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计字段自动填充。
 */
@Component
public class MetaObjectHandlerImpl implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();

        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", String.class, operator);
        strictInsertFill(metaObject, "updateBy", String.class, operator);
        strictInsertFill(metaObject, "delFlag", Integer.class, Constants.NOT_DELETED);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateBy", String.class, currentOperator());
    }

    private String currentOperator() {
        LoginUser user = SecurityUtils.getLoginUserOrNull();
        return user == null ? "system" : user.getUsername();
    }
}
