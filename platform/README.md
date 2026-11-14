# 工程平台（platform）

需求 → 分支 → 四环境逐级部署的平台。当前是可本地运行的第一版：

- `server/` Spring Boot 3.3.5 + Java 17 + MyBatis-Plus + Lombok + MapStruct + MySQL 8
- `web/` Vue 3 + Vite + Element Plus + Pinia + Vue Router
- `prototype/` 已确认的静态原型（视觉基准，不再改动）

## 当前实现范围

| 模块 | 已实现 |
|---|---|
| 登录 | 账号密码登录，服务端 `sessions` 表保存 UUID token，7 天过期 |
| 需求 | 只看自己创建/相关的需求；新建需求、创建或绑定开发分支、指定 CR |
| 部署 | dev / test / uat / prod 环境板；门禁校验；批量部署；失败重试；合并冲突解决；生产回滚 |
| 部署执行 | 异步五步流水线（拉取代码 / 编译 / 镜像构建 / 部署到 k3s / 健康检查），进度落库、可终止 |
| CR | test 首次部署前的审批待办；通过后自动部署 test；驳回后回到重新指定状态 |
| Pod | `RuntimeService` 双实现：mock（演示数据）或 k3s（真实 Pod / 日志 / YAML / 事件） |
| 运维 | 管理员扩缩容、重启 Pod；副本数区间与环境下限校验 |

部署执行有**双执行器**，由 `platform.git.mode` 选择：

- `mock`（默认）：平台自己维护五步状态，适合前端演示。
- `gitlab`：平台向 GitLab 触发 pipeline，轮询 job 状态回写到五个步骤；终止部署时调用 GitLab cancel。

Pod 数据和扩缩容同样可以通过配置切到真实 k3s（见下文「运行时接入」）。

## 目录

```
platform/
├─ server/
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/zhengte/platform/
│     │  ├─ controller/   REST 接口（auth / projects / requirements / deploy / approvals）
│     │  ├─ service/      业务接口 + DeployGate 门禁规则
│     │  ├─ service/impl/ 业务实现（XxxServiceImpl）
│     │  ├─ mapper/       MyBatis-Plus Mapper，SQL 只在这里
│     │  ├─ entity/       表实体（Tables.*）与查询投影
│     │  ├─ dto/          API 出入参（只有 Views）
│     │  ├─ enums/        环境、服务、角色、分支/CR/环境/部署/步骤状态
│     │  ├─ constants/    步骤定义、默认值、校验正则、HTTP/Git 常量
│     │  ├─ converter/    RecordMapper（MapStruct 表→record）、ViewConverter（record→视图）
│     │  ├─ common/       异常定义与全局异常处理
│     │  ├─ config/       登录拦截、CORS、Git 配置
│     │  ├─ integration/  git（GitLab / Mock）、k8s（RuntimeService）
│     │  └─ utils/        时间与 SHA 格式化
│     └─ resources/
│        ├─ application.yml
│        ├─ schema.sql    幂等建表
│        └─ data.sql      幂等基础数据（账号、项目；业务数据从空白开始）
└─ web/
   ├─ src/
   │  ├─ views/           Login / Requirements / Deploy / Approvals
   │  ├─ components/      需求弹窗、CR 弹窗、部署确认、进度、弹窗壳
   │  ├─ stores/          auth、approval
   │  ├─ api/client.js    axios + token + 401 处理
   │  ├─ router/
   │  ├─ layout/
   │  └─ styles.css       直接复用原型样式
   └─ vite.config.js      /api → localhost:8090
```

## 依赖

- Node.js 18+（当前机器已具备）
- JDK 17（`D:\develop\Java\jdk-17`）+ Maven 3.10.0（`D:\develop\apache-maven-3.10.0`），两者未加入系统 PATH
- 本机 `~/.m2/settings.xml` 已配阿里云 Maven 镜像（Maven Central 直连会挂起）
- MySQL 8.4：后端从 Windows 连 `localhost:3306`；WSL 内由 `mysql-nodeport-forward.service`（socat）转发到 k3s NodePort `30306`，见 `infra/wsl/mysql-nodeport-forward.service`
- 数据库 `platform`，账号 `platform / platform_pw`（`infra/mysql-init/01-init.sql` 已包含）

## 启动

### 1. 数据库

