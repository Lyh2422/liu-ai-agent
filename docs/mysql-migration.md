# H2 迁移到 MySQL

## 当前状态

应用的默认和 `prod` 数据源已改为 MySQL 8：

- JDBC 驱动：`com.mysql.cj.jdbc.Driver`
- schema 管理：Flyway
- JPA 策略：`ddl-auto: validate`
- 编码：`utf8mb4`
- JDBC 时区：UTC

H2 依赖仍保留为测试和一次性搬迁工具使用，不再是默认业务数据库。

## 新环境启动 MySQL

本地 Docker：

```sh
cp .env.mysql.example .env.mysql
docker compose --env-file .env.mysql -f compose.mysql.yml up -d
```

生产整套服务：

```sh
cp .env.prod.example .env.prod
# 将所有 replace-with-* 换成真实密钥
docker compose --env-file .env.prod -f compose.prod.yml up --build -d
```

`.env.mysql` 和 `.env.prod` 已加入 `.gitignore`。

当前开发机因为 `3306` 已有其他 MySQL，迁移后的隔离实例使用
`127.0.0.1:3307`，数据目录是 `tmp/mysql-data`。机器重启后可运行：

```sh
./scripts/start-local-mysql.sh
```

这个辅助脚本只启动已经初始化的本机实例；新环境仍建议使用上面的
Docker Compose 配置。

## 搬迁存量 H2 数据

迁移工具会：

1. 检查 H2 文件没有被后端占用。
2. 创建带 UTC 时间戳的 H2 备份。
3. 用 Flyway 建立 MySQL schema。
4. 在一个 MySQL 事务中复制全部业务表。
5. 逐表对比 H2 和 MySQL 的记录数；不匹配则回滚。

目标 MySQL 必须是空的，工具不会把 H2 数据合并进已有业务数据。

```sh
# 先停止会写入 H2 的后端
export TARGET_MYSQL_URL='jdbc:mysql://127.0.0.1:3306/liu_ai_agent?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true'
export TARGET_MYSQL_USERNAME='liu_ai_agent'
export TARGET_MYSQL_PASSWORD='your-password'
./scripts/migrate-h2-to-mysql.sh
```

如果 H2 不在默认位置，可额外传入：

```sh
export H2_DATABASE_FILE='/absolute/path/database.mv.db'
export SOURCE_H2_URL='jdbc:h2:file:/absolute/path/database;MODE=MySQL;AUTO_SERVER=TRUE'
```

## 回滚

搬迁不会删除 H2。需要回滚时：

1. 停止后端，避免 MySQL 继续产生新数据。
2. 把数据源 URL 改回 `jdbc:h2:file:...`。
3. 使用脚本创建的 `*.mv.db.backup-<UTC时间>` 备份。

回滚只能恢复到搬迁时刻；切换后新写入 MySQL 的数据不会自动反向同步到 H2。
