/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *     Copyright (C) 2002-2015 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     "This program is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation; either version 2
 *     of the License, or (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *     As a special exception to the terms and conditions of version 2.0 of
 *     the GPL (or any later version), you may redistribute this Program in connection
 *     with Free/Libre and Open Source Software ("FLOSS") applications as described
 *     in Jahia's FLOSS exception. You should have received a copy of the text
 *     describing the FLOSS exception, also available here:
 *     http://www.jahia.com/license"
 *
 *     2/ JSEL - Commercial and Supported Versions of the program
 *     ======================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE JSEL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
 *
 *     Alternatively, commercial and supported versions of the program - also known as
 *     Enterprise Distributions - must be used in accordance with the terms and conditions
 *     contained in a separate written agreement between you and Jahia Solutions Group SA.
 *
 *     If you are unsure which license is appropriate for your use,
 *     please contact the sales department at sales@jahia.com.
 *
 *
 * ==========================================================================================
 * =                                   ABOUT JAHIA                                          =
 * ==========================================================================================
 *
 *     Rooted in Open Source CMS, Jahia’s Digital Industrialization paradigm is about
 *     streamlining Enterprise digital projects across channels to truly control
 *     time-to-market and TCO, project after project.
 *     Putting an end to “the Tunnel effect”, the Jahia Studio enables IT and
 *     marketing teams to collaboratively and iteratively build cutting-edge
 *     online business solutions.
 *     These, in turn, are securely and easily deployed as modules and apps,
 *     reusable across any digital projects, thanks to the Jahia Private App Store Software.
 *     Each solution provided by Jahia stems from this overarching vision:
 *     Digital Factory, Workspace Factory, Portal Factory and eCommerce Factory.
 *     Founded in 2002 and headquartered in Geneva, Switzerland,
 *     Jahia Solutions Group has its North American headquarters in Washington DC,
 *     with offices in Chicago, Toronto and throughout Europe.
 *     Jahia counts hundreds of global brands and governmental organizations
 *     among its loyal customers, in more than 20 countries across the globe.
 *
 *     For more information, please visit http://www.jahia.com
 */
package org.jahia.ajax.gwt.helper;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.value.StringValue;
import org.apache.tika.io.IOUtils;
import org.jahia.ajax.gwt.client.data.definition.GWTJahiaNodeProperty;
import org.jahia.ajax.gwt.client.data.definition.GWTJahiaNodePropertyType;
import org.jahia.ajax.gwt.client.data.definition.GWTJahiaNodePropertyValue;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.service.GWTCompositeConstraintViolationException;
import org.jahia.ajax.gwt.client.service.GWTJahiaServiceException;
import org.jahia.ajax.gwt.content.server.GWTFileManagerUploadServlet;
import org.jahia.api.Constants;
import org.jahia.exceptions.JahiaException;
import org.jahia.services.categories.Category;
import org.jahia.services.content.*;
import org.jahia.services.content.nodetypes.ExtendedItemDefinition;
import org.jahia.services.content.nodetypes.ExtendedNodeDefinition;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.utils.EncryptionUtils;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.i18n.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.i18n.LocaleContextHolder;

import javax.jcr.*;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NodeDefinition;
import javax.jcr.nodetype.PropertyDefinition;
import java.io.InputStream;
import java.util.*;

/**
 * Helper class for setting node properties based on GWT bean values.
 * User: toto
 * Date: Sep 28, 2009
 * Time: 2:45:42 PM
 */
public class PropertiesHelper {
    private static Logger logger = LoggerFactory.getLogger(PropertiesHelper.class);

    private ContentDefinitionHelper contentDefinition;
    private NavigationHelper navigation;

    private Set<String> ignoredProperties = Collections.emptySet();

    public void setContentDefinition(ContentDefinitionHelper contentDefinition) {
        this.contentDefinition = contentDefinition;
    }

    public void setNavigation(NavigationHelper navigation) {
        this.navigation = navigation;
    }

    public Map<String, GWTJahiaNodeProperty> getProperties(String path, JCRSessionWrapper currentUserSession, Locale uiLocale) throws GWTJahiaServiceException {
        JCRNodeWrapper objectNode;
        try {
            objectNode = currentUserSession.getNode(path);
        } catch (RepositoryException e) {
            logger.error(e.toString(), e);
            throw new GWTJahiaServiceException(new StringBuilder(path).append(Messages.getInternal("label.gwt.error.could.not.be.accessed", uiLocale)).append(e.toString()).toString());
        }
        Map<String, GWTJahiaNodeProperty> props = new HashMap<String, GWTJahiaNodeProperty>();
        String propName = "null";
        try {
            PropertyIterator it = objectNode.getProperties();
            while (it.hasNext()) {
                Property prop = it.nextProperty();
                PropertyDefinition def = prop.getDefinition();
                // definition can be null if the file is versionned
                if (def != null && !ignoredProperties.contains(def.getName()) && ((ExtendedPropertyDefinition) def).getSelectorOptions().get("password") == null) {
                    propName = def.getName();

                    // check that we're not dealing with a not-set property from the translation nodes,
                    // in which case it needs to be omitted
                    final Locale locale = currentUserSession.getLocale();
                    if(Constants.nonI18nPropertiesCopiedToTranslationNodes.contains(propName) && objectNode.hasI18N(locale, false)) {
                        // get the translation node for the current locale
                        final Node i18N = objectNode.getI18N(locale, false);
                        if(!i18N.hasProperty(propName)) {
                            // if the translation node doesn't have the property and it's part of the set of copied properties, then we shouldn't return it
                            continue;
                        }
                    }


                    // create the corresponding GWT bean
                    GWTJahiaNodeProperty nodeProp = new GWTJahiaNodeProperty();
                    nodeProp.setName(propName);
                    nodeProp.setMultiple(def.isMultiple());
                    Value[] values;
                    if (!def.isMultiple()) {
                        Value oneValue = prop.getValue();
                        values = new Value[]{oneValue};
                    } else {
                        values = prop.getValues();
                    }
                    List<GWTJahiaNodePropertyValue> gwtValues = new ArrayList<GWTJahiaNodePropertyValue>(values.length);

                    for (Value val : values) {
                        GWTJahiaNodePropertyValue convertedValue = contentDefinition.convertValue(val, (ExtendedPropertyDefinition) def);
                        if (convertedValue != null) {
                            gwtValues.add(convertedValue);
                        }
                    }
                    nodeProp.setValues(gwtValues);
                    props.put(nodeProp.getName(), nodeProp);
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("The following property has been ignored " + prop.getName() + "," + prop.getPath());
                    }
                }
            }
            NodeIterator ni = objectNode.getNodes();
            while (ni.hasNext()) {
                Node node = ni.nextNode();
                if (node.isNodeType(Constants.NT_RESOURCE)) {
                    NodeDefinition def = node.getDefinition();
                    propName = def.getName();
                    // create the corresponding GWT bean
                    GWTJahiaNodeProperty nodeProp = new GWTJahiaNodeProperty();
                    nodeProp.setName(propName);
                    List<GWTJahiaNodePropertyValue> gwtValues = new ArrayList<GWTJahiaNodePropertyValue>();
                    gwtValues.add(new GWTJahiaNodePropertyValue(node.getProperty(Constants.JCR_MIMETYPE).getString(), GWTJahiaNodePropertyType.ASYNC_UPLOAD));
                    nodeProp.setValues(gwtValues);
                    props.put(nodeProp.getName(), nodeProp);
                } else if (node.isNodeType(Constants.JAHIANT_PAGE_LINK)) {

                    // case of link
                    NodeDefinition def = node.getDefinition();
                    propName = def.getName();
                    // create the corresponding GWT bean
                    GWTJahiaNodeProperty nodeProp = new GWTJahiaNodeProperty();
                    nodeProp.setName(propName);
                    List<GWTJahiaNodePropertyValue> gwtValues = new ArrayList<GWTJahiaNodePropertyValue>();
                    GWTJahiaNode linkNode = navigation.getGWTJahiaNode((JCRNodeWrapper) node);
                    if (node.isNodeType(Constants.JAHIANT_NODE_LINK)) {
                        linkNode.set("linkType", "internal");
                    } else if (node.isNodeType(Constants.JAHIANT_EXTERNAL_PAGE_LINK)) {
                        linkNode.set("linkType", "external");
                    }

                    // url
                    if (node.hasProperty(Constants.URL)) {
                        String linkUrl = node.getProperty(Constants.URL).getValue().getString();
                        linkNode.set(Constants.URL, linkUrl);
                    }

                    // title
                    if (node.hasProperty(Constants.JCR_TITLE)) {
                        String linkTitle = node.getProperty(Constants.JCR_TITLE).getValue().getString();
                        linkNode.set(Constants.JCR_TITLE, linkTitle);
                    }

                    // alt
                    if (node.hasProperty(Constants.ALT)) {
                        String alt = node.getProperty(Constants.ALT).getValue().getString();
                        linkNode.set(Constants.ALT, alt);
                    }

                    if (node.hasProperty(Constants.NODE)) {
                        JCRValueWrapper weekReference = (JCRValueWrapper) node.getProperty(Constants.NODE).getValue();
                        Node pageNode = weekReference.getNode();
                        if (pageNode != null) {
                            linkNode.set(Constants.NODE, navigation.getGWTJahiaNode((JCRNodeWrapper) pageNode));
                            linkNode.set(Constants.ALT, pageNode.getName());
                            linkNode.set(Constants.URL, ((JCRNodeWrapper) pageNode).getUrl());
                            linkNode.set(Constants.JCR_TITLE, ((JCRNodeWrapper) pageNode).getUrl());
                        } else {
                            String resource = Messages.getInternal("label.error.invalidlink", uiLocale);
                            linkNode.set(Constants.JCR_TITLE, resource);
                            linkNode.set(Constants.ALT, resource);
                        }

                    }


                    GWTJahiaNodePropertyValue proper = new GWTJahiaNodePropertyValue(linkNode, GWTJahiaNodePropertyType.PAGE_LINK);
                    gwtValues.add(proper);
                    nodeProp.setValues(gwtValues);
                    props.put(nodeProp.getName(), nodeProp);
                }
            }
        } catch (RepositoryException e) {
            logger.error("Cannot access property " + propName + " of node " + objectNode.getName(), e);
        }
        return props;
    }

    /**
     * A batch-capable save properties method.
     *
     * @param nodes              the nodes to save the properties of
     * @param newProps           the new properties
     * @param removedTypes
     * @param currentUserSession @throws org.jahia.ajax.gwt.client.service.GWTJahiaServiceException
     * @param uiLocale
     */
    public void saveProperties(List<GWTJahiaNode> nodes, List<GWTJahiaNodeProperty> newProps, Set<String> removedTypes, JCRSessionWrapper currentUserSession, Locale uiLocale) throws RepositoryException {
        for (GWTJahiaNode aNode : nodes) {
            JCRNodeWrapper objectNode = currentUserSession.getNode(aNode.getPath());
            List<String> types = aNode.getNodeTypes();
            if (removedTypes != null && !removedTypes.isEmpty()) {
                for (ExtendedNodeType mixin : objectNode.getMixinNodeTypes()) {
                    if (removedTypes.contains(mixin.getName())) {
                        List<ExtendedItemDefinition> items = mixin.getItems();
                        for (ExtendedItemDefinition item : items) {
                            removeItemFromNode(item, objectNode, currentUserSession);
                        }
                        objectNode.removeMixin(mixin.getName());
                    }
                }
                for (ExtendedNodeType mixin : objectNode.getPrimaryNodeType().getDeclaredSupertypes()) {
                    if (removedTypes.contains(mixin.getName())) {
                        List<ExtendedItemDefinition> items = mixin.getItems();
                        for (ExtendedItemDefinition item : items) {
                            removeItemFromNode(item, objectNode, currentUserSession);
                        }
                    }
                }
            }
            for (String type : types) {
                if (!objectNode.isNodeType(type)) {
                    currentUserSession.checkout(objectNode);
                    objectNode.addMixin(type);
                }
            }
            setProperties(objectNode, newProps);
        }
    }

    public void saveWorkInProgress(List<GWTJahiaNode> nodes, Map<String, List<GWTJahiaNodeProperty>> langProperties, JCRSessionWrapper currentUserSession) throws RepositoryException {
        for (GWTJahiaNode aNode : nodes) {
            JCRNodeWrapper currentNode = currentUserSession.getNode(aNode.getPath());
            saveWorkInProgress(currentNode, langProperties);
        }
    }

    public void saveWorkInProgress(JCRNodeWrapper node, Map<String, List<GWTJahiaNodeProperty>> langProperties) throws RepositoryException {
        node.checkLock();
        if (node.hasTranslations()) {
            for (String language : langProperties.keySet()) {
                boolean isWipLanguage = false;
                for (GWTJahiaNodeProperty prop : langProperties.get(language)) {
                    isWipLanguage |= StringUtils.equals(prop.getName(), Constants.WORKINPROGRESS) && prop.getValues().get(0).getBoolean();
                }
                if (node.hasI18N(LanguageCodeConverters.languageCodeToLocale(language))) {
                    Node i18N = node.getI18N(LanguageCodeConverters.languageCodeToLocale(language));
                    if (isWipLanguage) {
                        if (node.hasProperty(Constants.WORKINPROGRESS)) {
                            node.getProperty(Constants.WORKINPROGRESS).remove();
                        }
                        i18N.setProperty(Constants.WORKINPROGRESS, true);
                    } else if (i18N.hasProperty(Constants.WORKINPROGRESS)) {
                        i18N.getProperty(Constants.WORKINPROGRESS).remove();
                    }
                }
            }
        } else {
            boolean isWipLanguage = false;
            for (String language : langProperties.keySet()) {
                for (GWTJahiaNodeProperty prop : langProperties.get(language)) {
                    isWipLanguage |= StringUtils.equals(prop.getName(), Constants.WORKINPROGRESS) && prop.getValues().get(0).getBoolean();
                }
            }
            if (isWipLanguage) {
                node.setProperty(Constants.WORKINPROGRESS, true);
            } else if (node.hasProperty(Constants.WORKINPROGRESS)) {
                node.getProperty(Constants.WORKINPROGRESS).remove();
            }
        }
    }



    private void removeItemFromNode(ExtendedItemDefinition item, JCRNodeWrapper objectNode, JCRSessionWrapper currentUserSession) throws RepositoryException {
        if (item.isNode()) {
            if (objectNode.hasNode(item.getName())) {
                currentUserSession.checkout(objectNode);
                objectNode.getNode(item.getName()).remove();
            }
        } else {
            if (item instanceof ExtendedPropertyDefinition){
                ExtendedPropertyDefinition itemProperty = (ExtendedPropertyDefinition) item;
                if(itemProperty.isInternationalized()){
                    NodeIterator nodeIterator = objectNode.getI18Ns();
                    while (nodeIterator.hasNext()){
                        Node i18nNode = nodeIterator.nextNode();
                        if (i18nNode.hasProperty(item.getName())) {
                            currentUserSession.checkout(i18nNode);
                            i18nNode.getProperty(item.getName()).remove();
                        }
                    }
                } else {
                    if (objectNode.hasProperty(item.getName())) {
                        currentUserSession.checkout(objectNode);
                        objectNode.getProperty(item.getName()).remove();
                    }
                }
            }
        }
    }

    public void setProperties(JCRNodeWrapper objectNode, List<GWTJahiaNodeProperty> newProps) throws RepositoryException {
        if (objectNode == null || newProps == null || newProps.isEmpty()) {
            logger.debug("node or properties are null or empty");
            return;
        }
        if (!objectNode.isCheckedOut()) {
            objectNode.checkout();
        }

        GWTJahiaNodeProperty wipProp = null;
        for (GWTJahiaNodeProperty prop : newProps) {
            try {
                if (prop != null &&
                        !prop.getName().equals("*") &&
                        !Constants.forbiddenPropertiesToCopy.contains(prop.getName()) &&
                        !StringUtils.equals(prop.getName(), Constants.WORKINPROGRESS)
                        ) {
                    if (prop.isMultiple()) {
                        List<Value> values = new ArrayList<Value>();
                        for (GWTJahiaNodePropertyValue val : prop.getValues()) {
                            if (val.getString() != null) {
                                values.add(contentDefinition.convertValue(val));
                            }
                        }
                        Value[] finalValues = new Value[values.size()];
                        values.toArray(finalValues);
                        objectNode.setProperty(prop.getName(), finalValues);
                    } else {
                        if (prop.getValues().size() > 0) {
                            GWTJahiaNodePropertyValue propValue = prop.getValues().get(0);
                            if (propValue.getType() == GWTJahiaNodePropertyType.ASYNC_UPLOAD) {
                                GWTFileManagerUploadServlet.Item fileItem = GWTFileManagerUploadServlet.getItem(propValue.getString());
                                boolean clear = propValue.getString().equals("clear");
                                if (!clear && fileItem == null) {
                                    continue;
                                }
                                ExtendedNodeDefinition end = ((ExtendedNodeType) objectNode.getPrimaryNodeType()).getChildNodeDefinitionsAsMap().get(prop.getName());

                                if (end != null) {
                                    try {
                                        if (!clear) {
                                            Node content;
                                            String s = end.getRequiredPrimaryTypeNames()[0];
                                            if (objectNode.hasNode(prop.getName())) {
                                                content = objectNode.getNode(prop.getName());
                                            } else {
                                                content = objectNode.addNode(prop.getName(), s.equals("nt:base") ? "jnt:resource" : s);
                                            }
                                            content.setProperty(Constants.JCR_MIMETYPE, fileItem.getContentType());
                                            InputStream is = fileItem.getStream();
                                            try {
                                                content.setProperty(Constants.JCR_DATA, is);
                                            } finally {
                                                IOUtils.closeQuietly(is);
                                                fileItem.dispose();
                                            }
                                            content.setProperty(Constants.JCR_LASTMODIFIED, new GregorianCalendar());
                                        } else {
                                            if (objectNode.hasNode(prop.getName())) {
                                                objectNode.getNode(prop.getName()).remove();
                                            }
                                        }
                                    } catch (Exception e) {
                                        logger.error(e.getMessage(), e);
                                    }
                                }
                            } else if (propValue.getType() == GWTJahiaNodePropertyType.PAGE_LINK) {
                                if (objectNode.hasNode(prop.getName())) {
                                    Node content = objectNode.getNodes(prop.getName()).nextNode();
                                    content.remove();
                                }

                                // case of link sub-node
                                GWTJahiaNode gwtJahiaNode = propValue.getLinkNode();
                                String linkUrl = gwtJahiaNode.get(Constants.URL);
                                String linkTitle = gwtJahiaNode.get(Constants.JCR_TITLE);
                                String alt = gwtJahiaNode.get(Constants.ALT);
                                String linkType = gwtJahiaNode.get("linkType");
                                GWTJahiaNode nodeReference = gwtJahiaNode.get(Constants.NODE);

                                // case of external
                                if (linkType.equalsIgnoreCase("external") && linkUrl != null) {
                                    Node content = objectNode.addNode(prop.getName(), Constants.JAHIANT_EXTERNAL_PAGE_LINK);
                                    content.setProperty(Constants.JCR_TITLE, linkTitle);
                                    content.setProperty(Constants.URL, linkUrl);
                                    content.setProperty(Constants.ALT, alt);
                                    content.setProperty(Constants.JCR_LASTMODIFIED, new GregorianCalendar());
                                }
                                // case of internal link
                                else if (linkType.equalsIgnoreCase("internal") && nodeReference != null) {
                                    Node content = objectNode.addNode(prop.getName(), Constants.JAHIANT_NODE_LINK);
                                    content.setProperty(Constants.JCR_TITLE, linkTitle);
                                    content.setProperty(Constants.NODE, nodeReference.getUUID());
                                    content.setProperty(Constants.JCR_LASTMODIFIED, new GregorianCalendar());
                                }
                            } else {
                                ExtendedPropertyDefinition epd = objectNode.getPrimaryNodeType().getPropertyDefinitionsAsMap().get(prop.getName());
                                if (epd != null && epd.getSelectorOptions().containsKey("password")) {
                                    if (propValue != null && propValue.getString() != null) {
                                        String enc = encryptPassword(propValue.getString());
                                        Value value = new StringValue(enc);
                                        objectNode.setProperty(prop.getName(), value);
                                    }
                                } else if (propValue != null && propValue.getString() != null) {
                                    Value value = contentDefinition.convertValue(propValue);
                                    objectNode.setProperty(prop.getName(), value);
                                } else {
                                    if (objectNode.hasProperty(prop.getName())) {
                                        objectNode.getProperty(prop.getName()).remove();
                                    }
                                }
                            }
                        } else if (objectNode.hasProperty(prop.getName())) {
                            objectNode.getProperty(prop.getName()).remove();
                        }
                    }

                } else if (StringUtils.equals(prop.getName(), Constants.WORKINPROGRESS)) {
                    // do not set wip property here, as we are in a loop if wip property is the first the i18n nodes are may be not created yet
                    wipProp = prop;
                }
            } catch (PathNotFoundException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Property with the name '"
                            + prop.getName() + "' not found on the node "
                            + objectNode.getPath() + ". Skipping.", e);
                } else {
                    logger.info("Property with the name '" + prop.getName()
                            + "' not found on the node "
                            + objectNode.getPath() + ". Skipping.");
                }
            }
        }

        // set wip property if necessary
        try {
            if(wipProp != null) {
                Node n;
                objectNode.checkLock();
                boolean wip = wipProp.getValues().get(0).getBoolean();
                Locale locale = objectNode.getSession().getLocale();
                if (locale == null || !objectNode.hasI18N(locale)) {
                    n = objectNode;
                } else {
                    n = objectNode.getI18N(locale);
                }
                if (!wip && n.hasProperty(Constants.WORKINPROGRESS)) {
                    n.getProperty(Constants.WORKINPROGRESS).remove();
                } else if (wip) {
                    n.setProperty(Constants.WORKINPROGRESS, wip);
                }
            }
        } catch (PathNotFoundException e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Property with the name '"
                        + wipProp.getName() + "' not found on the node "
                        + objectNode.getPath() + ". Skipping.", e);
            } else {
                logger.info("Property with the name '" + wipProp.getName()
                        + "' not found on the node "
                        + objectNode.getPath() + ". Skipping.");
            }
        }
    }

    public List<Value> getCategoryPathValues(String value) {
        if (value == null || value.length() == 0) {
            return Collections.emptyList();
        }
        List<Value> values = new LinkedList<Value>();
        String[] categories = StringUtils.split(value, ",");
        for (String categoryKey : categories) {
            try {
                values.add(new StringValue(Category.getCategoryPath(categoryKey.trim())));
            } catch (JahiaException e) {
                logger.warn("Unable to retrieve category path for category key '" + categoryKey + "'. Cause: " + e.getMessage(), e);
            }
        }
        return values;
    }

    public void setIgnoredProperties(Set<String> ignoredProperties) {
        if (ignoredProperties != null) {
            this.ignoredProperties = ignoredProperties;
        } else {
            this.ignoredProperties = Collections.emptySet();
        }
    }

    public String encryptPassword(String pwd) {
        return StringUtils.isNotEmpty(pwd) ? EncryptionUtils.passwordBaseEncrypt(pwd) : StringUtils.EMPTY;
    }

    public void convertException(NodeConstraintViolationException violationException) throws GWTJahiaServiceException {
        GWTCompositeConstraintViolationException gwt = new GWTCompositeConstraintViolationException(violationException.getMessage());
        addConvertedException(violationException, gwt);
        throw gwt;
    }

    public void convertException(CompositeConstraintViolationException e) throws GWTJahiaServiceException {
        GWTCompositeConstraintViolationException gwt = new GWTCompositeConstraintViolationException(e.getMessage());
        for (ConstraintViolationException violationException : e.getErrors()) {
            if (violationException instanceof NodeConstraintViolationException) {
                addConvertedException((NodeConstraintViolationException) violationException, gwt);
            }
        }
        throw gwt;
    }

    private void addConvertedException(NodeConstraintViolationException violationException, GWTCompositeConstraintViolationException gwt) throws GWTJahiaServiceException {
        if (violationException instanceof PropertyConstraintViolationException) {
            PropertyConstraintViolationException v = (PropertyConstraintViolationException) violationException;
            gwt.addError(v.getPath(), v.getConstraintMessage(), v.getLocale() != null ? v.getLocale().toString() : null, v.getDefinition().getName(), v.getDefinition().getLabel(LocaleContextHolder.getLocale(), v.getDefinition().getDeclaringNodeType()));
        } else {
            NodeConstraintViolationException v = violationException;
            gwt.addError(v.getPath(), v.getConstraintMessage(), v.getLocale() != null ? v.getLocale().toString() : null, null, null);
        }
    }


}
