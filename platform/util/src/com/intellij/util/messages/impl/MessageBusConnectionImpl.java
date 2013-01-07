/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.util.messages.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.messages.MessageHandler;
import com.intellij.util.messages.Topic;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MessageBusConnectionImpl implements MessageBusConnection {
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.messages.impl.MessageBusConnectionImpl");

  private final MessageBusImpl myBus;
  private final ThreadLocal<Queue<Message>> myPendingMessages = new QueueThreadLocal();
  private MessageHandler myDefaultHandler;
  private final Map<Topic, Object> mySubscriptions = new HashMap<Topic, Object>();

  public MessageBusConnectionImpl(MessageBusImpl bus) {
    myBus = bus;
  }

  @Override
  public <L> void subscribe(Topic<L> topic, L handler) throws IllegalStateException {
    if (mySubscriptions.put(topic, handler) != null) {
      throw new IllegalStateException("Subscription to " + topic + " already exists");
    }
    myBus.notifyOnSubscription(this, topic);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <L> void subscribe(Topic<L> topic) throws IllegalStateException {
    if (myDefaultHandler == null) {
      throw new IllegalStateException("Connection must have default handler installed prior to any anonymous subscriptions. "
                                      + "Target topic: " + topic);
    }
    if (topic.getListenerClass().isInstance(myDefaultHandler)) {
      throw new IllegalStateException("Can't subscribe to the topic '" + topic +"'. Default handler has incompatible type - expected: '" +
        topic.getListenerClass() + "', actual: '" + myDefaultHandler.getClass() + "'");
    }

    subscribe(topic, (L)myDefaultHandler);
  }

  @Override
  public void setDefaultHandler(MessageHandler handler) {
    myDefaultHandler = handler;
  }

  @Override
  public void disconnect() {
    Queue<Message> jobs = myPendingMessages.get();
    myPendingMessages.remove();
    myBus.notifyConnectionTerminated(this);
    if (!jobs.isEmpty()) {
      LOG.error("Not delivered events in the queue: " + jobs);
    }
  }

  @Override
  public void dispose() {
    disconnect();
  }

  @Override
  public void deliverImmediately() {
    while (!myPendingMessages.get().isEmpty()) {
      myBus.deliverSingleMessage();
    }
  }

  void deliverMessage(Message message) {
    final Message messageOnLocalQueue = myPendingMessages.get().poll();
    assert messageOnLocalQueue == message;

    final Topic topic = message.getTopic();
    final Object handler = mySubscriptions.get(topic);

    try {
      Method listenerMethod = message.getListenerMethod();

      if (handler == myDefaultHandler) {
        myDefaultHandler.handle(listenerMethod, message.getArgs());
      }
      else {
        listenerMethod.invoke(handler, message.getArgs());
      }
    }
    catch (AbstractMethodError e) {
      //Do nothing. This listener just does not implement something newly added yet.
    }
    catch(Throwable e) {
      LOG.error(e.getCause());
    }
  }

  void scheduleMessageDelivery(Message message) {
    myPendingMessages.get().offer(message);
  }

  public String toString() {
    return mySubscriptions.keySet().toString();
  }

  private static class QueueThreadLocal extends ThreadLocal<Queue<Message>> {
    @Override
    protected Queue<Message> initialValue() {
      return new ConcurrentLinkedQueue<Message>();
    }
  }
}
