package com.hparty.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 党组织树节点，用于组织架构图与上级组织选择。
 */
@Data
@Schema(description = "党组织树节点")
public class SysDeptTreeVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 组织 ID */
    @Schema(description = "组织 ID")
    private Long orgId;

    /** 父组织 ID，0=根 */
    @Schema(description = "父组织 ID，0=根")
    private Long parentId;

    /** 组织名称 */
    @Schema(description = "组织名称")
    private String orgName;

    /** 组织类型：1=党委 2=党总支 3=党支部 4=党小组 */
    @Schema(description = "组织类型：1=党委 2=党总支 3=党支部 4=党小组")
    private Integer orgType;

    /** 组织类型中文名 */
    @Schema(description = "组织类型中文名")
    private String orgTypeLabel;

    /** 层级，与 orgType 取值一致 */
    @Schema(description = "层级，与 orgType 取值一致")
    private Integer orgLevel;

    /** 负责人姓名 */
    @Schema(description = "负责人姓名")
    private String leader;

    /** 党员数 */
    @Schema(description = "党员数")
    private Integer memberCount;

    /** 书记姓名 */
    @Schema(description = "书记姓名")
    private String secretaryName;

    /** 成立日期 */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Schema(description = "成立日期")
    private LocalDate foundedDate;

    /** 子组织 */
    @Schema(description = "子组织")
    private List<SysDeptTreeVO> children = new ArrayList<>();
}
