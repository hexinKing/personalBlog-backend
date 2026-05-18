package com.blog.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InteractionDTO {
    @NotNull(message = "互动类型不能为空")
    @Min(value = 1, message = "互动类型不正确")
    @Max(value = 2, message = "互动类型不正确")
    private Integer interactionType;
}
