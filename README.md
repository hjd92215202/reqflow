# 🌊 ReqFlow Backend

**ReqFlow** 是一个专注于工作需求记录、阶段拆解与协作协同的管理系统。
本项目为 ReqFlow 的**后端服务**，基于 `Java 21` + `Spring Boot 3` + `PostgreSQL` 构建。

> 💡 **核心架构理念：Self-Hosted & Bring Your Own Backend**  
> 企业的需求与工作任务往往涉及高度机密。因此，ReqFlow 采用了**私有化自托管**模式。我们不提供中心化的 SaaS 服务，您可以将本后端一键部署在公司内网或个人云服务器上。数据完全掌握在您自己手中，配合 ReqFlow 桌面客户端，实现 100% 的数据隐私与安全。

---

## 🚀 特性

- ⚡️ **极致性能**：基于 JDK 21 虚拟线程 (Virtual Threads) 优化，完美支持高并发。
- 🔒 **安全隔离**：完全支持跨域 (CORS)，内置 JWT 无状态认证与 Bcrypt 密码哈希。
- 📦 **开箱即用**：提供 Docker 一键部署方案，免去繁琐的环境配置。
- 📊 **动态扩展**：利用 PostgreSQL 的 `JSONB` 特性，原生支持需求属性与子任务标签的无限扩展。

---

## 🐳 Docker 一键部署 (推荐)

