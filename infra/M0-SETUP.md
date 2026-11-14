# M0 本地环境搭建（全在 WSL2 + k3s）

目标：本机跑通 GitLab → Runner → k3s，四环境部署为 Pod。全程不装 Docker Desktop。

```
Windows 11
└─ WSL2 Ubuntu 24.04（虚拟磁盘在 D:\wsl\k3s）
   ├─ Docker Engine（apt 安装）
   │  └─ gitlab 容器 :8080（命名卷存数据）
   ├─ gitlab-runner（shell executor，tag=local）
   └─ k3s server（systemd 常驻，自带 kubectl）
      ├─ ns shared     MySQL StatefulSet + PVC，NodePort 30306
      ├─ ns app-dev    api(ClusterIP) + web(NodePort 30081)
      ├─ ns app-test   web 30082
      ├─ ns app-uat    web 30083
      └─ ns app-prod   web 30084
浏览器：localhost:8080 / 30081~30084
```

为什么全放 WSL2：同一台 Linux 主机上，GitLab、Docker、k3s、runner 之间全是 `localhost`，没有跨 Windows/WSL 的网络问题；也不用 Docker Desktop 许可。代价是要手动装 Docker Engine（下面三条命令）。

约定：PowerShell 命令在 Windows 执行；`bash` 命令在 WSL 的 `k3s` 发行版里执行（默认用户即可，需要时加 `sudo`）。

## 0. 前置

- Windows 11，D 盘预留 40GB+
- 本机没有 Java/Maven/Docker，后端构建由 Docker 完成
- 先 `wsl --update`

## 1. 新建 D 盘 WSL 发行版

```powershell
wsl --install -d Ubuntu-24.04 --name k3s --location D:\wsl\k3s
```

首次进入按提示创建 Linux 用户。之后进入：

```bash
sudo apt-get update && sudo apt-get install -y curl git
sudo tee /etc/wsl.conf >/dev/null <<'EOF'
[boot]
systemd=true
EOF
```

回 PowerShell：

```powershell
wsl --shutdown
wsl -d k3s
```

验证：`systemctl is-system-running`（`running` 或 `degraded` 均可）。

`C:\Users\<用户名>\.wslconfig`：

```ini
[wsl2]
memory=20GB
processors=8
swap=8GB
```

改完 `wsl --shutdown`。

> 所有数据（Docker 镜像、GitLab 卷、MySQL 数据）都在这个发行版的虚拟磁盘里，物理位置 `D:\wsl\k3s`，不占 C 盘。

## 2. Docker Engine（不装 Docker Desktop）

```bash
sudo apt-get install -y docker.io
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
```

退出重进 WSL（或 `newgrp docker`），验证：

```bash
docker version
docker run --rm hello-world
```

拉镜像慢就加加速器（`/etc/docker/daemon.json`）：

```json
{ "registry-mirrors": ["<你可用的加速地址>"] }
```

```bash
sudo systemctl restart docker
```

## 3. GitLab 容器

```bash
docker pull gitlab/gitlab-ce:latest
docker images --digests gitlab/gitlab-ce   # 记下 digest，用 digest 固定版本
```

用命名卷存数据，避免权限问题：

```bash
docker run -d --name gitlab --restart unless-stopped --shm-size 256m \
  -p 8080:80 \
  -v gitlab-config:/etc/gitlab \
  -v gitlab-logs:/var/log/gitlab \
  -v gitlab-data:/var/opt/gitlab \
  -e GITLAB_OMNIBUS_CONFIG="external_url 'http://localhost:8080'; puma['worker_processes'] = 2; sidekiq['concurrency'] = 5" \
  gitlab/gitlab-ce@sha256:<digest>
```

首次启动 3~6 分钟，取初始密码：

```bash
docker exec gitlab cat /etc/gitlab/initial_root_password
```

浏览器开 `http://localhost:8080`（WSL2 会自动把端口映射到 Windows 的 localhost），root 登录后立即改密码。

## 4. 安装 k3s

```bash
curl -sfL https://get.k3s.io | INSTALL_K3S_MIRROR=cn INSTALL_K3S_EXEC="server --disable traefik --write-kubeconfig-mode 644" sh -

sudo systemctl enable --now k3s
sudo systemctl status k3s --no-pager

mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown "$USER:$USER" ~/.kube/config

kubectl get nodes -o wide
```

单节点 `Ready` 即可。k3s 自带 containerd 和 kubectl，与 Docker 共存没问题。

## 5. 命名空间与 MySQL

```bash
cd /mnt/d/develop/engineering-platform

kubectl apply -f infra/k8s/00-namespaces.yaml

kubectl -n shared create configmap mysql-init \
  --from-file=01-init.sql=infra/mysql-init/01-init.sql

kubectl -n shared create secret generic mysql-root \
  --from-literal=password=root_local_pw

kubectl apply -f infra/k8s/mysql.yaml
kubectl -n shared rollout status statefulset/mysql --timeout=180s
```

验证五个库：

```bash
kubectl -n shared exec statefulset/mysql -- \
  mysql -uroot -proot_local_pw -e "show databases;"
```

> 初始化脚本只在数据目录为空时执行，重置需删 PVC（会丢数据）。

## 6. GitLab Runner（shell executor）

