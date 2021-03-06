// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.uibuilder.actions;

import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class SelectAllAction extends AnAction {
  private final DesignSurface mySurface;

  public SelectAllAction(@NotNull DesignSurface surface) {
    super("Select All", "Select All", null);
    mySurface = surface;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    SceneView screenView = mySurface.getCurrentSceneView();
    if (screenView != null) {
      SelectionModel selectionModel = screenView.getSelectionModel();
      selectionModel.setSelection(screenView.getModel().getComponents());
      mySurface.repaint();
    }
  }
}