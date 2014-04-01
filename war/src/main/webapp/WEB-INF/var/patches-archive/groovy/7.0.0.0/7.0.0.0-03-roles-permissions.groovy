import org.apache.jackrabbit.core.security.JahiaPrivilegeRegistry
import org.apache.log4j.Logger
import org.jahia.services.content.*

import javax.jcr.ImportUUIDBehavior
import javax.jcr.NodeIterator
import javax.jcr.RepositoryException
import javax.jcr.query.Query
import javax.jcr.query.QueryResult

final Logger log = Logger.getLogger("org.jahia.tools.groovyConsole");

def callback = new JCRCallback<Object>() {
    public Object doInJCR(JCRSessionWrapper jcrsession) throws RepositoryException {
        def refToNames = { String nodeType, String refPropName, String namesPropName ->
            QueryResult result = jcrsession.getWorkspace().getQueryManager().createQuery("select * from [" + nodeType + "]", Query.JCR_SQL2).execute();
            NodeIterator ni = result.getNodes();
            while (ni.hasNext()) {
                JCRNodeWrapper next = (JCRNodeWrapper) ni.next();
                try {
                    if (next.hasProperty(refPropName)) {
                        JCRPropertyWrapper property = next.getProperty(refPropName);
                        JCRValueWrapper[] values = property.getRealValues();
                        def names = [];
                        values.collect(names, {
                            try {
                                def node = it.getNode();
                                if (node != null) {
                                    node.getName();
                                }
                            } catch (Exception e) {
                                log.error("Failed to get permission name", e);
                            }
                        });
                        next.setProperty(namesPropName, (String[]) names.toArray(new String[names.size()]));
                        property.remove();
                        jcrsession.save();
                    }
                } catch (Exception e) {
                    log.error("Failed to change references to names", e);
                }
            }
        }
        def workspaceName = jcrsession.getWorkspace().getName();
        log.info("Changing permission references to names on roles on workspace " + workspaceName + "...");
        refToNames("jnt:role", "j:permissions", "j:permissionNames");
        log.info("...update done.")
        log.info("Changing permission references to names on external permissions on workspace " + workspaceName + "...");
        refToNames("jnt:externalPermissions", "j:permissions", "j:permissionNames");
        log.info("...update done.")
        log.info("Changing required permissions references to names on workspace " + workspaceName + "...");
        refToNames("jmix:requiredPermissions", "j:requiredPermissions", "j:requiredPermissionNames");
        log.info("...update done.")
        return null;
    }
};
JCRTemplate.getInstance().doExecuteWithSystemSession(callback);
JCRTemplate.getInstance().doExecuteWithSystemSession(null, "live", callback);



