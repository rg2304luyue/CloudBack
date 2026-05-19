# CloudBack 微服务电商系统

Spring Cloud Alibaba 微服务架构，8 个模块，三种角色（买家/卖家/管理员）。

## 技术栈

|层级|技术|版本|
|---|---|---|
|语言|Java|21|
|框架|Spring Boot|3.3.9|
|微服务|Spring Cloud|2023.0.3|
|微服务|Spring Cloud Alibaba|2023.0.1.0|
|注册配置中心|Nacos|2.5.x (Docker)|
|API 网关|Spring Cloud Gateway|—|
|认证|JWT (jjwt 0.12.6)|—|
|ORM|MyBatis-Plus|3.5.11|
|连接池|Druid|1.2.24|
|缓存|Redis|7.x (宿主机)|
|消息队列|Kafka|3.9.x (Docker)|
|熔断限流|Sentinel|1.8.x (Docker)|
|远程调用|OpenFeign|—|
|对象存储|MinIO|latest (Docker)|

## 项目结构

```text
CloudBack/
├── pom.xml                      # 父 POM
├── .env / .env.example           # 环境变量（Spring Boot 启动时自动加载）
├── start-all.bat                 # Windows 一键启动脚本
├── cloud-common/                 # 公共模块：实体、工具、配置、异常
├── cloud-gateway/  :8080         # API 网关：JWT 认证 + 路由分发
├── cloud-auth/     :8081         # 认证服务：注册、登录、JWT 签发
├── cloud-product/  :8082         # 商品服务：分类、商品 CRUD、审核、库存
├── cloud-user/     :8083         # 用户服务：信息、地址、卖家申请、管理员
├── cloud-cart/     :8084         # 购物车服务：Redis Hash
├── cloud-order/    :8085         # 订单服务：下单 + Kafka 生产者
├── cloud-payment/  :8086         # 支付服务：模拟支付 + Kafka 消费者
├── sql/init.sql                 # 建库建表脚本
└── docker/                       # Nacos / Kafka / Sentinel / MinIO 编排
```

## 角色体系

三种角色，通过 `user.role` 字段区分（BUYER / SELLER / ADMIN），JWT 中携带。

|角色|常量|注册方式|核心权限|
|---|---|---|---|
|买家|BUYER|公开注册|浏览商品、加入购物车、下单、申请成为卖家|
|卖家|SELLER|买家申请→管理员审批|管理自己的商品（增删改、提交审核）、管理分类|
|管理员|ADMIN|手动提升|审批卖家申请+商品审核、重置密码、管理所有用户|

**JWT 角色流转**：

```text
登录 → AuthServiceImpl 签发 JWT（含 role claim）
  → Gateway AuthGlobalFilter 解析 JWT → 注入 X-User-Id / X-Username / X-User-Role
  → 下游服务通过 @RequestHeader 读取
```

Gateway 白名单外的所有请求强制携带有效 JWT。

---

## 业务流程

### 1. 注册与登录

```text
POST /api/auth/register (username, password, nickname)
  → 默认角色 BUYER → BCrypt 加密 → 入库

POST /api/auth/login (username, password)
  → 查用户 → BCrypt 验证 → 检查状态 → 签发 JWT（含 userId + username + role）
  → 前端存 token → fetchUserInfo → 按角色跳转首页
```

注册/登录在 Gateway 白名单中，无需 Token。

### 2. 买家浏览与购物

```text
GET  /api/product/list         → 公开，只返回 status=1（审核通过）的商品
GET  /api/product/detail/{id}  → 公开
POST /api/cart/add             → 需登录 → Redis HASH cloud:cart:{userId}
GET  /api/cart/list            → 需登录 → Redis + Feign 查商品信息
POST /api/order/create         → 需登录 → 完整下单链路（见第 5 节）
```

### 3. 申请成为卖家

```text
BUYER 登录 → 个人中心 → 点击「申请成为卖家」
  → POST /api/user/apply-seller → seller_application 表插入 PENDING 记录
  → ADMIN 登录 → 用户管理页 → 看到待审批列表
  → 「通过」→ user.role 变为 SELLER + application.status = APPROVED
  → 「拒绝」→ application.status = REJECTED
```

已申请的不可重复提交。审批通过后需重新登录获取新 JWT。

### 4. 卖家商品管理 + 审核

```text
卖家添加/编辑商品
  → POST/PUT /api/product
  → sellerId 自动设为当前用户
  → SELLER 角色：status 自动设为 2（待审核）
  → ADMIN  角色：status 直接设为 1（上架）

管理员审核
  → GET  /api/product/admin/pending          列出所有 status=2 的商品
  → PUT  /api/product/admin/review/{id}?approved=true    → 通过 → status=1
  → PUT  /api/product/admin/review/{id}?approved=false   → 拒绝 → status=0

公开商品列表（首页/商品页）
  → GET /api/product/list → 只查 status=1 的商品
```

