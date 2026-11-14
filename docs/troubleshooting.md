# 常见问题

## 平台与模式

### 五步流水线 1.5 秒跑完，但 GitLab 没有新 pipeline

**原因**：后端没读到 `PLATFORM_GIT_MODE=gitlab`，回退到了 mock。

**处理**：启动前显式设置环境变量，确认进程继承到了：

```bash
export PLATFORM_GIT_MODE=gitlab
export GITLAB_TOKEN=<PAT>
export GITLAB_URL='http://[::1]:8080'
```

mock 模式只适合前端演示，不会建真实分支，也不会触发 CI。

### prod 部署成功了，但「生产发布版本」还是空的

**原因**：这次成功发生在发布历史功能上线之前，或者跑的是 mock 模式。

**处理**：用 GitLab 模式对同一需求在 prod 重新部署一次；成功后会自动写历史并归档。旧数据不会自动回填。

### 归档后需求从所有部署列表消失了

这是预期行为。prod 成功后需求归档，不能再普通部署；需要恢复线上版本时，在 prod 页选择历史版本回滚。

## GitLab 与 Runner

### `http://localhost:8080` 打不开或返回奇怪页面

Windows 的 IPv4 `:8080` 可能被 Steam 占用。改用 IPv6：

```text
http://[::1]:8080
```

平台默认的 `GITLAB_URL` 也是这个地址。

### Runner 一直 offline / pipeline 一直 pending

```bash
sudo systemctl status gitlab-runner --no-pager
sudo gitlab-runner verify
```

常见原因：

- `config.toml` 里留了空的 `[runners.shell]` 段（16.10 会报 TOML 类型错误）。
- Runner 的 tag 与 CI 的 `tags` 不匹配（本项目 CI 用 `local`）。
- `concurrent` 被其他卡住的 job 占满。

### 新建 GitLab 项目后 pipeline 卡在 code_quality

新建项目默认开启 Auto DevOps，会生成大量额外 job 抢占 Runner。

```bash
curl -X PUT -H "PRIVATE-TOKEN: <PAT>" \
  "http://[::1]:8080/api/v4/projects/<id>?auto_devops_enabled=false"
```

取消卡住的 pipeline/job，再重启 Runner。

### 合并后流水线报 `couldn't find remote ref`

平台合并需求分支时，GitLab 项目默认 `remove_source_branch_after_merge=true`，把源分支删了。

- 建 MR 时传 `remove_source_branch: false`
- 合并时传 `should_remove_source_branch: false`
- 已删除的分支可以用原 SHA 通过 branches API 恢复

### verify 失败：`bind: address already in use`

通常是两条流水线并发：

- 一条平台触发，一条合并提交自动触发。检查 GitLab pipeline 的来源。
- 两仓 CI 的 `workflow` 必须只接受带 `DEPLOY_ENV` 的流水线。
- api 的 verify 使用唯一端口：

```bash
PF_PORT=$((18000 + CI_PIPELINE_ID % 2000))
```

### 镜像拉取失败 / ImagePullBackOff

本项目不依赖镜像仓库。Runner 构建后执行：

```bash
docker save "app/api:${TAG}" | sudo k3s ctr images import -
```

Deployment 使用 `imagePullPolicy: IfNotPresent`。基础镜像 `app/api-base:17-jre`、`app/web-base:nginx` 要预先用 `docker save | k3s ctr images import` 导入。

## 网络与数据库

### Windows 连不上 MySQL 或 NodePort

WSL2 的 localhost 转发只桥接真实监听进程；NodePort 是 iptables 规则，没有监听 socket。

检查 WSL 里的 socat 服务：

```bash
systemctl status mysql-nodeport-forward.service
systemctl status web-nodeport-forward.service   # 端口按实际单元名
```

单元文件在 `infra/wsl/`，监听 `0.0.0.0:3306`、`30081~30084`，转发到 WSL 内的 NodePort。

### 平台启动时报 `JcaPEMKeyConverter is provided by BouncyCastle`

fabric8 读 k3s 的 EC 私钥需要 BouncyCastle。确认 `pom.xml` 有：

```xml
<dependency>
  <groupId>org.bouncycastle</groupId>
  <artifactId>bcpkix-jdk18on</artifactId>
  <version>1.78.1</version>
</dependency>
```

### Windows 连不上 k3s API

- kubeconfig 的 server 用 `https://127.0.0.1:6443`，依赖 WSL2 localhost 转发。
- 不通就把 server 改成 WSL IP，并给 k3s 加 `--tls-san=<WSL-IP>`。
- kubeconfig 放在 `%USERPROFILE%\.kube\config`。

## 数据库与启动

### 重启后 schema 初始化失败

`spring.sql.init.mode=always` 每次启动都会执行 `schema.sql`，且 `continue-on-error=false`。

- 只能写 `CREATE TABLE IF NOT EXISTS`。
- 不要给已存在的表加列；新增状态放新表。
- 结构性变更用一次性增量 SQL 手工执行。

### 平台重启后页面一直显示「部署中」

正常不会。启动时 `reconcileAfterRestart` 会把 RUNNING 的批次/部署/步骤清理成终止态并释放锁。若仍残留，检查：

```sql
SELECT * FROM deploy_batches WHERE status = 'RUNNING';
SELECT * FROM deploy_locks;
```

并确认后端确实完成了启动事件。

## 前端

### 改了页面但浏览器还是旧内容

- Vite 开发服务器是否还在运行。
- 强制刷新（`Ctrl+F5`）。
- 后端改动不会热更新，必须手动重启 Java 进程。

### Pod 面板没有数据 / 显示的是演示 Pod

检查 `platform.runtime.mode`：

- `mock`：演示数据，不会查询 k3s。
- `k3s`：需要可用的 kubeconfig 和集群权限。

## 已知限制

- MySQL 单实例、单点故障。
- Runner 以 root 运行，平台用管理员 kubeconfig，只适合本地学习。
- NodePort 按环境固定 30081~30084，同一环境只能有一个 web 应用占用。
- 归档需求没有「取消归档」入口；需要恢复时用回滚。
