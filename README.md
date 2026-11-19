# checkdata-agent

`checkdata-agent` 是基于 `spring-ai-agentx` 构建的 Java Web 智能体项目，当前最核心的能力是 **data-agent 数据分析智能体**。

用户可以用自然语言查询业务数据库，系统会自动理解问题、查看表结构、生成 SQL、校验 SQL、执行查询，并结合数据权限、敏感字段脱敏、复杂计算和图表生成，最终输出一份数据分析报告。

除了 data-agent，项目还内置了用户管理、Skills、文件问答和联网查询等能力，用来展示一个完整业务智能体在真实 Web 项目中的落地方式。

## 当前功能

### data-agent 数据分析

这是 checkdata-agent 当前最核心的功能。

- 自然语言查询示例业务库。
- 自动查看表结构、生成 SQL、校验 SQL、执行 SQL。
- SQL 安全校验：只允许只读查询，拦截危险语句，强制 LIMIT。
- 数据权限：根据当前登录用户的角色和部门自动改写 SQL，限制可见数据范围。
- 敏感字段脱敏：密码、身份证号、家庭住址等字段返回 `********`。
- 复杂计算：通过 `CalculateTool` 计算环比、占比、贡献度等指标。
- 图表生成：通过 `mcp-echarts` 生成图表，并上传到 MinIO 返回图片 URL。

### 用户管理

- 基于 Sa-Token 实现登录、退出、当前用户查询。
- 支持用户、角色、部门管理。
- 内置 `sys_user`、`sys_role`、`sys_dept`、`sys_user_role`、`sys_user_dept` 等系统表。
- 支持 `ALL`、`DEPT_AND_SUB`、`DEPT`、`SELF` 四种数据范围。

### Skills

- 支持 Skills 列表展示、启用、停用、上传、删除。
- 支持本地 `skills/` 目录和数据库记录同步。
- 内置示例 Skill：`data-analysis`、`skill-creator`、`域名创意生成器`。

### 文件问答

- 支持文件上传、解析、预览和多轮追问。
- 支持 PDF、Office、Markdown、HTML、纯文本等格式。
- 小文件直接读取，大文件自动切分并走向量检索。
- 支持图片多模态理解。

### 联网查询

- 通过 Tavily MCP 做实时联网搜索。
- 前端提供联网开关。
- 关闭联网时不注入搜索工具，开启后 Agent 按需调用 Tavily。

## 环境要求

| 组件 | 用途 |
|---|---|
| JDK 21 | 编译和运行项目 |
| Maven 3.9+ | 构建项目 |
| MySQL 8.0+ | 主数据库、示例业务数据、用户权限数据 |
| Redis | Sa-Token 会话、Schema 缓存 |
| PostgreSQL + pgvector | 大文件 RAG 和语义记忆 |
| MinIO | 文件存储、图表图片存储 |
| Node.js 18+ | 运行 `mcp-echarts` 图表服务 |

## 快速启动

以下命令默认在仓库根目录 `checkdata-agent/` 下执行。

### 初始化数据库

创建数据库：

```sql
CREATE DATABASE checkdata_agent DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

导入初始化脚本：

```bash
mysql -u root -p checkdata_agent < sql/init.sql
```

`init.sql` 包含项目运行需要的表、示例业务数据、用户角色部门数据。

### 准备配置文件

复制配置模板：

```bash
cp src/main/resources/application-example.yml src/main/resources/application.yml
```

修改 `application.yml` 中的占位符：

| 配置项 | 说明 |
|---|---|
| `spring.datasource.*` | MySQL 配置 |
| `spring.data.redis.*` | Redis 配置 |
| `spring.ai.deepseek.api-key` | DeepSeek API Key，主模型使用 |
| `spring.ai.openai.api-key` | DashScope API Key，多模态和 Embedding 使用 |
| `skills.directory` | 本地 `skills` 目录绝对路径 |
| `minio.*` | MinIO 配置 |
| `embeddings.store.*` | PgVector 配置 |
| `tavily.api-key` | Tavily API Key，不使用联网可保持占位 |
| `data-agent.chart.mcp-url` | mcp-echarts 地址，默认 `http://localhost:3033/mcp` |

### 启动 MinIO

按你的环境启动 MinIO，并保证 `application.yml` 中的 `minio.*` 配置正确。

MinIO 主要用于存储上传文件和图表图片。

### 启动 mcp-echarts

安装：

```bash
npm install -g @mcp-echarts/server
```

Windows 示例：

```bash
cmd /c "set MINIO_ENDPOINT=<your-minio-host>&& set MINIO_PORT=19000&& set MINIO_USE_SSL=false&& set MINIO_ACCESS_KEY=<your-minio-access-key>&& set MINIO_SECRET_KEY=<your-minio-secret-key>&& set MINIO_BUCKET_NAME=checkdata-agent-files&& mcp-echarts -t streamable"
```

启动后默认监听：

```text
http://localhost:3033/mcp
```

建议使用 `streamable` 模式。不要让图表直接返回 base64，否则会占用大量大模型上下文。

### 编译并启动 checkdata-agent

在仓库根目录先编译整个项目：

```bash
mvn clean install -DskipTests
```

编译成功后，直接在 IDE 中运行 checkdata-agent 的启动类：

```text
com.checkdata.agent.CheckdataAgentApplication
```

启动成功后访问：

```text
http://localhost:8889/
```

## 默认账号

初始化脚本内置了示例账号：

| 账号 | 密码        | 说明 |
|---|-----------|---|
| `admin` | `123456`  | 管理员，数据范围为 `ALL` |

其他用户管理中的内置用户，密码均为：123456
可以用两个账号问同一个 data-agent 问题，对比数据权限和敏感字段脱敏效果。

## 常见问题

### 编译后依赖找不到

请确认你是在项目根目录执行过完整编译：

```bash
mvn clean install -DskipTests
```

这样会把所有依赖安装到本地 Maven 仓库，项目才能正常启动。

### 图表生成失败

优先检查：

- `mcp-echarts` 是否已启动。
- `data-agent.chart.mcp-url` 是否为 `http://localhost:3033/mcp`。
- MinIO 环境变量是否和 `application.yml` 中配置一致。
- `mcp-echarts` 是否使用 `-t streamable` 启动。

### 不想启用联网搜索

可以不配置 Tavily API Key，并在前端关闭联网开关。关闭后不会注入 Tavily 工具。