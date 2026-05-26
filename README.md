# CloudBack 微服务电商系统

Spring Cloud Alibaba 微服务架构，8 个模块，三种角色（买家/卖家/管理员）。

## 技术栈

| 层级 | 技术 | 版本 |
|---|---|---|
| 语言 | Java | 21 |
| 框架 | Spring Boot | 3.3.9 |
| 微服务 | Spring Cloud | 2023.0.3 |
| 微服务 | Spring Cloud Alibaba | 2023.0.1.0 |
| 注册配置 | Nacos | 2.5.x (Docker) |
| API 网关 | Spring Cloud Gateway | — |
| 认证 | JWT (jjwt 0.12.6 / HMAC-SHA256) | — |
| ORM | MyBatis-Plus | 3.5.11 |
| 连接池 | Druid | 1.2.24 |
| 缓存 | Redis | 7.x (宿主机) |
| 本地缓存 | Caffeine | 3.1.8 |
| 消息队列 | Kafka | 3.9.x (Docker) |
| 对象存储 | MinIO | latest (Docker) |
| 熔断限流 | Sentinel | 1.8.x (Docker) |
| 远程调用 | OpenFeign + LoadBalancer | — |
| 第三方支付 | 支付宝 SDK (alipay-sdk-java) | 4.40.806.ALL |
| 工具 | Hutool、Fastjson2、Lombok | — |

---

## 项目结构

```text
CloudBack/
├── pom.xml                     # 父 POM，统一依赖版本管理
├── .env                        # 环境变量（VM_HOST 等，启动时自动加载）
├── start-all.bat               # Windows 一键启动 7 个服务
│
├── cloud-common/               # 公共模块（被所有模块依赖，不独立部署）
│   └── src/main/java/org/cloudback/common/
│       ├── entity/             # BaseEntity、User（共享实体）
│       ├── result/             # R<T> 统一响应体、ResultCode 错误码枚举
│       ├── exception/          # BusinessException、GlobalExceptionHandler
│       ├── config/             # MyBatisPlusConfig、RedisConfig、JacksonConfig、MinioConfig
│       │                       # CaffeineConfig、TwoLevelCacheService
│       │                       # AutoFillMetaObjectHandler、DotenvEnvironmentPostProcessor
│       ├── constant/           # SystemConstants（Token前缀、Redis Key、订单状态等）
│       ├── utils/              # JwtUtil（签发/解析/验证 JWT）
│       └── mapper/             # UserMapper（共享 User 的 MyBatis-Plus 映射）
│
├── cloud-gateway/  :8080       # API 网关
│   └── src/main/java/org/cloudback/gateway/
│       ├── GatewayApplication.java
│       ├── config/CorsConfig.java         # CORS 跨域（WebFlux 方式）
│       └── filter/AuthGlobalFilter.java   # 全局 JWT 认证过滤器
│
├── cloud-auth/     :8081       # 认证服务
│   └── src/main/java/org/cloudback/auth/
│       ├── AuthApplication.java
│       ├── controller/AuthController.java
│       └── service/impl/AuthServiceImpl.java
│
├── cloud-user/     :8083       # 用户服务
│   └── src/main/java/org/cloudback/user/
│       ├── UserApplication.java
│       ├── controller/UserController.java
│       ├── service/impl/UserServiceImpl.java
│       ├── model/entity/       # Address、SellerApplication
│       └── mapper/             # AddressMapper、SellerApplicationMapper
│
├── cloud-product/  :8082       # 商品服务
│   └── src/main/java/org/cloudback/product/
│       ├── ProductApplication.java
│       ├── controller/ProductController.java
│       ├── service/impl/ProductServiceImpl.java
│       ├── model/entity/       # Category、Product
│       └── mapper/             # CategoryMapper、ProductMapper
│
├── cloud-cart/     :8084       # 购物车服务（Redis Hash + Caffeine 读缓存）
│   └── src/main/java/org/cloudback/cart/
│       ├── CartApplication.java
│       ├── controller/CartController.java
│       ├── service/impl/CartServiceImpl.java
│       ├── feign/ProductFeignClient.java   # 查商品信息
│       └── dto/CartItem.java
│
├── cloud-order/    :8085       # 订单服务
│   └── src/main/java/org/cloudback/order/
│       ├── OrderApplication.java
│       ├── controller/OrderController.java
│       ├── service/impl/OrderServiceImpl.java    # 核心下单编排
│       ├── mq/PaymentResultConsumer.java         # Kafka 支付结果消费者
│       ├── feign/              # CartFeignClient、ProductFeignClient、UserFeignClient
│       │                       # CartItemDTO、ProductDTO
│       ├── config/FeignRequestInterceptor.java   # 用户上下文透传
│       ├── model/entity/       # Order、OrderItem
│       └── mapper/             # OrderMapper、OrderItemMapper
│
├── cloud-payment/  :8086       # 支付服务
│   └── src/main/java/org/cloudback/payment/
│       ├── PaymentApplication.java
│       ├── config/AlipayProperties.java          # 支付宝配置属性绑定
│       ├── config/AlipayConfig.java              # AlipayClient Bean
│       ├── controller/PaymentController.java
│       ├── service/impl/PaymentServiceImpl.java  # 支付宝页面支付核心逻辑
│       ├── mq/OrderCreateConsumer.java           # Kafka 订单创建消费者
│       ├── model/entity/Payment.java
│       └── mapper/PaymentMapper.java
│
├── sql/init.sql                # 建库建表 DDL
└── docker/                     # Docker Compose（Nacos、Kafka、Sentinel、MinIO 对象存储）
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
  → Gateway AuthGlobalFilter 解析 JWT → 注入 X-User-Id、X-Username、X-User-Role
  → 下游服务 Controller 用 @RequestHeader 读取
  → Feign 调用时由 FeignRequestInterceptor 透传这三个头
```

