## SpringMVC--@RequestMapping注解标注方法解析

> 本文是基于springboot进行源码追踪分析

### 问题

- `@RequestMapping`注释的类及方法，Spring是何时，何种方式解析成url与方法的映射关系的？

### 背景

- `@RequestMapping`注解的解析识别工作是由`RequestMappingHandlerMapping`类去完成的，会生成对应的`RequestMappingInfo`实例
- `RequestMappingHandlerMapping`类的位置是在`org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.EnableWebMvcConfiguration`通过`@Bean`注解声明的
- `org.springframework.context.support.AbstractApplicationContext#invokeBeanFactoryPostProcessors`方法解析通过`@Bean`标记的方法，将对应对象转换为`org.springframework.context.annotation.ConfigurationClassBeanDefinitionReader.ConfigurationClassBeanDefinition`注册到`org.springframework.beans.factory.support.DefaultListableBeanFactory`
- `org.springframework.beans.factory.support.DefaultListableBeanFactory#preInstantiateSingletons`方法中实例化对象，并在`org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#invokeInitMethods`方法中调用`org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping#afterPropertiesSet`方法实现`@RequestMapping`注解类及方法的解析与注册

> `org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration`中定义了WEB MVC相关的自动配置类，就比如`org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping`、`org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter`等等的实例化，

### 解析

#### 类继承图

