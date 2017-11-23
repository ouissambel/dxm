/**
 * ==========================================================================================
 * =                   JAHIA'S DUAL LICENSING - IMPORTANT INFORMATION                       =
 * ==========================================================================================
 *
 *                                 http://www.jahia.com
 *
 *     Copyright (C) 2002-2017 Jahia Solutions Group SA. All rights reserved.
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
package org.jahia.settings.readonlymode;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.jahia.services.SpringContextSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The read-only mode controller service that is responsible for performing mode switch and ensuring an appropriate event is broadcasted and
 * the related services are "notified".
 *
 * @author Sergiy Shyrkov
 */
public class ReadOnlyModeController {

    private static final Logger logger = LoggerFactory.getLogger(ReadOnlyModeController.class);

    private static final Comparator<ReadOnlyModeCapable> SERVICES_COMPARATOR_BY_PRIORITY = new Comparator<ReadOnlyModeCapable>() {
        @Override
        public int compare(ReadOnlyModeCapable o1, ReadOnlyModeCapable o2) {
            return -Integer.compare(o1.getReadOnlyModePriority(), o2.getReadOnlyModePriority());
        }
    };

    /**
     * Read only mode status.
     */
    public enum ReadOnlyModeStatus {

        /**
         * Read only mode is switched off
         */
        OFF,

        /**
         * Read only mode is switched on
         */
        ON,

        /**
         * The application is in progress switching read only mode on
         */
        PENDING_ON,

        /**
         * The application is in progress switching read only mode off
         */
        PENDING_OFF
    }

    private volatile ReadOnlyModeStatus readOnlyStatus = ReadOnlyModeStatus.OFF;

    private long serviceNotificationTimeout = 60 * 1000L;

    /**
     * Handles the case, when a service encounters read-only mode violation, i.e. a data or state modification is requested.
     *
     * @param message the detailed error message from the service
     * @throws ReadOnlyModeException as a result of violation of read-only mode
     */
    public static void readOnlyModeViolated(String message) throws ReadOnlyModeException {
        throw new ReadOnlyModeException(message);

    }

    /**
     * Performs the switch of all DX services into read-only mode or back.
     *
     * @param enable <code>true</code> if the read-only mode should be enabled; <code>false</code> if it should be disabled
     */
    public synchronized void switchReadOnlyMode(boolean enable) {

        ReadOnlyModeStatus targetStatus = enable ? ReadOnlyModeStatus.ON : ReadOnlyModeStatus.OFF;
        logger.info("Received request to switch read only mode to {}", targetStatus);

        if (!checkStatusUpdateNeeded(enable)) {
            throw new IllegalArgumentException("The read-only mode flag is " + readOnlyStatus + ", unable to switch " + targetStatus);
        }

        // switch to pending
        readOnlyStatus = enable ? ReadOnlyModeStatus.PENDING_ON : ReadOnlyModeStatus.PENDING_OFF;

        List<ReadOnlyModeCapable> services = new LinkedList<>(
                SpringContextSingleton.getBeansOfType(ReadOnlyModeCapable.class).values());
        Collections.sort(services, SERVICES_COMPARATOR_BY_PRIORITY);

        logger.info("Notifying {} services about read-only mode change", services.size());
        for (ReadOnlyModeCapable service : services) {
            try {
                service.onReadOnlyModeChanged(enable, serviceNotificationTimeout);
            } catch (Exception e) {
                logger.error("Error notifying service " + service + " about read-only mode change", e);
            }
        }

        // switch to final state
        readOnlyStatus = targetStatus;

        logger.info("Finished read-only mode switch. Now the read-only mode is {}", readOnlyStatus);
    }

    private boolean checkStatusUpdateNeeded(boolean enable) {
        return enable ? readOnlyStatus == ReadOnlyModeStatus.OFF : readOnlyStatus == ReadOnlyModeStatus.ON;
    }

    // Initialization on demand holder idiom: thread-safe singleton initialization
    private static class Holder {
        static final ReadOnlyModeController INSTANCE = new ReadOnlyModeController();
    }
    public static ReadOnlyModeController getInstance() {
        return Holder.INSTANCE;
    }

    public void setServiceNotificationTimeout(long serviceNotificationTimeout) {
        this.serviceNotificationTimeout = serviceNotificationTimeout;
    }

    public ReadOnlyModeStatus getReadOnlyStatus() {
        return readOnlyStatus;
    }
}