Gateway 白名单外的所有请求强制携带有效 JWT，解析失败返回 401。支付回调路径（支付宝异步通知和同步回跳）在白名单内，无需 JWT。

---

## cloud-common 公共模块详解

被所有模块依赖，不独立部署（`spring-boot-maven-plugin` 跳过）。条件化配置：MySQL/MyBatis-Plus/Redis 仅在引入对应依赖的模块中生效。

### 核心类

| 类 | 职责 |
|---|---|
| `R<T>` | 统一响应体 `{ code, message, data }`，静态工厂方法 `ok()`/`fail()` |
| `ResultCode` | 错误码枚举：SUCCESS(200)、UNAUTHORIZED(401)、FORBIDDEN(403)、PRODUCT_NOT_EXIST(2001)、STOCK_INSUFFICIENT(2002)、ORDER_NOT_EXIST(3001) 等 |
| `BaseEntity` | 实体基类，`id`（雪花算法）、`createTime`、`updateTime`、`deleted`（逻辑删除），由 `AutoFillMetaObjectHandler` 自动填充 |
| `BusinessException` | 业务异常，携带 `code` + `message`，由 `GlobalExceptionHandler` 统一捕获返回 `R.fail()` |
| `GlobalExceptionHandler` | `@RestControllerAdvice`，处理 BusinessException / BindException / 兜底 Exception |
| `JwtUtil` | HMAC-SHA256 签发/解析/验证 JWT，token 有效期 2 小时，claims 含 userId、username、role |
| `SystemConstants` | 所有常量：Redis Key 前缀、订单状态码、Kafka Topic 名、角色字符串 |
| `User` | 共享 User 实体（auth 和 user 服务共用），字段：username、password(BCrypt)、role、status |
| `UserMapper` | 共享 User 的 MyBatis-Plus BaseMapper |

### 配置类

| 类 | 职责 |
|---|---|
| `MyBatisPlusConfig` | 注册 `MybatisPlusInterceptor`，条件化（有 DataSource 才加载） |
| `AutoFillMetaObjectHandler` | INSERT 时自动填 `createTime`/`updateTime`/`deleted=0`；UPDATE 时填 `updateTime` |
| `RedisConfig` | 注册 `RedisTemplate<String, Object>`，Fastjson2 序列化，支持类型自动推断 |
| `JacksonConfig` | `Long` → `String`，防止前端 JS 精度丢失雪花 ID |
| `DotenvEnvironmentPostProcessor` | Spring 启动前加载 `.env` 文件到 Environment，支持 `${VAR}` 引用 |
| `MinioConfig` | 创建 `MinioClient` Bean，自动初始化 Bucket 并设置公开读策略；`@ConditionalOnProperty` 确保只在配置了 `minio.endpoint` 的模块中加载 |
| `CaffeineConfig` | 创建 4 个 Caffeine Cache 实例（商品详情/热门/ID列表/购物车），并组装对应的 `TwoLevelCacheService` Bean；`@ConditionalOnClass(RedisTemplate.class)` 确保 gateway/auth 等无 Redis 依赖的模块自动跳过 |
| `TwoLevelCacheService` | 通用二级缓存服务：L1(Caffeine 本地 30s)→L2(Redis 分布式 5min)→DB，提供 `get()` 穿透读取和 `evict()` 双删（先 Redis 后 Caffeine）|

