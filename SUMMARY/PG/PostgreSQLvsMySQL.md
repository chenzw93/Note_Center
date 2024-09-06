## like使用

在MySQL中，建立索引之后，如果使用左确认值匹配，是能使用到索引的。但是在PostgreSQL中，如果只是默认创建了索引，使用最左匹配，是用不到索引的，需要创建索引时，添加 一个关键字 `text_pattern_ops`

```shell
idxdemo=> create index ix_title2 on movies (title text_pattern_ops);
CREATE INDEX

idxdemo=> explain select title from movies where title like 'T%';
                                 QUERY PLAN
-----------------------------------------------------------------------------
 Bitmap Heap Scan on movies  (cost=236.08..1085.19 rows=8405 width=17)
   Filter: (title ~~ 'T%'::text)
   ->  Bitmap Index Scan on ix_title2  (cost=0.00..233.98 rows=8169 width=0)
         Index Cond: ((title ~>=~ 'T'::text) AND (title ~<~ 'U'::text))
(4 rows)
```

