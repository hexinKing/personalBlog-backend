package com.blog.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_generation_record")
public class AiGenerationRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long userId;
    private String taskType;
    private String provider;
    private String modelName;
    private String prompt;
    private String result;
    private Integer status;
    private String errorMessage;
    private LocalDateTime createTime;
}
