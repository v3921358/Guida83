/*
 * This file is part of Guida.
 * Copyright (C) 2020 Guida
 *
 * Guida is a fork of the OdinMS MapleStory Server.
 * The following is the original copyright notice:
 *
 *     This file is part of the OdinMS Maple Story Server
 *     Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
 *                        Matthias Butz <matze@odinms.de>
 *                        Jan Christian Meyer <vimes@odinms.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation. You may not use, modify
 * or distribute this program under any other version of the
 * GNU Affero General Public License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package guida.server;

import guida.net.channel.ChannelServer;
import guida.server.playerinteractions.HiredMerchant;

import java.rmi.RemoteException;

/**
 * @author Frz
 */
public class ShutdownServer implements Runnable {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ShutdownServer.class);
    private final int myChannel;

    public ShutdownServer(int channel) {
        myChannel = channel;
    }

    @Override
    public void run() {
        try {
            ChannelServer.getInstance(myChannel).shutdown();
        } catch (Throwable t) {
            log.error("SHUTDOWN ERROR", t);
        }

        try {
            ChannelServer.getWorldRegistry().deregisterChannelServer(myChannel);
        } catch (RemoteException e) {
            // we are shutting down
        }
        log.info("Channel " + myChannel + " deregistered from world server.");
        try {
            ChannelServer.getInstance(myChannel).unbind();
        } catch (Throwable t) {
            log.error("SHUTDOWN ERROR", t);
        }
        log.info("Channel " + myChannel + " has unbinded.");
        boolean allShutdownFinished = true;
        for (ChannelServer cserv : ChannelServer.getAllInstances()) {
            if (!cserv.hasFinishedShutdown()) {
                allShutdownFinished = false;
            }
        }
        if (allShutdownFinished) {
            TimerManager.getInstance().stop();
            HiredMerchant.clearMerchants();
            /*try {
				DatabaseConnection.closeAll();
			} catch (SQLException e) {
				log.error("THROW", e);
			}*/
            log.info("Shutdown success - ignore repeats of this.");
        } else {
            log.info("Shutdown failed - ignore repeats of this.");
        }
    }
}