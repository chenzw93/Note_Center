#### `ConcurrentHashmap `与 `HashMap` 相关学习记录

[TOC]

#### 1. 源码相关

##### 1.1 init

###### 1.1.1  默认初始化大小都为16，扩展因子0.75，size长度永远为2的幂次



##### 1.2 put

###### 1.2.1 `HashMap`  put()

```java
	public V put(K key, V value) {
        return putVal(hash(key), key, value, false, true);
    }

    /**
     * Implements Map.put and related methods
     *
     * @param hash hash for key
     * @param key the key
     * @param value the value to put
     * @param onlyIfAbsent if true, don't change existing value
     * @param evict if false, the table is in creation mode.
     * @return previous value, or null if none
     */
    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        else {
            Node<K,V> e; K k;
            if (p.hash == hash &&
                ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            else {
                for (int binCount = 0; ; ++binCount) {
                    if ((e = p.next) == null) {
                        p.next = newNode(hash, key, value, null);
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;
                }
            }
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        return null;
    }
```

putVal有四个参数，

> onlyIfAbsent：true时，不覆盖已存在的值;
>
> evict：当前处于某种状态
1. 数组为null或者长度为0，初始化数组

2. 对应位置值为null则直接添加

3. 此处节点已经有值，
    3.1 根据hash值跟key值判断是否已经存在，覆盖原有值
    3.2 如果此处是树节点，按树节点插入
    3.3 否则，循环如果有重复则覆盖，无重复则添加到链表最后边，如果大于等于7，则转成树结构，
    如果存在返回原来的值，不存在，返回null
  
4. ++modcount
    如果数量大于阈值threshold，就要resize；下面附上resize()过程
    
###### 1.2.2 `HashMap`  resize()

~~~java
```java
/**
     * Initializes or doubles table size.  If null, allocates in
     * accord with initial capacity target held in field threshold.
     * Otherwise, because we are using power-of-two expansion, the
     * elements from each bin must either stay at same index, or move
     * with a power of two offset in the new table.
     *
     * @return the table
     */
    final Node<K,V>[] resize() {
        Node<K,V>[] oldTab = table;
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        if (oldCap > 0) {
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                     oldCap >= DEFAULT_INITIAL_CAPACITY)
                newThr = oldThr << 1; // double threshold
        }
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                      (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
            Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab;
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    else { // preserve order
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }

~~~

1. 判断容器容量是否大于0，如果是，
  1.1如果再大于等于1<<30最大值，则更改threshold为integer最大值，返回原map
  1.2 新的容量扩大一倍小于1<<30，并且老map的容量大于等于16，则新的threshold扩大一倍
  2如果旧的threshold大于0，则新容量为旧的threshold大小

2. 否则初始化，容量16，threshold为16*0.75=12

3. 新的threshold为0。。。。

4. 如果旧的map不为null，则遍历整个hashmap，
  如果某索引的节点的值不为null：

  5.1 如果此处位置只有一个值，即e. next为null，则重新计算该值在新hash表里的新位置，直接赋值
  5.2 如果该处是树结构，则按树结构处理
  5.3 如果此索引处为链表结构，开始遍历链表
  **因为map长度为2的倍数，所以新位置相当于hash值多计算了一位，这一位值得值刚好为旧容器的容量长度，所以新位置的索引位置要不就是0+旧容器长度，要不就是老索引+旧容器长度，依次遍历整个链表，这样旧链表在新容器中就会可能占据两个索引位置。两个位置可能分别为老位置和老位置加+旧容器长度的和的索引位置**

###### 1.2.3 `ConcurrentHashMap` put()

```java
/**
     * Maps the specified key to the specified value in this table.
     * Neither the key nor the value can be null.
     *
     * <p>The value can be retrieved by calling the {@code get} method
     * with a key that is equal to the original key.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     * @return the previous value associated with {@code key}, or
     *         {@code null} if there was no mapping for {@code key}
     * @throws NullPointerException if the specified key or value is null
     */
    public V put(K key, V value) {
        return putVal(key, value, false);
    }

    /** Implementation for put and putIfAbsent */
    final V putVal(K key, V value, boolean onlyIfAbsent) {
        if (key == null || value == null) throw new NullPointerException();
        int hash = spread(key.hashCode());
        int binCount = 0;
        for (Node<K,V>[] tab = table;;) {
            Node<K,V> f; int n, i, fh;
            if (tab == null || (n = tab.length) == 0)
                tab = initTable();
            else if ((f = tabAt(tab, i = (n - 1) & hash)) == null) {
                if (casTabAt(tab, i, null,
                             new Node<K,V>(hash, key, value, null)))
                    break;                   // no lock when adding to empty bin
            }
            else if ((fh = f.hash) == MOVED)
                tab = helpTransfer(tab, f);
            else {
                V oldVal = null;
                synchronized (f) {
                    if (tabAt(tab, i) == f) {
                        if (fh >= 0) {
                            binCount = 1;
                            for (Node<K,V> e = f;; ++binCount) {
                                K ek;
                                if (e.hash == hash &&
                                    ((ek = e.key) == key ||
                                     (ek != null && key.equals(ek)))) {
                                    oldVal = e.val;
                                    if (!onlyIfAbsent)
                                        e.val = value;
                                    break;
                                }
                                Node<K,V> pred = e;
                                if ((e = e.next) == null) {
                                    pred.next = new Node<K,V>(hash, key,
                                                              value, null);
                                    break;
                                }
                            }
                        }
                        else if (f instanceof TreeBin) {
                            Node<K,V> p;
                            binCount = 2;
                            if ((p = ((TreeBin<K,V>)f).putTreeVal(hash, key,
                                                           value)) != null) {
                                oldVal = p.val;
                                if (!onlyIfAbsent)
                                    p.val = value;
                            }
                        }
                    }
                }
                if (binCount != 0) {
                    if (binCount >= TREEIFY_THRESHOLD)
                        treeifyBin(tab, i);
                    if (oldVal != null)
                        return oldVal;
                    break;
                }
            }
        }
        addCount(1L, binCount);
        return null;
    }
