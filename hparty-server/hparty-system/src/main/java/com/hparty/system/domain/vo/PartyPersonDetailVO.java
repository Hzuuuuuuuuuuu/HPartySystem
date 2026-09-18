package com.hparty.system.domain.vo;

import com.hparty.system.domain.entity.PartyMemberProfile;
import com.hparty.system.domain.entity.PartyPosition;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * 人员档案详情展示对象：基础档案 + 党员扩展信息 + 党内职务 + 所属党小组。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PartyPersonDetailVO extends PartyPersonVO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 党员扩展信息，非党员为 null */
    private PartyMemberProfile memberProfile;

    /** 党内职务任职记录（现任在前），无记录时为空列表 */
    private List<PartyPosition> positions = Collections.emptyList();
}
