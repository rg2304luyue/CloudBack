# CloudBack 微服务电商系统

基于 Spring Cloud Alibaba 全家桶构建的电商微服务系统，8 个微服务模块协同工作。

## 技术栈

|层级|技术|版本|
|---|---|---|
|语言|Java|21|
|框架|Spring Boot|4.0.6|
|微服务|Spring Cloud|2025.0.0 (Northfields)|
|微服务|Spring Cloud Alibaba|2025.0.0.0|
|注册/配置中心|Nacos|2.5.x|
|API 网关|Spring Cloud Gateway|-|
|安全认证|JWT (jjwt 0.12.6)|-|
|ORM|MyBatis-Plus|3.5.11|
|连接池|Druid|1.2.24|
|缓存|Redis|7.x|
|消息队列|Kafka|3.9.x|
|熔断限流|Sentinel|1.8.x|
|远程调用|OpenFeign|-|
|工具库|Hutool|5.8.x|
|JSON|Fastjson2|2.0.x|
|API 文档|Knife4j|4.5.0|
|构建|Maven|3.9+|

## 项目结构

```text
CloudBack/
├── pom.xml                              # 父 POM，统一依赖版本管理
├── cloud-common/                        # 公共模块
│   └── src/main/java/org/cloudback/common/
│       ├── result/          R.java, ResultCode.java        # 统一响应体
│       ├── entity/          BaseEntity.java, User.java     # 公共实体
│       ├── mapper/          UserMapper.java               # 公共 Mapper
│       ├── exception/       BusinessException.java        # 业务异常
│       │                    GlobalExceptionHandler.java    # 全局异常处理
│       ├── constant/        SystemConstants.java          # 系统常量
│       ├── utils/           JwtUtil.java                  # JWT 工具
│       └── config/          MyBatisPlusConfig.java        # MyBatis 配置
│                            RedisConfig.java              # Redis 配置
│                            AutoFillMetaObjectHandler.java # 字段自动填充
├── cloud-gateway/   :8080   # API 网关
│   └── src/main/java/org/cloudback/gateway/
│       ├── GatewayApplication.java
│       ├── config/          CorsConfig.java               # 跨域配置
│       └── filter/          AuthGlobalFilter.java         # 全局 JWT 认证
├── cloud-auth/      :8081   # 认证服务
│   └── src/main/java/org/cloudback/auth/
│       ├── AuthApplication.java
│       ├── controller/      AuthController.java           # 登录/注册接口
│       └── service/impl/    AuthServiceImpl.java          # 密码加密、JWT 签发
├── cloud-product/   :8082   # 商品服务
│   └── src/main/java/org/cloudback/product/
│       ├── ProductApplication.java
│       ├── model/entity/    Product.java, Category.java   # 商品、分类实体
│       ├── mapper/          ProductMapper.java, CategoryMapper.java
│       ├── service/impl/    ProductServiceImpl.java       # 分类树、分页、库存扣减
│       └── controller/      ProductController.java
├── cloud-user/      :8083   # 用户服务
│   └── src/main/java/org/cloudback/user/
│       ├── UserApplication.java
│       ├── model/entity/    Address.java
│       ├── mapper/          AddressMapper.java
│       ├── service/impl/    UserServiceImpl.java          # 用户信息、地址管理
│       └── controller/      UserController.java
├── cloud-cart/      :8084   # 购物车服务
│   └── src/main/java/org/cloudback/cart/
│       ├── CartApplication.java
│       ├── dto/             CartItem.java                 # 购物车项 DTO
│       ├── feign/           ProductFeignClient.java       # Feign 调用商品服务
│       ├── service/impl/    CartServiceImpl.java          # Redis Hash 操作
│       └── controller/      CartController.java
├── cloud-order/     :8085   # 订单服务
│   └── src/main/java/org/cloudback/order/
│       ├── OrderApplication.java
│       ├── model/entity/    Order.java, OrderItem.java
│       ├── mapper/          OrderMapper.java, OrderItemMapper.java
│       ├── feign/           CartFeignClient.java, ProductFeignClient.java
│       │                    UserFeignClient.java
│       ├── service/impl/    OrderServiceImpl.java         # 下单(multi-Feign+Kafka)
│       ├── controller/      OrderController.java
│       └── mq/              PaymentResultConsumer.java    # 支付结果 Kafka 消费
├── cloud-payment/   :8086   # 支付服务
│   └── src/main/java/org/cloudback/payment/
│       ├── PaymentApplication.java
│       ├── model/entity/    Payment.java
│       ├── mapper/          PaymentMapper.java
│       ├── service/impl/    PaymentServiceImpl.java
│       ├── controller/      PaymentController.java
│       └── mq/              OrderCreateConsumer.java      # 订单创建 Kafka 消费
├── sql/
│   └── init.sql                        # 数据库初始化脚本（7 张表）
└── docker/
    ├── docker-compose.yml              # 中间件编排
    └── nacos/conf/application.properties
```

