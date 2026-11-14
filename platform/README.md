# 平台模块（platform）

平台本体由两个进程组成：`server` 提供 REST API 与部署编排，`web` 提供操作界面。它不直接构建业务代码，构建与部署交给 GitLab CI / Runner；平台负责**规则、编排、记录和观察**。

## 技术栈

| 模块 | 技术 |
|---|---|
| `server` | Spring Boot 3.3.5 · Java 17 · MyBatis-Plus 3.5.9 · MapStruct 1.6.3 · Lombok · fabric8 6.13.4 · MySQL 8 |
| `web` | Vue 3 · Vite 5 · Element Plus · Pinia · Vue Router |
| `prototype` | 已确认的静态原型，作为视觉基准 |

## 目录

```
platform/
├─ server/src/main/
│  ├─ java/com/zhengte/platform/
│  │  ├─ controller/    REST 接口（auth / projects / requirements / deploy / approvals）
│  │  ├─ service/       业务接口 + DeployGate 门禁
│  │  ├─ service/impl/  业务实现
│  │  ├─ mapper/        MyBatis-Plus Mapper（SQL 只在这里）
│  │  ├─ entity/        表实体 Tables.* 与查询 record
│  │  ├─ dto/           API 出入参 Views
│  │  ├─ enums/         环境、服务、角色、各类状态
│  │  ├─ constants/     步骤、默认值、正则、Git/HTTP 常量
│  │  ├─ converter/     RecordMapper（表→record）、ViewConverter（record→视图）
│  │  ├─ config/        登录拦截、CORS、Git 配置
│  │  ├─ integration/   git（GitLab / Mock）、k8s（RuntimeService）
│  │  └─ utils/
│  └─ resources/
│     ├─ application.yml
│     ├─ schema.sql     幂等建表（启动时执行）
│     └─ data.sql       幂等基础数据（账号、项目）
└─ web/src/
   ├─ views/            Login / Requirements / Deploy / Approvals
   ├─ components/       需求、CR、部署确认、进度、弹窗壳
   ├─ stores/           auth、approval
   ├─ api/client.js     axios + Bearer token + 401 处理
   └─ styles.css        原型样式
```

## 启动

### 后端

```bash
cd platform/server
export PLATFORM_GIT_MODE=gitlab          # mock | gitlab
export GITLAB_TOKEN=<GitLab PAT>
export GITLAB_URL='http://[::1]:8080'    # 可选
mvn spring-boot:run                       # 默认 8090
```

未设置 `PLATFORM_GIT_MODE` 时默认 `mock`。mock 只模拟五步状态，不会触发 GitLab pipeline，适合纯页面演示。

### 前端

```bash
cd platform/web
npm install
npm run dev                               # 5173，/api 代理到 8090
```

### 数据库

后端启动时按 `spring.sql.init.mode=always` 执行 `schema.sql`、`data.sql`，两者都是幂等的（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`）。

> 不要给已存在的表在 `schema.sql` 里加列：每次启动都会执行，`continue-on-error=false` 会直接启动失败。新增状态请新建表，或用一次性增量 SQL 手工迁移。

当前数据库：MySQL 8（k3s `shared` 命名空间），Windows 侧通过 `localhost:3306` → WSL socat → NodePort `30306` 访问。库 `platform`，账号 `platform / platform_pw`。

## 配置

`server/src/main/resources/application.yml`：

| 配置 | 默认 | 说明 |
|---|---|---|
| `server.port` | `8090` | 平台后端端口 |
| `platform.git.mode` | `mock` | `mock` / `gitlab` |
| `platform.git.gitlab.url` | `http://[::1]:8080` | GitLab 地址 |
| `platform.git.gitlab.token` | 环境变量 | root PAT，不要写进仓库 |
| `platform.git.gitlab.projects` | `app-api:5`、`app-web:6` | 平台项目 → GitLab project id |
| `platform.runtime.mode` | `k3s` | `mock` / `k3s` |
| `platform.runtime.log-tail-lines` | `200` | Pod 日志尾部行数 |
| `platform.pipeline.poll-interval-ms` | `2000` | GitLab pipeline 轮询间隔 |
| `platform.pipeline.timeout-seconds` | `1800` | 流水线超时 |
| `platform.pipeline.step-delay-ms` | `1500` | mock 模式每步耗时 |

