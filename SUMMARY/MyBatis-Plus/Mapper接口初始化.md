## 配置

无论是MyBatis还是[MyBatis-Plus](https://baomidou.com/introduce/)，都会有以下基础配置

* pom.xml中引入相关的依赖
* 配置 `MapperScan`，指定Mapper包扫描位置

## 初始化过程

### 1. 解析Mapper成BeanDefinition

@Mapper标注的类交给Spring管理后，因为是接口，最终的实例化class是 `org.mybatis.spring.mapper.MapperFactoryBean`，它是一个FactoryBean

```java
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {

    // 接口对应的class
  private Class<T> mapperInterface;

  private boolean addToConfig = true;

  public MapperFactoryBean() {
    // intentionally empty
  }

  public MapperFactoryBean(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected void checkDaoConfig() {
    super.checkDaoConfig();

    notNull(this.mapperInterface, "Property 'mapperInterface' is required");

    Configuration configuration = getSqlSession().getConfiguration();
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public T getObject() throws Exception {
      //实例化时，会调用getMapper获取到实例化对象
    return getSqlSession().getMapper(this.mapperInterface);
  }
}
```

### 2. Mapper实例化

实例化时，也是调用上述类的 `org.mybatis.spring.mapper.MapperFactoryBean#getObject` 方法就行实例化，即Mapper的实例化.具体代码如下：

其中核心的类是 `org.apache.ibatis.binding.MapperProxyFactory`，它是 Mapper的代理工厂类，通过 jdk的Proxy生成Mapper接口的实例，实例的 InvocationHandler是 `org.apache.ibatis.binding.MapperProxy<T> implements InvocationHandler, Serializable`，属性包括 SqlSession、mapperInterface、methodCache

```java
public class MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T> {
  @Override
  public T getObject() throws Exception {
     // org.mybatis.spring.SqlSessionTemplate#getMapper
    return getSqlSession().getMapper(this.mapperInterface);
  }
} 

public class SqlSessionTemplate implements SqlSession, DisposableBean {
  @Override
  public <T> T getMapper(Class<T> type) {
      // org.apache.ibatis.session.Configuration
    return getConfiguration().getMapper(type, this);
  }
    
}

public class Configuration { 
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
      // org.apache.ibatis.binding.MapperRegistry
    return mapperRegistry.getMapper(type, sqlSession);
  }
}

public class MapperRegistry {

  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }
}

public class MapperProxyFactory<T> {

  @SuppressWarnings("unchecked")
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  public T newInstance(SqlSession sqlSession) {
      // org.apache.ibatis.binding.MapperProxy是一个InvocationHandler
    final MapperProxy<T> mapperProxy = new MapperProxy<>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }
}
```

