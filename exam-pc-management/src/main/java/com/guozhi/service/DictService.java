package com.guozhi.service;

import com.guozhi.dto.DictDTO;

import java.util.List;

/**
 * @author LiuchangLan
 * @date 2020/7/13 14:53
 */
public interface DictService {

    List<DictDTO> getAllDicts();

    Integer addDicts(DictDTO dictDTO);

    Integer deleteDicts(Integer id);
}