MySQL 跑在 k3s 里，Service 是 NodePort `30306`（只在 WSL 内可直接访问）。

Windows 上的后端要走 `localhost:3306`，由 WSL 里的 socat 服务桥接：

```bash
# 在 WSL 内执行（只需一次，之后随 systemd 自启）
cp /mnt/d/develop/project/engineering-platform/infra/wsl/mysql-nodeport-forward.service /etc/systemd/system/
systemctl daemon-reload && systemctl enable --now mysql-nodeport-forward.service
```

为什么不能直接连 `localhost:30306`：WSL2 的 localhost 转发只桥接真实监听端口，NodePort 是 kube-proxy 的 iptables 规则、WSL 内没有监听 socket，所以 Windows 侧连不上；`3306 → 30306` 的 socat 监听器解决了这个问题。

初始化库表（一般不需要，`01-init.sql` 已在 M0 阶段执行过）：

```bash
mysql -h 127.0.0.1 -P 3306 -uroot -proot_local_pw < infra/mysql-init/01-init.sql
```

建表和数据不用手工执行：后端启动时 `spring.sql.init.mode=always` 会依次执行 `schema.sql`、`data.sql`，两者都是幂等的（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE`）。以后改表就手工执行增量 SQL，项目不使用 Flyway。

### 2. 后端

```bash
export JAVA_HOME="D:\develop\Java\jdk-17"
export PATH="/d/develop/Java/jdk-17/bin:/d/develop/apache-maven-3.10.0/bin:$PATH"
cd platform/server
mvn spring-boot:run
```

默认端口 `8090`，数据库连接在 `src/main/resources/application.yml`。

要以 GitLab 真实流水线模式启动，额外注入两个环境变量（token 是 root 的 api scope PAT，不要写进配置文件）：

```bash
export PLATFORM_GIT_MODE=gitlab
export GITLAB_TOKEN=glpat-xxxxxxxx
export GITLAB_URL="http://[::1]:8080"   # 可选；Windows 的 IPv4:8080 被 Steam 占用
mvn spring-boot:run
```

`platform.git.gitlab.projects` 维护「平台项目 ID → GitLab project id」，当前 `app: 1`。

### 3. 前端

```bash
cd platform/web
npm install
npm run dev
```

打开 `http://localhost:5173`。Vite 把 `/api` 代理到 `http://localhost:8090`。

### 演示账号

| 账号 | 密码 | 角色 |
|---|---|---|
| `devuser` | `123456` | 开发 |
| `zhanggong` | `123456` | CR 管理员 |
| `ligong` | `123456` | CR 管理员 |

> 登录页也提供了「填入 CR 管理员账号」。

## 流水线执行（GitLab 模式）

仓库拆分（2026-11-14）：

- 后端 `root/app-api`、前端 `root/app-web`，由原 monorepo `root/app` 用 `git subtree split` 拆出，保留各自历史。
- 两个仓库都有 `main/dev/test/uat`；平台侧也拆成两个项目 `app-api`（service=api，映射 `GITLAB_PROJECT_APP_API`）与 `app-web`（service=web，映射 `GITLAB_PROJECT_APP_WEB`）。每条需求只属于一个项目、只操作一个仓库；「后端部署」只列 app-api，「前端部署」只列 app-web。
- 平台项目通过 `projects.runtime_app` 关联 k3s 运行时应用：app-api → `app-api-<env>`、app-web → `app-web-<env>`，两个应用独立命名空间，不共享 Pod。
- 需求分支 tip 存在 `requirement_branches(requirement_id, service, branch_name, tip_sha)`；SHA 门禁按项目对应 service 的 tip 判断。

平台与 GitLab 的契约：

