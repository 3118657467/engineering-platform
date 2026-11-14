param()

$ErrorActionPreference = "Stop"

function Invoke-Wsl([string]$Command) {
    wsl -d k3s -c $Command
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[warn] 命令失败: $Command" -ForegroundColor Yellow
    }
}

Write-Host "启动 Docker 与 k3s ..."
Invoke-Wsl "sudo systemctl start docker k3s"

Write-Host "启动 GitLab 容器 ..."
Invoke-Wsl "docker start gitlab || docker run -d --name gitlab --restart unless-stopped --shm-size 256m -p 8080:80 -v gitlab-config:/etc/gitlab -v gitlab-logs:/var/log/gitlab -v gitlab-data:/var/opt/gitlab -e GITLAB_OMNIBUS_CONFIG=`"external_url 'http://localhost:8080'; puma['worker_processes'] = 2; sidekiq['concurrency'] = 5`" gitlab/gitlab-ce:latest"

Write-Host ""
Write-Host "=== k3s 节点 ==="
Invoke-Wsl "kubectl get nodes"
Write-Host ""
Write-Host "=== Pod ==="
Invoke-Wsl "kubectl get pods -A"
Write-Host ""
Write-Host "GitLab : http://localhost:8080"
Write-Host "应用   : http://localhost:30081 (dev) / 30082 (test) / 30083 (uat) / 30084 (prod)"
Write-Host "MySQL  : localhost:30306"
