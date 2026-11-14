# 工程平台

需求 → 开发分支 → dev / test / uat / prod 逐级部署的本地工程平台。包含需求管理、CR 审批、SHA 一致门禁、GitLab 流水线对接与 k3s 运行时管理。

## 目录

```
engineering-platform/
├─ platform/          工程平台本体
│  ├─ server/         Spring Boot 3.3.5 + Java 17 + MyBatis-Plus + MySQL 8
│  ├─ web/            Vue 3 + Vite + Element Plus + Pinia
│  ├─ prototype/      已确认的静态原型
│  └─ docs/           架构图、新仓库 CI 模板
└─ infra/             本地基础设施说明与清单（k3s / MySQL / 启动脚本）
```

## 能力概览

- 需求与分支：需求只属于一个应用；从 `main` 创建日期前缀分支或绑定已有分支
- 应用隔离：平台项目 `app-api` / `app-web` 分别对应独立 GitLab 仓库与独立 k3s 命名空间 `app-api-<env>` / `app-web-<env>`
- 环境隔离：dev / test / uat / prod 独立命名空间、独立部署记录；部署进行中锁定环境与项目切换
- 部署门禁：按环境逐级解锁，上一环境 SHA 与当前分支 tip 一致才可继续；test 首次部署需 CR 审批
- 双执行器：`platform.git.mode=mock` 演示模式 / `gitlab` 真实流水线模式
- 运行时双实现：mock Pod 数据 / k3s 真实 Pod、日志、YAML、Events、扩缩容、重启
- 生产回滚与合并冲突处理

架构图见 [`platform/docs/architecture.png`](platform/docs/architecture.png)。

## 快速开始

- 平台启动、配置与接口说明：[`platform/README.md`](platform/README.md)
- 本地基础设施（GitLab CE、Runner、k3s、MySQL）：[`infra/M0-SETUP.md`](infra/M0-SETUP.md)

## 说明

- `app/` 是早期一体化演示应用，已通过 `git subtree split` 拆成 `app-api` / `app-web` 两个仓库；本地 `app/` 是独立 Git 仓库，不包含在本仓库中。
- 仓库中的数据库密码、Token 等均为本地开发占位值，不可用于任何真实环境。
