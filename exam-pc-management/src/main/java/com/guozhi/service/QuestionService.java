package com.guozhi.service;

import com.github.pagehelper.PageInfo;
import com.guozhi.dto.QuestionDTO;
import com.guozhi.rvo.QuestionRVO;
import com.guozhi.vo.PageVO;

/**
 * 题目Service
 * @author LiuchangLan
 * @date 2020/7/15 16:44
 */
public interface QuestionService {

    /**
     * @description 添加题目
     * @author LiuChangLan
     * @since 2020/7/15 16:46
     */
    Integer addQuestion(QuestionDTO questionDTO);

    /**
     * @description 删除题目
     * @author LiuChangLan
     * @since 2020/7/15 16:46
     */
    Integer deleteQuestion(Integer id);

    /**
     * @description 修改题目
     * @author LiuChangLan
     * @since 2020/7/15 16:46
     */
    Integer updateQuestion(QuestionDTO questionDTO);

    /**
     * @description 查询题目分页
     * @author LiuChangLan
     * @since 2020/7/15 16:46
     */
    PageInfo<QuestionRVO> getQuestionsByPage(PageVO pageVO);

    /**
     * @description 根据题目id查询题目详细信息
     * @author LiuChangLan
     * @since 2020/7/17 15:10
     */
    QuestionDTO getQuestionById(Integer id);
}
