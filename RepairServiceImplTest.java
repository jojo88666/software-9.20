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
import com.example.dormitory.repair.vo.RepairVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RepairServiceImplTest {

    @Mock
    private RepairRecordMapper repairRecordMapper;

    @Mock
    private StudentQueryPort studentQueryPort;

    @Mock
    private DormitoryQueryPort dormitoryQueryPort;

    @Mock
    private UserQueryPort userQueryPort;

    @InjectMocks
    private RepairServiceImpl repairService;

    private StudentInfo student;
    private DormitoryInfo dormitory;

    @BeforeEach
    void setUp() {
        student = new StudentInfo(1L, 10L, "20260001", "张三", 100L);
        dormitory = new DormitoryInfo(100L, "1号楼", "101", 2L);
    }

    @Test
    void submitShouldCreatePendingRepair() {
        when(studentQueryPort.getByUserId(10L)).thenReturn(student);
        when(dormitoryQueryPort.getById(100L)).thenReturn(dormitory);
        when(repairRecordMapper.insert(any(RepairRecord.class))).thenAnswer(invocation -> {
            RepairRecord record = invocation.getArgument(0);
            record.setId(500L);
            return 1;
        });

        RepairVO result = repairService.submit(10L, new RepairSubmitCommand("宿舍门锁松动"));

        assertEquals(500L, result.getId());
        assertEquals(RepairStatus.PENDING.getCode(), result.getStatus());
        assertEquals("待接单", result.getStatusLabel());
        assertEquals("张三", result.getStudentName());
        assertEquals("1号楼-101", result.getDormitoryName());

        ArgumentCaptor<RepairRecord> captor = ArgumentCaptor.forClass(RepairRecord.class);
        verify(repairRecordMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getStudentId());
        assertEquals(100L, captor.getValue().getDormitoryId());
        assertEquals(RepairStatus.PENDING.getCode(), captor.getValue().getStatus());
    }

    @Test
    void submitShouldRejectStudentWithoutDormitory() {
        StudentInfo noDormStudent = new StudentInfo(2L, 11L, "20260002", "李四", null);
        when(studentQueryPort.getByUserId(11L)).thenReturn(noDormStudent);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> repairService.submit(11L, new RepairSubmitCommand("灯坏了")));

        assertEquals("尚未分配宿舍，不能提交报修", exception.getMessage());
        verify(repairRecordMapper, never()).insert(any(RepairRecord.class));
    }

    @Test
    void acceptShouldRejectManagerOutsideDormitory() {
        RepairRecord pending = repairRecord(500L, RepairStatus.PENDING.getCode(), null);
        when(repairRecordMapper.findById(500L)).thenReturn(pending);
        when(dormitoryQueryPort.getById(100L))
                .thenReturn(new DormitoryInfo(100L, "1号楼", "101", 99L));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> repairService.accept(500L, 2L));

        assertEquals("无权处理该宿舍的报修", exception.getMessage());
        verify(repairRecordMapper, never()).accept(500L, 2L);
    }

    @Test
    void acceptShouldTransitionPendingToAccepted() {
        RepairRecord pending = repairRecord(500L, RepairStatus.PENDING.getCode(), null);
        RepairRecord accepted = repairRecord(500L, RepairStatus.ACCEPTED.getCode(), 2L);
        accepted.setAcceptTime(LocalDateTime.now());
        when(repairRecordMapper.findById(500L)).thenReturn(pending, accepted);
        when(dormitoryQueryPort.getById(100L)).thenReturn(dormitory);
        when(repairRecordMapper.accept(500L, 2L)).thenReturn(1);
        when(studentQueryPort.getByStudentId(1L)).thenReturn(student);
        when(userQueryPort.getRealName(2L)).thenReturn("王宿管");

        RepairVO result = repairService.accept(500L, 2L);

        assertEquals(RepairStatus.ACCEPTED.getCode(), result.getStatus());
        assertEquals("王宿管", result.getHandlerName());
        verify(repairRecordMapper).accept(500L, 2L);
    }

    @Test
    void finishShouldRejectEmptyResult() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> repairService.finish(500L, 2L, new RepairFinishCommand("  ")));

        assertEquals("修复结果不能为空", exception.getMessage());
        verify(repairRecordMapper, never()).finish(any(), any(), any());
    }

    @Test
    void finishShouldTransitionAcceptedToFixed() {
        RepairRecord accepted = repairRecord(500L, RepairStatus.ACCEPTED.getCode(), 2L);
        RepairRecord fixed = repairRecord(500L, RepairStatus.FIXED.getCode(), 2L);
        fixed.setResult("已更换门锁");
        fixed.setFinishTime(LocalDateTime.now());
        when(repairRecordMapper.findById(500L)).thenReturn(accepted, fixed);
        when(dormitoryQueryPort.getById(100L)).thenReturn(dormitory);
        when(repairRecordMapper.finish(500L, 2L, "已更换门锁")).thenReturn(1);
        when(studentQueryPort.getByStudentId(1L)).thenReturn(student);
        when(userQueryPort.getRealName(2L)).thenReturn("王宿管");

        RepairVO result = repairService.finish(500L, 2L, new RepairFinishCommand("已更换门锁"));

        assertEquals(RepairStatus.FIXED.getCode(), result.getStatus());
        assertEquals("已更换门锁", result.getResult());
        verify(repairRecordMapper).finish(500L, 2L, "已更换门锁");
    }

    @Test
    void managerListShouldReturnEmptyWhenManagerHasNoDormitory() {
        when(dormitoryQueryPort.listManagedDormitoryIds(2L)).thenReturn(Collections.emptyList());

        List<RepairVO> result = repairService.listByManagerUserId(2L);

        assertTrue(result.isEmpty());
        verify(repairRecordMapper, never()).findByDormitoryIds(anyList());
    }

    @Test
    void studentListShouldEnrichRepairView() {
        RepairRecord fixed = repairRecord(500L, RepairStatus.FIXED.getCode(), 2L);
        fixed.setResult("已修复");
        when(studentQueryPort.getByUserId(10L)).thenReturn(student);
        when(repairRecordMapper.findByStudentId(1L)).thenReturn(Collections.singletonList(fixed));
        when(studentQueryPort.getByStudentId(1L)).thenReturn(student);
        when(dormitoryQueryPort.getById(100L)).thenReturn(dormitory);
        when(userQueryPort.getRealName(2L)).thenReturn("王宿管");

        List<RepairVO> result = repairService.listByStudentUserId(10L);

        assertEquals(1, result.size());
        assertEquals("张三", result.get(0).getStudentName());
        assertEquals("1号楼-101", result.get(0).getDormitoryName());
        assertEquals("王宿管", result.get(0).getHandlerName());
    }

    private RepairRecord repairRecord(Long id, String status, Long handlerId) {
        RepairRecord record = new RepairRecord();
        record.setId(id);
        record.setStudentId(1L);
        record.setDormitoryId(100L);
        record.setDescription("宿舍门锁松动");
        record.setStatus(status);
        record.setHandlerId(handlerId);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateTime(LocalDateTime.now());
        return record;
    }
}
