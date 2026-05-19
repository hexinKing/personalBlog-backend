package com.blog.backend.service.impl;

import com.blog.backend.common.ClientIpUtils;
import com.blog.backend.common.SecurityUtils;
import com.blog.backend.entity.OperationAuditLog;
import com.blog.backend.entity.User;
import com.blog.backend.mapper.OperationAuditLogMapper;
import com.blog.backend.service.AuditService;
import com.blog.backend.service.UserService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {
    private final OperationAuditLogMapper operationAuditLogMapper;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    @Override
    public void record(String action, String targetType, Long targetId, String detail, boolean success, String errorMessage) {
        try {
            String username = SecurityUtils.currentUsername();
            User user = username == null ? null : userService.getByUsername(username);
            HttpServletRequest request = currentRequest();

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
                auditLog.setRequestIp(ClientIpUtils.getClientIp(request));
                auditLog.setUserAgent(request.getHeader("User-Agent"));
            }
            operationAuditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.warn("记录操作审计失败: {}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private String toJsonDetail(String detail) throws JsonProcessingException {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        return objectMapper.writeValueAsString(Map.of("message", detail));
    }
}
