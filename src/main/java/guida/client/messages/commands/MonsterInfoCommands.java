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

package guida.client.messages.commands;

import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.server.life.MapleMonster;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapObject;

import java.util.List;

public class MonsterInfoCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted[0].equals("!killall") || splitted[0].equals("!monsterdebug") || splitted[0].equals("!killalldrops")) {
            String mapMessage = "";
            MapleMap map = c.getPlayer().getMap();
            List<MapleMapObject> monsters = map.getAllMonsters();
            boolean kill = splitted[0].startsWith("!killall");
            boolean killdrops = splitted[0].endsWith("drops");
            for (MapleMapObject monstermo : monsters) {
                MapleMonster monster = (MapleMonster) monstermo;
                if (kill) {
                    map.killMonster(monster, c.getPlayer(), killdrops);
                } else {
                    mc.dropMessage("Monster " + monster.toString());
                }
            }
            if (kill) {
                mc.dropMessage("Killed " + monsters.size() + " monsters" + mapMessage + ".");
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("killall", "", "Kills all monsters", 4),
                new CommandDefinition("killalldrops", "", "Kills all monsters with drops", 4),
                new CommandDefinition("monsterdebug", "", "", 4)
        };
    }
}