/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.BuildAdapter;
import org.gradle.api.Project;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.composite.CompositeBuildContext;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.composite.internal.IncludedBuildInternal;
import org.gradle.execution.ProjectConfigurer;
import org.gradle.initialization.GradleLauncher;
import org.gradle.internal.Cast;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.launcher.exec.GradleBuildController;
import org.gradle.tooling.internal.protocol.InternalUnsupportedModelException;
import org.gradle.tooling.internal.provider.BuildActionResult;
import org.gradle.tooling.internal.provider.BuildModelAction;
import org.gradle.tooling.internal.provider.PayloadSerializer;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry;
import org.gradle.tooling.provider.model.UnknownModelException;
import org.gradle.tooling.provider.model.internal.ProjectSensitiveToolingModelBuilder;
import org.gradle.tooling.provider.model.internal.ProjectToolingModelBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

//TODO use a better protocol than a List of Object arrays. Transfer `ModelResults` directly?
//TODO get rid of the many `isAllModels` checks.
//TODO avoid serialize/deserialize for included builds
public class BuildModelActionRunner implements BuildActionRunner {
    @Override
    public void run(final BuildAction action, final BuildController buildController) {
        if (!(action instanceof BuildModelAction)) {
            return;
        }

        final BuildModelAction buildModelAction = (BuildModelAction) action;
        final GradleInternal gradle = buildController.getGradle();
        final ServiceRegistry services = gradle.getServices();
        final PayloadSerializer payloadSerializer = gradle.getServices().get(PayloadSerializer.class);

        final List<Object[]> compositeResults = Lists.newArrayList();
        gradle.addBuildListener(new BuildAdapter() {
            //TODO settingsEvaluated should be enough, but CompositeBuildSettingsLoader acts only after the settingsLoaded event
            @Override
            public void projectsEvaluated(Gradle gradle) {
                if (!buildModelAction.isAllModels()) {
                    return;
                }
                CompositeBuildContext compositeBuildContext = services.get(CompositeBuildContext.class);
                Set<? extends IncludedBuild> includedBuilds = compositeBuildContext.getIncludedBuilds();

                for (IncludedBuild includedBuild : includedBuilds) {
                    if (includedBuild.getProjectDir().equals(gradle.getRootProject().getProjectDir())) {
                        return;
                    }
                }

                for (IncludedBuild includedBuild : includedBuilds) {
                    IncludedBuildInternal includedBuildInternal = (IncludedBuildInternal) includedBuild;
                    GradleLauncher gradleLauncher = includedBuildInternal.createGradleLauncher();
                    GradleBuildController includedController = new GradleBuildController(gradleLauncher);
                    run(action, includedController);
                    BuildActionResult result = (BuildActionResult) includedController.getResult();
                    List<Object[]> includedResults = Cast.uncheckedCast(payloadSerializer.deserialize(result.result));
                    compositeResults.addAll(includedResults);
                }
            }

        });

        if (buildModelAction.isRunTasks()) {
            buildController.run();
        } else {
            buildController.configure();

            // Currently need to force everything to be configured
            services.get(ProjectConfigurer.class).configureHierarchy(gradle.getRootProject());
            for (Project project : gradle.getRootProject().getAllprojects()) {
                ProjectInternal projectInternal = (ProjectInternal) project;
                projectInternal.getTasks().discoverTasks();
                projectInternal.bindAllModelRules();
            }
        }


        String modelName = buildModelAction.getModelName();
        ToolingModelBuilderRegistry builderRegistry = getToolingModelBuilderRegistry(gradle);
        ToolingModelBuilder builder;
        try {
            builder = builderRegistry.getBuilder(modelName);
        } catch (UnknownModelException e) {
            throw (InternalUnsupportedModelException) new InternalUnsupportedModelException().initCause(e);
        }

        Object modelResult;
        if (buildModelAction.isAllModels()) {
            addModels(gradle, modelName, builder, compositeResults);
            modelResult = compositeResults;
        } else {
            modelResult = createModelResult(gradle, modelName, builder);
        }

        BuildActionResult result = new BuildActionResult(payloadSerializer.serialize(modelResult), null);
        buildController.setResult(result);
    }

    private Object createModelResult(GradleInternal gradle, String modelName, ToolingModelBuilder builder) {
        if (builder instanceof ProjectSensitiveToolingModelBuilder) {
            return ((ProjectSensitiveToolingModelBuilder) builder).buildAll(modelName, gradle.getDefaultProject(), true);
        } else {
            return builder.buildAll(modelName, gradle.getDefaultProject());
        }
    }

    private void addModels(GradleInternal gradle, String modelName, ToolingModelBuilder builder, List<Object[]> models) {
        Map<String, Object> modelsByPath = Maps.newLinkedHashMap();
        if (builder instanceof ProjectToolingModelBuilder) {
            ((ProjectToolingModelBuilder) builder).addModels(modelName, gradle.getDefaultProject(), modelsByPath);
        } else {
            Object buildScopedModel = builder.buildAll(modelName, gradle.getDefaultProject());
            modelsByPath.put(gradle.getDefaultProject().getPath(), buildScopedModel);
        }
        List<Object[]> myModels = Lists.newArrayList();
        for (Map.Entry<String, Object> entry : modelsByPath.entrySet()) {
            myModels.add(new Object[] { gradle.getDefaultProject().getProjectDir(), entry.getKey(), entry.getValue()});
        }
        models.addAll(0, myModels);
    }

    private ToolingModelBuilderRegistry getToolingModelBuilderRegistry(GradleInternal gradle) {
        return gradle.getDefaultProject().getServices().get(ToolingModelBuilderRegistry.class);
    }
}
