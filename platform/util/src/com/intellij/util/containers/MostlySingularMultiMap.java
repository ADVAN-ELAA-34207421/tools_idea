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
package com.intellij.util.containers;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class MostlySingularMultiMap<K, V> implements Serializable {
  private static final long serialVersionUID = 2784448345881807109L;

  private final THashMap<K, Object> myMap = new THashMap<K, Object>();

  public void add(K key, V value) {
    Object current = myMap.get(key);
    if (current == null) {
      myMap.put(key, value);
    }
    else if (current instanceof Object[]) {
      Object[] curArr = (Object[])current;
      Object[] newArr = ArrayUtil.append(curArr, value, ArrayUtil.OBJECT_ARRAY_FACTORY);
      myMap.put(key, newArr);
    }
    else {
      myMap.put(key, new Object[]{current, value});
    }
  }

  public Set<K> keySet() {
    return myMap.keySet();
  }

  public boolean isEmpty() {
    return myMap.isEmpty();
  }

  public boolean processForKey(K key, Processor<V> p) {
    return processValue(p, myMap.get(key));
  }

  private boolean processValue(Processor<V> p, Object v) {
    if (v instanceof Object[]) {
      for (Object o : (Object[])v) {
        if (!p.process((V)o)) return false;
      }
    }
    else if (v != null) {
      return p.process((V)v);
    }

    return true;
  }

  public boolean processAllValues(Processor<V> p) {
    for (Object v : myMap.values()) {
      if (!processValue(p, v)) return false;
    }

    return true;
  }

  public int size() {
    return myMap.size();
  }

  public int valuesForKey(K key) {
    Object current = myMap.get(key);
    if (current == null) return 0;
    if (current instanceof Object[]) return ((Object[])current).length;
    return 1;
  }

  @NotNull
  public Iterable<V> get(K name) {
    final Object value = myMap.get(name);
    if (value == null) return Collections.emptyList();

    if (value instanceof Object[]) {
      return (Iterable<V>)Arrays.asList((Object[])value);
    }

    return Collections.singleton((V)value);
  }

  public void compact() {
    myMap.compact();
  }

  @Override
  public String toString() {
    return "{" + StringUtil.join(myMap.entrySet(), new Function<Map.Entry<K, Object>, String>() {
      @Override
      public String fun(Map.Entry<K, Object> entry) {
        Object value = entry.getValue();
        String s = (value instanceof Object[] ? Arrays.asList((Object[])value) : Arrays.asList(value)).toString();
        return entry.getKey() + ": " + s;
      }
    }, "; ") + "}";
  }
}
