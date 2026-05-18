package com.blog.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.backend.common.Result;
import com.blog.backend.entity.OperationAuditLog;
import com.blog.backend.mapper.OperationAuditLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "操作审计")
@RestController
@RequestMapping("/api/admin/audits")
@RequiredArgsConstructor
public class AuditController {
    private final OperationAuditLogMapper operationAuditLogMapper;

    @Operation(summary = "操作审计分页")
    @GetMapping
    public Result<Page<OperationAuditLog>> list(@RequestParam(defaultValue = "1") Integer pageNum,
                                                @RequestParam(defaultValue = "10") Integer pageSize,
                                                @RequestParam(required = false) String action,
                                                @RequestParam(required = false) String operatorUsername) {
        LambdaQueryWrapper<OperationAuditLog> wrapper = new LambdaQueryWrapper<>();
        if (action != null && !action.isBlank()) {
            wrapper.eq(OperationAuditLog::getAction, action);
        }
        if (operatorUsername != null && !operatorUsername.isBlank()) {
            wrapper.like(OperationAuditLog::getOperatorUsername, operatorUsername);
        }
        wrapper.orderByDesc(OperationAuditLog::getCreateTime);
        return Result.success(operationAuditLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper));
    }
}
