package com.hparty.system.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hparty.common.core.PageResult;
import com.hparty.framework.core.PageUtils;
import com.hparty.system.domain.dto.SysLogQuery;
import com.hparty.system.domain.entity.SysLoginLog;
import com.hparty.system.domain.entity.SysOperLog;
import com.hparty.system.mapper.SysLoginLogMapper;
import com.hparty.system.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 日志服务。
 *
 * <p>日志表只增不改，因此不套用数据权限（日志属于系统级审计信息，
 * 且已通过 {@code system:loginlog:list} / {@code system:operlog:list} 做了功能权限隔离）。</p>
 */
@Service
@RequiredArgsConstructor
public class SysLogService {

    private final SysLoginLogMapper loginLogMapper;
    private final SysOperLogMapper operLogMapper;

    // ==================== 登录日志 ====================

    public PageResult<SysLoginLog> pageLoginLogs(SysLogQuery query) {
        LambdaQueryWrapper<SysLoginLog> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getUsername())) {
            wrapper.like(SysLoginLog::getUsername, query.getUsername());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysLoginLog::getStatus, query.getStatus());
        }
        applyTimeRange(wrapper, query, SysLoginLog::getLoginTime);
        wrapper.orderByDesc(SysLoginLog::getLoginTime);

        Page<SysLoginLog> page = loginLogMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(page);
    }

    public void removeLoginLogs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        loginLogMapper.deleteBatchIds(ids);
    }

    public void cleanLoginLogs() {
        loginLogMapper.delete(new LambdaQueryWrapper<>());
    }

    // ==================== 操作日志 ====================

    public PageResult<SysOperLog> pageOperLogs(SysLogQuery query) {
        LambdaQueryWrapper<SysOperLog> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(query.getTitle())) {
            wrapper.like(SysOperLog::getTitle, query.getTitle());
        }
        if (StrUtil.isNotBlank(query.getUsername())) {
            wrapper.like(SysOperLog::getOperName, query.getUsername());
        }
        if (query.getBusinessType() != null) {
            wrapper.eq(SysOperLog::getBusinessType, query.getBusinessType());
        }
        if (query.getStatus() != null) {
            wrapper.eq(SysOperLog::getStatus, query.getStatus());
        }
        applyTimeRange(wrapper, query, SysOperLog::getOperTime);
        wrapper.orderByDesc(SysOperLog::getOperTime);

        Page<SysOperLog> page = operLogMapper.selectPage(PageUtils.toPage(query), wrapper);
        return PageResult.of(page);
    }

    public SysOperLog getOperLog(Long operId) {
        return operLogMapper.selectById(operId);
    }

    public void removeOperLogs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        operLogMapper.deleteBatchIds(ids);
    }

    public void cleanOperLogs() {
        operLogMapper.delete(new LambdaQueryWrapper<>());
    }

    /**
     * 应用时间范围条件。
     * <p>结束时间只给到日期时补成当天 23:59:59，否则「查 9 月 16 日」
     * 会查不到当天的数据 —— 这是这类筛选最常见的坑。</p>
     */
    private <T> void applyTimeRange(LambdaQueryWrapper<T> wrapper, SysLogQuery query,
                                    com.baomidou.mybatisplus.core.toolkit.support.SFunction<T, ?> column) {
        if (query.getBeginTime() != null) {
            wrapper.ge(column, query.getBeginTime());
        }
        if (query.getEndTime() != null) {
            LocalDateTime end = query.getEndTime();
            if (end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
                end = LocalDateTime.of(end.toLocalDate(), LocalTime.MAX.withNano(0));
            }
            wrapper.le(column, end);
        }
    }

    /** 保留近 N 天的日志，供定时清理调用 */
    public void cleanBefore(int days) {
        LocalDateTime threshold = LocalDate.now().minusDays(days).atStartOfDay();
        loginLogMapper.delete(new LambdaQueryWrapper<SysLoginLog>()
                .lt(SysLoginLog::getLoginTime, threshold));
        operLogMapper.delete(new LambdaQueryWrapper<SysOperLog>()
                .lt(SysOperLog::getOperTime, threshold));
    }
}
