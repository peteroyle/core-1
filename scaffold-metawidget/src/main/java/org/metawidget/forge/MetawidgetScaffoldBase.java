/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.metawidget.forge;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import org.jboss.forge.parser.JavaParser;
import org.jboss.forge.parser.java.JavaClass;
import org.jboss.forge.parser.java.JavaSource;
import org.jboss.forge.parser.xml.Node;
import org.jboss.forge.parser.xml.XMLParser;
import org.jboss.forge.project.Project;
import org.jboss.forge.project.dependencies.Dependency;
import org.jboss.forge.project.dependencies.DependencyBuilder;
import org.jboss.forge.project.dependencies.events.AddedDependencies;
import org.jboss.forge.project.facets.BaseFacet;
import org.jboss.forge.project.facets.DependencyFacet;
import org.jboss.forge.project.facets.JavaSourceFacet;
import org.jboss.forge.project.facets.ResourceFacet;
import org.jboss.forge.project.facets.WebResourceFacet;
import org.jboss.forge.project.facets.events.InstallFacets;
import org.jboss.forge.resources.DirectoryResource;
import org.jboss.forge.resources.FileResource;
import org.jboss.forge.resources.Resource;
import org.jboss.forge.scaffold.AccessStrategy;
import org.jboss.forge.scaffold.ScaffoldProvider;
import org.jboss.forge.scaffold.TemplateStrategy;
import org.jboss.forge.scaffold.util.ScaffoldUtil;
import org.jboss.forge.shell.ShellColor;
import org.jboss.forge.shell.ShellMessages;
import org.jboss.forge.shell.ShellPrintWriter;
import org.jboss.forge.shell.ShellPrompt;
import org.jboss.forge.spec.javaee.CDIFacet;
import org.jboss.forge.spec.javaee.FacesFacet;
import org.jboss.forge.spec.javaee.PersistenceFacet;
import org.jboss.forge.spec.javaee.ServletFacet;
import org.jboss.seam.render.TemplateCompiler;
import org.jboss.seam.render.template.CompiledTemplateResource;
import org.jboss.shrinkwrap.descriptor.api.spec.cdi.beans.BeansDescriptor;
import org.jboss.shrinkwrap.descriptor.api.spec.servlet.web.WebAppDescriptor;

/**
 * @author <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 */
public abstract class MetawidgetScaffoldBase extends BaseFacet implements ScaffoldProvider
{
   private static final String SEAM_PERSIST_TRANSACTIONAL_ANNO = "org.jboss.seam.transaction.Transactional";
   private static final String SEAM_PERSIST_INTERCEPTOR = "org.jboss.seam.transaction.TransactionInterceptor";

   private static final String REWRITE_CONFIG_TEMPLATE = "org/metawidget/scaffold/URLRewriteConfiguration.jv";
   private static final String BACKING_BEAN_TEMPLATE = "org/metawidget/scaffold/BackingBean.jv";
   private static final String VIEW_TEMPLATE = "org/metawidget/scaffold/view.xhtml";
   private static final String CREATE_TEMPLATE = "org/metawidget/scaffold/create.xhtml";
   private static final String LIST_TEMPLATE = "org/metawidget/scaffold/list.xhtml";
   private static final String E404_TEMPLATE = "org/metawidget/templates/404.xhtml";
   private static final String E500_TEMPLATE = "org/metawidget/templates/500.xhtml";
   private static final String INDEX_TEMPLATE = "org/metawidget/templates/index.xhtml";
   private static final String CONFIG_TEMPLATE = "org/metawidget/metawidget.xml";

   private final Dependency richfaces3UI = DependencyBuilder.create("org.richfaces.ui:richfaces-ui");
   private final Dependency richfaces3Impl = DependencyBuilder.create("org.richfaces.framework:richfaces-impl");
   private final Dependency richfaces4UI = DependencyBuilder.create("org.richfaces.ui:richfaces-components-ui");
   private final Dependency richfaces4Impl = DependencyBuilder.create("org.richfaces.core:richfaces-core-impl");

   private final CompiledTemplateResource viewTemplate;
   private final CompiledTemplateResource createTemplate;
   private final CompiledTemplateResource listTemplate;
   private final CompiledTemplateResource configTemplate;
   private final CompiledTemplateResource e404Template;
   private final CompiledTemplateResource e500Template;
   private final CompiledTemplateResource indexTemplate;

