/**
 * This file is part of Jahia, next-generation open source CMS:
 * Jahia's next-generation, open source CMS stems from a widely acknowledged vision
 * of enterprise application convergence - web, search, document, social and portal -
 * unified by the simplicity of web content management.
 *
 * For more information, please visit http://www.jahia.com.
 *
 * Copyright (C) 2002-2011 Jahia Solutions Group SA. All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 * As a special exception to the terms and conditions of version 2.0 of
 * the GPL (or any later version), you may redistribute this Program in connection
 * with Free/Libre and Open Source Software ("FLOSS") applications as described
 * in Jahia's FLOSS exception. You should have received a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license
 *
 * Commercial and Supported Versions of the program (dual licensing):
 * alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms and conditions contained in a separate
 * written agreement between you and Jahia Solutions Group SA.
 *
 * If you are unsure which license is appropriate for your use,
 * please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.render.filter;

import org.slf4j.Logger;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.RenderService;
import org.jahia.services.render.Resource;
import org.jahia.services.render.scripting.Script;

import javax.jcr.RepositoryException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Base filter implementation that is also supports conditional execution, i.e.
 * the filter logic is executed only if all the specified conditions are
 * matched.
 *
 * @author Thomas Draier
 * @author Sergiy Shyrkov
 */
public abstract class AbstractFilter implements RenderFilter {

    /**
     * Evaluates to <code>true</code> if any of the underlying conditions
     * evaluates to true.
     *
     * @author Sergiy Shyrkov
     */
    public static class AnyOfCondition implements ExecutionCondition {

        private List<ExecutionCondition> conditions = new LinkedList<ExecutionCondition>();

        public void add(ExecutionCondition condition) {
            conditions.add(condition);
        }

        public boolean matches(RenderContext renderContext, Resource resource) {
            boolean matches = false;
            for (ExecutionCondition condition : conditions) {
                if (condition.matches(renderContext, resource)) {
                    matches = true;
                    break;
                }
            }
            return matches;
        }
    }

    public interface ExecutionCondition {
        /**
         * Returns <code>true</code> if the condition matches the specified
         * resource.
         *
         * @param renderContext Current RenderContext
         * @param resource      Resource being displayed
         * @return <code>true</code> if the condition matches the specified
         *         resource
         */
        boolean matches(RenderContext renderContext, Resource resource);
    }

    /**
     * Evaluates to <code>true</code> if the current resource is the main resource
     *
     * @author Thomas Draier
     */
    public static class MainResourceCondition implements ExecutionCondition {

        public boolean matches(RenderContext renderContext, Resource resource) {
            return (renderContext.getMainResource().getNode().getPath().equals(resource.getNode().getPath()));
        }
    }

    public static class ConfigurationCondition implements ExecutionCondition {
        private String conf;

        public ConfigurationCondition(String conf) {
            this.conf = conf;
        }

        public boolean matches(RenderContext renderContext, Resource resource) {
            return (resource.getContextConfiguration().equals(conf));
        }
    }

    /**
     * Evaluates to <code>true</code> if the current mode equals to the
     * specified one
     *
     * @author Sergiy Shyrkov
     */
    public static class ModeCondition implements ExecutionCondition {

        public static boolean matches(RenderContext renderContext, String mode) {
            boolean matches = false;
            if ("live".equals(mode)) {
                matches = renderContext.isLiveMode();
            } else if ("preview".equals(mode)) {
                matches = renderContext.isPreviewMode();
            } else if ("edit".equals(mode)) {
                matches = renderContext.isEditMode();
            } else if ("contribution".equals(mode)) {
                matches = renderContext.isContributionMode();
            } else {
                throw new IllegalArgumentException("Unsupported mode '" + mode + "'");
            }
            return matches;
        }
        
        private String mode;

        /**
         * Initializes an instance of this class.
         *
         * @param mode the target mode to check for
         */
        public ModeCondition(String mode) {
            super();
            this.mode = mode;
        }

        public boolean matches(RenderContext renderContext, Resource resource) {
            return matches(renderContext, mode);
        }
    }

    /**
     * Evaluates to <code>true</code> if the current resource's template type
     * matches the provided one.
     *
     * @author Sergiy Shyrkov
     */
    public static class ModuleCondition extends PatternCondition {

