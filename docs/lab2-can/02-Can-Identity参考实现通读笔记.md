# Lab2・Can0401 交付物 ②：Identity 参考实现通读笔记（PB-05）

> 作者：Can0401 ｜ 对应任务：通读 Identity 参考实现（PB-05）
> 阅读范围：
>
> `backend/co-identity`
>
>  五层（api /domain/application /infrastructure/interfaces）+ 领域测试
> 用途：S0 架构理解笔记（docs/notes/architecture.md）素材 + Sprint 1 实现 Q&A 时的 "照抄模板"
> 日期：2026-10-26



***

## 1. 分层结构与文件地图

Identity 是唯一完整实现的上下文，是其他三个上下文的**教科书**。5 个 Maven 模块依赖方向严格由外向内（ArchUnit FF-4 守护）：



| 模块                         | 职责                         | 关键文件                                                                                                 | 依赖                            |
| -------------------------- | -------------------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------- |
| co-identity-api            | 发布语言：查询接口 + 只读 DTO + 集成事件  | `IdentityApi`、`UserSummary`、`CourseSummary`、`event/UserRegisteredEvent`                              | 仅 JDK + 共享内核（FF-5）            |
| co-identity-domain         | 业务规则：聚合根、策略、仓储**接口**       | `User`、`Course`、`CourseMember`、`PasswordPolicy`、`UserRepository`、`CourseRepository`                  | 共享内核（零 Spring/JPA，FF-1）       |
| co-identity-application    | 用例编排：事务边界、端口调用、事件、审计       | `UserRegistrationService`、`CourseService`、`AdminUserService`、`IdentityApiImpl`、`UserQueryService`    | domain + 本上下文 api + 共享内核      |
| co-identity-infrastructure | 端口实现：JPA、加密、安全、限流          | `UserRepositoryAdapter`、`UserJpaEntity`、`FieldEncryptor`、`BCryptPasswordHasher`、`RedisLoginThrottle` | domain + application          |
| co-identity-interfaces     | REST 控制器 + 请求校验 + Actor 解析 | `AuthController`、`CourseController`、`AdminUserController`                                            | application + shared.security |



***

## 2. 领域层模式（Q\&A 照抄清单 ①）

### 2.1 聚合根：工厂方法创建，重建不校验

`User` 的完整骨架（Q\&A 的 `Question`/`Answer` 骨架已预置同样的结构，只需填方法体）：



```
private User(Long id, ...) { ... }                    // 私有构造器，外部只能走工厂

public static User register(String username, String displayName, String email,
                            String passwordHash, String college, String className, Instant now) {
    return new User(null, checkUsername(username), checkDisplayName(displayName), checkEmail(email),
            Objects.requireNonNull(passwordHash), Role.STUDENT, UserStatus.ACTIVE, false,
            optional(college, 50), optional(className, 50), now, 0);
}

public static User reconstitute(Long id, ..., long version) {  // 仅供仓储重建，不做业务校验
    return new User(id, ...);
}

public void assignId(long id) {                       // 持久化后回填主键；重复分配抛 IllegalStateException
    if (this.id != null) { throw new IllegalStateException("ID 已分配"); }
    this.id = id;
}
```

要点：



* **校验集中在工厂方法**（`register` 内调用 `checkUsername/checkDisplayName/checkEmail`），应用服务不 new 半成品再 set；

* **reconstitute 零校验**，保证历史数据可加载（`UserRepositoryAdapter.toDomain` 直接用它）；

* **显式传入操作者**：`verify(Actor)`、`changeRole(Actor, Role)`、`disable(Actor)` 内部调用 `requireAdmin(actor)`，领域层不碰 SecurityContext（Q\&A 对应 `Question.accept(Answer, Actor, boolean)`）。

### 2.2 领域策略（静态工具类）

`PasswordPolicy.validate(raw)`：8–64 位 + 含字母和数字，违反抛 `BusinessRuleException("ID-1004", ...)`。

→ Q\&A 对应：`MarkdownBody.ofQuestion/ofAnswer`、`Title`、`TagName` 的**值对象紧凑构造器内自校验**（指导书 3.6）。

### 2.3 仓储端口（依赖倒置）

`UserRepository` 接口在领域层，签名直接返回**领域对象** `User`（不是 JPA 实体）：



```
public interface UserRepository {
    Optional<User> findById(long id);
    boolean existsByUsername(String username);
    List<User> findAllById(Collection<Long> ids);      // 批量取，避免 N+1
    PageResult<User> findByVerified(boolean verified, PageRequest page);
    User save(User user);
}
```

→ Q\&A 的 `QuestionRepository`/`AnswerRepository`/`VoteRepository`/`CommentRepository`/`TagRepository` 已按同样风格预置。



***

## 3. 应用层模式：七步编排模板（Q\&A 照抄清单 ②）

### 3.1 UserRegistrationService.register —— 必背模板



