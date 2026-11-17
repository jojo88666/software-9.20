# 实验 6：编码规范与模块化编程

## 1. 模块选择

本次从学生宿舍管理系统中选取“报修模块”作为核心模块进行实现。

选择原因：

- 报修模块同时连接学生、宿管和管理员三类角色。
- 业务具有明确的状态流转：待接单 → 已接单 → 已修复。
- 需要调用用户模块和宿舍/学生模块的查询接口，适合展示模块间接口调用。
- 业务规则集中，便于编写单元测试和进行可维护性分析。

该目录是一个可独立编译、测试的“报修模块抽取版”，不包含完整登录页面、数据库配置和全部 Thymeleaf 页面。完整系统中的其他模块通过接口适配器接入本模块。

## 2. 目录结构
├── DormitoryQueryPort.java     // 宿舍信息查询接口
├── StudentQueryPort.java       // 学生信息查询接口
├── RepairService.java          // 报修业务服务接口
├── RepairServiceImpl.java      // 报修业务接口实现类
├── RepairRecordMapper.xml      // MyBatis 数据库映射文件
├── RepairServiceImplTest.java  // 报修模块单元测试
├── README.md                   // 项目说明文档
├── hello.c                     // 实验 1 代码
└── 软工实验设计规划.docx       // 前期实验规划文档

## 3. 模块边界

### 3.1 报修模块内部

- RepairRecord：报修记录实体。
- RepairStatus：报修状态枚举。
- RepairRecordMapper：报修表的数据访问接口。
- RepairService / RepairServiceImpl：报修业务规则和状态机。
- RepairController：Web 适配器，只负责接收参数。
- RepairExceptionHandler：统一业务异常响应。

### 3.2 模块外部

报修模块不直接访问 student、dormitory 和 sys_user 表，而是依赖以下只读接口：

| 接口 | 提供方 | 作用 |
|---|---|---|
| StudentQueryPort | 宿舍/学生模块 | 根据用户ID或学生ID获取学生与宿舍信息 |
| DormitoryQueryPort | 宿舍模块 | 获取宿舍信息、宿管负责的宿舍ID列表 |
| UserQueryPort | 用户模块 | 根据用户ID获取宿管姓名 |

这样，报修模块只依赖接口，不依赖其他模块的 Mapper 或数据库表，降低模块耦合。

## 4. 模块间接口调用

### 4.1 学生提交报修

调用链：

    RepairController
      → RepairService.submit
        → StudentQueryPort.getByUserId
        → DormitoryQueryPort.getById
        → RepairRecordMapper.insert

输入：

- studentUserId：当前登录学生用户ID。
- RepairSubmitCommand：报修描述。

输出：

- RepairVO：包含报修ID、学生信息、宿舍信息、状态“待接单”。

### 4.2 宿管查看报修

调用链：

    DormitoryQueryPort.listManagedDormitoryIds
      → RepairRecordMapper.findByDormitoryIds
      → 组装 RepairVO

报修模块不直接查询 dormitory 表，而是向宿舍模块询问“当前宿管负责哪些宿舍”。

### 4.3 宿管接单

调用链：

    RepairService.accept
      → RepairRecordMapper.findById
      → DormitoryQueryPort.getById
      → 校验 managerId
      → RepairRecordMapper.accept
      → 重新查询并组装 RepairVO

只有 PENDING 状态可以接单，接单后状态变为 ACCEPTED。

### 4.4 宿管标记修复

调用链：

    RepairService.finish
      → 校验修复结果
      → RepairRecordMapper.findById
      → DormitoryQueryPort.getById
      → 校验宿管权限
      → RepairRecordMapper.finish
      → 重新查询并组装 RepairVO

只有 ACCEPTED 状态可以标记修复，完成后状态变为 FIXED。

## 5. 报修状态机

    PENDING（待接单）
       │ 宿管接单
       ▼
    ACCEPTED（已接单）
       │ 填写修复结果并标记修复
       ▼
    FIXED（已修复）

状态规则集中在 RepairStatus 和 RepairServiceImpl 中，避免在 Controller 和页面中散落状态判断。

## 6. 接口定义

### 6.1 RepairService

| 方法 | 功能 | 输入 | 输出 |
|---|---|---|---|
| submit | 学生提交报修 | 学生用户ID、报修描述 | RepairVO |
| listByStudentUserId | 查询本人报修 | 学生用户ID | List<RepairVO> |
| listByManagerUserId | 查询管辖宿舍报修 | 宿管用户ID | List<RepairVO> |
| listAll | 查询全部报修 | 无 | List<RepairVO> |
| accept | 宿管接单 | 报修ID、宿管用户ID | 更新后的 RepairVO |
| finish | 宿管标记修复 | 报修ID、宿管用户ID、修复结果 | 更新后的 RepairVO |