        public ModuleCondition(String module) {
            super(module, false);
        }

        public ModuleCondition(String module, boolean isRegExp) {
            super(module, isRegExp);
        }

        public String getValue(RenderContext renderContext, Resource resource) {
            return ((Script) renderContext.getRequest().getAttribute("script")).getView().getModule().getName();
        }
    }

    /**
     * Evaluates to <code>true</code> if the current resource's node has the
     * specified node type.
     *
     * @author Sergiy Shyrkov
     */
    public static class NodeTypeCondition implements ExecutionCondition {

        private String nodeTypeName;

        public NodeTypeCondition(String nodeTypeName) {
            super();
            this.nodeTypeName = nodeTypeName;
        }

        public boolean matches(RenderContext renderContext, Resource resource) {
            boolean matches = false;
            JCRNodeWrapper node = resource.getNode();
            if (node != null) {
                try {
                    matches = node.isNodeType(nodeTypeName);
                } catch (RepositoryException e) {
                    logger.warn("Unable to evaluate filter execution condition for resource " + resource, e);
                }
            } else {
                logger.warn("Unable to evaluate filter execution condition." + "Node is null for resource " + resource);
            }

            return matches;
        }
    }

    /**
     * Inverts the result of the underlying condition.
     *
     * @author Sergiy Shyrkov
     */
    public static class NotCondition implements ExecutionCondition {

        private ExecutionCondition condition;

        public NotCondition(ExecutionCondition condition) {
            super();
            this.condition = condition;
        }

        public boolean matches(RenderContext renderContext, Resource resource) {
            return !condition.matches(renderContext, resource);
        }
    }

    /**
     * Evaluates to <code>true</code> if the current resource's template type
     * matches the provided one.
     *
     * @author Sergiy Shyrkov
     */
    public static abstract class PatternCondition implements ExecutionCondition {

        private String exactMatch;
        private Pattern pattern;

        /**
         * Initializes an instance of this class.
         *
         * @param pattern  the string to be matched
         * @param isRegExp do we consider this pattern as a regular expression and
         *                 not as an exact string?
         */
        public PatternCondition(String pattern, boolean isRegExp) {
            super();
            if (isRegExp) {
                this.pattern = Pattern.compile(pattern);
            } else {
                exactMatch = pattern;
            }

        }

        protected abstract String getValue(RenderContext renderContext, Resource resource);

        public final boolean matches(RenderContext renderContext, Resource resource) {
            return pattern != null ? pattern.matcher(getValue(renderContext, resource)).matches() : exactMatch.equals(
                    getValue(renderContext, resource));
        }
    }

    /**
     * Evaluates to <code>true</code> if the current resource's template matches
     * the provided one.
     *
     * @author Sergiy Shyrkov
     */
    public static class TemplateCondition extends PatternCondition {

        public TemplateCondition(String template) {
            super(template, false);
        }

        public TemplateCondition(String template, boolean isRegExp) {
            super(template, isRegExp);
        }

        public String getValue(RenderContext renderContext, Resource resource) {
            return resource.getResolvedTemplate();
        }
    }

    /**
     * Evaluates to <code>true</code> if the current resource's template type
     * matches the provided one.
     *
     * @author Sergiy Shyrkov
     */
    public static class TemplateTypeCondition extends PatternCondition {

        public TemplateTypeCondition(String templateType) {
            super(templateType, false);
        }

        public TemplateTypeCondition(String templateType, boolean isRegExp) {
            super(templateType, isRegExp);
        }

        public String getValue(RenderContext renderContext, Resource resource) {
            return resource.getTemplateType();
        }
    }

    public static class AjaxRequestCondition implements ExecutionCondition {

        /**
         * Returns <code>true</code> if the condition matches the specified
         * resource.
         *
         * @param renderContext Current RenderContext
         * @param resource      Resource being displayed
         * @return <code>true</code> if the condition matches the specified
         *         resource
         */
        public boolean matches(RenderContext renderContext, Resource resource) {
            return renderContext.isAjaxRequest();
        }
    }

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(AbstractFilter.class);

    private List<ExecutionCondition> conditions = new LinkedList<ExecutionCondition>();
    
    private boolean disabled;
    
    private int priority = 99;