```
@Transactional
public long register(RegisterUserCommand cmd) {
    PasswordPolicy.validate(cmd.password());                  // 1 策略校验（先于工厂）
    User user = User.register(cmd.username(), ..., clock.instant()); // 2 工厂创建聚合
    ensureUnique(user);                                       // 3 业务查重（Conflict 409）
    User saved = users.save(user);                            // 4 保存
    events.publish(new UserRegisteredEvent(EventIds.next(), clock.instant(),   // 5 同一事务内发布事件
            saved.id(), saved.displayName(), saved.role()));
    audit.record("USER_REGISTERED", saved.id(), "USER",       // 6 审计
            String.valueOf(saved.id()), Masking.account(saved.username()));
    return saved.id();                                        // 7 返回
}
```

7 个要点（评审必问）：



1. **@Transactional**：保存 + 发事件在**同一本地事务**（发件箱保证至少一次，指导书 2.5.1）；

2. **Clock 注入**：`clock.instant()` 而非 `Instant.now()`（可测试性，指导书 3.6）；

3. **事件在保存之后发布**，事件内容用保存后的 id；

4. **Masking.account(username)**：敏感字段脱敏再写审计；

5. 唯一性冲突抛 `ConflictException("ID-2001", ...)` → 409；

6. 异常全部带错误码（`BusinessRule/Conflict/Forbidden/NotFound` 四类 + 码）；

7. 返回 `long id`，由控制器包成 `{ "id": ... }`。

### 3.2 变体模板



| 服务                             | 用例      | 模板差异                                                                                   |
| ------------------------------ | ------- | -------------------------------------------------------------------------------------- |
| `AdminUserService.verify`      | 管理员认证用户 | 加载→**领域方法校验权限**→保存→审计（无事件）                                                             |
| `AdminUserService.createStaff` | 创建教职工   | 同 register，但工厂 `User.createStaff(actor,...)` 工厂内校验管理员；事件复用 `UserRegisteredEvent`       |
| `CourseService.create`         | 建课      | 工厂 `Course.create`（校验教师 / 管理员）→ 查重（学期内代码唯一）→ 保存 → 保存 `CourseMember.teacher(...)`（同一事务） |
| `CourseService.join`           | 加入课程    | 加载课程（无则 404）→ 查重复加入（409）→ 保存成员                                                         |
| `CourseService.assignRole`     | 任命助教    | 权限在应用层判断（管理员或课程教师）→ 加载成员（404）→ **成员。领域方法 changeRole**（内护 "教师不可降级"ID-1013）→ 保存          |

注意：权限判断分两级 ——URL 级放 `SecurityConfig`（hasRole ADMIN），**业务级仍在领域方法**（`requireAdmin`/`canManageCourse`），缺一不可。

### 3.3 只读查询模式

`CourseService.listAll / myCourses / members`、`UserQueryService.me` 全部 `@Transactional(readOnly = true)`，且：



* **批量取作者**：`users.findAllById(...)` 一次拿回 Map，避免循环内逐条查询（N+1）；

* 视图 DTO 单独定义（`CourseView`/`CourseMemberView`/`MeView`），不直接暴露领域对象；

* 缺名字兜底 `"未知用户"` / `"—"`。



***

## 4. 开放主机服务（OHS）：IdentityApi 与事件

### 4.1 IdentityApiImpl（Q\&A 的 QaQueryApiImpl 照抄）



```
@Service @Transactional(readOnly = true)
public class IdentityApiImpl implements IdentityApi {
    public Map<Long, UserSummary> findUsers(Collection<Long> userIds) { ... } // 批量，缺 id 不出现
    public Optional<CourseSummary> findCourse(long courseId) { ... }
    public boolean canManageCourse(long userId, Long courseId) {
        // 管理员 → true；courseId==null 时全局教师 → true；否则查课程成员角色
    }
}
```



* `UserSummary` 只含 `id, displayName, role, verified, college` —— **不含 username（学号）/email**（隐私，FF-2 语义）；

* `canManageCourse` 是 Q\&A/Reputation 一切权限判定的统一入口（采纳、认证、悬赏都用它）。

### 4.2 集成事件

`UserRegisteredEvent` 是 `record(String eventId, Instant occurredAt, long userId, String displayName, Role role) implements IntegrationEvent` —— 跨上下文**不可变值对象**，只放消费方需要的字段（FF-5：api 包只依赖 JDK + 共享内核）。



***

## 5. 接口层模式（Q\&A 控制器照抄）

`AuthController` 三个要点：



1. **Actor 由框架注入**：`@Parameter(hidden = true) Actor actor`（`ActorArgumentResolver` 从会话解析），应用层不用 Spring Security 类型；

2. **命令 record + Bean Validation**：`RegisterRequest` record 内嵌 `@NotBlank @Size(...)`，控制器转 `RegisterUserCommand`；

3. **状态码语义**：注册 201 + `{"id":n}`；登录返回 `MeView`；写操作 204；`/csrf` 先取 token（双提交 CSRF）。



***

