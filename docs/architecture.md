# 架构说明

## 总览

平台是「控制面」，GitLab/Runner/k3s 是「执行面」：

```mermaid
flowchart LR
  subgraph Windows
    B["浏览器"]
    FE["平台前端<br/>Vue 3 :5173"]
    BE["平台后端<br/>Spring Boot :8090"]
  end

  subgraph WSL2["WSL2 Ubuntu 24.04"]
    GL["GitLab CE<br/>Docker :8080"]
    RUN["GitLab Runner<br/>systemd · shell"]
    K3S["k3s<br/>systemd · containerd"]
    DB[("MySQL shared<br/>NodePort 30306")]
  end

  B --> FE -->|"/api"| BE
  BE -->|"REST：分支 / MR / pipeline"| GL
  GL -->|"分配 Job"| RUN
  RUN -->|"docker build / save / kubectl apply"| K3S
  BE -->|"fabric8：Pod / 日志 / 扩缩容"| K3S
  BE -->|"platform 库"| DB
  K3S -->|"app_ENV 库"| DB
```

## 组件职责

| 组件 | 职责 | 不负责 |
|---|---|---|
| 平台前端 | 需求、部署、审批、Pod 面板 | 不直接调 GitLab / k8s |
| 平台后端 | 规则、门禁、编排、状态落库、运行时代理 | 不构建业务代码 |
| GitLab | 仓库、分支、MR、pipeline 定义 | 不主动推状态给平台 |
| GitLab Runner | 拉代码、构建、镜像导入、`kubectl apply` | 不决定部署顺序 |
| k3s | 运行 Pod、滚动更新、扩缩容、自愈 | 不感知需求和环境门禁 |
| MySQL | 平台业务数据 + 各环境应用库 | 不做业务逻辑 |

平台不接收 GitLab webhook，而是**主动轮询** pipeline 和 jobs。Runner 也是向 GitLab 长轮询领取任务，GitLab 不会反向连 Runner。

## 一次部署的时序

```mermaid
sequenceDiagram
  actor U as 用户
  participant FE as 平台前端
  participant API as 平台后端
  participant GL as GitLab
  participant R as Runner
  participant K as k3s
  participant DB as MySQL

  U->>FE: 选择需求，点击部署
  FE->>API: POST /api/deploy/start
  API->>DB: 创建 batch / deployment / steps / lock
  API-->>FE: batchId

  loop 每个需求
    API->>GL: 合并需求分支到环境分支（MR API）
    alt 合并冲突
      API->>DB: env=CONFLICT，batch=FAILED
    else 合并成功
      API->>GL: 触发 pipeline（DEPLOY_ENV / DEPLOY_SHA）
      GL->>R: 分配 Job
      R->>K: 构建镜像 → save/import → kubectl apply
      loop 每 2 秒
        API->>GL: 查询 pipeline + jobs
        API->>DB: 回写五步状态
      end
      alt 成功
        API->>DB: env=SUCCESS
        opt prod
          API->>DB: 发布历史 + 归档需求 + 当前版本
        end
      else 失败 / 终止
        API->>DB: env=READY 或 FAILED，释放锁
      end
    end
  end
```

## 双仓库与双应用

前后端是两个独立 GitLab 仓库，也是两个独立运行时应用：

| 平台项目 | 服务 | GitLab | 命名空间 | web 端口 |
|---|---|---|---|---|
| `app-api` | `api` | `root/app-api`（id 5） | `app-api-<env>` | — |
| `app-web` | `web` | `root/app-web`（id 6） | `app-web-<env>` | 30081~30084 |

- 每个仓库都有 `main / dev / test / uat`，需求分支同名创建。
- 分支 tip 存在 `requirement_branches(requirement_id, service, branch_name, tip_sha)`，SHA 门禁按 service 比较。
- web 的 nginx upstream 是 `api.<API_NAMESPACE>.svc.cluster.local:8080`，跨命名空间访问 api；api 不存在时 web 仍能启动，只是 `/api` 返回 502。

## 网络与端口

```mermaid
flowchart LR
  W["Windows"] -->|"localhost:5173"| FE["平台前端"]
  W -->|"localhost:8090"| BE["平台后端"]
  W -->|"http://[::1]:8080"| GL["GitLab"]
  W -->|"localhost:3306"| SOCAT1["socat 3306→30306"]
  W -->|"localhost:30081~30084"| SOCAT2["socat → 应用 NodePort"]
  SOCAT1 --> MYSQL["MySQL Service"]
  SOCAT2 --> WEB["web Service"]
```

为什么需要 socat：

- WSL2 的 `localhost` 转发只桥接**真实监听进程**。
- NodePort 由 kube-proxy 的 iptables 规则实现，WSL 内没有监听 socket，Windows 直接连 `localhost:30306/30081` 会失败。
- WSL 内起 systemd socat 监听，Windows 才能访问。单元文件在 `infra/wsl/`。

GitLab 的 IPv4 `:8080` 可能被 Steam 的调试端口占用，所以平台默认使用 `http://[::1]:8080`。

## 镜像交付

不使用镜像仓库：

1. Runner 在宿主机 `docker build`。
2. `docker save app/api:<tag> | sudo k3s ctr images import -`。
3. Deployment 使用 `imagePullPolicy: IfNotPresent`。

Docker Engine 与 k3s 使用两套镜像存储（Docker 的 overlay2 vs k3s 的 containerd），所以必须显式搬运。基础镜像（`app/api-base:17-jre`、`app/web-base:nginx`）用 debootstrap 预构建，避免依赖 Docker Hub。

## 模式开关

| 配置 | 值 | 影响 |
|---|---|---|
| `platform.git.mode` | `mock` | 平台自己推进五步，不访问 GitLab |
| | `gitlab` | 真实建分支 / MR / pipeline / cancel |
| `platform.runtime.mode` | `mock` | Pod 面板是演示数据 |
| | `k3s` | fabric8 读写真实集群 |

两组开关独立。真实链路是 `gitlab + k3s`；只做页面演示可以用 `mock + mock`。
