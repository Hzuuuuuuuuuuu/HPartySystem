package com.hparty.system.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hparty.common.constant.Constants;
import com.hparty.common.core.ResultCode;
import com.hparty.common.enums.DataScope;
import com.hparty.common.exception.BizException;
import com.hparty.common.util.PasswordPolicy;
import com.hparty.framework.security.LoginUser;
import com.hparty.framework.security.SecurityUtils;
import com.hparty.system.domain.dto.ChangePasswordDTO;
import com.hparty.system.domain.dto.LoginDTO;
import com.hparty.system.domain.entity.PartyPerson;
import com.hparty.system.domain.entity.SysDept;
import com.hparty.system.domain.entity.SysLoginLog;
import com.hparty.system.domain.entity.SysMenu;
import com.hparty.system.domain.entity.SysUser;
import com.hparty.system.domain.vo.*;
import com.hparty.system.mapper.AuthMapper;
import com.hparty.system.mapper.PartyPersonMapper;
import com.hparty.system.mapper.SysDeptMapper;
import com.hparty.system.mapper.SysLoginLogMapper;
import com.hparty.system.mapper.SysMenuMapper;
import com.hparty.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 登录鉴权服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthMapper authMapper;
    private final SysUserMapper userMapper;
    private final SysMenuMapper menuMapper;
    private final SysDeptMapper deptMapper;
    private final SysLoginLogMapper loginLogMapper;
    private final PartyPersonMapper personMapper;
    private final StringRedisTemplate redisTemplate;

    /** 是否开启验证码 */
    @Value("${hparty.captcha.enabled:true}")
    private boolean captchaEnabled;

    /** 令牌有效期（秒） */
    @Value("${sa-token.timeout:7200}")
    private long tokenTimeout;

    /** 密码有效期（天），0 或负数表示不过期 */
    @Value("${hparty.password.expire-days:90}")
    private int passwordExpireDays;

    // ==================== 验证码 ====================

    /**
     * 生成图形验证码，存入 Redis 供登录时比对。
     */
    public CaptchaVO captcha() {
        if (!captchaEnabled) {
            return new CaptchaVO(null, null, false);
        }
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(130, 44, 4, 20);
        String uuid = IdUtil.fastSimpleUUID();

        redisTemplate.opsForValue().set(
                Constants.CACHE_CAPTCHA + uuid,
                captcha.getCode(),
                Duration.ofMinutes(Constants.CAPTCHA_EXPIRE_MINUTES));

        return new CaptchaVO(uuid, captcha.getImageBase64Data(), true);
    }

    // ==================== 登录 ====================

    /**
     * 账号密码登录。
     */
    public LoginVO login(LoginDTO dto, HttpServletRequest request) {
        String ip = getClientIp(request);

        // 1. 登录失败次数限制
        String failKey = Constants.CACHE_LOGIN_FAIL + dto.getUsername();
        String failCount = redisTemplate.opsForValue().get(failKey);
        if (failCount != null && Integer.parseInt(failCount) >= Constants.LOGIN_FAIL_LIMIT) {
            recordLoginLog(dto.getUsername(), ip, request, 0, "连续登录失败次数过多，账号已锁定");
            throw new BizException(String.format(
                    "连续登录失败 %d 次，账号已锁定，请 10 分钟后再试。", Constants.LOGIN_FAIL_LIMIT));
        }

        // 2. 验证码校验
        if (captchaEnabled) {
            validateCaptcha(dto);
        }

        // 3. 查账号
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));

        if (user == null || !BCrypt.checkpw(dto.getPassword(), user.getPassword())) {
            long count = incrementFail(failKey);
            recordLoginLog(dto.getUsername(), ip, request, 0, "账号或密码错误");
            long remain = Math.max(0, Constants.LOGIN_FAIL_LIMIT - count);
            throw new BizException(remain > 0
                    ? String.format("账号或密码错误，还可尝试 %d 次。", remain)
                    : "账号或密码错误，账号已锁定，请 10 分钟后再试。");
        }

        if (user.getStatus() != null && user.getStatus() == Constants.STATUS_DISABLED) {
            recordLoginLog(dto.getUsername(), ip, request, 0, "账号已停用");
            throw new BizException("账号已停用，请联系管理员。");
        }

        // 4. 清空失败计数
        redisTemplate.delete(failKey);

        // 5. 装配登录用户并建立会话
        LoginUser loginUser = buildLoginUser(user);

        StpUtil.login(user.getUserId());
        StpUtil.getSession().set(Constants.SESSION_USER_ID, loginUser);

        // 6. 记录登录信息
        SysUser update = new SysUser();
        update.setUserId(user.getUserId());
        update.setLoginIp(ip);
        update.setLoginDate(LocalDateTime.now());
        userMapper.updateById(update);

        recordLoginLog(user.getUsername(), ip, request, 1, "登录成功");

        log.info("用户登录成功: {} ({}), 角色={}, 数据范围={}",
                user.getUsername(), ip, loginUser.getRoles(),
                DataScope.of(loginUser.getDataScope()).getLabel());

        LoginVO vo = new LoginVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setTokenName(StpUtil.getTokenName());
        vo.setExpiresIn(tokenTimeout);
        vo.setPasswordExpireDays(passwordExpireDays);
        applyPasswordCheck(vo, user);
        return vo;
    }

    /**
     * 判断是否需要强制修改密码。
     *
     * <p>两种情况置 true：从未改过初始密码（{@code pwdUpdateDate} 为空），
     * 或密码已超过 {@code hparty.password.expire-days} 天。</p>
     *
     * <p>这个判断放在登录时做，而不是拦截每个请求 —— 后者会让「改密接口本身」
     * 也被拦住，形成死锁。</p>
     */
    private void applyPasswordCheck(LoginVO vo, SysUser user) {
        LocalDateTime pwdUpdate = user.getPwdUpdateDate();
        if (pwdUpdate == null) {
            vo.setNeedChangePwd(true);
            vo.setChangePwdReason("您尚未修改过初始密码，为了账号安全，请先设置新密码。");
            return;
        }
        if (passwordExpireDays > 0) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(pwdUpdate, LocalDateTime.now());
            if (days >= passwordExpireDays) {
                vo.setNeedChangePwd(true);
                vo.setChangePwdReason(String.format(
                        "您的密码已使用 %d 天，超过 %d 天的有效期，请设置新密码。", days, passwordExpireDays));
            }
        }
    }

    // ==================== 修改密码 ====================

    /**
     * 用户自助修改密码。
     *
     * <p><b>必须验证原密码</b>：否则会话被劫持后，攻击者可以直接改掉密码把账号锁死。
     * 改完强制下线重新登录 —— 既让用户确认新密码可用，也顺带清掉可能已泄漏的旧会话。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordDTO dto) {
        LoginUser loginUser = SecurityUtils.getLoginUser();

        SysUser user = userMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BizException("用户不存在");
        }
        if (!BCrypt.checkpw(dto.getOldPassword(), user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (dto.getOldPassword().equals(dto.getNewPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }

        String reason = PasswordPolicy.validate(dto.getNewPassword(), user.getUsername());
        if (reason != null) {
            throw new BizException(reason);
        }

        SysUser patch = new SysUser();
        patch.setUserId(user.getUserId());
        patch.setPassword(BCrypt.hashpw(dto.getNewPassword()));
        patch.setPwdUpdateDate(LocalDateTime.now());
        userMapper.updateById(patch);

        log.info("用户 {} 修改了自己的密码，会话已失效", user.getUsername());

        // 强制重新登录
        StpUtil.logout(user.getUserId());
    }

    /** 校验验证码 */
    private void validateCaptcha(LoginDTO dto) {
        if (StrUtil.isBlank(dto.getUuid()) || StrUtil.isBlank(dto.getCode())) {
            throw new BizException("请输入验证码");
        }
        String cacheKey = Constants.CACHE_CAPTCHA + dto.getUuid();
        String cached = redisTemplate.opsForValue().get(cacheKey);

        // 验证码一次性使用，无论对错都删除
        redisTemplate.delete(cacheKey);

        if (cached == null) {
            throw new BizException("验证码已过期，请重新获取");
        }
        if (!cached.equalsIgnoreCase(dto.getCode().trim())) {
            throw new BizException("验证码错误");
        }
    }

    private long incrementFail(String failKey) {
        Long count = redisTemplate.opsForValue().increment(failKey);
        redisTemplate.expire(failKey, Duration.ofMinutes(10));
        return count == null ? 1 : count;
    }

    // ==================== 登出 ====================

    public void logout() {
        try {
            Object obj = StpUtil.getSession().get(Constants.SESSION_USER_ID);
            if (obj instanceof LoginUser user) {
                log.info("用户登出: {}", user.getUsername());
            }
        } catch (Exception ignored) {
            // 会话已失效，直接走登出即可
        }
        StpUtil.logout();
    }

    // ==================== 用户信息与路由 ====================

    /**
     * 拉取当前登录用户信息，前端启动时调用一次。
     */
    public UserInfoVO getUserInfo() {
        LoginUser user = SecurityUtils.getLoginUser();

        UserInfoVO vo = new UserInfoVO();
        vo.setUserId(user.getUserId());
        vo.setUsername(user.getUsername());
        vo.setNickName(user.getNickName());
        vo.setAvatar(user.getAvatar());
        vo.setPersonId(user.getPersonId());
        vo.setPersonName(user.getPersonName());
        vo.setOrgId(user.getOrgId());
        vo.setOrgName(user.getOrgName());
        vo.setOrgType(user.getOrgType());
        vo.setOrgPath(user.getOrgPath());
        vo.setDataScope(user.getDataScope());
        vo.setSuperAdmin(user.isSuperAdmin());
        vo.setRoles(user.getRoles());
        vo.setPerms(user.getPerms());
        return vo;
    }

    /**
     * 构建前端动态路由树。
     */
    public List<RouterVO> getRouters() {
        LoginUser user = SecurityUtils.getLoginUser();

        List<SysMenu> menus;
        if (user.isSuperAdmin()) {
            // 超级管理员取全部可见菜单，避免角色菜单表未配全时看不到入口
            menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .in(SysMenu::getMenuType, List.of("M", "C"))
                    .eq(SysMenu::getStatus, Constants.STATUS_NORMAL)
                    .orderByAsc(SysMenu::getParentId)
                    .orderByAsc(SysMenu::getOrderNum));
        } else {
            menus = authMapper.selectMenusByUserId(user.getUserId());
        }

        return buildRouters(menus);
    }

    /** 由菜单列表构建路由树 */
    private List<RouterVO> buildRouters(List<SysMenu> menus) {
        Map<Long, List<SysMenu>> byParent = menus.stream()
                .collect(Collectors.groupingBy(m -> m.getParentId() == null ? 0L : m.getParentId()));

        List<RouterVO> routers = new ArrayList<>();
        for (SysMenu top : byParent.getOrDefault(0L, List.of())) {
            if ("M".equals(top.getMenuType())) {
                // 目录：本身作为 Layout，子菜单挂在下面
                RouterVO router = new RouterVO();
                router.setName(toRouteName(top));
                router.setPath(top.getPath());
                router.setComponent("Layout");
                router.setHidden(top.getVisible() != null && top.getVisible() == 0);
                router.setMeta(buildMeta(top));

                List<RouterVO> children = buildChildren(byParent, top.getMenuId());
                if (children.isEmpty()) {
                    continue;
                }
                router.setChildren(children);
                router.setRedirect(children.get(0).getPath());
                routers.add(router);
            } else {
                // 顶层菜单：包一层 Layout，使其能显示在侧边栏
                RouterVO wrapper = new RouterVO();
                wrapper.setPath("/");
                wrapper.setComponent("Layout");
                wrapper.setHidden(top.getVisible() != null && top.getVisible() == 0);

                RouterVO child = new RouterVO();
                child.setName(toRouteName(top));
                child.setPath(StrUtil.removePrefix(top.getPath(), "/"));
                child.setComponent(top.getComponent());
                child.setMeta(buildMeta(top));

                wrapper.setChildren(List.of(child));
                routers.add(wrapper);
            }
        }
        return routers;
    }

    private List<RouterVO> buildChildren(Map<Long, List<SysMenu>> byParent, Long parentId) {
        List<RouterVO> list = new ArrayList<>();
        for (SysMenu menu : byParent.getOrDefault(parentId, List.of())) {
            RouterVO router = new RouterVO();
            router.setName(toRouteName(menu));
            router.setPath(StrUtil.removePrefix(menu.getPath(), "/"));
            router.setComponent(menu.getComponent());
            router.setHidden(menu.getVisible() != null && menu.getVisible() == 0);
            router.setMeta(buildMeta(menu));

            List<RouterVO> grandChildren = buildChildren(byParent, menu.getMenuId());
            if (!grandChildren.isEmpty()) {
                router.setChildren(grandChildren);
            }
            list.add(router);
        }
        return list;
    }

    private RouterVO.MetaVO buildMeta(SysMenu menu) {
        RouterVO.MetaVO meta = new RouterVO.MetaVO();
        meta.setTitle(menu.getMenuName());
        meta.setIcon(menu.getIcon());
        meta.setNoCache(menu.getIsCache() != null && menu.getIsCache() == 0);
        meta.setPerms(menu.getPerms());
        return meta;
    }

    /** 路径转路由名（大驼峰），保证唯一 */
    private String toRouteName(SysMenu menu) {
        String path = menu.getPath() == null ? String.valueOf(menu.getMenuId())
                : menu.getPath().replaceAll("[^A-Za-z0-9]+", "-");
        String name = Arrays.stream(path.split("-"))
                .filter(s -> !s.isEmpty())
                .map(s -> Character.toUpperCase(s.charAt(0)) + s.substring(1))
                .collect(Collectors.joining());
        return name.isEmpty() ? "Menu" + menu.getMenuId() : name;
    }

    // ==================== 内部方法 ====================

    /**
     * 装配登录用户：把账号、人员档案、组织、角色、权限、数据范围聚合起来塞进会话。
     * <p>登录时一次性算好，后续鉴权直接读会话，避免每次请求都查库。</p>
     */
    private LoginUser buildLoginUser(SysUser user) {
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getUserId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickName(user.getNickName());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setPersonId(user.getPersonId());
        loginUser.setOrgId(user.getOrgId());

        // 角色与权限
        loginUser.setRoles(new HashSet<>(authMapper.selectRoleKeys(user.getUserId())));
        loginUser.setPerms(new HashSet<>(authMapper.selectPerms(user.getUserId())));

        boolean superAdmin = loginUser.getRoles().contains(Constants.SUPER_ADMIN_ROLE)
                || Constants.SUPER_ADMIN_USER_ID.equals(user.getUserId());
        loginUser.setSuperAdmin(superAdmin);

        // 数据范围：多角色取最宽
        Integer dataScope = authMapper.selectDataScope(user.getUserId());
        loginUser.setDataScope(dataScope == null ? DataScope.SELF.getCode() : dataScope);

        // 人员档案
        if (user.getPersonId() != null) {
            PartyPerson person = personMapper.selectById(user.getPersonId());
            if (person != null) {
                loginUser.setPersonName(person.getName());
                if (loginUser.getAvatar() == null) {
                    loginUser.setAvatar(person.getAvatar());
                }
            }
        }

        // 所属组织
        if (user.getOrgId() != null) {
            SysDept dept = deptMapper.selectById(user.getOrgId());
            if (dept != null) {
                loginUser.setOrgName(dept.getOrgName());
                loginUser.setOrgType(dept.getOrgType());
                loginUser.setOrgPath(dept.getOrgPath());
            }
        }

        return loginUser;
    }

    private void recordLoginLog(String username, String ip, HttpServletRequest request,
                                int status, String msg) {
        try {
            SysLoginLog logEntity = new SysLoginLog();
            logEntity.setUsername(username);
            logEntity.setIpaddr(ip);
            logEntity.setLoginLocation("-");
            logEntity.setBrowser(parseBrowser(request));
            logEntity.setOs(parseOs(request));
            logEntity.setStatus(status);
            logEntity.setMsg(msg);
            logEntity.setLoginTime(LocalDateTime.now());
            loginLogMapper.insert(logEntity);
        } catch (Exception e) {
            // 日志失败不能影响登录主流程
            log.warn("记录登录日志失败", e);
        }
    }

    private String parseBrowser(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (StrUtil.isBlank(ua)) {
            return "-";
        }
        if (ua.contains("Edg")) return "Edge";
        if (ua.contains("Chrome")) return "Chrome";
        if (ua.contains("Firefox")) return "Firefox";
        if (ua.contains("Safari")) return "Safari";
        return "其他";
    }

    private String parseOs(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (StrUtil.isBlank(ua)) {
            return "-";
        }
        if (ua.contains("Windows")) return "Windows";
        if (ua.contains("Mac OS")) return "macOS";
        if (ua.contains("Android")) return "Android";
        if (ua.contains("iPhone") || ua.contains("iPad")) return "iOS";
        if (ua.contains("Linux")) return "Linux";
        return "其他";
    }

    /** 取客户端真实 IP，兼容 Nginx 反向代理 */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP", "WL-Proxy-Client-IP"};
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (StrUtil.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                int idx = ip.indexOf(',');
                return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
            }
        }
        String ip = request.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) ? "127.0.0.1" : ip;
    }
}
