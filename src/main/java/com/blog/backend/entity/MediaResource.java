package com.blog.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("media_resource")
public class MediaResource {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long uploaderId;
    private String fileName;
    private String originalName;
    private String url;
    private String objectKey;
    private String storageType;
    private String mimeType;
    private Long fileSize;
    private Integer width;
    private Integer height;
    private Integer usageStatus;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
