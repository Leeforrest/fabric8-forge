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

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

import io.fabric8.forge.addon.utils.completer.StringCompleter;
import io.fabric8.forge.addon.utils.validator.ResourceNameValidator;
import io.fabric8.forge.camel.commands.project.completer.XmlFileCompleter;
import io.fabric8.forge.camel.commands.project.helper.CamelCommandsHelper;
import io.fabric8.forge.camel.commands.project.helper.CamelXmlHelper;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.facets.constraints.FacetConstraint;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.resource.FileResource;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.resource.URLResource;
import org.jboss.forge.addon.templates.Template;
import org.jboss.forge.addon.templates.TemplateFactory;
import org.jboss.forge.addon.templates.freemarker.FreemarkerTemplate;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UIValidationContext;
import org.jboss.forge.addon.ui.facets.HintsFacet;
import org.jboss.forge.addon.ui.hints.InputType;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;

import static io.fabric8.forge.camel.commands.project.helper.CollectionHelper.first;

@FacetConstraint({ResourcesFacet.class})
public class CamelNewCamelContextXmlCommand extends AbstractCamelProjectCommand {

    @Inject
    @WithAttributes(label = "Directory", required = true, description = "The directory name where this type will be created")
    private UIInput<String> directory;

    @Inject
    @WithAttributes(label = "File Name", required = true, description = "Name of XML file")
    private UIInput<String> name;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Inject
    private TemplateFactory factory;

    @Inject
    ResourceFactory resourceFactory;

    @Override
    public boolean isEnabled(UIContext context) {
        boolean enabled = super.isEnabled(context);
        if (enabled) {
            Project project = getSelectedProject(context);
            // must be blueprint or spring project
            boolean blueprint = CamelCommandsHelper.isBlueprintProject(project);
            boolean spring = CamelCommandsHelper.isSpringProject(project);
            return blueprint || spring;

        }
        return false;
    }

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(CamelNewRouteBuilderCommand.class).name(
                "Camel: New CamelContext XML").category(Categories.create(CATEGORY))
                .description("Creates a new XML file with CamelContext");
    }

    @Override
    public void initializeUI(UIBuilder builder) throws Exception {
        Project project = getSelectedProject(builder.getUIContext());
        boolean blueprint = CamelCommandsHelper.isBlueprintProject(project);
        boolean spring = CamelCommandsHelper.isSpringProject(project);

        directory.getFacet(HintsFacet.class).setInputType(InputType.DIRECTORY_PICKER);
        if (blueprint) {
            directory.setDefaultValue("OSGI-INF/blueprint");
        } else if (spring) {
            directory.setDefaultValue("META-INF/spring");
        }

        XmlFileCompleter xmlFileCompleter = createXmlFileCompleter(builder.getUIContext(), null);
        Set<String> directories = xmlFileCompleter.getDirectories();
        if (directories.size() == 1) {
            directory.setDefaultValue(first(directories));
        }
        if (!directories.isEmpty()) {
            directory.setCompleter(new StringCompleter(directories));
        }

        name.addValidator(new ResourceNameValidator("xml"));
        name.getFacet(HintsFacet.class).setInputType(InputType.FILE_PICKER);
        builder.add(directory).add(name);
    }

    @Override
    public void validate(UIValidationContext validator) {
        XmlFileCompleter xmlFileCompleter = createXmlFileCompleter(validator.getUIContext(), null);
        xmlFileCompleter.validateFileDoesNotExist(directory, name, validator);
        super.validate(validator);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        Project project = getSelectedProject(context);
        String projectName = project.getRoot().getName();

        String fileName = CamelXmlHelper.createFileName(directory, name);
        String fullName = "src" + File.separator + "main" + File.separator + "resources" + File.separator + fileName;

        ResourcesFacet facet = project.getFacet(ResourcesFacet.class);
        // this will get a file in the src/main/resources directory where we want to store the spring xml file
        FileResource<?> fileResource = facet.getResource(fileName);

        if (fileResource.exists()) {
            return Results.fail("XML file " + fullName + " already exists");
        }

        // does the project already have camel?
        Dependency core = findCamelCoreDependency(project);
        if (core == null) {
            return Results.fail("The project does not include camel-core");
        }

        boolean blueprint = CamelCommandsHelper.isBlueprintProject(project);
        boolean spring = CamelCommandsHelper.isSpringProject(project);

        if (blueprint) {
            DependencyBuilder dep = DependencyBuilder.create().setGroupId("org.apache.camel")
                    .setArtifactId("camel-blueprint").setVersion(core.getCoordinate().getVersion());

            // install camel-blueprint if missing
            if (!dependencyInstaller.isManaged(project, dep)) {
                dependencyInstaller.install(project, dep);
            }
        } else if (spring) {
            DependencyBuilder dep = DependencyBuilder.create().setGroupId("org.apache.camel")
                    .setArtifactId("camel-spring").setVersion(core.getCoordinate().getVersion());

            // install camel-spring if missing
            if (!dependencyInstaller.isManaged(project, dep)) {
                dependencyInstaller.install(project, dep);
            }
        }

        Resource<URL> xml = null;
        if (blueprint) {
            xml = resourceFactory.create(getClass().getResource("/templates/camel-blueprint.ftl")).reify(URLResource.class);
        } else if (spring) {
            xml = resourceFactory.create(getClass().getResource("/templates/camel-spring.ftl")).reify(URLResource.class);
        }
        Template template = factory.create(xml, FreemarkerTemplate.class);

        // any dynamic options goes into the params map
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("projectName", projectName);
        String output = template.process(params);

        // create the new file and set the content
        fileResource.createNewFile();
        fileResource.setContents(output);

        // if we are in an GUI editor then open the file
        if (isRunningInGui(context.getUIContext())) {
            context.getUIContext().setSelection(fileResource);
        }

        return Results.success("Created new XML file " + fullName);
    }

}
