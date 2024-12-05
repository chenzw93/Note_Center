基于PostgreSQL 14.x版本

http://www.postgres.cn/docs/14/explicit-locking.html

# 表级锁

## 分类

### Access share

所有select查询，都会在表上加这个锁

### Row share

select... for update 及 select for share会添加这个锁

### Row Exclusive

执行 update、delete、insert时，目标表获取这个锁

### Share update exclusive

执行VACUM(不带FULL)、analyze、create index concurrently、reindex concurrently、create statistics、 **alter index**、**alter table**

### share

执行 **create index（不带concurrently）**取得

### share row exclusive

**create trigge**r和**某些 alter table**

### exclusive

由`REFRESH MATERIALIZED VIEW CONCURRENTLY`获得

### access exclusive

由 alter table、drop table、truncate、reindex、cluster、vacum、refresh materialized view（不带concurrently）命令获取。

很多形式的`ALTER INDEX`和`ALTER TABLE`也在这个层面上获得锁（见[ALTER TABLE](http://www.postgres.cn/docs/14/sql-altertable.html)）。这也是未显式指定模式的`LOCK TABLE`命令的默认锁模式

**只有一个`ACCESS EXCLUSIVE`锁阻塞一个`SELECT`（不带`FOR UPDATE/SHARE`）语句。**

## 冲突的锁模式

一旦被获取，一个锁通常将被持有直到事务结束。 但是如果在建立保存点之后才获得锁，那么在回滚到这个保存点的时候将立即释放该锁。 这与`ROLLBACK`取消保存点之后所有的影响的原则保持一致。 同样的原则也适用于在PL/pgSQL异常块中获得的锁：一个跳出块的错误将释放在块中获得的锁。

| 请求的锁模式         | 已存在的锁模式 |             |                      |         |                   |         |                |      |
| -------------------- | -------------- | ----------- | -------------------- | ------- | ----------------- | ------- | -------------- | ---- |
| `ACCESS SHARE`       | `ROW SHARE`    | `ROW EXCL.` | `SHARE UPDATE EXCL.` | `SHARE` | `SHARE ROW EXCL.` | `EXCL.` | `ACCESS EXCL.` |      |
| `ACCESS SHARE`       |                |             |                      |         |                   |         |                | X    |
| `ROW SHARE`          |                |             |                      |         |                   |         | X              | X    |
| `ROW EXCL.`          |                |             |                      |         | X                 | X       | X              | X    |
| `SHARE UPDATE EXCL.` |                |             |                      | X       | X                 | X       | X              | X    |
| `SHARE`              |                |             | X                    | X       |                   | X       | X              | X    |
| `SHARE ROW EXCL.`    |                |             | X                    | X       | X                 | X       | X              | X    |
| `EXCL.`              |                | X           | X                    | X       | X                 | X       | X              | X    |
| `ACCESS EXCL.`       | X              | X           | X                    | X       | X                 | X       | X              | X    |

# 行级锁

## 分类

### for update

**select --- for update** 查询到的行会被锁定，阻止被其他事务锁定、修改或者删除，直到事务结束

任何一行上执行**Delete**命令，也会获取for update锁模式

**update 外键** 会获取锁模式

### for no key update

与for update类似，锁更弱，是个排他锁

任何不获取 for update 锁的 update会获得这种锁模式

### for share

与for no key update 类似，是个共享锁而非排他锁。

阻塞其他事务在这些行上执行`UPDATE`、`DELETE`、`SELECT FOR UPDATE`或者`SELECT FOR NO KEY UPDATE`，但是它不会阻止它们执行`SELECT FOR SHARE`或者`SELECT FOR KEY SHARE`

### for key share

行为与`FOR SHARE`类似，不过锁较弱：`SELECT FOR UPDATE`会被阻塞，但是`SELECT FOR NO KEY UPDATE`不会被阻塞。一个键共享锁会阻塞其他事务执行修改键值的`DELETE`或者`UPDATE`，但不会阻塞其他`UPDATE`，也不会阻止`SELECT FOR NO KEY UPDATE`、`SELECT FOR SHARE`或者`SELECT FOR KEY SHARE`。

## 冲突的行级锁

PostgreSQL不会在内存里保存任何关于已修改行的信息，因此对一次锁定的行数没有限制。 不过，锁住一行会导致一次磁盘写，例如， `SELECT FOR UPDATE`将修改选中的行以标记它们被锁住，并且因此会导致磁盘写入

**冲突的行级锁**

| 要求的锁模式      | 当前的锁模式  |           |                   |            |
| ----------------- | ------------- | --------- | ----------------- | ---------- |
|                   | FOR KEY SHARE | FOR SHARE | FOR NO KEY UPDATE | FOR UPDATE |
| FOR KEY SHARE     |               |           |                   | X          |
| FOR SHARE         |               |           | X                 | X          |
| FOR NO KEY UPDATE |               | X         | X                 | X          |
| FOR UPDATE        | X             | X         | X                 | X          |

# 页级锁

页面级别的共享/排他锁被用来控制对共享缓冲池中表页面的读/写。 这些锁在行被抓取或者更新后马上被释放。应用开发者通常不需要关心页级锁，我们在这里提到它们只是为了完整

# 死锁

防止死锁的最好方法通常是保证所有使用一个数据库的应用都以一致的顺序在多个对象上获得锁。在上面的例子里，如果两个事务以同样的顺序更新那些行，那么就不会发生死锁。 我们也应该保证一个事务中在一个对象上获得的第一个锁是该对象需要的最严格的锁模式。如果我们无法提前验证这些，那么可以通过重试因死锁而中断的事务来即时处理死锁。

只要没有检测到死锁情况，寻求一个表级或行级锁的事务将无限等待冲突锁被释放。这意味着一个应用长时间保持事务开启不是什么好事（例如等待用户输入）

# 咨询锁

