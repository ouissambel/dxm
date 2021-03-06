/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2018 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ===================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 */
package org.jahia.services.content.interceptor;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.util.Text;
import org.jahia.api.Constants;
import org.jahia.bin.Jahia;
import org.jahia.services.content.*;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.ContextPlaceholdersReplacer;
import org.jahia.services.render.filter.HtmlTagAttributeTraverser;
import org.jahia.services.render.filter.HtmlTagAttributeTraverser.HtmlTagAttributeVisitor;
import org.jahia.utils.WebUtils;
import org.jahia.utils.i18n.Messages;
import org.slf4j.Logger;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.i18n.LocaleContextHolder;

import javax.jcr.*;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.version.VersionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jahia.api.Constants.JAHIAMIX_REFERENCES_IN_FIELD;
import static org.jahia.api.Constants.JAHIA_REFERENCE_IN_FIELD_PREFIX;

/**
 * URL Interceptor catches internal URLs inside richtext, and transform them to store references to the pointed nodes
 * instead of paths. It also replaces the servlet context and servlet name by a placeholder so that the stored link
 * is not dependant of the deployment.
 * <p/>
 * Two types of links are detected : CMS links (like /cms/render/default/en/sites/ACME/home.html ) and files
 * links ( /files/sites/ACME/files/Pictures/BannerTeaser/img-home-fr.jpg ).
 * <p/>
 * File path are transformed with references placeholders like ##ref:link1##. References targets are stored in the
 * jmix:referenceInField child nodes.
 */
public class URLInterceptor extends BaseInterceptor implements InitializingBean {

    private static Logger logger = org.slf4j.LoggerFactory.getLogger(URLInterceptor.class);

    private String dmsContext;
    private String cmsContext;

    private static final String DOC_CONTEXT_PLACEHOLDER = "##doc-context##/";
    private static final String CMS_CONTEXT_PLACEHOLDER = "##cms-context##/";
    private static final Pattern DOC_CONTEXT_PLACEHOLDER_PATTERN = Pattern.compile(DOC_CONTEXT_PLACEHOLDER, Pattern.LITERAL);
    private static final Pattern CMS_CONTEXT_PLACEHOLDER_PATTERN = Pattern.compile(CMS_CONTEXT_PLACEHOLDER, Pattern.LITERAL);

    private Pattern cmsPattern;
    private Pattern cmsPatternWithContextPlaceholder;
    private Pattern refPattern;

    private HtmlTagAttributeTraverser urlTraverser;

    private String escape(String s) {
        s = s.replace("{", "\\{");
        s = s.replace("}", "\\}");
        return s;
    }

    /**
     * Initializes an instance of this class.
     *
     * @param urlTraverser the URL utility class to visit HTML tag attributes
     */
    public URLInterceptor(HtmlTagAttributeTraverser urlTraverser) {
        super();
        this.urlTraverser = urlTraverser;
    }

    public void beforeRemove(JCRNodeWrapper node, String name, ExtendedPropertyDefinition definition) throws VersionException, LockException, ConstraintViolationException, RepositoryException {
    	
        if (node.isNodeType(JAHIAMIX_REFERENCES_IN_FIELD)) {
            NodeIterator ni = node.getNodes(JAHIA_REFERENCE_IN_FIELD_PREFIX);
            if (definition.isInternationalized()) {
                name += "_" + node.getSession().getLocale();
            }
            while (ni.hasNext()) {
                JCRNodeWrapper ref = (JCRNodeWrapper) ni.next();
                if (name.equals(ref.getProperty("j:fieldName").getString())) {
                    ref.remove();
                }
            }
        }
    }

