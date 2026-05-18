package com.blog.backend.service;

public interface AuditService {
    void record(String action, String targetType, Long targetId, String detail, boolean success, String errorMessage);
}