---

## 二级缓存架构（Caffeine + Redis）

`TwoLevelCacheService` 是通用二级缓存抽象，为不同场景创建独立的 Caffeine 实例和 Redis Key 前缀，互不干扰。

```
读路径:  Caffeine(L1, 30s TTL) → Redis(L2, 5min TTL) → MySQL → 回填 L1+L2
写路径:  DB 更新 → evict(Redis → Caffeine) 双删，L1 短 TTL 兜底
```

| Bean 名称 | 使用场景 | Caffeine 最大条目 | Redis Key 前缀 |
|---|---|---|---|
| `productDetailTwoLevel` | 商品详情 | 1000 | `cloud:product:detail:` |
| `hotProductsTwoLevel` | 热门商品 Top8 | 5 | `cloud:product:hot` |
| `productIdListTwoLevel` | 商品列表 ID 映射 | 200 | `cloud:product:ids:` |
| `cartTwoLevel` | 购物车（独立 key） | 500 | `cloud:cart:cache:` |

### 各场景实现

**商品详情**：`getProductDetail()` 查询时先走二级缓存，miss 才查 DB。`updateProduct()` / `deleteProduct()` / `deductStock()` / `reviewProduct()` 调用后 evict 对应 key。

**热门商品**：`getHotProducts()` 的 DB loader 仍从 Redis ZSet 取 topId → 批量查 DB，结果被 L1/L2 缓存。L1 30s 过期后自动从 L2 或 DB 刷新，避免每次首页请求都查 ZSet+DB。

**商品列表（ID-Mapping）**：条件查询的组合太多（categoryId × keyword），无法直接缓存全量产品。改为缓存"查询条件 → 产品 ID 列表"的映射（`cloud:product:ids:{categoryId}:{keyword}`），拿到 ID 列表后手动分页，再逐个从 `productDetailTwoLevel` 获取产品详情。一份产品详情只存一份，避免缓存膨胀。

**购物车**：源数据仍在 Redis Hash（`cloud:cart:{userId}`），读路径上包一层 Caffeine 缓存（`cloud:cart:cache:{userId}`），减少高频读操作的 Redis 网络往返。`addItem()` / `updateQuantity()` / `removeItem()` / `checkItem()` / `clearCart()` 操作后同步 evict 缓存。

---

## 基础架构详解

### 请求入口：cloud-gateway

**AuthGlobalFilter**（`order = -100`，最高优先级）：

```text
1. 检查路径是否在白名单（/auth/login、/auth/register）
   → 白名单：直接放行
2. 提取 Authorization: Bearer <token> 头
   → 缺失或格式不正确：返回 401 JSON
3. JwtUtil.parseToken(token) 解析
   → 过期或无效：返回 401 JSON
4. 从 Claims 提取 userId、username、role
5. 向请求头注入 X-User-Id、X-Username、X-User-Role
6. 放行到下游微服务
```

**网关路由**（`application.yml`）：

```yaml
spring.cloud.gateway.routes:
  - /api/auth/**     → lb://cloud-auth      (StripPrefix=1)
  - /api/user/**     → lb://cloud-user
  - /api/product/**  → lb://cloud-product
  - /api/cart/**     → lb://cloud-cart
  - /api/order/**    → lb://cloud-order
  - /api/payment/**  → lb://cloud-payment
```

CORS 通过 `CorsConfig`（WebFlux `CorsWebFilter`）全局允许所有来源。

### OpenFeign 远程调用

| 调用方 | Feign 接口 | 目标服务 | 接口 |
|---|---|---|---|
| cloud-cart | `ProductFeignClient` | cloud-product | `GET /product/detail/{id}` |
| cloud-order | `CartFeignClient` | cloud-cart | `GET /cart/checked`、`DELETE /cart/clear` |
| cloud-order | `ProductFeignClient` | cloud-product | `PUT /product/stock/deduct/{id}`、`GET /product/detail/{id}` |
| cloud-order | `UserFeignClient` | cloud-user | `GET /user/address/{id}` |

**Feign 用户上下文透传**：`cloud-order` 中的 `FeignRequestInterceptor` 实现了 `feign.RequestInterceptor`，从当前 HTTP 请求（`RequestContextHolder`）提取 `X-User-Id`、`X-Username`、`X-User-Role`，自动注入到每个 Feign 请求头，确保下游服务能识别调用方身份。

