# [Apache Tomcat如何高并发处理请求](https://www.cnblogs.com/chenzw93/p/16072325.html)

### 介绍

作为常用的`http`协议服务器，`tomcat`应用非常广泛。tomcat也是遵循Servelt协议的，`Servelt`协议可以让服务器与真实服务逻辑代码进行解耦。各自只需要关注`Servlet`协议即可。
对于tomcat是如何作为一个高性能的服务器的呢？你是不是也会有这样的疑问？

tomcat是如何接收网络请求？

如何做到高性能的`http`协议服务器？

> tomcat从8.0往后开始使用了NIO非阻塞io模型，提高了吞吐量，本文的源码是tomcat 9.0.48版本

### 接收Socket请求

`org.apache.tomcat.util.net.Acceptor`实现了`Runnable`接口，在一个单独的线程中以死循环的方式一直进行socket的监听

线程的初始化及启动是在方法`org.apache.tomcat.util.net.AbstractEndpoint#startAcceptorThread`

有个很重要的属性`org.apache.tomcat.util.net.AbstractEndpoint`；同时实现了`run`方法，方法中主要有以下功能：

- 请求最大连接数限制： 最大为 8*1024；请你注意到达最大连接数后操作系统底层还是会接收客户端连接，但用户层已经不再接收
- 获取`socketChannel`

```java
public void run() {
        int errorDelay = 0;
        try {
            // Loop until we receive a shutdown command
            while (!stopCalled) {
					...
                if (stopCalled) {
                    break;
                }
                state = AcceptorState.RUNNING;

                try {
                    //if we have reached max connections, wait
                    // 如果连接超过了 8*1024，则线程阻塞等待; 是使用org.apache.tomcat.util.threads.LimitLatch类实现了分享锁(内部实现了AbstractQueuedSynchronizer)
                    // 请你注意到达最大连接数后操作系统底层还是会接收客户端连接，但用户层已经不再接收。
                    endpoint.countUpOrAwaitConnection();

                    // Endpoint might have been paused while waiting for latch
                    // If that is the case, don't accept new connections
                    if (endpoint.isPaused()) {
                        continue;
                    }

                    U socket = null;
                    try {
                        // Accept the next incoming connection from the server
                        // socket
                        // 抽象方法，不同的endPoint有不同的实现方法。NioEndPoint为例，实现方法为serverSock.accept()，这个方法主要看serverSock实例化时如果为阻塞，accept方法为阻塞；反之为立即返回，如果没有socket链接，则为null
                        socket = endpoint.serverSocketAccept();
                    } catch (Exception ioe) {
                        // We didn't get a socket
                        endpoint.countDownConnection();
                        if (endpoint.isRunning()) {
                            // Introduce delay if necessary
                            errorDelay = handleExceptionWithDelay(errorDelay);
                            // re-throw
                            throw ioe;
                        } else {
                            break;
                        }
                    }
                    // Successful accept, reset the error delay
                    errorDelay = 0;

                    // Configure the socket
                    if (!stopCalled && !endpoint.isPaused()) {
                        // setSocketOptions() will hand the socket off to
                        // an appropriate processor if successful
                        // endPoint类的抽象方法，不同的endPoint有不同的实现。处理获取到的socketChannel链接，如果该socket链接能正常处理，那么该方法会返回true，否则为false
                        if (!endpoint.setSocketOptions(socket)) {
                            endpoint.closeSocket(socket);
                        }
                    } else {
                        endpoint.destroySocket(socket);
                    }
                } catch (Throwable t) {
                    ...
                }
            }
        } finally {
            stopLatch.countDown();
        }
        state = AcceptorState.ENDED;
    }
```

再来看下`org.apache.tomcat.util.net.NioEndpoint#setSocketOptions`方法的具体实现(NioEndpoint为例)

这个方法中主要做的事：

- 创建NioChannel
- 设置socket为非阻塞
- 将socket添加到Poller的队列中

