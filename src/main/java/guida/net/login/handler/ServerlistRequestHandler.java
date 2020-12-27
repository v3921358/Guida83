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

import guida.client.MapleClient;
import guida.net.AbstractMaplePacketHandler;
import guida.net.login.LoginServer;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class ServerlistRequestHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (c.getLoginState() == MapleClient.PIN_CORRECT || c.getLoginState() == MapleClient.VIEW_ALL_CHAR) {
            c.updateLoginState(MapleClient.LOGIN_LOGGEDIN);
            c.sendPacket(MaplePacketCreator.sendLoginMethod());
            c.sendPacket(MaplePacketCreator.getServerList(0, LoginServer.getInstance().getServerName(), LoginServer.getInstance().getLoad()));
            c.sendPacket(MaplePacketCreator.getEndOfServerList());
            c.sendPacket(MaplePacketCreator.getRecommendedServer(0));
            c.sendPacket(MaplePacketCreator.getEndOfServerList(true, 0, "Welcome to Guida!"));
        }
    }
}