package com.example.dormitory.repair.port;

import com.example.dormitory.repair.port.dto.DormitoryInfo;

import java.util.List;

/**
 * 宿舍模块向报修模块暴露的只读接口。
 */
public interface DormitoryQueryPort {

    DormitoryInfo getById(Long dormitoryId);

    List<Long> listManagedDormitoryIds(Long managerUserId);
}
