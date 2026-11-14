-- 仅本地开发环境使用，密码禁止用于任何真实环境
CREATE DATABASE IF NOT EXISTS app_dev  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS app_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS app_uat  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS app_prod CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'app_dev'@'%'  IDENTIFIED BY 'app_dev_pw';
CREATE USER IF NOT EXISTS 'app_test'@'%' IDENTIFIED BY 'app_test_pw';
CREATE USER IF NOT EXISTS 'app_uat'@'%'  IDENTIFIED BY 'app_uat_pw';
CREATE USER IF NOT EXISTS 'app_prod'@'%' IDENTIFIED BY 'app_prod_pw';
CREATE USER IF NOT EXISTS 'platform'@'%' IDENTIFIED BY 'platform_pw';

GRANT ALL PRIVILEGES ON app_dev.*  TO 'app_dev'@'%';
GRANT ALL PRIVILEGES ON app_test.* TO 'app_test'@'%';
GRANT ALL PRIVILEGES ON app_uat.*  TO 'app_uat'@'%';
GRANT ALL PRIVILEGES ON app_prod.* TO 'app_prod'@'%';
GRANT ALL PRIVILEGES ON platform.* TO 'platform'@'%';

FLUSH PRIVILEGES;
