package com.hparty.develop.rule;

import com.hparty.develop.domain.dto.DevHandleDTO;
import com.hparty.develop.domain.entity.DevApplicant;
import com.hparty.develop.domain.entity.DevMaterial;
import com.hparty.develop.domain.entity.DevStep;
import com.hparty.develop.domain.entity.DevStepRecord;
import com.hparty.framework.security.LoginUser;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 办理上下文：规则校验所需的全部信息。
 */
@Data
@Builder
public class DevContext {

    /** 申请人实例（含当前阶段、当前步骤、各项日期） */
    private DevApplicant applicant;

    /** 申请人的人员档案（含出生日期、党员身份等，用于资格校验） */
    private com.hparty.system.domain.entity.PartyPerson person;

    /** 当前办理的步骤模板 */
    private DevStep step;

    /** 办理表单 */
    private DevHandleDTO form;

    /** 办理人 */
    private LoginUser operator;

    /** 该申请人已有的全部办理记录（按时间正序） */
    @Builder.Default
    private List<DevStepRecord> records = Collections.emptyList();

    /** 该申请人已有的材料 */
    @Builder.Default
    private List<DevMaterial> materials = Collections.emptyList();

    /** 步骤模板索引：步骤编码 → 步骤模板 */
    @Builder.Default
    private Map<String, DevStep> stepMap = Collections.emptyMap();

    /** 办理时刻，默认取当前时间（便于单元测试注入固定时间） */
    @Builder.Default
    private LocalDateTime now = LocalDateTime.now();

    public LocalDate today() {
        return now.toLocalDate();
    }

    /** 按步骤编码取模板 */
    public DevStep step(String stepCode) {
        return stepMap.get(stepCode);
    }

    /**
     * 取指定步骤最近一次「通过」的办理时间。
     * <p>这是 {@code INTERVAL_RULE} 计算时间间隔的基准。</p>
     */
    public Optional<LocalDateTime> lastPassTime(String stepCode) {
        return records.stream()
                .filter(r -> stepCode.equals(r.getStepCode()))
                .filter(r -> r.getResult() != null && r.getResult() == 1)
                .map(DevStepRecord::getHandleTime)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo);
    }

    /**
     * 取指定步骤**首次**「通过」的办理时间。
     * <p>周期性步骤（如培养教育考察）可能有多条记录，起算点应取第一次。</p>
     */
    public Optional<LocalDateTime> firstPassTime(String stepCode) {
        return records.stream()
                .filter(r -> stepCode.equals(r.getStepCode()))
                .filter(r -> r.getResult() != null && r.getResult() == 1)
                .map(DevStepRecord::getHandleTime)
                .filter(java.util.Objects::nonNull)
                .min(LocalDateTime::compareTo);
    }

    /** 取指定步骤的最后一条记录（不论结论） */
    public Optional<DevStepRecord> lastRecord(String stepCode) {
        return records.stream()
                .filter(r -> stepCode.equals(r.getStepCode()))
                .filter(r -> r.getHandleTime() != null)
                .max(java.util.Comparator.comparing(DevStepRecord::getHandleTime));
    }

    /** 指定步骤已办结的记录条数 */
    public long countRecords(String stepCode) {
        return records.stream().filter(r -> stepCode.equals(r.getStepCode())).count();
    }

    /**
     * 指定步骤**已办结**（status=1）的记录条数，用于给周期性考察编号。
     * <p>不能用 {@link #countRecords} —— 它把待办记录也算进去，会让序号恒比真实次数大 1。</p>
     */
    public long countDoneRecords(String stepCode) {
        return records.stream()
                .filter(r -> stepCode.equals(r.getStepCode()))
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .count();
    }
}