    protected RenderService service;

    /**
     * Initializes an instance of this class.
     */
    public AbstractFilter() {
        super();
    }

    /**
     * Initializes an instance of this class.
     *
     * @param conditions the execution conditions to be matched.
     */
    public AbstractFilter(ExecutionCondition... conditions) {
        this();
        for (ExecutionCondition cond : conditions) {
            addCondition(cond);
        }
    }

    /**
     * Get the priority number of the filter. Filter will be executed in order of priority, lower first.
     *
     * @return priority
     */
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    /**
     * Adds the specified condition for this filter.
     *
     * @param condition the condition to be added for this filter
     */
    public void addCondition(ExecutionCondition condition) {
        conditions.add(condition);
    }

    /**
     * Adds the specified condition for this filter at the specified position.
     *
     * @param condition the condition to be added for this filter
     */
    public void addCondition(int index, ExecutionCondition condition) {
        conditions.add(index, condition);
    }

    public boolean areConditionsMatched(RenderContext renderContext, Resource resource) {
        if (disabled) {
            return false;
        }
        
        boolean matches = true;
        for (ExecutionCondition condition : conditions) {
            if (!condition.matches(renderContext, resource)) {
                matches = false;
                break;
            }
        }
        return matches;
    }

    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain)
            throws Exception {
        return previousOut;
    }

    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        return null;
    }

    /**
     * If set to <code>true</code>, the current filter will only be executed on
     * the main resource.
     *
     * @param bool set to <code>true</code> to limit filter execution for main
     *             resource only.
     */
    public void setApplyOnMainResource(boolean bool) {
        if (bool) {
            addCondition(new MainResourceCondition());
        }
    }

    /**
     * Comma-separated list of module names this filter will be executed for
     * (all others are skipped).
     *
     * @param modules comma-separated list of module names this filter will be
     *                executed for (all others are skipped)
     */
    public void setApplyOnModules(String modules) {
        if (modules.contains(",")) {
            AnyOfCondition condition = new AnyOfCondition();
            for (String module : modules.split(",")) {
                condition.add(new ModuleCondition(module.trim()));
            }
            addCondition(condition);
        } else {
            addCondition(new ModuleCondition(modules));
        }
    }

    /**
     * Comma-separated list of node type names this filter will be executed for
     * (all others are skipped).
     *
     * @param nodeTypes comma-separated list of node type names this filter will
     *                  be executed for (all others are skipped)
     */
    public void setApplyOnNodeTypes(String nodeTypes) {
        if (nodeTypes.contains(",")) {
            AnyOfCondition condition = new AnyOfCondition();
            for (String nodeType : nodeTypes.split(",")) {
                condition.add(new NodeTypeCondition(nodeType.trim()));
            }
            addCondition(condition);
        } else {
            addCondition(new NodeTypeCondition(nodeTypes));
        }
    }

    /**
     * Comma-separated list of modes this filter will be executed for
     * (all others are skipped).
     *
     * @param modes comma-separated list of modes this filter will
     *              be executed for (all others are skipped)
     */
    public void setApplyOnModes(String modes) {
        if (modes.contains(",")) {
            AnyOfCondition condition = new AnyOfCondition();
            for (String mode : modes.split(",")) {
                condition.add(new ModeCondition(mode.trim()));
            }
            addCondition(0, condition);
        } else {
            addCondition(0, new ModeCondition(modes));
        }
    }

    /**
     * Comma-separated list of resource configuration this filter will be executed for
     * (all others are skipped).
     *
     * @param configurations comma-separated list of configurations this filter will
     *                       be executed for (all others are skipped)
     */
    public void setApplyOnConfigurations(String configurations) {
        if (configurations.contains(",")) {
            AnyOfCondition condition = new AnyOfCondition();
            for (String conf : configurations.split(",")) {
                condition.add(new ConfigurationCondition(conf.trim()));
            }
            addCondition(0, condition);
        } else {
            addCondition(0, new ConfigurationCondition(configurations));
        }
    }

    /**
     * Comma-separated list of template names this filter will be executed for
     * (all others are skipped).
     *
     * @param templates comma-separated list of template names this filter will
     *                  be executed for (all others are skipped)
     */
    public void setApplyOnTemplates(String templates) {
        if (templates.contains(",")) {
            AnyOfCondition condition = new AnyOfCondition();
            for (String template : templates.split(",")) {
                condition.add(new TemplateCondition(template.trim()));
            }
            addCondition(condition);
        } else {
            addCondition(new TemplateCondition(templates));
        }
    }

    /**
     * Comma-separated list of template type names this filter will be executed
     * for (all others are skipped).
     *
     * @param templateTypes comma-separated list of template type names this filter
     *                      will be executed for (all others are skipped)
     */
    public void setApplyOnTemplateTypes(String templateTypes) {
        if (templateTypes.contains(",")) {
            AnyOfCondition condition = new AnyOfCondition();
            for (String templateType : templateTypes.split(",")) {
                condition.add(new TemplateTypeCondition(templateType.trim()));
            }
            addCondition(condition);
        } else {
            addCondition(new TemplateTypeCondition(templateTypes));
        }
    }

    /**
     * Adds specified conditions to the list of filter conditions. N.B. this
     * operation does not reset the list of conditions, but rather appends
     * specified list to the current one.
     *
     * @param conditions list of conditions to be added for this filter
     */
    public void setConditions(Set<ExecutionCondition> conditions) {
        this.conditions.addAll(conditions);
    }

    public final void setRenderService(RenderService service) {
        this.service = service;
    }

    /**
     * If set to <code>true</code>, the current filter won't be executed on main resources.
     *
     * @param bool set to <code>true</code> to prevent filter from executing on main module
     */
    public void setSkipOnMainResource(boolean bool) {
        if (bool) {
            addCondition(new NotCondition(new MainResourceCondition()));
        }
    }

    /**
     * Comma-separated list of module names this filter won't be executed for.
     *
     * @param modules comma-separated list of module names this filter won't be
     *                executed for
     */
    public void setSkipOnModules(String modules) {
        ExecutionCondition condition = null;
        if (modules.contains(",")) {
            AnyOfCondition anyOf = new AnyOfCondition();
            for (String module : modules.split(",")) {
                anyOf.add(new ModuleCondition(module.trim()));
            }
            condition = anyOf;
        } else {
            condition = new ModuleCondition(modules);
        }
        addCondition(new NotCondition(condition));
    }

    /**
     * Comma-separated list of mode names this filter won't be executed
     * for.
     *
     * @param modes comma-separated list of node type names this filter
     *              won't be executed for
     */
    public void setSkipOnModes(String modes) {
        ExecutionCondition condition = null;
        if (modes.contains(",")) {
            AnyOfCondition anyOf = new AnyOfCondition();
            for (String mode : modes.split(",")) {
                anyOf.add(new ModeCondition(mode.trim()));
            }
            condition = anyOf;
        } else {
            condition = new ModeCondition(modes);
        }
        addCondition(0, new NotCondition(condition));
    }

    /**
     * Comma-separated list of configuration names this filter won't be executed
     * for.
     *
     * @param configurations comma-separated list of node type names this filter
     *                       won't be executed for
     */
    public void setSkipOnConfiguration(String configurations) {
        ExecutionCondition condition = null;
        if (configurations.contains(",")) {
            AnyOfCondition anyOf = new AnyOfCondition();
            for (String configuration : configurations.split(",")) {
                anyOf.add(new ConfigurationCondition(configuration.trim()));
            }
            condition = anyOf;
        } else {
            condition = new ConfigurationCondition(configurations);
        }
        addCondition(0, new NotCondition(condition));
    }

    /**
     * Comma-separated list of node type names this filter won't be executed
     * for.
     *
     * @param nodeTypes comma-separated list of node type names this filter
     *                  won't be executed for
     */
    public void setSkipOnNodeTypes(String nodeTypes) {
        ExecutionCondition condition = null;
        if (nodeTypes.contains(",")) {
            AnyOfCondition anyOf = new AnyOfCondition();
            for (String nodeType : nodeTypes.split(",")) {
                anyOf.add(new NodeTypeCondition(nodeType.trim()));
            }
            condition = anyOf;
        } else {
            condition = new NodeTypeCondition(nodeTypes);
        }
        addCondition(new NotCondition(condition));
    }

    /**
     * Comma-separated list of template names this filter won't be executed for.
     *
     * @param templates comma-separated list of template names this filter won't
     *                  be executed for
     */
    public void setSkipOnTemplates(String templates) {
        ExecutionCondition condition = null;
        if (templates.contains(",")) {
            AnyOfCondition anyOf = new AnyOfCondition();
            for (String template : templates.split(",")) {
                anyOf.add(new TemplateCondition(template.trim()));
            }
            condition = anyOf;
        } else {
            condition = new TemplateCondition(templates);
        }
        addCondition(new NotCondition(condition));
    }

    /**
     * Comma-separated list of template type names this filter won't be executed
     * for.
     *
     * @param templateTypes comma-separated list of template type names this
     *                      filter won't be executed for
     */
    public void setSkipOnTemplateTypes(String templateTypes) {
        ExecutionCondition condition = null;
        if (templateTypes.contains(",")) {
            AnyOfCondition anyOf = new AnyOfCondition();
            for (String templateType : templateTypes.split(",")) {
                anyOf.add(new TemplateTypeCondition(templateType.trim()));
            }
            condition = anyOf;
        } else {
            condition = new TemplateTypeCondition(templateTypes);
        }
        addCondition(new NotCondition(condition));
    }

    public void setSkipOnAjaxRequest(Boolean skip) {
        if(skip) {
            addCondition(new NotCondition(new AjaxRequestCondition()));
        }
    }

    public int compareTo(RenderFilter o) {
        int i = getPriority() - o.getPriority();
        return i != 0 ? i : getClass().getName().compareTo(o.getClass().getName());
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * <p/>
     * The <code>equals</code> method implements an equivalence relation
     * on non-null object references:
     * <ul>
     * <li>It is <i>reflexive</i>: for any non-null reference value
     * <code>x</code>, <code>x.equals(x)</code> should return
     * <code>true</code>.
     * <li>It is <i>symmetric</i>: for any non-null reference values
     * <code>x</code> and <code>y</code>, <code>x.equals(y)</code>
     * should return <code>true</code> if and only if
     * <code>y.equals(x)</code> returns <code>true</code>.
     * <li>It is <i>transitive</i>: for any non-null reference values
     * <code>x</code>, <code>y</code>, and <code>z</code>, if
     * <code>x.equals(y)</code> returns <code>true</code> and
     * <code>y.equals(z)</code> returns <code>true</code>, then
     * <code>x.equals(z)</code> should return <code>true</code>.
     * <li>It is <i>consistent</i>: for any non-null reference values
     * <code>x</code> and <code>y</code>, multiple invocations of
     * <tt>x.equals(y)</tt> consistently return <code>true</code>
     * or consistently return <code>false</code>, provided no
     * information used in <code>equals</code> comparisons on the
     * objects is modified.
     * <li>For any non-null reference value <code>x</code>,
     * <code>x.equals(null)</code> should return <code>false</code>.
     * </ul>
     * <p/>
     * The <tt>equals</tt> method for class <code>Object</code> implements
     * the most discriminating possible equivalence relation on objects;
     * that is, for any non-null reference values <code>x</code> and
     * <code>y</code>, this method returns <code>true</code> if and only
     * if <code>x</code> and <code>y</code> refer to the same object
     * (<code>x == y</code> has the value <code>true</code>).
     * <p/>
     * Note that it is generally necessary to override the <tt>hashCode</tt>
     * method whenever this method is overridden, so as to maintain the
     * general contract for the <tt>hashCode</tt> method, which states
     * that equal objects must have equal hash codes.
     *
     * @param obj the reference object with which to compare.
     * @return <code>true</code> if this object is the same as the obj
     *         argument; <code>false</code> otherwise.
     * @see #hashCode()
     * @see java.util.Hashtable
     */
    @Override
    public boolean equals(Object obj) {
        if(this.getClass().getName().equals(obj.getClass().getName())) {
            return this.getPriority()==((AbstractFilter)obj).getPriority();
        }
        return false; 
    }

    public void handleError(RenderContext renderContext, Resource resource, RenderChain renderChain, Exception e) {
        logger.debug("Handling exception {} in {}", e.getMessage(), resource.getNode().getPath());
    }

    public void finalize(RenderContext renderContext, Resource resource, RenderChain renderChain) {
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }
}