### Kafka 异步消息

三个 Topic，形成订单-支付闭环：

```
cloud-order 下单成功（事务提交后）
  → Kafka "order-create" { orderId, orderNo, userId, totalAmount }
  → cloud-payment.OrderCreateConsumer 消费
    → 创建待支付记录（status=0）—— 不再自动标记支付成功
    → 等待用户手动发起支付宝页面支付

用户点击"立即支付" → POST /api/payment/pay/{orderNo}
  → 后端生成支付宝页面支付表单 → 前端新窗口打开 → 跳转支付宝
  → 用户完成付款 → 支付宝异步通知 POST /api/payment/notify/alipay
    → 验签 → 更新 payment.status=1 → Kafka "payment-result"
    OR 支付宝同步回跳 GET /api/payment/return/alipay
    → 主动查询支付宝交易状态 → 确认已付 → 同上更新
  → cloud-order.PaymentResultConsumer 消费 "payment-result"
    → UPDATE order_info.status = 已支付, pay_time = now()
```

---

## 完整业务流程

### 1. 注册与登录

```
注册 POST /api/auth/register?username=&password=&nickname=
  1. 检查 username 唯一性（SELECT count FROM user WHERE username = ?）
  2. BCrypt.hashpw(password) 加密
  3. INSERT user (username, password, nickname, role='BUYER', status=1)
  4. 返回成功 → 前端跳转 /login

登录 POST /api/auth/login?username=&password=
  1. SELECT FROM user WHERE username = ?
  2. BCrypt.checkpw(password, user.password) 验证
  3. 检查 user.status != 0（未禁用）
  4. JwtUtil.createToken(userId, username, {role: role}) 签发 JWT（2h 有效）
  5. 返回 token 字符串
```

### 2. 热门商品（Redis ZSET 排行榜）

```
浏览商品 GET /api/product/detail/{id}
  → ZINCRBY cloud:product:views {productId} 1
  → Redis 挂了：catch 异常，仅 log.warn，不影响详情页正常返回

首页 GET /api/product/hot
  1. 先查二级缓存(L1→L2)，命中直接返回
  2. 未命中：ZREVRANGE cloud:product:views 0 7（取浏览量 Top8 的商品 ID）
  3. selectBatchIds(ids) 批量查 MySQL 获取完整商品信息
  4. 按 ZSET 排名排序，写入 L1+L2 缓存
  → Redis 为空或挂了：降级查询 MySQL ORDER BY sales DESC LIMIT 8
```

### 3. 商品列表（分类筛选 + ID-Mapping 缓存）

```
GET /api/product/list?categoryId=&keyword=&page=&size=

采用 ID-Mapping 策略避免缓存膨胀（详见二级缓存架构章节）：
  1. 先查 cloud:product:ids:{categoryId}:{keyword} 缓存（L1→L2）
  2. 未命中则查 DB：只 SELECT id，按条件过滤 + 按销量排序
     → 缓存 ID 列表，TTL 5min
  3. 手动分页截取当前页的 ID 子集
  4. 逐个从商品详情缓存获取完整 Product 对象（复用 getProductDetail 的缓存）

分类筛选逻辑：
  5. 若 categoryId 有值：递归收集该分类及其所有后代子分类的 ID
     → wrapper.in(Product::getCategoryId, categoryIds)
  6. 若 categoryId 为空：不筛选分类
  7. keyword 非空 → wrapper.like(Product::getName, keyword)
  8. 只查 status = 1 的商品
```

### 4. 购物车（Redis Hash + Caffeine 读缓存）

```text
源数据（Redis Hash）
  Key:   cloud:cart:{userId}
  Field: productId（String）
  Value: CartItem JSON { productId, name, mainImage, price, quantity, checked }
  TTL:   7 天，每次操作续期

读缓存（Caffeine + Redis，独立 key）
  Key:   cloud:cart:cache:{userId}        → 全量列表
         cloud:cart:cache:checked:{userId} → 已勾选列表
  L1:    Caffeine 30s，最大 500 条
  L2:    Redis String，5min TTL
  写操作后同步 evict 两个 key（先 Redis 后 Caffeine）

添加商品 POST /api/cart/add?productId=&quantity=
  1. Feign → ProductService.getProductDetail(productId) 获取商品名/图/价
  2. 从 Redis Hash 检查是否已有该 productId
     → 已有：累加 quantity，更新 name/image/price（保证最新）
     → 没有：创建新 CartItem，checked 默认 true
  3. HSET + EXPIRE 续期 7 天
  4. evict 读缓存（两个 key）

更新数量 PUT /api/cart/quantity?productId=&quantity=
  → HGET → 修改 quantity → HSET → evict 读缓存

勾选 PUT /api/cart/check?productId=&checked=
  → HGET → 修改 checked → HSET → evict 读缓存

获取购物车 GET /api/cart/list
  → L1 → L2 → HGETALL → 回填 L1+L2

获取勾选商品 GET /api/cart/checked
  → L1 → L2 → HGETALL → filter(checked=true) → 回填 → 供订单服务下单时使用

清空 DEL /api/cart/clear → DEL cloud:cart:{userId} → evict 读缓存
```

