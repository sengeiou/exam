package com.guozhi.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.guozhi.common.DataGlobalVariable;
import com.guozhi.core.BusinessException;
import com.guozhi.core.Result;
import com.guozhi.core.ResultStatusCode;
import com.guozhi.dto.*;
import com.guozhi.mapper.*;
import com.guozhi.rvo.*;
import com.guozhi.service.PaperService;
import com.guozhi.utils.JwtUtils;
import com.guozhi.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author LiuchangLan
 * @date 2020/7/14 10:29
 */
@Service
@Slf4j
public class PaperServiceImpl implements PaperService {

    @Resource
    private PaperMapper paperMapper;

    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private PaperQuestionMapper paperQuestionMapper;

    @Resource
    private PaperUserMapper paperUserMapper;

    @Resource
    private QuestionOptionMapper questionOptionMapper;

    @Resource
    private QuestionNumberMapper questionNumberMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Resource
    private SubmitPaperMapper submitPaperMapper;


    @Value("${spring.rabbitmq.correctPaper.queue}")
    private String queueName;
    @Value("${spring.rabbitmq.correctPaper.exchange}")
    private String exchangeName;
    @Value("${spring.rabbitmq.correctPaper.routingKey}")
    private String routingKey;


    /**
     * @description ????????????
     * @author LiuChangLan
     * @since 2020/7/14 10:30
     */
    @Override
    public Integer addPaper(PaperDTO paperDTO) {
        return paperMapper.insertSelective(paperDTO);
    }

    /**
     * @description ????????????
     * @author LiuChangLan
     * @since 2020/7/14 10:30
     */
    @Override
    public Integer deletePaper(int id) {
        PaperDTO paperDTO = new PaperDTO();
        paperDTO.setId(id);
        paperDTO.setIsDeleted(DataGlobalVariable.IS_DELETE);
        paperDTO.setUpdateTime(DateUtil.now());
        return paperMapper.updateByPrimaryKeySelective(paperDTO);
    }

    /**
     * @description ????????????
     * @author LiuChangLan
     * @since 2020/7/15 16:17
     */
    @Override
    public Integer updatePaper(PaperDTO paperDTO) {
        paperDTO.setUpdateTime(DateUtil.now());
        return paperMapper.updateByPrimaryKeySelective(paperDTO);
    }

    /**
     * @description ????????????????????????
     * @author LiuChangLan
     * @since 2020/7/14 10:30
     */
    @Override
    public PageInfo<PaperRVO> getPaperByPage(PageVO pageVO) {
        PageHelper.startPage(pageVO.getPageIndex(), pageVO.getPageSize());
        List<PaperRVO> papers = paperMapper.getPaperList();
        return new PageInfo<>(papers);
    }

    /**
     * @description ??????id??????????????????
     * @author LiuChangLan
     * @since 2020/7/15 16:19
     */
    @Override
    public PaperDTO getPaperById(String id) {
        return paperMapper.selectByPrimaryKey(id);
    }

    /**
     * @description ?????????????????????
     * @author LiuChangLan
     * @since 2020/9/2 16:31
     */
    @Override
    @Transactional
    public Integer addPaperQuestion(PaperImportQuestionVO paperImportQuestionVO){

        Integer result = -1;

        //?????????????????????id
        Set<Integer> questionIds = new HashSet<Integer>(paperImportQuestionVO.getFixedQuestion());

        QuestionDTO questionDTO = new QuestionDTO();
        questionDTO.setIsDeleted(DataGlobalVariable.IS_NOT_DELETE);
        // ??????????????????
        final List<QuestionDTO> allQuestion = questionMapper.select(questionDTO);
        // ??????????????????????????????
        Map<String, List<QuestionDTO>> collect = allQuestion.stream().collect(Collectors.groupingBy(QuestionDTO::getQuestionType));

        //????????????
        for (PaperRandomQuestionVO randomQuestionVO : paperImportQuestionVO.getRandomQuestion()) {
            // ???????????????????????????
            List<QuestionDTO> questionDTOS = collect.get(randomQuestionVO.getQuestionType());
            // ???????????????????????????0???????????????
            while (questionDTOS.size() > 0 && randomQuestionVO.getQuestionNum() > 0){
                Random random = new Random();
                // ?????????????????????
                int index = random.nextInt(questionDTOS.size());
                // ???????????????id
                Integer id = questionDTOS.get(index).getId();
                if (questionIds.add(id)){
                    //????????????????????????id????????????
                    continue;
                }else {
                    // ???????????????????????????
                    questionDTOS.remove(id);
                    log.info("??????????????????id???{}",id);
                }
                randomQuestionVO.setQuestionNum(randomQuestionVO.getQuestionNum() - 1);
            }
        }
        // ????????????
        for (Integer questionId : questionIds) {
            PaperQuestionsDTO paperQuestionsDTO = new PaperQuestionsDTO();
            paperQuestionsDTO.setQuestionId(questionId);
            paperQuestionsDTO.setPaperId(paperImportQuestionVO.getId());
            result = paperQuestionMapper.insertSelective(paperQuestionsDTO);
        }

        for (String questionType : paperImportQuestionVO.getQuestionTypeNumber().keySet()) {
            QuestionsNumberDTO questionsNumberDTO = new QuestionsNumberDTO();
            questionsNumberDTO.setPaperId(paperImportQuestionVO.getId());
            questionsNumberDTO.setQuestionType(questionType);
            questionsNumberDTO.setQuestionScore(Double.parseDouble(String.valueOf(paperImportQuestionVO.getQuestionTypeNumber().get(questionType))));
            questionNumberMapper.insertSelective(questionsNumberDTO);
        }

        PaperDTO paperDTO = BeanUtil.copyProperties(paperImportQuestionVO, PaperDTO.class);
        paperDTO.setUpdateTime(DateUtil.now());
        paperDTO.setUpdatedBy(JwtUtils.getCurrentUserJwtPayload().getId());
        result = paperMapper.updateByPrimaryKeySelective(paperDTO);
        return result;
    }

