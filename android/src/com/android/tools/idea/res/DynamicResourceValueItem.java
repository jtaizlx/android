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
package com.android.tools.idea.res;

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.ResourceValueImpl;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@link ResourceItem} for an item that is dynamically defined in a Gradle file. This needs a special class because (1) we can't rely on
 * the normal resource value parser to create resource values from XML, and (2) we need to implement getQualifiers since there is no source
 * file.
 */
public class DynamicResourceValueItem implements ResourceItem, ResolvableResourceItem {
  @NotNull private final ResourceValue myResourceValue;

  public DynamicResourceValueItem(@NotNull ResourceNamespace namespace,
                                  @NotNull ResourceType type,
                                  @NotNull String name,
                                  @NotNull String value) {
    // Dynamic values are always in the "current module", so they don't live in a namespace.
    myResourceValue = new ResourceValueImpl(namespace, type, name, value);
  }

  @Override
  @NotNull
  public ResolveResult createResolveResult() {
    return new ResolveResult() {
      @Nullable
      @Override
      public PsiElement getElement() {
        // TODO: Try to find the item in the Gradle files
        return null;
      }

      @Override
      public boolean isValidResult() {
        return false;
      }
    };
  }

  @Override
  @NotNull
  public String getName() {
    return myResourceValue.getName();
  }

  @Override
  @NotNull
  public ResourceType getType() {
    return myResourceValue.getResourceType();
  }

  @Override
  @Nullable
  public String getLibraryName() {
    return null;
  }

  @Override
  @NotNull
  public ResourceNamespace getNamespace() {
    return myResourceValue.getNamespace();
  }

  @Override
  @NotNull
  public ResourceReference getReferenceToSelf() {
    return myResourceValue.asReference();
  }

  @Override
  @NotNull
  public FolderConfiguration getConfiguration() {
    return new FolderConfiguration();
  }

  @Override
  @NotNull
  public String getKey() {
    return myResourceValue.getResourceUrl().toString().substring(1);
  }

  @Override
  @NotNull
  public ResourceValue getResourceValue() {
    return myResourceValue;
  }

  @Override
  @Nullable
  public PathString getSource() {
    return null;
  }

  @Override
  public boolean isFileBased() {
    return false;
  }
}
