package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.backend.common.ArticleStatus;
import com.blog.backend.common.BusinessException;
import com.blog.backend.common.CommentStatus;
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
                .eq(Comment::getStatus, CommentStatus.APPROVED)
                .orderByDesc(Comment::getCreateTime));
    }

    @Override
    public List<CommentVO> listCommentTreeByArticleId(Long articleId) {
        List<Comment> comments = baseMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getArticleId, articleId)
                .eq(Comment::getStatus, CommentStatus.APPROVED)
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
        if (article == null || !Integer.valueOf(ArticleStatus.PUBLISHED).equals(article.getStatus())) {
            throw new BusinessException(404, "文章不存在或未发布");
        }
        if (Integer.valueOf(0).equals(article.getAllowComment())) {
            throw new BusinessException("该文章已关闭评论");
        }

        Comment parent = null;
        if (dto.getParentId() != null) {
            parent = getById(dto.getParentId());
            if (parent == null || !dto.getArticleId().equals(parent.getArticleId())) {
                throw new BusinessException(404, "父评论不存在");
            }
        }

        User user = username == null ? null : userService.getByUsername(username);
        Comment comment = new Comment();
        applyCommentFields(dto, comment);
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

        if (Integer.valueOf(CommentStatus.APPROVED).equals(comment.getStatus())) {
            adjustCommentCount(article, 1);
        }
    }

    @Override
    @Transactional
    public void auditComment(Long id, CommentAuditDTO dto, String auditorUsername) {
        Comment comment = getById(id);
        if (comment == null) {
            throw new BusinessException(404, "评论不存在");
        }
        User auditor = auditorUsername == null ? null : userService.getByUsername(auditorUsername);
        Integer oldStatus = comment.getStatus();
        comment.setStatus(dto.getStatus());
        comment.setRejectReason(dto.getRejectReason());
        comment.setAuditUserId(auditor == null ? null : auditor.getId());
        comment.setAuditTime(LocalDateTime.now());
        comment.setUpdateTime(LocalDateTime.now());
        updateById(comment);

        if (!statusEquals(oldStatus, CommentStatus.APPROVED) && statusEquals(dto.getStatus(), CommentStatus.APPROVED)) {
            Article article = articleMapper.selectById(comment.getArticleId());
            adjustCommentCount(article, 1);
        } else if (statusEquals(oldStatus, CommentStatus.APPROVED) && !statusEquals(dto.getStatus(), CommentStatus.APPROVED)) {
            Article article = articleMapper.selectById(comment.getArticleId());
            adjustCommentCount(article, -1);
        }
    }

    private void applyCommentFields(CommentSubmitDTO dto, Comment comment) {
        comment.setArticleId(dto.getArticleId());
        comment.setParentId(dto.getParentId());
        comment.setNickname(dto.getNickname());
        comment.setEmail(dto.getEmail());
        comment.setContent(dto.getContent());
    }

    private void adjustCommentCount(Article article, int delta) {
        if (article == null) {
            return;
        }
        int current = article.getCommentCount() == null ? 0 : article.getCommentCount();
        article.setCommentCount(Math.max(0, current + delta));
        articleMapper.updateById(article);
    }

    private boolean statusEquals(Integer actual, int expected) {
        return Integer.valueOf(expected).equals(actual);
    }

    private List<CommentVO> buildTree(List<Comment> comments) {
        Map<Long, CommentVO> map = new LinkedHashMap<>();
        for (Comment comment : comments) {
            CommentVO vo = new CommentVO();
            BeanUtils.copyProperties(comment, vo);
            map.put(comment.getId(), vo);
        }

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
            return CommentStatus.PENDING;
        }
        boolean reject = words.stream()
                .filter(word -> word.getWord() != null && !word.getWord().isBlank())
                .filter(word -> content != null && content.contains(word.getWord()))
                .anyMatch(word -> Integer.valueOf(2).equals(word.getLevel()));
        return reject ? CommentStatus.REJECTED : CommentStatus.PENDING;
    }
}