## 6. 基础设施层模式（Q\&A 适配器照抄）



| 模式    | 实现                                                                                             | Q\&A 对应                                                                    |
| ----- | ---------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| 手写映射器 | `UserRepositoryAdapter.toDomain/toJpa`：JPA 实体 ⇄ 领域对象，`reconstitute` 重建（ADR-008，领域对象与 JPA 实体分离） | `QuestionRepositoryAdapter` 等已预置                                           |
| 字段加密  | `FieldEncryptor`：email 存 AES-GCM 密文 + HMAC 摘要（`existsByEmailHash` 查重不解密）                       | 无需（Q\&A 无敏感字段）                                                             |
| 乐观锁   | `@Version private long version` → 409 SYS-409-CONCURRENT                                       | `QuestionJpaEntity` 等已带 @Version                                           |
| 表名前缀  | `id_user` / `id_course`（FF-6：本上下文前缀）                                                           | `qa_question` / `qa_answer` / `qa_vote` / `qa_comment` / `qa_question_tag` |
| 密码哈希  | `BCryptPasswordHasher implements PasswordHasher`（领域端口）                                         | —                                                                          |
| 限流    | `RedisLoginThrottle`：1 分钟 5 次失败 → 429 AUTH-429                                                 | —                                                                          |
| 会话安全  | Spring Session + Redis，登录成功后 `changeSessionId()` 防会话固定；禁用账号可删会话下线                              | —                                                                          |



***

## 7. 对 Sprint 1（Q\&A）的模仿清单 —— 落地到每个方法



| Identity 原型                           | 照抄到 Q\&A                                                                                                 |
| ------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| `User.register`（工厂校验）                 | `Question.post`、`Answer.submit`、`Vote.cast`、`Comment.post`                                               |
| `User.reconstitute` / `assignId`      | `Question.reconstitute` / `assignId`（骨架已有，勿改）                                                            |
| `PasswordPolicy.validate`             | `MarkdownBody.ofQuestion/ofAnswer`、`Title`、`TagName` 紧凑构造器自校验                                            |
| `requireAdmin(actor)`                 | `Question.accept/edit/softDelete` 用 `actorManagesCourse` 参数（应用层查 `IdentityApi.canManageCourse` 后传入）      |
| `UserRegistrationService.register` 七步 | `QuestionCommandService.post/edit/delete`、`AnswerCommandService.submit/accept/delete`、`VoteService.cast` |
| `IdentityApiImpl.findUsers` 批量        | `QuestionQueryService.list/detail` 用 `identity.findUsers(ids)` 一次取作者                                     |
| `Masking.account`                     | 列表 / 详情不暴露邮箱、学号（Q\&A 本来就没有，输出仅作者 id / 昵称）                                                                |
| `PageRequest/PageResult`              | `QuestionQueryService.list` 分页结构 `{items,total,page,size}`                                               |
| `@Transactional(readOnly=true)` 查询    | `QuestionQueryService.list/detail/popularTags`                                                           |
| `audit.record(...)`                   | 采纳 `ANSWER_ACCEPTED`、删除 `QUESTION_DELETED` 等审计（指导书 6.3.3）                                                |
| 事件在保存后发布                              | `QuestionPostedEvent`/`AnswerAcceptedEvent`/`VoteCastEvent` 等（Q\&A 是全部事件的源头）                             |



***

## 8. 测试风格指引（S1 写测试时参考）

`UserTest` / `CourseTest` 的套路：



* 固定 Actor 常量（`ASKER`/`OTHER`/`TEACHER`/`ADMIN`）；

* 测试名即行为声明（`otherStudentsCannotAccept`、`askerCanDeleteOnlyWhenNoAnswers`…）；

* 只测领域规则，不启动 Spring（纯 JUnit + AssertJ）；

* 违反规则断言异常类型 + 错误码 + 消息关键词。

→ Q\&A 已有 `QuestionTest`（Posting/Accepting/Editing/Deleting 四组）、`AnswerTest`、`VoteAndCommentTest`，S1 只需让它们全绿，再按 QA-T15 补边界测试。



***

## 9. 常见坑（给团队的提醒）



1. **TODO (S2) = 指导书 Sprint 1**：代码里 Q\&A 的 TODO 标的是 `S2`，这是起始代码 README 的旧标记，指导书附录 A 明确 "原 S2 对应本指导书 Sprint 1"，别按字面理解成第 4 周做。

2. **不要改 reconstitute / 已有测试断言**（学术诚信红线，9.3 节）：reconstitute 只重建状态。

3. **事件必须在 @Transactional 内发布**，否则发件箱跨事务写入会丢事件。

4. **计数器不回写**（ADR-008）：`score/answerCount/viewCount` 由仓储 `adjustXxx` 原子 SQL 维护，领域层永远不 `setAnswerCount(...)`。

5. **依赖方向**：application 可以依赖 `identity-api`（`IdentityApi`），domain 绝不能 import 任何 Identity 类（FF-2 会红）。