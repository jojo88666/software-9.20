package com.example.dormitory.repair.service;

import com.example.dormitory.repair.dto.RepairFinishCommand;
import com.example.dormitory.repair.dto.RepairSubmitCommand;
import com.example.dormitory.repair.vo.RepairVO;

import java.util.List;

/**
 * 报修模块对外暴露的业务接口。
 * Controller 不直接依赖 RepairRecordMapper。
 */
public interface RepairService {

    RepairVO submit(Long studentUserId, RepairSubmitCommand command);

    List<RepairVO> listByStudentUserId(Long studentUserId);

    List<RepairVO> listByManagerUserId(Long managerUserId);

    List<RepairVO> listAll();

    RepairVO accept(Long repairId, Long managerUserId);

    RepairVO finish(Long repairId, Long managerUserId, RepairFinishCommand command);
}
