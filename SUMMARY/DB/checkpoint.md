## 介绍

 WAL序列的一个点，在这个点上可以保证堆和索引数据文件已经被更新，包括所有来自检查点之前被修改过的 shared_memory 的信息；一个checkpoint record 被写入并刷新到 WAL 以标记该点。

> 由[*instance*](http://postgres.cn/docs/14/glossary.html#GLOSSARY-INSTANCE)的公共进程使用的RAM。 它可以镜像部分[*database*](http://postgres.cn/docs/14/glossary.html#GLOSSARY-DATABASE)文件，为[*WAL records*](http://postgres.cn/docs/14/glossary.html#GLOSSARY-WAL-RECORD)提供一个临时区域，并存储其他公共信息。注意，**共享内存属于整个实例，而不是单个数据库**。
>
> 共享内存中最大的部分称为***shared buffers***，用于镜像组成页面的部分数据文件。 当页面被修改时，它被称为**脏页面**，直到它被写回文件系统。

检查点也是执行到达上述定义的检查点所需的所有操作的行为。当满足预定义的条件时，如已经过了指定的时间，或者已写入了一定数量的记录，则启动此过程；也可以由用户通过命令 CHECKPOINT 来调用

## 作用

* **保证事务的持久性** 

  > PostgreSQL 使用事务日志（Write-Ahead Logging, WAL）来保证数据库的持久性。在发生崩溃时，WAL 日志可以帮助恢复到一致性状态。Checkpoint 通过将内存中缓冲区的脏页（即被修改但尚未写入磁盘的页面）强制写入磁盘，确保数据库的持久性和一致性。它确保了以下几点：
  >
  > * 数据页从内存刷新到磁盘。
  > * 相关的 WAL 日志也被刷新，以确保恢复时能够应用所有必要的操作。

* **加速数据库恢复** 

  > 在 PostgreSQL 崩溃恢复时，数据库会根据 **WAL** 日志**重做**操作，恢复到崩溃时的状态。Checkpoint 可以减小恢复时间，因为它确保在发生崩溃时，所有的更新操作已经被写入磁盘，因此恢复时可以从最近的 checkpoint 开始，而不需要回滚太多日志。

* **释放事务日志空间** 

  > PostgreSQL 的 WAL 日志文件会不断增加，直到被清理或归档。Checkpoint 是清理和回滚过期日志的关键时机。在 checkpoint 时，WAL 文件中已写入磁盘的数据可以安全地被丢弃或归档，减少磁盘空间的占用。

* **性能优化** 

  > Checkpoint 的执行虽然会增加磁盘 I/O 负担，但也有助于避免事务日志过多，导致数据库的性能下降。合理的 checkpoint 频率可以平衡写入性能与恢复效率。

## 时机

触发的因素

* **基于时间间隔的触发** 

  > 默认情况下，PostgreSQL 会根据 `checkpoint_timeout` 参数的设定时间来触发 checkpoint。`checkpoint_timeout` 是一个可配置的参数，表示两次 checkpoint 之间的最大时间间隔。默认值通常为 5 分钟，但可以根据实际负载和恢复需求进行调整。
  >
  > `SHOW checkpoint_timeout;`
  >
  > 该参数的值可以通过 `postgresql.conf` 文件配置：`checkpoint_timeout = 5min  # 设置 checkpoint 最大间隔时间为 5 分钟`

* **基于 WAL 日志大小的触发** 

  > 当 WAL 日志写入达到一定的大小后，也会触发 checkpoint。这个大小由 `checkpoint_completion_target` 和 `max_wal_size` 参数控制：* 
  >
  > * `max_wal_size`: 设置在触发 checkpoint 前，WAL 文件允许写入的最大大小。
  >
  > * `checkpoint_completion_target`: 控制 checkpoint 触发时的 I/O 负载。这个参数决定了 PostgreSQL 在 checkpoint 完成时希望完成多少的 WAL 刷新。如果这个值设置为 1，表示尽可能将所有操作完成在 checkpoint 之前，降低性能冲击；如果设置为 0.5，表示更快速地完成，可能会带来更高的磁盘 I/O。
  >
  > 例：
  >
  > ```sql
  > SHOW max_wal_size;
  > SHOW checkpoint_completion_target;
  > ```

* **基于活动的触发**

  > 在某些情况下，系统会因为活动的需要而触发 checkpoint。例如，执行 `pg_stat_bgwriter` 视图中的 **bgwriter** 进程或者当系统认为存在性能瓶颈时，可能会强制执行 checkpoint。

* **手动触发** 

  > 管理员可以手动触发 checkpoint，通过执行 SQL 命令：`CHECKPOINT;` 这个命令会立即强制进行 checkpoint 操作

## 过程

当 PostgreSQL 执行 checkpoint 时，以下操作会依次发生：

* **刷新脏页**：将内存中的所有脏数据页（modified buffer）写入磁盘。
* **刷新 WAL 日志**：将所有未写入磁盘的 WAL 日志刷新到磁盘，以确保数据库崩溃后能够通过 WAL 恢复。
* **更新控制文件**：更新数据库控制文件，记录当前的 checkpoint 位置信息。
* **清理旧的 WAL 日志文件**：旧的 WAL 文件（即已经被 checkpoint 覆盖的日志）会被删除或者归档。

## 参数

* **`checkpoint_timeout`**

  - 默认值：`5min`（5 分钟）

  - 作用：设置两次 checkpoint 之间的最大时间间隔。超过这个时间，就会触发一次 checkpoint。

* **`checkpoint_completion_target`**

  - 默认值：`0.5`

  - 作用：这个参数控制 checkpoint 的 I/O 完成目标。值为 0 时，PostgreSQL 会尽快完成 checkpoint，可能导致 I/O 峰值；值为 1 时，PostgreSQL 会尽可能平滑地完成 checkpoint。

* **`max_wal_size`**

  - 默认值：`1GB`

  - 作用：设置在触发 checkpoint 之前，最大允许写入的 WAL 日志文件大小。

* **`min_wal_size`**

  - 默认值：`80MB`

  - 作用：设置在系统需要回滚或清理 WAL 时，WAL 文件的最小大小。

* **`wal_buffers`**

  - 默认值：`16MB`

  - 作用：设置 WAL 缓冲区的大小，影响 WAL 写入性能。

* **`archive_mode`**

  - 默认值：`off`

  - 作用：启用归档模式，允许将已完成的 WAL 文件保存到备份介质中。

## 性能优化

* **调整 `checkpoint_timeout` 和 `max_wal_size`**： 如果你有非常频繁的写入操作，增加 `checkpoint_timeout` 和 `max_wal_size` 的值可以减少 checkpoint 的触发频率，减少磁盘 I/O 负担，但会增加恢复时间。

* **适当配置 `checkpoint_completion_target`**： 将 `checkpoint_completion_target` 设置为较高的值（如 0.9）可以让 checkpoint 更加平滑，避免短时间内的高负载，从而减少对系统性能的影响。

* **监控 `pg_stat_bgwriter`**： 可以通过 `pg_stat_bgwriter` 视图来监控后台写入进程的活动情况，检查是否有大量的脏页未被写入磁盘，进而判断是否需要调整 checkpoint 配置。

