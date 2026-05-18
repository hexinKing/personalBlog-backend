package com.blog.backend.controller;

import com.blog.backend.common.Result;
import com.blog.backend.service.DashboardService;
import com.blog.backend.vo.DashboardVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "后台看板")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final DashboardService dashboardService;

    @Operation(summary = "数据概览")
    @GetMapping("/dashboard")
    public Result<DashboardVO> dashboard() {
        return Result.success(dashboardService.getOverview());
    }
}
