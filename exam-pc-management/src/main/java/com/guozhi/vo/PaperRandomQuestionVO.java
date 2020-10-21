package com.guozhi.vo;

import lombok.Data;

/**
 * 随机题目入参VO
 * @author LiuchangLan
 * @date 2020/9/2 9:54
 */
@Data
public class PaperRandomQuestionVO {

    //题目数量
    private Integer questionNum;

    //每题分数
    private Integer questionScore;

    //题目类型
    private Integer questionType;

    // 专题id
    private Integer specialId;


}