## 核心模型

### 项目与服务

- 平台项目 `app-api`（`service=api`）对应 GitLab `root/app-api`；
- 平台项目 `app-web`（`service=web`）对应 GitLab `root/app-web`；
- `projects.runtime_app` 决定 k3s 命名空间：`app-api-<env>` / `app-web-<env>`。

一条需求只属于一个项目、只操作一个仓库；「后端部署」只列 api 项目，「前端部署」只列 web 项目。

### 环境

`dev → test → uat → prod`。prod 对应 Git 的 `main`，其余环境对应同名分支。每个环境有独立的 `env_states` 记录和独立命名空间。

### 发布版本

每次 prod 成功都会产生一条 `prod_release_history`，并把需求写入 `prod_requirement_archives`。每个 `(project, service)` 只有一条当前版本；历史全部保留。

## 部署执行

平台把一次部署拆成五步，状态落库，可轮询、可终止：

1. 拉取代码
2. 编译
3. 镜像构建
4. 部署到 k3s
5. 健康检查

两种执行器：

| 模式 | 行为 |
|---|---|
| `mock` | 平台按 `step-delay-ms` 自己推进五步，不访问 GitLab |
| `gitlab` | 合并需求分支到环境分支 → 触发 pipeline → 每 2 秒轮询 pipeline/jobs → 回写五步 → 失败/终止时 cancel |

GitLab 契约：

- 触发：`POST /projects/{id}/pipeline`，变量 `DEPLOY_ENV`、`DEPLOY_SERVICE`、`DEPLOY_SHA`、`REQ_NO`。
- CI 在每个 job 的 `before_script` 里 `git checkout "$DEPLOY_SHA"`，镜像 tag 也用该 SHA，保证每级部署同一份提交。
- 两个仓库的 `workflow` 只接受带 `DEPLOY_ENV` 的流水线，避免合并提交再触发一条重复部署流水线。
- api 的 verify 使用 `PF_PORT=$((18000 + CI_PIPELINE_ID % 2000))`，并发 verify 不抢端口。

更完整的时序见 [`../docs/architecture.md`](../docs/architecture.md)。

## 门禁规则

| 规则 | 说明 |
|---|---|
| 分支 | 需求必须先有开发分支（日期前缀或绑定已有分支） |
| 顺序 | dev → test → uat → prod，不能跳级 |
| SHA 一致 | 目标环境的 SHA 必须等于该 service 分支的最新 tip |
| test CR | test 首次部署需要 `APPROVED` 的 CR；审批通过后平台自动发起 test 部署 |
| 串行 | 同一 `project + service + env` 同时只能有一个部署（`deploy_locks`） |
| 归档 | prod 成功后需求归档，不能再次普通部署；回滚不受限 |

## 合并冲突与终止

- **合并冲突**：环境状态置 `CONFLICT`，不触发流水线，MR 留在 GitLab；页面点「已解决」后回到 `READY`，再重新部署。
- **终止**：当前步骤置失败、部署 `CANCELLED`、环境回 `READY`、释放锁；GitLab 模式下同时 cancel pipeline。
- **失败**：批次 `FAILED`，环境回到可部署状态，页面区分「部署失败」和「部署已终止」。

## 发布归档与回滚

- prod 流水线 `success` 后，同一事务内：更新部署与环境状态 → 写发布历史 → 设置当前版本 → 归档需求。
- 同一 SHA 重复发布只更新当前行的发布时间，不新增重复历史。
- 回滚不合并代码：读取历史版本的 SHA，以 `ref=main` 触发 prod 流水线，`DEPLOY_SHA` 使用历史 SHA；成功后把该行设为当前版本。
- 历史行数不变；回滚到当前版本返回 400。
- 归档需求不能再从任何环境普通部署，但可以被回滚。

