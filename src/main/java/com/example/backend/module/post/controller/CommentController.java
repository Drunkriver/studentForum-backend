package com.example.backend.module.post.controller;

import com.example.backend.common.annotation.LoginRequired;
import com.example.backend.common.api.ApiResult;
import com.example.backend.jwt.AuthInterceptor;
import com.example.backend.module.post.dto.CommentDTO;
import com.example.backend.module.post.entity.Comment;
import com.example.backend.module.post.service.ICommentService;
import com.example.backend.module.post.vo.CommentVO;
import com.example.backend.module.user.entity.User;
import io.swagger.annotations.Api;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

@Api(tags = "评论相关接口")
@RestController
@RequestMapping("/api/comment")
public class CommentController {
    @Resource
    private ICommentService iCommentService;

    @GetMapping("/get_comments")
    public ApiResult<List<CommentVO>> getCommentsByTopicID(@RequestParam(value = "topicid", defaultValue = "1") String topicid) {
        List<CommentVO> lstBmsComment = null;
        try{
            lstBmsComment = iCommentService.getCommentTreeByPostId(topicid);
        } catch (IOException e){

        }

        return ApiResult.success(lstBmsComment);
    }

    @LoginRequired(allowAll = true)
    @PostMapping("/add_comment")
    public ApiResult<Comment> add_comment(@RequestBody CommentDTO dto) {
        User user = AuthInterceptor.getCurrentUser();
        Comment comment = iCommentService.createFirstLevelComment(dto, user);
        return ApiResult.success(comment);
    }
}