## 系统架构

```text
                            ┌─────────────┐
                            │   Browser   │
                            │  Vue 3 前端 │
                            └──────┬──────┘
                                   │ HTTP
                                   ▼
                          ┌────────────────┐
                          │  cloud-gateway │  :8080
                          │  Spring Cloud  │
                          │    Gateway     │
                          │  JWT 认证过滤  │
                          │  CORS 跨域处理 │
                          └───────┬────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    │             │             │
              ┌─────▼─────┐ ┌────▼────┐ ┌─────▼─────┐
              │ Nacos     │ │Sentinel │ │  路由分发  │
              │注册/配置  │ │ 熔断限流 │ │lb://service│
              └───────────┘ └─────────┘ └─────┬─────┘
                                              │
      ┌────────┬────────┬────────┬───────────┬──────────┬─────────┐
      ▼        ▼        ▼        ▼           ▼          ▼         ▼
  auth:8081 product  user:8083 cart:8084  order:8085 payment:8086
              :8082
      │                  │        │           │          │         │
      │                  │        │    ┌──────┘          │         │
      │                  │        │    │ Feign 调用       │         │
      │                  │        │    ▼                  │         │
      │                  │        │  cart                 │         │
      │                  │        │  获取勾选商品          │         │
      │                  │        └───────────────┬───────┘         │
      │                  │                        │                  │
      │                  │        ┌───────────────┘                  │
      │                  │        │ Feign 调用库存扣减               │
      │                  │        ▼                                  │
      │                  └──product                                  │
      │                                                              │
      ▼                                                              │
   MySQL:3306 ←── user表、order表、product表、payment表               │
   Redis:6379 ←── 购物车 Hash、Token 黑名单                           │
                                                                      │
                  ┌───────────────────────────────────────────────────┘
                  ▼
          ┌──────────────┐
          │ Kafka :9092  │
          │ order-create │────────► payment 消费
          │ pay-result   │────────► order 消费
          └──────────────┘
```

## 服务调用链路

### 用户登录

```text
POST /auth/login (白名单，无需 Token)
  → AuthController.login()
    → AuthServiceImpl.login()
      → UserMapper.selectOne()        # 查询用户
      → BCrypt.checkpw()              # 验证密码
      → JwtUtil.createToken()         # 签发 JWT (2h有效期)
    ← 返回 { code:200, data:"<token>" }
```

### 用户下单（完整链路）

```text
POST /order/create (Header: Authorization: Bearer <token>)
  │
  │  [Gateway] AuthGlobalFilter 解析 JWT → 注入 X-User-Id / X-Username
  │
  ├─① Feign → CartService.getCheckedItems()       # 从 Redis Hash 取勾选商品
  │
  ├─② Feign → UserService.getAddressById()         # 查收货地址
  │
  ├─③ Feign → ProductService.deductStock() × N     # 逐个扣减库存
  │      │  @Transactional 保护，失败回滚
  │      │  检查 stock >= quantity，否则抛 STOCK_INSUFFICIENT
  │
  ├─④ INSERT INTO order_info ...                    # 创建订单，状态=待支付
  │      INSERT INTO order_item × N                # 创建订单明细
  │      │  @Transactional 保护，异常回滚全部
  │
  ├─⑤ Feign → CartService.clearCart()              # 清空购物车
  │
  └─⑥ Kafka.send("order-create", {orderId, ...})  # 通知支付服务
         │
         ▼
  [cloud-payment] OrderCreateConsumer.onOrderCreate()
    │
    ├─ PaymentService.processPayment()              # 创建支付记录
    │      INSERT INTO payment ..., status=已支付
    │
    └─ Kafka.send("payment-result", {status:SUCCESS})
         │
         ▼
  [cloud-order] PaymentResultConsumer.onPaymentResult()
    └─ UPDATE order_info SET status=已支付, pay_time=NOW()
```

