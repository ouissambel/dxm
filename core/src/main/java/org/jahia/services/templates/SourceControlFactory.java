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
package org.jahia.services.templates;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.PumpStreamHandler;
import org.jahia.utils.StringOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source control management central factory class responsible for checking out a remote SCM repository content and instantiating
 * {@link SourceControlManagement} helpers for SCM operations on module's sources.
 */
public class SourceControlFactory {
    
    private static final Logger logger = LoggerFactory.getLogger(SourceControlFactory.class);
    
    private Map<String, String> sourceControlExecutables;

    /**
     * Performs checkout of the remote SCM content into the provided working directory.
     * 
     * @param workingDir
     *            working directory to perform checkout into
     * @param scmURI
     *            the remote SCM repository URL
     * @param branchOrTag
     *            the name of the remote branch or tag if any
     * @return an instance of the {@link SourceControlManagement} helper for the checked out content
     * @throws IOException
     *             in case of communication errors
     */
    public SourceControlManagement checkoutRepository(File workingDir, String scmURI, String branchOrTag, boolean initRepository)
            throws IOException {
        SourceControlManagement scm = getSCM(scmURI);
        if (scm != null) {
            String scmUrl = getScmURL(scmURI);
            if (initRepository) {
                scm.sendToSCM(workingDir, scmUrl);
            } else  {
                scm.getFromSCM(workingDir, scmUrl, branchOrTag);
            }
        }
        return scm;
    }

    /**
     * List all available tags on distant repository
     * @param scmURI the remote SCM repository URI (of the trunk for SVN)
     * @return a map tag/uri
     * @throws IOException
     */
    public Map<String, String> listTags(String scmURI) throws IOException {
        SourceControlManagement scm = getSCM(scmURI);
        if (scm != null) {
            String scmUrl = getScmURL(scmURI);
            return scm.getTagInfos(scmUrl);
        }
        return null;
    }


    /**
     * List all available branches on distant repository
     * @param scmURI the remote SCM repository URI (of the trunk for SVN)
     * @return a map tag/uri
     * @throws IOException
     */
    public Map<String, String> listBranches(String scmURI) throws IOException {
        SourceControlManagement scm = getSCM(scmURI);
        if (scm != null) {
            String scmUrl = getScmURL(scmURI);
            return scm.getBranchInfos(scmUrl);
        }
        return null;
    }

    private SourceControlManagement getSCM(String scmURI) throws IOException {
        SourceControlManagement scm = null;
        if (scmURI.startsWith("scm:")) {
            String scmProvider = scmURI.substring(4, scmURI.indexOf(":", 4));
            if (scmProvider.equals("git") && sourceControlExecutables.containsKey("git")) {
                scm = new GitSourceControlManagement(sourceControlExecutables.get("git"));
            } else if (scmProvider.equals("svn") && sourceControlExecutables.containsKey("svn")) {
                scm = new SvnSourceControlManagement(sourceControlExecutables.get("svn"));
            } else {
                throw new IOException("Unknown repository type");
            }
        }
        return scm;
    }

    private String getScmURL(String scmURI) {
        return scmURI.substring(scmURI.indexOf(":", 4) + 1);
    }

    /**
     * Returns a registry of executables (paths to the SCM executables) by SCM type.
     * 
     * @return a registry of executables (paths to the SCM executables) by SCM type
     */
    public Map<String, String> getSourceControlExecutables() {
        return sourceControlExecutables;
    }

    /**
     * Returns an instance of the {@link SourceControlManagement} helper for the specified working directory.
     * 
     * @param workingDir
     *            the working directory to get SCM helper for
     * @return an instance of the {@link SourceControlManagement} helper for the specified working directory
     * @throws IOException
     *             in case of I/O errors
     */
    public SourceControlManagement getSourceControlManagement(File workingDir) throws IOException {
        SourceControlManagement scm = null;
        File dir = workingDir;
        do {
            if (new File(dir, ".git").exists()) {
                if (!sourceControlExecutables.containsKey("git")) {
                    // no git SCM provider found
                    break;
                }
                scm = new GitSourceControlManagement(sourceControlExecutables.get("git"));
            } else if (new File(dir, ".svn").exists()) {
                if (!sourceControlExecutables.containsKey("svn")) {
                    // no SVN SCM provider found
                    break;
                }
                scm = new SvnSourceControlManagement(sourceControlExecutables.get("svn"));
            } else {
                dir = dir.getParentFile();
            }
        } while (scm == null && dir != null);

        if (scm != null) {
            scm.initWithWorkingDirectory(workingDir);
        }
        return scm;
    }

    /**
     * Returns a set of supported SCM types.
     * 
     * @return a set of supported SCM types
     */
    public Set<String> getSupportedSourceControls() {
        return sourceControlExecutables.keySet();
    }

    /**
     * Sets the executables for various SCM providers.
     * 
     * @param sourceControlExecutables
     *            a map with paths to SCM executables by SCM type
     */
    public void setSourceControlExecutables(Map<String, String> sourceControlExecutables) {
        this.sourceControlExecutables = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : sourceControlExecutables.entrySet()) {
            try {
                DefaultExecutor executor = new DefaultExecutor();
                executor.setStreamHandler(new PumpStreamHandler(new StringOutputStream(), new StringOutputStream()));
                executor.execute(new CommandLine(entry.getValue()), System.getenv());
            } catch (ExecuteException e) {
                // ignore this one as the command always returns error code 1
            } catch (IOException e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Unable to execute the " + entry.getKey() + " SCM executable: " + entry.getValue()
                            + ". The SCM provider will be disabled. Cause: " + e.getMessage(), e);
                } else {
                    logger.info("Cannot find a valid " + entry.getKey() + " SCM executable at: " + entry.getValue()
                            + ". The SCM provider will be skipped.");
                }
                continue;
            }
            this.sourceControlExecutables.put(entry.getKey(), entry.getValue());
        }
    }
}
