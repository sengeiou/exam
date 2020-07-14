package com.guozhi.dto;

import com.fasterxml.jackson.databind.ser.Serializers;
import lombok.Data;

import javax.persistence.Table;

/**
 * 试卷DTO
 * @author LiuchangLan
 * @date 2020/7/13 11:32
 */
@Data
@Table(name = "tab_exam_paper")
public class PaperDTO extends BaseDTO {
    // 试卷名称
    private String paperName;

    // 试卷说明
    private String psperContent;

    // 试卷类型 0考试 1练习
    private Integer paperType;

    // 考试开始时间
    private String startTime;

    // 考试结束时间
    private String endTime;

    // 考试时长
    private Integer examTime;

    // 总分
    private Double totalScore;

    // 合格分
    private Double passScore;

    // 试卷状态 0-未发布 1-已发布
    private Integer paperStatus;

    // 发布时间
    private String releaseTime;

    // 业务类型 0默认类型 1多选题错一个算错
    private Integer businessType;

    // 查看分数时间
    private String lookScoreTime;

    // 可考次数
    private Integer examCount;
}
