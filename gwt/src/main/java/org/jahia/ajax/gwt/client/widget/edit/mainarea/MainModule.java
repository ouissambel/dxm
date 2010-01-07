package org.jahia.ajax.gwt.client.widget.edit.mainarea;

import com.extjs.gxt.ui.client.widget.layout.FlowLayout;
import com.extjs.gxt.ui.client.widget.LayoutContainer;
import com.extjs.gxt.ui.client.widget.ContentPanel;
import com.extjs.gxt.ui.client.Style;
import com.extjs.gxt.ui.client.event.ComponentEvent;
import com.extjs.gxt.ui.client.event.Listener;
import com.extjs.gxt.ui.client.event.Events;
import com.extjs.gxt.ui.client.widget.tips.ToolTipConfig;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Event;
import com.google.gwt.core.client.GWT;
import org.jahia.ajax.gwt.client.core.JahiaGWTParameters;
import org.jahia.ajax.gwt.client.messages.Messages;
import org.jahia.ajax.gwt.client.service.content.JahiaContentManagementService;
import org.jahia.ajax.gwt.client.data.node.GWTJahiaNode;
import org.jahia.ajax.gwt.client.widget.edit.contentengine.EditContentEnginePopupListener;
import org.jahia.ajax.gwt.client.widget.edit.EditLinker;

import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * First module of any rendered element.
 * Sub content will be created as ListModule or SimpleModule.
 */
public class MainModule extends ContentPanel implements Module {

    private static MainModule module;
    private GWTJahiaNode node;
    private HTML html;
    private String path;
    private String template;
    private boolean selectable = true;
    private String originalHtml;
    private EditLinker editLinker;

    Map<Element, Module> m;

    public MainModule(final String html, final String path, final String template) {
        super(new FlowLayout());
        setHeading("Page : "+path);
        setScrollMode(Style.Scroll.AUTO);

        this.originalHtml = html;
        this.path = path;
        this.template = template;
        getHeader().setStyleAttribute("z-index","999");
        getHeader().setStyleAttribute("position","relative");
        Hover.getInstance().setMainModule(this);
        Selection.getInstance().setMainModule(this);

        module = this;
        exportStaticMethod();
    }

    public void initWithLinker(EditLinker linker) {
        this.editLinker = linker;
        display(originalHtml);

        sinkEvents(Event.ONCLICK + Event.ONDBLCLICK + Event.ONMOUSEOVER + Event.ONMOUSEOUT);

        Listener<ComponentEvent> listener = new Listener<ComponentEvent>() {
            public void handleEvent(ComponentEvent ce) {
                if (selectable) {
                    editLinker.onModuleSelection(MainModule.this);
                }
            }
        };
        addListener(Events.OnClick, listener);
        addListener(Events.OnDoubleClick, new EditContentEnginePopupListener(this,editLinker));

//        getHeader().sinkEvents(Event.ONCLICK + Event.ONDBLCLICK);
//        Listener<ComponentEvent> listener = new Listener<ComponentEvent>() {
//            public void handleEvent(ComponentEvent ce) {
//                Log.info("click" + path);
//                editLinker.onModuleSelection(MainModule.this);
//            }
//        };
//        getHeader().addListener(Events.OnClick, listener);
//        getHeader().addListener(Events.OnDoubleClick, new EditContentEnginePopupListener(this,editLinker));
    }

    public EditLinker getEditLinker() {
        return editLinker;
    }

    public void refresh() {
        JahiaContentManagementService.App.getInstance().getRenderedContent(path, null, editLinker.getLocale(), template, "wrapper.bodywrapper", null, true, new AsyncCallback<String>() {
            public void onSuccess(String result) {
                int i = getVScrollPosition();
                setHeading("Page : "+path);                
                removeAll();
                Selection.getInstance().hide();
                Hover.getInstance().removeAll();
                display(result);

                setVScrollPosition(i);
                List<String> list = new ArrayList<String>(1);
                list.add(path);
                editLinker.getMainModule().unmask();                
            }

            public void onFailure(Throwable caught) {
                GWT.log("error", caught);
            }
        });

    }

    private void display(String result) {
        html = new HTML(result);
        add(html);
        ModuleHelper.tranformLinks(html);
        ModuleHelper.initAllModules(this, html);
        ModuleHelper.buildTree(this);
        parse();
        layout();
    }

    @Override
    protected void onAfterLayout() {
        super.onAfterLayout();
        if (m != null) {
            ModuleHelper.move(m);
        }
    }

    public void parse() {
        m = ModuleHelper.parse(this);
    }

    public void onParsed() {        
    }

    public String getModuleId() {
        return "main";
    }

    public HTML getHtml() {
        return html;
    }

    public LayoutContainer getContainer() {
        return this;
    }

    public int getDepth() {
        return 0;
    }

    public void setDepth(int depth) {
    }   

    public void setSelectable(boolean selectable) {
        this.selectable = selectable;
    }

    public boolean isSelectable() {
        return selectable;
    }

    public String getPath() {
        return path;
    }

    public void goTo(String path, String template) {
        mask("Loading","x-mask-loading");
        this.path = path;
        this.template = template;
        refresh();        
    }

    public static void staticGoTo(String path, String template) {
        module.mask("Loading","x-mask-loading");
        module.path = path;
        module.template = template;
        module.refresh();
    }

    public GWTJahiaNode getNode() {
        return node; 
    }

    public void setNode(GWTJahiaNode node) {
        this.node = node;
        if (node.getNodeTypes().contains("jnt:page") || node.getInheritedNodeTypes().contains("jnt:page")) {
//            editManager.getEditLinker().getCreatePageButton().setEnabled(true);
        }
        if(node.getNodeTypes().contains("jmix:shareable")) {
//            this.setStyleAttribute("background","rgb(210,50,50) url("+ JahiaGWTParameters.getContextPath()+"/css/images/andromeda/rayure.png)");
            this.setToolTip(new ToolTipConfig(Messages.get("info_important","Important"),Messages.get("info_sharednode","This is a shared node")));
        }
    }


    public Module getParentModule() {
        return null;
    }

    public void setParentModule(Module module) {
    }

    public String getTemplate() {
        return null;
    }

    public void handleNewModuleSelection(Module selectedModule) {
        Selection l = Selection.getInstance();
        l.hide();
        if (selectedModule != null) {
            l.select(selectedModule);
            l.show();
        }
        l.layout();
    }

    public void handleNewSidePanelSelection(GWTJahiaNode node) {

    }

    public boolean isDraggable() {
        return false;
    }

    public void setDraggable(boolean isDraggable) {
    }

    public static native void exportStaticMethod() /*-{
       $wnd.goTo = function(x,y) {
          @org.jahia.ajax.gwt.client.widget.edit.mainarea.MainModule::staticGoTo(Ljava/lang/String;Ljava/lang/String;)(x,y);
       }
    }-*/;


}
