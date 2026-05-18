package com.blog.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentAuditDTO {
    @NotNull(message = "审核状态不能为空")
    @Min(value = 1, message = "审核状态只能是1或2")
    @Max(value = 2, message = "审核状态只能是1或2")
    private Integer status;

    private String rejectReason;
}
