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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.server.maps.MapleMap;

import java.util.HashMap;

public class GotoCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted.length < 2) {
            mc.dropMessage("Syntax: '!goto <mapname>' - or '!goto locations' for a list of map names.");
        } else {
            HashMap<String, Integer> gotomaps = new HashMap<>();
            gotomaps.put("gmmap", 180000000);
            gotomaps.put("gmmap2", 180000001);
            gotomaps.put("southperry", 2000000);
            gotomaps.put("amherst", 1000000);
            gotomaps.put("henesys", 100000000);
            gotomaps.put("ellinia", 101000000);
            gotomaps.put("perion", 102000000);
            gotomaps.put("kerningcity", 103000000);
            gotomaps.put("lithharbor", 104000000);
            gotomaps.put("sleepywood", 105040300);
            gotomaps.put("florinabeach", 110000000);
            gotomaps.put("orbis", 200000000);
            gotomaps.put("happyville", 209000000);
            gotomaps.put("elnath", 211000000);
            gotomaps.put("ludibrium", 220000000);
            gotomaps.put("aquaroad", 230000000);
            gotomaps.put("leafre", 240000000);
            gotomaps.put("mulung", 250000000);
            gotomaps.put("herbtown", 251000000);
            gotomaps.put("omegasector", 221000000);
            gotomaps.put("koreanfolktown", 222000000);
            gotomaps.put("newleafcity", 600000000);
            gotomaps.put("sharenian", 990000000);
            gotomaps.put("pianus", 230040420);
            gotomaps.put("horntail", 240060200);
            gotomaps.put("mushmom", 100000005);
            gotomaps.put("griffey", 240020101);
            gotomaps.put("manon", 240020401);
            gotomaps.put("jrbalrog", 105090900);
            gotomaps.put("zakum", 280030000);
            gotomaps.put("papulatus", 220080001);
            gotomaps.put("showatown", 801000000);
            gotomaps.put("guildhq", 200000301);
            gotomaps.put("mushroomshrine", 800000000);
            gotomaps.put("freemarket", 910000000);
            gotomaps.put("ariant", 260000000);
            gotomaps.put("nautilus", 120000000);
            gotomaps.put("singapore", 540000000);
            gotomaps.put("amoria", 680000000);
            gotomaps.put("zombiemushmom", 105070002);
            gotomaps.put("ereve", 130000200);
            gotomaps.put("rien", 140000000);

            if (gotomaps.containsKey(splitted[1].toLowerCase())) {
                MapleMap target = c.getChannelServer().getMapFactory().getMap(gotomaps.get(splitted[1]));
                if (splitted.length < 3) {
                    c.getPlayer().changeMap(target, target.getRandomSpawnPoint());
                } else {
                    MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[2]);
                    if (victim != null) {
                        victim.changeMap(target, target.getRandomSpawnPoint());
                    } else {
                        mc.dropMessage("Character with name " + splitted[2] + " not found.");
                    }
                }
            } else {
                if (splitted[1].equals("locations")) {
                    mc.dropMessage("Use !goto <location>. Locations are as follows: ");
                    StringBuilder sb = new StringBuilder();
                    for (String s : gotomaps.keySet()) {
                        sb.append(s).append(", ");
                    }
                    mc.dropMessage(sb.substring(0, sb.length() - 2));
                } else {
                    mc.dropMessage("Invalid command syntax - Use !goto <location>. For a list of locations, use !goto locations.");
                }
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("goto", "?", "go <town/map name> <player> (player is optional)", 4)
        };
    }
}