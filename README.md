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
*提示：程序利用了 Hibernate 的 `ddl-auto: update`，启动时会自动在数据库中创建所需的所有数据表。*

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