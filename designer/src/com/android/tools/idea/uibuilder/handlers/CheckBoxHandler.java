/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.uibuilder.handlers;

import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.SdkConstants.*;

public class CheckBoxHandler extends TextViewHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_BUTTON,
      ATTR_BUTTON_TINT,
      ATTR_CHECKED,
      ATTR_FOCUSABLE,
      ATTR_CLICKABLE,
      ATTR_DUPLICATE_PARENT_STATE);
  }

  @Override
  @NotNull
  public List<String> getBaseStyles(@NotNull String tagName) {
    return ImmutableList.of(PREFIX_ANDROID + "Widget.CompoundButton." + tagName);
  }
}
