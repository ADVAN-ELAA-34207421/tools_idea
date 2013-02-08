package org.jetbrains.io;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.ApplicationManager;
import org.jboss.netty.buffer.BigEndianHeapChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.util.CharsetUtil;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.DATE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.setContentLength;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public final class Responses {
  public static final DateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);

  static {
    DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  public static void addDate(HttpResponse response) {
    addDate(response, Calendar.getInstance().getTime());
  }

  public static void addDate(HttpResponse response, Date date) {
    response.setHeader(DATE, DATE_FORMAT.format(date));
  }

  public static void addServer(HttpResponse response) {
    Application app = ApplicationManager.getApplication();
    if (app != null && !app.isDisposed()) {
      response.setHeader("Server", ApplicationInfo.getInstance().getBuild().asString());
    }
  }

  public static void send(HttpResponse response, HttpRequest request, byte[] bytes, ChannelHandlerContext context) {
    response.setContent(new BigEndianHeapChannelBuffer(bytes));
    send(response, request, context);
  }

  public static void send(CharSequence content, HttpRequest request, ChannelHandlerContext context) {
    send("text/plain; charset=UTF-8", content, request, context);
  }

  public static void send(String contentType, CharSequence content, HttpRequest request, ChannelHandlerContext context) {
    HttpResponse response = create(contentType);
    response.setContent(ChannelBuffers.copiedBuffer(content, CharsetUtil.UTF_8));
    send(response, request, context);
  }

  public static void send(HttpResponse response, HttpRequest request, ChannelHandlerContext context) {
    setContentLength(response, response.getContent().readableBytes());
    send(response, context, !isKeepAlive(request));
  }

  public static HttpResponse create(String contentType) {
    DefaultHttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
    response.setHeader(CONTENT_TYPE, contentType);
    return response;
  }

  public static void send(HttpResponse response, CharSequence content, HttpRequest request, ChannelHandlerContext context) {
    response.setContent(ChannelBuffers.copiedBuffer(content, CharsetUtil.US_ASCII));
    send(response, request, context);
  }

  public static void send(HttpResponse response, ChannelHandlerContext context) {
    send(response, context, true);
  }

  public static void send(HttpResponseStatus status, ChannelHandlerContext context) {
    send(new DefaultHttpResponse(HTTP_1_1, status), context);
  }

  public static void send(HttpResponse response, ChannelHandlerContext context, boolean close) {
    ChannelFuture future = context.getChannel().write(response);
    if (close) {
      future.addListener(ChannelFutureListener.CLOSE);
    }
  }
}
