package com.blog.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.backend.common.SecurityUtils;
import com.blog.backend.common.Result;
import com.blog.backend.dto.CommentAuditDTO;
import com.blog.backend.dto.CommentQueryDTO;
import com.blog.backend.dto.CommentSubmitDTO;
import com.blog.backend.entity.Comment;
import com.blog.backend.service.CommentService;
import com.blog.backend.vo.CommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "评论管理")
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "文章评论列表")
    @GetMapping("/article/{articleId}")
    public Result<List<CommentVO>> list(@PathVariable Long articleId) {
        return Result.success(commentService.listCommentTreeByArticleId(articleId));
    }

    @Operation(summary = "后台评论分页")
    @GetMapping("/admin")
    public Result<Page<Comment>> adminList(CommentQueryDTO queryDTO) {
        return Result.success(commentService.pageAdminComments(queryDTO));
    }

    @Operation(summary = "提交评论")
    @PostMapping
    public Result<Void> submit(@Valid @RequestBody CommentSubmitDTO comment, HttpServletRequest request) {
        // IP 和 UA 只进入风控字段，不直接暴露在前台评论数据中。
        commentService.submitComment(comment, request.getRemoteAddr(), request.getHeader("User-Agent"), SecurityUtils.currentUsername());
        return Result.success();
    }

    @Operation(summary = "审核评论")
    @PutMapping("/{id}/status")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody CommentAuditDTO dto) {
        commentService.auditComment(id, dto, SecurityUtils.currentUsername());
        return Result.success();
    }
}