### 5. 下单（核心编排，@Transactional）

```text
POST /api/order/create?addressId=&remark=

Step 1: Feign → CartFeignClient.getCheckedItems()
        获取用户已勾选的购物车商品（过滤 checked=true）
        若为空 → BusinessException "购物车中没有已勾选的商品"

Step 2: 循环处理每个商品
  - 计算 itemTotal = price × quantity，累加 totalAmount
  - 从 CartItemDTO 提取 productName / productImage
    若为 null → Feign → ProductFeignClient.getProductDetail(productId) 补全
    仍为 null → BusinessException（防止 SQL 报错）

Step 3: 构建 Order 实体
  - 雪花 ID 生成 orderNo
  - status = 0 (UNPAID)
  - 若传了 addressId → Feign → UserFeignClient.getAddressById() 获取收货信息

Step 4: INSERT order_info（orderMapper.insert）

Step 5: INSERT order_item × N（orderItemMapper.insert）
  - 每条记录保存商品名称/图片/价格快照

Step 6: Feign → ProductFeignClient.deductStock() × N
  - 扣减库存 + 增加销量（product 服务内 @Transactional）
  - 任一失败 → 抛出异常 → 整个 createOrder 回滚

Step 7: Feign → CartFeignClient.clearCart()
        清空购物车

Step 8: 注册 TransactionSynchronization.afterCommit()
        → Kafka.send("order-create", { orderId, orderNo, userId, totalAmount })
        → 确保事务提交后支付服务才消费

Step 9: 返回 Order 实体
```

**关键设计**：先写订单再扣库存（订单入库失败不扣库存）；Kafka 消息在事务提交后发送（支付服务消费时订单一定存在）。

### 6. 支付（支付宝沙箱页面支付）

```text
消费 order-create:
  1. 解析消息 { orderNo, userId, totalAmount }
  2. 幂等检查：SELECT FROM payment WHERE order_no = ?
     → 已有记录：跳过
  3. INSERT payment（status=0 待支付，payMethod=ALIPAY）—— 不自动标记成功

发起支付 POST /api/payment/pay/{orderNo}:
  1. 检查支付记录存在、归属正确、状态为待支付
  2. 构造 AlipayTradePagePayRequest：
     - out_trade_no = orderNo
     - total_amount = 订单金额
     - subject = "CloudMall订单-{orderNo}"
     - product_code = FAST_INSTANT_TRADE_PAY
     - notify_url = 异步通知地址（网关 /api/payment/notify/alipay）
     - return_url = 同步回跳地址（前端 /payment/result）
  3. alipayClient.pageExecute() → 返回支付表单 HTML
  4. 前端将 HTML 写入新窗口 → 表单自动提交 → 跳转支付宝收银台

用户在支付宝完成付款:

  同步回跳（浏览器）:
    GET /api/payment/return/alipay?out_trade_no=xxx&trade_no=xxx&...
    → 提取 orderNo → getPaymentByOrderNo()
      → 发现本地 status=0 → 调 alipay.trade.query API 查支付宝
      → 返回 TRADE_SUCCESS → 更新 payment.status=1, tradeNo, payTime
      → 发送 Kafka "payment-result" → 302 跳转前端支付结果页

  异步通知（服务端，需公网可达）:
    POST /api/payment/notify/alipay
    → 验签（AlipaySignature.rsaCheckV1, RSA2）
    → trade_status == TRADE_SUCCESS → 同上更新本地状态 → 返回 "success"

cloud-order 消费 payment-result:
  1. status == "SUCCESS" 且 order 为 UNPAID
     → UPDATE order_info SET status = 1 (PAID), pay_time = now()
  2. 条件更新防止覆盖已取消/已完成的订单

查询支付记录 GET /api/payment/{orderNo}:
  若本地记录仍为待支付 → 主动调 alipay.trade.query 查支付宝
  → 确认已付则更新本地状态并通知订单服务
```

