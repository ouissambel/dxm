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
package org.jahia.test.services.content.decorator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import javax.jcr.NodeIterator;
import javax.jcr.PathNotFoundException;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;

import org.jahia.services.content.JCRCallback;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRPasswordHistoryEntryNode;
import org.junit.Test;

/**
 * Test of the {@link JCRPasswordHistoryEntryNode} decorator.
 * 
 * @author Sergiy Shyrkov
 */
public class PasswordHistoryEntryDecoratorTest {

    private static final String PROP = "j:password";

    @Test
    public void testSystemSession() throws RepositoryException {
        JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Boolean>() {
            public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                for (NodeIterator iterator = session.getNode("/users/root/passwordHistory").getNodes(); iterator
                        .hasNext();) {
                    JCRNodeWrapper child = (JCRNodeWrapper) iterator.nextNode();
                    if (child.isNodeType("jnt:passwordHistoryEntry")) {
                        assertTrue("Cannot find j:password property", child.hasProperty(PROP));
                        assertNotNull("Cannot find j:password property", child.getProperty(PROP));
                        assertNotNull("Cannot find j:password property", child.getPropertyAsString(PROP));
                        assertTrue("Cannot find j:password property", child.getPropertiesAsString().containsKey(PROP));
                        boolean found = false;
                        for (PropertyIterator propIterator = child.getProperties(); propIterator.hasNext();) {
                            if (propIterator.nextProperty().getName().equals(PROP)) {
                                found = true;
                                break;
                            }
                        }
                        assertTrue("Cannot find j:password property", found);
                        break;
                    }
                }
                return Boolean.TRUE;
            }
        });
    }

    @Test
    public void testUserSession() throws RepositoryException {
        JCRTemplate.getInstance().doExecuteWithUserSession("root", "default", new JCRCallback<Boolean>() {
            public Boolean doInJCR(JCRSessionWrapper session) throws RepositoryException {
                for (NodeIterator iterator = session.getNode("/users/root/passwordHistory").getNodes(); iterator
                        .hasNext();) {
                    JCRNodeWrapper child = (JCRNodeWrapper) iterator.nextNode();
                    if (child.isNodeType("jnt:passwordHistoryEntry")) {
                        assertFalse("Found j:password property", child.hasProperty(PROP));
                        PathNotFoundException pnfe = null;
                        try {
                            child.getProperty(PROP);
                        } catch (PathNotFoundException e) {
                            pnfe = e;
                        }
                        assertNotNull("Found j:password property", pnfe);
                        assertNull("Found j:password property", child.getPropertyAsString(PROP));
                        assertFalse("Found j:password property", child.getPropertiesAsString().containsKey(PROP));
                        boolean found = false;
                        for (PropertyIterator propIterator = child.getProperties(); propIterator.hasNext();) {
                            if (propIterator.nextProperty().getName().equals(PROP)) {
                                found = true;
                                break;
                            }
                        }
                        assertFalse("Found j:password property", found);
                        break;
                    }
                }
                return Boolean.TRUE;
            }
        });
    }
}
