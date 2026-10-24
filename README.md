# CloudBack 微服务电商系统

Spring Cloud Alibaba 微服务架构，8 个模块，三种角色（买家/卖家/管理员）。

## 快速开始

> 想先跑起来，只看这一节即可；完整步骤见后面的「部署」与「环境变量」。

**1. 中间件**：MySQL 8.0+ 与 Redis 必须就绪；Nacos、Kafka、Sentinel、MinIO、Meilisearch 由 compose 起：

```bash
cd docker && docker compose config && docker compose up -d
```

**2. 环境变量**：`cp .env.example .env`，把 `VM_HOST` 改成中间件所在 IP。`JWT_SECRET`、`CLOUD_INTERNAL_TOKEN`、`ALIPAY_*`、MySQL/Redis/MinIO 密码在 yml 里都没有默认值，缺了服务起不来。

**3. 初始化数据库**：`mysql -u root -p < sql/init.sql`。该脚本是运行库的结构+数据全量克隆，含 `DROP TABLE`，执行前先看「部署」里的两条前提。

**4. 启动后端**：

```bash
mvn clean install -DskipTests
mvn spring-boot:run -pl cloud-gateway   # 8080，其余模块按 auth→user→product→cart→order→payment 顺序
```

也可以直接双击 `start-all.bat`，它按同一顺序在独立窗口启动 7 个后端，最后在 `..\CloudFront` 执行 `npm run preview`。

**5. 验证**：Nacos 控制台（`http://<VM_HOST>:8848/nacos`）能看到 7 个服务实例；前端 `http://localhost:5173`（`npm run dev`）或 `http://localhost:4173`（preview）。端口分配见「项目结构 → 端口分配」。

> 待确认：`start-all.bat`（调用系统 `mvn`）与 Maven Wrapper（`mvnw` / `mvnw.cmd`）哪个是本项目的权威启动方式。两者仓库内都存在，本文暂并列给出，未做取舍。

---

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.3.9 |
| 微服务 | Spring Cloud | 2023.0.3 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.0 |
| 注册中心 | Nacos | 2.5.1 (Docker，仅服务发现；配置中心依赖存在但未启用) |
| API 网关 | Spring Cloud Gateway | — |
| 认证 | JWT (jjwt 0.12.6)，固定 HS256 | — |
| 内部服务鉴权 | InternalAuthFilter（常量时间比对 `X-Internal-Token`） | — |
| Token 吊销 | Redis 黑名单（TokenBlacklistService + JwtBlacklistFilter） | — |
| ORM | MyBatis-Plus | 3.5.11 |
| 连接池 | Druid | 1.2.24 |
| 缓存 | Redis | 7.x (宿主机) |
| 本地缓存 | Caffeine | 3.1.8（版本由 Spring Boot 父 POM 管理） |
| 消息队列 | Kafka | 3.9.x (Docker) |
| 对象存储 | MinIO | latest (Docker) |
| 熔断限流 | Sentinel | 1.8.x (Docker，当前仅接入 dashboard，未配置规则) |
| 远程调用 | OpenFeign + LoadBalancer | — |
| 搜索引擎 | Meilisearch | 1.10 (Docker) |
| 第三方支付 | 支付宝 SDK (alipay-sdk-java) | 4.40.806.ALL |
| 分布式锁 / 布隆过滤器 | Redisson | — |
| 接口文档 | Knife4j | 4.5.0（除 gateway 外各服务均配置，但 `knife4j.enable: ${KNIFE4J_ENABLE:false}` 默认关闭） |
| 数据库驱动 | mysql-connector-j | 9.1.0 |
| 工具 | Hutool、Fastjson2、Lombok | — |

项目只使用 Kafka 作为消息队列，没有 RocketMQ / Seata。

---

## 部署

### 虚拟机（Ubuntu）

```bash
# MySQL + Redis 宿主机安装
sudo apt install mysql-server redis-server

# 初始化（2026-10-24 起 init.sql 是「结构与数据全量克隆」，可直接整体执行）
#   内容：nacos_config（10 表 + admin 账号）+ cloud_mall（11 表 + 全部 mock 数据），
#   来源为 192.168.91.130 上运行中的库（mysqldump 原样导出）。已在隔离库加载验证：
#   21 张表 CHECKSUM 与源库逐表一致，列/索引/CHECK/collation 结构签名 162 项零差异。
# ⚠ 两个前提：
#   1. 每张表前都有 DROP TABLE IF EXISTS —— 在已有数据的库上执行会清空重建；
#      只想要空表结构时，先注释掉所有 DROP TABLE 与 INSERT 段。
#   2. 若目标环境的 Nacos 已在运行且库里有配置/额外账号，执行前删掉或注释掉 [1] nacos_config 整段。
#   需要 MySQL 8.0+（CHECK 约束、utf8mb4_0900_ai_ci 排序规则）。
mysql -u root -p < sql/init.sql

# 历史库升级才需要跑迁移；新库已被 init.sql 覆盖，无需再执行下列三条
#   sql/migrations/20260907_inventory_operation.sql
#   sql/migrations/20260910_order_request.sql
#   sql/migrations/20260928_order_request_compensate.sql

# 开放远程访问
# MySQL: /etc/mysql/mysql.conf.d/mysqld.cnf → bind-address = 0.0.0.0
# Redis: /etc/redis/redis.conf → bind 0.0.0.0
sudo ufw allow 3306,6379,8848,9848,9000,9001,9092,8088,8858,7700/tcp

# Docker 中间件（compose 里的密码是必填变量，缺一个就起不来，先校验）
cd docker && docker compose config && docker compose up -d
```

Kafka 需要新建本次新增的三个主题（compose 未预建）：
`payment-close`、`refund-request`、`cart-clear`，以及各自的 `.DLQ`。

### Windows 本地开发

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env → VM_HOST 设为虚拟机 IP

# 2. 编译（也可用 .\mvnw.cmd 代替 mvn）
mvn clean install -DskipTests

# 3. 依次启动（或双击 start-all.bat，它按下面的顺序在独立窗口中启动）
mvn spring-boot:run -pl cloud-gateway
mvn spring-boot:run -pl cloud-auth
mvn spring-boot:run -pl cloud-user
mvn spring-boot:run -pl cloud-product
#   start-all.bat 在这里等待 15 秒
mvn spring-boot:run -pl cloud-cart
mvn spring-boot:run -pl cloud-order
mvn spring-boot:run -pl cloud-payment
#   start-all.bat 再等待 10 秒后，在 ..\CloudFront 执行 npm run preview（http://localhost:4173）
```

`start-all.bat` 使用系统 `mvn`，不是 Maven Wrapper。

### 测试

单元测试在 cloud-common、cloud-order、cloud-product、cloud-payment 四个模块（均引入 `spring-boot-starter-test`），共 7 个测试类 46 个用例：

| 模块 | 测试类 | 用例数 |
|---|---|---|
| cloud-common | `CaffeineConfigTest` | 3 |
| cloud-order | `OrderRequestServiceTest`、`OrderServiceRegressionTest`、`StockRestoreConsumerTest` | 8 / 14 / 3 |
| cloud-product | `InventoryOperationTest` | 11 |
| cloud-payment | `PaymentServiceImplTest`、`PaymentReconciliationSchedulerTest` | 4 / 3 |

```bash
mvn test -pl cloud-common,cloud-order,cloud-product,cloud-payment -am
```

上面的构建命令都带 `-DskipTests`，默认不会跑测试。

### 手工验证（可选）

```bash
# 编译 payment / order（Maven Wrapper 写法；与 start-all.bat 用系统 mvn 的差异见「快速开始」的待确认项）
.\mvnw.cmd -q -DskipTests -pl cloud-payment,cloud-order -am compile
```

验证支付恢复场景前，先确保 MySQL、Redis、Kafka、Nacos 与 cloud-payment 都在运行：在支付宝沙箱完整付一笔，再确认 payment 侧写入支付记录、order 侧消费到 `payment-result`。

---

## 环境变量

项目根目录 `.env` 文件，支持 `${KEY}` 引用：

```ini
VM_HOST=<虚拟机IP>
MYSQL_ADDR=${VM_HOST}:3306
MYSQL_USER=<mysql用户>
MYSQL_PASSWORD=<mysql密码>
REDIS_ADDR=${VM_HOST}
REDIS_PORT=6379
REDIS_PASSWORD=<redis密码>
NACOS_ADDR=${VM_HOST}:8848
# NACOS_NAMESPACE=<可选，仅 gateway 读取>
SENTINEL_ADDR=${VM_HOST}:8858
KAFKA_ADDR=${VM_HOST}:9092
# JWT 密钥（至少 32 字符，缺失或过短启动失败）
JWT_SECRET=<your-secret>
# MinIO 对象存储
MINIO_ENDPOINT=${VM_HOST}:9000
MINIO_ACCESS_KEY=<minio-access-key>
MINIO_SECRET_KEY=<minio-secret-key>
MINIO_BUCKET=cloudmall-products
# Meilisearch 搜索引擎
MEILI_HOST=${VM_HOST}
MEILI_PORT=7700
MEILI_MASTER_KEY=<your-master-key>
# 内部调用令牌（所有服务一致；留空时 InternalAuthFilter 放行并记 WARN，内部接口失去保护）
CLOUD_INTERNAL_TOKEN=<high-entropy-shared-value>
# Nacos 鉴权（Docker 里的 Nacos 开启了鉴权）
# 说明：NACOS_USERNAME / NACOS_PASSWORD 会被所有服务读取，但 .env.example 里目前没有这两项，需要时自行补上
NACOS_USERNAME=<nacos用户名>
NACOS_PASSWORD=<nacos密码>
NACOS_AUTH_IDENTITY_KEY=<identity-key>
NACOS_AUTH_IDENTITY_VALUE=<identity-value>
NACOS_AUTH_TOKEN=<Base64 编码的 32 字节以上密钥>
# Sentinel 控制台登录
SENTINEL_DASHBOARD_AUTH_USERNAME=<sentinel用户名>
SENTINEL_DASHBOARD_AUTH_PASSWORD=<sentinel密码>
# MinIO 的 docker-compose root 账号（与服务端 MINIO_ACCESS_KEY/SECRET_KEY 对应）
MINIO_USER=<minio-root-user>
MINIO_PASSWORD=<minio-root-password>
# 网关 CORS 允许来源（逗号分隔；5173 为 dev，4173 为 preview）
CORS_ALLOWED_ORIGINS=http://localhost:5173,http://localhost:4173
# Knife4j 接口文档开关（生产保持 false；不在 .env.example 中，需自行添加或用系统环境变量）
# KNIFE4J_ENABLE=false
# docker/.env.example 另含容器专用键：MYSQL_PORT、KAFKA_HOST