**支付宝配置**：沙箱网关 `openapi-sandbox.dl.alipaydev.com`，RSA2 签名，公钥模式。密钥通过 `.env` 注入，单行 PKCS8 格式。应用私钥和支付宝公钥均不含 `----BEGIN/END----` 标记。

### 7. 取消订单

```text
PUT /api/order/cancel/{id}

  1. 查订单，校验 userId 归属（防越权）
  2. 检查 status == UNPAID（只有待支付可取消）
     → 否则 BusinessException "只能取消待支付的订单"
  3. UPDATE order_info SET status = 4 (CANCELLED)
```

### 8. 申请成为卖家

```text
买家 POST /api/user/apply-seller
  1. 检查 role == BUYER（只有买家可申请）
  2. 检查是否已有 PENDING 的申请记录（防止重复提交）
  3. INSERT seller_application (userId, status='PENDING')

管理员 GET /api/user/admin/applications
  → 列出所有 PENDING 的申请

管理员 PUT /api/user/admin/applications/{id}?approved=true/false
  - 通过：application.status = APPROVED，user.role = SELLER
  - 拒绝：application.status = REJECTED
  - 通过后需重新登录获取新 JWT（旧 token 中 role 仍为 BUYER）
```

### 9. 商品审核

```text
卖家添加商品 POST /api/product
  → SELLER：status = 2 (PENDING，待审核)
  → ADMIN：status = 1（直接上架）

卖家编辑商品 PUT /api/product
  → SELLER 编辑后 status 重新设为 2 (PENDING)
  → 卖家只能编辑自己的商品（sellerId 校验）

管理员审核 GET /api/product/admin/pending
  → 列出所有 status = 2 的商品

管理员审批 PUT /api/product/admin/review/{id}?approved=true/false
  - 通过：status = 1（上架，可被公开查询到）
  - 拒绝：status = 0（下架）
  - 只能审批 status = 2 的商品
```

### 10. 用户管理（管理员）

```text
GET /api/user/admin/list
  → 返回所有用户列表，密码字段置 null 脱敏

PUT /api/user/admin/reset-password?targetUserId=&newPassword=
  → BCrypt 加密新密码 → 更新指定用户密码
```

### 11. 图片上传（MinIO 对象存储）