```java
 protected boolean setSocketOptions(SocketChannel socket) {
        NioSocketWrapper socketWrapper = null;
        try {
            // Allocate channel and wrapper
            // 优先使用已有的缓存nioChannel
            NioChannel channel = null;
            if (nioChannels != null) {
                channel = nioChannels.pop();
            }
            if (channel == null) {
                SocketBufferHandler bufhandler = new SocketBufferHandler(
                        socketProperties.getAppReadBufSize(),
                        socketProperties.getAppWriteBufSize(),
                        socketProperties.getDirectBuffer());
                if (isSSLEnabled()) {
                    channel = new SecureNioChannel(bufhandler, this);
                } else {
                    channel = new NioChannel(bufhandler);
                }
            }
            // 将nioEndpoint与NioChannel进行包装
            NioSocketWrapper newWrapper = new NioSocketWrapper(channel, this);
            channel.reset(socket, newWrapper);
            connections.put(socket, newWrapper);
            socketWrapper = newWrapper;

            // Set socket properties
            // Disable blocking, polling will be used
            // 设置当前链接的socket为非阻塞
            socket.configureBlocking(false);
            if (getUnixDomainSocketPath() == null) {
                socketProperties.setProperties(socket.socket());
            }

            socketWrapper.setReadTimeout(getConnectionTimeout());
            socketWrapper.setWriteTimeout(getConnectionTimeout());
            socketWrapper.setKeepAliveLeft(NioEndpoint.this.getMaxKeepAliveRequests());
            // 将包装后的nioChannel与nioEndpoint进行注册，注册到Poller，将对应的socket包装类添加到Poller的队列中，同时唤醒selector
            poller.register(socketWrapper);
            return true;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            try {
                log.error(sm.getString("endpoint.socketOptionsError"), t);
            } catch (Throwable tt) {
                ExceptionUtils.handleThrowable(tt);
            }
            if (socketWrapper == null) {
                destroySocket(socket);
            }
        }
        // Tell to close the socket if needed
        return false;
    }
```

### Socket请求轮询

上一小节是接收到了socket请求，进行包装之后，将socket添加到了`Poller`的队列上，并可能唤醒了`Selector`，本小节就来看看，Poller是如何进行socket的轮询的。

首先`org.apache.tomcat.util.net.NioEndpoint.Poller`也是实现了Runnable接口，是一个可以单独启动的线程

初始化及启动是在`org.apache.tomcat.util.net.NioEndpoint#startInternal`

重要的属性：

- `java.nio.channels.Selector`：在Poller对象初始化的时候，就会启动轮询器
- `SynchronizedQueue<PollerEvent>`：同步的事件队列

再来看下具体处理逻辑，run方法的源码

```java
		public void run() {
            // Loop until destroy() is called
            while (true) {

                boolean hasEvents = false;

                try {
                    if (!close) {
                        // 去SynchronizedQueue事件队列中拉去，看是否已经有了事件，如果有，则返回true
                        // 如果从队列中拉取到了event(即上一步将NioSocketWrapper封装为PollerEvent添加到次队列中)，将socketChannel注册到Selector上，标记为SelectionKey.OP_READ，添加处理函数attachment(为Accetpor添加到Poller时的    
                        // NioSocketWrapper)
                        hasEvents = events();
                        if (wakeupCounter.getAndSet(-1) > 0) {
                            // If we are here, means we have other stuff to do
                            // Do a non blocking select
                            keyCount = selector.selectNow();
                        } else {
                            keyCount = selector.select(selectorTimeout);
                        }
                        wakeupCounter.set(0);
                    }
                    if (close) {
                        events();
                        timeout(0, false);
                        try {
                            selector.close();
                        } catch (IOException ioe) {
                            log.error(sm.getString("endpoint.nio.selectorCloseFail"), ioe);
                        }
                        break;
                    }
                    // Either we timed out or we woke up, process events first
                    if (keyCount == 0) {
                        hasEvents = (hasEvents | events());
                    }
                } catch (Throwable x) {
                    ExceptionUtils.handleThrowable(x);
                    log.error(sm.getString("endpoint.nio.selectorLoopError"), x);
                    continue;
                }

                Iterator<SelectionKey> iterator =
                    keyCount > 0 ? selector.selectedKeys().iterator() : null;
                // Walk through the collection of ready keys and dispatch
                // any active event.
                // selector轮询获取已经注册的事件，如果有事件准备好，此时通过selectKeys方法就能拿到对应的事件
                while (iterator != null && iterator.hasNext()) {
                    SelectionKey sk = iterator.next();
                    // 获取到事件后，从迭代器删除事件，防止事件重复轮询
                    iterator.remove();
                    // 获取事件的处理器，这个attachment是在event()方法中注册的，后续这个事件的处理，就交给这个wrapper去处理
                    NioSocketWrapper socketWrapper = (NioSocketWrapper) sk.attachment();
                    // Attachment may be null if another thread has called
                    // cancelledKey()
                    if (socketWrapper != null) {
                        processKey(sk, socketWrapper);
                    }
                }

                // Process timeouts
                timeout(keyCount,hasEvents);
            }

            getStopLatch().countDown();
        }
```

