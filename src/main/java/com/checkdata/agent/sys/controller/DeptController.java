package com.checkdata.agent.sys.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.checkdata.agent.common.R;
import com.checkdata.agent.sys.entity.SysDept;
import com.checkdata.agent.sys.service.DeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 部门接口（只读树，仅 admin 可访问）。
 */
@RestController
@RequestMapping("/sys/dept")
@RequiredArgsConstructor
@SaCheckRole("admin")
public class DeptController {

    private final DeptService deptService;

    /**
     * 部门树（用于用户编辑时选择部门）。
     */
    @GetMapping("/tree")
    public R<List<SysDept>> tree() {
        return R.ok(deptService.tree());
    }
}