   private final ShellPrompt prompt;
   private final ShellPrintWriter writer;
   private final TemplateCompiler compiler;
   private final Event<InstallFacets> install;

   abstract protected List<Dependency> getMetawidgetDependencies();

   @Inject
   public MetawidgetScaffoldBase(final ShellPrompt prompt, final ShellPrintWriter writer,
            final TemplateCompiler compiler,
            final Event<InstallFacets> install)
   {
      this.prompt = prompt;
      this.writer = writer;
      this.compiler = compiler;
      this.install = install;
      viewTemplate = compiler.compile(VIEW_TEMPLATE);
      createTemplate = compiler.compile(CREATE_TEMPLATE);
      listTemplate = compiler.compile(LIST_TEMPLATE);
      configTemplate = compiler.compile(CONFIG_TEMPLATE);
      e404Template = compiler.compile(E404_TEMPLATE);
      e500Template = compiler.compile(E500_TEMPLATE);
      indexTemplate = compiler.compile(INDEX_TEMPLATE);
   }

   @Override
   public List<Resource<?>> setup(final Resource<?> template, final boolean overwrite)
   {
      List<Resource<?>> resources = generateIndex(template, overwrite);
      setupRichFaces(project);
      setupWebXML(project);
      setupRewrite(project);

      CDIFacet cdi = project.getFacet(CDIFacet.class);

      if (!project.getFacet(CDIFacet.class).getConfig().getInterceptors().contains(SEAM_PERSIST_INTERCEPTOR))
      {
         BeansDescriptor config = cdi.getConfig();
         config.interceptor(SEAM_PERSIST_INTERCEPTOR);
         cdi.saveConfig(config);
      }
      createMetawidgetConfig(false);

      return resources;
   }

   public void handleAddedDependencies(@Observes final AddedDependencies event)
   {
      Project project = event.getProject();
      if (project.hasFacet(MetawidgetScaffoldBase.class))
      {
         boolean richFacesUI = false;
         boolean richFacesImpl = false;
         for (Dependency d : event.getDependencies())
         {
            if (DependencyBuilder.areEquivalent(richfaces3UI, d))
            {
               richFacesUI = true;
            }
            if (DependencyBuilder.areEquivalent(richfaces3Impl, d))
            {
               richFacesImpl = true;
            }
            if (DependencyBuilder.areEquivalent(richfaces4UI, d))
            {
               richFacesUI = true;
            }
            if (DependencyBuilder.areEquivalent(richfaces4Impl, d))
            {
               richFacesImpl = true;
            }
         }

         if (richFacesImpl || richFacesUI)
         {
            setupRichFaces(project);
         }
      }
   }

