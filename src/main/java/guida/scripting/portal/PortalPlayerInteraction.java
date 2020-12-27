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

package guida.scripting.portal;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.scripting.AbstractPlayerInteraction;
import guida.server.MaplePortal;
import guida.server.TimerManager;
import guida.server.maps.MapleMap;

public class PortalPlayerInteraction extends AbstractPlayerInteraction {

    private final MaplePortal portal;

    public PortalPlayerInteraction(MapleClient c, MaplePortal portal) {
        super(c);
        this.portal = portal;
    }

    public MaplePortal getPortal() {
        return portal;
    }

    public void schedulePortalSpawn(final MapleMap map, final String name, final boolean spawn, long time) {
        TimerManager.getInstance().schedule(() -> map.getPortal(name).setSpawned(spawn), time);
    }

    public boolean miniDungeon(int fromMap, int fstDungeon, int noDungeons) {
        final MapleParty p = getPlayer().getParty();
        final MapleCharacter ch = getPlayer();
        if (getMapId() == fromMap) {
            if (p != null && p.getLeader().getId() != getPlayer().getId()) {
                this.playerMessage(5, "Only the party leader can enter Mini Dungeons.");
                return false;
            }
            int mapId;
            for (int x = 0; x < noDungeons; x++) {
                mapId = fstDungeon + x;
                if (getPlayerCount(mapId) == 0) {
                    // does the client have a party?
                    if (p != null) { // Then warp his party in. We've checked for leader already.

                        for (MaplePartyCharacter mpc : p.getMembers()) {
                            if (mpc.isOnline() && mpc.getChannel() == ch.getClient().getChannel() && mpc.getMapId() == ch.getMapId()) {
                                this.warp(mpc.getPlayer(), mapId, "out00");
                            }
                        }
                    } else {
                        this.warp(mapId, "out00");
                    }
                    return true;
                }
            }
            playerMessage(5, "All the Mini Dungeons are currently occupied. Please try again later.");
            return false;
        } else {
            if (p != null && p.getLeader().getId() == getPlayer().getId()) { // Then warp his party out.

                for (MaplePartyCharacter mpc : p.getMembers()) {
                    if (mpc.isOnline() && mpc.getChannel() == ch.getClient().getChannel() && mpc.getMapId() == ch.getMapId()) {
                        this.warp(mpc.getPlayer(), fromMap, "MD00");
                    }
                }
            } else {
                this.warp(fromMap, "MD00");
            }
            return true;
        }
    }
}