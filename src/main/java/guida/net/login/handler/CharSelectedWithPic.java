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

package guida.net.login.handler;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.net.AbstractMaplePacketHandler;
import guida.net.login.LoginServer;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;

public class CharSelectedWithPic extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CharSelectedWithPic.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (c.getLoginState() != MapleClient.LOGIN_LOGGEDIN) {
            return;
        }
        final String PIC = slea.readMapleAsciiString();
        if (!c.getPIC().equals(PIC)) {
            c.sendPacket(MaplePacketCreator.sendPICResponse(20));
            return;
        }
        final int charId = slea.readInt();
        final String macs = slea.readMapleAsciiString();
        final String lastMacAddress = slea.readMapleAsciiString();
        c.updateMacs(macs, lastMacAddress);
        if (c.hasBannedMac()) {
            c.disconnect();
            return;
        }
        try {
            /*if (c.getIdleTask() != null) {
				c.getIdleTask().cancel(true);
				c.setIdleTask(null);
			}*/
            if (c.hasCharacter(charId)) {
                MapleCharacter.setLoggedInState(charId, 0);
            }
            c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION);
            final String channelServerIP = MapleClient.getChannelServerIPFromSubnet(c.getIP(), c.getChannel());
            final String[] socket = LoginServer.getInstance().getIP(c.getChannel()).split(":");

            if (channelServerIP.equals("0.0.0.0")) {
                c.sendPacket(MaplePacketCreator.getServerIP(InetAddress.getByName(socket[0]), Integer.parseInt(socket[1]), charId));
            } else {
                c.sendPacket(MaplePacketCreator.getServerIP(InetAddress.getByName(channelServerIP), Integer.parseInt(socket[1]), charId));
            }
        } catch (NullPointerException | SQLException e) {
            e.printStackTrace();
        } catch (UnknownHostException e) {
            log.error("Host not found", e);
        }
    }
}