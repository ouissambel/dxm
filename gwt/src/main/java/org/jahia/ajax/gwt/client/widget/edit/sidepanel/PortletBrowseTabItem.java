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

package org.jahia.ajax.gwt.client.widget.edit.sidepanel;

import com.allen_sauer.gwt.log.client.Log;
import com.extjs.gxt.ui.client.Style;
import com.extjs.gxt.ui.client.data.*;
import com.extjs.gxt.ui.client.dnd.DND;
import com.extjs.gxt.ui.client.dnd.ListViewDragSource;
import com.extjs.gxt.ui.client.dnd.StatusProxy;
import com.extjs.gxt.ui.client.event.*;
import com.extjs.gxt.ui.client.store.ListStore;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.extjs.gxt.ui.client.widget.ListView;
import com.extjs.gxt.ui.client.widget.TabItem;
import com.extjs.gxt.ui.client.widget.layout.VBoxLayoutData;
import com.google.gwt.user.client.rpc.AsyncCallback;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.data.toolbar.GWTSidePanelTab;
import org.jahia.ajax.gwt.client.messages.Messages;
import org.jahia.ajax.gwt.client.service.content.JahiaContentManagementService;
import org.jahia.ajax.gwt.client.util.content.JCRClientUtils;
import org.jahia.ajax.gwt.client.widget.content.ThumbsListView;
import org.jahia.ajax.gwt.client.widget.edit.EditLinker;
import org.jahia.ajax.gwt.client.widget.edit.EditModeDNDListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Side panel tab item for browsing portlet repository.
 * User: toto
 * Date: Dec 21, 2009
 * Time: 2:22:24 PM
 */
class PortletBrowseTabItem extends BrowseTabItem {
    protected transient LayoutContainer contentContainer;
    protected transient ListLoader<ListLoadResult<GWTJahiaNode>> listLoader;
    protected transient ListStore<GWTJahiaNode> contentStore;
    protected transient ImageDragSource dragSource;

