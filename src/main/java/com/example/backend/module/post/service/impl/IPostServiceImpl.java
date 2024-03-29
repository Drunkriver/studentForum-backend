package com.example.backend.module.post.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.backend.common.api.ErrorResponse;
import com.example.backend.module.post.dto.CreateTopicDTO;
import com.example.backend.module.post.entity.*;
import com.example.backend.module.post.mapper.*;
import com.example.backend.module.post.service.*;
import com.example.backend.module.post.vo.CommentVO;
import com.example.backend.module.post.vo.PostVO;
import com.example.backend.module.user.entity.User;
import com.example.backend.module.user.mapper.UserMapper;
import com.example.backend.module.user.service.IUserService;
import com.example.backend.module.user.vo.ProfileVO;
import com.vdurmont.emoji.EmojiParser;
import javafx.geometry.Pos;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.backend.common.api.ApiErrorCode.POST_NOT_EXISTS;
import static com.example.backend.common.api.ApiErrorCode.USER_NOT_EXISTS;

@Service
public class IPostServiceImpl extends ServiceImpl<TopicMapper, Post> implements IPostService {
    @Resource
    private TagMapper tagMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private PostPraiseMapper postPraiseMapper;
    @Resource
    private PostCollectMapper postCollectMapper;
    @Resource
    private CommentMapper commentMapper;
    @Resource
    private IPostPraiseService iPostPraiseService;
    @Resource
    private IPostCollectService iPostCollectService;

    @Autowired
    @Lazy
    private ITagService iTagService;

    @Autowired
    private IUserService iUserService;

    @Autowired
    private ITopicTagService ITopicTagService;

