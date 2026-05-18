# CloudBack 微服务电商系统

基于 Spring Cloud Alibaba 全家桶构建的电商微服务系统，8 个微服务模块协同工作。

## 技术栈

|层级|技术|版本|
|---|---|---|
|语言|Java|21|
|框架|Spring Boot|3.3.9|
|微服务|Spring Cloud|2023.0.3|
|微服务|Spring Cloud Alibaba|2023.0.1.0|
|注册 & 配置中心|Nacos|2.5.x (Docker)|
|API 网关|Spring Cloud Gateway|—|
|安全认证|JWT (jjwt 0.12.6)|—|
|ORM|MyBatis-Plus|3.5.11|
|连接池|Druid|1.2.24|
|缓存|Redis|7.x (宿主机)|
|消息队列|Kafka|3.9.x (Docker)|
|熔断限流|Sentinel|1.8.x (Docker)|
|远程调用|OpenFeign|—|
|工具库|Hutool|5.8.x|
|JSON|Fastjson2|2.0.x|

## 项目结构

```text
CloudBack/
├── pom.xml                              # 父 POM，统一依赖版本管理
├── .env                                 # 中间件连接配置（gitignored）
├── .env.example                         # .env 模板
├── cloud-common/                        # 公共模块
│   └── src/main/java/org/cloudback/common/
│       ├── result/          R.java, ResultCode.java        # 统一响应体
│       ├── entity/          BaseEntity.java, User.java     # 公共实体
│       ├── mapper/          UserMapper.java               # 公共 Mapper
│       ├── exception/       BusinessException.java        # 业务异常
│       │                    GlobalExceptionHandler.java    # 全局异常处理
│       ├── constant/        SystemConstants.java          # 系统常量
│       ├── utils/           JwtUtil.java                  # JWT 工具
│       └── config/          DotenvEnvironmentPostProcessor.java  # .env 加载器
│                            MyBatisPlusConfig.java        # MyBatis 配置
│                            RedisConfig.java              # Redis 配置
│                            AutoFillMetaObjectHandler.java # 字段自动填充
├── cloud-gateway/   :8080   # API 网关 (WebFlux)
├── cloud-auth/      :8081   # 认证服务
├── cloud-product/   :8082   # 商品服务
├── cloud-user/      :8083   # 用户服务
├── cloud-cart/      :8084   # 购物车服务 (Redis Hash)
├── cloud-order/     :8085   # 订单服务 (Kafka 生产者)
├── cloud-payment/   :8086   # 支付服务 (Kafka 消费者)
├── sql/
│   └── init.sql                        # 数据库初始化脚本 (7 张业务表)
└── docker/
    ├── docker-compose.yml              # Nacos / Kafka / Sentinel 编排
    ├── .env                             # Docker Compose 环境变量
    └── nacos/conf/
        └── application.properties      # Nacos 配置
```

## 系统架构

```text
                            ┌─────────────┐
                            │   Browser   │
                            │  Vue 3 前端  │
                            └──────┬──────┘
                                   │ HTTP
                                   ▼
                          ┌────────────────┐
                          │  cloud-gateway │  :8080 (Windows 本机)
                          │  JWT 认证 + CORS │
                          └───────┬────────┘
                                  │ Nacos 服务发现
                    ┌─────────────┼─────────────┐
                    │             │             │
            ┌───────▼──┐  ┌──────▼──┐  ┌──────▼──────┐
            │  Nacos   │  │ Sentinel│  │  lb://路由   │
            │ 注册/配置 │  │ 熔断限流 │  │  到各微服务   │
            └──────────┘  └─────────┘  └──────┬──────┘
                                              │
      ┌────────┬────────┬────────┬───────────┬──────────┬─────────┐
      ▼        ▼        ▼        ▼           ▼          ▼         ▼
  auth:8081 product  user:8083 cart:8084  order:8085 payment:8086
              :8082
      │                  │        │           │          │
      ▼                  │        ▼           │          │
   MySQL:3306 ◄──────────┘    Redis:6379      │          │
  (user/order/product)      (购物车 Hash)      │          │
                                               ▼          ▼
                                         ┌──────────────────┐
                                         │   Kafka :9092    │
                                         │ order-create ──▶ │
                                         │ ◀── payment-result│
                                         └──────────────────┘
```

## 部署架构（混合模式）

|组件|部署位置|方式|
|---|---|---|
|Spring Boot 微服务|Windows 本机|`mvn spring-boot:run`|
|MySQL 8.0+|Ubuntu 24.04 VM|宿主机直接安装|
|Redis 7.x|Ubuntu 24.04 VM|宿主机直接安装|
|Nacos 2.5|VM (Docker)|docker compose|
|Kafka 3.9|VM (Docker)|docker compose|
|Sentinel|VM (Docker)|docker compose|
|Kafka UI|VM (Docker)|docker compose|

