

#### 窗口函数



#### 继承

[官方网址](https://www.postgresql.org/docs/14/tutorial-inheritance.html)

```postgresql
CREATE TABLE cities (
  name       text,
  population real,
  elevation  int     -- (in ft)
);

CREATE TABLE capitals (
  state      char(2) UNIQUE NOT NULL
) INHERITS (cities);

SELECT name, elevation
  FROM cities
  WHERE elevation > 500;
  
SELECT name, elevation
FROM ONLY cities
WHERE elevation > 500;
```



