package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.backend.common.CryptoUtils;
import com.blog.backend.dto.CommentAuditDTO;
import com.blog.backend.dto.CommentQueryDTO;
import com.blog.backend.dto.CommentSubmitDTO;
import com.blog.backend.entity.Article;
import com.blog.backend.entity.Comment;
import com.blog.backend.entity.SensitiveWord;
import com.blog.backend.entity.User;
import com.blog.backend.mapper.ArticleMapper;
import com.blog.backend.mapper.CommentMapper;
import com.blog.backend.mapper.SensitiveWordMapper;
import com.blog.backend.service.CommentService;
import com.blog.backend.service.UserService;
import com.blog.backend.vo.CommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment> implements CommentService {
    private final ArticleMapper articleMapper;
    private final UserService userService;
    private final SensitiveWordMapper sensitiveWordMapper;

    @Override
    public List<Comment> listCommentsByArticleId(Long articleId) {
        return baseMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, 1) // 只显示审核通过的
                .orderByDesc(Comment::getCreateTime));
    }

    @Override
    public List<CommentVO> listCommentTreeByArticleId(Long articleId) {
        List<Comment> comments = baseMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, 1)
                .orderByAsc(Comment::getCreateTime));
        return buildTree(comments);
    }

    @Override
    public Page<Comment> pageAdminComments(CommentQueryDTO queryDTO) {
        Page<Comment> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Comment> wrapper = new LambdaQueryWrapper<>();
        if (queryDTO.getArticleId() != null) {
            wrapper.eq(Comment::getArticleId, queryDTO.getArticleId());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Comment::getStatus, queryDTO.getStatus());
        }
        if (queryDTO.getNickname() != null && !queryDTO.getNickname().isBlank()) {
            wrapper.like(Comment::getNickname, queryDTO.getNickname());
        }
        if (queryDTO.getEmail() != null && !queryDTO.getEmail().isBlank()) {
            wrapper.like(Comment::getEmail, queryDTO.getEmail());
        }
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isBlank()) {
            wrapper.like(Comment::getContent, queryDTO.getKeyword());
        }
        wrapper.orderByDesc(Comment::getCreateTime);
        return page(page, wrapper);
    }

    @Override
    @Transactional
    public void submitComment(CommentSubmitDTO dto, String ip, String userAgent, String username) {
        Article article = articleMapper.selectById(dto.getArticleId());
        if (article == null || !Integer.valueOf(1).equals(article.getStatus())) {
            throw new RuntimeException("文章不存在或未发布");
        }
        if (Integer.valueOf(0).equals(article.getAllowComment())) {
            throw new RuntimeException("该文章已关闭评论");
        }

        Comment parent = null;
        if (dto.getParentId() != null) {
            parent = getById(dto.getParentId());
            if (parent == null || !dto.getArticleId().equals(parent.getArticleId())) {
                throw new RuntimeException("父评论不存在");
            }
        }

        User user = username == null ? null : userService.getByUsername(username);
        Comment comment = new Comment();
        BeanUtils.copyProperties(dto, comment);
        comment.setUserId(user == null ? null : user.getId());
        comment.setRootId(parent == null ? null : (parent.getRootId() == null ? parent.getId() : parent.getRootId()));
        comment.setStatus(resolveInitialStatus(dto.getContent()));
        comment.setLikeCount(0);
        comment.setIpHash(ip == null ? null : CryptoUtils.sha256(ip));
        comment.setUserAgent(userAgent);
        comment.setCreateTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        comment.setDeleted(0);
        save(comment);

        // 审核通过才计入文章评论数，避免后台待审核评论污染前台统计。
        if (Integer.valueOf(1).equals(comment.getStatus())) {
            article.setCommentCount((article.getCommentCount() == null ? 0 : article.getCommentCount()) + 1);
            articleMapper.updateById(article);
        }
    }

    @Override
    @Transactional
    public void auditComment(Long id, CommentAuditDTO dto, String auditorUsername) {
        Comment comment = getById(id);
        if (comment == null) {
            throw new RuntimeException("评论不存在");
        }
        User auditor = auditorUsername == null ? null : userService.getByUsername(auditorUsername);
        Integer oldStatus = comment.getStatus();
        comment.setStatus(dto.getStatus());
        comment.setRejectReason(dto.getRejectReason());
        comment.setAuditUserId(auditor == null ? null : auditor.getId());
        comment.setAuditTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        updateById(comment);

        if (!Integer.valueOf(1).equals(oldStatus) && Integer.valueOf(1).equals(dto.getStatus())) {
            Article article = articleMapper.selectById(comment.getArticleId());
            if (article != null) {
                article.setCommentCount((article.getCommentCount() == null ? 0 : article.getCommentCount()) + 1);
                articleMapper.updateById(article);
            }
        }
    }

    private List<CommentVO> buildTree(List<Comment> comments) {
        Map<Long, CommentVO> map = new LinkedHashMap<>();
        for (Comment comment : comments) {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(comment, vo);
            map.put(comment.getId(), vo);
        }

        // 使用内存组树，前台拿到的数据就能直接渲染评论和回复层级。
        List<CommentVO> roots = new ArrayList<>();
        for (CommentVO vo : map.values()) {
            if (vo.getParentId() == null || !map.containsKey(vo.getParentId())) {
                roots.add(vo);
            } else {
                map.get(vo.getParentId()).getChildren().add(vo);
            }
        }
        return roots;
    }

    private Integer resolveInitialStatus(String content) {
        List<SensitiveWord> words = sensitiveWordMapper.selectList(new LambdaQueryWrapper<SensitiveWord>()
                .eq(SensitiveWord::getStatus, 1));
        if (words == null || words.isEmpty()) {
            // 默认仍进入审核，保持原系统“先审后发”的内容安全策略。
            return 0;
        }
        List<SensitiveWord> matched = words.stream()
                .filter(word -> content != null && content.contains(word.getWord()))
                .collect(Collectors.toList());
        if (matched.stream().anyMatch(word -> Integer.valueOf(2).equals(word.getLevel()))) {
            return 2;
        }
        return 0;
    }
}