### 购物车 Redis 数据结构

```text
  购物车数据完全存储在 Redis，结构: Hash

  Key:   cloud:cart:{userId}
  Field: productId
  Value: CartItem JSON

  ┌─────────────────────────────────────────────┐
  │ cloud:cart:10086      TTL: 7天              │
  │  ┌──────────┬──────────────────────────────┐│
  │  │   1001   │ {"productId":1001,           ││
  │  │          │  "productName":"iPhone",     ││
  │  │          │  "price":6999,               ││
  │  │          │  "quantity":2,               ││
  │  │          │  "checked":true}              ││
  │  ├──────────┼──────────────────────────────┤│
  │  │   2002   │ {"productId":2002,           ││
  │  │          │  "productName":"耳机",        ││
  │  │          │  "price":299,                ││
  │  │          │  "quantity":1,               ││
  │  │          │  "checked":false}             ││
  │  └──────────┴──────────────────────────────┘│
  └─────────────────────────────────────────────┘

  Redis 命令映射:
    HSET     cloud:cart:{userId} {productId} {cartItemJSON}   → 添加商品
    HGETALL  cloud:cart:{userId}                              → 查看购物车
    HDEL     cloud:cart:{userId} {productId}                  → 删除单个
    DEL      cloud:cart:{userId}                              → 清空购物车
    HGET   → 修改对象 → HSET                                  → 更新数量/勾选
```

## 统一响应格式

所有接口返回统一结构 `R<T>`：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { "": "" }
}
```

|状态码|含义|
|---|---|
|200|操作成功|
|400|参数错误|
|401|未登录或 Token 已过期|
|403|没有访问权限|
|404|资源不存在|
|500|服务内部错误|
|1001|用户名或密码错误|
|1002|用户不存在|
|1003|用户已存在|
|1004|Token 已过期|
|1005|Token 无效|
|2001|商品不存在|
|2002|库存不足|
|3001|订单不存在|
|3002|订单状态异常|
|4001|支付失败|

## 数据库设计

|表名|说明|关键字段|
|---|---|---|
|user|用户表|username(唯一), password(BCrypt加密), status|
|address|收货地址|user_id, receiver_name, is_default|
|category|商品分类|parent_id, name, sort|
|product|商品表|category_id, price, stock, sales, status|
|order_info|订单表|order_no(唯一), user_id, total_amount, status|
|order_item|订单明细|order_id, product_id, quantity, price|
|payment|支付记录|order_no, amount, status, trade_no|

所有表使用 MyBatis-Plus 逻辑删除 (deleted=0/1)，id 使用雪花算法自动生成。

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- MySQL 8.0+（宿主机直接安装）
- Redis 7.x（宿主机直接安装）
- Docker & Docker Compose（仅用于运行 Nacos / Kafka / Sentinel 等中间件）

### 1. 初始化数据库

在宿主机 MySQL 上执行初始化脚本，创建 `cloud_mall` 和 `nacos_config` 两个数据库及所有表：

```bash
mysql -u root -p < sql/init.sql
```

> 如果 MySQL 的 root 密码不是 `root`，请同步修改 `docker/.env` 中的 `MYSQL_PASSWORD` 和 `docker/nacos/conf/application.properties` 中的 `db.password.0`。

### 2. 配置环境变量

编辑 `docker/.env`，按需修改以下变量：

|变量|默认值|说明|
|---|---|---|
|MYSQL_USER|root|MySQL 用户名|
|MYSQL_PASSWORD|root|MySQL 密码|
|MYSQL_PORT|3306|MySQL 端口|
|REDIS_PASSWORD|redis|Redis 密码|
|KAFKA_HOST|localhost|Kafka 广播地址（Spring Boot 与 Docker 在同一机器时无需修改）|
|NACOS_ADDR|localhost:8848|Nacos 地址|
|SENTINEL_ADDR|localhost:8858|Sentinel 地址|

### 3. 启动中间件（Docker）

```bash
cd docker
docker compose up -d
```

|服务|端口|账号/密码|说明|
|---|---|---|---|
|Nacos 2.5|8848|nacos / nacos|注册+配置中心|
|Kafka 3.9|9092|-|自动创建 Topic|
|Kafka UI|8088|-|Kafka 管理界面|
|Sentinel|8858|admin / admin123|熔断限流控制台|

|服务|端口|部署方式|说明|
|---|---|---|---|
|MySQL 8.0+|3306|宿主机|已安装，需手动执行 init.sql|
|Redis 7.x|6379|宿主机|已安装，需配置密码|

### 4. 编译项目

```bash
export JAVA_HOME=/path/to/jdk-21
mvn clean compile -DskipTests
```

### 5. 启动服务

按依赖顺序启动：

```bash
# 先启动不依赖其他服务的基础服务
mvn spring-boot:run -pl cloud-gateway
mvn spring-boot:run -pl cloud-auth
mvn spring-boot:run -pl cloud-user
mvn spring-boot:run -pl cloud-product