# 支付宝沙箱
ALIPAY_APP_ID=<支付宝沙箱AppID>
ALIPAY_PRIVATE_KEY=<PKCS8应用私钥单行>
ALIPAY_PUBLIC_KEY=<支付宝公钥单行>
ALIPAY_GATEWAY=https://openapi-sandbox.dl.alipaydev.com/gateway.do
ALIPAY_NOTIFY_URL=http://<IP>:8080/api/payment/notify/alipay
ALIPAY_RETURN_URL=http://localhost:4173/payment/result
# 前端地址（支付宝同步回跳目标，默认 http://localhost:4173；写入 .env 不生效，需以系统环境变量覆盖）
ALIPAY_FRONTEND_URL=http://localhost:4173
```

`DotenvEnvironmentPostProcessor` 启动时自动从工作目录向上查找 `.env`（最多 5 层），解析后以**最低优先级**注入 Spring Environment：同名的操作系统环境变量、命令行参数会覆盖 `.env` 中的值。完整键列表以 `.env.example` 为准。

注意：
- `cloud-product` 用到 Redis（浏览量、缓存、Redisson），但它的 `application.yml` 没有 `spring.data.redis` 配置，会回退到 `localhost:6379`、无密码。读取 `REDIS_ADDR / REDIS_PORT / REDIS_PASSWORD` 的是 auth、user、cart、order 四个服务。Redis 不在本机时需要给 product 补上配置。
- 弱默认值已清除：MySQL 密码、Redis 密码、MinIO 账号、Meilisearch master key 在 yml 中都改为空默认值，必须由 `.env` 或系统环境变量提供；仅 Nacos 用户名保留 `nacos` 默认值。
- `docker/docker-compose.yml` 中 Nacos、Sentinel、MinIO 的密码改为 `${VAR:?must set in .env}` 必填变量，缺变量容器会直接启动失败；改完先 `docker compose config` 校验再 `up -d`。
- 所有服务都有 `nacos.config` 配置块，但没有 `spring.config.import`，配置中心实际未启用；各服务均开启 `sentinel.eager: true`。
- Kafka：消费者组 `order-consumer-group`（payment-result）、`order-stock-restore-group`（stock-restore）、`payment-consumer-group`、`cart-consumer-group`，JSON 序列化且 `trusted.packages` 已收紧到 `org.cloudback.**`；Docker 中为单节点 KRaft，自动建 Topic，`KAFKA_HOST` 决定对外通告地址；kafka-ui 映射到宿主机 8088。新增主题需手动创建：payment-close、refund-request、cart-clear（及各自 `.DLQ`）。
- Docker 中的 Nacos 开启了鉴权，使用 MySQL 库 `nacos_config`；`init.sql` 现已包含该库 10 张表的结构与 admin 账号（配置 0 条），不再依赖 Nacos 官方脚本初始化。服务端配置挂载自 `docker/nacos/conf/application.properties`。

---

## 项目结构

```text
CloudBack/
├── pom.xml                     # 父 POM，统一依赖版本管理
├── .env                        # 环境变量（VM_HOST 等，启动时自动加载；不入库，模板为 .env.example）
├── .mvn/wrapper/ + mvnw(.cmd)  # Maven Wrapper（Maven 3.9.15）
├── start-all.bat               # Windows 一键启动：mvn clean install -DskipTests → 7 个后端 → 前端 npm run preview(4173)
│
├── cloud-common/               # 公共模块（被所有模块依赖，不独立部署）
│   └── src/main/java/org/cloudback/common/
│       ├── entity/             # BaseEntity、User（共享实体）
│       ├── result/             # R<T> 统一响应体、ResultCode 错误码枚举
│       ├── exception/          # BusinessException、GlobalExceptionHandler
│       ├── config/             # MyBatisPlusConfig、RedisConfig、JacksonConfig、MinioConfig
│       │                       # CaffeineConfig、TwoLevelCacheService、CommonAutoConfiguration
│       │                       # AutoFillMetaObjectHandler、DotenvEnvironmentPostProcessor
│       │                       # MeilisearchConfig（Meilisearch 客户端 Bean）
│       │                       # InternalTokenFeignConfig（Feign 自动带内部令牌）
│       │                       # CacheEvictListenerConfig（Redis 广播跨实例 L1 失效）
│       ├── filter/             # InternalAuthFilter（校验 X-Internal-Token，保护 /internal/ 接口）
│       ├── service/            # FileService（MinIO 文件上传）、OutboxService（发件箱可靠投递）
│       │                       # TokenBlacklistService（JWT 黑名单，重置密码时吊销存量 token）
│       ├── constant/           # SystemConstants（final class，Token前缀、Redis Key、订单状态、Kafka Topic 等）
│       ├── utils/              # JwtUtil（签发/解析/验证 JWT）
│       └── mapper/             # UserMapper、OutboxMessageMapper（共享 MyBatis-Plus 映射）

│
├── cloud-gateway/  :8080       # API 网关
│   └── src/main/java/org/cloudback/gateway/
│       ├── GatewayApplication.java
│       ├── config/CorsConfig.java         # CORS 跨域（WebFlux 方式，来源白名单）
│       └── filter/AuthGlobalFilter.java   # 全局 JWT 认证过滤器 + /internal/ 路径拦截

│
├── cloud-auth/     :8081       # 认证服务
│   └── src/main/java/org/cloudback/auth/
│       ├── AuthApplication.java
│       ├── controller/AuthController.java
│       └── service/impl/AuthServiceImpl.java
│
├── cloud-user/     :8083       # 用户服务（个人信息、地址、卖家申请审批）
│   └── src/main/java/org/cloudback/user/
│       ├── UserApplication.java
│       ├── controller/
│       │   ├── UserController.java
│       │   └── AdminUserController.java      # 管理员：用户管理 + 卖家申请审批
│       ├── service/impl/UserServiceImpl.java
│       ├── dto/                # AddressRequest、ProcessApplicationRequest 等
│       ├── model/entity/       # Address、SellerApplication
│       └── mapper/             # AddressMapper、SellerApplicationMapper
│
├── cloud-product/  :8082       # 商品服务
│   └── src/main/java/org/cloudback/product/
│       ├── ProductApplication.java
│       ├── controller/         # ProductController、AdminProductController、SellerProductController、CategoryController
│       ├── service/
│       │   ├── impl/ProductServiceImpl.java
│       │   ├── SearchService.java
│       │   └── impl/SearchServiceImpl.java    # Meilisearch 索引与搜索
│       ├── model/entity/       # Category、Product、InventoryOperation
│       └── mapper/             # CategoryMapper、ProductMapper、InventoryOperationMapper（库存账本）
│
├── cloud-cart/     :8084       # 购物车服务（Redis Hash + Caffeine 读缓存）
│   └── src/main/java/org/cloudback/cart/
│       ├── CartApplication.java
│       ├── config/KafkaConsumerConfig.java # 消费失败 3 次转 {topic}.DLQ
│       ├── consumer/CartClearConsumer.java # 消费 cart-clear，清空购物车
│       ├── controller/CartController.java
│       ├── service/impl/CartServiceImpl.java
│       ├── feign/ProductFeignClient.java   # 查商品信息（含批量）
│       └── dto/CartItem.java、CartClearMessage.java、ProductInfo.java

│
├── cloud-order/    :8085       # 订单服务（@EnableScheduling 订单超时自动取消）
│   └── src/main/java/org/cloudback/order/
│       ├── OrderApplication.java
│       ├── controller/
│       │   ├── OrderController.java              # 买家订单：创建/查询/取消/确认收货
│       │   ├── SellerOrderController.java        # 卖家订单：查询/发货
│       │   └── OutboxAdminController.java        # 失败 Outbox 查看/重放（ADMIN）
│       ├── service/OrderRequestService.java     # 下单请求状态机（order_request + 30 秒恢复调度）
│       ├── service/impl/OrderServiceImpl.java    # 核心下单编排
│       ├── scheduler/OrderTimeoutScheduler.java  # 定时扫描超时订单自动取消
│       ├── scheduler/OutboxScheduler.java        # Outbox 消息定时发送
│       ├── mq/PaymentResultConsumer.java         # Kafka 支付结果消费者（竞态检测 + 触发退款）
│       ├── mq/StockRestoreConsumer.java          # Kafka 库存回补消费者（幂等由商品服务账本保证）
│       ├── feign/              # CartFeignClient、ProductFeignClient、UserFeignClient
│       │                       # CartItemDTO、ProductDTO
│       ├── config/FeignRequestInterceptor.java   # 用户上下文透传
│       ├── config/KafkaConsumerConfig.java       # 消费失败 3 次转 {topic}.DLQ

│       ├── model/entity/       # Order、OrderItem、OrderRequest
│       └── mapper/             # OrderMapper、OrderItemMapper、OrderRequestMapper
│
├── cloud-payment/  :8086       # 支付服务
│   └── src/main/java/org/cloudback/payment/
│       ├── PaymentApplication.java
│       ├── config/AlipayProperties.java          # 支付宝配置属性绑定（含 frontendUrl 前端回跳地址）
│       ├── config/AlipayConfig.java              # AlipayClient Bean
│       ├── config/OutboxConfig.java            # OutboxService Bean 声明
│       ├── controller/PaymentController.java
│       ├── service/impl/PaymentServiceImpl.java  # 支付宝页面支付核心逻辑（含退款、关单）
│       ├── feign/OrderFeignClient.java           # 支付前校验订单状态（fail-closed）
│       ├── mq/OrderCreateConsumer.java           # Kafka 订单创建消费者
│       ├── mq/PaymentCloseConsumer.java          # 消费 payment-close，关闭支付单
│       ├── mq/RefundRequestConsumer.java         # 消费 refund-request，调用支付宝退款
│       ├── config/KafkaConsumerConfig.java       # 消费失败 3 次转 {topic}.DLQ
│       ├── scheduler/OutboxScheduler.java        # Outbox 消息定时发送
│       ├── scheduler/PaymentReconciliationScheduler.java # 双向对账（待支付正向 + 已支付反向）
│       ├── model/entity/Payment.java
│       └── mapper/PaymentMapper.java

│
├── sql/
│   ├── init.sql                # 全量克隆：nacos_config + cloud_mall 两库的结构与数据（mysqldump，2026-10-24；含 order_request / inventory_operation / seller_application）
│   └── migrations/             # 20260907_inventory_operation.sql、20260910_order_request.sql(+ .md)
│                               # 20260928_order_request_compensate.sql、README.md

└── docker/                     # Docker Compose（Nacos、Kafka、kafka-ui、Sentinel、MinIO、Meilisearch）+ nacos/conf
```

### 端口分配

| 服务 | 端口 |
|---|---|
| cloud-gateway | 8080 |
| cloud-auth | 8081 |
| cloud-product | 8082 |
| cloud-user | 8083 |
| cloud-cart | 8084 |
| cloud-order | 8085 |
| cloud-payment | 8086 |

---

## 角色体系

三种角色，通过 `user.role` 字段区分，JWT `role` claim 携带，Gateway 解析后注入 `X-User-Role` 请求头。

| 角色 | 常量 | 获取方式 | 核心权限 |
|---|---|---|---|
| 买家 | BUYER | 公开注册 | 浏览商品、购物车、下单、申请成为卖家 |
| 卖家 | SELLER | 买家申请→管理员审批 | 管理自己的商品（CRUD、提交审核）、管理分类 |
| 管理员 | ADMIN | 手动提升 | 商品审核、卖家申请审批、重置密码、管理用户 |

### JWT 用户上下文流转

```text
登录 → AuthServiceImpl 签发 JWT（sub=userId, username, role）
  → 前端存 localStorage，每次请求带 Authorization: Bearer <token>
  → Gateway 先剥离入站可信头，再解析 JWT → 注入 X-User-Id、X-Username、X-User-Role（role 缺省按 BUYER）
  → 路径含 /internal/ 的外部请求一律 403；网关注入 X-Internal-Token 供服务间调用

  → 下游服务 Controller 用 @RequestHeader 读取
  → Feign 调用时由 FeignRequestInterceptor 透传这三个头
