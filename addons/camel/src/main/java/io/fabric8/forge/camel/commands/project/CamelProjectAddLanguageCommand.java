/**
 *  Copyright 2005-2015 Red Hat, Inc.
 *
 *  Red Hat licenses this file to you under the Apache License, version
 *  2.0 (the "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied.  See the License for the specific language governing
 *  permissions and limitations under the License.
 */
package io.fabric8.forge.camel.commands.project;

import javax.inject.Inject;

import io.fabric8.forge.camel.commands.project.completer.CamelLanguagesCompleter;
import io.fabric8.forge.camel.commands.project.dto.LanguageDto;
import org.jboss.forge.addon.convert.Converter;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.input.ValueChangeListener;
import org.jboss.forge.addon.ui.input.events.ValueChangeEvent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createLanguageDto;

public class CamelProjectAddLanguageCommand extends AbstractCamelProjectCommand {

    @Inject
    @WithAttributes(label = "Name", required = true, description = "Name of language to add")
    private UISelectOne<LanguageDto> languageName;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelProjectAddLanguageCommand.class).name(
                "Camel: Project Add Language").category(Categories.create(CATEGORY))
                .description("Adds a Camel language to your project dependencies");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Project project = getSelectedProject(builder);
        // use value choices instead of completer as that works better in web console
        languageName.setValueChoices(new CamelLanguagesCompleter(project, getCamelCatalog()).getValueChoices());
        // include converter from string->dto
        languageName.setValueConverter(new Converter<String, LanguageDto>() {
            @Override
            public LanguageDto convert(String text) {
                return createLanguageDto(getCamelCatalog(), text);
            }
        });
        // show note about the chosen language
        languageName.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                LanguageDto language = (LanguageDto) event.getNewValue();
                if (language != null) {
                    String description = language.getDescription();
                    languageName.setNote(description != null ? description : "");
                } else {
                    languageName.setNote("");
                }
            }
        });

        builder.add(languageName);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);

        // does the project already have camel?
        Dependency core = findCamelCoreDependency(project);
        if (core == null) {
            return Results.fail("The project does not include camel-core");
        }

        LanguageDto dto = languageName.getValue();
        if (dto != null) {

            // we want to use same version as camel-core
            DependencyBuilder component = DependencyBuilder.create().setGroupId(dto.getGroupId())
                    .setArtifactId(dto.getArtifactId()).setVersion(core.getCoordinate().getVersion());

            // install the component
            dependencyInstaller.install(project, component);

            return Results.success("Added Camel language " + dto.getName() + " (" + dto.getArtifactId() + ") to the project");
        } else {
            return Results.fail("Unknown Camel language");
        }
    }
}