# 再启动有 Feign 依赖的服务
mvn spring-boot:run -pl cloud-cart
mvn spring-boot:run -pl cloud-order
mvn spring-boot:run -pl cloud-payment
```

或 IDE 中逐个运行各服务的 `*Application.java` 的 main 方法。

### 6. 验证

访问 Nacos 控制台确认所有服务已注册：<http://localhost:8848>

API 测试：

```bash
# ======== 认证 ========

# 注册用户
curl -X POST "http://localhost:8080/api/auth/register" \
  -d "username=test&password=123456&nickname=测试用户"

# 登录（保存返回的 token）
curl -X POST "http://localhost:8080/api/auth/login" \
  -d "username=test&password=123456"
# → {"code":200,"data":"eyJhbGciOiJIUzI1NiJ9..."}

export TOKEN="<上面返回的token>"

# ======== 商品 ========

# 查看商品列表
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/product/list?page=1&size=10"

# 添加商品（需要先手动插入测试数据或调接口）
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone 16","price":6999,"stock":100,"categoryId":1}' \
  "http://localhost:8080/api/product"

# ======== 购物车 ========

# 加入购物车
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/cart/add?productId=1&quantity=2"

# 查看购物车
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/cart/list"

# ======== 订单 ========

# 添加收货地址
curl -X POST -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"receiverName":"张三","phone":"13800138000","province":"广东省","city":"深圳市","district":"南山区","detail":"科技园路1号","isDefault":1}' \
  "http://localhost:8080/api/user/address"

# 下单
curl -X POST -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/order/create?addressId=<地址ID>"

# 查看订单
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/order/list?page=1&size=10"

# ======== 支付 ========

# 查看支付记录
curl -H "Authorization: Bearer $TOKEN" \
  "http://localhost:8080/api/payment/<订单号>"
```

## 环境变量

各服务通过环境变量配置中间件连接地址，默认值指向本地：

|变量|默认值|说明|
|---|---|---|
|NACOS_ADDR|localhost:8848|Nacos 地址|
|MYSQL_ADDR|localhost:3306|MySQL 地址|
|MYSQL_USER|root|MySQL 用户名|
|MYSQL_PASSWORD|root123|MySQL 密码|
|REDIS_ADDR|localhost|Redis 地址|
|REDIS_PORT|6379|Redis 端口|
|REDIS_PASSWORD|redis123|Redis 密码|
|KAFKA_ADDR|localhost:9092|Kafka 地址|
|SENTINEL_ADDR|localhost:8858|Sentinel 地址|

生产环境可通过 `-e` 或 `export` 覆盖。

## 安全设计

- **密码加密**：BCrypt 单向哈希，不可逆，即使数据库泄露也无法还原明文
- **JWT 认证**：Gateway 层统一校验，白名单 URL (login/register/health) 放行，其余请求强制携带 Token
- **用户隔离**：Controller 从 `X-User-Id` 请求头获取用户身份（Gateway 解析 JWT 后注入），防止越权访问其他用户数据
- **Feign 传递上下文**：`FeignRequestInterceptor` 将当前请求的 `X-User-Id`、`X-Username` 透传到 Feign 调用
- **密码脱敏**：`getUserInfo` 返回前将 `password` 字段置为 `null`
- **SQL 注入防护**：MyBatis-Plus LambdaQueryWrapper 使用类型安全查询，编译期检查字段名