在这里，有一个很重要的方法，`org.apache.tomcat.util.net.NioEndpoint.Poller#events()`，他是从`Poller`的事件队列中获取`Acceptor`接收到的可用socket，并将其注册到`Selector`上

```java
		/**
         * Processes events in the event queue of the Poller.
         *
         * @return <code>true</code> if some events were processed,
         *   <code>false</code> if queue was empty
         */
        public boolean events() {
            boolean result = false;

            PollerEvent pe = null;
            // 如果Acceptor将socket添加到队列中，那么events.poll()方法就能拿到对应的事件，否则拿不到就返回false
            for (int i = 0, size = events.size(); i < size && (pe = events.poll()) != null; i++ ) {
                result = true;
                NioSocketWrapper socketWrapper = pe.getSocketWrapper();
                SocketChannel sc = socketWrapper.getSocket().getIOChannel();
                int interestOps = pe.getInterestOps();
                if (sc == null) {
                    log.warn(sm.getString("endpoint.nio.nullSocketChannel"));
                    socketWrapper.close();
                } else if (interestOps == OP_REGISTER) {
                    // 如果是Acceptor刚添加到队列中的事件，那么此时的ops就是OP_REGISTER
                    try {，
                        // 将次socket注册到selector上，标记为OP_READ事件，添加事件触发时处理函数socketWrapper
                        sc.register(getSelector(), SelectionKey.OP_READ, socketWrapper);
                    } catch (Exception x) {
                        log.error(sm.getString("endpoint.nio.registerFail"), x);
                    }
                } else {
                    // ？？这里的逻辑，不清楚什么情况下会进入到这个分支里面
                    final SelectionKey key = sc.keyFor(getSelector());
                    if (key == null) {
                        // The key was cancelled (e.g. due to socket closure)
                        // and removed from the selector while it was being
                        // processed. Count down the connections at this point
                        // since it won't have been counted down when the socket
                        // closed.
                        socketWrapper.close();
                    } else {
                        final NioSocketWrapper attachment = (NioSocketWrapper) key.attachment();
                        if (attachment != null) {
                            // We are registering the key to start with, reset the fairness counter.
                            try {
                                int ops = key.interestOps() | interestOps;
                                attachment.interestOps(ops);
                                key.interestOps(ops);
                            } catch (CancelledKeyException ckx) {
                                cancelledKey(key, socketWrapper);
                            }
                        } else {
                            cancelledKey(key, socketWrapper);
                        }
                    }
                }
                if (running && !paused && eventCache != null) {
                    pe.reset();
                    eventCache.push(pe);
                }
            }

            return result;
        }
```

还有一个重要方法就是`org.apache.tomcat.util.net.NioEndpoint.Poller#processKey`，上一个方法是获取event，并注册到selector，那这个方法就是通过`Selector`获取到的数据准备好的event，并开始封装成对应的业务处理线程`SocketProcessorBase`，扔到线程池里开始处理