    // 获取分页下的文章信息
    @Override
    public Page<PostVO> getList(Page<PostVO> page, String tab) {
        // 查询话题
        Page<PostVO> iPage = this.baseMapper.selectListAndPage(page, tab);
        Page<PostVO> res = new Page<PostVO>(page.getCurrent(),page.getSize());
        res.setTotal(iPage.getTotal());
        List<PostVO> list = new ArrayList<>();
        for (PostVO postVO : iPage.getRecords()) {
            // 点赞, 收藏, 评论数
            List<PostPraise> praiseList = iPostPraiseService.getBaseMapper().selectList(new LambdaQueryWrapper<PostPraise>().eq(PostPraise::getPostId,postVO.getId()));
            postVO.setPraises(praiseList.size());
            List<PostCollect> collectList = iPostCollectService.getBaseMapper().selectList(new LambdaQueryWrapper<PostCollect>().eq(PostCollect::getPostId,postVO.getId()));
            postVO.setCollects(collectList.size());
            List<Comment> commentList = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getTopicId,postVO.getId()));
            postVO.setComments(commentList.size());
            // tag相关
            List<Tag> tags = new ArrayList<>();
            // 标签
            List<TopicTag> postTag = ITopicTagService.getBaseMapper().selectList(new LambdaQueryWrapper<TopicTag>().eq(TopicTag::getTopicId,postVO.getId()));
            for (TopicTag topicTag : postTag) {
                Tag tag = iTagService.getById(topicTag.getTagId());
                tags.add(tag);
            }
            postVO.setTags(tags);
            list.add(postVO);
        }
        res.setRecords(list);
        return res;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO create(CreateTopicDTO dto, User user) {
        // 创建帖子对象，封装
        Post post = Post.builder()
                .userId(user.getId())
                .title(dto.getTitle())
                .content(EmojiParser.parseToAliases(dto.getContent()))
                .createTime(new Date())
                .build();
        this.baseMapper.insert(post);  // 将该对象插入表单
        post = this.getBaseMapper().selectOne(new LambdaQueryWrapper<Post>().eq(Post::getId, post.getId()));

        // 插入标签
        for (String tag : dto.getTags()) {
            Tag existTag = iTagService.getBaseMapper().selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName,tag));
            if (existTag == null){
                Tag newTag = Tag.builder().name(tag).topicCount(1).build();
                iTagService.getBaseMapper().insert(newTag);
            }
            else{
                existTag.setTopicCount(existTag.getTopicCount()+1);
                iTagService.getBaseMapper().updateById(existTag);
            }
            TopicTag postTag = TopicTag.builder()
                    .tagId(iTagService.getBaseMapper().selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName,tag)).getId())
                    .topicId(post.getId())
                    .build();
            ITopicTagService.getBaseMapper().insert(postTag);
        }
        PostVO postVO = this.changePostToPostVO(post);
        return postVO;
    }

    // 返回指定id的帖子
    @Override
    public Map<String, Object> viewTopic(String id) throws IOException {
        Map<String, Object> map = new HashMap<>(16);
        Post topic = this.baseMapper.selectById(id);  // topic 为指定的帖子对象
        if (topic == null){
            ErrorResponse.sendJsonMessage(POST_NOT_EXISTS);
            return null;
        }
        // 查询话题详情
        topic.setView(topic.getView() + 1);  // view表示帖子的访问次数，访问一次加一一次
        this.baseMapper.updateById(topic);
        // emoji转码
        topic.setContent(EmojiParser.parseToUnicode(topic.getContent()));
        Map<String,Object> topicMap = JSONObject.parseObject(JSON.toJSONString(topic));

        // 点赞、收藏、评论
        List<PostPraise> praiseList = iPostPraiseService.getBaseMapper().selectList(new LambdaQueryWrapper<PostPraise>().eq(PostPraise::getPostId,topic.getId()));
        topicMap.put("praises",praiseList.size());
        List<PostCollect> collectList = iPostCollectService.getBaseMapper().selectList(new LambdaQueryWrapper<PostCollect>().eq(PostCollect::getPostId,topic.getId()));
        topicMap.put("collects",collectList.size());
        List<Comment> commentList = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getTopicId,topic.getId()));
        topicMap.put("comments",commentList.size());

        map.put("topic", topicMap);
        // 标签  找到帖子绑定的所有标签
        QueryWrapper<TopicTag> wrapper = new QueryWrapper<>();
        wrapper.lambda().eq(TopicTag::getTopicId, topic.getId());
        List<Tag> tags = new ArrayList<>();
        List<TopicTag>  topicTags = ITopicTagService.getBaseMapper().selectList(wrapper);
        if(topicTags != null){
            for (TopicTag topicTag : topicTags) {
                Tag tag = iTagService.getById(topicTag.getTagId());
                tags.add(tag);
            }
        }
        map.put("tags", tags);
        // 作者
        ProfileVO user = iUserService.getUserProfile(topic.getUserId()); // 得到用户信息，只需要Profile中的字段
        map.put("user", user);
        return map;
    }

    @Override
    public List<Post> getRecommend(String id) {
        return this.baseMapper.selectRecommend(id);
    }

    // 查询帖子
    @Override
    public Page<PostVO> searchByKey(String keyword, Page<PostVO> page) {
        // 查询话题
        Page<PostVO> iPage = this.baseMapper.searchByKey(page, keyword);
        // 查询话题的标签
        setTopicTags(iPage);
        return iPage;
    }
    /**
     * // 按标签查询
     * @param tag
     * @param page
     * @return
     */
    @Override
    public Page<PostVO> searchByTag(String tag, Page<PostVO> page) {
        // TODO
        return null;
    }

    private void setTopicTags(Page<PostVO> iPage) {
        iPage.getRecords().forEach(topic -> {
            List<TopicTag> topicTags = ITopicTagService.selectByTopicId(topic.getId());
            if (!topicTags.isEmpty()) {
                List<String> tagIds = topicTags.stream().map(TopicTag::getTagId).collect(Collectors.toList());
                List<Tag> tags = tagMapper.selectBatchIds(tagIds);
                topic.setTags(tags);
            }
        });
    }

    @Override
    public List<PostVO> getAllPostForUser(String userId) throws IOException {
        User user = iUserService.getById(userId);
        if(user == null){
            ErrorResponse.sendJsonMessage(USER_NOT_EXISTS);
            return null;
        }
        List<Post> posts = this.getBaseMapper().selectList(new LambdaQueryWrapper<Post>().eq(Post::getUserId,userId));
        List<PostVO> postVOs = new ArrayList<>();
        for (Post post : posts) {
            postVOs.add(this.changePostToPostVO(post));
        }
        return postVOs;
    }

    @Override
    public PostVO changePostToPostVO(Post post){
        PostVO postVO = new PostVO();
        BeanUtils.copyProperties(post,postVO);
        // 点赞, 收藏, 评论数
        List<PostPraise> praiseList = iPostPraiseService.getBaseMapper().selectList(new LambdaQueryWrapper<PostPraise>().eq(PostPraise::getPostId,post.getId()));
        postVO.setPraises(praiseList.size());
        List<PostCollect> collectList = iPostCollectService.getBaseMapper().selectList(new LambdaQueryWrapper<PostCollect>().eq(PostCollect::getPostId,post.getId()));
        postVO.setCollects(collectList.size());
        List<Comment> commentList = commentMapper.selectList(new LambdaQueryWrapper<Comment>().eq(Comment::getTopicId,post.getId()));
        postVO.setComments(commentList.size());

        List<Tag> tags = new ArrayList<>();
        // 标签
        List<TopicTag> postTag = ITopicTagService.getBaseMapper().selectList(new LambdaQueryWrapper<TopicTag>().eq(TopicTag::getTopicId,post.getId()));
        for (TopicTag topicTag : postTag) {
            Tag tag = iTagService.getById(topicTag.getTagId());
            tags.add(tag);
        }
        postVO.setTags(tags);
        // 作者相关
        User user = iUserService.getById(post.getUserId());
        if (user != null){
            postVO.setAlias(user.getAlias());
            postVO.setAvatar(user.getAvatar());
            postVO.setUsername(user.getUsername());
        }
        return postVO;
    }
}
