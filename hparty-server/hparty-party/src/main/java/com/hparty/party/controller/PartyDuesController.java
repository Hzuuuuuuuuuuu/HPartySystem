package com.hparty.party.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.party.domain.dto.DuesRecordQuery;
import com.hparty.party.domain.entity.PartyDuesRecord;
import com.hparty.party.domain.entity.PartyDuesUse;
import com.hparty.party.service.PartyDuesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 党费收缴及使用接口。
 * <p>应缴金额由后端按党费标准自动推导，前端只需提交缴纳基数。</p>
 */
@Tag(name = "26-党费收缴及使用")
@RestController
@RequestMapping("/party/dues")
@RequiredArgsConstructor
public class PartyDuesController {

    private final PartyDuesService duesService;

    @Operation(summary = "分页查询党费缴纳记录")
    @SaCheckPermission("dues:list")
    @GetMapping("/record/page")
    public R<PageResult<Map<String, Object>>> recordPage(DuesRecordQuery query) {
        return R.ok(duesService.recordPage(query));
    }

    /**
     * 欠缴预警清单。
     *
     * <p>《党章》规定党员连续 6 个月不缴纳党费按自行脱党处理，
     * 因此这个清单是支部必须定期核查的。默认按 6 个月筛查。</p>
     */
    @Operation(summary = "欠缴预警清单", description = "默认筛查连续欠缴 6 个月（面临自行脱党风险）的人员")
    @SaCheckPermission("dues:list")
    @GetMapping("/record/arrears")
    public R<List<Map<String, Object>>> arrears(
            @RequestParam(required = false, defaultValue = "6") Integer minMonths) {
        return R.ok(duesService.listArrears(minMonths));
    }

    /**
     * 把欠缴标记刷新到数据库列上。
     * <p>查询时是现算的（见 {@code PartyDuesService.computeOverdue}），
     * 这个接口供定时任务与需要带索引批量筛查的场景调用。</p>
     */
    @Operation(summary = "刷新欠缴标记", description = "供定时任务调用；查询接口本身是现算的，不依赖此列")
    @SaCheckPermission("dues:edit")
    @PostMapping("/record/refresh-overdue")
    public R<Integer> refreshOverdue() {
        return R.ok("刷新完成", duesService.refreshOverdueFlags());
    }

    @Operation(summary = "党费收缴统计")
    @SaCheckPermission("dues:list")
    @GetMapping("/record/statistics")
    public R<Map<String, Object>> recordStatistics(@RequestParam(required = false) Integer year,
                                                   @RequestParam(required = false) Long orgId) {
        return R.ok(duesService.recordStatistics(year, orgId));
    }

    @Operation(summary = "新增缴纳记录")
    @SaCheckPermission("dues:add")
    @PostMapping("/record")
    public R<Long> addRecord(@RequestBody PartyDuesRecord record) {
        return R.ok("新增成功", duesService.addRecord(record));
    }

    @Operation(summary = "修改缴纳记录")
    @SaCheckPermission("dues:edit")
    @PutMapping("/record")
    public R<Void> updateRecord(@RequestBody PartyDuesRecord record) {
        duesService.updateRecord(record);
        return R.ok("修改成功", null);
    }

    @Operation(summary = "删除缴纳记录")
    @SaCheckPermission("dues:remove")
    @DeleteMapping("/record/{duesId}")
    public R<Void> removeRecord(@PathVariable Long duesId) {
        duesService.removeRecord(duesId);
        return R.ok("删除成功", null);
    }

    @Operation(summary = "批量生成月度党费账单")
    @SaCheckPermission("dues:generate")
    @PostMapping("/record/generate")
    public R<Integer> generate(@RequestParam Integer year, @RequestParam Integer month) {
        return R.ok("生成成功", duesService.generate(year, month));
    }

    @Operation(summary = "党费使用记录列表")
    @SaCheckPermission("dues:list")
    @GetMapping("/use/list")
    public R<List<Map<String, Object>>> useList(@RequestParam(required = false) Long orgId,
                                                @RequestParam(required = false) Integer useYear) {
        return R.ok(duesService.useList(orgId, useYear));
    }

    @Operation(summary = "新增党费使用记录")
    @SaCheckPermission("dues:use:add")
    @PostMapping("/use")
    public R<Long> addUse(@RequestBody PartyDuesUse use) {
        return R.ok("新增成功", duesService.addUse(use));
    }

    @Operation(summary = "删除党费使用记录")
    @SaCheckPermission("dues:use:remove")
    @DeleteMapping("/use/{useId}")
    public R<Void> removeUse(@PathVariable Long useId) {
        duesService.removeUse(useId);
        return R.ok("删除成功", null);
    }
}