```

Gateway 白名单（方法+路径精确匹配）之外的请求强制携带有效 JWT，解析失败返回 401；命中白名单时先剥离 X-User-Id / X-Username / X-User-Role / X-Internal-Token 再直接放行（不解析 JWT）。支付回调路径（POST /api/payment/notify/alipay、GET /api/payment/return/alipay）在白名单内。

放行或认证通过后，网关注入 `X-Internal-Token`（取 `cloud.internal.token`）；路径段含 `/internal/` 的请求直接返回 403，保证 `/orders/internal/status`、`/products/internal/batch` 这类接口只能由服务间调用。

---

## cloud-common 公共模块详解

被所有模块依赖，不独立部署（`spring-boot-maven-plugin` 跳过）。MyBatis-Plus、MySQL、Druid、Redisson、Meilisearch、feign-core 在 `cloud-common/pom.xml` 中是 optional 依赖，只在显式引入的模块中生效；`spring-boot-starter-data-redis` 不是 optional，所有模块（包括 gateway、auth）的 classpath 上都有 Redis。

### 核心类

| 类 | 职责 |
|---|---|
| `R<T>` | 统一响应体 `{ code, message, data }`，静态工厂方法 `ok()`/`fail()` |
| `ResultCode` | 错误码枚举：SUCCESS(200)、UNAUTHORIZED(401)、FORBIDDEN(403)、PRODUCT_NOT_EXIST(2001)、STOCK_INSUFFICIENT(2002)、ORDER_NOT_EXIST(3001) 等 |
| `BaseEntity` | 实体基类，`id`（雪花算法）、`createTime`、`updateTime`、`deleted`（逻辑删除），由 `AutoFillMetaObjectHandler` 自动填充 |
| `BusinessException` | 业务异常，携带 `code` + `message`，由 `GlobalExceptionHandler` 统一捕获返回 `R.fail()` |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，处理 BusinessException / BindException / MethodArgumentNotValidException / 兜底 Exception |
| `FeignExceptionHandler` | 独立 Feign 异常处理器，`@ConditionalOnClass` 保证无 Feign 依赖的模块可启动 |
| `OutboxMessage` | 消息发件箱实体，映射 outbox_message 表（status 0=PENDING/1=SENT/2=FAILED/3=SENDING） |
| `OutboxService` | 消息发件箱：saveMessage() 持久化 + pollPending()/claimMessage() 条件抢占（PENDING→SENDING）+ 超时回收 + 条件更新标记已发送/失败，退避 2^(n+1) 秒，由 cloud-order/cloud-payment 显式声明 Bean |
| `JwtUtil` | 固定 HS256 签发/解析/验证 JWT，token 有效期 2 小时，claims 含 userId、username、role |
| `InternalAuthFilter` | Servlet 过滤器：常量时间比对 `X-Internal-Token`，校验失败返回 403；未配置令牌时放行并记一次 WARN；跳过 `/actuator` |
| `InternalTokenFeignConfig` | Feign 请求拦截器：自动给服务间调用带上 `X-Internal-Token`（已有显式头时不覆盖） |
| `TokenBlacklistService` | JWT 黑名单：重置密码时按 username 吊销该用户存量 token；Redis 写失败时 fail-open 并告警 |
| `JwtBlacklistFilter` | 校验请求携带的 JWT 是否在黑名单中，命中则 401 |
| `CacheEvictListenerConfig` | 通过 Redis 广播订阅，让一个实例的缓存淘汰同步到其他实例的 L1 |
| `JwtSecretConfig` | 启动时校验 JWT_SECRET（缺失或 <32 字符直接失败）并注入 JwtUtil |
| `SystemConstants` | 所有常量：Redis Key 前缀、订单状态码、Kafka Topic 名、角色字符串 |
| `User` | 共享 User 实体（auth 和 user 服务共用），字段：username、password(BCrypt)、role、status |
| `UserMapper` | 共享 User 的 MyBatis-Plus BaseMapper |

### 配置类

| 类 | 职责 |
|---|---|
| `MyBatisPlusConfig` | 注册 `MybatisPlusInterceptor`，条件化（有 DataSource 才加载） |
| `AutoFillMetaObjectHandler` | INSERT 时自动填 `createTime`/`updateTime`/`deleted=0`；UPDATE 时填 `updateTime` |
| `RedisConfig` | 注册 `RedisTemplate<String, Object>`，Fastjson2 序列化并配置 AutoType 包白名单（防反序列化 RCE），支持类型自动推断 |
| `JacksonConfig` | `Long` → `String`，防止前端 JS 精度丢失雪花 ID |
| `DotenvEnvironmentPostProcessor` | Spring 启动前加载 `.env` 文件到 Environment，支持 `${VAR}` 引用 |
| `MinioConfig` | 创建 `MinioClient` Bean，自动初始化 Bucket 并设置公开读策略；`@ConditionalOnProperty` 确保只在配置了 `minio.endpoint` 的模块中加载 |
| `CaffeineConfig` | 创建 4 个 Caffeine Cache 实例（商品详情/热门/ID列表/购物车），并组装对应的 `TwoLevelCacheService` Bean；条件为 `@ConditionalOnClass({RedisTemplate, RedissonClient})`，因此只在 product / cart / order 生效 |
| `TwoLevelCacheService` | 通用二级缓存服务：L1(Caffeine 30s)→L2(Redis)→DB，Redis 故障自动降级；空值哨兵（60s）防穿透、Redisson 互斥回源防击穿、缓存淘汰经 Redis 广播同步到其他实例的 L1；`evictByPattern()` 用 SCAN 而非 KEYS |
| `CommonAutoConfiguration` | `@ComponentScan("org.cloudback.common")`，使 `FileService` 等 `@Service` 组件能被所有依赖 cloud-common 的模块自动发现 |
| `FileService` | MinIO 文件上传服务，封装 `putObject` 逻辑，按 prefix 分目录存储（avatar/product）；上传前校验扩展名白名单并嗅探文件魔数，Content-Type 由服务端固定，防存储型 XSS；`@ConditionalOnBean(MinioClient.class)` 确保仅在配置了 MinIO 的模块中加载 |
| `MeilisearchConfig` | 创建 `com.meilisearch.sdk.Client` Bean；`@ConditionalOnProperty(prefix = "meili", name = "host")` 确保仅在配置了 Meilisearch 的模块中加载 |
| `BloomFilterService` | Redisson 布隆过滤器，拦截不存在的商品 ID，防止缓存穿透（10 万容量，1% 误判率） |

---

## 二级缓存架构（Caffeine + Redis）

`TwoLevelCacheService` 是通用二级缓存抽象，为不同场景创建独立的 Caffeine 实例和 Redis Key 前缀，互不干扰。

```
读路径:  Caffeine(L1, 30s TTL) → Redis(L2, 5min TTL) → MySQL → 回填 L1+L2
写路径:  DB 更新 → evict(Redis → Caffeine) 双删，L1 短 TTL 兜底
```

| Bean 名称 | 使用场景 | Caffeine 最大条目 | Redis Key 前缀 | Redis TTL |
|---|---|---|---|---|
| `productDetailTwoLevel` | 商品详情 | 1000 | `cloud:product:detail:` | 300s |
| `hotProductsTwoLevel` | 热门商品 Top8 | 5 | `cloud:product:hot` | 300s |
| `productIdListTwoLevel` | 商品列表 ID 映射 | 200 | `cloud:product:ids:` | 120s（2min，库存变动不淘汰，结构变更按 pattern 淘汰） |
| `cartTwoLevel` | 购物车（独立 key） | 500 | `cloud:cart:cache:` | 300s |

### 各场景实现

**商品详情**：`getProductDetail()` 查询时先走二级缓存，miss 才查 DB。`updateProduct()` / `deleteProduct()` / `deductStock()` / `reviewProduct()` 调用后 evict 对应 key。

**热门商品**：`getHotProducts()` 的 DB loader 仍从 Redis ZSet 取 topId → 批量查 DB，结果被 L1/L2 缓存。L1 30s 过期后自动从 L2 或 DB 刷新，避免每次首页请求都查 ZSet+DB。

**商品列表（ID-Mapping）**：条件查询的组合太多（categoryId × keyword），无法直接缓存全量产品。改为缓存"查询条件 → 产品 ID 列表"的映射（`cloud:product:ids:{categoryId|all}`，Redis TTL 2min），拿到 ID 列表后手动分页，再逐个从 `productDetailTwoLevel` 获取产品详情。一份产品详情只存一份，避免缓存膨胀。新增/删除/审核等结构变更按 `cloud:product:ids:*` 模式淘汰；纯库存变动不淘汰，避免高频写放大。

**购物车**：源数据仍在 Redis Hash（`cloud:cart:{userId}`），读路径上包一层 Caffeine 缓存（`cloud:cart:cache:{userId}`），减少高频读操作的 Redis 网络往返。`addItem()` / `updateQuantity()` / `removeItem()` / `checkItem()` / `checkAllItems()` / `clearCart()` 在锁内完成后立即 evict 两个 key（先 Redis 后 Caffeine），不经事务回调。

---

## 基础架构详解

### 请求入口：cloud-gateway

**AuthGlobalFilter**（`order = -100`，最高优先级）：

```text
0. 路径段含 /internal/ → 直接 403（内部接口只允许服务间调用，网关注入 X-Internal-Token）
1. 路径去掉 /api 前缀后按「方法+路径」匹配白名单

   （POST /auth/login、POST /auth/register、GET /auth/health、
    POST /payment/notify/alipay、GET /payment/return/alipay、
    GET /products、GET /products/{id}（纯数字）、GET /products/hot、
    GET /products/search、GET /products/suggest、GET /categories）
   注：GET /auth/health 在白名单中，但 AuthController 没有对应接口，请求会返回 404
   → 命中：剥离 X-User-Id、X-Username、X-User-Role、X-Internal-Token 后直接放行（不解析 JWT）
2. 提取 Authorization: Bearer <token> 头
   → 缺失或格式不正确：返回 401 JSON
3. JwtUtil 解析
   → 过期或无效：返回 401 JSON；role 为空时按 BUYER 处理
4. 剥离请求方自带的 X-User-Id、X-Username、X-User-Role、X-Internal-Token
5. 注入 X-User-Id、X-Username、X-User-Role，并在配置了内部令牌时注入 X-Internal-Token
6. 放行到下游微服务

