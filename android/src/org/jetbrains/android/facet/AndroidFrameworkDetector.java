// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.facet;

import com.android.SdkConstants;
import com.android.tools.idea.gradle.project.GradleProjectInfo;
import com.android.tools.idea.project.AndroidRunConfigurations;
import com.android.tools.idea.run.activity.DefaultActivityLocator;
import com.intellij.configurationStore.StoreUtil;
import com.intellij.facet.FacetType;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FacetBasedFrameworkDetector;
import com.intellij.framework.detection.FileContentPattern;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.ElementPattern;
import com.intellij.util.indexing.FileContent;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.importDependencies.ImportDependenciesUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.PROJECT_TYPE_LIBRARY;
import static org.jetbrains.android.facet.AndroidRootUtil.getProjectPropertyValue;
import static org.jetbrains.android.util.AndroidUtils.*;

/**
 * @author nik
 */
public class AndroidFrameworkDetector extends FacetBasedFrameworkDetector<AndroidFacet, AndroidFacetConfiguration> {
  private static final NotificationGroup ANDROID_MODULE_IMPORTING_NOTIFICATION = NotificationGroup.balloonGroup("Android Module Importing");

  public AndroidFrameworkDetector() {
    super("android");
  }

  @Override
  public List<? extends DetectedFrameworkDescription> detect(@NotNull Collection<VirtualFile> newFiles,
                                                             @NotNull FrameworkDetectionContext context) {
    Project project = context.getProject();
    if (project != null) {
      GradleProjectInfo gradleProjectInfo = GradleProjectInfo.getInstance(project);
      // See https://code.google.com/p/android/issues/detail?id=203384
      // Since this method is invoked before sync, 'isBuildWithGradle' may return false even for Gradle projects. If that happens, we fall
      // back to checking that a project has a build.gradle file.
      if (gradleProjectInfo.isBuildWithGradle() || gradleProjectInfo.hasTopLevelGradleBuildFile()) {
        return Collections.emptyList();
      }
    }
    return super.detect(newFiles, context);
  }

  @Override
  public void setupFacet(@NotNull AndroidFacet facet, ModifiableRootModel model) {
    Module module = facet.getModule();
    Project project = module.getProject();

    VirtualFile[] contentRoots = model.getContentRoots();

    if (contentRoots.length == 1) {
      facet.getConfiguration().init(module, contentRoots[0]);
    }
    ImportDependenciesUtil.importDependencies(module, true);

    StartupManager.getInstance(project).runWhenProjectIsInitialized(() -> DumbService.getInstance(project).runWhenSmart(() -> {
      doImportSdkAndFacetConfiguration(facet, model);
      StoreUtil.saveDocumentsAndProjectSettings(project);
    }));
  }

  public static void doImportSdkAndFacetConfiguration(@NotNull AndroidFacet facet, @Nullable ModifiableRootModel model) {
    Module module = facet.getModule();
    AndroidSdkUtils.setupAndroidPlatformIfNecessary(module, true);

    if (model != null && !model.isDisposed() && model.isWritable()) {
      model.setSdk(ModuleRootManager.getInstance(module).getSdk());
    }

    Pair<String, VirtualFile> manifestMergerProperty = getProjectPropertyValue(module, ANDROID_MANIFEST_MERGER_PROPERTY);
    if (manifestMergerProperty != null) {
      facet.getProperties().ENABLE_MANIFEST_MERGING = getFirstAsBoolean(manifestMergerProperty);
    }

    Pair<String, VirtualFile> dexDisableMergerProperty = getProjectPropertyValue(module, ANDROID_DEX_DISABLE_MERGER);
    if (dexDisableMergerProperty != null) {
      facet.getProperties().ENABLE_PRE_DEXING = !getFirstAsBoolean(dexDisableMergerProperty);
    }

    // Left here for compatibility with loading older projects
    Pair<String, VirtualFile> androidLibraryProperty = getProjectPropertyValue(module, ANDROID_LIBRARY_PROPERTY);
    if (androidLibraryProperty != null && getFirstAsBoolean(androidLibraryProperty)) {
      facet.getConfiguration().setProjectType(PROJECT_TYPE_LIBRARY);
    }

    Pair<String, VirtualFile> androidProjectTypeProperty = getProjectPropertyValue(module, ANDROID_PROJECT_TYPE_PROPERTY);
    if (androidProjectTypeProperty != null) {
      facet.getConfiguration().setProjectType(Integer.parseInt(androidProjectTypeProperty.getFirst()));
    }

    if (facet.getConfiguration().isAppProject()) {
      Pair<String, VirtualFile> dexForceJumboProperty = getProjectPropertyValue(module, ANDROID_DEX_FORCE_JUMBO_PROPERTY);
      if (dexForceJumboProperty != null) {
        showDexOptionNotification(module, ANDROID_DEX_FORCE_JUMBO_PROPERTY);
      }

      Manifest manifest = facet.getManifest();
      if (manifest != null && DefaultActivityLocator.getDefaultLauncherActivityName(module.getProject(), manifest) != null) {
        AndroidRunConfigurations.getInstance().addRunConfiguration(facet, null);
      }
    }
  }

  private static boolean getFirstAsBoolean(@NotNull Pair<String, VirtualFile> pair) {
    return Boolean.parseBoolean(pair.getFirst());
  }

  @NotNull
  public static Notification showDexOptionNotification(@NotNull Module module, @NotNull String propertyName) {
    Project project = module.getProject();
    Notification notification = ANDROID_MODULE_IMPORTING_NOTIFICATION.createNotification(
      AndroidBundle.message("android.facet.importing.title", module.getName()),
      "'" + propertyName +
      "' property is detected in " + SdkConstants.FN_PROJECT_PROPERTIES +
      " file.<br>You may enable related option in <a href='configure'>Settings | Compiler | Android DX</a>",
      NotificationType.INFORMATION, new NotificationListener.Adapter() {
        @Override
        protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
          notification.expire();
          ShowSettingsUtil.getInstance().showSettingsDialog(
            project, AndroidBundle.message("android.dex.compiler.configurable.display.name"));
        }
      });
    notification.notify(project);
    return notification;
  }

  @NotNull
  @Override
  public FacetType<AndroidFacet, AndroidFacetConfiguration> getFacetType() {
    return AndroidFacet.getFacetType();
  }

  @Override
  @NotNull
  public FileType getFileType() {
    return StdFileTypes.XML;
  }

  @Override
  @NotNull
  public ElementPattern<FileContent> createSuitableFilePattern() {
    return FileContentPattern.fileContent().withName(SdkConstants.FN_ANDROID_MANIFEST_XML);
  }
}