详细状态机与规则见 [`../docs/workflow.md`](../docs/workflow.md)。

## 运行时

`platform.runtime.mode`：

| 值 | 行为 |
|---|---|
| `mock` | 按 `env_states` 生成演示 Pod，扩缩容只改内存 |
| `k3s` | fabric8 直连集群，读写真实 Deployment / Pod / 日志 / Events |

命名约定：namespace = `<runtime_app>-<env>`，Deployment 名 = `api` / `web`，Pod 按 `app=<service>` 选择。

- Pod / 日志 / YAML / Events：所有用户可查看。
- 扩缩容 / 重启 Pod：仅 `CR_ADMIN`；副本 1~10，prod 下限 2。
- 扩缩容直接 patch Deployment，不写部署记录、不触发流水线。

## API 速查

除登录外都需要 `Authorization: Bearer <token>`。

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 token + user |
| POST | `/api/auth/logout` | 退出 |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/auth/approvers` | CR 管理员列表 |
| GET | `/api/projects` | 项目列表 |
| GET | `/api/requirements?projectId=app-api` | 我可见的需求 |
| GET | `/api/requirements/{reqNo}` | 需求详情 |
| POST | `/api/requirements` | 新建需求 |
| POST | `/api/requirements/{reqNo}/branch` | 创建分支（`suffix`） |
| POST | `/api/requirements/{reqNo}/branch/bind` | 绑定分支（`branch`） |
| POST | `/api/requirements/{reqNo}/cr` | 指定 CR（`service` / `approver` / `comment`） |
| GET | `/api/deploy/board?projectId&service&env` | 环境板 |
| POST | `/api/deploy/start` | 批量部署（`projectId` / `service` / `env` / `ids`） |
| POST | `/api/deploy/{batchId}/abort` | 终止部署 |
| POST | `/api/deploy/conflict/resolve` | 标记冲突已解决 |
| POST | `/api/deploy/rollback` | 回滚（`projectId` / `service` / `env` / `releaseId`） |
| GET | `/api/deploy/pods/logs?name=&projectId&service&env` | Pod 日志 |
| GET | `/api/deploy/pods/yaml?name=&projectId&service&env` | Pod YAML |
| GET | `/api/deploy/pods/events?name=&projectId&service&env` | Pod Events |
| GET | `/api/deploy/scale?projectId&service&env` | 当前副本数与上下限 |
| POST | `/api/deploy/scale` | 调整副本数（仅 CR_ADMIN） |
| POST | `/api/deploy/pods/restart` | 重启 Pod（仅 CR_ADMIN） |
| GET | `/api/approvals` | 待审批列表 |
| POST | `/api/approvals/{crId}/approve` | 通过并自动部署 test |
| POST | `/api/approvals/{crId}/reject` | 驳回 |

## 数据库

- 表结构与关系见 [`../docs/data-model.md`](../docs/data-model.md)。
- `prod_releases` 是旧版单版本回滚表，已不再读写，保留只读以兼容历史数据。
- 新增发布相关表：`prod_release_history`、`prod_requirement_archives`、`deployment_release_target`。

## 排错

优先看 [`../docs/troubleshooting.md`](../docs/troubleshooting.md)。几个高频点：

- 后端五步 1.5 秒跑完 → 实际是 mock 模式，检查 `PLATFORM_GIT_MODE=gitlab`。
- GitLab 打不开 → Windows 用 `http://[::1]:8080`，IPv4 8080 可能被 Steam 占用。
- Windows 连不上 MySQL / NodePort → 检查 WSL 里的 socat systemd 服务。
- test 部署被终止、Pod 却是 Running → 检查是否有两条 GitLab pipeline 并发。
