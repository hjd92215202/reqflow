-- 插入一条初始测试用户数据 (密码明文为: 123456, 此处使用 bcrypt 加密后的 hash)
INSERT INTO sys_user (username, password_hash, nickname)
VALUES ('admin', '$2a$10$X9D38iKkPzO9I8nQ4CqWfO1VwepvGgKq9K7W/H8HqH8K8H8K8H8K8', '管理员');


生产部署时：您可以在服务器上使用命令行参数来临时覆盖激活的环境，而无需修改代码文件，例如：
java -jar reqflow-backend.jar --spring.profiles.active=prod


