package com.hparty.system.service;

import com.hparty.framework.security.LoginUser;
import com.hparty.system.domain.entity.SysFile;

/**
 * 业务文件的资源级访问策略。
 *
 * <p>通用文件模块默认只能按组织做数据范围校验；对于发展党员材料这类
 * “同组织内仍需隔离到具体人员/业务关系”的文件，由业务模块提供实现。
 * 这样 hparty-system 不反向依赖具体业务模块。</p>
 */
public interface SysFileAccessPolicy {

    /** 当前策略是否负责该文件。 */
    boolean supports(SysFile file);

    /** 当前登录用户是否可以读取/预览/下载该文件。 */
    boolean canRead(SysFile file, LoginUser user);

    /**
     * 是否允许通过通用 DELETE /file/{fileId} 删除。
     *
     * <p>业务附件通常应通过业务模块删除，以保证业务记录与物理文件一致；
     * 因此默认禁止。</p>
     */
    default boolean canDeleteDirectly(SysFile file, LoginUser user) {
        return false;
    }
}