```bash
mkdir -p ~/bin
curl -L --retry 3 -o ~/bin/gitlab-runner \
  https://gitlab-runner-downloads.s3.amazonaws.com/latest/binaries/gitlab-runner-linux-amd64
chmod +x ~/bin/gitlab-runner

sudo ~/bin/gitlab-runner install --user "$USER" --working-directory "/home/$USER/gitlab-runner"
sudo systemctl enable --now gitlab-runner
```

GitLab 页面：Admin → CI/CD → Runners → New instance runner → 平台 Linux → 复制 token：

```bash
~/bin/gitlab-runner register --non-interactive \
  --url "http://localhost:8080" \
  --token "<TOKEN>" \
  --executor "shell" \
  --tag-list "local"

sudo systemctl restart gitlab-runner
```

给 CI 里的 `sudo k3s` 开免密（部署阶段导入镜像要用）：

```bash
echo "$USER ALL=(root) NOPASSWD: /usr/local/bin/k3s" | sudo tee /etc/sudoers.d/gitlab-runner-k3s
sudo chmod 440 /etc/sudoers.d/gitlab-runner-k3s
sudo -n k3s ctr version >/dev/null && echo "sudo ok"
```

## 7. 仓库、分支、变量

1. GitLab 建 group `dev`，组内建空项目 `app`
2. 推送：

```powershell
cd D:\develop\engineering-platform\app
git init -b main
git add .
git commit -m "init"
git remote add origin http://localhost:8080/dev/app.git
git push -u origin main
```

3. 建分支 `dev`、`test`、`uat`
4. Settings → Repository → Protected branches：`main`、`dev`、`test`、`uat` 全部设
   - Allowed to merge: Maintainers
   - Allowed to push: No one
5. 建用户 `platform-bot`，加入 `dev` 组，Maintainer（平台后续合并用）
6. Settings → CI/CD → Variables（Masked + Protected）：
   - `DB_DEV_PASSWORD` = `app_dev_pw`
   - `DB_TEST_PASSWORD` = `app_test_pw`
   - `DB_UAT_PASSWORD` = `app_uat_pw`
   - `DB_PROD_PASSWORD` = `app_prod_pw`

## 8. 四环境验证

1. 建 `feature/REQ-001`，把 `web/src/App.vue` 的 `<h1>App</h1>` 改成新标题，提交推送
2. GitLab 建 MR → `dev`，合并
3. 流水线依次执行 build:api → build:web → deploy → verify
4. 观察 Pod：

```bash
kubectl -n app-dev get pods -o wide
kubectl -n app-dev get deploy,svc
```

5. 浏览器打开 `http://localhost:30081`：新标题、环境=dev、数据库=连接正常
6. 同样方式走 `test`、`uat`、`main`，确认 30082 / 30083 / 30084
7. 故障演练：`kubectl -n app-dev scale deploy/api --replicas=0`，再合一次到 dev → verify 红；恢复 `--replicas=1` 后重跑

## 9. k3s 练习清单

```bash
kubectl get nodes -o wide
kubectl -n app-dev get pods -o wide
kubectl -n app-dev describe pod <pod>
kubectl -n app-dev logs deploy/api --tail=50
kubectl -n app-dev exec -it deploy/api -- sh
kubectl -n app-dev scale deploy/web --replicas=3
kubectl -n app-dev delete pod <pod>            # 观察自愈
kubectl -n app-dev rollout history deploy/web
kubectl -n app-dev rollout undo deploy/web
kubectl -n app-dev port-forward svc/api 8081:8080
kubectl -n app-dev get endpoints
kubectl get ns
```

## 10. 故障排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `kubectl` 连不上 | k3s 未启动 / kubeconfig 权限 | `systemctl status k3s`、`journalctl -u k3s -n 100` |
| Pod `ImagePullBackOff` | 镜像没导入 k3s | 部署脚本里 `docker save ... \| sudo k3s ctr images import -` |
| `sudo: a password is required` | 免密未配 | 检查 `/etc/sudoers.d/gitlab-runner-k3s` |
| Windows 打不开 localhost:8080 | WSL2 端口转发异常 | WSL 里 `curl localhost:8080` 先自测；必要时 `netsh interface portproxy` |
| 30081 打不开 | Pod 未就绪 / Service 缺 endpoints | `kubectl -n app-dev get pods,svc,endpoints` |
| web 502 | api 未 ready（数据库没通） | `kubectl -n app-dev logs deploy/api` |
| k3s 起不来且日志提 CNI/iptables | 与 Docker 网络规则冲突 | `journalctl -u k3s -n 200`，必要时重启 k3s |
| MySQL 初始化没执行 | 数据目录已存在 | 删 PVC 后重建（会丢数据） |
| GitLab 502 | 还在启动 | 等 3~6 分钟，`docker logs -f gitlab` |
| token / 证书错误 | WSL2 睡眠后时钟漂移 | `wsl --shutdown`，重新进入 |
| D 盘变大 | 镜像/卷累积 | `docker system df`、`sudo k3s ctr images ls` |

## 11. 收尾

- 启动脚本：`infra/start-all.ps1`（起 docker、gitlab、k3s，打印 nodes/pods）
- 演示前确认时钟正常，避免 GitLab 鉴权失败
- M0 通过后进入 M1：平台骨架（登录 / 项目 / 需求 / 建分支）
