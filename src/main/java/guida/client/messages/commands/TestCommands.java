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
import guida.net.MaplePacket;
import guida.tools.MaplePacketCreator;
import guida.tools.StringUtil;

import java.rmi.RemoteException;

public class TestCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        /*if (splitted[0].equals("!test")) {
            // faeks id is 30000 (30 75 00 00)
            // MapleCharacter faek = ((MapleCharacter) c.getPlayer().getMap().getMapObject(30000));
            // c.sendPacket(MaplePacketCreator.getPacketFromHexString("2B 00 14 30 C0 23 00 00 11 00 00 00"));
        } else*/
        if (splitted[0].equals("!packet")) {
            if (splitted.length > 1) {
                switch (splitted[1]) {
                    case "m" -> c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getPacketFromHexString(StringUtil.joinStringFrom(splitted, 2)));
                    case "p" -> {
                        MapleCharacter cx = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[2]);
                        if (cx == null) {
                            mc.dropMessage(splitted[2] + " is not online or in your channel.");
                            return;
                        }
                        cx.getClient().sendPacket(MaplePacketCreator.getPacketFromHexString(StringUtil.joinStringFrom(splitted, 3)));
                    }
                    default -> c.sendPacket(MaplePacketCreator.getPacketFromHexString(StringUtil.joinStringFrom(splitted, 1)));
                }
            } else {
                mc.dropMessage("Please enter packet data!");
            }
        } else if (splitted[0].equalsIgnoreCase("!invismonster")) {
            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.makeMonsterInvisible(c.getPlayer().getMap().getMonsterByOid(Integer.parseInt(splitted[1]))));
        } else if (splitted[0].equalsIgnoreCase("!gimmecp")) {
            if (splitted.length > 1) {
                c.getPlayer().gainCP(Integer.parseInt(splitted[1]));
            }
        } else if (splitted[0].equalsIgnoreCase("!playerdied")) {
            if (splitted.length > 2) {
                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.playerDiedMessage(splitted[1], Integer.parseInt(splitted[2]), c.getPlayer().getTeam()));
            }
        } else if (splitted[0].equalsIgnoreCase("!playersummoned")) {
            if (splitted.length > 3) {
                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.playerSummoned(splitted[1], Integer.parseInt(splitted[2]), Integer.parseInt(splitted[3])));
            }
        } else if (splitted[0].equalsIgnoreCase("!curpos")) {
            mc.dropMessage("Current position: " + c.getPlayer().getPosition().getX() + ", " + c.getPlayer().getPosition().getY());
        } else if (splitted[0].equalsIgnoreCase("!curteam")) {
            mc.dropMessage("Team: " + c.getPlayer().getTeam());
        } else if (splitted[0].equals("!mapletip")) {
            try {
                MaplePacket packet = MaplePacketCreator.sendMapleTip("[Guida Tip] " + StringUtil.joinStringFrom(splitted, 1));
                c.getChannelServer().getWorldInterface().broadcastMessage(c.getPlayer().getName(), packet.getBytes());
            } catch (RemoteException e) {
                c.getChannelServer().reconnectWorld();
            }
        } else if (splitted[0].equals("!fakewarp")) {
            MapleCharacter ch = c.getChannelServer().getPlayerStorage().getCharacterByName(splitted[1]);
            if (ch == null) {
                mc.dropMessage(splitted[1] + " is not on your channel or is offline.");
                return;
            }
            ch.getClient().sendPacket(MaplePacketCreator.getWarpToMap(Integer.parseInt(splitted[2]), 0, ch));
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("test", "?", "Probably does something", 5),
                new CommandDefinition("packet", "hex data", "Shows a clock to everyone in the map", 5),
                new CommandDefinition("invismonster", "", "", 5),
                new CommandDefinition("gimmecp", "", "", 5),
                new CommandDefinition("playerdied", "", "", 5),
                new CommandDefinition("playersummoned", "", "", 5),
                new CommandDefinition("curpos", "", "", 5),
                new CommandDefinition("curteam", "", "", 5),
                new CommandDefinition("curteam", "", "", 5),
                new CommandDefinition("mapletip", "", "", 5),
                new CommandDefinition("fakewarp", "[chr name] [mapid]", "Makes the [chr name]'s client think they were warped to [mapid]. Note that this can cause [chr name] to seem like they're hacking!", 5),
        };
    }
}