    public TabItem create(GWTSidePanelTab config) {
        super.create(config);

        contentContainer = new LayoutContainer();
        contentContainer.setBorders(true);
        contentContainer.setId("images-view");
        contentContainer.setScrollMode(Style.Scroll.AUTOY);

        // data proxy
        RpcProxy<PagingLoadResult<GWTJahiaNode>> listProxy = new RpcProxy<PagingLoadResult<GWTJahiaNode>>() {
            @Override
            protected void load(Object gwtJahiaFolder, AsyncCallback<PagingLoadResult<GWTJahiaNode>> listAsyncCallback) {
                if (gwtJahiaFolder != null) {
                    Log.debug("retrieving children of " + ((GWTJahiaNode) gwtJahiaFolder).getName());
                    try {
                        JahiaContentManagementService.App.getInstance()
                                .lsLoad((GWTJahiaNode) gwtJahiaFolder, JCRClientUtils.PORTLET_NODETYPES, null, null, Arrays.asList(GWTJahiaNode.ICON, GWTJahiaNode.PUBLICATION_INFO, GWTJahiaNode.THUMBNAILS, GWTJahiaNode.TAGS), false, -1, -1, false, null, null, listAsyncCallback);
                    } catch (org.jahia.ajax.gwt.client.service.GWTJahiaServiceException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                } else {
                    contentContainer.unmask();
                }
            }
        };

        listLoader = new BaseListLoader<ListLoadResult<GWTJahiaNode>>(listProxy);
        listLoader.addLoadListener(new LoadListener() {
            @Override
            public void loaderLoad(LoadEvent le) {
                if (!le.isCancelled()) {
                    contentContainer.unmask();
                }
            }
        });

        contentStore = new ListStore<GWTJahiaNode>(listLoader);

        tree.getSelectionModel().addSelectionChangedListener(new SelectionChangedListener<GWTJahiaNode>() {
            @Override
            public void selectionChanged(SelectionChangedEvent<GWTJahiaNode> event) {
                contentContainer.mask(Messages.get("label.loading","Loading..."), "x-mask-loading");
                listLoader.load(event.getSelectedItem());
            }
        });

        ThumbsListView listView = new ThumbsListView(true);
        listView.setStyleAttribute("overflow-x", "hidden");
        listView.setStore(contentStore);
        listView.setTemplate(getPortletTemplate());
        contentContainer.add(listView);

        tree.setContextMenu(createContextMenu(config.getTreeContextMenu(), tree.getSelectionModel()));
        listView.setContextMenu(createContextMenu(config.getTableContextMenu(), listView.getSelectionModel()));

        VBoxLayoutData contentVBoxData = new VBoxLayoutData();
        contentVBoxData.setFlex(2);
        tab.add(contentContainer, contentVBoxData);

        dragSource = new ImageDragSource(listView);
        return tab;
    }

    @Override
    public void initWithLinker(EditLinker linker) {
        super.initWithLinker(linker);
        dragSource.addDNDListener(linker.getDndListener());
    }

    public class ImageDragSource extends ListViewDragSource {
        public ImageDragSource(ListView listView) {
            super(listView);
            DragListener listener = new DragListener() {
                public void dragEnd(DragEvent de) {
                    DNDEvent e = new DNDEvent(ImageDragSource.this, de.getEvent());
                    e.setData(data);
                    e.setDragEvent(de);
                    e.setComponent(component);
                    e.setStatus(statusProxy);

                    onDragEnd(e);
                }
            };
            draggable.addDragListener(listener);
        }

        @Override
        protected void onDragStart(DNDEvent e) {
            e.getStatus().setData(EditModeDNDListener.SOURCE_TYPE, EditModeDNDListener.CONTENT_SOURCE_TYPE);
            List<GWTJahiaNode> nodes = new ArrayList<GWTJahiaNode>(1);
            nodes.add((GWTJahiaNode) listView.getSelectionModel().getSelectedItem());
            e.setData(nodes);
            List<GWTJahiaNode> list = new ArrayList<GWTJahiaNode>(1);
            GWTJahiaNode selectedItem = (GWTJahiaNode) listView.getSelectionModel().getSelectedItem();
            list.add(selectedItem.getReferencedNode()!=null?selectedItem.getReferencedNode():selectedItem);
            e.getStatus().setData("size", list.size());
            e.getStatus().setData(EditModeDNDListener.SOURCE_NODES, list);
            e.setOperation(DND.Operation.COPY);
            super.onDragStart(e);
        }

        @Override
        protected void onDragCancelled(DNDEvent dndEvent) {
            super.onDragCancelled(dndEvent);
            onDragEnd(dndEvent);
        }

        protected void onDragEnd(DNDEvent e) {
            StatusProxy sp = e.getStatus();
            sp.setData(EditModeDNDListener.SOURCE_TYPE, null);
            sp.setData(EditModeDNDListener.CONTENT_SOURCE_TYPE, null);
            sp.setData(EditModeDNDListener.TARGET_TYPE, null);
            sp.setData(EditModeDNDListener.TARGET_NODE, null);
            sp.setData(EditModeDNDListener.TARGET_PATH, null);
            sp.setData(EditModeDNDListener.SOURCE_NODES, null);
            sp.setData(EditModeDNDListener.SOURCE_QUERY, null);
            sp.setData(EditModeDNDListener.SOURCE_TEMPLATE, null);
            sp.setData(EditModeDNDListener.OPERATION_CALLED, null);
            e.setData(null);
        }


    }

    @Override
    protected boolean acceptNode(GWTJahiaNode node) {
        return node.getInheritedNodeTypes().contains("jnt:portlet");
    }

    /**
     * Portlet template
     *
     * @return
     */
    public native String getPortletTemplate() /*-{
    return ['<tpl for=".">',
        '<div style="padding: 5px ;border-bottom: 1px solid #D9E2F4;float: left;width: 100%;" class="thumb-wrap" id="{name}">',
        '<div><div style="width: 140px; float: left; text-align: center;" class="thumb">{nodeImg}</div>',
        '<div style="margin-left: 160px; " class="thumbDetails">',
        '<div><b>{nameLabel}: </b>{name}</div>',
        '<div><b>{authorLabel}: </b>{createdBy}</div>',
        '{tagsHTML}',
        '</div>',
        '</div>',
        '<div style="padding-left: 10px; padding-top: 10px; clear: left">{description}</div></div></tpl>',
        '<div class="x-clear"></div>','</tpl>'
    ].join("");
    }-*/;


}
