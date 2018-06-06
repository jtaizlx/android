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
package com.android.tools.idea.gradle.structure.model.android

import com.android.builder.model.AndroidProject.*
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.asString
import com.android.tools.idea.gradle.structure.model.repositories.search.AndroidSdkRepositories
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.util.GradleUtil.getAndroidModuleIcon
import com.android.utils.StringHelper
import java.io.File
import javax.swing.Icon

class PsAndroidModule(
  parent: PsProject,
  name: String,
  val resolvedModel: AndroidModuleModel?,
  gradlePath: String,
  parsedModel: GradleBuildModel?
) : PsModule(parent, name, gradlePath, parsedModel) {
  private var buildTypeCollection: PsBuildTypeCollection? = null
  private var productFlavorCollection: PsProductFlavorCollection? = null
  private var variantCollection: PsVariantCollection? = null
  private var dependencyCollection: PsAndroidModuleDependencyCollection? = null
  private var signingConfigCollection: PsSigningConfigCollection? = null

  val projectType: Int? get() = resolvedModel?.androidProject?.projectType ?: parsedModel?.parsedModelModuleType()
  val isLibrary: Boolean get() = projectType != PROJECT_TYPE_APP

  val buildTypes: List<PsBuildType> get() = getOrCreateBuildTypeCollection().items()
  val productFlavors: List<PsProductFlavor> get() = getOrCreateProductFlavorCollection().items()
  val variants: List<PsVariant> get() = getOrCreateVariantCollection().items()
  val dependencies: PsAndroidModuleDependencyCollection get() = getOrCreateDependencyCollection()
  val signingConfigs: List<PsSigningConfig> get() = getOrCreateSigningConfigCollection().items()
  val defaultConfig = PsAndroidModuleDefaultConfig(this)
  val flavorDimensions: Collection<String>
    get() {
      val result = mutableSetOf<String>()
      result.addAll(resolvedModel?.androidProject?.flavorDimensions.orEmpty())
      val parsedFlavorDimensions = parsedModel?.android()?.flavorDimensions()?.toList()
      if (parsedFlavorDimensions != null) {
        result.addAll(parsedFlavorDimensions.map { v -> v.toString() })
      }
      return result
    }

  fun findBuildType(buildType: String): PsBuildType? = getOrCreateBuildTypeCollection().findElement(buildType)

  fun findProductFlavor(name: String): PsProductFlavor? = getOrCreateProductFlavorCollection().findElement(name)

  fun findVariant(name: String): PsVariant? = getOrCreateVariantCollection().findElement(name)

  fun findSigningConfig(signingConfig: String): PsSigningConfig? = getOrCreateSigningConfigCollection().findElement(signingConfig)

  override fun canDependOn(module: PsModule): Boolean =
    // 'module' is either a Java library or an AAR module.
    (module as? PsAndroidModule)?.isLibrary == true

  override val rootDir: File?
    get() = resolvedModel?.rootDirPath ?: parsedModel?.virtualFile?.path?.let { File(it).parentFile }

  override val icon: Icon? get() = projectType?.let { getAndroidModuleIcon(it) }

  override fun populateRepositories(repositories: MutableList<ArtifactRepository>) {
    super.populateRepositories(repositories)
    repositories.addAll(listOfNotNull(AndroidSdkRepositories.getAndroidRepository(), AndroidSdkRepositories.getGoogleRepository()))
  }

  // TODO(solodkyy): Return a collection of PsBuildConfiguration instead of strings.
  override fun getConfigurations(): List<String> {

    fun applicableArtifacts() = listOf("", "test", "androidTest")

    fun flavorsByDimension(dimension: String) =
      productFlavors.filter { (it.dimension as? ParsedValue.Set.Parsed)?.value == dimension }.map { it.name }

    fun buildFlavorCombinations() = when {
      flavorDimensions.size > 1 -> flavorDimensions
        .fold(listOf(listOf("")), { acc, dimension ->
          flavorsByDimension(dimension).flatMap { flavor ->
            acc.map { prefix -> prefix + flavor }
          }
        })
        .map { StringHelper.combineAsCamelCase(it.filter { it != "" }) }
      else -> listOf()  // There are no additional flavor combinations if there is only one flavor dimension.
    }

    fun applicableProductFlavors() =
      listOf("") + productFlavors.map { it.name } + buildFlavorCombinations()

    fun applicableBuildTypes(artifact: String) =
    // TODO(solodkyy): Include product flavor combinations
      when (artifact) {
        "androidTest" -> listOf("")  // androidTest is built only for the configured buildType.
        else -> listOf("") + buildTypes.map { it.name }
      }

    // TODO(solodkyy): When explicitly requested return other advanced scopes (compileOnly, api).
    fun applicableScopes() = listOf("implementation")

    val result = mutableListOf<String>()
    applicableArtifacts().forEach { artifact ->
      applicableProductFlavors().forEach { productFlavor ->
        applicableBuildTypes(artifact).forEach { buildType ->
          applicableScopes().forEach { scope ->
            result.add(StringHelper.combineAsCamelCase(listOf(artifact, productFlavor, buildType, scope).filter { it != "" }))
          }
        }
      }
    }
    return result.toList()
  }

  override fun addLibraryDependency(library: String, scopesNames: List<String>) {
    // Update/reset the "parsed" model.
    addLibraryDependencyToParsedModel(scopesNames, library)

    resetDependencies()

    val spec = PsArtifactDependencySpec.create(library)!!
    fireLibraryDependencyAddedEvent(spec)
    isModified = true
  }

  override fun addModuleDependency(modulePath: String, scopesNames: List<String>) {
    // Update/reset the "parsed" model.
    addModuleDependencyToParsedModel(scopesNames, modulePath)

    resetDependencies()

    fireModuleDependencyAddedEvent(modulePath)
    isModified = true
  }

  override fun removeDependency(dependency: PsDeclaredDependency) {
    removeDependencyFromParsedModel(dependency)

    resetDependencies()

    fireDependencyRemovedEvent(dependency)
    isModified = true
  }

  override fun setLibraryDependencyVersion(
    spec: PsArtifactDependencySpec,
    configurationName: String,
    newVersion: String
  ) {
    var modified = false
    val matchingDependencies = dependencies
      .findLibraryDependencies(spec.group, spec.name)
      .filter { it -> it.spec == spec }
      .map { it as PsDeclaredDependency }
      .filter { it.configurationName == configurationName }
    // Usually there should be only one item in the matchingDependencies list. However, if there are duplicate entries in the config file
    // it might differ. We update all of them.

    for (dependency in matchingDependencies) {
      val parsedDependency = dependency.parsedModel
      assert(parsedDependency is ArtifactDependencyModel)
      val artifactDependencyModel = parsedDependency as ArtifactDependencyModel
      artifactDependencyModel.version().setValue(newVersion)
      modified = true
    }
    if (modified) {
      resetDependencies()
      for (dependency in matchingDependencies) {
        fireDependencyModifiedEvent(dependency)
      }
      isModified = true
    }
  }

  fun addNewBuildType(name: String): PsBuildType = getOrCreateBuildTypeCollection().addNew(name)

  fun removeBuildType(buildType: PsBuildType) = getOrCreateBuildTypeCollection().remove(buildType.name)

  fun addNewFlavorDimension(newName: String) {
    assert(parsedModel != null)
    val androidModel = parsedModel!!.android()!!
    androidModel.flavorDimensions().addListValue().setValue(newName)
    isModified = true
  }

  fun removeFlavorDimension(flavorDimension: String) {
    assert(parsedModel != null)
    val androidModel = parsedModel!!.android()!!

    val model = androidModel.flavorDimensions().getListValue(flavorDimension)
    if (model != null) {
      model.delete()
      isModified = true
    }
  }

  fun addNewProductFlavor(name: String): PsProductFlavor = getOrCreateProductFlavorCollection().addNew(name)

  fun removeProductFlavor(productFlavor: PsProductFlavor) = getOrCreateProductFlavorCollection().remove(productFlavor.name)

  fun addNewSigningConfig(name: String): PsSigningConfig = getOrCreateSigningConfigCollection().addNew(name)

  fun removeSigningConfig(signingConfig: PsSigningConfig) = getOrCreateSigningConfigCollection().remove(signingConfig.name)


  private fun getOrCreateBuildTypeCollection(): PsBuildTypeCollection =
    buildTypeCollection ?: PsBuildTypeCollection(this).also { buildTypeCollection = it }

  private fun getOrCreateProductFlavorCollection(): PsProductFlavorCollection =
    productFlavorCollection ?: PsProductFlavorCollection(this).also { productFlavorCollection = it }

  private fun getOrCreateVariantCollection(): PsVariantCollection =
    variantCollection ?: PsVariantCollection(this).also { variantCollection = it }

  private fun getOrCreateDependencyCollection(): PsAndroidModuleDependencyCollection =
    dependencyCollection ?: PsAndroidModuleDependencyCollection(this).also { dependencyCollection = it }

  private fun getOrCreateSigningConfigCollection(): PsSigningConfigCollection =
    signingConfigCollection ?: PsSigningConfigCollection(this).also { signingConfigCollection = it }

  private fun resetDependencies() {
    resetDeclaredDependencies()
    resetResolvedDependencies()
  }

  internal fun resetResolvedDependencies() {
    variants.forEach { variant -> variant.forEachArtifact { artifact -> artifact.resetDependencies() } }
  }

  private fun resetDeclaredDependencies() {
    dependencyCollection = null
  }
}

private fun GradleBuildModel.parsedModelModuleType(): Int? =
  plugins().mapNotNull { moduleProjectTypeFromPlugin(it.name().asString().orEmpty()) }.firstOrNull()

private fun moduleProjectTypeFromPlugin(plugin: String): Int? = when (plugin) {
  "com.android.application", "android" -> PROJECT_TYPE_APP
  "com.android.library", "android-library" -> PROJECT_TYPE_LIBRARY
  "com.android.instantapp" -> PROJECT_TYPE_INSTANTAPP
  "com.android.feature" -> PROJECT_TYPE_FEATURE
  "com.android.dynamic-feature" -> PROJECT_TYPE_DYNAMIC_FEATURE
  "com.android.test" -> PROJECT_TYPE_TEST
  else -> null
}
