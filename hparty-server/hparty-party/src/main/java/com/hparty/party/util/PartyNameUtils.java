package com.hparty.party.util;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨表查询结果转换工具。
 *
 * <p>{@code PartyLookupMapper} 的批量查询返回 {@code List<Map<String, Object>>}，
 * 这里统一转成「ID → 名称」映射，供各业务 Service 补全 {@code orgName} 等冗余字段。
 * 各模块都走同一份转换逻辑，避免每处各写一遍拆箱代码。</p>
 */
public final class PartyNameUtils {

    private PartyNameUtils() {
    }

    /**
     * 把 {@code selectOrgNames} 的查询结果转成 orgId → orgName 映射。
     */
    public static Map<Long, String> toOrgNameMap(List<Map<String, Object>> rows) {
        Map<Long, String> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Long id = toLong(row.get("org_id"));
            if (id != null) {
                Object name = row.get("org_name");
                map.put(id, name == null ? null : String.valueOf(name));
            }
        }
        return map;
    }

    /** JDBC 返回的数值可能是 Integer/Long/BigDecimal，统一转 Long */
    public static Long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    /** JDBC 返回的数值统一转 BigDecimal */
    public static BigDecimal toDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal d) {
            return d;
        }
        return value instanceof Number n ? new BigDecimal(n.toString()) : null;
    }
}