    /**
     * Transform user URL with servlet context and links placeholders for storage.
     * <p/>
     * Only URLs starting with /<context>/cms or /<context>/files are recognized.
     * <p/>
     * CMS URLs can use mode and language placeholders : /<context>/cms/render/default/en/sites/ACME/home.html and
     * /<context>/cms/##mode##/##lang##/sites/ACME/home.html are both recognized.
     * <p/>
     * If any link is invalid, a ConstraintViolationException is thrown.
     * <p/>
     * Add jmix:referencesInField mixin type to the parent node and j:referenceInField with the list of references
     * contained in the value.
     *
     * @param node
     * @param name
     * @param definition
     * @param originalValue Original value  @return Value to set, or null   @return
     * @throws ValueFormatException
     * @throws VersionException
     * @throws LockException
     * @throws ConstraintViolationException
     * @throws RepositoryException in case of JCR-related errors
     */
    public Value beforeSetValue(final JCRNodeWrapper node, String name, final ExtendedPropertyDefinition definition, Value originalValue) throws ValueFormatException, VersionException, LockException, ConstraintViolationException, RepositoryException {
        String content = originalValue.getString();
        
        // if the node is a translated node, then take the parent to have the references
        JCRNodeWrapper nodeWithReferences = node.isNodeType(Constants.JAHIANT_TRANSLATION) ? node.getParent() : node;

        if (definition.isInternationalized()) {
            Locale locale = node.getSession().getLocale();
            if (locale == null) {
                // This might happen under publication
                if (node.isNodeType(Constants.JAHIANT_TRANSLATION)) {
                    name += "_" + node.getProperty("jcr:language").getString();
                }
            } else {
                name += "_" + locale;
            }
        }

        final Map<String, Long> refs = new HashMap<String, Long>();

        if (logger.isDebugEnabled()) {
            logger.debug("Intercept setValue for " + node.getPath() + "/" + name);
        }

        if (nodeWithReferences.isNodeType(JAHIAMIX_REFERENCES_IN_FIELD)) {
            NodeIterator ni = nodeWithReferences.getNodes(JAHIA_REFERENCE_IN_FIELD_PREFIX);
            while (ni.hasNext()) {
                JCRNodeWrapper ref = (JCRNodeWrapper) ni.next();
                if (name.equals(ref.getProperty("j:fieldName").getString()) && ref.hasProperty("j:reference")) {
                    refs.put(ref.getProperty("j:reference").getString(), Long.valueOf(StringUtils.substringAfterLast(ref.getName(), "_")));
                }
            }
        }

        final Map<String, Long> newRefs = new HashMap<String, Long>();

        String result;
        try {
            result = urlTraverser.traverse(content, new HtmlTagAttributeVisitor() {
                public String visit(String value, RenderContext context, String tagName, String attrName, Resource resource) {
                    if (StringUtils.isNotEmpty(value)) {
                        try {
                            value = replaceRefsByPlaceholders(value, newRefs, refs, node.getSession().getWorkspace().getName(), node.getSession().getLocale(), node, definition);
                        } catch (RepositoryException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return value;
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof RepositoryException) {
                throw (RepositoryException) e.getCause();
            } else {
                throw e;
            }
        }

        Set<String> refsToRemove = new HashSet<>(refs.size());
        if (!newRefs.equals(refs)) {
            if (!newRefs.isEmpty() && !nodeWithReferences.isNodeType(JAHIAMIX_REFERENCES_IN_FIELD)) {
                nodeWithReferences.addMixin(JAHIAMIX_REFERENCES_IN_FIELD);
            }
            if (logger.isDebugEnabled()) {
                logger.debug("New references : " + newRefs);
            }

            NodeIterator ni = nodeWithReferences.getNodes(JAHIA_REFERENCE_IN_FIELD_PREFIX);
            while (ni.hasNext()) {
                JCRNodeWrapper ref = (JCRNodeWrapper) ni.next();
                if (name.equals(ref.getProperty("j:fieldName").getString()) && (!ref.hasProperty("j:reference") || !newRefs.containsKey(ref.getProperty("j:reference").getString()))) {
                    refsToRemove.add(ref.getName());
                }
            }

            for (Map.Entry<String, Long> entry : newRefs.entrySet()) {
                if (!refs.containsKey(entry.getKey())) {
                    JCRNodeWrapper ref = nodeWithReferences.addNode("j:referenceInField_" + Text.escapeIllegalJcrChars(name) + "_" + entry.getValue(), "jnt:referenceInField");
                    ref.setProperty("j:fieldName", name);
                    ref.setProperty("j:reference", entry.getKey());
                }
            }
        }

        for (String refToRemove : refsToRemove) {
            nodeWithReferences.getNode(refToRemove).remove();
        }

        if (!result.equals(content)) {
            return node.getSession().getValueFactory().createValue(result);
        }
        return originalValue;
    }

    /**
     * Called before setting the value on the property. Can throw an exception if the value is not valid, and transform
     * the value into another value.
     * <p/>
     * The interceptor can also directly operate on the property before the property is effectively set.
     * <p/>
     * Returns the value to set - or null if no property need to be set, but without sending an error.
     *
     * @param node
     * @param name
     * @param definition
     * @param originalValues Original value  @return Value to set, or null   @throws ValueFormatException
     * @throws javax.jcr.version.VersionException
     * @throws javax.jcr.lock.LockException
     * @throws javax.jcr.nodetype.ConstraintViolationException
     */
    public Value[] beforeSetValues(JCRNodeWrapper node, String name, ExtendedPropertyDefinition definition, Value[] originalValues) throws ValueFormatException, VersionException, LockException, ConstraintViolationException, RepositoryException {
        Value[] res = new Value[originalValues.length];

        for (int i = 0; i < originalValues.length; i++) {
            Value originalValue = originalValues[i];
            res[i] = beforeSetValue(node, name, definition, originalValue);
        }
        return res;
    }

    /**
     * Restore value by replace context ( ##doc-context## and ##cms-context## ) and references ( ##ref:link[0-9]+##
     * placeholders. Resolves reference node and put path instead to make a valid link. If referenced node is not found,
     * log an error and put # as a path.
     *
     * @param property
     * @param storedValue
     * @return
     * @throws ValueFormatException
     * @throws RepositoryException in case of JCR-related errors
     */
    public Value afterGetValue(final JCRPropertyWrapper property, Value storedValue) throws ValueFormatException, RepositoryException {
        String content = storedValue.getString();
        if (content == null || !content.contains(DOC_CONTEXT_PLACEHOLDER) && !content.contains(CMS_CONTEXT_PLACEHOLDER)) {
            return storedValue;
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Intercept getValue for " + property.getPath());
        }

        final Map<Long, String> refs = new HashMap<Long, String>();

        final ExtendedPropertyDefinition definition = (ExtendedPropertyDefinition) property.getDefinition();
        String name = definition.getName();
        JCRNodeWrapper parent = property.getParent();
        if (definition.isInternationalized()) {
            name += "_" + property.getLocale();
        }

        if (parent.isNodeType(Constants.JAHIANT_TRANSLATION)) {
            parent = parent.getParent();
        }
        if (parent.isNodeType(JAHIAMIX_REFERENCES_IN_FIELD)) {
            NodeIterator ni = parent.getNodes(JAHIA_REFERENCE_IN_FIELD_PREFIX);
            while (ni.hasNext()) {
                JCRNodeWrapper ref = (JCRNodeWrapper) ni.next();
                if (name.equals(ref.getProperty("j:fieldName").getString()) && ref.hasProperty("j:reference")) {
                    try {
                        refs.put(Long.valueOf(StringUtils.substringAfterLast(ref.getName(), "_")), ref.getProperty("j:reference").getString());
                    } catch (PathNotFoundException e) {
                        logger.warn(
                                "Unable to get j:reference field on the node {}. Skipping reference.",
                                ref.getPath());
                    }
                }
            }
        }

        String result;
        try {
            result = urlTraverser.traverse(content, new HtmlTagAttributeVisitor() {
                public String visit(String value, RenderContext context, String tagName, String attrName, Resource resource) {
                    if (StringUtils.isNotEmpty(value)) {
                        try {
                            value = replacePlaceholdersByRefs(value, refs, property.getSession().getWorkspace().getName(), property.getSession().getLocale(), property.getParent());
                            if ("#".equals(value) && attrName.toLowerCase().equals("src") && tagName.toLowerCase().equals("img")) {
                                value = "/missing-image.png";
                            }
                        } catch (RepositoryException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return value;
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof RepositoryException) {
                throw (RepositoryException) e.getCause();
            } else {
                throw e;
            }
        }

        if (!result.equals(content)) {
            return property.getSession().getValueFactory().createValue(result);
        }
        return storedValue;
    }

    /**
     * Called after getting the value. Stored value is passed to the interceptor and can be transformed.
     *
     * @param property
     * @param storedValues
     * @return
     */
    public Value[] afterGetValues(JCRPropertyWrapper property, Value[] storedValues) throws ValueFormatException, RepositoryException {
        Value[] res = new Value[storedValues.length];

        for (int i = 0; i < storedValues.length; i++) {
            Value storedValue = storedValues[i];
            res[i] = afterGetValue(property, storedValue);
        }
        return res;
    }

    private String replaceRefsByPlaceholders(final String originalValue, final Map<String, Long> newRefs, final Map<String, Long> oldRefs, String workspace, final Locale locale, final JCRNodeWrapper node, final ExtendedPropertyDefinition definition) throws RepositoryException {

        if (logger.isDebugEnabled()) {
            logger.debug("Before replaceRefsByPlaceholders : " + originalValue);
        }

        String pathPart = originalValue;
        final boolean isCmsContext;
        if (pathPart.startsWith(dmsContext)) {
            // Remove DOC context part
            pathPart = StringUtils.substringAfter(StringUtils.substringAfter(pathPart, dmsContext), "/");
            isCmsContext = false;
        } else if (pathPart.startsWith(cmsContext)) {
            // Remove CMS context part
            Matcher m = cmsPattern.matcher(pathPart);
            if (!m.matches()) {
                throw new PropertyConstraintViolationException(node, Messages.getInternal("label.error.invalidlink", LocaleContextHolder.getLocale(), "Invalid link") + pathPart, definition.isInternationalized() ? locale : null,definition);
            }
            pathPart = m.group(5);
            isCmsContext = true;
        } else {
            return originalValue;
        }

        final String path = "/" + WebUtils.urlDecode(pathPart);

        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspace, null, new JCRCallback<String>() {
            public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
                String value = originalValue;
                String ext = null;
                String tpl = null;
                JCRNodeWrapper reference;
                try {
                    String currentPath = path;
                    if (isCmsContext) {
                        while (true) {
                            int i = currentPath.lastIndexOf('.');
                            if (i > currentPath.lastIndexOf('/')) {
                                if (ext == null) {
                                    ext = currentPath.substring(i + 1);
                                } else if (tpl == null) {
                                    tpl = currentPath.substring(i + 1);
                                } else {
                                    tpl = currentPath.substring(i + 1) + "." + tpl;
                                }
                                currentPath = currentPath.substring(0, i);
                            } else {
                                throw new PathNotFoundException("not found in " + path);
                            }
                            try {
                                reference = session.getNode(JCRContentUtils.escapeNodePath(currentPath));
                                break;
                            } catch (PathNotFoundException e) {
                                // continue
                            }
                        }
                        value = CMS_CONTEXT_PLACEHOLDER + StringUtils.substringAfter(value, cmsContext);
                    } else {
                        // retrieve path
                        while (true) {
                            if (StringUtils.contains(currentPath, '/')) {
                                currentPath = StringUtils.substringAfter(currentPath, "/");
                            } else {
                                throw new PathNotFoundException("not found in " + path);
                            }
                            try {
                                reference = session.getNode(JCRContentUtils.escapeNodePath("/" + currentPath));
                                break;
                            } catch (PathNotFoundException e) {
                                // continue
                            }
                        }
                        value = DOC_CONTEXT_PLACEHOLDER + StringUtils.substringAfter(value, dmsContext);
                    }
                } catch (PathNotFoundException e) {
                    throw new PropertyConstraintViolationException(node, Messages.getInternal("label.error.invalidlink", LocaleContextHolder.getLocale(), "Invalid link") + path, definition.isInternationalized() ? locale : null, definition);
                }
                String id = reference.getIdentifier();
                if (!newRefs.containsKey(id)) {
                    if (oldRefs.containsKey(id)) {
                        newRefs.put(id, oldRefs.get(id));
                    } else {
                        Long max = Math.max(oldRefs.isEmpty() ? 0 : Collections.max(oldRefs.values()), newRefs.isEmpty() ? 0 : Collections.max(newRefs.values()));
                        newRefs.put(id, max + 1);
                    }
                }
                Long index = newRefs.get(id);
                String link = "/##ref:link" + index + "##";
                if (tpl != null) {
                    link += "." + tpl;
                }
                if (ext != null) {
                    link += "." + ext;
                }
                value = WebUtils.urlDecode(value).replace(path, link);
                if (logger.isDebugEnabled()) {
                    logger.debug("After replaceRefsByPlaceholders : " + value);
                }
                return value;
            }
        });
    }


    private String replacePlaceholdersByRefs(final String originalValue, final Map<Long, String> refs, final String workspaceName, final Locale locale, final JCRNodeWrapper parent) throws RepositoryException {

        String pathPart = originalValue;
        if (logger.isDebugEnabled()) {
            logger.debug("Before replacePlaceholdersByRefs : " + originalValue);
        }
        final boolean isCmsContext;

        if (pathPart.startsWith(DOC_CONTEXT_PLACEHOLDER)) {
            // Remove DOC context part
            pathPart = StringUtils.substringAfter(StringUtils.substringAfter(pathPart, DOC_CONTEXT_PLACEHOLDER), "/");
            isCmsContext = false;
        } else if (pathPart.startsWith(CMS_CONTEXT_PLACEHOLDER)) {
            // Remove CMS context part
            Matcher m = cmsPatternWithContextPlaceholder.matcher(pathPart);
            if (!m.matches()) {
                logger.error("Cannot match URL : " + pathPart);
                return originalValue;
            }
            pathPart = m.group(5);
            isCmsContext = true;
        } else {
            return originalValue;
        }

        final String path = "/" + pathPart;
        return JCRTemplate.getInstance().doExecuteWithSystemSessionAsUser(null, workspaceName, null, new JCRCallback<String>() {
            public String doInJCR(JCRSessionWrapper session) throws RepositoryException {
                String value = originalValue;
                try {
                    Matcher matcher = refPattern.matcher(path);
                    if (!matcher.matches()) {
                        logger.error("Cannot match value, should contain ##ref : " + path);
                        return originalValue;
                    }
                    String id = matcher.group(1);
                    String ext = matcher.group(2);
                    String uuid = refs.get(new Long(id));
                    String nodePath = null;

                    JCRNodeWrapper node = null;
                    if (!StringUtils.isEmpty(uuid)) {
                        try {
                            node = session.getNodeByUUID(uuid);
                        } catch (ItemNotFoundException infe) {
                            // Warning is logged below (also if uuid is empty)
                        }
                    }
                    if (node == null) {
                        logger.warn("Cannot find referenced item : " + parent.getPath() + " -> " + path + " -> " + uuid);
                        return "#";
                    }
                    nodePath = Text.escapePath(node.getPath());
                    value = originalValue.replace(path, nodePath + ext);
                    if (isCmsContext) {
                        value = CMS_CONTEXT_PLACEHOLDER_PATTERN.matcher(value).replaceAll(cmsContext);
                    } else {
                        value = DOC_CONTEXT_PLACEHOLDER_PATTERN.matcher(value).replaceAll(dmsContext);
                    }
                    if (logger.isDebugEnabled()) {
                        logger.debug("After replacePlaceholdersByRefs : " + value);
                    }
                } catch (Exception e) {
                    logger.error("Exception when transforming placeholder for " + parent.getPath() + " -> " + path, e);
                }
                return value;
            }
        });
    }

    public void afterPropertiesSet() throws Exception {
        dmsContext = Jahia.getContextPath() + "/files/";
        cmsContext = Jahia.getContextPath() + "/cms/";

        String pattern = "(((render|edit|live|contribute)/[a-zA-Z]+)|" +
                escape(ContextPlaceholdersReplacer.CURRENT_CONTEXT_PLACEHOLDER) + ")/([a-zA-Z_]+|" +
                escape(ContextPlaceholdersReplacer.LANG_PLACEHOLDER) + ")/(.*)";

        refPattern = Pattern.compile("/##ref:link([0-9]+)##(.*)");
        cmsPattern = Pattern.compile(cmsContext + pattern);
        cmsPatternWithContextPlaceholder = Pattern.compile(escape(CMS_CONTEXT_PLACEHOLDER) + pattern);
    }


}
