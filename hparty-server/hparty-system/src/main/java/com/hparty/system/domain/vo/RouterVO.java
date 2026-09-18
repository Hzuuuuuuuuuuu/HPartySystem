package com.hparty.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 前端动态路由节点，由菜单树转换而来。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RouterVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 路由名称（大驼峰），需唯一 */
    private String name;

    /** 路由地址 */
    private String path;

    /** 是否隐藏（visible=0 时为 true） */
    private Boolean hidden;

    /** 重定向地址，仅目录节点使用 */
    private String redirect;

    /** 组件路径。目录为 Layout，菜单为页面组件相对路径 */
    private String component;

    /** 路由元信息 */
    private MetaVO meta;

    /** 子路由 */
    private List<RouterVO> children;

    /**
     * 路由元信息。
     */
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetaVO implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 菜单标题 */
        private String title;

        /** 菜单图标（Ant Design 图标组件名） */
        private String icon;

        /** 是否缓存页面 */
        private Boolean noCache;

        /** 权限标识，前端按钮级控制可用 */
        private String perms;
    }
}
