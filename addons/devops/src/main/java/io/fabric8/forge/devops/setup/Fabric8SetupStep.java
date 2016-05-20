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
package io.fabric8.forge.devops.setup;

import io.fabric8.forge.addon.utils.MavenHelpers;
import io.fabric8.forge.addon.utils.VersionHelper;
import io.fabric8.forge.addon.utils.validator.ClassNameOrMavenPropertyValidator;
import io.fabric8.utils.Objects;
import io.fabric8.utils.Strings;
import org.apache.maven.model.Build;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Site;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jboss.forge.addon.dependencies.Dependency;
import org.jboss.forge.addon.dependencies.builder.DependencyBuilder;
import org.jboss.forge.addon.facets.FacetFactory;
import org.jboss.forge.addon.facets.constraints.FacetConstraint;
import org.jboss.forge.addon.maven.plugins.Configuration;
import org.jboss.forge.addon.maven.plugins.ConfigurationElement;
import org.jboss.forge.addon.maven.plugins.ExecutionBuilder;
import org.jboss.forge.addon.maven.plugins.MavenPlugin;
import org.jboss.forge.addon.maven.plugins.MavenPluginBuilder;
import org.jboss.forge.addon.maven.projects.MavenFacet;
import org.jboss.forge.addon.maven.projects.MavenPluginFacet;
import org.jboss.forge.addon.projects.Project;
import org.jboss.forge.addon.projects.dependencies.DependencyInstaller;
import org.jboss.forge.addon.projects.facets.ResourcesFacet;
import org.jboss.forge.addon.resource.ResourceFactory;
import org.jboss.forge.addon.templates.TemplateFactory;
import org.jboss.forge.addon.ui.context.UIBuilder;
import org.jboss.forge.addon.ui.context.UIContext;
import org.jboss.forge.addon.ui.context.UIExecutionContext;
import org.jboss.forge.addon.ui.context.UINavigationContext;
import org.jboss.forge.addon.ui.input.InputComponent;
import org.jboss.forge.addon.ui.input.UICompleter;
import org.jboss.forge.addon.ui.input.UIInput;
import org.jboss.forge.addon.ui.input.UISelectOne;
import org.jboss.forge.addon.ui.input.ValueChangeListener;
import org.jboss.forge.addon.ui.input.events.ValueChangeEvent;
import org.jboss.forge.addon.ui.metadata.UICommandMetadata;
import org.jboss.forge.addon.ui.metadata.WithAttributes;
import org.jboss.forge.addon.ui.result.NavigationResult;
import org.jboss.forge.addon.ui.result.Result;
import org.jboss.forge.addon.ui.result.Results;
import org.jboss.forge.addon.ui.util.Categories;
import org.jboss.forge.addon.ui.util.Metadata;
import org.jboss.forge.addon.ui.wizard.UIWizardStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;

import static io.fabric8.forge.addon.utils.MavenHelpers.getVersion;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.getDockerFromImage;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.hasSpringBoot;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.hasSpringBootWeb;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.hasWildlySwarm;
import static io.fabric8.forge.devops.setup.DockerSetupHelper.setupDocker;
import static io.fabric8.forge.devops.setup.SetupProjectHelper.findCamelArtifacts;

@FacetConstraint({MavenFacet.class, MavenPluginFacet.class, ResourcesFacet.class})
public class Fabric8SetupStep extends AbstractFabricProjectCommand implements UIWizardStep {
    private static final transient Logger LOG = LoggerFactory.getLogger(Fabric8SetupStep.class);

    public static final String EXTENSION_DAV_GROUP_ID = "org.apache.maven.wagon";
    public static final String EXTENSION_DAV_ARTIFACT_ID = "wagon-webdav-jackrabbit";
    public static final String EXTENSION_DAV_VERSION =
            getVersion(EXTENSION_DAV_GROUP_ID, EXTENSION_DAV_ARTIFACT_ID, "2.10");

    public static final String PLUGIN_JAVADOC_GROUP_ID = "org.apache.maven.plugins";
    public static final String PLUGIN_JAVADOC_ARTIFACT_ID = "maven-javadoc-plugin";
    public static final String PLUGIN_JAVADOC_VERSION =
            getVersion(PLUGIN_JAVADOC_GROUP_ID, PLUGIN_JAVADOC_ARTIFACT_ID, "2.10.3");

