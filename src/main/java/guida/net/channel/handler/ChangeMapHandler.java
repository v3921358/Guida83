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

package guida.net.channel.handler;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.net.AbstractMaplePacketHandler;
import guida.net.MaplePacket;
import guida.server.MapleInventoryManipulator;
import guida.server.MaplePortal;
import guida.server.maps.MapleMap;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.net.InetAddress;

public class ChangeMapHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChangeMapHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        MapleCharacter player = c.getPlayer();
        player.resetAfkTimer();
        if (slea.available() == 0) {
            int channel = c.getChannel();
            String ip = c.getChannelServer().getIP(channel);
            String[] socket = ip.split(":");
            player.saveToDB(true);
            player.setInCS(false);
            player.setInMTS(false);
            player.cancelSavedBuffs();
            player.resetCooldowns();
            c.getChannelServer().removePlayer(player);
            c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION);
            try {
                MapleCharacter.setLoggedInState(player.getId(), 0);
                c.setPlayer(null);
                MaplePacket packet = MaplePacketCreator.getChannelChange(InetAddress.getByName(socket[0]), Integer.parseInt(socket[1]));
                c.sendPacket(packet);
                c.disconnect();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            slea.skip(1); // 1 = from dying 2 = regular portal
            final int targetid = slea.readInt();
            final String startwp = slea.readMapleAsciiString();
            final MaplePortal portal = player.getMap().getPortal(startwp);
            slea.skip(1);
            final boolean useDeathItem = slea.readShort() == 1;

            if (targetid != -1 && !player.isAlive()) {
                boolean executeStandardPath = true;
                boolean hasDeathItem = false;
                if (player.getEventInstance() != null) {
                    executeStandardPath = player.getEventInstance().revivePlayer(player);
                }
                if (executeStandardPath) {
                    player.cancelAllBuffs();
                    MapleMap to = player.getMap().getReturnMap();
                    if (useDeathItem && player.getInventory(MapleInventoryType.CASH).countById(5510000) > 0) {
                        hasDeathItem = true;
                        to = player.getMap();
                    }
                    player.setStance((byte) 0);
                    if (player.getMap().canExit() && to != null && to.canEnter() || player.isGM()) {
                        player.setHp(50);
                        if (hasDeathItem) {
                            MapleInventoryManipulator.removeById(c, MapleInventoryType.CASH, 5510000, 1, true, false);
                        }
                        player.changeMap(to, to.getRandomSpawnPoint());
                    } else {
                        c.sendPacket(MaplePacketCreator.serverNotice(5, "You will remain dead."));
                        c.sendPacket(MaplePacketCreator.enableActions());
                    }
                }
            } else if (targetid != -1 && (player.isGM() || targetid == player.getAutoChangeMapId())) {
                MapleMap to = c.getChannelServer().getMapFactory().getMap(targetid);
                if (player.getMapId() == 2010000) {
                    player.changeMap(to, to.getPortal(5));
                } else {
                    player.changeMap(to, to.getRandomSpawnPoint());
                }
            } else if (targetid != -1 && !player.isGM()) {
                log.warn("Player {} attempted Map jumping without being a GM", player.getName());
            } else {
                if (player.getEventInstance() != null && (player.getMapId() == 910510201 || player.getMapId() == 108000700)) {
                    player.getEventInstance().playerMapExit(player);
                }
                if (portal != null) {
                    portal.enterPortal(c);
                } else {
                    //c.sendPacket(MaplePacketCreator.enableActions());
                    log.warn(MapleClient.getLogMessage(c, "Portal {} not found on map {}", startwp, player.getMap().getId()));
                    c.disconnect();
                }
            }
        }
    }
}