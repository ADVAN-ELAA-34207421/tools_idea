/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.io;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ActionCallback;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.oio.OioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.oio.OioSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

import java.io.IOException;
import java.net.SocketAddress;

public final class NettyUtil {
  public static final int DEFAULT_CONNECT_ATTEMPT_COUNT = 8;
  public static final int MIN_START_TIME = 100;

  public static void log(Throwable throwable, Logger log) {
    if (isAsWarning(throwable)) {
      log.warn(throwable);
    }
    else {
      log.error(throwable);
    }
  }

  public static Channel connectClient(Bootstrap bootstrap, SocketAddress remoteAddress, ActionCallback asyncResult) {
    return connect(bootstrap, remoteAddress, asyncResult, DEFAULT_CONNECT_ATTEMPT_COUNT);
  }

  @Nullable
  public static Channel connect(Bootstrap bootstrap, SocketAddress remoteAddress, ActionCallback asyncResult, int maxAttemptCount) {
    int attemptCount = 0;
    while (true) {
      try {
        ChannelFuture future = bootstrap.connect(remoteAddress).await();
        if (future.isSuccess()) {
          return future.channel();
        }
        else if (asyncResult.isRejected()) {
          return null;
        }
        else if (++attemptCount < maxAttemptCount) {
          //noinspection BusyWait
          Thread.sleep(attemptCount * 100);
        }
        else {
          asyncResult.reject("cannot connect");
          return null;
        }
      }
      catch (Throwable e) {
        asyncResult.reject(e.getMessage());
        return null;
      }
    }
  }

  private static boolean isAsWarning(Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null) {
      return false;
    }

    return (throwable instanceof IOException && message.equals("An existing connection was forcibly closed by the remote host")) ||
           (throwable instanceof ChannelException && message.startsWith("Failed to bind to: "));
  }

  // applicable only in case of ClientBootstrap&OioClientSocketChannelFactory
  public static void closeAndReleaseFactory(Channel channel) {
    EventLoop channelFactory = channel.eventLoop();
    try {
      channel.close().awaitUninterruptibly();
    }
    finally {
      // in our case it does nothing, we don't use ExecutorService, but we are aware of future changes
      channelFactory.shutdownGracefully();
    }
  }

  public static ServerBootstrap nioServerBootstrap(EventLoopGroup eventLoopGroup) {
    ServerBootstrap bootstrap = new ServerBootstrap().group(eventLoopGroup).channel(NioServerSocketChannel.class);
    bootstrap.childOption(ChannelOption.TCP_NODELAY, true).childOption(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  public static Bootstrap oioClientBootstrap() {
    Bootstrap bootstrap = new Bootstrap().group(new OioEventLoopGroup(1, PooledThreadExecutor.INSTANCE)).channel(OioSocketChannel.class);
    bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static Bootstrap nioClientBootstrap() {
    Bootstrap bootstrap = new Bootstrap().group(new NioEventLoopGroup(1, PooledThreadExecutor.INSTANCE)).channel(NioSocketChannel.class);
    bootstrap.option(ChannelOption.TCP_NODELAY, true).option(ChannelOption.SO_KEEPALIVE, true);
    return bootstrap;
  }

  public static void initHttpHandlers(ChannelPipeline pipeline) {
    pipeline.addLast(new HttpRequestDecoder(), new HttpObjectAggregator(1048576), new HttpResponseEncoder());
  }
}