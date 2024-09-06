## 背景

使用 `AbstractRoutingDataSource` 做 PostgreSQL 的动态 Schema 切换，即可以无感知切换到指定 schema下进行表操作，减少 sql 语句中的 schema 拼接

### 配置

1. 配置工程使用的dataSource为 `AbstractRoutingDataSource`

   1. 继承 `AbstractRoutingDataSource` ，重写`determineCurrentLookupKey()`方法

      > 根据 determineCurrentLookupKey 方法返回的key值，从AbstractRoutingDataSource类中的`resolvedDataSources`属性中

   2. 调用 `afterPropertiesSet()` 刷新key与dataSource映射关系

## 问题

偶发性（概率极小）出现堆栈溢出异常

## 定位

```java
public abstract class AbstractRoutingDataSource extends AbstractDataSource implements InitializingBean {
	@Nullable
	private Map<Object, DataSource> resolvedDataSources; //key -> datasource 
    @Nullable
	private DataSource resolvedDefaultDataSource; // 如果key找不到指定的dataSource,则使用这个默认的dataSource
    @Nullable
	private Map<Object, Object> targetDataSources; // 设置目标dataSource源，遍历之后，放入到resolvedDataSources
    
    //刷新dataSource缓存
    @Override
	public void afterPropertiesSet() {
		if (this.targetDataSources == null) {
			throw new IllegalArgumentException("Property 'targetDataSources' is required");
		}
		this.resolvedDataSources = CollectionUtils.newHashMap(this.targetDataSources.size());
		this.targetDataSources.forEach((key, value) -> {
			Object lookupKey = resolveSpecifiedLookupKey(key);
			DataSource dataSource = resolveSpecifiedDataSource(value);
			this.resolvedDataSources.put(lookupKey, dataSource);
		});
		if (this.defaultTargetDataSource != null) {
			this.resolvedDefaultDataSource = resolveSpecifiedDataSource(this.defaultTargetDataSource);
		}
	}
    
    protected DataSource determineTargetDataSource() {
		Assert.notNull(this.resolvedDataSources, "DataSource router not initialized");
        // 这里是抽象方法，根据业务需求，定义dataSource的key
		Object lookupKey = determineCurrentLookupKey();
		DataSource dataSource = this.resolvedDataSources.get(lookupKey);
		if (dataSource == null && (this.lenientFallback || lookupKey == null)) {
            
			dataSource = this.resolvedDefaultDataSource;
		}
		if (dataSource == null) {
			throw new IllegalStateException("Cannot determine target DataSource for lookup key [" + lookupKey + "]");
		}
		return dataSource;
	}
    
    @Nullable
	protected abstract Object determineCurrentLookupKey();
}
```

### 分析

`afterPropertiesSet`方法不是线程安全的，即多个线程同时调用此方法，会导致 resolvedDataSources 属性中的key丢失