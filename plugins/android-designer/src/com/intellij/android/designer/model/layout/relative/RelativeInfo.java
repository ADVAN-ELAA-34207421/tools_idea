/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model.layout.relative;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;

/**
 * @author Alexander Lobas
 */
public class RelativeInfo {
  public static final String KEY = "RelativeInfos";

  public RadViewComponent alignTop;
  public RadViewComponent alignBottom;
  public RadViewComponent alignLeft;
  public RadViewComponent alignRight;
  public RadViewComponent alignBaseline;
  public RadViewComponent above;
  public RadViewComponent below;
  public RadViewComponent toLeftOf;
  public RadViewComponent toRightOf;

  public boolean parentTop;
  public boolean parentBottom;
  public boolean parentLeft;
  public boolean parentRight;

  public boolean parentCenterHorizontal;
  public boolean parentCenterVertical;

  public boolean contains(RadComponent component) {
    return component == alignTop ||
           component == alignBottom ||
           component == alignLeft ||
           component == alignRight ||
           component == alignBaseline ||
           component == above ||
           component == below ||
           component == toLeftOf ||
           component == toRightOf;
  }
}