### 6.2 跨模块查询接口

| 方法 | 输入 | 输出 | 说明 |
|---|---|---|---|
| StudentQueryPort.getByUserId | 用户ID | StudentInfo | 学生提交报修时定位学生档案和宿舍 |
| StudentQueryPort.getByStudentId | 学生ID | StudentInfo | 查询报修记录时补充学生信息 |
| DormitoryQueryPort.getById | 宿舍ID | DormitoryInfo | 获取宿舍名称和负责宿管 |
| DormitoryQueryPort.listManagedDormitoryIds | 宿管用户ID | List<Long> | 宿管查看管辖报修 |
| UserQueryPort.getRealName | 用户ID | String | 页面显示处理人姓名 |

## 7. 单元测试说明

测试类：RepairServiceImplTest。

测试覆盖：

1. 学生提交报修后生成 PENDING 记录。
2. 未分配宿舍的学生不能提交报修。
3. 宿管不能处理非负责宿舍的报修。
4. 宿管接单后状态变为 ACCEPTED。
5. 修复结果为空时拒绝操作。
6. 宿管填写结果后状态变为 FIXED。
7. 没有负责宿舍时，宿管报修列表为空。
8. 学生报修列表能正确补充学生、宿舍和处理人信息。

测试使用 Mockito 模拟跨模块接口和 Mapper，不需要启动 MySQL 或完整 Spring Boot 应用。

运行测试：

    mvn test

如果只在 IDE 中运行，可以在 IDEA 或 Eclipse 中打开本目录作为 Maven 项目，运行 RepairServiceImplTest。

## 8. 可读性与可维护性设计

1. 业务规则集中在 RepairServiceImpl，Controller 不包含状态判断。
2. 报修状态使用 RepairStatus 枚举，避免魔法字符串。
3. 对外输入使用 Command，对外输出使用 VO，避免页面直接依赖 Entity。
4. 跨模块访问通过 QueryPort 接口，不直接注入其他模块的 Mapper。
5. 业务异常统一由 RepairExceptionHandler 转换为错误响应。
6. 关键状态变更使用条件更新 SQL，避免并发情况下重复接单或重复修复。
7. 单元测试覆盖正常流程、权限边界和异常流程。
8. 写操作使用 @Transactional，保证业务状态和日志字段的一致性。

## 9. 本地验证结果

本次使用 JDK 8 和本地 Maven 依赖完成编译验证：

- 主代码与测试代码编译通过。
- 8 个 JUnit 测试全部通过。
- 额外的内存版冒烟测试通过：提交报修 → 接单 → 标记修复 → 查询学生报修列表。
- 实际测试覆盖状态流转、越权处理、空修复结果、未分配宿舍和空管辖列表等关键边界。

正式环境中可执行：

    mvn test

如果 Maven 未安装，也可以在 IDEA 或 Eclipse 中导入 pom.xml 后运行 RepairServiceImplTest。

## 10. 集成到完整宿舍系统

完整项目中需要为三个 QueryPort 提供适配器：

    @Component
    public class StudentQueryAdapter implements StudentQueryPort {
        private final StudentService studentService;

        public StudentQueryAdapter(StudentService studentService) {
            this.studentService = studentService;
        }

        @Override
        public StudentInfo getByUserId(Long userId) {
            Student student = studentService.getByUserId(userId);
            return new StudentInfo(
                student.getId(),
                student.getUserId(),
                student.getStudentNo(),
                student.getName(),
                student.getDormitoryId()
            );
        }

        @Override
        public StudentInfo getByStudentId(Long studentId) {
            Student student = studentService.getById(studentId);
            return new StudentInfo(
                student.getId(),
                student.getUserId(),
                student.getStudentNo(),
                student.getName(),
                student.getDormitoryId()
            );
        }
    }

DormitoryQueryAdapter 和 UserQueryAdapter 的实现方式类似。

完整 Thymeleaf 系统中可以保留原有页面控制器，让它们调用 RepairService。本目录中的 RepairController 是可独立测试的 Web 适配器示例，也可作为后续 REST 接口的基础。

## 11. 本模块未包含的内容

- 完整登录与 Session 权限校验。
- 完整 Thymeleaf 页面。
- MySQL 数据源配置。
- 学生、宿舍、用户模块的完整实现。
- 图片上传和消息通知。

这些内容由完整宿舍管理系统的其他模块提供；本实验重点展示报修模块的核心业务、模块接口调用和可测试性。