    private String[] jarImages = new String[]{DockerSetupHelper.DEFAULT_JAVA_IMAGE, DockerSetupHelper.S2I_JAVA_IMAGE};
    private String[] bundleImages = new String[]{DockerSetupHelper.DEFAULT_KARAF_IMAGE};
    private String[] warImages = new String[]{DockerSetupHelper.DEFAULT_TOMCAT_IMAGE, DockerSetupHelper.DEFAULT_WILDFLY_IMAGE};

    @Inject
    @WithAttributes(label = "Docker Organization", required = true, description = "The Docker organization/company")
    private UIInput<String> organization;

    @Inject
    @WithAttributes(label = "Docker Image From", required = true, description = "The Docker image to use as base line")
    private UIInput<String> from;

    @Inject
    @WithAttributes(label = "Container label", required = false, description = "Container label to use for the app")
    private UIInput<String> container;

    @Inject
    @WithAttributes(label = "Group label", required = false, description = "Group label to use for the app")
    private UIInput<String> group;

    @Inject
    @WithAttributes(label = "Icon", required = false, description = "Icon to use for the app")
    private UISelectOne<String> icon;

    @Inject
    @WithAttributes(label = "Main class", required = false, description = "Main class to use for Java standalone")
    private UIInput<String> main;

    @Inject
    @WithAttributes(label = "Kubernetes Service", required = false, defaultValue = "true", description = "Whether to create Kubernetes service if applicable")
    private UIInput<Boolean> service;

    @Inject
    @WithAttributes(label = "Kubernetes Readiness Probe", required = false, defaultValue = "true", description = "Whether to create Kubernetes readiness probe if applicable")
    private UIInput<Boolean> readinessProbe;

    @Inject
    @WithAttributes(label = "Maven Fabric8 Profiles", required = false, defaultValue = "true", description = "Include Maven fabric8 profiles for easily building and deploying")
    private UIInput<Boolean> profiles;

    @Inject
    private DependencyInstaller dependencyInstaller;

    @Inject
    private TemplateFactory factory;

    @Inject
    ResourceFactory resourceFactory;

    @Inject
    FacetFactory facetFactory;

    @Override
    public UICommandMetadata getMetadata(UIContext context) {
        return Metadata.forCommand(Fabric8SetupStep.class).name(
                "Fabric8: Setup").category(Categories.create(CATEGORY))
                .description("Configure the Fabric8 and Docker options for the project");
    }

    @Override
    public boolean isEnabled(UIContext context) {
        // this is a step in a wizard, you cannot run this standalone
        return false;
    }

    @Override
    public NavigationResult next(UINavigationContext context) throws Exception {
        return null;
    }

