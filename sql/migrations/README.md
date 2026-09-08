# 2026-09-07 库存操作账本迁移

这不是初始化脚本，不会自动执行。虚拟机关闭时仅做代码和离线测试，部署前必须人工完成此迁移。

## 前置条件（任何一项无法确认都不要迁移）

1. 停止入口接单；使用旧版本处理完库存补偿、取消订单和 Outbox 重试，核对失败/死信消息。不能简单删除消息当作已处理。
2. 暂停全部后端实例、消费者和定时任务，确认没有在途扣减/回补请求。备份整个 `cloud_mall`，确认备份成功。
3. 核对历史库存、销量与订单；尤其是取消/删除订单必须已正确回补。脚本不会替历史异常自动加库存。没有订单记录的旧失败下单补偿也必须处理完。
4. 检查订单状态、deleted、明细数量无 NULL/非法值；同订单同商品汇总数量必须在 1..2147483647；明细无孤儿引用。任何异常先人工核账。

```sql
SELECT id, order_no FROM order_info
WHERE status IS NULL OR status NOT IN (0,1,2,3,4) OR deleted IS NULL OR deleted NOT IN (0,1);
SELECT i.id FROM order_item i LEFT JOIN order_info o ON o.id=i.order_id
WHERE o.id IS NULL OR i.quantity IS NULL OR i.quantity <= 0 OR i.order_no <> o.order_no;
SELECT order_id, product_id, SUM(quantity) FROM order_item
GROUP BY order_id, product_id HAVING SUM(quantity) < 1 OR SUM(quantity) > 2147483647;
```

以上均应为空。确认后在 Ubuntu 用具备建表权限的 MySQL 账号执行（路径替换为上传后的绝对路径）：

```bash
mysql -h 127.0.0.1 -u YOUR_DB_USER -p cloud_mall < /absolute/path/20260907_inventory_operation.sql
```

不要使用 `--force`。脚本只执行一次；建表失败或回填失败立即停止，保留报错，不要直接重跑或删表。MySQL DDL 会提交，无法用 ROLLBACK 撤销整个文件。

## 行为与部署顺序

- `(order_no, product_id)` 唯一确定操作，数量不可改变。
- 非取消且未删除的历史订单回填 RESERVED；取消或删除订单回填 RESTORED，仅记录状态，不修改商品库存。包含历史明细（包括逻辑删除明细），核账须先确认它们与实际扣减一致。
- 新请求在商品数据库事务中锁定账本，再扣减/恢复；重复请求不重复改库存。恢复先到会留下 RESTORED，迟到扣减被拒绝。
- 迁移成功后一次性更新商品、订单及所有库存接口调用方，再恢复服务；旧调用没有 orderNo 会被拒绝，不支持滚动混用新旧版本。
- 不要清理账本或恢复旧版库存服务来“回滚”。若上线失败，保持停机，保留账本、备份及消息，人工制定一致回退方案。

## 上线验收

验证同订单同商品并发扣减只发生一次、重复恢复只发生一次、恢复先于扣减时库存不增加且后到扣减失败、数量变更被拒绝、库存不足时账本与库存一起回滚。模拟提交成功但响应丢失后重试。

Mockito 测试只证明分支契约，不证明 MySQL 行锁、事务回滚或消息投递可靠性；这些必须在虚拟机开启后验证。