```java
	    protected void processKey(SelectionKey sk, NioSocketWrapper socketWrapper) {
            try {
                if (close) {
                    cancelledKey(sk, socketWrapper);
                } else if (sk.isValid()) {
                    if (sk.isReadable() || sk.isWritable()) {
                        if (socketWrapper.getSendfileData() != null) {
                            processSendfile(sk, socketWrapper, false);
                        } else {
                            unreg(sk, socketWrapper, sk.readyOps());
                            boolean closeSocket = false;
                            // Read goes before write
                            if (sk.isReadable()) {
                                //这里如果是异步的操作，就会走这里
                                if (socketWrapper.readOperation != null) {
                                    if (!socketWrapper.readOperation.process()) {
                                        closeSocket = true;
                                    }
                                } else if (socketWrapper.readBlocking) {
                                    // readBlocking默认为false
                                    synchronized (socketWrapper.readLock) {
                                        socketWrapper.readBlocking = false;
                                        socketWrapper.readLock.notify();
                                    }
                                } else if (!processSocket(socketWrapper, SocketEvent.OPEN_READ, true)) {
                                    // 处理正常的事件，这里的processSocket就要正式开始处理请求了。
                                    // 将对应的事件封装成对应的线程，然后交给线程池去处理正式的请求业务
                                    closeSocket = true;
                                }
                            }
                            if (!closeSocket && sk.isWritable()) {
                                if (socketWrapper.writeOperation != null) {
                                    if (!socketWrapper.writeOperation.process()) {
                                        closeSocket = true;
                                    }
                                } else if (socketWrapper.writeBlocking) {
                                    synchronized (socketWrapper.writeLock) {
                                        socketWrapper.writeBlocking = false;
                                        socketWrapper.writeLock.notify();
                                    }
                                } else if (!processSocket(socketWrapper, SocketEvent.OPEN_WRITE, true)) {
                                    closeSocket = true;
                                }
                            }
                            if (closeSocket) {
                                cancelledKey(sk, socketWrapper);
                            }
                        }
                    }
                } else {
                    // Invalid key
                    cancelledKey(sk, socketWrapper);
                }
            } catch (CancelledKeyException ckx) {
                cancelledKey(sk, socketWrapper);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString("endpoint.nio.keyProcessingError"), t);
            }
        }
```

### 请求具体处理

上一步，Selector获取到了就绪的请求socket，然后根据socket注册的触发处理函数等，将这些数据进行封装，扔到了线程池里，开始具体的业务逻辑处理。本节就是从工作线程封装开始，`org.apache.tomcat.util.net.SocketProcessorBase`为工作线程类的抽象类，实现了Runnable接口，不同的Endpoint实现具体的处理逻辑，本节以NioEndpoint为例

以下为`org.apache.tomcat.util.net.AbstractEndpoint#processSocket`方法源码

```java
    /**
     * Process the given SocketWrapper with the given status. Used to trigger
     * processing as if the Poller (for those endpoints that have one)
     * selected the socket.
     *
     * @param socketWrapper The socket wrapper to process
     * @param event         The socket event to be processed
     * @param dispatch      Should the processing be performed on a new
     *                          container thread
     *
     * @return if processing was triggered successfully
     */
    public boolean processSocket(SocketWrapperBase<S> socketWrapper,
            SocketEvent event, boolean dispatch) {
        try {
            if (socketWrapper == null) {
                return false;
            }
            // 优先使用已经存在的线程
            SocketProcessorBase<S> sc = null;
            if (processorCache != null) {
                sc = processorCache.pop();
            }
            if (sc == null) {
                sc = createSocketProcessor(socketWrapper, event);
            } else {
                sc.reset(socketWrapper, event);
            }
            // 获取线程池。线程池的初始化，是在Acceptor、Poller这两个单独线程启动之前创建
            // tomcat使用了自定义的org.apache.tomcat.util.threads.TaskQueue，这块tomcat也进行了小的适配开发
            // 核心线程为10个，最大200线程
            Executor executor = getExecutor();
            if (dispatch && executor != null) {
                executor.execute(sc);
            } else {
                sc.run();
            }
        } catch (RejectedExecutionException ree) {
            getLog().warn(sm.getString("endpoint.executor.fail", socketWrapper) , ree);
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            getLog().error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }
```

上面的方法是得到了处理业务逻辑的线程SocketProcessorBase，NioEndpoint内部类`org.apache.tomcat.util.net.NioEndpoint.SocketProcessor`继承了这个抽象类，也就是具体的业务处理逻辑在`org.apache.tomcat.util.net.NioEndpoint.SocketProcessor#doRun`方法中，最终调用到我们的`Servlet`