卖家只能操作 `sellerId` 匹配的商品；管理员可操作全部。卖家编辑已上架商品后重新进入待审核。

### 5. 用户下单（完整链路）

```text
POST /api/order/create

① Feign → CartService.getCheckedItems()          # Redis HASH
② Feign → UserService.getAddressById()            # MySQL
③ Feign → ProductService.deductStock() × N        # MySQL @Transactional
④ INSERT order_info + order_item × N              # MySQL
⑤ Feign → CartService.clearCart()                 # Redis
⑥ Kafka.send("order-create")                      # 通知支付服务
    → cloud-payment 消费 → 模拟支付 → INSERT payment
    → Kafka.send("payment-result")
    → cloud-order 消费 → UPDATE order_info SET status=已支付
```

### 6. 管理员完整功能

|功能|接口|说明|
|---|---|---|
|商品审核|`GET /product/admin/pending`|查看所有待审核商品|
|审批|`PUT /product/admin/review/{id}?approved=true`|通过/拒绝|
|用户列表|`GET /user/admin/list`|所有用户（密码脱敏）|
|重置密码|`PUT /user/admin/reset-password`|给任意用户设新密码|
|卖家申请|`GET /user/admin/applications`|查看待审批|
|审批申请|`PUT /user/admin/applications/{id}?approved=true`|通过/拒绝|

---

## 数据库

|表|说明|关键字段|
|---|---|---|
|user|用户|username(唯一), password(BCrypt), role(BUYER/SELLER/ADMIN)|
|address|收货地址|user_id, receiver_name, is_default|
|category|商品分类|parent_id (树形), name, sort|
|product|商品|category_id, seller_id, price, stock, status(0/1/2), main_image|
|seller_application|卖家申请|user_id, status(PENDING/APPROVED/REJECTED)|
|order_info|订单|order_no(唯一), user_id, status(0待支付~4已取消)|
|order_item|订单明细|order_id, product_id, quantity, price|
|payment|支付记录|order_no, trade_no, status|

### Redis 购物车

```text
Key:   cloud:cart:{userId}
Type:  Hash, TTL 7 天
Field: productId → Value: CartItem JSON
```

---

## 环境变量

项目根目录 `.env` 文件，支持 `${KEY}` 引用，`VM_HOST` 一键切换：

```ini
VM_HOST=192.168.91.130
MYSQL_ADDR=${VM_HOST}:3306
REDIS_ADDR=${VM_HOST}
NACOS_ADDR=${VM_HOST}:8848
SENTINEL_ADDR=${VM_HOST}:8858
KAFKA_ADDR=${VM_HOST}:9092
```

`cloud-common` 内置 dotenv 加载器，启动时自动从工作目录向上查找 `.env` 并注入。

---

## 部署

### 虚拟机（Ubuntu 24.04）

MySQL 8.0 + Redis 7 宿主机安装；Nacos / Kafka / Sentinel / MinIO 通过 Docker 运行。

```bash
# 初始化数据库
mysql -u root -p < sql/init.sql

# 开放远程访问与防火墙
# MySQL: bind-address = 0.0.0.0
# Redis: bind 0.0.0.0 + requirepass
sudo ufw allow 3306,6379,8848,9848,9000,9001,9092,8088,8858/tcp

# Docker 中间件
cd docker && docker compose up -d
```

### Windows 本地开发

```bash
cp .env.example .env
vim .env     # VM_HOST=192.168.91.130

mvn clean install -DskipTests
mvn spring-boot:run -pl cloud-gateway
mvn spring-boot:run -pl cloud-auth
# ... 依次启动 7 个服务，或双击 start-all.bat
```

---

## 常见问题

|现象|原因|解决|
|---|---|---|
|Gateway 连 localhost 而非 VM IP|`.env` 未加载|`mvn clean install -DskipTests` 后重启|
|Nacos 9848 端口连不上|防火墙未放行|`sudo ufw allow 9848/tcp`|
|Nacos 客户端解析到 localhost|Docker 通告了容器内网 IP|`nacos.inetutils.ip-address=192.168.91.130`|
|Redis 连接被拒|Redis 只监听 127.0.0.1|改 `bind 0.0.0.0`|
|审批显示"申请不存在"|JS 精度丢失 Snowflake ID|`JacksonConfig`：Long → String|
|新增商品报 JSON 错误|`images` 字段为空字符串|改为 `null`|
|改角色后仍报权限错误|JWT 仍是旧角色|退出重新登录|

## 安全设计

BCrypt 密码加密 · Gateway 全局 JWT 校验 · X-User-Id 用户隔离 · 密码脱敏 · 逻辑删除 · 雪花 ID
