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
package com.android.tools.idea.gradle.structure.configurables.suggestions

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractMainPanel
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModulePath
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.Place
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import java.awt.BorderLayout

class AndroidModuleSuggestionsConfigurable(
    context: PsContext,
    module: PsModule,
    private val extraModules: List<PsModule>
) : AbstractModuleConfigurable<PsModule, AbstractMainPanel>(context, module) {
  override fun getId() = "android.psd.suggestions." + displayName

  override fun createPanel(): AbstractMainPanel = object : AbstractMainPanel(context, extraModules) {
    private val panel = createInnerPanel().also {
      add(it.panel, BorderLayout.CENTER)
    }

    override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = panel.navigateTo(place, requestFocus)
    override fun queryPlace(place: Place) = panel.queryPlace(place)
    override fun restoreUiState() = Unit
    override fun dispose() {
      Disposer.dispose(panel)
    }
  }

  private fun createInnerPanel(): SuggestionsForm {
    val psModulePath = when (module) {
      is PsAllModulesFakeModule -> null
      else -> PsModulePath(module)
    }
    val issueRenderer = SuggestionsViewIssueRenderer(context)
    return SuggestionsForm(context, issueRenderer).apply {
      renderIssues(getIssues(context, psModulePath), psModulePath)

      context.analyzerDaemon.add( {
        invokeLaterIfNeeded {
          if (!uiDisposed) {
            renderIssues(getIssues(context, psModulePath), psModulePath)
          }
        }
      }, this)
    }
  }

  companion object {
    const val SUGGESTIONS_VIEW = "SuggestionsView"
  }
}

internal fun getIssues(psContext: PsContext, psModulePath: PsModulePath?): List<PsIssue> {
  val issueCollection = psContext.analyzerDaemon.issues
  return if (psModulePath != null)
    issueCollection.findIssues(psModulePath, null)
  else issueCollection.getValues(PsModulePath::class.java)
}

