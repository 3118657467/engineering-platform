CREATE TABLE IF NOT EXISTS users (
  username       VARCHAR(64)  NOT NULL PRIMARY KEY,
  display_name   VARCHAR(64)  NOT NULL,
  role           VARCHAR(16)  NOT NULL,
  password_hash  CHAR(64)     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sessions (
  token       CHAR(36)     NOT NULL PRIMARY KEY,
  username    VARCHAR(64)  NOT NULL,
  expires_at  DATETIME     NOT NULL,
  created_at  DATETIME     NOT NULL,
  KEY idx_sessions_user (username),
  CONSTRAINT fk_sessions_user FOREIGN KEY (username) REFERENCES users (username) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS projects (
  id          VARCHAR(64)  NOT NULL PRIMARY KEY,
  name        VARCHAR(128) NOT NULL,
  service     VARCHAR(16)  NULL,
  runtime_app VARCHAR(64)  NULL,
  repo_url    VARCHAR(512) NULL,
  created_at  DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS requirements (
  id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id         VARCHAR(64)  NOT NULL,
  req_no             VARCHAR(32)  NOT NULL,
  title              VARCHAR(256) NOT NULL,
  description        TEXT         NULL,
  owner              VARCHAR(64)  NOT NULL,
  branch_name        VARCHAR(128) NULL,
  branch_status      VARCHAR(16)  NOT NULL DEFAULT 'NONE',
  tip_sha            CHAR(7)      NULL,
  branch_created_at  DATETIME     NULL,
  created_at         DATETIME     NOT NULL,
  updated_at         DATETIME     NOT NULL,
  UNIQUE KEY uk_req_no (project_id, req_no),
  CONSTRAINT fk_req_project FOREIGN KEY (project_id) REFERENCES projects (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS requirement_related (
  requirement_id BIGINT      NOT NULL,
  username       VARCHAR(64) NOT NULL,
  PRIMARY KEY (requirement_id, username),
  CONSTRAINT fk_related_req FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS requirement_branches (
  requirement_id BIGINT       NOT NULL,
  service        VARCHAR(16)  NOT NULL,
  branch_name    VARCHAR(128) NOT NULL,
  tip_sha        CHAR(7)      NULL,
  updated_at     DATETIME     NOT NULL,
  PRIMARY KEY (requirement_id, service),
  CONSTRAINT fk_reqbranch_req FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cr_requests (
  id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  requirement_id BIGINT     NOT NULL,
  service       VARCHAR(16) NOT NULL,
  approver      VARCHAR(64) NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'NONE',
  requested_by  VARCHAR(64) NULL,
  requested_at  DATETIME    NULL,
  decided_at    DATETIME    NULL,
  comment       VARCHAR(512) NULL,
  UNIQUE KEY uk_cr_req_service (requirement_id, service),
  CONSTRAINT fk_cr_req FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS env_states (
  requirement_id BIGINT      NOT NULL,
  service        VARCHAR(16) NOT NULL,
  env            VARCHAR(16) NOT NULL,
  status         VARCHAR(16) NOT NULL,
  sha            CHAR(7)     NULL,
  deployed_at    DATETIME    NULL,
  operator       VARCHAR(64) NULL,
  updated_at     DATETIME    NOT NULL,
  error_message  VARCHAR(512) NULL,
  PRIMARY KEY (requirement_id, service, env),
  CONSTRAINT fk_env_req FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS deploy_batches (
  id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id  VARCHAR(64) NOT NULL,
  service     VARCHAR(16) NOT NULL,
  env         VARCHAR(16) NOT NULL,
  status      VARCHAR(16) NOT NULL,
  operator    VARCHAR(64) NOT NULL,
  started_at  DATETIME    NOT NULL,
  finished_at DATETIME    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS deployments (
  id             BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
  batch_id       BIGINT      NOT NULL,
  requirement_id BIGINT      NOT NULL,
  service        VARCHAR(16) NOT NULL,
  env            VARCHAR(16) NOT NULL,
  branch_name    VARCHAR(128) NOT NULL,
  sha            CHAR(7)     NOT NULL,
  status         VARCHAR(16) NOT NULL,
  operator       VARCHAR(64) NOT NULL,
  started_at     DATETIME    NOT NULL,
  finished_at    DATETIME    NULL,
  error_message  VARCHAR(512) NULL,
  KEY idx_deploy_batch (batch_id),
  CONSTRAINT fk_deploy_batch FOREIGN KEY (batch_id) REFERENCES deploy_batches (id) ON DELETE CASCADE,
  CONSTRAINT fk_deploy_req FOREIGN KEY (requirement_id) REFERENCES requirements (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS deployment_steps (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  deployment_id BIGINT       NOT NULL,
  seq           INT          NOT NULL,
  name          VARCHAR(64)  NOT NULL,
  status        VARCHAR(16)  NOT NULL,
  duration_ms   INT          NULL,
  KEY idx_step_deploy (deployment_id),
  CONSTRAINT fk_step_deploy FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prod_releases (
  requirement_id BIGINT      NOT NULL,
  service        VARCHAR(16) NOT NULL,
  current_sha    CHAR(7)     NOT NULL,
  released_at    DATETIME    NOT NULL,
  previous_sha   CHAR(7)     NULL,
  prev_released_at DATETIME  NULL,
  PRIMARY KEY (requirement_id, service),
  CONSTRAINT fk_release_req FOREIGN KEY (requirement_id) REFERENCES requirements (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prod_release_history (
  id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  project_id     VARCHAR(64)  NOT NULL,
  service        VARCHAR(16)  NOT NULL,
  requirement_id BIGINT       NOT NULL,
  req_no         VARCHAR(32)  NOT NULL,
  title          VARCHAR(256) NOT NULL,
  branch_name    VARCHAR(128) NOT NULL,
  sha            CHAR(7)      NOT NULL,
  released_at    DATETIME     NOT NULL,
  operator       VARCHAR(64)  NOT NULL,
  batch_id       BIGINT       NULL,
  deployment_id  BIGINT       NULL,
  is_current     TINYINT      NULL,
  KEY idx_rel_scope (project_id, service, released_at),
  KEY idx_rel_req (requirement_id, service),
  UNIQUE KEY uk_rel_current (project_id, service, is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS prod_requirement_archives (
  requirement_id BIGINT      NOT NULL PRIMARY KEY,
  project_id     VARCHAR(64) NOT NULL,
  service        VARCHAR(16) NOT NULL,
  release_id     BIGINT      NULL,
  archived_at    DATETIME    NOT NULL,
  KEY idx_archive_scope (project_id, service)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS deployment_release_target (
  deployment_id BIGINT NOT NULL PRIMARY KEY,
  release_id    BIGINT NOT NULL,
  KEY idx_drt_release (release_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS deploy_locks (
  project_id    VARCHAR(64) NOT NULL,
  service       VARCHAR(16) NOT NULL,
  env           VARCHAR(16) NOT NULL,
  batch_id      BIGINT      NOT NULL,
  acquired_at   DATETIME    NOT NULL,
  PRIMARY KEY (project_id, service, env)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS deployment_pipelines (
  deployment_id BIGINT      NOT NULL PRIMARY KEY,
  pipeline_id   BIGINT      NOT NULL,
  status        VARCHAR(32) NOT NULL,
  updated_at    DATETIME    NOT NULL,
  CONSTRAINT fk_pipeline_deploy FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