最简单、最不易出错的部署方式。只需一台安装了 [Docker](https://www.docker.com/) 和 `docker-compose` 的服务器。

### 1. 克隆项目
```bash
git clone https://github.com/your-username/reqflow-backend.git
cd reqflow-backend
```

### 2. 配置环境变量 (可选但推荐)
项目中已自带 `docker-compose.yml`，默认可以直接启动。但为了生产环境安全，建议使用文本编辑器打开 `docker-compose.yml`，修改以下环境变量：
- `POSTGRES_PASSWORD`: 数据库的密码
- `SPRING_DATASOURCE_PASSWORD`: 需与上方数据库密码保持一致
- `JWT_SECRET`: 强烈建议修改为一段随机且复杂的长字符串，用于签发用户 Token

### 3. 启动服务
```bash
docker-compose up -d
```
*首次启动时，Docker 会自动使用 Maven 编译源码并构建出极轻量级的运行镜像，请耐心等待几分钟。构建完成后，服务将运行在服务器的 `8080` 端口。*

### 4. 客户端连接
启动完成后，下载并打开 **ReqFlow 桌面客户端**，在登录/注册界面的“服务器地址”处输入：
```text
http://您的服务器IP:8080
```
*(如果没有客户端账号，直接在客户端点击“注册账户”即可开始使用！)*

---

## ⚙️ 环境变量说明

如果您不使用 Docker Compose，而是想集成到 k8s 或自定义的 CI/CD 流程中，本程序支持以下核心环境变量注入：

| 环境变量名 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/req_tracker_prod` | PostgreSQL 数据库连接地址 |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | 数据库用户名 |
| `SPRING_DATASOURCE_PASSWORD` | `123456` | 数据库密码 |
| `JWT_SECRET` | `reqflow_default_dev_...` | JWT 签名密钥 (生产环境务必重写此值) |

---

## 💻 本地开发与源码运行

如果您想参与二次开发或手动编译，请确保本地已安装 `JDK 21` 和 `Maven 3.8+`。

### 1. 准备数据库
在本地启动一个 PostgreSQL 15+ 实例，创建名为 `req_tracker` 的数据库。

### 2. 修改开发配置
打开 `src/main/resources/application-dev.yml`，修改为您本地的数据库账号密码：
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/req_tracker
    username: postgres
    password: 123
```

### 3. 运行服务
```bash
mvn clean spring-boot:run
```
*数据库结构由 Flyway 的版本化 migration 管理，Hibernate 使用 `ddl-auto: validate` 进行校验。升级前请备份并确认 `flyway_schema_history` 与当前环境配置，不要修改已经应用的 migration。*

### 工程成长闭环：问题定义

新增 `V1.0.6__requirement_definition.sql`，保留旧需求字段并增加结构化定义、版本和确认信息。配套前端通过 `GET /api/capabilities` 检查能力；定义提供 GET/PUT `/api/requirements/{id}/definition` 与 POST `/api/requirements/{id}/definition/confirm`，全部需要 JWT 和需求访问权限。

保存请求为 `{ "version": 0, "definition": { "problemStatement": "问题", "targetOutcome": "目标", "constraints": [], "assumptions": [], "outOfScope": [], "successCriteria": [] } }`。确认请求为 `{ "version": 1 }`；版本不匹配返回 409，问题、目标和成功标准不完整时不能确认。完整内容变更使原确认失效，确认身份由服务端取得。

### 工程成长闭环：阶段与任务标准

新增 `V1.0.7__execution_standards.sql`：阶段可选 `goal`、`expectedOutput`、`exitCriteria`，任务可选 `deliverable`、`completionCriteria`；新列均为 nullable TEXT，旧记录保持为空。

沿用阶段和子任务 CRUD；PUT 省略新字段保留原值，显式 null/空串/纯空白清空，其余文本裁剪首尾空白，各最多 10000 个 UTF-16 字符。SubTask PUT 使用局部更新 DTO，省略日期、负责人、备注、自定义字段也保留；不可借此修改所属阶段或父任务。所有接口沿用需求访问权限。空标准不影响 DONE；普通待办更新和旧实体并发保存保留标准。能力接口返回 `executionStandards: 1`，前端据此开放入口；同一标准多客户端编辑以最后写入为准。

### 工程成长闭环：决策记录

新增 `V1.0.8__decision_records.sql`，能力接口返回 `decisionRecords: 1`。GET/POST `/api/requirements/{requirementId}/decisions` 提供分页查询/创建，GET/PUT `/{id}` 读取/更新，POST `/{id}/supersede` 创建替代记录。创建请求为 `{ stageId?, subTaskId?, content }`，编辑/替代为 `{ version, content }`，身份与上下文快照由服务端取得。

标题和问题背景必填。PROPOSED 可不填最终选择；ACCEPTED 须填写最终选择与理由；REJECTED 须有理由。候选方案、风险、信心、复查日期及用户填写的 AI 参与说明均可选。只允许替代已采纳记录，新记录也须已采纳；同一事务保存新决策、旧决策 SUPERSEDED 状态、双向引用和审计。版本冲突返回 409，旧决策只读，不覆盖历史。相同内容保存不递增版本或重复审计。

需求创建人或授权项目成员可读写；跨需求阶段/任务关联被拒绝，客户端不能改作者、时间或已有上下文。列表按创建时间及 ID 降序，支持 stageId/subTaskId/status 与 page/size；page 最小 0，size 裁剪至 1～100。删除阶段/任务置空对应关联 ID，保留名称快照；删除整个需求级联删除决策。没有独立删除决策接口，也不保存普通编辑的每次全文修订。

运行 `mvn verify` 执行测试、打包与格式检查。数据库集成测试需要 Docker，使用隔离 PostgreSQL 容器演练 V1.0.5/V1.0.7 存量需求/阶段/任务升级至 V1.0.8、JSONB 读写、标准 CRUD、权限、显式清空、待办兼容及并发保护，并验证决策状态、替代竞争、审计回滚、删除关联后的历史及稳定分页。不连接配置中的远程开发数据库；没有 Docker 时数据库测试会明确跳过。

---

## 🛠 初始化测试数据 (可选)

系统启动并自动建表后，如果您不想通过前端页面注册，而是想直接注入一个管理员账号，可在您的 PostgreSQL 数据库中执行以下 SQL：

```sql
-- 插入一条初始测试用户数据 (账号: admin, 明文密码: 123456)
-- 密码采用 bcrypt 加密存储，切勿直接明文修改
INSERT INTO sys_user (username, password_hash, nickname)
VALUES ('admin', '$2a$10$X9D38iKkPzO9I8nQ4CqWfO1VwepvGgKq9K7W/H8HqH8K8H8K8H8K8', '超级管理员');
```

---

## 📄 生产环境手动启动参数

如果您是将 `jar` 包直接传到服务器手动运行，可以通过命令行参数强制激活生产环境 (`prod`) 配置：

```bash
java -jar reqflow-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod \
  --SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/reqflow \
  --SPRING_DATASOURCE_USERNAME=root \
  --SPRING_DATASOURCE_PASSWORD=your_password \
  --JWT_SECRET=your_super_secret_key
```
