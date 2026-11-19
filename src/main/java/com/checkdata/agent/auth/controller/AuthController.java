package com.checkdata.agent.auth.controller;

import com.checkdata.agent.auth.dto.LoginRequest;
import com.checkdata.agent.auth.dto.LoginUserVO;
import com.checkdata.agent.auth.service.AuthService;
import com.checkdata.agent.common.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（登录、登出、当前用户）。
 *
 * 不需要登录即可访问 /auth/login，其他接口需要登录（由 SaTokenConfig 拦截器控制）。
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录。
     */
    @PostMapping("/login")
    public R<LoginUserVO> login(@RequestBody LoginRequest request) {
        return R.ok("登录成功", authService.login(request));
    }

    /**
     * 登出。
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        authService.logout();
        return R.ok();
    }

    /**
     * 当前登录用户完整信息（含角色、部门、数据范围）。
     */
    @GetMapping("/me")
    public R<LoginUserVO> me() {
        return R.ok(authService.getCurrentUser());
    }
}
