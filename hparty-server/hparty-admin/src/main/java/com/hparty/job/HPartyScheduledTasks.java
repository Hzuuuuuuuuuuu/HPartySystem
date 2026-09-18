package com.hparty.job;

import com.hparty.develop.service.DevFlowService;
import com.hparty.party.service.PartyDuesService;
import com.hparty.party.service.PartyTransferService;
import com.hparty.system.service.SysLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 定时任务。
 *
 * <p>放在启动模块：它依赖 system / party / develop 三个域的服务，
 * 只有 admin 能同时看到它们。</p>
 *
 * <p><b>每个任务都必须是幂等的</b> —— 手动补跑、重启后重跑、集群多实例同时跑，
 * 都不能产生重复数据。这里只负责调度，幂等性由被调用方保证
 * （如党费生成内部按「人 + 年 + 月」查重）。</p>
 *
 * <p>用 {@code hparty.job.enabled=false} 可整体关闭（单元测试、本地调试时有用）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "hparty.job.enabled", havingValue = "true", matchIfMissing = true)
public class HPartyScheduledTasks {

    private final PartyDuesService duesService;
    private final PartyTransferService transferService;
    private final DevFlowService flowService;
    private final SysLogService logService;

    /** 日志保留天数 */
    @Value("${hparty.job.log-retain-days:90}")
    private int logRetainDays;

    /**
     * 生成当月党费账单。
     *
     * <p>《党章》规定党员应按期交纳党费，支部每月需要知道应收多少、谁还没交。
     * 生成逻辑自身按「人 + 年 + 月」查重，重复执行不会产生重复账单。</p>
     */
    @Scheduled(cron = "${hparty.job.dues-generate-cron:0 7 1 1 * ?}")
    public void generateMonthlyDues() {
        LocalDate today = LocalDate.now();
        try {
            int count = duesService.generate(today.getYear(), today.getMonthValue());
            if (count > 0) {
                log.info("[定时任务] 生成 {} 年 {} 月党费账单 {} 条",
                        today.getYear(), today.getMonthValue(), count);
            } else {
                log.debug("[定时任务] {} 年 {} 月党费账单已存在，无需生成",
                        today.getYear(), today.getMonthValue());
            }
        } catch (Exception e) {
            // 定时任务的异常必须自己吞掉并记录 —— 抛出去只会让调度线程静默失败，
            // 而且默认配置下还会取消该任务的后续调度
            log.error("[定时任务] 生成党费账单失败", e);
        }
    }

    /**
     * 扫描各类超期项并落库。
     *
     * <p>覆盖三类：发展党员待办步骤、党费欠缴、组织关系转接超期未落地。</p>
     *
     * <p><b>为什么要把超期「落库」而不是查询时现算</b>：统计与预警需要按列检索
     * （例如「列出所有超期的发展对象」），现算意味着每次全表扫描。
     * 落库后一句带索引的 SQL 即可。</p>
     */
    @Scheduled(cron = "${hparty.job.overdue-scan-cron:0 23 7 * * ?}")
    public void scanOverdue() {
        try {
            int steps = flowService.markOverduePendingRecords();
            long stepTotal = flowService.countOverduePendingRecords();
            int dues = duesService.refreshOverdueFlags();
            int transfers = transferService.refreshOverdueFlags();

            if (steps > 0 || dues > 0 || transfers > 0) {
                log.warn("[定时任务] 超期扫描：发展党员新增 {} 条（合计 {} 条）、党费欠缴 {} 条、转接超期 {} 条",
                        steps, stepTotal, dues, transfers);
            } else {
                log.debug("[定时任务] 超期扫描完成，无新增超期项");
            }
        } catch (Exception e) {
            log.error("[定时任务] 超期扫描失败", e);
        }
    }

    /**
     * 清理过期日志。
     *
     * <p>登录日志与操作日志是只增不改的表，不清理会无限膨胀。
     * 保留天数由 {@code hparty.job.log-retain-days} 控制，默认 90 天 ——
     * 党务系统的操作留痕通常要求保留一段时间，不要设得太短。</p>
     */
    @Scheduled(cron = "${hparty.job.log-clean-cron:0 13 3 * * ?}")
    public void cleanExpiredLogs() {
        try {
            logService.cleanBefore(logRetainDays);
            log.debug("[定时任务] 已清理 {} 天前的日志", logRetainDays);
        } catch (Exception e) {
            log.error("[定时任务] 清理日志失败", e);
        }
    }
}
