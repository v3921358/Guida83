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

import guida.client.MapleClient;
import guida.net.AbstractMaplePacketHandler;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.awt.Point;

public class MonsterCarnivalHandler extends AbstractMaplePacketHandler {

    private static int rand(int lbound, int ubound) {
        return (int) (Math.random() * (ubound - lbound + 1) + lbound);
    }

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        int tab = slea.readByte();
        int num = slea.readByte();
        c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.playerSummoned(c.getPlayer().getName(), tab, num));
        if (tab == 0) { // only spawning for now..
            MapleMonster mob = MapleLifeFactory.getMonster(getMonsterIdByNum(num));
            c.getPlayer().getMap().spawnMonsterOnGroundBelow(mob, randomizePosition(c.getPlayer().getMapId(), 1));
        }
    }

    public Point randomizePosition(int mapid, int team) {
        int posx = 0;
        int posy = 0;
        if (mapid == 980000301) { //room 3 iirc
            posy = 162;
            if (team == 0) { //maple red goes left
                posx = rand(-1554, -151);
            } else { //maple blue goes right
                posx = rand(148, 1571);
            }
        }
        return new Point(posx, posy);
    }

    public int getMonsterIdByNum(int num) {
        /*
		 *  1 - Brown Teddy - 3000005
		2 - Bloctopus - 3230302
		3 - Ratz - 3110102
		4 - Chronos - 3230306
		5 - Toy Trojan - 3230305
		6 - Tick-Tock - 4230113
		7 - Robo - 4230111
		8 - King Bloctopus - 3230103
		9 - Master Chronos - 4230115
		10 - Rombot - 4130103
		 * */
        int mid = 0;
        num++; //whatever, don't wanna change all the cases XD

        mid = switch (num) {
            case 1 -> 3000005;
            case 2 -> 3230302;
            case 3 -> 3110102;
            case 4 -> 3230306;
            case 5 -> 3230305;
            case 6 -> 4230113;
            case 7 -> 4230111;
            case 8 -> 3230103;
            case 9 -> 4230115;
            case 10 -> 4130103;
//LOL slime.. w/e, shouldn't happen
            default -> 210100;
        };
        return mid;
    }
}