- 合并：部署前平台先用 Merge Request API 把需求分支合入**对应仓库**的环境分支（`dev/test/uat`，prod 合入 `main`）；有冲突则环境状态置 `CONFLICT`、不触发流水线，MR 留在 GitLab 供解决。
- 触发：`POST /projects/{id}/pipeline`，`ref` 用该仓库的需求分支，变量带 `DEPLOY_ENV`、`DEPLOY_SERVICE`、`DEPLOY_SHA`、`REQ_NO`。
- CI 在每个 job 的 `before_script` 里 `git checkout "$DEPLOY_SHA"`，镜像 tag 也用该 SHA，保证「每一级部署同一份提交」。
- 两仓 CI 的 `workflow` 只接受带 `DEPLOY_ENV` 的流水线（平台触发）；合并到 dev/test/uat/main 不会再自动跑部署流水线，避免「平台流水线 + 分支流水线」并发重复部署。
- api 的 verify 用 `PF_PORT=$((18000 + CI_PIPELINE_ID % 2000))` 做 port-forward 并 `trap` 清理，并发 verify 不会互相抢 18080。
- 状态回写：平台每 2 秒轮询 pipeline 与 jobs，按 job 名映射到五个步骤——`build` → 编译 + 镜像构建，`deploy` → 部署到 k3s，`verify` → 健康检查；「拉取代码」在 runner 开始执行时置成功。
- 终止：`abort` 或轮询检测到 batch 已终止时调用 GitLab `POST /pipelines/{id}/cancel`，未完成步骤置失败。
- 超时：默认 30 分钟（`platform.pipeline.timeout-seconds`）。

构建与镜像（不依赖 Docker Hub）：

- 两个仓库各自一套 `.gitlab-ci.yml`：app-api 只构建/部署 api，app-web 只构建/部署 web；`DEPLOY_SERVICE` 仅作标记。两仓都已关闭 Auto DevOps，并复制了 `DB_*_PASSWORD` CI 变量。
- Runner 在宿主机执行 `mvn package`（JDK 17 + 阿里云 settings）或 `npm run build`，Docker 只负责把产物装进运行时镜像。
- 运行时基础镜像是本机用 debootstrap 预先构建的：`app/api-base:17-jre`（noble + openjdk-17-jre-headless）、`app/web-base:nginx`（noble + nginx-light）；`docker save | k3s ctr images import` 直接进 k3s，无需 registry。
- api 部署后 verify 用 `kubectl port-forward svc/api 18080:8080` 验 `/api/info`；web 验证 NodePort 首页。端口转发与 deploy 一样用 `build.env` 传递 NAMESPACE / NODEPORT。
- web 的 nginx 用模板 + 启动时注入 `NAMESPACE`，upstream 为 `api.<namespace>.svc.cluster.local`；api Service 不存在时 web 也能启动（首页正常，`/api` 返回 502），前后端只在 HTTP 契约上耦合。

相关配置：

```yaml
platform:
  pipeline:
    poll-interval-ms: 2000
    timeout-seconds: 1800
  git:
    mode: mock            # mock | gitlab
    gitlab:
      url: "http://[::1]:8080"
      token: "${GITLAB_TOKEN:}"
      default-ref: main
      projects:
        app-api:
          api: ${GITLAB_PROJECT_APP_API:5}
        app-web:
          web: ${GITLAB_PROJECT_APP_WEB:6}
```

## 运行时接入（Pod 扩缩容与排查）

`platform.runtime.mode` 决定 Pod 数据来源：

| 值 | 行为 |
|---|---|
| `mock` | 按 `env_states` 生成演示 Pod（每个需求固定一个），扩缩容只改内存，不需要 k3s |
| `k3s`（当前默认） | fabric8 Kubernetes Client 直连集群，真实读写 Deployment / Pod / 日志 / Events；滚动更新时能看到新旧两个 Pod 及 ContainerCreating / Terminating 状态 |

Windows 侧已把 WSL 的 kubeconfig 放到 `%USERPROFILE%\.kube\config`（server 为 `https://127.0.0.1:6443`，WSL2 localhost 转发可用）。fabric8 读取 k3s 的 EC 私钥依赖 BouncyCastle，`bcpkix-jdk18on` 已加入 `pom.xml`。

切换步骤：

1. `application.yml` 把 `platform.runtime.mode` 改成 `k3s`
2. 让进程读到 kubeconfig：设置环境变量 `KUBECONFIG`，或把 WSL 里的 `~/.kube/config` 复制到 `%USERPROFILE%\.kube\config`
3. k3s 的 API Server 默认只监听 `127.0.0.1:6443`。平台跑在 Windows 宿主时依赖 WSL2 的 localhost 转发；不通就把 kubeconfig 的 server 改成 `https://<WSL-IP>:6443`，并在 k3s 启动参数补 `--tls-san=<WSL-IP>`，否则证书校验会失败
4. 日志只取尾部 `platform.runtime.log-tail-lines` 行（默认 200）

