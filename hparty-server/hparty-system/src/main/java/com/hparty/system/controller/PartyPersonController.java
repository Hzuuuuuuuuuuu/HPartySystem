package com.hparty.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.hparty.common.core.PageResult;
import com.hparty.common.core.R;
import com.hparty.system.domain.dto.PartyPersonDTO;
import com.hparty.system.domain.dto.PartyPersonQuery;
import com.hparty.system.domain.vo.PartyPersonDetailVO;
import com.hparty.system.domain.vo.PartyPersonStatVO;
import com.hparty.system.domain.vo.PartyPersonVO;
import com.hparty.system.service.PartyPersonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 人员档案接口（群众 / 申请人 / 积极分子 / 发展对象 / 预备党员 / 正式党员统一档案）。
 * <p>权限说明：人员档案主要服务于「党组织基本情况 - 党员名册」，初始化脚本预置的权限是
 * {@code orginfo:member:list}；这里对写操作采用 OR 语义，兼容后续补充的
 * {@code system:person:add/edit/remove} 按钮权限。</p>
 * <p>所有查询均经过 {@code DataScopeHelper} 数据权限裁剪。</p>
 */
@Tag(name = "人员档案")
@RestController
@RequestMapping("/system/person")
@RequiredArgsConstructor
public class PartyPersonController {

    private final PartyPersonService personService;

    /**
     * 分页查询人员档案。
     *
     * @param query 查询条件：姓名/电话/身份证号模糊，组织/人员状态/政治面貌/党员标识过滤
     * @return 分页结果
     */
    @Operation(summary = "分页查询人员档案")
    @SaCheckPermission(value = {"system:person:list", "orginfo:member:list"}, mode = SaMode.OR)
    @GetMapping("/page")
    public R<PageResult<PartyPersonVO>> page(PartyPersonQuery query) {
        return R.ok(personService.page(query));
    }

    /**
     * 分页查询党员名册（只查党员）。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Operation(summary = "分页查询党员名册")
    @SaCheckPermission(value = {"system:person:list", "orginfo:member:list"}, mode = SaMode.OR)
    @GetMapping("/members")
    public R<PageResult<PartyPersonVO>> members(PartyPersonQuery query) {
        return R.ok(personService.listMembers(query));
    }

    /**
     * 人员统计：总人数、各状态人数、正式党员数、预备党员数（党组织基本情况页面用）。
     *
     * @return 统计结果
     */
    @Operation(summary = "人员统计（党组织基本情况）")
    @SaCheckPermission(value = {"system:person:list", "orginfo:member:list", "orginfo:tree"}, mode = SaMode.OR)
    @GetMapping("/statistics")
    public R<PartyPersonStatVO> statistics() {
        return R.ok(personService.getStatistics());
    }

    /**
     * 按组织查询人员列表。
     *
     * @param orgId 组织ID
     * @return 人员列表
     */
    @Operation(summary = "按组织查询人员列表")
    @SaCheckPermission(value = {"system:person:list", "orginfo:member:list"}, mode = SaMode.OR)
    @GetMapping("/org/{orgId}")
    public R<List<PartyPersonVO>> listByOrg(@PathVariable Long orgId) {
        return R.ok(personService.listByOrg(orgId));
    }

    /**
     * 人员档案详情（含党员扩展信息、党内职务、所属党小组）。
     *
     * @param personId 人员ID
     * @return 详情
     */
    @Operation(summary = "查询人员档案详情")
    @SaCheckPermission(value = {"system:person:query", "orginfo:member:list"}, mode = SaMode.OR)
    @GetMapping("/{personId}")
    public R<PartyPersonDetailVO> detail(@PathVariable Long personId) {
        return R.ok(personService.getPerson(personId));
    }

    /**
     * 新增人员档案。
     *
     * @param dto 人员信息
     * @return 新增的人员ID
     */
    @Operation(summary = "新增人员档案")
    @SaCheckPermission(value = {"system:person:add", "orginfo:member:list"}, mode = SaMode.OR)
    @PostMapping
    public R<Long> add(@Valid @RequestBody PartyPersonDTO dto) {
        return R.ok("新增成功", personService.addPerson(dto));
    }

    /**
     * 修改人员档案。
     *
     * @param dto 人员信息
     * @return 操作结果
     */
    @Operation(summary = "修改人员档案")
    @SaCheckPermission(value = {"system:person:edit", "orginfo:member:list"}, mode = SaMode.OR)
    @PutMapping
    public R<Void> update(@Valid @RequestBody PartyPersonDTO dto) {
        return R.toR(personService.updatePerson(dto));
    }

    /**
     * 删除人员档案（存在进行中的发展党员流程时不允许删除）。
     *
     * @param personId 人员ID
     * @return 操作结果
     */
    @Operation(summary = "删除人员档案")
    @SaCheckPermission(value = {"system:person:remove", "orginfo:member:list"}, mode = SaMode.OR)
    @DeleteMapping("/{personId}")
    public R<Void> remove(@PathVariable Long personId) {
        return R.toR(personService.removePerson(personId));
    }
}
