# 工程成长闭环升级与迁移指引

适用范围：当前包含 V1.0.11 的后端与配套前端。本文是部署步骤和验收条件；2026-10-11 开发验收只使用隔离 PostgreSQL，未执行真实环境升级。

## 版本与上线顺序

当前完整 P1 需要数据库迁移至 V1.0.11，以及 `/api/capabilities` 返回 requirementDefinition、executionStandards、decisionRecords、verificationRecords、activityTimeline、wikiKnowledge、requirementCloseout 均为 1。迁移版本代表结构，不能只凭数据库版本判定服务端具备接口能力。

1. 记录当前后端制品、前端制品、数据库版本、有效环境配置与 `flyway_schema_history`；取得可恢复备份并在副本验证恢复。
2. 在真实数据副本演练下述迁移及旧客户端操作。安排维护窗口，停止写入，再取得最终备份。
3. 启动新后端执行增量迁移，检查 Flyway 成功和 Hibernate validate；先验证旧客户端基本需求、任务、待办、Wiki。
4. 认证后检查能力接口，再发布配套前端，执行问题定义至收尾的抽样闭环。
5. 保留备份与原制品，恢复写入并观察错误、权限与保存情况。

## 数据库准备和执行

后端要求 JDK 21、PostgreSQL 15+；本批数据库演练为 PostgreSQL 16。迁移由应用启动时的 Flyway 执行，Hibernate 只 validate。不要修改已应用的脚本或手工删除历史记录来强制升级。

检查部署平台注入的 `SPRING_PROFILES_ACTIVE=prod`、`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD` 和 `JWT_SECRET`。JWT 直接读取环境变量；保持现有有效密钥与来源。显式配置连接目标与凭据，不能把仓库默认值当成目标环境配置。下面命令假定环境已配置，没有在命令行传入密码：

```powershell
java -jar target/reqflow-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod --spring.flyway.baseline-on-migrate=false
```

本命令适用于空库，或已有匹配 Flyway 历史的环境。仓库 prod 默认 baseline-on-migrate=true、baseline-version=1.0.0；升级演练显式关闭自动 baseline，避免错误接入未知存量库。非空且没有历史的数据库，必须先比对结构、迁移与备份再制定一次性接入方案，不能直接套用此命令或自动 baseline。

可在连接的目标副本中只读检查：

```sql
SELECT installed_rank, version, description, checksum, success
FROM flyway_schema_history ORDER BY installed_rank;
```

空库执行 V1.0.0～V1.0.11 共 12 个脚本；已有 V1.0.5 的库仅执行 V1.0.6～V1.0.11。现有脚本如下，业务原数据不要求回填新字段：

| 版本   | 增量与旧数据行为                                                                          |
| ------ | ----------------------------------------------------------------------------------------- |
| 1.0.6  | 定义 JSONB、版本/确认及个人需求审计；旧定义为空                                           |
| 1.0.7  | 阶段 goal/expectedOutput/exitCriteria、任务 deliverable/completionCriteria；旧标准为 null |
| 1.0.8  | 决策、上下文快照、替代关联与索引；不生成旧决策                                            |
| 1.0.9  | 验证历史、幂等、不可逆失效触发器、修复来源；不把旧 DONE 转为 PASS                         |
| 1.0.10 | Wiki 可空类型与创建幂等；旧正文、标签和分享链接保留                                       |
| 1.0.11 | 独立收尾、事实修订号与触发器；旧需求初始化修订号，不自动生成/确认收尾                     |

不要只比对行数：确认旧需求描述/状态/日期/项目，阶段和父子任务、依赖、待办来源、备注/自定义 JSONB、Wiki 正文/标签/分享链接。任务/需求数不变并不足以证明这些引用与内容正确。

## 验收与自动化复现

```powershell
mvn verify
```

需要 Docker；测试自行覆盖数据源，使用临时 PostgreSQL 容器，不连接仓库配置中的开发数据库。本批 48 项测试全部通过，37 项真实 PostgreSQL 集成测试。没有 Docker 时数据库测试会跳过，必须检查测试报告，不能只凭构建成功声明迁移通过。

`EngineeringGrowthLegacyMigrationTest` 从含旧需求、父子任务、依赖、待办和已分享 Wiki 的 V1.0.5 升级，校验历史与重跑 0 迁移，并执行旧 payload 的读写。各模块测试还演练 V1.0.7/8/9/10 升级。`EngineeringGrowthHttpIntegrationTest` 从空库初始化，经真实 TCP/认证走完定义、决策替代、失败修复重验、Wiki、收尾和权限闭环。

目标环境还需抽样：

- 旧客户端可以读写普通需求/任务/Wiki，省略新字段不清空已填标准与类型；局部更新和显式清空按接口契约执行。
- 已有分享链接仍可使用；内部 Wiki 读取与修改现在核对需求/创建者权限，列表只展示可访问文档。升级前核对原有成员和项目关系，不能用旧的宽松读取行为作为新权限预期。
- 新功能的定义确认、决策替代、验证历史、失败修复、收尾未决处置、Wiki 显式保存可完成；DONE/WAIVED/UNVERIFIED 不变成 PASS。
- 修改事实后已完成收尾提示复核；重开保留总结，旧版本/令牌返回 409，不覆盖其他人的结论。
- 使用未授权账户检查需求、决策、验证、Wiki、收尾的拒绝访问，且草稿没有自动分享。

## 功能关闭与故障处理

前端 `VITE_ENGINEERING_GROWTH=false` 是构建开关，需重新构建并分发，不是服务端运行时开关。PowerShell 示例：

```powershell
$env:VITE_ENGINEERING_GROWTH = 'false'
npm run build
Remove-Item Env:VITE_ENGINEERING_GROWTH
```

关闭隐藏新入口，不清理数据库/已保存记录；普通需求、任务和 Wiki 继续保留。旧后端能力接口返回 404 时配套前端隐藏不支持的功能；网络错误显示重试。完整功能必须以能力探测结果为准。

迁移或 validate 失败时停止对该目标的发布，保留日志、历史和备份，先在副本定位。优先用新增 migration 前向修复，不删除表或改历史 checksum。旧后端制品回退可能无法通过已升级数据库的校验，不能认为附加列必然支持直接回退。确需恢复时，在维护窗口同时恢复匹配数据库备份和制品，明确处理备份之后的写入；此恢复操作不属于自动化开发验收。

完整 AC-01～18 和本机试用证据见[前端技术验收报告](https://github.com/hjd92215202/reqflow-ui/blob/dev/docs/engineering-growth-acceptance.md)。技术验收不代替目标环境迁移演练、桌面安装验收和真实用户反馈。
