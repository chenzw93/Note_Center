[TOC]

## URL中包含特殊字符时，访问url会出现

### 背景

团队提供公共的文件存储及下载服务，由于未限制文件名称，所以文件名称中会包含一些特殊符号(**#**、**;**、**%**)，而这些特殊符号是URL规范中预留或者有其他特殊含义的字符，所以在直接访问的时候，会出现资源访问不存在的情况

### 问题研究

URL中有一些特殊字符，如下

| 字符 | 含义                                                         |
| ---- | ------------------------------------------------------------ |
| #    | 井号作为页面定位符出现在URL中，比如：*http://www.httpwatch.com/features.htm#print* ，此URL表示在页面features.htm中print的位置。浏览器读取这个URL后，会自动将print位置滚动至可视区域 |
| ;    |                                                              |
| %    |                                                              |

知道问题根因之后，处理问题就简单了，其实就是需要对URL进行encode，那么第一次的处理如下

```java
// encode是对url进行转码，但是有个问题是，JDK对于空格转码之后，变成了加号(+)，但是空格真是的unicode编码应该是 %20，所以encode之后，把+替换成了%20
String url = URLEncoder.encode("", StandardCharsets.UTF-8.name()).replace("+","%20");
```

前端拿到转义后的url进行访问，直接访问服务，没有问题，可以正常访问。但是项目中URL是经过`Nginx`进行转发的，在URL通过`Nginx`后，发现又访问不了了，这下蒙圈了，这是什么原因呢？

经过调查，发现`Nginx`对转发的URL会进行一次`decode`，所以一次转义后的请求，到了`Nginx`之后，`Nginx`进行了一次`decode`，那么对于上述特殊字符，又出现了URL被截断的现象

知道原因之后，在代码中对URL进行两次encode，如下

```java
// encode是对url进行转码，但是有个问题是，JDK对于空格转码之后，变成了加号(+)，但是空格真是的unicode编码应该是 %20，所以encode之后，把+替换成了%20
String url = URLEncoder.encode(URLEncoder.encode("", StandardCharsets.UTF-8.name()).replace("+","%20"), StandardCharsets.UTF-8.name()).replace("+","%20");
```

这样请求通过`Nginx`后进行一次decode，这个时候，URL还是encode之后的，所以不会出现被截断的请求

那么通过服务访问文件时，还需要进行一次decode，才能识别到真实的文件名

```java
//SpringBoot服务配置如下
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String encodedPath = UriUtils.encodePath("/path/to/files/with#semicolon.txt", "UTF-8");
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("/usr/local/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        String decodedResourcePath = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8);
                        return super.getResource(decodedResourcePath, location);
                    }
                });
    }
}
```

