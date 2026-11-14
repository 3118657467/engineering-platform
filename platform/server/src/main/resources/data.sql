-- 基础数据：全部使用 INSERT IGNORE，重复启动不会产生重复行。
-- 账号密码均为 123456，password_hash = SHA-256(username:password)
-- 需求、分支、部署等业务数据从空白开始，由验收流程现场产生。

INSERT IGNORE INTO users (username, display_name, role, password_hash) VALUES
  ('devuser',      '开发者', 'DEV',      '75076473d5425cf8a5ed419af5971e2cf0d7390d93e5c34331685a55855ca434'),
  ('zhanggong',    '张工',   'CR_ADMIN', '2379b9b1dc92505689d5738e254f1fd9a2fa047332c81320406c563b25760403'),
  ('ligong',       '李工',   'CR_ADMIN', 'ad5fe983d6beab0d0cf4524d68fff8919d5ad77730086751030f28bdf3585dc4');

INSERT IGNORE INTO projects (id, name, service, runtime_app, repo_url, created_at) VALUES
  ('app-api',    'app-api',    'api', 'app-api',    'http://localhost:8080/root/app-api.git', '2026-10-20 09:00:00'),
  ('app-web',    'app-web',    'web', 'app-web',    'http://localhost:8080/root/app-web.git', '2026-10-20 09:00:01'),
  ('pay-center', 'pay-center', NULL,  'pay-center', 'http://localhost:8080/dev/pay-center.git', '2026-10-20 09:00:02');

UPDATE projects SET service = 'api', runtime_app = 'app-api',
  repo_url = 'http://localhost:8080/root/app-api.git' WHERE id = 'app-api';
UPDATE projects SET service = 'web', runtime_app = 'app-web',
  repo_url = 'http://localhost:8080/root/app-web.git' WHERE id = 'app-web';
