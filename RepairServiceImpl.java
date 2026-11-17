package com.example.dormitory.repair.service.impl;

import com.example.dormitory.repair.dto.RepairFinishCommand;
import com.example.dormitory.repair.dto.RepairSubmitCommand;
import com.example.dormitory.repair.exception.BusinessException;
import com.example.dormitory.repair.mapper.RepairRecordMapper;
import com.example.dormitory.repair.model.RepairRecord;
import com.example.dormitory.repair.model.RepairStatus;
import com.example.dormitory.repair.port.DormitoryQueryPort;
import com.example.dormitory.repair.port.StudentQueryPort;
import com.example.dormitory.repair.port.UserQueryPort;
import com.example.dormitory.repair.port.dto.DormitoryInfo;
import com.example.dormitory.repair.port.dto.StudentInfo;
import com.example.dormitory.repair.service.RepairService;
import com.example.dormitory.repair.vo.RepairVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RepairServiceImpl implements RepairService {

    private static final int MAX_DESCRIPTION_LENGTH = 500;
    private static final int MAX_RESULT_LENGTH = 500;

    private final RepairRecordMapper repairRecordMapper;
    private final StudentQueryPort studentQueryPort;
    private final DormitoryQueryPort dormitoryQueryPort;
    private final UserQueryPort userQueryPort;

    public RepairServiceImpl(RepairRecordMapper repairRecordMapper,
                             StudentQueryPort studentQueryPort,
                             DormitoryQueryPort dormitoryQueryPort,
                             UserQueryPort userQueryPort) {
        this.repairRecordMapper = repairRecordMapper;
        this.studentQueryPort = studentQueryPort;
        this.dormitoryQueryPort = dormitoryQueryPort;
        this.userQueryPort = userQueryPort;
    }

    @Override
    @Transactional
    public RepairVO submit(Long studentUserId, RepairSubmitCommand command) {
        validateDescription(command);
        StudentInfo student = studentQueryPort.getByUserId(studentUserId);
        if (student == null) {
            throw new BusinessException("当前登录用户没有学生档案");
        }
        if (student.getDormitoryId() == null) {
            throw new BusinessException("尚未分配宿舍，不能提交报修");
        }
        DormitoryInfo dormitory = dormitoryQueryPort.getById(student.getDormitoryId());
        if (dormitory == null) {
            throw new BusinessException("学生所属宿舍不存在");
        }

        RepairRecord record = new RepairRecord();
        record.setStudentId(student.getStudentId());
        record.setDormitoryId(dormitory.getDormitoryId());
        record.setDescription(command.getDescription().trim());
        record.setStatus(RepairStatus.PENDING.getCode());
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        repairRecordMapper.insert(record);

        return toVO(record, student, dormitory, null);
    }

    @Override
    public List<RepairVO> listByStudentUserId(Long studentUserId) {
        StudentInfo student = studentQueryPort.getByUserId(studentUserId);
        if (student == null) {
            throw new BusinessException("当前登录用户没有学生档案");
        }
        return repairRecordMapper.findByStudentId(student.getStudentId())
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RepairVO> listByManagerUserId(Long managerUserId) {
        List<Long> dormitoryIds = dormitoryQueryPort.listManagedDormitoryIds(managerUserId);
        if (dormitoryIds == null || dormitoryIds.isEmpty()) {
            return Collections.emptyList();
        }
        return repairRecordMapper.findByDormitoryIds(dormitoryIds)
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public List<RepairVO> listAll() {
        return repairRecordMapper.findAll()
                .stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public RepairVO accept(Long repairId, Long managerUserId) {
        RepairRecord record = requireRecord(repairId);
        requireManagedDormitory(record.getDormitoryId(), managerUserId);
        if (!RepairStatus.PENDING.getCode().equals(record.getStatus())) {
            throw new BusinessException("只有待接单的报修才能接单");
        }
        if (repairRecordMapper.accept(repairId, managerUserId) != 1) {
            throw new BusinessException("接单失败，报修状态可能已经变化");
        }
        return toVO(requireRecord(repairId));
    }

    @Override
    @Transactional
    public RepairVO finish(Long repairId, Long managerUserId, RepairFinishCommand command) {
        validateResult(command);
        RepairRecord record = requireRecord(repairId);
        requireManagedDormitory(record.getDormitoryId(), managerUserId);
        if (!RepairStatus.ACCEPTED.getCode().equals(record.getStatus())) {
            throw new BusinessException("只有已接单的报修才能标记修复");
        }
        if (repairRecordMapper.finish(repairId, managerUserId, command.getResult().trim()) != 1) {
            throw new BusinessException("标记失败，报修状态可能已经变化");
        }
        return toVO(requireRecord(repairId));
    }

    private void validateDescription(RepairSubmitCommand command) {
        if (command == null || !StringUtils.hasText(command.getDescription())) {
            throw new BusinessException("报修描述不能为空");
        }
        if (command.getDescription().trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessException("报修描述不能超过500个字符");
        }
    }

    private void validateResult(RepairFinishCommand command) {
        if (command == null || !StringUtils.hasText(command.getResult())) {
            throw new BusinessException("修复结果不能为空");
        }
        if (command.getResult().trim().length() > MAX_RESULT_LENGTH) {
            throw new BusinessException("修复结果不能超过500个字符");
        }
    }

    private RepairRecord requireRecord(Long repairId) {
        RepairRecord record = repairRecordMapper.findById(repairId);
        if (record == null) {
            throw new BusinessException("报修记录不存在");
        }
        return record;
    }

    private DormitoryInfo requireManagedDormitory(Long dormitoryId, Long managerUserId) {
        DormitoryInfo dormitory = dormitoryQueryPort.getById(dormitoryId);
        if (dormitory == null) {
            throw new BusinessException("报修宿舍不存在");
        }
        if (managerUserId == null || !managerUserId.equals(dormitory.getManagerId())) {
            throw new BusinessException("无权处理该宿舍的报修");
        }
        return dormitory;
    }

    private RepairVO toVO(RepairRecord record) {
        StudentInfo student = studentQueryPort.getByStudentId(record.getStudentId());
        DormitoryInfo dormitory = dormitoryQueryPort.getById(record.getDormitoryId());
        String handlerName = record.getHandlerId() == null ? null : userQueryPort.getRealName(record.getHandlerId());
        return toVO(record, student, dormitory, handlerName);
    }

    private RepairVO toVO(RepairRecord record, StudentInfo student,
                          DormitoryInfo dormitory, String handlerName) {
        RepairVO vo = new RepairVO();
        vo.setId(record.getId());
        vo.setStudentId(record.getStudentId());
        vo.setStudentNo(student == null ? null : student.getStudentNo());
        vo.setStudentName(student == null ? null : student.getName());
        vo.setDormitoryId(record.getDormitoryId());
        vo.setDormitoryName(dormitory == null ? null : dormitory.getDisplayName());
        vo.setDescription(record.getDescription());
        vo.setStatus(record.getStatus());
        vo.setStatusLabel(RepairStatus.fromCode(record.getStatus()).getLabel());
        vo.setHandlerId(record.getHandlerId());
        vo.setHandlerName(handlerName);
        vo.setResult(record.getResult());
        vo.setCreateTime(record.getCreateTime());
        vo.setAcceptTime(record.getAcceptTime());
        vo.setFinishTime(record.getFinishTime());
        return vo;
    }
}