```

 1.1 key value 都不能为null
    1.2 先根据key的hash值计算扰动后的hash（如果本身实现的hashcode方法不理想，会扰动）
1.3 遍历node<K,V>[]，如果table是空，先初始化；
1.3.1 如果多线程初始化，解决的办法是使用CAS，默认初始化容量16，0.75，12等，有个主要变量sizeCtl，值为12，线程Thread. yield()
1.4 如果当前位置处value为null，则直接将值使用CAS存放
1.5 helptransfer方法
1.6 此时说明已经有了冲突，此时重点是使用Synchronized整体加锁，使用的锁是当前位置的Node头节点；
1.6.1 在判断一次值是否一样，
如果当前移动因子为正，说明此时没有结构变化，遍历没个node链表，已经存在的key，就会变更为新值，返回旧值
如果不存在，则添加新的node节点到链表最后
1.6.2 如果此处节点为树节点，则用树存放方式存储
1.6.3 在上边两者处理的时候，会记录此时链表的长度，如果大于阈值8，会链表转成树，

##### 1.3 get

```java
/**
     * Returns the value to which the specified key is mapped,
     * or {@code null} if this map contains no mapping for the key.
     *
     * <p>More formally, if this map contains a mapping from a key
     * {@code k} to a value {@code v} such that {@code (key==null ? k==null :
     * key.equals(k))}, then this method returns {@code v}; otherwise
     * it returns {@code null}.  (There can be at most one such mapping.)
     *
     * <p>A return value of {@code null} does not <i>necessarily</i>
     * indicate that the map contains no mapping for the key; it's also
     * possible that the map explicitly maps the key to {@code null}.
     * The {@link #containsKey containsKey} operation may be used to
     * distinguish these two cases.
     *
     * @see #put(Object, Object)
     */
    public V get(Object key) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }

    /**
     * Implements Map.get and related methods
     *
     * @param hash hash for key
     * @param key the key
     * @return the node, or null if none
     */
    final Node<K,V> getNode(int hash, Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
        if ((tab = table) != null && (n = tab.length) > 0 &&
            (first = tab[(n - 1) & hash]) != null) {
            if (first.hash == hash && // always check first node
                ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            if ((e = first.next) != null) {
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                do {
                    if (e.hash == hash &&
                        ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }
```

1. 判断数组不为null且长度大于0且key对应索引处的值不为null，如果其中一个不满足，返回null
1.1 如果key的hash值相等，且key值相等或者key！=null且key. equal（k），直接返回该node
1.2 如果头节点不符合条件，查看头节点的next，如果next不为null，则便利该节点，如果是树，按树结构获取，否则按链表遍历，条件同头节点获取条件一致

#### 2. 相关问题

##### 2.1 为什么使用扰动函数？

因为map为了快速计算key值对应的数据数组所在的索引位置，采用key的hash值&(n-1)，这样其实只是根据hash值低位参与计算获取索引的值，如果hash不够散乱，发生hash冲突概率会提升。即hash值一定要够散乱，源码采用前16位异或低16位，这样保证hash的低位、高位都能参与运算

##### 2.2 高低位采取异或？

保证了对象的 hashCode 的 32 位值只要有一位发生改变，整个 hash() 返回值就会改变。尽可能的减少碰撞

##### 2.3 为什么Map的长度采用2的幂次长度？

1. 方便快速计算key所处的索引值，`index=hash & (n-1)`

2. 待补充