package com.blog.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.backend.dto.CommentAuditDTO;
import com.blog.backend.dto.CommentQueryDTO;
import com.blog.backend.dto.CommentSubmitDTO;
import com.blog.backend.entity.Comment;
import com.blog.backend.vo.CommentVO;

import java.util.List;

public interface CommentService extends IService<Comment> {
    List<Comment> listCommentsByArticleId(Long articleId);
    List<CommentVO> listCommentTreeByArticleId(Long articleId);
    Page<Comment> pageAdminComments(CommentQueryDTO queryDTO);
    void submitComment(CommentSubmitDTO dto, String ip, String userAgent, String username);
    void auditComment(Long id, CommentAuditDTO dto, String auditorUsername);
}
