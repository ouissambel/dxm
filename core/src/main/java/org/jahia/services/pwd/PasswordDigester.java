/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2016 Jahia Solutions Group SA. All rights reserved.
 *
 *     THIS FILE IS AVAILABLE UNDER TWO DIFFERENT LICENSES:
 *     1/GPL OR 2/JSEL
 *
 *     1/ GPL
 *     ==================================================================================
 *
 *     IF YOU DECIDE TO CHOOSE THE GPL LICENSE, YOU MUST COMPLY WITH THE FOLLOWING TERMS:
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
 *
 */
package org.jahia.services.pwd;

/**
 * Common interface for all password encryption (hashing) operations.
 * 
 * @author Sergiy Shyrkov
 */
public interface PasswordDigester {

    /**
     * Create a digest of the provided password.
     * 
     * @param password
     *            the clear text password to be hashed
     * @return the digest of the provided password
     */
    String digest(String password);

    /**
     * Returns unique identifier of this digester to be able to distinguish between various hashing algorithms.
     * 
     * @return unique identifier of this digester to be able to distinguish between various hashing algorithms
     */
    String getId();

    /**
     * Should this digester become the default password digester, which is used in the system?
     * 
     * @return <code>true</code> if this digester becomes the default one, which is used in the system
     */
    boolean isDefault();

    /**
     * Checks, if the provided clear text password matches the specified digest, considering all aspects like salt, hashing iterations, etc.
     * 
     * @param password
     *            the clear text password to be checked
     * @param digest
     *            the digest against which the password will be matched
     * @return <code>true</code>, if the provided password matches its hashed equivalent
     */
    boolean matches(String password, String digest);
}
