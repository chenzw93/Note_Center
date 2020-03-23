[TOC]

#### 使用框架 `springboot` + `spring-data-jpa` + `h2`搭建服务，遇到问题记录

[Spring-Data-JPA](https://spring.io/projects/spring-data-jpa)

[H2](http://www.h2database.com/html/functions.html#csvwrite) 

##### 1. `spring-data-jpa`调用`h2`内置函数

如果想利用`spring-data-jpa`调用`h2`的`built-in function`，如导出数据库查询结果到`csv`。`h2`自带function执行为

```sql
CALL CSVWRITE('data/test.csv', 'SELECT * FROM TEST')
```

> **其中第二个参数必须为单引号，如果sql中有单引号，需要转义，使用双单引号。即h2中的转义符为单引号** 

```java
@Query(value= "{call CSVWRITE(:path, 'SELECT * FROM TEST','')}", nativeQuery = true)
public void export(@Param("path") String path)
```

> **两个重要的点：**
>
> 1. @Query中必须设置nativeQuery为true，即为原生sql
> 2. 如果path是可变的，则必须使用{call CSVWRITE(:param, 'sql')}