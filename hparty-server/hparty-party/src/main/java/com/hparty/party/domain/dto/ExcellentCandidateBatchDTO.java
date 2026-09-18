package com.hparty.party.domain.dto;

import com.hparty.party.domain.entity.ExcellentCandidate;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 先优评选候选人批量保存请求体（全量替换某评选下的候选人）。
 */
@Data
public class ExcellentCandidateBatchDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 评选ID */
    private Long selectionId;

    /** 候选人列表（全量替换） */
    private List<ExcellentCandidate> candidates;
}
