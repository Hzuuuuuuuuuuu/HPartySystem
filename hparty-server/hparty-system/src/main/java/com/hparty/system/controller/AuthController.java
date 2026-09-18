package com.hparty.system.controller;

import com.hparty.common.core.R;
import com.hparty.system.domain.dto.ChangePasswordDTO;
import com.hparty.system.domain.dto.LoginDTO;
import com.hparty.system.domain.vo.CaptchaVO;
import com.hparty.system.domain.vo.LoginVO;
import com.hparty.system.domain.vo.RouterVO;
import com.hparty.system.domain.vo.UserInfoVO;
import com.hparty.system.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 登录鉴权接口。
 */
@Tag(name = "01-登录鉴权")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "获取图形验证码")
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(authService.captcha());
    }

    @Operation(summary = "账号密码登录")
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return R.ok("登录成功", authService.login(dto, request));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok("已退出登录", null);
    }

    /**
     * 用户自助修改密码。
     * <p>需验证原密码；改完会强制下线，需用新密码重新登录。</p>
     */
    @Operation(summary = "修改自己的密码", description = "需验证原密码；成功后当前会话失效，需重新登录")
    @PostMapping("/changePassword")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok("密码修改成功，请用新密码重新登录", null);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("/userInfo")
    public R<UserInfoVO> userInfo() {
        return R.ok(authService.getUserInfo());
    }

    @Operation(summary = "获取动态路由菜单")
    @GetMapping("/routers")
    public R<List<RouterVO>> routers() {
        return R.ok(authService.getRouters());
    }
}