JCRTemplate.getInstance().doExecuteWithSystemSession(new JCRCallback<Object>() {
    public Object doInJCR(JCRSessionWrapper jcrsession) throws RepositoryException {
        if (jcrsession.nodeExists("/permissions")) {
            log.info("Start re-importing permissions...")
            jcrsession.getNode("/permissions").remove();
            JCRContentUtils.importSkeletons("WEB-INF/etc/repository/root-permissions.xml", "/", jcrsession, ImportUUIDBehavior.IMPORT_UUID_COLLISION_THROW, null);
            JahiaPrivilegeRegistry.init(jcrsession);
            log.info("...permissions re-imported.")
        }
        jcrsession.save();

        JCRNodeWrapper rolesNode = jcrsession.getNode("/roles");
        if (rolesNode.hasNode("j:acl")) {
            log.info("Removing /roles/j:acl node.");
            rolesNode.getNode("j:acl").remove();
        }
        if (rolesNode.isNodeType("jmix:accessControlled")) {
            rolesNode.removeMixin("jmix:accessControlled")
        }

        JCRNodeWrapper role;
        JCRNodeWrapper subNode;

        def setPermissions = { JCRNodeWrapper node, List<String> permsToRemove, List<String> permsToAdd ->
            def permissionNames = [];
            node.getProperty("j:permissionNames").getValues().collect(permissionNames, { it.getString() });
            permissionNames.removeAll(permsToRemove);
            for (String permName : permsToAdd) {
                if (!permissionNames.contains(permName)) {
                    permissionNames.add(permName);
                }
            }
            node.setProperty("j:permissionNames", (String[]) permissionNames.toArray(new String[permissionNames.size()]));
        }

        def permsToAdd;
        def permsToRemove
        def permissionNames

        if (rolesNode.hasNode("privileged")) {
            log.info("Start updating privileged role...");
            role = rolesNode.getNode("privileged");
            role.setProperty("j:hidden", true);
            log.info("...update done.");
        }

        if (rolesNode.hasNode("contributor")) {
            log.info("Start updating contributor role...");
            role = rolesNode.getNode("contributor");
            role.setProperty("j:hidden", false);
            permsToRemove = ["contributeMode", "viewContentTab", "viewMetadataTab", "viewCategoriesTab",
                                 "viewTagsTab", "view-basic-wysiwyg-editor", "1-step-publication-start",
                                 "1-step-unpublication-start","2-step-publication-finish-correction","2-step-publication-start"];
            permsToAdd = ["publication-start", "publication-finish-correction"];
            setPermissions(role, permsToRemove, permsToAdd);

            subNode = role.addNode("currentSite-access", "jnt:externalPermissions");
            subNode.setProperty("j:path", "currentSite");
            permissionNames = ["contributeMode", "components", "editorialContentManager", "fileManager", "templates", "viewCategoriesTab", "viewContentTab", "viewMetadataTab", "viewTagsTab"];
            subNode.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));
            subNode = role.addNode("j:translation_en", "jnt:translation");
            subNode.setProperty("jcr:description", "Can edit content using contribute mode");
            subNode.setProperty("jcr:language", "en");
            subNode.setProperty("jcr:title", "Contributor");
            log.info("...update done.");
        }

        if (rolesNode.hasNode("reviewer")) {
            log.info("Start updating reviewer role...");
            role = rolesNode.getNode("reviewer");
            role.setProperty("j:hidden", false);
            permsToRemove = ["editModeAccess", "sitemapSelector", "viewContentTab", "viewLayoutTab",
                                 "viewMetadataTab", "viewOptionsTab", "viewCategoriesTab", "viewLiveRolesTab", "viewTagsTab",
                                 "viewSeoTab", "viewVisibilityTab", "viewContributeModeTab", "contributeModeAccess", "templates",
                                 "fileManager", "portletManager", "view-full-wysiwyg-editor", "1-step-publication-review", "1-step-unpublication-unpublish",
                                 "2-step-publication-first-review",
                                 "1-step-unpublication-choose-remote-publication",
                                 "1-step-publication-choose-remote-publication",
                                 "2-step-publication-choose-remote-publication",
                                 "2-step-publication-final-review"
            ];
            permsToAdd = ["publication-choose-remote", "publication-review", "publication-first-review"];
            setPermissions(role, permsToRemove, permsToAdd);

            subNode = role.addNode("currentSite-access", "jnt:externalPermissions");
            subNode.setProperty("j:path", "currentSite");
            permissionNames = ["contributeMode", "editModeAccess", "sitemapSelector", "viewCategoriesTab", "viewContentTab",
                                   "viewLayoutTab", "viewMetadataTab", "viewSeoTab", "viewTagsTab"];
            subNode.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));

            subNode = role.addNode("j:translation_en", "jnt:translation");
            subNode.setProperty("jcr:description", "Grant access to view the content and validate changes done by editors before publication");
            subNode.setProperty("jcr:language", "en");
            subNode.setProperty("jcr:title", "Reviewer");
            log.info("...update done.");
        }

        if (rolesNode.hasNode("editor")) {
            log.info("Start updating editor role...");
            role = rolesNode.getNode("editor");
            role.setProperty("j:hidden", false);
            permsToRemove = ["editModeActions", "editModeAccess", "editSelector", "viewContentTab", "viewLayoutTab",
                                 "viewMetadataTab", "viewOptionsTab", "viewCategoriesTab", "viewLiveRolesTab", "viewTagsTab",
                                 "viewSeoTab", "viewVisibilityTab", "viewContributeModeTab", "contributeMode", "templates",
                                 "fileManager", "portletManager", "view-full-wysiwyg-editor", "1-step-publication-start",
                                 "1-step-unpublication-start", "2-step-publication-finish-correction", "siteManager", "2-step-publication-start"
            ];
            permsToAdd = ["publication-start", "publication-finish-correction"];
            setPermissions(role, permsToRemove, permsToAdd);

            subNode = role.addNode("currentSite-access", "jnt:externalPermissions");
            subNode.setProperty("j:path", "currentSite");
            permissionNames = ["components", "editModeAccess", "editModeActions", "editSelector", "managers",
                                   "templates", "view-full-wysiwyg-editor", "viewCategoriesTab", "viewContentTab", "viewContributeModeTab",
                                   "viewLayoutTab", "viewMetadataTab", "viewOptionsTab", "viewSeoTab", "viewTagsTab", "viewVisibilityTab"];
            subNode.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));
            subNode = role.addNode("j:translation_en", "jnt:translation");
            subNode.setProperty("jcr:description", "Can edit content using edit mode");
            subNode.setProperty("jcr:language", "en");
            subNode.setProperty("jcr:title", "Editor");
            log.info("...update done.");
        }

        if (rolesNode.hasNode("editor-in-chief")) {
            log.info("Start updating editor-in-chief role...");
            permsToAdd = [];
            if (jcrsession.nodeExists("/roles/editor/editor-in-chief")) {
                // copy permissions and remove existing node
                jcrsession.save()
                JCRObservationManager.setAllEventListenersDisabled(Boolean.TRUE);
                JCRNodeWrapper existingNode = jcrsession.getNode("/roles/editor/editor-in-chief");
                if (existingNode.hasProperty("j:permissionNames")) {
                    existingNode.getProperty("j:permissionNames").getValues().collect(permsToAdd, { it.getString() });
                }
                existingNode.remove();
                jcrsession.save()
                JCRObservationManager.setAllEventListenersDisabled(Boolean.FALSE);
            }
            role = rolesNode.getNode("editor-in-chief");
            role.setProperty("j:hidden", false);
            permsToAdd = ["adminMicrosoftTranslation", "siteAdminLinkChecker"]
            permsToRemove = ["jcr:all_default", "categoryManager", "editorialContentManager", "fileManager",
                                 "portletManager", "siteManager", "tagManager", "jobs", "editMode", "view-basic-wysiwyg-editor",
                                 "templates", "adminLinkChecker", "rolesManager"];
            setPermissions(role, permsToRemove, permsToAdd);

            subNode = role.addNode("currentSite-access", "jnt:externalPermissions");
            subNode.setProperty("j:path", "currentSite");
            subNode.setProperty("j:permissionNames", ["editMode"].toArray(new String[1]));
            subNode = role.addNode("j:translation_en", "jnt:translation");
            subNode.setProperty("jcr:description", "View all engine tabs, is also allowed to grant and revoke roles on content");
            subNode.setProperty("jcr:language", "en");
            subNode.setProperty("jcr:title", "Editor in chief");

            jcrsession.save()
            JCRObservationManager.setAllEventListenersDisabled(Boolean.TRUE);
            jcrsession.move("/roles/editor-in-chief", "/roles/editor/editor-in-chief");
            jcrsession.save()
            JCRObservationManager.setAllEventListenersDisabled(Boolean.FALSE);

            log.info("...update done.");
        }

        if (rolesNode.hasNode("web-designer")) {
            log.info("Start updating web-designer role...");
            role = rolesNode.getNode("web-designer");
            role.setProperty("j:hidden", false);
            def nodeTypes = [];
            role.getProperty("j:nodeTypes").getValues().collect(nodeTypes, { it.getString() });
            nodeTypes.remove("jnt:virtualsite");
            if (!nodeTypes.contains("rep:root")) {
                nodeTypes.add("rep:root");
            }
            role.setProperty("j:nodeTypes", (String[]) nodeTypes.toArray(new String[nodeTypes.size()]));
            role.setProperty("j:permissionNames", ["adminTemplates"].toArray(new String[1]));
            role.setProperty("j:roleGroup", "server-role");

            if (role.hasNode("studio-access")) {
                role.getNode("studio-access").remove();
            }

            if (role.hasNode("modules-management")) {
                role.getNode("studio-management").remove();
            }

            subNode = role.addNode("modules-access", "jnt:externalPermissions");
            subNode.setProperty("j:path", "/modules");
            permissionNames = ["components","editModeActions","editSelector","engineTabs","jcr:all_default",
                                   "managers","studioMode","templates","useComponent","wysiwyg-editor-toolbar"];
            subNode.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));

            subNode = role.addNode("j:translation_en", "jnt:translation");
            subNode.setProperty("jcr:description", "Gives full access to the studio");
            subNode.setProperty("jcr:language", "en");
            subNode.setProperty("jcr:title", "Web designer");
            log.info("...update done.");
        }

        if (rolesNode.hasNode("site-administrator")) {
            log.info("Start updating site-administrator role...");
            role = rolesNode.getNode("site-administrator");
            role.setProperty("j:hidden", false);
            def nodeTypes = [];
            role.getProperty("j:nodeTypes").getValues().collect(nodeTypes, { it.getString() });
            nodeTypes.remove("jnt:virtualsite");
            role.setProperty("j:nodeTypes", (String[]) nodeTypes.toArray(new String[nodeTypes.size()]));
            permsToRemove = ["administrationAccess", "siteAdminLanguages", "siteAdminUrlmapping",
                                 "siteAdminHtmlSettings", "adminDocumentation", "siteAdminGroups", "adminIssueTracking",
                                 "siteAdminTemplates", "siteAdminWcagCompliance", "adminGroups", "adminHtmlSettings",
                                 "adminLinkChecker", "adminSiteLanguages", "adminSiteTemplates", "adminUrlmapping",
                                 "categoryManager", "editorialContentManager", "fileManager", "portletManager",
                                 "repositoryExplorer", "rolesManager", "siteManager", "tagManager"];
            permsToAdd = ["components", "remotePublicationManager", "repositoryExplorer", "site-admin",
                          "adminBootstrapCustomization", "adminMicrosoftTranslation",
                          "adminSampleTemplatesCustomizationcomponents", "managers",
                          "publish", "siteAdminLinkChecker","workflow-tasks"];
            setPermissions(role, permsToRemove, permsToAdd);
            role.setProperty("j:roleGroup", "site-role");

            subNode = role.addNode("bootstrap-write-publish", "jnt:externalPermissions");
            subNode.setProperty("j:path", "currentSite/files/bootstrap");
            permissionNames = ["jcr:all_default", "workflow-tasks", "publish"];
            subNode.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));

            subNode = role.addNode("j:translation_en", "jnt:translation");
            subNode.setProperty("jcr:description", "Gives administrative privileges over the site");
            subNode.setProperty("jcr:language", "en");
            subNode.setProperty("jcr:title", "Site administrator");
            log.info("...update done.");
        }

        log.info("Start creating translator role...");
        permsToAdd = ["jcr:versionManagement_default", "publication-start", "publication-finish-correction"];
        if (jcrsession.nodeExists("/roles/translator")) {
            // copy permissions and remove existing node
            JCRNodeWrapper existingNode = jcrsession.getNode("/roles/translator");
            if (existingNode.hasProperty("j:permissionNames")) {
                existingNode.getProperty("j:permissionNames").getValues().collect(permsToAdd, { it.getString() });
            }
            jcrsession.save()
            JCRObservationManager.setAllEventListenersDisabled(Boolean.TRUE);
            existingNode.remove();
            jcrsession.save()
            JCRObservationManager.setAllEventListenersDisabled(Boolean.FALSE);
        }
        role = rolesNode.addNode("translator", "jnt:role");
        role.setProperty("j:hidden", true);
        role.setProperty("j:permissionNames", permsToAdd.toArray(new String[permsToAdd.size()]));
        role.setProperty("j:privilegedAccess", true);
        role.setProperty("j:roleGroup", "edit-role");
        subNode = role.addNode("currentSite-access", "jnt:externalPermissions");
        subNode.setProperty("j:path", "currentSite");
        permissionNames = ["components", "editModeAccess", "sitemapSelector", "view-full-wysiwyg-editor"];
        subNode.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));

        permsToRemove = ["editModeAccess", "sitemapSelector", "jcr:versionManagement_default"];
        jcrsession.save()
        JCRObservationManager.setAllEventListenersDisabled(Boolean.TRUE);
        for (JCRNodeWrapper trans : rolesNode.getNodes("translator-*")) {
            setPermissions(trans, permsToRemove, [])
            jcrsession.move(trans.getPath(), "/roles/translator/" + trans.getName());
        };
        jcrsession.save()
        JCRObservationManager.setAllEventListenersDisabled(Boolean.FALSE);
        log.info("...creation done.");

        log.info("Start creating server-administrator role...");
        role = rolesNode.addNode("server-administrator", "jnt:role");
        role.setProperty("j:hidden", false);
        role.setProperty("j:nodeTypes", ["rep:root"].toArray(new String[1]));
        permissionNames = ["repository-permissions", "admin", "publish"];
        role.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));
        role.setProperty("j:privilegedAccess", true);
        role.setProperty("j:roleGroup", "server-role");
        subNode = role.addNode("currentSite-access", "jnt:externalPermissions");
        subNode.setProperty("j:path", "/sites/systemsite");
        permissionNames = ["managers", "engineTabs"];
        subNode.setProperty("j:permissionNames", permissionNames.toArray(new String[permissionNames.size()]));

        subNode = role.addNode("j:translation_en", "jnt:translation");
        subNode.setProperty("jcr:description", "Grant access to the server administration");
        subNode.setProperty("jcr:language", "en");
        subNode.setProperty("jcr:title", "Server administrator");
        log.info("...creation done.");

        jcrsession.save();
        return null;
    }
});