命名约定：namespace = `<projects.runtime_app>-<env>`，Deployment 名 = 服务名（`api` / `web`），Pod 按标签 `app=<服务名>` 选择。

应用隔离（2026-11-14）：

- 每个平台项目一个独立运行时命名空间：`app-api-<env>`、`app-web-<env>`；不同应用的同名 Service / Deployment 互不重叠，Pod 面板只按当前项目的命名空间查询。
- web 与 api 跨命名空间通信：web 的 nginx upstream 为 `api.<API_NAMESPACE>.svc.cluster.local:8080`，`API_NAMESPACE` 由 CI 渲染进 web.yaml（`app-api-<env>`）。
- 旧的一体化 `app-<env>` 命名空间已删除；删除前 30081~30083 能打开页面，是因为旧 api/web Pod 还在，并不属于 app-api / app-web。
- NodePort 仍按环境分配（30081~30084），同一环境只允许一个 web 应用占用；新增 web 应用需要另分配端口或改 Ingress，这是当前多应用支持的限制。
- 部署页不因某个环境在部署而锁定环境/项目切换：多人可分别在不同环境并行操作；当前环境有 RUNNING 时只显示操作人并禁止该环境重复提交，锁按 `project + service + env` 建立。

平台将来部署进集群时，用独立 ServiceAccount 收窄权限（按环境各建一份）：

```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: platform-ops
  namespace: app-api-dev
rules:
  - apiGroups: [""]
    resources: ["pods", "pods/log", "events"]
    verbs: ["get", "list", "watch", "delete"]
  - apiGroups: ["apps"]
    resources: ["deployments", "deployments/scale"]
    verbs: ["get", "list", "watch", "patch", "update"]
```

开发期在宿主用 kubeconfig，那是集群管理员身份，只适合本地，不要带到生产。

运行约束：

- 扩容 / 缩容 / 重启 Pod 仅 `CR_ADMIN` 可操作，`DEV` 会返回 403。
- 副本数 1~10；`prod` 下限为 2，其他环境为 1。
- 扩缩容直接 patch Deployment，不写部署记录、不触发流水线；由 k3s 负责增删 Pod。
- 若给 Deployment 配了 HPA，手动副本数会被 HPA 覆盖，MVP 不启用 HPA。
- CPU / 内存曲线需要集群另装 `metrics-server`，当前只提供状态、重启次数、存活时长和事件。

## 关键规则

- 需求可见性：`owner = 当前用户` 或 `requirement_related` 包含当前用户。
- 分支命名：`yyyyMMdd-后缀`，例如 `20261114-bugfix-ledger`；也可绑定已有分支。
- 环境顺序：`dev → test → uat → prod`，上一环境成功且 SHA 与当前分支最新提交一致才放行。
- test 首次部署：必须有 `APPROVED` 的 CR；提交人不能审批自己的 CR（审批接口只允许 `CR_ADMIN`）。
- 同一 project + service + env 同时只能有一个部署（`deploy_locks` 唯一键）。
- 部署终止：当前步骤置失败、当前部署 `CANCELLED`、环境回到 `READY`、释放锁。
- 合并冲突：环境状态 `CONFLICT`，点「已解决」后置 `READY`，再走正常确认部署。
- 生产回滚：以 `prod_releases.previous_sha` 重新跑五步流水线，成功后当前/上一版本互换。
- Pod / 日志 / YAML / 事件 / 扩缩容：`platform.runtime.mode=mock` 时是演示数据，`k3s` 时直连集群；扩缩容与重启仅管理员可操作。

