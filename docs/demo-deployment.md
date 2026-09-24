# 朋友内测部署

这套配置把 Vue 前端和 Spring Boot 后端放进两个 Docker 容器，只向本机 `127.0.0.1:8080` 暴露前端入口。Nginx 同源转发 `/api`，H2 数据库、头像、聊天记忆和生成文件保存在名为 `liu-ai-agent-demo-data` 的 Docker 卷中。

## 1. 填写环境变量

```bash
cp .env.demo.example .env.demo
openssl rand -hex 32
```

编辑 `.env.demo`，至少填写：

- `DASHSCOPE_API_KEY`
- `AUTH_JWT_SECRET`：粘贴上面 `openssl` 生成的随机值
- `AUTH_BOOTSTRAP_ADMIN_USERNAME`
- `AUTH_BOOTSTRAP_ADMIN_PASSWORD`
- `SEARCH_API_KEY`：不使用联网搜索时可以留空

首次邀请朋友注册时保留 `AUTH_REGISTRATION_ENABLED=true`。所有人注册完成后改为 `false`，再运行 `docker compose -f compose.demo.yml up -d --force-recreate backend` 让新的环境变量生效。

## 2. 构建并启动

```bash
docker compose -f compose.demo.yml up -d --build
docker compose -f compose.demo.yml logs -f backend
```

看到应用启动完成后，在另一个终端验证：

```bash
curl http://localhost:8080/api/health
```

返回 `ok` 即表示前后端代理正常。浏览器可先打开 `http://localhost:8080` 本地验证。

## 3. 创建临时公网地址

macOS 可先安装 Cloudflare Tunnel：

```bash
brew install cloudflared
cloudflared tunnel --url http://localhost:8080
```

把命令输出的 `https://...trycloudflare.com` 地址发给朋友。终端、Docker Desktop 和电脑需要保持运行；Quick Tunnel 重启后地址通常会变化。

## 日常操作

```bash
# 查看状态
docker compose -f compose.demo.yml ps

# 查看日志
docker compose -f compose.demo.yml logs -f --tail=200

# 更新代码后重新构建
docker compose -f compose.demo.yml up -d --build

# 停止服务，但保留数据
docker compose -f compose.demo.yml down
```

不要运行 `docker compose -f compose.demo.yml down -v`，除非确认要删除全部内测数据。`-v` 会一并删除持久化卷。

## 内测安全默认值

- 内测部署配置不注册终端执行、任意文件读写、网页抓取、资源下载和 PDF 写入工具。
- Swagger/Knife4j 在内测部署配置中关闭。
- AI 对话默认限制为每位登录用户每分钟 6 次，可通过 `AI_REQUESTS_PER_MINUTE` 调整。
- 只有 Nginx 绑定本机端口，后端端口不会直接暴露给局域网或公网。