JCRTemplate.getInstance().doExecuteWithSystemSession(null, "live", new JCRCallback<Object>() {
    public Object doInJCR(JCRSessionWrapper jcrsession) throws RepositoryException {
        if (jcrsession.nodeExists("/permissions")) {
            log.info("Removing /permissions node on live.");
            jcrsession.getNode("/permissions").remove();
        }
        if (jcrsession.nodeExists("/roles")) {
            log.info("Removing /roles node on live.");
            JCRObservationManager.setAllEventListenersDisabled(Boolean.TRUE);
            jcrsession.getNode("/roles").remove();
            jcrsession.save();
            JCRObservationManager.setAllEventListenersDisabled(Boolean.FALSE);
        }
        jcrsession.save();
        return null;
    }
});

JCRTemplate.getInstance().doExecuteWithSystemSession(null, null, new JCRCallback<Object>() {
    public Object doInJCR(JCRSessionWrapper jcrsession) throws RepositoryException {
        JCRNodeIteratorWrapper ni = jcrsession.getWorkspace().getQueryManager().createQuery("select * from [jnt:virtualsite]", Query.JCR_SQL2).execute().getNodes();
        while (ni.hasNext()) {
            JCRNodeWrapper next = (JCRNodeWrapper) ni.next();
            if (next.hasNode("files/contributed")) {
                JCRNodeWrapper contributed = next.getNode("files/contributed");
                contributed.revokeAllRoles();
            }
        }
        jcrsession.save();
        return null;
    }
});