```

**网关路由**（`application.yml`）：

```yaml
spring.cloud.gateway.routes:
  - /api/auth/**             → lb://cloud-auth      (StripPrefix=1)
  - /api/users/**            → lb://cloud-user
  - /api/admin/users/**      → lb://cloud-user
  - /api/admin/applications/** → lb://cloud-user
  - /api/categories/**       → lb://cloud-product
  - /api/seller/products/**  → lb://cloud-product
  - /api/admin/products/**   → lb://cloud-product
  - /api/products/**         → lb://cloud-product
  - /api/cart/**             → lb://cloud-cart
  - /api/orders/**           → lb://cloud-order
  - /api/seller/orders/**    → lb://cloud-order
  - /api/payment/**          → lb://cloud-payment
```

此外 `spring.cloud.gateway.discovery.locator.enabled: true`（小写服务名），网关还会暴露 `/cloud-order/**`、`/cloud-product/**` 这类按服务名的路由。它们同样经过 `AuthGlobalFilter`（需要 JWT，`/internal/` 路径同样被 403 拦截），但不在上面的显式路由表中。gateway 还引入了 `spring-cloud-alibaba-sentinel-gateway`。

CORS 通过 `CorsConfig`（WebFlux `CorsWebFilter`）按来源白名单放行，白名单来自 `cloud.cors.allowed-origins`（逗号分隔）。CorsConfig 的兜底默认是 `localhost:5173` 与 `127.0.0.1:5173`，网关 yml 的默认值再补上 4173 的两个来源；实际部署用 `CORS_ALLOWED_ORIGINS` 覆盖。

### OpenFeign 远程调用

| 调用方 | Feign 接口 | 目标服务 | 接口 |
|---|---|---|---|
| cloud-cart | `ProductFeignClient` | cloud-product | `GET /products/{id}` |
| cloud-order | `CartFeignClient` | cloud-cart | `GET /cart/checked`、`DELETE /cart/items`、`DELETE /cart/items/{productId}` |
| cloud-order | `ProductFeignClient` | cloud-product | `POST /products/{id}/stock/deduct`、`POST /products/{id}/stock/restore`（均带 X-Internal-Token）、`GET /products/{id}`、`GET /products/seller?sellerId=` |
| cloud-order | `UserFeignClient` | cloud-user | `GET /users/me/addresses/{addressId}` |
| cloud-cart | `ProductFeignClient` | cloud-product | `POST /products/internal/batch`（批量查商品，实时价格/库存） |
| cloud-payment | `OrderFeignClient` | cloud-order | `GET /orders/internal/status?orderNo=`（发起支付前校验订单状态） |

**Feign 用户上下文透传**：`cloud-order` 中的 `FeignRequestInterceptor` 实现了 `feign.RequestInterceptor`，从当前 HTTP 请求（`RequestContextHolder`）提取 `X-User-Id`、`X-Username`、`X-User-Role`，自动注入到每个 Feign 请求头，确保下游服务能识别调用方身份。

**Feign 内部令牌**：`cloud-common` 的 `InternalTokenFeignConfig` 会自动给每个 Feign 调用带上 `X-Internal-Token`（调用方已显式传入时不覆盖），因此下游 `InternalAuthFilter` 能识别服务间调用。

### Kafka 异步消息

六条 Topic：order-create、payment-result、stock-restore、payment-close、refund-request、cart-clear；order、payment、cart 三个服务的消费者都配了 `KafkaConsumerConfig`（重试配置为 `FixedBackOff(1000ms, 3)`，重试耗尽后转入 `{topic}.DLQ`）。

```
cloud-order 下单成功（事务提交后）
  → Kafka "order-create" { orderId, orderNo, userId, totalAmount }
  → cloud-payment.OrderCreateConsumer 消费
    → 创建待支付记录（status=0）—— 不再自动标记支付成功
    → 等待用户手动发起支付宝页面支付

用户点击"立即支付" → POST /api/payment/alipay (JSON body: {orderNo, method})
  → 后端生成支付宝页面支付表单 → 前端新窗口打开 → 跳转支付宝
  → 用户完成付款 → 支付宝异步通知 POST /api/payment/notify/alipay
    → 验签 → 更新 payment.status=1 → Outbox → Kafka "payment-result"
    OR 浏览器回跳前端 /payment/result（默认 return_url）
    → 前端轮询 GET /api/payment/{orderNo} → 后端主动查询支付宝 → 确认已付 → 同上更新
    （GET /api/payment/return/alipay 只有在 ALIPAY_RETURN_URL 指向网关时才会被调用）
  → cloud-order.PaymentResultConsumer 消费 "payment-result"
    → UPDATE order_info.status = 已支付, pay_time = now()
    → 若发现订单已被取消：写 refund-request outbox（自动退款）

库存回补链路：
cloud-order（取消/超时/下单失败补偿）生产 "stock-restore"
  → cloud-order.StockRestoreConsumer 消费
    → Feign 调 cloud-product 回补库存；幂等由 cloud-product 的 inventory_operation 账本（EMPTY/RESERVED/RESTORED）在商品库事务内保证

资金收尾链路：
cloud-order 取消订单（事务内）生产 "payment-close"
  → cloud-payment.PaymentCloseConsumer 消费 → closePayment() 条件更新 0 → 3（CLOSED，幂等）
cloud-order.PaymentResultConsumer 发现"已支付但订单已取消" 生产 "refund-request"
  → cloud-payment.RefundRequestConsumer 消费 → 调支付宝退款（out_request_no=orderNo，幂等）
    → 条件更新 1 → 4（REFUNDED）

购物车清理链路：
cloud-order 下单成功后生产 "cart-clear"
  → cloud-cart.CartClearConsumer 消费 → 清空该用户购物车已下单的商品
（SystemConstants 中另有未被引用的历史常量 inventory-deduct）

```

---

## 完整业务流程

### 1. 注册与登录

```
注册 POST /api/auth/register（JSON body: username, password, nickname）
  1. 检查 username 唯一性（SELECT count FROM user WHERE username = ?）
  2. BCrypt.hashpw(password) 加密
  3. INSERT user (username, password, nickname, role='BUYER', status=1)
  4. 返回成功 → 前端跳转 /login

登录 POST /api/auth/login（JSON body: username, password）
  1. SELECT FROM user WHERE username = ?
  2. BCrypt.checkpw(password, user.password) 验证
  3. 检查 user.status != 0（未禁用）
  4. JwtUtil.createToken(userId, username, {role: role}) 签发 JWT（2h 有效）
  5. 返回 token 字符串
```

### 2. 热门商品（Redis ZSET 排行榜）

```
浏览商品 GET /api/products/{id}
  → ZINCRBY cloud:product:views {productId} 1
  → Redis 挂了：catch 异常，仅 log.warn，不影响详情页正常返回

首页 GET /api/products/hot
  1. 先查二级缓存(L1→L2)，命中直接返回
  2. 未命中：ZREVRANGE cloud:product:views 0 7（取浏览量 Top8 的商品 ID）
  3. selectBatchIds(ids) 批量查 MySQL 获取完整商品信息
  4. 按 ZSET 排名排序，写入 L1+L2 缓存
  → Redis 为空或挂了：降级查询 MySQL ORDER BY sales DESC LIMIT 8
```

### 3. 商品列表与搜索

```
GET /api/products?categoryId=&keyword=&page=&size=
GET /api/products/search?keyword=&categoryId=&page=&size=
GET /api/products/suggest?keyword=

有 keyword 时走 Meilisearch 全文搜索（中文分词 + 拼写纠错）：
  1. SearchService.search(keyword, categoryId, page, size)
     → Meilisearch 返回商品 ID 列表（按相关性排序）
  2. 逐个从商品详情缓存获取完整 Product 对象
  3. 直接返回分页结果

无 keyword 时走原有 ID-Mapping 缓存（详见二级缓存架构章节）：
  1. 先查 cloud:product:ids:{categoryId|all} 缓存（L1→L2）
  2. 未命中则查 DB：只 SELECT id，按条件过滤 + 按销量排序
     → 缓存 ID 列表，TTL 2min
  3. 手动分页截取当前页的 ID 子集
  4. 逐个从商品详情缓存获取完整 Product 对象

搜索建议 GET /api/products/suggest:
  → Meilisearch 搜索 → 返回商品名列表（自动补全用）

分类筛选逻辑：
  5. 若 categoryId 有值：递归收集该分类及其所有后代子分类的 ID
  6. 若 categoryId 为空：不筛选分类
  7. 只查 status = 1 的商品
```

### 4. 购物车（Redis Hash + Caffeine 读缓存）

```text
源数据（Redis Hash）
  Key:   cloud:cart:{userId}
  Field: productId（String）
  Value: CartItem JSON { productId, name, mainImage, price, quantity, checked }
  TTL:   7 天（addItem/updateQuantity 时续期）

读缓存（Caffeine + Redis，独立 key）
  Key:   cloud:cart:cache:{userId}        → 全量列表
         cloud:cart:cache:checked:{userId} → 已勾选列表
  L1:    Caffeine 30s，最大 500 条
  L2:    Redis String，5min TTL
  写操作后同步 evict 两个 key（先 Redis 后 Caffeine）

添加商品 POST /api/cart/items (JSON body: {productId, quantity})
  1. Feign → ProductService.getProductDetail(productId) 获取商品名/图/价
  2. Redisson RLock 加锁（cloud:cart:{userId}:lock, TTL 5s）
  3. 从 Redis Hash 检查是否已有该 productId
     → 已有：累加 quantity，更新 name/image/price（保证最新），并重新置 checked = true
     → 没有：创建新 CartItem，checked 默认 true
  4. HSET + EXPIRE 续期 7 天
  5. evict 读缓存（锁内，两个 key）→ 解锁

更新数量 PATCH /api/cart/items/{productId} (JSON body: {quantity})
  → Redisson RLock → HGET → 修改 quantity → HSET → evict 读缓存（锁内）→ 解锁

勾选 PATCH /api/cart/items/{productId}/check (JSON body: {checked})
  → Redisson RLock → HGET → 修改 checked → HSET → evict 读缓存（锁内）→ 解锁

全选/取消全选 PATCH /api/cart/items/check-all (JSON body: {checked})
  → Redisson RLock → HGETALL → 遍历所有商品修改 checked → 批量 HSET → evict 读缓存（锁内）→ 解锁

获取购物车 GET /api/cart
  → L1 → L2 → HGETALL → 回填 L1+L2

获取勾选商品 GET /api/cart/checked
  → L1 → L2 → HGETALL → filter(checked=true) → 回填 → 供订单服务下单时使用

清空 DELETE /api/cart/items → DEL cloud:cart:{userId} → evict 读缓存（锁内）
```

### 5. 下单（核心编排，先扣库存再写订单）

```text
POST /api/orders (JSON body: { addressId, remark, orderToken })

Step 0: 获取下单请求（GET /api/orders/token）
  - OrderRequestService.issue() 在 order_request 表创建/复用请求：
    token=UUID 主键、orderNo=雪花号（预分配）、status=READY、expires_at=+30 分钟
  - 同一用户仅允许一个未完成请求（uk_order_request_active_user 唯一键，多实例并发由 DuplicateKeyException 兜底）
  - 令牌不在 Redis；请求状态持久化在 MySQL

Step 1: 提交订单（OrderRequestService.create）
  - 以 SELECT ... FOR UPDATE SKIP LOCKED 锁定请求行
  - status == SUCCEEDED → 直接返回原订单（重试不重复下单）
  - status 不是 READY → 抛出对应状态提示（PROCESSING / COMPENSATING / EXPIRED 等）
  - 准备阶段（无库存副作用）：
    - Feign → CartFeignClient.getCheckedItems(userId)，为空 → BusinessException "购物车中没有已勾选的商品"
    - 逐个 Feign → ProductFeignClient.getProductDetail(productId) 重载商品（名称/主图/价格，仅 status=1）
    - 多卖家混单校验：勾选商品属于多个卖家 → 直接报错"购物车中包含多个卖家的商品，请分开下单"
      （否则会产生卖家无法整单发货的死锁订单）
    - 收货地址经 Feign 校验归属与必填字段
    - 校验类失败（错误码 42201）→ 请求保持 READY，可用同一令牌修正后重试
    - 订单快照（orderItems + 收货信息）写入 order_request.snapshot；status: READY → PROCESSING

Step 2: 执行（库存扣减在事务外，订单写入用 REQUIRES_NEW 短事务）
  - 逐项 Feign → ProductFeignClient.deductStock(productId, quantity, orderNo, X-Internal-Token)
    原子 SQL：UPDATE product SET stock = stock - ?, sales = sales + ?
              WHERE id = ? AND status = 1 AND stock >= ? AND deleted = 0
    库存账本 inventory_operation(order_no, product_id) 三态守卫（EMPTY/RESERVED/RESTORED），重复扣减不重复生效
  - 随后在一个 REQUIRES_NEW 事务内：
    INSERT order_info + order_item × N（每条保存商品名称/图片/价格快照）
    INSERT outbox_message（topic="order-create"），与订单同事务，保证消息不丢
    同事务内注册 afterCommit 回调（registerSynchronization 要求存在活动事务，因此不能把扣库存也放在事务外后遗漏它）
    status: PROCESSING → SUCCEEDED
  - 事务提交后（TransactionSynchronization.afterCommit）：
    - 写 cart-clear Outbox（可靠补偿）+ 逐商品 Feign → CartFeignClient.removeItem(userId, productId)
      （快路径直调失败时，由 cart 服务消费 cart-clear 幂等清理；两条路径都幂等）
    - Redis: 将 orderNo → 超时时间戳存入 ZSet cloud:order:timeout，供定时任务扫描
  - OutboxScheduler（每 5 秒）扫描 outbox_message → 发送 Kafka → 标记已发送/重试

Step 3（执行失败/结果不明）: 补偿
  - status → COMPENSATING，逐项 Feign → ProductFeignClient.restoreStock(productId, quantity, orderNo)
    回补 SQL 与扣减有意不对称：UPDATE product SET stock = stock + ?, sales = sales - ?
              WHERE id = ? AND sales >= ? AND deleted = 0（不带 status=1，允许对已下架商品回补）
  - 回补幂等由账本保证：重复回补只生效一次；先回补后迟到的扣减会被拒绝（记 RESTORED 墓碑）
  - 回补成功后强制取消可能已落库的待支付订单（cancelOrphanOrder），避免"库存已回滚但订单还在"的孤儿订单超卖
  - 全部回补成功 → status = FAILED（提示"下单未完成，库存已恢复，可以重新下单"）
  - 未完成 → 保持 COMPENSATING，由恢复调度每 30 秒重试
  - 补偿次数上限 10 次（order_request.compensate_count）：达到上限转 FAILED 并提示联系客服，
    避免永久失败的补偿无限占用该用户的下单槽位
  - 明确的失败原因（如库存不足）会透传给用户，而不是笼统提示
  - 返回 BusinessException "下单结果正在确认，请查询本次请求状态，勿重新下单"

Step 4: 客户端确认（GET /api/orders/requests/{token}）
  - 返回 { status, orderId, orderNo, message }
  - 前端在响应不确定时每 2 秒轮询（最多 20 次），SUCCEEDED 后跳转订单详情

恢复调度（OrderRequestService.recover，@Scheduled fixedDelay=30s）:
  - 扫描 PROCESSING 超过 5 分钟、COMPENSATING 超过 30 秒的请求（LIMIT 20），重放补偿
  - 服务重启后可继续；全部库存确认恢复后才置 FAILED
```

**关键设计**：请求状态持久化（`order_request`）+ 行锁（`SELECT ... FOR UPDATE SKIP LOCKED`）+ 唯一键（`uk_order_request_active_user`）保证同一请求在多实例下串行执行且只生效一次；先扣库存再写订单（防止超卖）；库存副作用由 `inventory_operation` 账本与商品库事务共同保证幂等；非 DB 副作用（清购物车项/Redis）通过 `TransactionSynchronization.afterCommit` 延迟到事务提交后执行；Kafka 消息通过 Outbox 模式保证可靠投递（PENDING→SENDING 条件抢占、2 分钟超时回收、2^n 秒退避、5 次后转 FAILED 可管理端重放）；消费者重试 3 次仍失败转 `{topic}.DLQ`，不再静默丢失。

### 6. 支付（支付宝沙箱页面支付）

```text
消费 order-create:
  1. 解析消息 { orderNo, userId, totalAmount }
  2. 幂等检查：SELECT FROM payment WHERE order_no = ?
     → 已有记录：跳过
  3. INSERT payment（status=0 待支付，payMethod=ALIPAY）—— 不自动标记成功

发起支付 POST /api/payment/alipay (JSON body: {orderNo, method}):
  1. 检查支付记录存在、归属正确、状态为待支付
  1.1 通过 OrderFeignClient 调 GET /orders/internal/status 校验订单状态：
      订单已取消 → 拒绝支付并同步关单；校验失败按 fail-closed 处理（order 服务不可用时不允许支付）

  2. 构造 AlipayTradePagePayRequest：
     - out_trade_no = orderNo
     - total_amount = 订单金额
     - subject = "CloudMall订单-{orderNo}"
     - product_code = FAST_INSTANT_TRADE_PAY
     - notify_url = 异步通知地址（网关 /api/payment/notify/alipay）
     - return_url = 同步回跳地址（ALIPAY_RETURN_URL，默认前端 /payment/result）+ `?orderNo=`
     - timeout_express = 30m
  3. alipayClient.pageExecute() → 返回支付表单 HTML
     （支付宝返回 TRADE_HAS_SUCCESS 时改为同步本地支付状态）
  4. 前端将 HTML 写入新窗口 → 表单自动提交 → 跳转支付宝收银台

用户在支付宝完成付款:

  同步回跳（浏览器）:
    默认 return_url 是前端 /payment/result，由前端轮询 GET /api/payment/{orderNo} 触发同步。
    仅当 ALIPAY_RETURN_URL 配置为网关地址时才走下面的后端回跳接口：
    GET /api/payment/return/alipay?out_trade_no=xxx&trade_no=xxx&...
    → 提取 orderNo → syncPaymentStatus()（主动向支付宝查询最新状态并同步）
      → 返回 TRADE_SUCCESS → 更新 payment.status=1, tradeNo, payTime
      → 写入 Outbox "payment-result" → 302 跳转前端支付结果页

  异步通知（服务端，需公网可达）:
    POST /api/payment/notify/alipay
    → 验签（AlipaySignature.rsaCheckV1, RSA2）+ 校验 app_id + 校验支付金额
    → trade_status == TRADE_SUCCESS 或 TRADE_FINISHED → 同上更新本地状态 → 返回 "success"
    → trade_status == TRADE_CLOSED → 标记支付失败（status=2）
    → 本地无支付记录、app_id 或金额不匹配、状态无法处理 → 返回 "failure"

cloud-order 消费 payment-result:
  1. status == "SUCCESS" 且 order 为 UNPAID
     → UPDATE order_info SET status = 1 (PAID), pay_time = now()
  2. 条件更新防止覆盖已取消/已完成的订单
  3. 反序列化失败的消息直接丢弃（无效格式），DB 异常抛出触发 Kafka 重试

查询支付记录 GET /api/payment/{orderNo}:
  若本地记录仍为待支付 → 30秒内只查一次支付宝 API（防频率限制）
  → 前端支付结果页依赖此行为轮询确认支付结果

**支付-取消竞态保护**：PaymentResultConsumer 在条件 UPDATE 返回 0 行时，检测订单是否已被取消。若已取消：log.error 告警、把订单 remark 写为 `[需退款] 支付成功但订单已取消, tradeNo=...`，**并写 refund-request Outbox 触发自动退款**；RefundRequestConsumer 调支付宝退款（`out_request_no=orderNo` 幂等），成功后条件更新 payment 1 → 4 (REFUNDED)。退款失败由 Kafka 按 `FixedBackOff(1000ms, 3)` 重试，耗尽后转入 `refund-request.DLQ`，需人工介入；Outbox 只负责保证消息可靠投递。标记支付成功时通过 Outbox 可靠发送 Kafka 通知。
```

**支付宝配置**：沙箱网关 `openapi-sandbox.dl.alipaydev.com`，RSA2 签名，公钥模式。密钥通过 `.env` 注入，单行 PKCS8 格式。应用私钥和支付宝公钥均不含 `----BEGIN/END----` 标记。

### 7. 取消订单

```text
PATCH /api/orders/{id}/cancel

  1. 查订单，校验 userId 归属（防越权）
  2. 检查 status == UNPAID（只有待支付可取消）
     → 否则 BusinessException "只能取消待支付的订单"
  3. UPDATE order_info SET status = 4 (CANCELLED)
  4. 查询订单明细 (order_item)
  5. 逐项写入 stock-restore Outbox 消息（与状态更新同一事务）；由 StockRestoreConsumer 消费后调用商品服务回补，inventory_operation 账本保证重复回补只生效一次
  6. 同事务写入 payment-close Outbox → payment 端 closePayment() 条件更新支付单 0 → 3 (CLOSED)，
     避免"订单已取消但支付单还能发起支付"

```

### 8. 申请成为卖家

```text
买家 POST /api/users/me/seller-applications
  1. 检查 role == BUYER（只有买家可申请）
  2. 检查是否已有 PENDING 的申请记录（防止重复提交）
  3. INSERT seller_application (userId, status='PENDING')

管理员 GET /api/admin/applications
  → 列出所有 PENDING 的申请

管理员 PATCH /api/admin/applications/{id} (JSON body: {approved})
  - 通过：application.status = APPROVED，user.role = SELLER
  - 拒绝：application.status = REJECTED
  - 通过后需重新登录获取新 JWT（旧 token 中 role 仍为 BUYER）
```

### 9. 商品审核

```text
卖家添加商品 POST /api/seller/products
  → SELLER：status = 2 (PENDING，待审核)
  → ADMIN：status = 1（直接上架）

卖家编辑商品 PUT /api/seller/products/{id}
  → SELLER 编辑后 status 重新设为 2 (PENDING)
  → 卖家只能编辑自己的商品（sellerId 校验）

管理员审核 GET /api/admin/products/pending
  → 列出所有 status = 2 的商品

管理员审批 PATCH /api/admin/products/{id}/review (JSON body: {approved})
  - 通过：status = 1（上架，可被公开查询到）
  - 拒绝：status = 0（下架）
  - 只能审批 status = 2 的商品
```

### 10. 用户管理（管理员）

```text
GET /api/admin/users
  → 返回所有用户列表，密码字段置 null 脱敏

PATCH /api/admin/users/{id}/password (JSON body: {newPassword})
  → BCrypt 加密新密码 → 更新指定用户密码
```

### 11. 卖家订单管理与发货

```text
卖家查看订单 GET /api/seller/orders?page=&size=
  1. Feign → ProductFeignClient.getProductsBySellerId(sellerId) 获取卖家商品 ID 列表
  2. 按 order_item 分组（GROUP BY order_id）统计并分页，而不是先取全部订单再内存分页
  3. SELECT * FROM order_info WHERE id IN (...) 取订单主体
  4. 每个订单只回填属于该卖家的 orderItems 明细（不暴露同单其他卖家的商品行）
  → 返回包含卖家商品的订单列表（按创建时间降序）

卖家发货 PATCH /api/seller/orders/{id}/ship
  1. 查订单，校验 status == PAID（只能对已支付订单发货）
  2. Feign 校验该订单的全部商品均属于此卖家（整单发货；接口不校验角色，靠这条归属校验兜底）
  3. UPDATE order_info SET status = 2 (SHIPPED)

```

### 12. 确认收货

```text
买家确认收货 PATCH /api/orders/{id}/receive
  1. 查订单，校验 userId 归属（防越权）
  2. 检查 status == SHIPPED（只能确认已发货订单）
  3. UPDATE order_info SET status = 3 (COMPLETED)
```

### 13. 订单超时自动取消（双层保障）

```text
第一层 — Redis ZSet 高频扫描（@Scheduled fixedRate=1000ms）:
  1. ZRANGEBYSCORE cloud:order:timeout 0 NOW → 取过期订单（每次最多 20 条）
  2. 逐个调用 cancelOrderByNo()，**成功后才 ZREM 移除**；失败留在 ZSet 等下一轮重试
  3. 单个失败不中断，继续处理下一个

第二层 — DB 兜底扫描（@Scheduled fixedRate=300000ms）:
  1. SELECT * FROM order_info WHERE status=0 AND create_time < NOW() - 30min LIMIT 20
  2. 逐个取消并回滚库存
  3. 防止 Redis ZSet 数据丢失（重启/故障恢复后依然能取消超时订单）

取消订单：
  a. UPDATE order_info SET status = 4 (CANCELLED)
  b. 查询 order_item 明细
  c. 逐项写入 stock-restore Outbox（与取消同事务），由消费者异步回补并以账本保证幂等与不重复回补
  d. REMOVE Redis ZSet 中的 orderNo
```

### 14. 管理员看板

当前未实现：仓库中不存在 /api/admin/stats 控制器，cloud-user 无 Feign 客户端，网关未路由该路径。

### 15. 图片上传（MinIO 对象存储）

```text
前端选择文件
  → FormData 封装，POST /api/products/upload （商品图，SELLER/ADMIN）或 POST /api/users/me/avatar （头像）
    → Controller 接收 MultipartFile，先校验扩展名白名单并嗅探文件魔数（防伪装脚本），
      Content-Type 由服务端固定，不信任客户端提交值
    → 生成 UUID 文件名
    → MinioClient.putObject(bucket, objectName, inputStream, contentType)

    → 返回可公开访问的 URL：http://{VM_HOST}:9000/{bucket}/{objectName}
  → 前端将 URL 存入表单（商品 mainImage / 用户 avatar）→ 提交保存
```

**MinIO 配置机制**：
- `MinioConfig` 在 `cloud-common` 中定义，通过 `AutoConfiguration.imports` 自动注册
- `@ConditionalOnProperty(prefix = "minio", name = "endpoint")`：仅在配置了 `minio.endpoint` 的模块中创建 `MinioClient` Bean（cloud-product、cloud-user）
- cloud-gateway（无 minio 配置）不会创建，无冲突
- Bucket 首次启动时自动创建，并设置 ReadOnly 的公开读策略

**Gateway 文件上传支持**：
- `spring.codec.max-in-memory-size: 10MB`（Netty 缓冲限制）
- cloud-product / cloud-user 配置 `spring.servlet.multipart.max-file-size: 10MB`

**文件命名**：`{prefix}/{UUID}{ext}`，如 `product/a1b2c3.jpg`、`avatar/d4e5f6.jpg`
**支付对账（`PaymentReconciliationScheduler`）**：支付宝异步通知是主确认路径，浏览器回跳只是便利路径 —— 回跳中断、网络断开或支付服务重启时，支付在支付宝侧仍可能已完成，因此 cloud-payment 还有一层服务端对账：启动 10 秒后首跑，此后每 30 秒一轮；正向对账扫 `status=0` 且 `last_sync_time` 为空或早于 30 秒前的待支付记录（每轮最多 20 条），先用 `last_sync_time` 条件更新原子抢占，再向支付宝查询；反向对账复查本地已支付记录的订单状态 —— 订单仍未支付则重发 `payment-result`，订单已取消则走 `refund-request` 自动退款。

已支付订单在页面仍显示未支付时：等中间件健康后重启 payment，等一轮对账再刷新订单列表，**不要**直接改 `order_info` / `payment` 表来"补"一笔支付。

---

## 搜索引擎（Meilisearch）

### 为什么选择 Meilisearch

| 对比 | MySQL LIKE | Elasticsearch | Meilisearch |
|------|-----------|---------------|-------------|
| 内存 | — | 1-2 GB | 100-200 MB |
| 中文分词 | 不支持 | 需 IK 插件 | 内置 |
| 拼写纠错 | 不支持 | 需额外配置 | 内置 typo tolerance |
| 相关性排序 | 不支持 | 复杂 DSL | 内置智能排序 |
| 部署 | — | 集群 + JVM 调优 | 单二进制 `docker run` |
| 搜索建议 | 不支持 | 需 suggest API | 开箱即用 |

### 架构

```
商品 CRUD → ProductServiceImpl 同步索引
          → searchService.indexProduct(product)
          → searchService.deleteProduct(productId)

启动时 → initSearchIndex() 全量重建索引（只索引 status=1 的上架商品）

用户搜索 → Controller /products/search
         → SearchService.search() → Meilisearch
         → 返回 ID 列表 → 从缓存/DB 加载完整商品
```

### 索引同步时机

| 操作 | 同步动作 |
|------|---------|
| 添加商品（ADMIN / status=1） | `indexProduct` |
| 添加商品（SELLER / status=2） | 不索引（待审核） |
| 编辑商品 → status=1 | `indexProduct` |
| 编辑商品 → status≠1 | `deleteProduct` |
| 删除商品 | `deleteProduct` |
| 审核通过（status→1） | `indexProduct` |
| 审核拒绝（status→0） | `deleteProduct` |
| 启动时全量重建 | 遍历所有 status=1 商品逐个索引 |

### 配置

**`cloud-common/MeilisearchConfig`**：`@ConditionalOnProperty(prefix = "meili", name = "host")` 保证仅在配置了 `meili.host` 的模块（cloud-product）中创建 `Client` Bean。

**`cloud-product/SearchServiceImpl`**：`@ConditionalOnProperty(prefix = "meili", name = "host")` 与 MeilisearchConfig 同条件，避免 Bean 加载时序问题。

### 容错

- Meilisearch 不可用时：搜索降级为 `Collections.emptyList()`，不阻断业务流程
- 启动重建索引失败：仅 `log.warn`，不影响应用启动
- 索引同步失败：`log.error`，不中断主流程（下次启动时会全量重建）

---

## 完整 API 参考

所有接口通过 Gateway 8080 访问，路径前缀 `/api`。

| 方法 | 路径 | 服务 | 认证 | 说明 |
|---|---|---|---|---|
| POST | `/api/auth/register` | auth | 否 | 注册（JSON body: username, password, nickname） |
| POST | `/api/auth/login` | auth | 否 | 登录，返回 JWT（JSON body: username, password） |
| GET | `/api/users/me` | user | 是 | 获取个人信息 |
| PATCH | `/api/users/me` | user | 是 | 修改个人信息（JSON body） |
| POST | `/api/users/me/avatar` | user | 是 | 上传头像到 MinIO |
| GET | `/api/users/me/addresses` | user | 是 | 地址列表 |
| GET | `/api/users/me/addresses/{id}` | user | 是 | 地址详情 |
| POST | `/api/users/me/addresses` | user | 是 | 添加地址（JSON body） |
| PUT | `/api/users/me/addresses/{id}` | user | 是 | 修改地址（JSON body，id 在 URL） |
| DELETE | `/api/users/me/addresses/{id}` | user | 是 | 删除地址 |
| POST | `/api/users/me/seller-applications` | user | 是 | 申请卖家 |
| GET | `/api/admin/users` | user | 是 | 管理员-用户列表 |
| PATCH | `/api/admin/users/{id}/password` | user | 是 | 管理员-重置密码（JSON body: newPassword） |
| GET | `/api/admin/applications` | user | 是 | 管理员-卖家申请列表 |
| PATCH | `/api/admin/applications/{id}` | user | 是 | 管理员-审批卖家申请（JSON body: approved） |
| GET | `/api/categories` | product | 否 | 分类树 |
| POST | `/api/categories` | product | 是 | 添加分类（JSON body，SELLER/ADMIN） |
| PUT | `/api/categories/{id}` | product | 是 | 修改分类（JSON body，id 在 URL） |
| DELETE | `/api/categories/{id}` | product | 是 | 删除分类 |
| GET | `/api/products/hot` | product | 否 | 热门商品 Top8 |
| GET | `/api/products` | product | 否 | 商品列表(分页/搜索/分类) |
| GET | `/api/products/{id}` | product | 否 | 商品详情（公开白名单接口） |
| GET | `/api/products/search` | product | 否 | Meilisearch 全文搜索（支持中文分词/拼写纠错） |
| GET | `/api/products/suggest` | product | 否 | 搜索建议/自动补全（query: keyword，返回商品名列表） |
| GET | `/api/seller/products/mine` | product | 是 | 卖家自己的商品 |
| POST | `/api/seller/products` | product | 是 | 添加商品（JSON body，SELLER/ADMIN） |
| PUT | `/api/seller/products/{id}` | product | 是 | 修改商品（JSON body，id 在 URL） |
| DELETE | `/api/seller/products/{id}` | product | 是 | 删除商品 |
| GET | `/api/admin/products/pending` | product | 是(ADMIN) | 管理员-待审核商品（query: page=1, size=20） |
| PATCH | `/api/admin/products/{id}/review` | product | 是 | 管理员-审批商品（JSON body: approved） |
| PUT | `/api/products/admin/{id}/status` | product | 是(ADMIN) | 管理员上下架商品（JSON body: status） |
| POST | `/api/products/upload` | product | 是 | 上传商品图片到 MinIO |
| POST | `/api/products/{id}/stock/deduct` | product | 内部 | Feign 原子扣库存（需 X-Internal-Token，WHERE stock >= ? AND status = 1） |
| POST | `/api/products/{id}/stock/restore` | product | 内部 | Feign 回补库存（需 X-Internal-Token，取消/补偿时调用） |
| POST | `/api/products/internal/batch` | product | 内部 | 批量查商品（≤100 个，供购物车刷新实时价格/库存） |
| GET | `/api/cart` | cart | 是 | 购物车列表 |
| GET | `/api/cart/checked` | cart | 是 | 已勾选商品列表（供下单 Feign 调用） |
| POST | `/api/cart/items` | cart | 是 | 加入购物车（JSON body: productId, quantity） |
| PATCH | `/api/cart/items/{productId}` | cart | 是 | 修改数量（JSON body: quantity） |
| PATCH | `/api/cart/items/{productId}/check` | cart | 是 | 勾选/取消（JSON body: checked） |
| PATCH | `/api/cart/items/check-all` | cart | 是 | 全选/取消全选（JSON body: checked） |
| DELETE | `/api/cart/items/{productId}` | cart | 是 | 删除单项 |
| DELETE | `/api/cart/items` | cart | 是 | 清空购物车 |
| GET | `/api/orders/token` | order | 是 | 获取/复用当前用户未完成的下单请求令牌（order_request，30 分钟有效） |
| GET | `/api/orders/requests/{token}` | order | 是 | 查询下单请求状态（READY/PROCESSING/SUCCEEDED/COMPENSATING/FAILED/EXPIRED） |
| POST | `/api/orders` | order | 是 | 创建订单（JSON body: addressId, remark, orderToken） |
| GET | `/api/orders` | order | 是 | 订单列表（query: page, size） |
| GET | `/api/orders/{id}` | order | 是 | 订单详情（含订单明细 orderItems） |
| GET | `/api/orders/by-no/{orderNo}` | order | 是 | 按订单号查询当前用户订单（支付结果页核对） |
| GET | `/api/orders/internal/status` | order | 内部 | 按订单号查订单状态（支付服务发起支付前校验，需 X-Internal-Token） |
| PATCH | `/api/orders/{id}/cancel` | order | 是 | 取消订单（同时回滚库存） |
| PATCH | `/api/orders/{id}/receive` | order | 是 | 确认收货（已发货 → 已完成） |
| GET | `/api/seller/orders` | order | 是 | 卖家查看包含自己商品的订单（接口本身不校验角色，只按 X-User-Id 过滤） |
| PATCH | `/api/seller/orders/{id}/ship` | order | 是 | 卖家发货（已支付 → 已发货）；不校验角色，要求订单内所有商品都属于当前用户 |
| GET | `/api/orders/admin/outbox/failed` | order | 是(ADMIN) | 失败 Outbox 消息列表（limit 默认 50） |
| POST | `/api/orders/admin/outbox/{id}/retry` | order | 是(ADMIN) | 重放失败 Outbox 消息 |
| GET | `/api/products/seller?sellerId=` | product | 是 | 供 Feign 按卖家 ID 查商品（返回商品 ID 等）；控制器校验 X-Internal-Token，但网关注入该头后任何已登录用户都能调用 |
| POST | `/api/payment/alipay` | payment | 是 | 发起支付（JSON body: orderNo, method），返回支付表单 HTML |
| GET | `/api/payment/{orderNo}` | payment | 是 | 查询支付记录（待支付时自动同步支付宝状态） |
| POST | `/api/payment/{orderNo}/sync` | payment | 是 | 主动向支付宝查询并同步支付状态 |
| POST | `/api/payment/notify/alipay` | payment | 否 | 支付宝异步通知回调 |
| GET | `/api/payment/return/alipay` | payment | 否 | 支付宝同步回跳（查支付→跳前端）；默认配置下不会被调用，且同步时不做用户归属校验 |

---

## 数据库

### ER 概要

```
user ──< address
user ──< seller_application
user ──< order_info
category ──< category (parent_id 自引用树形)
category ──< product
product ──< order_item
order_info ──< order_item
order_info ──< payment
```

### 表结构

| 表 | 核心字段 | 索引 |
|---|---|---|
| `user` | id, username(UK), password, role, status | uk_username, uk_phone |
| `address` | id, user_id, receiver_name, phone, province, city, district, detail, is_default | idx_user_id |
| `category` | id, parent_id, name, sort | idx_parent_id |
| `product` | id, category_id, seller_id, name, price, stock, sales, status | idx_category_id, idx_status, idx_seller_id |
| `seller_application` | id, user_id, status(PENDING/APPROVED/REJECTED) | idx_user_id |
| `order_info` | id, order_no(UK), user_id, total_amount, status(0-4), receiver_* | uk_order_no, idx_user_id, idx_status |
| `order_item` | id, order_id, order_no, product_id, product_name, price, quantity, total_amount | idx_order_id, idx_order_no |
| `payment` | id, order_no, user_id, amount, pay_method, trade_no, status(0=待支付/1=已支付/2=支付失败/3=已关闭/4=已退款), last_sync_time | idx_order_no, idx_user_id |
| `outbox_message` | id, topic, message_key, payload, status(0=PENDING/1=SENT/2=FAILED/3=SENDING), retry_count, max_retries, next_retry_at | idx_status_retry |
| `order_request` | token(PK, UUID), user_id, active_user_id(UK), order_no(UK), status(READY/PROCESSING/SUCCEEDED/COMPENSATING/FAILED/EXPIRED), snapshot, failure_reason, expires_at, compensate_count(上限 10) | uk_order_request_active_user, uk_order_request_order_no, idx_order_request_recovery |
| `inventory_operation` | order_no + product_id(复合 PK), quantity, state(EMPTY/RESERVED/RESTORED) | 复合主键即唯一键 |

业务表（user/address/category/product/order_info/order_item/payment）包含 `create_time` / `update_time` 与逻辑删除；`outbox_message`、`order_request`、`inventory_operation` 无逻辑删除；`order_request` 主键为 UUID token，`inventory_operation` 为复合主键。`payment.last_sync_time` 记录上次主动查询支付宝的时间，用于频率限制（30 秒内不重复查询）。

### 订单状态流转

```
0-待支付 → 1-已支付 → 2-已发货 → 3-已完成
   │              │         │         ↑
   │              │         └──── 买家确认收货
   │              │
   │              └── 卖家发货 (SellerOrderController)
   │
   └────────── 4-已取消（手动取消 / 定时任务超时自动取消）
```

- **卖家发货**：`PATCH /seller/orders/{id}/ship`，仅已支付(1)可操作
- **买家确认收货**：`PATCH /orders/{id}/receive`，仅已发货(2)可操作
- **超时自动取消**：`OrderTimeoutScheduler` 每秒扫描 Redis ZSet（每批 20 条），另有每 5 分钟的 DB 兜底扫描（30 分钟阈值），改为已取消并触发库存回补

### 存量库检查（payment）

新库直接用 `sql/init.sql`（结构与数据全量克隆）即可，无需额外迁移；**存量库**升级前先确认 `payment.last_sync_time` 与 `payment.order_no` 唯一索引：

```sql
SHOW COLUMNS FROM payment LIKE 'last_sync_time';
SHOW INDEX FROM payment WHERE Column_name = 'order_no';
SELECT order_no, COUNT(*) FROM payment GROUP BY order_no HAVING COUNT(*) > 1;
```

`order_no` 应为唯一索引（`uk_order_no`），保证每个订单只有一条支付记录。存量库若缺这一列或唯一约束，按该库实际情况单独评审迁移脚本 —— **不要把整份初始化脚本当生产迁移执行**。

---

## Redis 数据结构

| Key | 类型 | 用途 |
|---|---|---|
| `cloud:cart:{userId}` | Hash | 购物车源数据，field=productId, value=CartItem JSON，TTL 7 天 |
| `cloud:product:views` | ZSet | 商品浏览量排行榜，score=浏览次数，member=productId |
| `cloud:product:detail:{id}` | String | 商品详情 L2 缓存，TTL 5 分钟 |
| `cloud:product:hot` | String | 热门商品 Top8 列表 L2 缓存，TTL 5 分钟 |
| `cloud:product:ids:{categoryId\|all}` | String | 商品列表 ID 映射 L2 缓存，TTL 2 分钟（写操作不主动淘汰，短 TTL 兜底） |
| `cloud:cart:cache:{userId}` | String | 购物车 L2 缓存，TTL 5 分钟 |
| `cloud:cart:cache:checked:{userId}` | String | 已勾选商品 L2 缓存，TTL 5 分钟 |
| `cloud:token:blacklist:{username}` | String | Token 黑名单：重置密码时吊销该用户存量 token（JwtBlacklistFilter 校验） |
| `cloud:login:fail:{username}` | String | 登录失败计数，5 次后锁定 15 分钟（滑动窗口） |
| `cloud:cache:evict` | Pub/Sub | 缓存淘汰广播，跨实例失效 L1 |
| `cloud:cache:lock:{key}` | String（Redisson） | 缓存互斥回源锁，防缓存击穿 |
| `cloud:order:timeout` | ZSet | 超时订单追踪，score=过期时间戳，OrderTimeoutScheduler 每秒扫描 |
| `cloud:order:timeout:lock` | String（Redisson RLock） | 超时扫描分布式锁（tryLock(0, 30s)），防止多实例重复执行 |
| `cloud:order:scan:lock` | String（Redisson RLock） | DB 兜底扫描分布式锁（tryLock(0, 30s)） |

下单幂等令牌不在 Redis：`order_request` 表持久化请求状态（UUID token 主键、30 分钟过期、`active_user_id` 唯一键约束同一用户仅一个活跃请求）；Outbox 投递不加锁，靠 DB 条件 UPDATE 抢占（PENDING→SENDING）。

**购物车并发保护**：`addItem`/`updateQuantity`/`removeItem`/`checkItem`/`checkAllItems`/`clearCart` 均使用 Redisson `RLock`（key: `cloud:cart:{userId}:lock`，leaseTime 5s，无看门狗续期），防止同一用户并发请求导致数据覆盖。

---

## 安全设计

- **密码**：BCrypt 加密存储（Hutool），永不返回给前端
- **JWT**：固定 HS256 签名，2 小时过期，Gateway 全局校验；重置密码时把该用户存量 token 写入黑名单
- **登录防爆破**：同一用户名连续失败 5 次锁定 15 分钟（滑动窗口）；用户名不存在与密码错误统一提示，避免账号枚举
- **内部接口**：`InternalAuthFilter` 常量时间比对 `X-Internal-Token`，网关对含 `/internal/` 的路径一律 403
- **用户隔离**：通过 `X-User-Id` 头实现，地址/订单/商品归属校验防止越权
- **角色控制**：`X-User-Role` 头 + Service 层权限检查
- **上传校验**：扩展名白名单 + 文件魔数嗅探 + 服务端固定 Content-Type，防存储型 XSS
- **缓存反序列化**：Fastjson2 AutoType 包白名单，防反序列化 RCE
- **CORS**：按来源白名单放行（`cloud.cors.allowed-origins`），不再是通配
- **接口文档**：Knife4j 默认关闭（`KNIFE4J_ENABLE=false`），生产不暴露
- **逻辑删除**：业务表（user/address/category/product/order_info/order_item/payment）使用 MyBatis-Plus `@TableLogic`；outbox_message、order_request、inventory_operation 无逻辑删除
- **雪花 ID**：JacksonConfig 将 Long 序列化为 String，防止前端精度丢失

## 常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| Gateway 连 localhost 而非 VM IP | `.env` 未加载 | 确认启动时的工作目录在 `.env` 所在目录或其下 5 层以内，并且没有同名系统环境变量覆盖 |
| Nacos 9848 端口连不上 | 防火墙未放行 gRPC 端口 | `sudo ufw allow 9848/tcp` |
| Nacos 客户端解析到 localhost | Docker 通告了容器内网 IP | 服务端在 `docker/nacos/conf/application.properties` 设置 `nacos.inetutils.ip-address` 为虚拟机 IP；客户端可按需设置 `spring.cloud.nacos.discovery.ip`（当前 yml 未配置） |
| Redis 连接被拒 | Redis 只监听 127.0.0.1 | 改 `bind 0.0.0.0` 并设 `requirepass` |
| 执行 init.sql 后原有数据被清空 | 2026-10-24 版是全量克隆脚本，内含 DROP TABLE IF EXISTS | 有数据的库不要执行本脚本；只想要空表结构时注释掉 DROP 与 INSERT 段 |
| 执行 init.sql 后 Nacos 配置/账号丢失 | 脚本 [1] 段重建了 nacos_config | 执行前注释掉该段，或用执行前的备份恢复 |
| cloud-product 连不上 Redis | product 的 yml 未配置 spring.data.redis，默认连 localhost | 在 product 的 application.yml 中补充与 cart/order 相同的 Redis 配置 |
| 审批显示"申请不存在" | JS 精度丢失 Snowflake ID | JacksonConfig 已处理 Long→String |
| 新增商品报 JSON 错误 | `images` 字段为空字符串 `""` | 前端发 `null` 或有效 JSON 数组 |
| 改角色后仍报权限错误 | JWT 仍是旧角色 | 退出重新登录获取新 token |
| 订单创建报 product_name 无默认值 | 购物车数据缺少商品名 | order 服务已加 Feign 回退查询商品详情 |
| 选父分类查不到商品 | 商品只挂叶子分类 | product 服务已改为递归收集子分类 ID |
| 图片上传失败 | MinIO 容器未启动 | `cd docker && docker compose up -d minio` |
| 上传图片后页面不显示 | MinIO Bucket 无公开读策略 | MinioConfig 首次启动自动创建 Bucket 并设置策略 |
| MinIO 启动报 invalid hostname | endpoint 含 `host:port` 格式 | MinioConfig 自动拆分 host 和 port 传入 SDK |
| 修改商品/头像后刷新才看到变更 | 前端无自动刷新 | 前端已加 30s 静默轮询 |
| 支付后订单仍显示未支付 | 异步通知无法到达本地（localhost 不可公网访问） | 查询支付记录时自动触发主动查询支付宝 API 同步状态 |
| 订单超时未自动取消 | Redis ZSet 数据因重启丢失 | 已实现 DB 兜底扫描（每 5 分钟），双重保障 |
| 无 Feign 的模块启动报 FeignException | GlobalExceptionHandler 硬引用 Feign | 已拆分为 FeignExceptionHandler + @ConditionalOnClass |
| 编译报 R.ok Long 参数不匹配 | R.ok(T, Integer) 不接受 long | total 参数使用 (int) 强制转换 |
| 支付点击后跳转 Alipay 错误页 | return_url 被支付宝拒绝 | 使用前端地址（localhost:4173）作为回跳，不经过网关 |
| 支付宝私钥配置后启动报错 | PKCS8 密钥含换行 | 将密钥合并为单行，去除 BEGIN/END 标记 |
| 支付同步回跳 404 | 前端预览端口非 5173 | 配置 ALIPAY_RETURN_URL 为实际端口（如 :4173） |
| Meilisearch 索引为空 | 没有上架商品（status=1） | 添加商品并审核通过，重启 cloud-product 自动索引 |
| 搜索无结果 | 关键词无匹配或索引未重建 | 访问 `http://VM:7700` 用 Meilisearch web UI 验证索引数据 |
| Meilisearch 连接超时 | 容器未启动或防火墙未放行 | `docker compose up -d meilisearch`，`sudo ufw allow 7700/tcp` |
| Meilisearch web UI 提示 Authorization header missing | API key 输入后页面未正确携带 | 刷新页面重试，或使用浏览器无痕窗口 |
| 下游服务报"非法的内部请求"403 | `CLOUD_INTERNAL_TOKEN` 在各服务不一致或未配置 | 所有服务与网关用同一个值，写入 `.env` 后重启 |
| 日志反复出现"内部认证过滤器处于放行状态" | 未配置 `CLOUD_INTERNAL_TOKEN`，内部接口无保护 | 配置该变量；留空仅用于本地调试 |
| 登录提示"失败次数过多，请15分钟后再试" | 连续失败 5 次触发锁定 | 等待 15 分钟，或清理 `cloud:login:fail:{username}` |
| 下单报"购物车中包含多个卖家的商品" | 一次结算勾选了多个卖家的商品 | 分开下单；当前设计不支持跨卖家混单 |
| 支付被拒绝且提示订单已取消 | 发起支付前会校验订单状态（fail-closed） | 属预期行为；order 服务不可用时支付会被拦截，稍后重试 |
| 容器启动即失败并提示 must set in .env | docker-compose 密码改为必填变量 | 在 `docker/.env` 补齐后 `docker compose config` 校验 |
| 订单已取消但支付单仍可支付 | payment-close 未消费 | 确认 Kafka 已创建 `payment-close` 主题且 payment 服务在运行 |
| 退款未到账 | refund-request 重试超限进了 `.DLQ` | 检查 `cloud-payment` 日志与 `refund-request.DLQ`，必要时人工处理 |

---

## Claude Code 开发配置

### .env 安全保护

项目通过 Skill + PreToolUse Hook 双层机制保护 `.env` 文件不被 AI 读取或修改：

```text
CloudBack/.claude/
├── skills/
│   └── env-security.md         # Skill：指示 Claude 只读 .env.example，忽略 .env
├── hooks/
│   └── block-env.sh            # Hook：硬性拦截对 .env 文件的 Read/Write/Edit/Glob/Grep/Bash
└── settings.json               # 注册 PreToolUse Hook
```

- `.env.example`：配置模板（仅含 key 和注释，不含真实密钥），已纳入 Git
- `docker/.env.example`：Docker Compose 环境变量模板
- `.env` 和 `docker/.env`：真实密钥文件，已被 `.gitignore` 忽略
- CloudFront 的 `.claude/settings.json` 通过相对路径引用此 Hook

### 打印日志安全

- `AlipayProperties.privateKey` 使用 `@ToString.Exclude`（Lombok），防止日志泄露 RSA 私钥
- JWT 密钥在启动时校验（长度 <32 字符或为空则立即报错而非运行时 500）

---

## 稳定性改进记录（2026-06 起，部分条目已按 2026-09 的 order_request 重构更新）

### 数据正确性
- **分页 total 修复**：Meilisearch 搜索结果总数改用 `rawSearch` 解析 `estimatedTotalHits`，修复分页仅显示一页的 Bug
- **购物车数量校验**：`addItem`/`updateQuantity` 增加正数/上限（999）校验
- **下单令牌优化**：改为 `order_request` 持久化请求状态机（2026-09 重构，取代原 Redis 方案）

### 并发安全
- **定时任务分布式锁**：`OrderTimeoutScheduler` 使用 Redisson `tryLock(0, 30s)`（timeout/scan 两把锁）防止多实例重复执行
- **卖家申请防重复**：`applySeller` 增加 `DuplicateKeyException` 捕获；`processApplication` 添加 `@Transactional` + 条件 UPDATE（`status = PENDING`）
- **商品操作事务**：`addProduct`/`updateProduct`/`reviewProduct` 添加 `@Transactional`

### NPE 防御
- `cancelOrder`/`cancelOrderByNo`/`shipOrder`/`receiveOrder` 中的状态比较改为常量前置（`SystemConstants.XXX.equals(order.getStatus())`）
- `applySeller` 中 `isBuyer()` 增加 `user != null` 空指针检查
- `resetPassword` 增加密码非空和长度校验

### 异常处理
- `GlobalExceptionHandler` 新增 `MethodArgumentNotValidException` 处理器（`@Valid` + `@RequestBody` 场景）
- 购物车 Feign 熔断错误提示区分"服务不可用"和"商品不存在"

### 代码质量
- `ProductServiceImpl` 提取 `applyProductRequest()` 复用 ProductRequest → Product 映射
- `UserServiceImpl` 提取 `validateAddressOwnership()` 复用地址归属校验
- `OrderServiceImpl.getSellerOrders` 批量查询 OrderItem 消除 N+1 问题

### 配置化
- `PaymentController` 中硬编码的 `localhost:4173` 改为 `AlipayProperties.frontendUrl` 配置属性

### 2026-06-03 架构增强

**并发安全**
- 购物车 `addItem`/`updateQuantity`/`removeItem`/`checkItem`/`checkAllItems`/`clearCart` 使用 Redisson `RLock` 分布式锁（leaseTime 5s），防止并发读写覆盖
- 下单幂等改用 `order_request` 状态机（`SELECT ... FOR UPDATE SKIP LOCKED` + `uk_order_request_active_user` 唯一键），消除并发窗口（2026-09 重构，取代原 Redis 方案）

**消息可靠性**
- 引入 Outbox 模式：订单创建和支付结果通知不再直接发送 Kafka，改为先写 `outbox_message` 表（与业务在同一事务），由 `OutboxScheduler` 每 5 秒扫描发送，失败指数退避重试（2^n 秒，最多 5 次）
- 取消/超时订单的库存回补与状态更新同事务写入 stock-restore Outbox；下单过程失败的补偿由 `order_request` 的 COMPENSATING 状态 + 30 秒恢复调度逐项重试，全部回补成功后才置 FAILED

**缓存健壮性**
- `TwoLevelCacheService` Redis 读/写/删操作包裹 try-catch，Redis 故障时自动降级到 Caffeine → DB 路径
- `ProductServiceImpl.updateProduct` 缓存驱逐改为 `afterCommit` 回调，与 `deductStock`/`restoreStock` 保持一致，避免事务回滚后缓存误删

**支付安全**
- `PaymentResultConsumer` 增加支付-取消竞态检测：若支付成功但订单已取消，记录 error 告警（需人工退款）
- `getPaymentByOrderNo` 加 30 秒间隔限制，防止用户频繁刷新触发支付宝 API 限流
- `Payment.lastSyncTime` 字段记录上次查询时间

**Gateway 安全**
- `AuthGlobalFilter` 认证失败响应改用 `ObjectMapper` 生成 JSON，防止手动拼接导致的 JSON 注入风险

**代码质量**
- `SystemConstants` 从 `interface` 改为 `final class`（常量接口反模式修复）
- 前端订单状态 CSS（.status-0~4）从 3 个页面提取到 `global.css`，消除重复

### 2026-09-29 全量修复

**资金闭环**
- 取消订单时同事务写 `payment-close`，payment 端把支付单置为 3 (CLOSED)，避免已取消订单仍可发起支付
- 发起支付前经 `OrderFeignClient` 校验订单状态，order 服务不可用时 fail-closed 拒绝支付
- "已支付但订单已取消"从"仅告警等人工退款"改为写 `refund-request` 自动退款（`out_request_no=orderNo` 幂等，成功后置 4 REFUNDED）

**安全**

- 下游服务不再裸信任 `X-User-Id` 等头：`InternalAuthFilter` 强制校验 `X-Internal-Token`，网关拦截 `/internal/` 路径
- JWT 可吊销：重置密码时把存量 token 写入黑名单
- 登录失败 5 次锁定 15 分钟；用户名不存在与密码错误统一提示
- 上传文件加扩展名白名单与魔数嗅探；Redis 反序列化加 AutoType 白名单；CORS 改来源白名单；Knife4j 默认关闭
- 配置文件与 docker-compose 的硬编码密码改为必填环境变量

**业务与并发**
- 禁止多卖家混单（明确报错），卖家订单只返回自己的明细
- 购物车返回实时价格与库存，失效商品前端置灰且不计入合计
- 补偿次数上限 10 次后转 FAILED，释放用户下单槽位；补偿回滚库存后同步取消孤儿订单
- 超时取消改为"先取消成功再出 ZSet"；Outbox 标记改条件更新与原子 SQL

**缓存**
- 空值哨兵防穿透、Redisson 互斥回源防击穿、Redis 广播跨实例失效 L1；`evictByPattern` 用 SCAN 替代 KEYS
- 库存变动不再淘汰商品 ID 列表缓存，仅结构变更（新增/删除/审核）按 pattern 淘汰

**消息**
- order/payment/cart 三个服务新增 `KafkaConsumerConfig`：消费失败重试 3 次后转 `{topic}.DLQ`
- 新增主题 `payment-close`、`refund-request`、`cart-clear`

---
