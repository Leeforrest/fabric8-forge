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

import java.io.InputStream;
import java.util.List;
import java.util.function.Function;

import io.fabric8.forge.camel.commands.project.helper.PoorMansLogger;
import io.fabric8.forge.camel.commands.project.helper.XmlRouteParser;
import io.fabric8.forge.camel.commands.project.model.CamelEndpointDetails;
import org.jboss.forge.addon.projects.facets.WebResourcesFacet;
import org.jboss.forge.addon.resource.Resource;
import org.jboss.forge.addon.resource.visit.ResourceVisitor;
import org.jboss.forge.addon.resource.visit.VisitContext;

import static io.fabric8.forge.camel.commands.project.completer.XmlResourcesCamelEndpointsVisitor.containsCamelRoutes;

public class XmlWebResourcesCamelEndpointsVisitor implements ResourceVisitor {

    private static final PoorMansLogger LOG = new PoorMansLogger(false);

    private final WebResourcesFacet facet;
    private final List<CamelEndpointDetails> endpoints;
    private final Function<String, Boolean> filter;

    public XmlWebResourcesCamelEndpointsVisitor(WebResourcesFacet facet, List<CamelEndpointDetails> endpoints, Function<String, Boolean> filter) {
        this.facet = facet;
        this.endpoints = endpoints;
        this.filter = filter;
    }

    @Override
    public void visit(VisitContext visitContext, Resource<?> resource) {
        String name = resource.getName();
        if (name.endsWith(".xml")) {

            boolean include = true;
            if (filter != null) {
                String fqn = resource.getFullyQualifiedName();
                Boolean out = filter.apply(fqn);
                LOG.info("Filter " + name + " -> " + out);
                include = out == null || out;
            }

            if (include) {
                boolean camel = containsCamelRoutes(resource);
                if (camel) {
                    // find all the endpoints (currently only <endpoint> and within <route>)
                    try {
                        InputStream is = resource.getResourceInputStream();
                        String fqn = resource.getFullyQualifiedName();
                        String baseDir = facet.getWebRootDirectory().getFullyQualifiedName();
                        XmlRouteParser.parseXmlRouteEndpoints(is, baseDir, fqn, endpoints);
                    } catch (Throwable e) {
                        // ignore
                    }
                }
            }
        }
    }
}
