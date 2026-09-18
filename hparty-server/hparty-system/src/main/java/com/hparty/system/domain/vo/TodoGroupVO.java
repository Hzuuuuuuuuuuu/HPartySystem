package com.hparty.system.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 待办分组（按业务来源分组）。
 */
@Data
public class TodoGroupVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分组编码：DEVELOP / TASK / DUES / TRANSFER */
    private String type;

    /** 分组名称：发展党员 / 三会一课任务 / 党费欠缴 / 组织关系转接 */
    private String typeLabel;

    /** 本组待办条数 */
    private Integer count;

    /** 本组待办明细 */
    private List<TodoItemVO> items = new ArrayList<>();

    public TodoGroupVO() {
    }

    public TodoGroupVO(String type, String typeLabel) {
        this.type = type;
        this.typeLabel = typeLabel;
    }

    /** 用明细重建分组，自动计算条数 */
    public static TodoGroupVO of(String type, String typeLabel, List<TodoItemVO> items) {
        TodoGroupVO group = new TodoGroupVO(type, typeLabel);
        group.setItems(items == null ? new ArrayList<>() : items);
        group.setCount(group.getItems().size());
        return group;
    }

    /** 只带数量的轻量分组（{@code /my/todo/count} 用，不返回明细） */
    public static TodoGroupVO brief(String type, String typeLabel, Integer count) {
        TodoGroupVO group = new TodoGroupVO(type, typeLabel);
        group.setCount(count == null ? 0 : count);
        return group;
    }
}