```text
前端选择文件
  → FormData 封装，POST /api/product/upload （商品图）或 /api/user/avatar/upload （头像）
    → Controller 接收 MultipartFile，生成 UUID 文件名
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

---

## 完整 API 参考

所有接口通过 Gateway 8080 访问，路径前缀 `/api`。

| 方法 | 路径 | 服务 | 认证 | 说明 |
|---|---|---|---|---|
| POST | `/api/auth/register` | auth | 否 | 注册 |
| POST | `/api/auth/login` | auth | 否 | 登录，返回 JWT |
| GET | `/api/user/info` | user | 是 | 获取个人信息 |
| PUT | `/api/user/info` | user | 是 | 修改个人信息（含头像） |
| POST | `/api/user/avatar/upload` | user | 是 | 上传头像到 MinIO |
| GET | `/api/user/address` | user | 是 | 地址列表 |
| GET | `/api/user/address/{id}` | user | 是 | 地址详情 |
| POST | `/api/user/address` | user | 是 | 添加地址 |
| PUT | `/api/user/address` | user | 是 | 修改地址 |
| DELETE | `/api/user/address/{id}` | user | 是 | 删除地址 |
| POST | `/api/user/apply-seller` | user | 是 | 申请卖家 |
| GET | `/api/user/admin/list` | user | 是 | 管理员-用户列表 |
| PUT | `/api/user/admin/reset-password` | user | 是 | 管理员-重置密码 |
| GET | `/api/user/admin/applications` | user | 是 | 管理员-卖家申请列表 |
| PUT | `/api/user/admin/applications/{id}` | user | 是 | 管理员-审批卖家申请 |
| GET | `/api/product/category` | product | 否 | 分类树 |
| POST | `/api/product/category` | product | 是 | 添加分类(SELLER/ADMIN) |
| PUT | `/api/product/category` | product | 是 | 修改分类 |
| DELETE | `/api/product/category/{id}` | product | 是 | 删除分类 |
| GET | `/api/product/hot` | product | 否 | 热门商品 Top8 |
| GET | `/api/product/list` | product | 否 | 商品列表(分页/搜索/分类) |
| GET | `/api/product/detail/{id}` | product | 否 | 商品详情 |
| GET | `/api/product/my-list` | product | 是 | 卖家自己的商品 |
| POST | `/api/product` | product | 是 | 添加商品(SELLER/ADMIN) |
| PUT | `/api/product` | product | 是 | 修改商品 |
| DELETE | `/api/product/{id}` | product | 是 | 删除商品 |
| GET | `/api/product/admin/pending` | product | 是 | 管理员-待审核商品 |
| PUT | `/api/product/admin/review/{id}` | product | 是 | 管理员-审批商品 |
| POST | `/api/product/upload` | product | 是 | 上传商品图片到 MinIO |
| PUT | `/api/product/stock/deduct/{id}` | product | 内部 | Feign 扣库存 |
| GET | `/api/cart/list` | cart | 是 | 购物车列表 |
| POST | `/api/cart/add` | cart | 是 | 加入购物车 |
| PUT | `/api/cart/quantity` | cart | 是 | 修改数量 |
| PUT | `/api/cart/check` | cart | 是 | 勾选/取消 |
| DELETE | `/api/cart/{productId}` | cart | 是 | 删除单项 |
| DELETE | `/api/cart/clear` | cart | 是 | 清空购物车 |
| GET | `/api/cart/checked` | cart | 内部 | Feign 获取勾选商品 |
| POST | `/api/order/create` | order | 是 | 创建订单 |
| GET | `/api/order/list` | order | 是 | 订单列表 |
| GET | `/api/order/detail/{id}` | order | 是 | 订单详情 |
| PUT | `/api/order/cancel/{id}` | order | 是 | 取消订单 |
| POST | `/api/payment/pay/{orderNo}` | payment | 是 | 发起支付宝页面支付，返回支付表单 |
| POST | `/api/payment/notify/alipay` | payment | 否 | 支付宝异步通知回调 |
| GET | `/api/payment/return/alipay` | payment | 否 | 支付宝同步回跳（查支付→跳前端） |
| GET | `/api/payment/{orderNo}` | payment | 是 | 查询支付记录（待支付时主动查支付宝） |

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
| `payment` | id, order_no, user_id, amount, pay_method, trade_no, status | idx_order_no, idx_user_id |

所有表包含 `create_time` / `update_time` / `deleted`（逻辑删除），id 为雪花算法 BIGINT。

### 订单状态流转

```
0-待支付 → 1-已支付 → 2-已发货 → 3-已完成
   │                                    ↑
   └────────── 4-已取消 ────────────────┘（不可逆）
```

---

## Redis 数据结构

| Key | 类型 | 用途 |
|---|---|---|
| `cloud:cart:{userId}` | Hash | 购物车源数据，field=productId, value=CartItem JSON，TTL 7 天 |
| `cloud:product:views` | ZSet | 商品浏览量排行榜，score=浏览次数，member=productId |
| `cloud:product:detail:{id}` | String | 商品详情 L2 缓存，TTL 5 分钟 |
| `cloud:product:hot` | String | 热门商品 Top8 列表 L2 缓存，TTL 5 分钟 |
| `cloud:product:ids:{categoryId}:{keyword}` | String | 商品列表 ID 映射 L2 缓存，TTL 5 分钟 |
| `cloud:cart:cache:{userId}` | String | 购物车 L2 缓存，TTL 5 分钟 |
| `cloud:cart:cache:checked:{userId}` | String | 已勾选商品 L2 缓存，TTL 5 分钟 |
| `cloud:token:blacklist:{tokenId}` | String | Token 黑名单（预留，当前未使用） |

---

## 环境变量

项目根目录 `.env` 文件，支持 `${KEY}` 引用：

```ini
VM_HOST=192.168.91.130
MYSQL_ADDR=${VM_HOST}:3306
REDIS_ADDR=${VM_HOST}
NACOS_ADDR=${VM_HOST}:8848
SENTINEL_ADDR=${VM_HOST}:8858
KAFKA_ADDR=${VM_HOST}:9092
# MinIO 对象存储
MINIO_ENDPOINT=${VM_HOST}:9000
MINIO_ACCESS_KEY=admin
MINIO_SECRET_KEY=admin123456
MINIO_BUCKET=cloudmall-products
# 支付宝沙箱
ALIPAY_APP_ID=2021000148684222
ALIPAY_PRIVATE_KEY=<PKCS8应用私钥单行>
ALIPAY_PUBLIC_KEY=<支付宝公钥单行>
ALIPAY_GATEWAY=https://openapi-sandbox.dl.alipaydev.com/gateway.do
ALIPAY_NOTIFY_URL=http://<IP>:8080/api/payment/notify/alipay
ALIPAY_RETURN_URL=http://localhost:4173/payment/result
```

`DotenvEnvironmentPostProcessor` 启动时自动从工作目录向上查找 `.env`（最多 5 层），解析后注入 Spring Environment。

---

## 部署

### 虚拟机（Ubuntu）

```bash
# MySQL + Redis 宿主机安装
sudo apt install mysql-server redis-server

