package com.example.dormitory.repair.port;

import com.example.dormitory.repair.port.dto.StudentInfo;

/**
 * 宿舍/学生模块向报修模块暴露的只读接口。
 * 报修模块只依赖该接口，不依赖 StudentMapper 或 student 表。
 */
public interface StudentQueryPort {

    StudentInfo getByUserId(Long userId);

    StudentInfo getByStudentId(Long studentId);
}