    @Override
    public void initializeUI(final UIBuilder builder) throws Exception {
        organization.setDefaultValue("fabric8");
        builder.add(organization);

        final Project project = getSelectedProject(builder.getUIContext());

        String packaging = getProjectPackaging(project);
        boolean springBoot = hasSpringBoot(project);

        // limit the choices depending on the project packaging
        final List<String> choices = new ArrayList<String>();
        if (packaging == null || springBoot || "jar".equals(packaging)) {
            String currentImage = getDockerFromImage(project);
            if (currentImage != null) {
                choices.add(currentImage);
            } else {
                choices.addAll(Arrays.asList(jarImages));
            }
        }
        if (packaging == null || "bundle".equals(packaging)) {
            choices.add(bundleImages[0]);
        }
        if (!springBoot && (packaging == null || "war".equals(packaging))) {
            choices.addAll(Arrays.asList(warImages));
        }
        from.setCompleter(new UICompleter<String>() {
            @Override
            public Iterable<String> getCompletionProposals(UIContext context, InputComponent<?, String> input, String value) {
                return choices;
            }
        });

        // is it possible to pre select a choice?
        if (choices.size() > 0) {
            String defaultChoice = choices.get(0);
            if (defaultChoice != null) {
                from.setDefaultValue(defaultChoice);
            }
        }

        from.addValueChangeListener(new ValueChangeListener() {
            @Override
            public void valueChanged(ValueChangeEvent event) {
                // use a listener so the docker step knows what we selected as it want to reuse
                builder.getUIContext().getAttributeMap().put("docker.from", event.getNewValue());
            }
        });
        builder.add(from);

        if (packaging == null || (!packaging.equals("war") && !packaging.equals("ear"))) {
            boolean jarImage = DockerSetupHelper.isJarImage(from.getValue());
            // TODO until we can detect reliably executable JARS versus mains lets not make this mandatory
/*
            main.setRequired(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    return jarImage;
                }
            });
*/
            // only enable main if its required
            // TODO we could disable if we knew this was an executable jar
            main.setEnabled(jarImage);
            if (project != null) {
                main.setDefaultValue(DockerSetupHelper.defaultMainClass(project));
            }
            main.addValidator(new ClassNameOrMavenPropertyValidator(true));
            main.addValueChangeListener(new ValueChangeListener() {
                @Override
                public void valueChanged(ValueChangeEvent event) {
                    // use a listener so the docker step knows what we selected as it want to reuse
                    builder.getUIContext().getAttributeMap().put("docker.main", event.getNewValue());
                }
            });
            builder.add(main);
        }

        container.setDefaultValue(new Callable<String>() {
            @Override
            public String call() throws Exception {
                String from = (String) builder.getUIContext().getAttributeMap().get("docker.from");
                if (from != null) {
                    return asContainer(from);
                }
                return null;
            }
        });

        // the from image values
        icon.setValueChoices(new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                Set<String> choices = new LinkedHashSet<String>();
                choices.add("activemq");
                choices.add("camel");
                choices.add("java");
                choices.add("jetty");
                choices.add("karaf");
                choices.add("mule");
                choices.add("spring-boot");
                choices.add("tomcat");
                choices.add("tomee");
                choices.add("vertx");
                choices.add("weld");
                choices.add("wildfly");
                return choices.iterator();
            }
        });
        icon.setDefaultValue(new Callable<String>() {
            @Override
            public String call() throws Exception {
                // favor Camel if there is a Camel dependency
                if (!findCamelArtifacts(project).isEmpty()) {
                    return "camel";
                }

                // popular containers
                boolean springBoot = hasSpringBoot(project);
                if (springBoot) {
                    return "spring-boot";
                }
                boolean vertx = hasSpringBoot(project);
                if (vertx) {
                    return "vertx";
                }

                // match by docker container name
                if (container.getValue() != null) {
                    for (String choice : icon.getValueChoices()) {
                        if (choice.equals(container.getValue())) {
                            return choice;
                        }
                    }
                }

                // use java by default
                return "java";
            }
        });

        group.setDefaultValue(new Callable<String>() {
            @Override
            public String call() throws Exception {
                // use the project name as default value
                return null;
            }
        });

        builder.add(profiles).add(icon).add(group).add(container);
    }

    @Override
    public Result execute(UIExecutionContext context) throws Exception {
        LOG.debug("Starting to setup fabric8 project");

        Project project = getSelectedProject(context.getUIContext());
        if (project == null) {
            return Results.fail("No pom.xml available so cannot edit the project!");
        }

        // setup docker-maven-plugin and fabric8-maven-plugin
        setupDocker(project, organization.getValue(), from.getValue(), main.getValue());
        LOG.debug("docker-maven-plugin now setup");
        setupFabricMavenPlugin(project);
        LOG.debug("fabric8-maven-plugin now setup");

        MavenFacet maven = project.getFacet(MavenFacet.class);
        Model pom = maven.getModel();

        // make sure we have resources as we need it later
        facetFactory.install(project, ResourcesFacet.class);

        LOG.debug("setting up fabric8 properties");
        setupFabricProperties(project, maven);

        LOG.debug("setting up fabric8 maven profiles");
        boolean f8profiles = setupFabricMavenProfiles(project, maven);

        String msg = "Added Fabric8 Maven support with base Docker image: " + from.getValue();
        if (f8profiles) {
            msg += ". Added the following Maven profiles [f8-build, f8-deploy, f8-local-deploy] to make building the project easier, e.g. mvn -Pf8-local-deploy";
        }
        return Results.success(msg);
    }

    private void importFabricBom(Project project, Model pom) {
        if (!MavenHelpers.hasManagedDependency(pom, "io.fabric8", "fabric8-project")) {
            Dependency bom = DependencyBuilder.create()
                    .setCoordinate(MavenHelpers.createCoordinate("io.fabric8", "fabric8-project", VersionHelper.fabric8Version(), "pom"))
                    .setScopeType("import");
            dependencyInstaller.installManaged(project, bom);
        }
    }

    public static void setupFabricMavenPlugin(Project project) {
        MavenPluginBuilder pluginBuilder;
        MavenPlugin plugin = MavenHelpers.findPlugin(project, "io.fabric8", "fabric8-maven-plugin");
        if (plugin != null) {
            // if there is an existing then leave it as-is
            LOG.info("Found existing fabric8-maven-plugin");
            pluginBuilder = null;
        } else {
            LOG.info("Adding fabric8-maven-plugin");
            // add fabric8 plugin
            pluginBuilder = MavenPluginBuilder.create()
                    .setCoordinate(MavenHelpers.createCoordinate("io.fabric8", "fabric8-maven-plugin", VersionHelper.fabric8Version()))
                    .addExecution(ExecutionBuilder.create().setId("json").setPhase("generate-resources").addGoal("json"))
                    .addExecution(ExecutionBuilder.create().setId("attach").setPhase("package").addGoal("attach"));
        }

        if (pluginBuilder != null) {
            MavenPluginFacet pluginFacet = project.getFacet(MavenPluginFacet.class);
            pluginFacet.addPlugin(pluginBuilder);
        }
    }

    public static void setupSitePlugin(Project project) {
        if (project != null) {
            MavenFacet mavenFacet = project.getFacet(MavenFacet.class);
            if (mavenFacet != null) {
                Model model = mavenFacet.getModel();
                if (model != null) {
                    Build build = model.getBuild();
                    if (build == null) {
                        build = new Build();
                        model.setBuild(build);
                    }
                    List<Extension> extensions = build.getExtensions();
                    if (extensions == null) {
                        extensions = new ArrayList<>();
                    }

                    // lets check if we have a wagon extension
                    boolean found = false;
                    for (Extension extension : extensions) {
                        String artifactId = extension.getArtifactId();
                        String groupId = extension.getGroupId();
                        if (Objects.equal(artifactId, EXTENSION_DAV_ARTIFACT_ID) &&
                                Objects.equal(groupId, EXTENSION_DAV_GROUP_ID)) {
                            found = true;
                            break;
                        }
                    }
                    boolean changed = false;
                    if (!found) {
                        Extension extension = new Extension();
                        extension.setGroupId(EXTENSION_DAV_GROUP_ID);
                        extension.setArtifactId(EXTENSION_DAV_ARTIFACT_ID);
                        extension.setVersion(EXTENSION_DAV_VERSION);
                        extensions.add(extension);
                        build.setExtensions(extensions);
                        changed = true;
                    }

                    // lets add any missing reports
                    Reporting reporting = model.getReporting();
                    if (reporting == null) {
                        reporting = new Reporting();
                        model.setReporting(reporting);
                    }
                    Map<String, ReportPlugin> reportPluginsAsMap = reporting.getReportPluginsAsMap();

                    // TODO should we check if the project has no java maybe? I guess its not a biggie if there isn't
                    if (!reportPluginsAsMap.containsKey(PLUGIN_JAVADOC_GROUP_ID + ":" + PLUGIN_JAVADOC_ARTIFACT_ID)) {
                        ReportPlugin reportPlugin = new ReportPlugin();
                        reportPlugin.setGroupId(PLUGIN_JAVADOC_GROUP_ID);
                        reportPlugin.setArtifactId(PLUGIN_JAVADOC_ARTIFACT_ID);
                        reportPlugin.setVersion(PLUGIN_JAVADOC_VERSION);
                        reporting.addPlugin(reportPlugin);

                        // lets set configuration
                        Xpp3Dom config = new Xpp3Dom("configuration");
                        addChildElement(config, "detectLinks", "true");
                        addChildElement(config, "detectJavaApiLink", "true");
                        addChildElement(config, "linksource", "true");

                        reportPlugin.setConfiguration(config);
                        changed = true;
                    }

                    // lets ensure there's a site distribution
                    DistributionManagement distributionManagement = model.getDistributionManagement();
                    if (distributionManagement == null) {
                        Parent parent = model.getParent();
                        if (parent == null) {
                            // lets only add a distributionManagement if there is no parent
                            // as usually we add the distributionManagement in a parent pom once to reuse across projects
                            distributionManagement = new DistributionManagement();
                            model.setDistributionManagement(distributionManagement);
                        }
                    }
                    if (distributionManagement != null) {
                        Site site = distributionManagement.getSite();
                        if (site == null) {
                            site = new Site();
                            distributionManagement.setSite(site);
                        }
                        String siteId = site.getId();
                        if (Strings.isNullOrBlank(siteId)) {
                            site.setId("website");
                            changed = true;
                        }
                        String siteUrl = site.getUrl();
                        if (Strings.isNullOrBlank(siteUrl)) {
                            site.setUrl("dav:http://content-repository/sites/${project.groupId}/${project.artifactId}/${project.version}");
                            changed = true;
                        }
                    }

                    if (changed) {
                        mavenFacet.setModel(model);
                    }
                }
            }
        }
    }

    public static void addChildElement(Xpp3Dom config, String name, String value) {
        Xpp3Dom includeDependencySources = new Xpp3Dom(name);
        includeDependencySources.setValue(value);
        config.addChild(includeDependencySources);
    }

    private void setupFabricProperties(Project project, MavenFacet maven) {
        // must install the dependency before re-loading maven model
        Boolean isService = service.getValue();
        Boolean isReadinessProbe = readinessProbe.getValue();
        String group = this.group.getValue();
        String containerName = container.getValue();
        String icon = this.icon.getValue();

        if (isReadinessProbe) {
            String servicePort = getDefaultServicePort(project);
            if (servicePort != null && hasSpringBoot(project)) {
                MavenHelpers.ensureMavenDependencyAdded(project, dependencyInstaller, "org.springframework.boot", "spring-boot-starter-actuator", null);
            }
        }

        setupFabric8Properties(project, maven, isService, isReadinessProbe, group, containerName, icon);
    }

    public static void setupFabric8Properties(Project project, MavenFacet maven, Boolean isService, Boolean isReadinessProbe, String group, String containerName, String icon) {
        // update properties section in pom.xml
        boolean updated = false;

        // re-load maven after we have changed it in the previous steps (so the pom is up to date)
        Model pom = maven.getModelResource().getCurrentModel();

        Properties properties = pom.getProperties();
        updated = MavenHelpers.updatePomProperty(properties, "fabric8.label.container", containerName, updated);
        String iconValue = icon;
        if (Strings.isNotBlank(iconValue)) {
            updated = MavenHelpers.updatePomProperty(properties, "fabric8.iconRef", "icons/" + iconValue, updated);
        }
        updated = MavenHelpers.updatePomProperty(properties, "fabric8.label.group", group, updated);

        // kubernetes service
        if (isService) {
            String servicePort = getDefaultServicePort(project);
            if (servicePort != null) {
                String name = pom.getArtifactId();
                // there is a max 24 chars limit in OpenShift/Kubernetes
                if (name.length() > 24) {
                    // print a warning
                    String msg = "The fabric8.service.name: " + name + " is being limited to max 24 chars as that is required by Kubernetes/Openshift."
                            + " You can change the name of the service in the <properties> section of the Maven pom file.";
                    // log and print to system out as the latter is what is seen in the CLI
                    LOG.warn(msg);
                    System.out.println(msg);
                    // clip the name at max 24 chars
                    name = name.substring(0, 24);
                }
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.service.containerPort", servicePort, updated);
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.service.port", "80", updated);
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.service.name", name, updated);
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.service.type", "LoadBalancer", updated);
            }
        }

        // kubernetes readiness probe
        if (isReadinessProbe) {
            String servicePort = getDefaultServicePort(project);
            if (servicePort != null) {

                String path;
                if (hasSpringBoot(project)) {
                    path = "/health";
                } else {
                    path = "/";
                }

                updated = MavenHelpers.updatePomProperty(properties, "fabric8.readinessProbe.httpGet.port", servicePort, updated);
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.readinessProbe.httpGet.path", path, updated);
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.readinessProbe.timeoutSeconds", "30", updated);
                updated = MavenHelpers.updatePomProperty(properties, "fabric8.readinessProbe.initialDelaySeconds", "5", updated);
            }
        }

        // to save then set the model
        if (updated) {
            maven.setModel(pom);
            LOG.debug("updated pom.xml");
        }
    }

    private boolean setupFabricMavenProfiles(Project project, MavenFacet maven) {
        if (profiles.getValue() == null || !profiles.getValue()) {
            return false;
        }

        // re-load maven after we have changed it in the previous steps (so the pom is up to date)
        Model pom = maven.getModelResource().getCurrentModel();

        boolean updated = false;
        Profile profile = MavenHelpers.findProfile(pom, "f8-build");
        if (profile == null) {
            profile = new Profile();
            profile.setId("f8-build");
            Build build = new Build();
            build.setDefaultGoal("clean install docker:build fabric8:json");
            profile.setBuild(build);
            pom.addProfile(profile);
            updated = true;
        }
        profile = MavenHelpers.findProfile(pom, "f8-deploy");
        if (profile == null) {
            profile = new Profile();
            profile.setId("f8-deploy");
            Properties prop = new Properties();
            prop.setProperty("fabric8.imagePullPolicySnapshot", "Always");
            prop.setProperty("fabric8.recreate", "true");
            profile.setProperties(prop);
            Build build = new Build();
            build.setDefaultGoal("clean install docker:build docker:push fabric8:json fabric8:apply");
            profile.setBuild(build);
            pom.addProfile(profile);
            updated = true;
        }
        profile = MavenHelpers.findProfile(pom, "f8-local-deploy");
        if (profile == null) {
            profile = new Profile();
            profile.setId("f8-local-deploy");
            Properties prop = new Properties();
            prop.setProperty("fabric8.recreate", "true");
            profile.setProperties(prop);
            Build build = new Build();
            build.setDefaultGoal("clean install docker:build fabric8:json fabric8:apply");
            profile.setBuild(build);
            pom.addProfile(profile);
            updated = true;
        }

        // to save then set the model
        if (updated) {
            maven.setModel(pom);
            LOG.debug("updated pom.xml");
        }

        return true;
    }

    /**
     * Try to determine the default service port.
     *
     * If this is a WAR, EAR or spring-boot then lets assume 8080.
     *
     * For Karaf we cannot assume its 8181 as web is not installed by default
     * and there is no default index html on the port to use etc
     */
    protected static String getDefaultServicePort(Project project) {
        if (hasWildlySwarm(project)) {
            // lets find the swarm plugin
            MavenPlugin plugin = MavenHelpers.findPlugin(project, "org.wildfly.swarm", "wildfly-swarm-plugin");
            if (plugin != null) {
                Configuration config = plugin.getConfig();
                if (config != null) {
                    ConfigurationElement properties = config.getConfigurationElement("properties");
                    if (properties != null) {
                        ConfigurationElement portElement = properties.getChildByName("swarm.http.port");
                        if (portElement != null) {
                            String text = portElement.getText();
                            if (Strings.isNotBlank(text)) {
                                return text;
                            }
                        }
                    }
                }
            }
        }
        String packaging = getProjectPackaging(project);
        if (Strings.isNotBlank(packaging)) {
            if (Objects.equal("war", packaging) || Objects.equal("ear", packaging)) {
                return "8080";
            }
        }
        boolean springBoot = hasSpringBootWeb(project);
        if (springBoot) {
            return "8080";
        }
        return null;
    }

    private static String asContainer(String fromImage) {
        int idx = fromImage.indexOf('/');
        if (idx > 0) {
            fromImage = fromImage.substring(idx + 1);
        }
        idx = fromImage.indexOf('-');
        if (idx > 0) {
            fromImage = fromImage.substring(0, idx);
        }
        return fromImage;
    }

    private static String getProjectPackaging(Project project) {
        if (project != null) {
            MavenFacet maven = project.getFacet(MavenFacet.class);
            return maven.getModel().getPackaging();
        }
        return null;
    }
}