通过 `.env` 中的 `VM_HOST=192.168.91.130` 一键切换所有连接目标。

## 快速开始

### 环境要求

- **Windows 本机**：JDK 21、Maven 3.9+
- **Ubuntu 24.04 VM**：MySQL 8.0+、Redis 7.x、Docker & Docker Compose
- VM IP 示例：`192.168.91.130`（实际环境请替换）

---

### 第一步：虚拟机中间件准备

SSH 登录 Ubuntu 24.04 VM，依次执行。

#### 1.1 MySQL — 开放远程访问并导入数据库

```bash
# 1. 修改监听地址
sudo sed -i 's/^bind-address.*/bind-address = 0.0.0.0/' /etc/mysql/mysql.conf.d/mysqld.cnf
sudo systemctl restart mysql

# 2. 上传 sql/init.sql 并导入（创建 cloud_mall 和 nacos_config 两个库 + 7 张业务表）
mysql -u root -p < sql/init.sql

# 3. 导入 Nacos 系统表（Nacos 2.x 需要，否则启动闪退）
#    从 Nacos 官方发行版 nacos/conf/mysql-schema.sql 获取，或：
mysql -u root -p nacos_config < docker/nacos/conf/mysql-schema.sql

# 4. 开放 root 远程访问（若密码不是 root 请替换）
mysql -u root -p -e "
CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY 'root';
GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
"

# 5. 验证
mysql -u root -p -e "USE cloud_mall; SHOW TABLES;"
mysql -u root -p -e "USE nacos_config; SHOW TABLES;"
```

#### 1.2 Redis — 开放远程访问

```bash
# 修改监听地址和密码
sudo sed -i 's/^bind 127.0.0.1/bind 0.0.0.0/' /etc/redis/redis.conf
sudo sed -i 's/^# requirepass .*/requirepass redis/' /etc/redis/redis.conf
sudo systemctl restart redis-server

# 验证
redis-cli -a redis ping   # 应返回 PONG
```

#### 1.3 Docker 中间件

```bash
# 进入 docker 目录
cd docker

# 如果 MySQL root 密码不是 root，先编辑 .env
vim .env    # 改 MYSQL_PASSWORD 为实际密码

# 启动
docker compose up -d

# 确认容器状态
docker compose ps
```

|容器|端口|账号/密码|管理地址|
|---|---|---|---|
|cloud-nacos|8848 / 9848|nacos / nacos|<http://192.168.91.130:8848/nacos>|
|cloud-kafka|9092|—|—|
|cloud-kafka-ui|8088|—|<http://192.168.91.130:8088>|
|cloud-sentinel|8858|admin / admin123|<http://192.168.91.130:8858>|

#### 1.4 防火墙

```bash
sudo ufw allow 3306,6379,8848,9848,9092,8088,8858/tcp
```

---

### 第二步：Windows 本地开发

#### 2.1 配置 .env

```bash
cp .env.example .env
```

编辑 `.env`，只需改一行：

```ini
VM_HOST=192.168.91.130
```

所有中间件地址自动跟随。原理：`application.yml` 中连接地址默认值为 `${VM_HOST:localhost}:端口`，启动时 `DotenvEnvironmentPostProcessor` 自动加载 `.env` 并注入。

#### 2.2 编译项目

```bash
mvn clean install -DskipTests
```

#### 2.3 启动微服务

分两批启动（Gateway 先于其他服务启动）：

```bash
# 第一批：不依赖其他微服务的基础服务
mvn spring-boot:run -pl cloud-gateway
mvn spring-boot:run -pl cloud-auth
mvn spring-boot:run -pl cloud-user
mvn spring-boot:run -pl cloud-product

# 第二批：依赖 Feign 远程调用
mvn spring-boot:run -pl cloud-cart
mvn spring-boot:run -pl cloud-order
mvn spring-boot:run -pl cloud-payment
```

> 启动成功标志：Gateway 日志出现 `[dotenv] Loading ...\.env` 和 `serverIp = '192.168.91.130'`。
>
> 如果在 IDEA 中运行，Run Configuration → Environment variables 添加 `VM_HOST=192.168.91.130`。

### 验证

1. 访问 Nacos 控制台 <http://192.168.91.130:8848/nacos>，服务列表应有 7 个服务且状态正常
2. 启动 CloudFront 前端 `npm run dev`，通过页面操作验证完整流程

---

## 服务调用链路

### 用户登录