# 初始化数据库
mysql -u root -p < sql/init.sql

# 开放远程访问
# MySQL: /etc/mysql/mysql.conf.d/mysqld.cnf → bind-address = 0.0.0.0
# Redis: /etc/redis/redis.conf → bind 0.0.0.0
sudo ufw allow 3306,6379,8848,9848,9000,9001,9092,8088,8858/tcp

# Docker 中间件
cd docker && docker compose up -d
```

### Windows 本地开发

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env → VM_HOST 设为虚拟机 IP

# 2. 编译
mvn clean install -DskipTests

# 3. 依次启动（或双击 start-all.bat）
mvn spring-boot:run -pl cloud-gateway
mvn spring-boot:run -pl cloud-auth
mvn spring-boot:run -pl cloud-product
mvn spring-boot:run -pl cloud-user
mvn spring-boot:run -pl cloud-cart
mvn spring-boot:run -pl cloud-order
mvn spring-boot:run -pl cloud-payment
```

---

## 安全设计

- **密码**：BCrypt 加密存储（Hutool），永不返回给前端
- **JWT**：HMAC-SHA256 签名，2 小时过期，Gateway 全局校验
- **用户隔离**：通过 `X-User-Id` 头实现，地址/订单/商品归属校验防止越权
- **角色控制**：`X-User-Role` 头 + Service 层权限检查
- **逻辑删除**：所有表使用 MyBatis-Plus `@TableLogic`，数据不可恢复
- **雪花 ID**：JacksonConfig 将 Long 序列化为 String，防止前端精度丢失

## 常见问题

| 现象 | 原因 | 解决 |
|---|---|---|
| Gateway 连 localhost 而非 VM IP | `.env` 未加载 | `mvn clean install -DskipTests` 后重启 |
| Nacos 9848 端口连不上 | 防火墙未放行 gRPC 端口 | `sudo ufw allow 9848/tcp` |
| Nacos 客户端解析到 localhost | Docker 通告了容器内网 IP | Nacos `spring.cloud.nacos.discovery.ip` 配置 |
| Redis 连接被拒 | Redis 只监听 127.0.0.1 | 改 `bind 0.0.0.0` 并设 `requirepass` |
| 审批显示"申请不存在" | JS 精度丢失 Snowflake ID | JacksonConfig 已处理 Long→String |
| 新增商品报 JSON 错误 | `images` 字段为空字符串 `""` | 前端发 `null` 或有效 JSON 数组 |
| 改角色后仍报权限错误 | JWT 仍是旧角色 | 退出重新登录获取新 token |
| 订单创建报 product_name 无默认值 | 购物车数据缺少商品名 | order 服务已加 Feign 回退查询商品详情 |
| 选父分类查不到商品 | 商品只挂叶子分类 | product 服务已改为递归收集子分类 ID |
| 图片上传失败 | MinIO 容器未启动 | `cd docker && docker compose up -d minio` |
| 上传图片后页面不显示 | MinIO Bucket 无公开读策略 | MinioConfig 首次启动自动创建 Bucket 并设置策略 |
| MinIO 启动报 invalid hostname | endpoint 含 `host:port` 格式 | MinioConfig 自动拆分 host 和 port 传入 SDK |
| 修改商品/头像后刷新才看到变更 | 前端无自动刷新 | 前端已加 30s 静默轮询 |
| 支付后订单仍显示未支付 | 异步通知无法到达本地 | 查询支付记录时主动调支付宝 API 查状态 |
| 支付点击后跳转 Alipay 错误页 | return_url 被支付宝拒绝 | 使用前端地址（localhost:4173）作为回跳，不经过网关 |
| 支付宝私钥配置后启动报错 | PKCS8 密钥含换行 | 将密钥合并为单行，去除 BEGIN/END 标记 |
| 支付同步回跳 404 | 前端预览端口非 5173 | 配置 ALIPAY_RETURN_URL 为实际端口（如 :4173） |
