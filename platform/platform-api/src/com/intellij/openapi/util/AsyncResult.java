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
package com.intellij.openapi.util;

import org.jetbrains.annotations.NotNull;

public class AsyncResult<T> extends ActionCallback {
  private T myResult;

  @NotNull
  public AsyncResult<T> setDone(T result) {
    myResult = result;
    setDone();
    return this;
  }

  @NotNull
  public AsyncResult<T> setRejected(T result) {
    myResult = result;
    setRejected();
    return this;
  }

  @NotNull
  public AsyncResult<T> doWhenDone(@NotNull final Handler<T> handler) {
    doWhenDone(new Runnable() {
      public void run() {
        handler.run(myResult);
      }
    });
    return this;
  }

  @NotNull
  public AsyncResult<T> doWhenRejected(@NotNull final Handler<T> handler) {
    doWhenRejected(new Runnable() {
      public void run() {
        handler.run(myResult);
      }
    });
    return this;
  }

  public T getResult() {
    return myResult;
  }

  public interface Handler<T> {
    void run(T t);
  }

  public static class Done<T> extends AsyncResult<T> {
    public Done(T value) {
      setDone(value);
    }
  }

  public static class Rejected<T> extends AsyncResult<T> {
    public Rejected() {
      setRejected();
    }

    public Rejected(T value) {
      setRejected(value);
    }
  }
}