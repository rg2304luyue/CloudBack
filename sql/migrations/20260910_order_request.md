# 下单请求恢复迁移（2026-09-10）

只新增 `order_request` 表，不更新已有订单、支付、商品库存，也不运行旧 `init.sql`。
前提：之前的 `inventory_operation` 库存迁移已经完成。脚本仅供人工执行，不会自动运行。

## 切换前

1. 暂停下单入口，等待当前请求结束；停止全部旧订单服务和相关自动任务。不混用新旧订单服务。
2. 有下单响应不确定的用户，先按其订单列表核对。旧Redis令牌没有请求记录，不能可靠回填或凭令牌找回旧订单。
3. 本轮不清空Redis，不删除历史订单，不重新执行库存迁移。
4. 保留新表与请求记录。不要为“重试”删除请求/库存操作记录，或把COMPENSATING改成FAILED。

## Windows PowerShell：上传文件

```powershell
scp "D:\HuaweiMoveData\Users\陆\Desktop\cloud\CloudBack\sql\migrations\20260910_order_request.sql" luyue@192.168.91.130:/home/luyue/20260910_order_request.sql
ssh luyue@192.168.91.130
```

## Ubuntu：检查与备份

以下在同一个Ubuntu终端执行。数据库账号使用项目 `.env` 的 MYSQL_USER，密码使用 MYSQL_PASSWORD，不要发给别人。

```bash
read -r -p "MySQL 用户名: " db_user
mysql -h 127.0.0.1 -u "$db_user" -p cloud_mall -e "SHOW TABLES LIKE 'inventory_operation'; SHOW TABLES LIKE 'order_request';"
```

第一个查询必须有表，第二个应为空；如果新表已经存在，停止并检查是否已经迁移，不要删表重跑。

```bash
umask 077
backup_dir=$(mktemp -d /home/luyue/cloud-mall-checkout-backup.XXXXXX)
mysqldump -h 127.0.0.1 -u "$db_user" -p --single-transaction --quick --no-tablespaces --set-gtid-purged=OFF --routines --events --triggers --databases cloud_mall > "$backup_dir/cloud_mall.sql"
if [ $? -eq 0 ] && [ -s "$backup_dir/cloud_mall.sql" ]; then
  echo "备份成功：$backup_dir/cloud_mall.sql"
else
  echo "备份失败，停止后续操作"
  exit 1
fi
```

确认备份成功再继续。保持服务暂停，不要同时执行其他表结构变更。

## Ubuntu：执行一次迁移并验证

```bash
mysql -h 127.0.0.1 -u "$db_user" -p cloud_mall < /home/luyue/20260910_order_request.sql
```

不要使用 `--force`；失败立即停止。建表会提交，不能用ROLLBACK撤销该操作。

```bash
mysql -h 127.0.0.1 -u "$db_user" -p cloud_mall -e "SHOW CREATE TABLE order_request; SHOW INDEX FROM order_request; SELECT COUNT(*) AS request_count FROM order_request;"
```

新表应具有token主键、active_user_id唯一索引、order_no唯一索引以及恢复扫描索引。首次迁移且服务未启动时记录数应为0。

之后同步部署新订单服务和前端；其他服务无需执行本次SQL。旧页面刷新后获取新请求编号；旧令牌不再作为新下单的凭证。

## 业务验收

- 地址/购物车等前置校验失败，没有扣库存，可使用同一READY令牌修正后提交。
- 相同请求成功重试返回原订单，订单数与库存变化均只有一次。
- 请求超时后，前端保留原编号查询，不自动换令牌；刷新页面继续确认。
- 处理中/补偿中不允许换新请求绕过；同用户再次获取令牌复用未完成请求。
- 补偿按原订单号逐项重试，全部库存确认恢复后才能FAILED。
- 服务重启后恢复过期PROCESSING及待重试COMPENSATING；请求行锁保证恢复不会与执行同时操作。

离线单元测试不证明实际MySQL锁、进程退出恢复或浏览器运行行为。这些需要执行本次迁移后，使用专用测试数据另行联调。不应把之前版本的库存实测结果当成本次请求状态机制已经实测。
