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
import guida.client.MapleCharacterUtil;
import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.net.channel.ChannelServer;
import guida.tools.ReadableMillisecondFormat;

import java.lang.management.ManagementFactory;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Map;

public class OnlineCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted[0].toLowerCase().equals("!online")) {
            mc.dropMessage("Characters Online: ");
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                mc.dropMessage("[Channel " + cs.getChannel() + "]");
                StringBuilder sb = new StringBuilder();
                Collection<MapleCharacter> cmc = cs.getPlayerStorage().getAllCharacters();
                for (MapleCharacter chr : cmc) {
                    if (sb.length() > 150) {
                        sb.setLength(sb.length() - 2);
                        mc.dropMessage(sb.toString());
                        sb = new StringBuilder();
                    }
                    if (!chr.isGM()) {
                        sb.append(MapleCharacterUtil.makeMapleReadable(chr.getName()));
                        sb.append(", ");
                    }
                }
                if (sb.length() >= 2) {
                    sb.setLength(sb.length() - 2);
                    mc.dropMessage(sb.toString());
                }
            }
        } else if (splitted[0].equalsIgnoreCase("@channel")) {
            mc.dropMessage("Characters Online: ");
            mc.dropMessage("[Channel " + c.getChannel() + "]");
            StringBuilder sb = new StringBuilder();
            Collection<MapleCharacter> cmc = c.getChannelServer().getPlayerStorage().getAllCharacters();
            for (MapleCharacter chr : cmc) {
                if (sb.length() > 150) {
                    sb.setLength(sb.length() - 2);
                    mc.dropMessage(sb.toString());
                    sb = new StringBuilder();
                }
                if (!chr.isGM()) {
                    sb.append(MapleCharacterUtil.makeMapleReadable(chr.getName()));
                    sb.append(", ");
                }
            }
            if (sb.length() >= 2) {
                sb.setLength(sb.length() - 2);
            }
            mc.dropMessage(sb.toString());
        } else if (splitted[0].equalsIgnoreCase("!gmsonline")) {
            try {
                mc.dropMessage("Game Masters Online: " + ChannelServer.getInstance(1).getWorldInterface().listGMs());
            } catch (RemoteException re) {
                c.getChannelServer().reconnectWorld();
            }
        } else if (splitted[0].equalsIgnoreCase("!connected")) {
            try {
                Map<Integer, Integer> connected = ChannelServer.getInstance(1).getWorldInterface().getConnected();
                StringBuilder conStr = new StringBuilder("Connected Clients: ");
                boolean first = true;
                for (int i : connected.keySet()) {
                    if (!first) {
                        conStr.append(", ");
                    } else {
                        first = false;
                    }
                    if (i == 0) {
                        conStr.append("Total: ");
                    } else {
                        conStr.append("Channel ");
                        conStr.append(i);
                        conStr.append(": ");
                    }
                    conStr.append(connected.get(i));
                }
                mc.dropMessage(conStr.toString());
            } catch (RemoteException e) {
                ChannelServer.getInstance(1).reconnectWorld();
            }
        } else if (splitted[0].equalsIgnoreCase("@uptime")) {
            mc.dropMessage("The server has been running for " + new ReadableMillisecondFormat(ManagementFactory.getRuntimeMXBean().getUptime()).toString() + ".");
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("online", "", "List all of the users on the server, organized by channel.", 4),
                new CommandDefinition("channel", "", "List all characters online on a channel.", 0),
                new CommandDefinition("gmsonline", "", "Shows the name of every GM that is online", 1),
                new CommandDefinition("connected", "", "Shows how many players are connected on each channel", 4),
                new CommandDefinition("uptime", "", "Shows how long the server has been running", 0)
        };
    }
}