    /**
     * @description ?????????????????????????????????
     * @author LiuChangLan
     * @since 2020/11/4 15:07
     */
    @Override
    public List<UserPaperRVO> getUserPaper(Integer userId) {
        List<UserPaperRVO> userPaper = paperMapper.getUserPaper(userId);
//        List<UserPaperRVO> result = new ArrayList<>();
//        for (UserPaperRVO userPaperRVO : userPaper) {
//            if (PaperUtils.isNormalExamTime(userPaperRVO)){
//                result.add(userPaperRVO);
//            }
//        }
        return userPaper;
    }


    /**
     * @description ????????????
     * @author LiuChangLan
     * @since 2020/11/26 17:24
     */
    @Override
    @Transactional
    public Integer releasePaper(ReleasePaperVO vo) {
        List<Integer> userIds = vo.getUserIds();

        Integer insertCount = 0;

        for (Integer userId : userIds) {
            PaperUserDTO insertDTO = new PaperUserDTO();
            insertDTO.setPaperId(vo.getId());
            insertDTO.setUserId(userId);
            insertDTO.setExamStatus(0);
            insertCount += paperUserMapper.insertSelective(insertDTO);
        }

        PaperDTO paperDTO = new PaperDTO();
        paperDTO.setId(vo.getId());
        paperDTO.setUpdateTime(DateUtil.now());
        paperDTO.setUpdatedBy(JwtUtils.getCurrentUserJwtPayload().getId());
        paperDTO.setPaperStatus(DataGlobalVariable.PAPER_STATUS_PUBLISHED); // ???????????? ???????????????
        paperMapper.updateByPrimaryKeySelective(paperDTO);

        return insertCount;
    }

    /**
     * @description ????????????
     * @author LiuChangLan
     * @since 2020/11/26 17:24
     */
    @Override
    public JoinPaperRVO joinPaper(Integer paperId,Integer userId) {
        // ???
        String key = String.format("user%dpaper%d",userId,paperId);
        String submitAnswerJson = stringRedisTemplate.opsForValue().get(key);
        SubmitAnswerVO submitAnswerVO = null;
        if (!StringUtils.isEmpty(submitAnswerJson)) {
            submitAnswerVO = JSON.parseObject(submitAnswerJson, SubmitAnswerVO.class);
        }

        PaperDTO paperDTO = new PaperDTO();
        paperDTO.setId(paperId);
        paperDTO.setIsDeleted(DataGlobalVariable.IS_NOT_DELETE);
        // ??????????????????
        JoinPaperRVO joinPaperRVO = BeanUtil.copyProperties(paperMapper.selectOne(paperDTO),JoinPaperRVO.class);
        // ?????????????????????
        joinPaperRVO.setSubmitAnswerVO(submitAnswerVO);
        // ???????????????????????????
        List<JoinQuestionRVO> questionList = new ArrayList<>();
        // ?????????
        int submitAnswerIndex = 0;
        for (QuestionDTO questionsDTO : paperQuestionMapper.selectPaperQuestion(paperId)) {
            JoinQuestionRVO questionRVO = BeanUtil.copyProperties(questionsDTO, JoinQuestionRVO.class);
            QuestionsOptionDTO optionDto = new QuestionsOptionDTO();
            optionDto.setIsDeleted(DataGlobalVariable.IS_NOT_DELETE);
            optionDto.setQuestionId(questionsDTO.getId());
            // ???????????????????????????
            List<QuestionsOptionDTO> opstions = questionOptionMapper.select(optionDto);
            // ?????????????????????
            List<JoinOptionRVO> joinOptionRVOS = new ArrayList<>();
            // ?????????????????????????????????
            for (QuestionsOptionDTO option : opstions) {
                JoinOptionRVO joinOptionRVO = BeanUtil.copyProperties(option, JoinOptionRVO.class);
                List<Integer> answer = new ArrayList<>();
                if (!DataGlobalVariable.FILL_QUESTION_DICT_CODE.equals(questionsDTO.getQuestionType())) {
                    // ????????????
                    if (submitAnswerVO != null && submitAnswerVO.getAnswerVOS() != null && submitAnswerVO.getAnswerVOS().get(submitAnswerIndex).getAnswer() != null) {
                        answer = submitAnswerVO.getAnswerVOS().get(submitAnswerIndex).getAnswer().stream().map(item -> Integer.parseInt(String.valueOf(item))).collect(Collectors.toList());
                    }
                    if (answer != null && answer.size() > 0) {
                        if (answer.contains(joinOptionRVO.getId())) {
                            joinOptionRVO.setCheck(true);
                        } else {
                            joinOptionRVO.setCheck(false);
                        }
                    }
                }else {
                    // ?????????
//                    joinOptionRVO.setCheck(false);
                }
                joinOptionRVOS.add(joinOptionRVO);
            }
            questionRVO.setOptions(joinOptionRVOS);
            questionList.add(questionRVO);
            submitAnswerIndex ++;
        }
        joinPaperRVO.setQuestionDTOS(questionList);

        return joinPaperRVO;
    }