## API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/login` | 登录，返回 token + user |
| POST | `/api/auth/logout` | 退出 |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/auth/approvers` | CR 管理员列表 |
| GET | `/api/projects` | 项目列表 |
| GET | `/api/requirements?projectId=app` | 我可见的需求 |
| GET | `/api/requirements/{reqNo}` | 需求详情 |
| POST | `/api/requirements` | 新建需求 |
| POST | `/api/requirements/{reqNo}/branch` | 创建分支（body: `suffix`） |
| POST | `/api/requirements/{reqNo}/branch/bind` | 绑定已有分支（body: `branch`） |
| POST | `/api/requirements/{reqNo}/cr` | 指定 CR（body: `service` / `approver` / `comment`） |
| GET | `/api/deploy/board?projectId&service&env` | 环境板 |
| POST | `/api/deploy/start` | 批量部署（body: `projectId` / `service` / `env` / `ids`） |
| POST | `/api/deploy/{batchId}/abort` | 终止部署 |
| POST | `/api/deploy/conflict/resolve` | 标记冲突已解决 |
| POST | `/api/deploy/rollback` | 回滚生产版本 |
| GET | `/api/deploy/pods/logs?name=&projectId&service&env` | Pod 日志（尾部 N 行） |
| GET | `/api/deploy/pods/yaml?name=&projectId&service&env` | Pod YAML |
| GET | `/api/deploy/pods/events?name=&projectId&service&env` | Pod Events，排查启动失败 |
| GET | `/api/deploy/scale?projectId&service&env` | 当前副本数与上下限 |
| POST | `/api/deploy/scale` | 调整副本数（仅 CR_ADMIN，body: `projectId` / `service` / `env` / `replicas`） |
| POST | `/api/deploy/pods/restart` | 重启 Pod（仅 CR_ADMIN，body: `projectId` / `service` / `env` / `name`） |
| GET | `/api/approvals` | 待审批列表 |
| POST | `/api/approvals/{crId}/approve` | 通过并自动部署 test |
| POST | `/api/approvals/{crId}/reject` | 驳回 |

除 `/api/auth/login` 外都需要 `Authorization: Bearer <token>`。

## 已验证 / 未验证

已验证：

- `npm install`、`npm run build` 通过（Vite 5.4.21，构建产物约 1.05 MB JS / 354 KB CSS）。
- 用临时 mock API 在真实浏览器里走通登录、需求列表、后端部署 dev/test/prod、审批待办和需求详情弹窗，控制台无报错。
- `mvn -DskipTests compile` 通过：Java 17.0.12 + Maven 3.10.0，40 个源文件，BUILD SUCCESS。
- fabric8 6.13.4 使用的 `v1().events()`、`getLog()`、`scale(int)`、`Serialization.asYaml`、`KubernetesClient.close()` 已对照该版本源码逐个确认存在。
- **已连真实 MySQL 启动并回归**（2026-11-14）：`schema.sql`（13 张表）与 `data.sql`（3 个账号 + 2 个项目）执行成功；登录、`/api/projects`、`/api/requirements`、`/api/deploy/board`、`/api/approvals`、`/api/auth/approvers` 均 200。
- **验收前清库**（2026-11-14）：需求、分支、CR、环境状态、部署批次/步骤、流水线映射、发布记录全部清空；`data.sql` 已去掉演示需求，重启不会回灌；GitLab 测试分支已删除，只保留 dev/test/uat/main。
- 首跑修掉两个真实 bug：`prod_releases` 的 `current_time` 是 MySQL 保留函数名（列名改为 `released_at` / `prev_released_at`）；`relatedDisplayNames` 漏传 `projectId` 参数。
- **分层改造回归**（2026-11-14）：删除 `repository` 层、Service 直连 MyBatis-Plus Mapper；编译通过后重启，登录、`/api/projects`、`/api/requirements`、`/api/deploy/board`、`/api/approvals`、`/api/auth/approvers` 均 200。首跑修掉 `insertDeployment` 引用不存在参数 `#{now}` 的写路径 bug。
- **GitLab 模式联调**（2026-11-14）：平台创建需求分支（真实 GitLab 分支，tip SHA 写回需求）、触发 pipeline、按 job 状态回写五个步骤、取消部署时联动 `cancel` API，均已跑通。
- **真实构建 + k3s 部署跑通**（2026-11-14）：Runner 在宿主机用 Maven 3.8.7（JDK 17）与 Node 18/npm 构建，镜像基于本机 debootstrap 生成的 `app/api-base:17-jre`、`app/web-base:nginx`，`docker save | k3s ctr images import` 后部署到 `app-dev`；api/web 两个 Pod 1/1 Running，NodePort `30081` 的 `/api/info` 返回 `"db":true`，首页 200。
- **平台端到端验收**（2026-11-14）：平台新建需求 REQ-001 → 真实 GitLab 分支 `20261114-acceptance-smoke` → 部署 dev，五个步骤随 GitLab job 依次成功，Pod 镜像 tag 与需求 SHA 一致；冒烟数据已清理，分支已删除。
- **前后端双仓拆分验证**（2026-11-14）：`root/app-api`（8f877e6）与 `root/app-web`（e0c6572）由 subtree split 拆出；临时在平台单项目 `app` 下建需求分支 `20261114-split-test`，两仓同名创建、tip 分别为 8f877e6 / e0c6572（`requirement_branches` 两行）；api、web 分别经平台部署 dev，镜像 `app/api:20261114-split-test-8f877e6`、`app/web:20261114-split-test-e0c6572`，30081 首页 200、`/api/info` 返回 api 的 version 与 `db:true`。
- **平台项目拆分验证**（2026-11-14）：平台项目拆为 `app-api` / `app-web`（`projects.service` + `projects.runtime_app`），旧 `app` 项目与历史业务数据清空；API 实测 app-api 建分支 `20261114-split-api-check` 只落在 `root/app-api`（tip 8f877e6），app-web 建分支 `20261114-split-web-check` 只落在 `root/app-web`（tip e0c6572），`requirement_branches` 各一行；项目与 service 不匹配时返回 400。
- **运行时隔离验证**（2026-11-14）：旧 `app-*` 命名空间已删除；一条测试需求（`20261114-ceshi`）在 app-api/app-web 分别部署 dev，落到 `app-api-dev` / `app-web-dev`，api tip `f4303fd`、web tip `b131dcd`；web 首页 200，跨命名空间 `/api/info` 返回 `version=20261114-ceshi-f4303fd`、`db:true`；test/uat 无资源，30082/30083 拒绝连接；部署页在 uat RUNNING 时锁定环境/项目切换并显示操作人（临时批次已删）。