```text
POST /api/auth/login (白名单)
  → Gateway → lb://cloud-auth → AuthController.login()
    → BCrypt.checkpw() → JwtUtil.createToken()
  ← { code: 200, data: "<token>" }
```

### 用户下单

```text
POST /api/order/create (Header: Authorization: Bearer <token>)
  │  [Gateway] AuthGlobalFilter 解析 JWT → 注入 X-User-Id
  │
  ├─① Feign → CartService.getCheckedItems()      # Redis Hash
  ├─② Feign → UserService.getAddressById()        # MySQL
  ├─③ Feign → ProductService.deductStock() × N   # MySQL (扣库存)
  ├─④ INSERT order_info + order_item              # MySQL (建订单)
  ├─⑤ Feign → CartService.clearCart()             # Redis (清购物车)
  └─⑥ Kafka.send("order-create")                  # 通知支付服务
         │
         ▼
  [cloud-payment] 消费 order-create → 模拟支付 → INSERT payment
         │
         └─ Kafka.send("payment-result")
              │
              ▼
  [cloud-order] 消费 payment-result → UPDATE order_info SET status=已支付
```

### 购物车 Redis 结构

```text
Key:   cloud:cart:{userId}
Type:  Hash
TTL:   7 天

Field: productId  →  Value: CartItem JSON

示例：
  cloud:cart:10086
    ├── 1001 → {"productId":1001, "productName":"iPhone", "price":6999, "quantity":2, "checked":true}
    └── 2002 → {"productId":2002, "productName":"耳机", "price":299, "quantity":1, "checked":false}
```

## 数据库设计

|表名|说明|关键字段|
|---|---|---|
|user|用户表|username(唯一), password(BCrypt)|
|address|收货地址|user_id, receiver_name, is_default|
|category|商品分类|parent_id, name, sort|
|product|商品表|category_id, price, stock|
|order_info|订单表|order_no(唯一), status(0-4)|
|order_item|订单明细|order_id, product_id, quantity|
|payment|支付记录|order_no, trade_no, status|

所有表使用雪花算法主键 + MyBatis-Plus 逻辑删除 (deleted=0/1)。

## 统一响应格式

```json
{"code": 200, "message": "操作成功", "data": {...}}
```

|code|含义|
|---|---|
|200|操作成功|
|401|未登录或 Token 过期|
|403|无权限|
|1001|用户名或密码错误|
|1002|用户不存在|
|2002|库存不足|
|3001|订单不存在|

## 环境变量

通过项目根目录 `.env` 统一管理，支持 `${KEY}` 引用：

```ini
VM_HOST=192.168.91.130        # 改这一个，下面全跟随
MYSQL_ADDR=${VM_HOST}:3306
REDIS_ADDR=${VM_HOST}
NACOS_ADDR=${VM_HOST}:8848
SENTINEL_ADDR=${VM_HOST}:8858
KAFKA_ADDR=${VM_HOST}:9092
```

|变量|默认值|说明|
|---|---|---|
|VM_HOST|localhost|一级开关，所有连接的默认地址|
|MYSQL_USER|root|MySQL 用户名|
|MYSQL_PASSWORD|root|MySQL 密码|
|REDIS_PASSWORD|redis|Redis 密码|

## 常见问题

### Gateway 启动报 `serverIp = 'localhost'`

`.env` 没有被加载。检查是否先执行了 `mvn clean install -DskipTests`，确保 `cloud-common` 的改动已打包。

### Nacos gRPC 连接超时（9848 端口）

1. 确认 VM 防火墙已放行 9848：`sudo ufw status`
2. 确认 Nacos 容器重启后加载了 `nacos.inetutils.ip-address=192.168.91.130` 配置
3. 确认 docker-compose 端口映射包含 `9848:9848`

### Redis 连接拒绝

Redis 默认只监听 `127.0.0.1`，需改为 `bind 0.0.0.0` 并重启。

### Feign 调用报 `No Feign Client for loadBalancing`

缺少 `spring-cloud-starter-loadbalancer` 依赖。已为 cloud-cart、cloud-order、cloud-product 添加。

### Mapper 扫描不到

各模块的 `@MapperScan` 需包含自己的 mapper 包路径：

```java
// cloud-user 示例
@MapperScan({"org.cloudback.common.mapper", "org.cloudback.user.mapper"})
```

## 安全设计

- **密码加密**：BCrypt 单向哈希
- **JWT 认证**：Gateway 统一校验，白名单放行 login/register
- **用户隔离**：Gateway 解析 JWT 后注入 `X-User-Id`，各服务据此过滤数据
- **密码脱敏**：`getUserInfo` 返回前将 password 字段置 null
- **逻辑删除**：所有表 deleted=1 替代物理删除