    /**
     * @description ???????????????????????????
     * @author LiuChangLan
     * @since 2020/11/26 17:24
     */
    @Override
    public Result saveAnswer(SubmitAnswerVO submitAnswerVO) throws BusinessException {
        try {
            // ??????????????????
            PaperDTO paperDTO = paperMapper.selectByPrimaryKey(submitAnswerVO.getPaperId());
            // ??????????????????????????????
            long second = DateUtil.between(DateUtil.parseDateTime(paperDTO.getStartTime()), DateUtil.parseDateTime(paperDTO.getEndTime()), DateUnit.SECOND);
            // ???
            String key = String.format("user%dpaper%d",submitAnswerVO.getUserId(),submitAnswerVO.getPaperId());
            // ?????????
            stringRedisTemplate.opsForValue().set(key, JSON.toJSONString(submitAnswerVO),second, TimeUnit.SECONDS);
            return new Result(ResultStatusCode.PAPER_SAVE_SUCCESS);
        } catch (Exception e) {
            throw new BusinessException(ResultStatusCode.PAPER_SAVE_ERROR);
        }
    }

    /**
     * @description ????????????
     * @author LiuChangLan
     * @since 2020/11/26 17:24
     */
    @Override
    public Result submitPaper(SubmitAnswerVO submitAnswerVO) throws BusinessException {
        // ?????????json??????
        String commitJson = null;
        try {
            commitJson = JSON.toJSONString(submitAnswerVO);
            SubmitPaperDTO submitDTO = new SubmitPaperDTO();
            // ??????id
            submitDTO.setUserId(submitAnswerVO.getUserId());
            // ??????id
            submitDTO.setPaperId(submitAnswerVO.getPaperId());
            // ????????????
            submitDTO.setCommitContent(commitJson);
            // ????????????
            submitDTO.setCommitType(submitAnswerVO.getCommitType());
            // ??????????????????
            submitPaperMapper.insertSelective(submitDTO);

            // ???????????????????????????????????????
            PaperUserDTO paperUserDTO = new PaperUserDTO();
            paperUserDTO.setExamStatus(DataGlobalVariable.EXAM_STATUS_ALREADY_EXAM);

            Example example = new Example(PaperUserDTO.class);
            Example.Criteria criteria = example.createCriteria();
            criteria.andEqualTo("userId", submitAnswerVO.getUserId());
            criteria.andEqualTo("paperId", submitAnswerVO.getPaperId());
            // ??????userid ??? paperid????????????
            paperUserMapper.updateByExampleSelective(paperUserDTO,example);

            // ???????????????????????????
            rabbitTemplate.convertAndSend(exchangeName,routingKey,JSON.toJSONString(submitDTO));
            log.info("???{}??????{}???????????????{}?????????[????????????]?????????????????????{}",DateUtil.now(),submitAnswerVO.getUserId(),submitAnswerVO.getPaperId(),commitJson);
            // ???????????????????????????
            String key = String.format("user%dpaper%d",submitAnswerVO.getUserId(),submitAnswerVO.getPaperId());
            stringRedisTemplate.delete(key);
            return new Result(ResultStatusCode.PAPER_COMMIT_SUCCESS);
        } catch (Exception e) {
            log.error("???{}??????{}???????????????{}?????????[????????????]?????????????????????{}",DateUtil.now(),submitAnswerVO.getUserId(),submitAnswerVO.getPaperId(),commitJson,e);
            throw new BusinessException(ResultStatusCode.PAPER_COMMIT_ERROR);
        }
    }
}
