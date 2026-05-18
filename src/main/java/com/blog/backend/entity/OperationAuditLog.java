package com.blog.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_audit_log")
public class OperationAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long operatorId;
    private String operatorUsername;
    private String action;
    private String targetType;
    private Long targetId;
    private String requestMethod;
    private String requestPath;
    private String requestIp;
    private String userAgent;
    private String detail;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
}
