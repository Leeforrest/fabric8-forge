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
package io.fabric8.forge.camel.commands.project.completer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.forge.addon.utils.CamelProjectHelper;
import io.fabric8.forge.camel.commands.project.dto.LanguageDto;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;

import static io.fabric8.forge.addon.utils.CamelProjectHelper.findCamelArtifacts;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.createLanguageDto;
import static io.fabric8.forge.camel.commands.project.helper.CamelCatalogHelper.languagesFromArtifact;

public class CamelLanguagesCompleter implements UICompleter<LanguageDto> {

    private final Project project;
    private final CamelCatalog camelCatalog;
    private final Dependency core;

    public CamelLanguagesCompleter(Project project, CamelCatalog camelCatalog) {
        this.project = project;
        this.camelCatalog = camelCatalog;
        // need to find camel-core so we known the camel version
        core = CamelProjectHelper.findCamelCoreDependency(project);
    }

    @Override
    public Iterable<LanguageDto> getCompletionProposals(UIContext context, InputComponent input, String value) {
        if (core == null) {
            return null;
        }

        List<LanguageDto> answer = new ArrayList<>();

        // find all available language names
        List<String> names = camelCatalog.findLanguageNames();

        // filter non matching names first
        List<String> filtered = new ArrayList<String>();
        for (String name : names) {
            if (value == null || name.startsWith(value)) {
                filtered.add(name);
            }
        }

        // filter names which are already on the classpath
        for (String name : filtered) {
            // TODO: 2.17.3/2.18 method is bean language
            if ("method".equals(name)) {
                name = "bean";
            }
            String json = camelCatalog.languageJSonSchema(name);
            String artifactId = findArtifactId(json);

            // skip if we already have the dependency
            boolean already = false;
            if (artifactId != null) {
                already = CamelProjectHelper.hasDependency(project, "org.apache.camel", artifactId);
            }
            if (!already) {
                LanguageDto dto = createLanguageDto(camelCatalog, json);
                answer.add(dto);
            }
        }

        return answer;
    }

    public Iterable<LanguageDto> getValueChoices() {
        if (core == null) {
            return null;
        }

        List<String> names = camelCatalog.findLanguageNames();

        // filter out existing languages we already have
        Set<Dependency> artifacts = findCamelArtifacts(project);
        for (Dependency dep : artifacts) {
            Set<String> languages = languagesFromArtifact(camelCatalog, dep.getCoordinate().getArtifactId());
            names.removeAll(languages);
        }

        List<LanguageDto> answer = new ArrayList<>();
        for (String name : names) {
            LanguageDto dto = createLanguageDto(camelCatalog, name);
            answer.add(dto);
        }

        return answer;
    }

    private static String findArtifactId(String json) {
        List<Map<String, String>> data = JSonSchemaHelper.parseJsonSchema("language", json, false);
        for (Map<String, String> row : data) {
            if (row.get("artifactId") != null) {
                return row.get("artifactId");
            }
        }
        return null;
    }

}
