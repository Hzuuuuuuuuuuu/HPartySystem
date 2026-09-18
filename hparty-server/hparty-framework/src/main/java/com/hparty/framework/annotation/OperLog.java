package com.hparty.framework.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解。标注在 Controller 方法上，由 {@code OperLogAspect} 记录到 sys_oper_log。
 *
 * <pre>{@code
 * @OperLog(title = "发展党员", businessType = BusinessType.APPROVE)
 * @PostMapping("/handle")
 * public R<DevHandleResultVO> handle(@RequestBody DevHandleDTO dto) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperLog {

    /** 模块标题，如「发展党员」 */
    String title() default "";

    /** 业务类型 */
    BusinessType businessType() default BusinessType.OTHER;

    /** 是否记录请求参数 */
    boolean saveRequestData() default true;

    /** 是否记录响应结果 */
    boolean saveResponseData() default true;

    /**
     * 业务类型。
     */
    enum BusinessType {
        OTHER(0, "其它"),
        INSERT(1, "新增"),
        UPDATE(2, "修改"),
        DELETE(3, "删除"),
        APPROVE(4, "审批"),
        EXPORT(5, "导出"),
        UPLOAD(6, "上传"),
        IMPORT(7, "导入"),
        GRANT(8, "授权");

        private final int code;
        private final String label;

        BusinessType(int code, String label) {
            this.code = code;
            this.label = label;
        }

        public int getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }
}
