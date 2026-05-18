package com.blog.backend.service.impl;

import com.blog.backend.common.SecurityUtils;
import com.blog.backend.entity.OperationAuditLog;
import com.blog.backend.entity.User;
import com.blog.backend.mapper.OperationAuditLogMapper;
import com.blog.backend.service.AuditService;
import com.blog.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private final OperationAuditLogMapper operationAuditLogMapper;
    private final UserService userService;

    @Override
    public void record(String action, String targetType, Long targetId, String detail, boolean success, String errorMessage) {
        try {
            String username = SecurityUtils.currentUsername();
            User user = username == null ? null : userService.getByUsername(username);
            HttpServletRequest request = currentRequest();

            // 审计日志尽量自包含，后续排查误操作时不依赖业务表的当前状态。
            OperationAuditLog auditLog = new OperationAuditLog();
            auditLog.setOperatorId(user == null ? null : user.getId());
            auditLog.setOperatorUsername(username);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setDetail(toJsonDetail(detail));
            auditLog.setStatus(success ? 1 : 0);
            auditLog.setErrorMessage(errorMessage);
            auditLog.setCreateTime(LocalDateTime.now());
            if (request != null) {
                auditLog.setRequestMethod(request.getMethod());
                auditLog.setRequestPath(request.getRequestURI());
                auditLog.setRequestIp(request.getRemoteAddr());
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            operationAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("记录操作审计失败: {}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes.getRequest();
        }
        return null;
    }

    private String toJsonDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return "{\"message\":\"" + detail
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n") + "\"}";
    }
}
