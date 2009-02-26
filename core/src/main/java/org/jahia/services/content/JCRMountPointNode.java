/**
 * 
 * This file is part of Jahia: An integrated WCM, DMS and Portal Solution
 * Copyright (C) 2002-2009 Jahia Limited. All rights reserved.
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
 * in Jahia's FLOSS exception. You should have recieved a copy of the text
 * describing the FLOSS exception, and it is also available here:
 * http://www.jahia.com/license"
 * 
 * Commercial and Supported Versions of the program
 * Alternatively, commercial and supported versions of the program may be used
 * in accordance with the terms contained in a separate written agreement
 * between you and Jahia Limited. If you are unsure which license is appropriate
 * for your use, please contact the sales department at sales@jahia.com.
 */

package org.jahia.services.content;

import org.jahia.services.content.impl.vfs.VFSContentStoreProvider;

import javax.jcr.*;
import javax.jcr.version.VersionException;
import javax.jcr.lock.LockException;
import javax.jcr.nodetype.ConstraintViolationException;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Created by IntelliJ IDEA.
 * User: toto
 * Date: Dec 8, 2008
 * Time: 2:19:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class JCRMountPointNode extends JCRNodeDecorator {
    public JCRMountPointNode(JCRNodeWrapper node) {
        super(node);

    }

    public boolean checkValidity() {
        try {
            getRootNode();
            return true;
        } catch (RepositoryException e) {
            getProvider().getService().getDynamicMountPoints().remove(getPath());
            return false;
        }
    }

    public List<JCRNodeWrapper> getChildren() {
        try {
            return getRootNode().getChildren();
        } catch (RepositoryException e) {
            e.printStackTrace();
        }
        return new ArrayList<JCRNodeWrapper>();
    }

    public JCRNodeWrapper addNode(String name) throws RepositoryException {
        return getRootNode().addNode(name);
    }

    public JCRNodeWrapper addNode(String name, String type) throws RepositoryException {
        return getRootNode().addNode(name, type);
    }

    private JCRNodeWrapper getRootNode() throws RepositoryException {
        JCRStoreProvider provider = null;
        if (!getProvider().getService().getDynamicMountPoints().containsKey(getPath())) {
            if (isNodeType("jnt:vfsMountPoint")) {
                Map<String, Object> m = new HashMap<String, Object>();
                m.put("root",getProperty("j:root").getString());
                provider = getProvider().getService().mount(VFSContentStoreProvider.class, getPath(), getUUID(), m);
            }
        } else {
            provider = getProvider().getService().getDynamicMountPoints().get(getPath());
        }

        if (provider != null) {
            return provider.getNodeWrapper("/", getUser());
        }
        return null;
    }

    public void remove() throws VersionException, LockException, ConstraintViolationException, RepositoryException {
        getProvider().getService().unmount(getProvider());
        super.remove();
    }
}