未验证：

- test/uat/prod 三环境的双仓独立部署未跑；CR 审批后的自动部署与按 tip 的 SHA 门禁只做了 dev 验证。
- 合并冲突链路在双仓模式下未重新演练（单仓版已验收：CONFLICT、已解决后重试）。
- 建分支失败补偿（GitLab 建分支成功后数据库写入失败时删除刚建的分支）代码已实现，未做故障注入验证。
- 新建 GitLab 项目默认开 Auto DevOps，会抢占 Runner 生成 code_quality 卡死；两仓已关闭（`auto_devops_enabled=false`），新建项目时需注意。

## GitLab Runner（WSL 内，shell executor）

Runner 以 systemd 服务运行在 WSL 里（不在 Docker 里），版本 16.10.1：

```bash
# 安装源：腾讯镜像的 gitlab-runner apt 仓库（国内可达；华为镜像只有页面没有包文件）
BASE=https://mirrors.cloud.tencent.com/gitlab-runner/ubuntu
curl -s -o /tmp/packages.gz "$BASE/dists/noble/main/binary-amd64/Packages.gz"
file=$(gzip -dc /tmp/packages.gz | awk '/^Package: gitlab-runner$/{f=1} f&&/^Filename:/{print $2; exit}')
curl -L -o /tmp/gr.deb "$BASE/$file" && sudo dpkg -i /tmp/gr.deb

# 以 root 运行（shell executor 需要直接调 k3s / docker）
sudo gitlab-runner uninstall
sudo gitlab-runner install --user root --working-directory /root/gitlab-runner
sudo gitlab-runner start
```

注册用 `glrt-` 认证令牌，**只允许 name/executor/url/token**（tag、run-untagged 在 GitLab 服务端配置）：

```toml
# /etc/gitlab-runner/config.toml
concurrent = 2
[[runners]]
  name = "wsl-shell"
  url = "http://localhost:8080"
  token = "glrt-xxxx"
  executor = "shell"
```

> 不要留空的 `[runners.shell]` 段（16.10 会报 TOML 类型错误）。`gitlab-runner restart` 后用 `/api/v4/runners/all` 确认 `"status":"online"`。

## 下一步

1. 把 test → uat → prod 的真实部署也跑一遍（含 CR 审批自动部署与 prod 回滚）。
2. 把 `platform.runtime.mode` 切到 `k3s`，先验证只读的 Pod / 日志 / 事件，再试扩缩容。
3. 前端第二批 Element Plus 改造：RequirementsView、DeployView 与四个业务弹窗。
4. 前端补充扩容弹窗与 Events 面板（后端接口已就绪，页面目前只用到日志和 YAML）。