   private void setupRichFaces(final Project project)
   {
      if ((project.getFacet(DependencyFacet.class).hasDependency(richfaces3UI)
               && project.getFacet(DependencyFacet.class).hasDependency(richfaces3Impl))
               || (project.getFacet(DependencyFacet.class).hasDependency(richfaces4UI)
               && project.getFacet(DependencyFacet.class).hasDependency(richfaces4Impl)))
      {
         if (prompt
                  .promptBoolean(writer.renderColor(ShellColor.YELLOW, "Metawidget")
                           + " has detected RichFaces installed in this project. Would you like to configure Metawidget to use RichFaces?"))
         {

            WebResourceFacet web = project.getFacet(WebResourceFacet.class);

            ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("WEB-INF/metawidget.xml"),
                     getClass().getResourceAsStream("/org/metawidget/metawidget-richfaces.xml"), true);
         }
      }
   }

   @Override
   public List<Resource<?>> generateFromEntity(final Resource<?> template, final JavaClass entity,
            final boolean overwrite)
   {
      List<Resource<?>> result = new ArrayList<Resource<?>>();
      try
      {
         JavaSourceFacet java = project.getFacet(JavaSourceFacet.class);
         WebResourceFacet web = project.getFacet(WebResourceFacet.class);

         CompiledTemplateResource backingBeanTemplate = compiler.compile(BACKING_BEAN_TEMPLATE);
         HashMap<Object, Object> context = new HashMap<Object, Object>();
         context.put("entity", entity);

         // Create the Backing Bean for this entity
         JavaClass viewBean = JavaParser.parse(JavaClass.class, backingBeanTemplate.render(context));
         viewBean.setPackage(java.getBasePackage() + ".view");
         viewBean.addAnnotation(SEAM_PERSIST_TRANSACTIONAL_ANNO);
         result.add(ScaffoldUtil.createOrOverwrite(prompt, java.getJavaResource(viewBean), viewBean.toString(),
                  overwrite));

         // Set new context for view generation
         context = getTemplateContext(template);
         String beanName = viewBean.getName();
         beanName = beanName.substring(0, 1).toLowerCase() + beanName.substring(1);
         String entityPropName = entity.getName();
         entityPropName = entityPropName.substring(0, 1).toLowerCase() + entityPropName.substring(1);
         context.put("beanName", beanName);
         context.put("entity", entity);
         context.put("entityPropName", entityPropName);

         // Generate views
         String type = entity.getName().toLowerCase();
         result.add(ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("scaffold/" + type + "/view.xhtml"),
                  viewTemplate.render(context), overwrite));
         result.add(ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("scaffold/" + type + "/create.xhtml"),
                  createTemplate.render(context),
                  overwrite));
         result.add(ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("scaffold/" + type + "/list.xhtml"),
                  listTemplate.render(context), overwrite));
      }
      catch (Exception e)
      {
         throw new RuntimeException("Error generating default scaffolding.", e);
      }
      return result;
   }

   private HashMap<Object, Object> getTemplateContext(final Resource<?> template)
   {
      HashMap<Object, Object> context;
      context = new HashMap<Object, Object>();
      context.put("template", template);
      context.put("templateStrategy", getTemplateStrategy());
      return context;
   }

   public void createMetawidgetConfig(final boolean overwrite)
   {
      WebResourceFacet web = project.getFacet(WebResourceFacet.class);

      ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("WEB-INF/metawidget.xml"),
               configTemplate.render(new HashMap<Object, Object>()), overwrite);
   }

   @Override
   @SuppressWarnings("unchecked")
   public boolean install()
   {
      if (!(project.hasFacet(WebResourceFacet.class) && project.hasFacet(PersistenceFacet.class)
               && project.hasFacet(CDIFacet.class) && project.hasFacet(FacesFacet.class)))
      {
         install.fire(new InstallFacets(WebResourceFacet.class, PersistenceFacet.class, CDIFacet.class,
                  FacesFacet.class));
      }

      DependencyFacet df = project.getFacet(DependencyFacet.class);

      String version = null;
      for (Dependency dependency : getMetawidgetDependencies()) {
         if (!df.hasDependency(dependency))
         {
            if (version == null)
            {
               dependency = prompt.promptChoiceTyped("Install which version of Metawidget Scaffold?",
                        df.resolveAvailableVersions(dependency));
               version = dependency.getVersion();
            }
            else
               dependency = DependencyBuilder.create(dependency).setVersion(version);

            df.addDependency(dependency);
         }
      }

      return true;
   }

   private void setupWebXML(final Project project)
   {
      ServletFacet servlet = project.getFacet(ServletFacet.class);

      Node webXML = removeConflictingErrorPages(servlet);
      WebResourceFacet web = project.getFacet(WebResourceFacet.class);
      servlet.getConfigFile().setContents(XMLParser.toXMLInputStream(webXML));

      WebAppDescriptor config = servlet.getConfig();
      config.errorPage(404, getAccessStrategy().getWebPaths(web.getWebResource("404.xhtml")).get(0));
      config.errorPage(500, getAccessStrategy().getWebPaths(web.getWebResource("500.xhtml")).get(0));

      servlet.saveConfig(config);

      ShellMessages
               .info(writer,
                        "JSF2 ( Mojarra 2.0.3 - http://java.net/jira/browse/JAVASERVERFACES-1826 ) and Metawidget currently require Partial State Saving to be disabled.");
   }

   private Node removeConflictingErrorPages(final ServletFacet servlet)
   {
      Node webXML = XMLParser.parse(servlet.getConfigFile().getResourceInputStream());
      Node root = webXML.getRoot();
      List<Node> errorPages = root.get("error-page");

      for (String code : Arrays.asList("404", "500")) {
         for (Node errorPage : errorPages)
         {
            if (code.equals(errorPage.getSingle("error-code").getText())
                     && prompt.promptBoolean("Your web.xml already contains an error page for " + code
                              + " status codes, replace it?"))
            {
               root.removeChild(errorPage);
            }
         }
      }
      return webXML;
   }

   private void setupRewrite(final Project project)
   {
      JavaSourceFacet java = project.getFacet(JavaSourceFacet.class);
      FacesFacet faces = project.getFacet(FacesFacet.class);

      CompiledTemplateResource configTemplate = compiler.compile(REWRITE_CONFIG_TEMPLATE);

      Map<Object, Object> context = new HashMap<Object, Object>();
      context.put("indexPage", faces.getWebPaths("/index.xhtml").get(0));
      context.put("notFoundPage", faces.getWebPaths("/404.xhtml").get(0));
      context.put("errorPage", faces.getWebPaths("/500.xhtml").get(0));
      context.put("listPage", faces.getWebPaths("/scaffold/{domain}/list.xhtml").get(0));
      context.put("createPage", faces.getWebPaths("/scaffold/{domain}/create.xhtml").get(0));
      context.put("viewPage", faces.getWebPaths("/scaffold/{domain}/view.xhtml").get(0));

      JavaSource<?> rewriteConfig = JavaParser.parse(configTemplate.render(context));
      rewriteConfig.setPackage(java.getBasePackage() + ".rewrite");

      try {
         ScaffoldUtil.createOrOverwrite(prompt, java.getJavaResource(rewriteConfig),
                  rewriteConfig.toString(), false);
      }
      catch (FileNotFoundException e) {
         throw new RuntimeException("Could not save Rewrite Configuration source file", e);
      }

      ResourceFacet resources = project.getFacet(ResourceFacet.class);
      DirectoryResource services = resources.getResourceFolder().getOrCreateChildDirectory("META-INF")
               .getOrCreateChildDirectory("services");

      // Register the configuration provider
      ScaffoldUtil.createOrOverwrite(prompt,
               (FileResource<?>) services.getChild("com.ocpsoft.rewrite.config.ConfigurationProvider"),
               rewriteConfig.getQualifiedName(), false);
   }

   @Override
   public boolean isInstalled()
   {
      final DependencyFacet df = project.getFacet(DependencyFacet.class);
      boolean hasMW = true;
      for (Dependency dependency : getMetawidgetDependencies())
      {
         if (!df.hasDependency(dependency))
         {
            hasMW = false;
            break;
         }
      }

      return hasMW;
   }

   @Override
   public List<Resource<?>> generateIndex(final Resource<?> template, final boolean overwrite)
   {
      List<Resource<?>> result = new ArrayList<Resource<?>>();
      WebResourceFacet web = project.getFacet(WebResourceFacet.class);

      project.getFacet(ServletFacet.class).getConfig().welcomeFile("index.html");

      generateTemplates(overwrite);
      HashMap<Object, Object> context = getTemplateContext(template);

      result.add(ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("index.html"), getClass()
               .getResourceAsStream("/org/metawidget/templates/index.html"), overwrite));

      result.add(ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("index.xhtml"),
               indexTemplate.render(context), overwrite));

      result.add(ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("404.xhtml"),
               e404Template.render(context), overwrite));

      result.add(ScaffoldUtil.createOrOverwrite(prompt, web.getWebResource("500.xhtml"),
               e500Template.render(context), overwrite));

      return result;
   }

   @Override
   public List<Resource<?>> getGeneratedResources()
   {
      throw new RuntimeException("Not yet implemented!");
   }

   @Override
   public AccessStrategy getAccessStrategy()
   {
      return new MetawidgetAccessStrategy(project);
   }

   @Override
   public TemplateStrategy getTemplateStrategy()
   {
      return new MetawidgetTemplateStrategy(project);
   }

   @Override
   public List<Resource<?>> generateTemplates(final boolean overwrite)
   {
      List<Resource<?>> result = new ArrayList<Resource<?>>();

      result.add(ScaffoldUtil.createOrOverwrite(prompt, (FileResource<?>) getTemplateStrategy().getDefaultTemplate(),
               getClass().getResourceAsStream("/org/metawidget/templates/forge-template.xhtml"), overwrite));

      return result;
   }

}