```java
        protected void doRun() {
            /*
             * Do not cache and re-use the value of socketWrapper.getSocket() in
             * this method. If the socket closes the value will be updated to
             * CLOSED_NIO_CHANNEL and the previous value potentially re-used for
             * a new connection. That can result in a stale cached value which
             * in turn can result in unintentionally closing currently active
             * connections.
             */
            Poller poller = NioEndpoint.this.poller;
            if (poller == null) {
                socketWrapper.close();
                return;
            }

            try {
                int handshake = -1;
                try {
                    // 握手相关判断逻辑
                   ... 
                } catch (IOException x) {
                  ...
                }
                // 三次握手成功了
                if (handshake == 0) {
                    SocketState state = SocketState.OPEN;
                    // Process the request from this socket
                    // event为SocketEvent.OPEN_READ，这个变量是org.apache.tomcat.util.net.NioEndpoint.Poller#processKey方法赋值
                    if (event == null) {
                        state = getHandler().process(socketWrapper, SocketEvent.OPEN_READ);
                    } else {
                        // 这里就开始正式处理请求了
                        state = getHandler().process(socketWrapper, event);
                    }
                    if (state == SocketState.CLOSED) {
                        poller.cancelledKey(getSelectionKey(), socketWrapper);
                    }
                } else if (handshake == -1 ) {
                    getHandler().process(socketWrapper, SocketEvent.CONNECT_FAIL);
                    poller.cancelledKey(getSelectionKey(), socketWrapper);
                } else if (handshake == SelectionKey.OP_READ){
                    socketWrapper.registerReadInterest();
                } else if (handshake == SelectionKey.OP_WRITE){
                    socketWrapper.registerWriteInterest();
                }
            } catch (CancelledKeyException cx) {
                poller.cancelledKey(getSelectionKey(), socketWrapper);
            } catch (VirtualMachineError vme) {
                ExceptionUtils.handleThrowable(vme);
            } catch (Throwable t) {
                log.error(sm.getString("endpoint.processing.fail"), t);
                poller.cancelledKey(getSelectionKey(), socketWrapper);
            } finally {
                socketWrapper = null;
                event = null;
                //return to cache
                if (running && !paused && processorCache != null) {
                    processorCache.push(this);
                }
            }
        }
```

### 总结

- `Tomcat`是如何接收网络请求？

  使用java nio的同步非阻塞去进行网络监听。

  `org.apache.tomcat.util.net.AbstractEndpoint#bindWithCleanup`中初始化网络监听、SSL

  > ```java
  > 		{	
  >             ....
  >             serverSock = ServerSocketChannel.open();
  >             socketProperties.setProperties(serverSock.socket());
  >             InetSocketAddress addr = new InetSocketAddress(getAddress(), getPortWithOffset());
  >             // 当应用层面的连接数到达最大值时，操作系统可以继续接收连接，那么操作系统能继续接收的最大连接数就是这个队列长度，可以通过acceptCount 参数配置，默认是 100
  >             serverSock.bind(addr, getAcceptCount());
  >         }
  >         serverSock.configureBlocking(true); //mimic APR behavior
  > ```

  `org.apache.tomcat.util.net.NioEndpoint#startInternal`中初始化业务处理的线程池、连接限制器、Poller线程、Acceptor线程

- 如何做到高性能的`http`协议服务器？

  Tomcat把接收连接、检测 I/O 事件以及处理请求进行了拆分，用不同规模的线程去做对应的事情，这也是tomcat能高并发处理请求的原因。不让线程阻塞，尽量让CPU忙起来

![tomcat-socket](image/tomcat架构图.jpg)

- 是怎么设计的呢？

  通过接口、抽象类等，将不同的处理逻辑拆分，各司其职

  - ```
    org.apache.tomcat.util.net.AbstractEndpoint
    ```

    ：I/O事件的检测、处理逻辑都在这个类的实现类里面。使用模板方法，不同的协议有不同的实现方法。NioEndpoint/Nio2Endpoint/AprEndpoint

    - `org.apache.tomcat.util.net.NioEndpoint.Poller`：引用了`java.nio.channels.Selector`，内部有个事件队列，监听I/O事件具体就是在这里做的
    - `org.apache.tomcat.util.net.NioEndpoint.NioSocketWrapper`
    - `org.apache.tomcat.util.net.NioEndpoint.SocketProcessor`: 具体处理请求的线程类

### 参考：

[NioEndpoint组件：Tomcat如何实现非阻塞I/O？](https://time.geekbang.org/column/article/100307)

[Java NIO浅析](https://zhuanlan.zhihu.com/p/23488863)