![RequestMappingHandlerMapping](https://img2022.cnblogs.com/blog/2757229/202202/2757229-20220218094527483-1449186988.jpg)

#### 过程

在初始化`RequestMappingHandlerMapping`对象的时候，因为实现了`org.springframework.beans.factory.InitializingBean#afterPropertiesSet`方法，所以会调用`org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#invokeInitMethods`方法时，会调用`RequestMappingHandlerMapping#afterPropertiesSet`方法。

```java
	// RequestMappingHandlerMapping类中相关代码

	public void afterPropertiesSet() {

		this.config = new RequestMappingInfo.BuilderConfiguration();
		this.config.setTrailingSlashMatch(useTrailingSlashMatch());
		this.config.setContentNegotiationManager(getContentNegotiationManager());

		if (getPatternParser() != null) {
			this.config.setPatternParser(getPatternParser());
			Assert.isTrue(!this.useSuffixPatternMatch && !this.useRegisteredSuffixPatternMatch,
					"Suffix pattern matching not supported with PathPatternParser.");
		}
		else {
			this.config.setSuffixPatternMatch(useSuffixPatternMatch());
			this.config.setRegisteredSuffixPatternMatch(useRegisteredSuffixPatternMatch());
			this.config.setPathMatcher(getPathMatcher());
		}
		// 调用父类的方法进行具体的解析
		super.afterPropertiesSet();
	}
```

在自身类的重写方法中进行了一系列的配置，同时调用了父类(`org.springframework.web.servlet.handler.AbstractHandlerMethodMapping`)的`afterPropertiesSet`方法，而具体的解析方法就在父类中。

```java
	// AbstractHandlerMethodMapping中相关代码	

	public void afterPropertiesSet() {
		initHandlerMethods();
	}
	/**
	 * Scan beans in the ApplicationContext, detect and register handler methods.
	 * @see #getCandidateBeanNames()
	 * @see #processCandidateBean
	 * @see #handlerMethodsInitialized
	 */
	protected void initHandlerMethods() {
		for (String beanName : getCandidateBeanNames()) {
			if (!beanName.startsWith(SCOPED_TARGET_NAME_PREFIX)) {
                //处理每个可能的bean
				processCandidateBean(beanName);
			}
		}
		handlerMethodsInitialized(getHandlerMethods());
	}

	protected void processCandidateBean(String beanName) {
		Class<?> beanType = null;
		try {
			beanType = obtainApplicationContext().getType(beanName);
		}
		catch (Throwable ex) {
			// An unresolvable bean type, probably from a lazy bean - let's ignore it.
			if (logger.isTraceEnabled()) {
				logger.trace("Could not resolve type for bean '" + beanName + "'", ex);
			}
		}
        // beanType上是否由@Controller或者@RequestMapping，如果有则说明是一个待解析的RequestMappingInfo
		if (beanType != null && isHandler(beanType)) {
			detectHandlerMethods(beanName);
		}
	}

	protected void detectHandlerMethods(Object handler) {
		Class<?> handlerType = (handler instanceof String ?
				obtainApplicationContext().getType((String) handler) : handler.getClass());

		if (handlerType != null) {
			Class<?> userType = ClassUtils.getUserClass(handlerType);
            //获取该handler内所有的Method与RquestMappingInfo映射关系
			Map<Method, T> methods = MethodIntrospector.selectMethods(userType,
					(MethodIntrospector.MetadataLookup<T>) method -> {
						try {
							return getMappingForMethod(method, userType);
						}
						catch (Throwable ex) {
							throw new IllegalStateException("Invalid mapping on handler class [" +
									userType.getName() + "]: " + method, ex);
						}
					});
			if (logger.isTraceEnabled()) {
				logger.trace(formatMappings(userType, methods));
			}
			else if (mappingsLogger.isDebugEnabled()) {
				mappingsLogger.debug(formatMappings(userType, methods));
			}
			methods.forEach((method, mapping) -> {
				Method invocableMethod = AopUtils.selectInvocableMethod(method, userType);
                //按method将RequestMappingInfo进行注册
				registerHandlerMethod(handler, invocableMethod, mapping);
			});
		}
	}
```

在`AbstractHandlerMethodMapping#detectHandlerMethods`方法中，获取当前bean的所有method与RequestMapping映射关系，并进行注册。

现在继续看`AbstractHandlerMethodMapping#getMappingForMethod`，根据方法名即可猜测，这里就是通过method获取`RequestMappingInfo`，具体的实现方法在`RequestMappingHandlerMapping#createRequestMappingInfo`

```java
// org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping#getMappingForMethod实现代码

	/**
	 * Uses method and type-level @{@link RequestMapping} annotations to create
	 * the RequestMappingInfo.
	 * @return the created RequestMappingInfo, or {@code null} if the method
	 * does not have a {@code @RequestMapping} annotation.
	 * @see #getCustomMethodCondition(Method)
	 * @see #getCustomTypeCondition(Class)
	 */
	@Override
	@Nullable
	protected RequestMappingInfo getMappingForMethod(Method method, Class<?> handlerType) {
        // 根据method创建对应的RquestMappingInfo
		RequestMappingInfo info = createRequestMappingInfo(method);
		if (info != null) {
            // 如果方法所在类上也标注了@RequestMapping，则创建类对应的RequestMappingInfo
			RequestMappingInfo typeInfo = createRequestMappingInfo(handlerType);
			if (typeInfo != null) {
                // 将类与方法的requestMappingInfo进行合并，可以理解为获取完整的url路径
				info = typeInfo.combine(info);
			}
			String prefix = getPathPrefix(handlerType);
			if (prefix != null) {
				info = RequestMappingInfo.paths(prefix).options(this.config).build().combine(info);
			}
		}
        // 返回完整url的RequestMappingInfo对象
		return info;
	}
```

获取到对应的`RequestMappingInfo`之后，就需要进行注册了，下面看注册逻辑`org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#registerHandlerMethod`

```java
	public void register(T mapping, Object handler, Method method) {
			this.readWriteLock.writeLock().lock();
			try {
                // 根据方法生成对应的HandlerMethod，包含了method对应的bean实例，对应的method
				HandlerMethod handlerMethod = createHandlerMethod(handler, method);
                // 校验url路径是否有重复，如果重复会抛出异常
				validateMethodMapping(handlerMethod, mapping);

				Set<String> directPaths = AbstractHandlerMethodMapping.this.getDirectPaths(mapping);
				for (String path : directPaths) {
					this.pathLookup.add(path, mapping);
				}

				String name = null;
				if (getNamingStrategy() != null) {
					name = getNamingStrategy().getName(handlerMethod, mapping);
					addMappingName(name, handlerMethod);
				}

				CorsConfiguration corsConfig = initCorsConfiguration(handler, method, mapping);
				if (corsConfig != null) {
					corsConfig.validateAllowCredentials();
					this.corsLookup.put(handlerMethod, corsConfig);
				}

                //MappingRegistration包含RequestMappingInfo、HandlerMethod等
                //Map<T, MappingRegistration<T>> registry = new HashMap<>();
				this.registry.put(mapping,
						new MappingRegistration<>(mapping, handlerMethod, directPaths, name, corsConfig != null));
			}
			finally {
				this.readWriteLock.writeLock().unlock();
			}
		}
```

### 总结

- 契机：给需要暴漏接口的方法、类上添加`@RequestMapping`注解
- 时机：由于`org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping`间接实现了`org.springframework.beans.factory.InitializingBean#afterPropertiesSet`方法，所以在`RequestMappingHandlerMapping`对象初始化的时候，会调用自身的`org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping#afterPropertiesSet`方法，自身进行一系列配置之后，就会调用父类`org.springframework.web.servlet.handler.AbstractHandlerMethodMapping#afterPropertiesSet`的方法进行`@RequestMapping`标注方法的解析
- 解析：主体流程都是在`AbstractHandlerMethodMapping`类中的方法，具体的实现通过抽象方法的形式让子类`RequestMappingHandlerMapping`进行实现。具体根据method生成对应的RequestMappingInfo是在`RequestMappingHandlerMapping`类中的方法`org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping#getMappingForMethod`。过程中会先解析标注了`@RequestMapping`的方法，生成方法对应的`RequestMappingInfo`实例；如果方法所在的类上也有`@RequestMapping`标注的注解，生成类对应的`RequestMappingInfo`实例，然后将两者进行合并，也就是生成完整的url映射对象
- 注册：在解析完所有的方法之后，将`RequestMappingInfo`进行注册，注册容器位于`AbstractHandlerMethodMapping`类中，容器为 `MappingRegistry mappingRegistry = new MappingRegistry()`，而`MappingRegistry`中实际存储的容器为`Map<T, MappingRegistration<T>> registry = new HashMap<>()`。其中key为对应的`RequestMappingInfo`，value为`MappingRegistration`，其中`MappingRegistration`包含了`RequestMappingInfo`、`HandlerMethod`