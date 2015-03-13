/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.SyncIssue;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer;
import com.android.tools.idea.gradle.dependency.*;
import com.android.tools.idea.gradle.facet.AndroidGradleFacet;
import com.android.tools.idea.gradle.messages.ProjectSyncMessages;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.google.common.base.Objects;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;

import static com.android.SdkConstants.FD_JARS;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;

/**
 * Sets the dependencies of a module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class DependenciesModuleCustomizer extends AbstractDependenciesModuleCustomizer<IdeaAndroidProject>
  implements BuildVariantModuleCustomizer<IdeaAndroidProject> {

  @Override
  protected void setUpDependencies(@NotNull ModifiableRootModel moduleModel, @NotNull IdeaAndroidProject androidProject) {
    DependencySet dependencies = Dependency.extractFrom(androidProject);
    for (LibraryDependency dependency : dependencies.onLibraries()) {
      updateLibraryDependency(moduleModel, dependency, androidProject.getDelegate());
    }
    for (ModuleDependency dependency : dependencies.onModules()) {
      updateModuleDependency(moduleModel, dependency, androidProject.getDelegate());
    }

    ProjectSyncMessages messages = ProjectSyncMessages.getInstance(moduleModel.getProject());
    Collection<SyncIssue> syncIssues = androidProject.getSyncIssues();
    if (syncIssues != null) {
      messages.reportSyncIssues(syncIssues, moduleModel.getModule());
    }
    else {
      Collection<String> unresolvedDependencies = androidProject.getDelegate().getUnresolvedDependencies();
      messages.reportUnresolvedDependencies(unresolvedDependencies, moduleModel.getModule());
    }
  }

  private void updateModuleDependency(@NotNull ModifiableRootModel moduleModel,
                                      @NotNull ModuleDependency dependency,
                                      @NotNull AndroidProject androidProject) {
    ModuleManager moduleManager = ModuleManager.getInstance(moduleModel.getProject());
    Module moduleDependency = null;
    for (Module module : moduleManager.getModules()) {
      AndroidGradleFacet androidGradleFacet = AndroidGradleFacet.getInstance(module);
      if (androidGradleFacet != null) {
        String gradlePath = androidGradleFacet.getConfiguration().GRADLE_PROJECT_PATH;
        if (Objects.equal(gradlePath, dependency.getGradlePath())) {
          moduleDependency = module;
          break;
        }
      }
    }
    if (moduleDependency != null) {
      ModuleOrderEntry orderEntry = moduleModel.addModuleOrderEntry(moduleDependency);
      orderEntry.setExported(true);
      return;
    }

    LibraryDependency backup = dependency.getBackupDependency();
    String backupName = backup != null ? backup.getName() : null;

    DependencySetupErrors setupErrors = getSetupErrors(moduleModel.getProject());
    setupErrors.addMissingModule(dependency.getGradlePath(), moduleModel.getModule().getName(), backupName);

    // fall back to library dependency, if available.
    if (backup != null) {
      updateLibraryDependency(moduleModel, backup, androidProject);
    }
  }

  private void updateLibraryDependency(@NotNull ModifiableRootModel moduleModel,
                                       @NotNull LibraryDependency dependency,
                                       @NotNull AndroidProject androidProject) {
    Collection<String> binaryPaths = dependency.getPaths(LibraryDependency.PathType.BINARY);
    setUpLibraryDependency(moduleModel, dependency.getName(), dependency.getScope(), binaryPaths);

    File buildFolder = androidProject.getBuildFolder();

    // Exclude jar files that are in "jars" folder in "build" folder.
    // see https://code.google.com/p/android/issues/detail?id=123788
    ContentEntry[] contentEntries = moduleModel.getContentEntries();
    for (String binaryPath : binaryPaths) {
      File parent = new File(binaryPath).getParentFile();
      if (parent != null && FD_JARS.equals(parent.getName()) && isAncestor(buildFolder, parent, true)) {
        ContentEntry parentContentEntry = findParentContentEntry(parent, contentEntries);
        if (parentContentEntry != null) {
          parentContentEntry.addExcludeFolder(pathToIdeaUrl(parent));
        }
      }
    }
  }

  @Override
  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return GradleConstants.SYSTEM_ID;
  }

  @Override
  @NotNull
  public Class<IdeaAndroidProject> getSupportedModelType() {
    return IdeaAndroidProject.class;
  }
}
