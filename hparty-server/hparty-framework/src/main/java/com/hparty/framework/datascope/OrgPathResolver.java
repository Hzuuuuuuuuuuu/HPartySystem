package com.hparty.framework.datascope;

import com.hparty.framework.mapper.DataScopeMapper;
import org.springframework.stereotype.Component;

/**
 * 组织路径解析器：把 {@link DataScopeMapper} 透传给静态的 {@link DataScopeHelper}。
 *
 * <p><b>为什么要这样一个类</b>：判断越权需要查询目标组织的物化路径，但
 * {@code DataScopeHelper} 是静态工具类，被二十多处 Service 以
 * {@code DataScopeHelper.apply(wrapper)} 的形式调用。若把它改成 Spring Bean，
 * 所有调用点都要改动并各自注入字段 —— 收益不足以抵消改动面。
 * 这里在启动时把 Mapper 装配到一个静态引用上，保持调用方式不变。</p>
 *
 * <p>引用在 Spring 启动阶段一次性写入，之后只读，不存在并发写入问题。</p>
 */
@Component
public class OrgPathResolver {

    private static DataScopeMapper mapper;

    public OrgPathResolver(DataScopeMapper dataScopeMapper) {
        OrgPathResolver.mapper = dataScopeMapper;
    }

    /**
     * 取组织物化路径。
     *
     * @return 形如 {@code /1/3/7/}；组织不存在或尚未完成装配时返回 null
     */
    static String orgPathOf(Long orgId) {
        if (mapper == null || orgId == null) {
            return null;
        }
        return mapper.selectOrgPath(orgId);
    }

    /**
     * 判断用户是否通过启用中的 CUSTOM 角色获得指定组织访问权。
     */
    static boolean hasCustomOrgAccess(Long userId, Long orgId) {
        if (mapper == null || userId == null || orgId == null) {
            return false;
        }
        return mapper.countCustomOrgAccess(userId, orgId) > 0;
    }
}
