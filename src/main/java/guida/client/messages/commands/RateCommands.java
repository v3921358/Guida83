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
import guida.net.channel.ChannelServer;
import guida.server.TimerManager;
import guida.tools.MaplePacketCreator;

public class RateCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        switch (splitted[0]) {
            case "!mesorate":
            case "!droprate":
            case "!bossdroprate":
            case "!exprate":
                mc.dropMessage("That command is deprecated, please use !rate");
                break;
            case "!rate":
                if (splitted.length > 2) {
                    int arg = Integer.parseInt(splitted[2]);
                    int seconds = Integer.parseInt(splitted[3]);
                    int mins = Integer.parseInt(splitted[4]);
                    int hours = Integer.parseInt(splitted[5]);
                    int time = seconds + mins * 60 + hours * 60 * 60;
                    boolean bOk = true;
                    for (final ChannelServer cservs : ChannelServer.getAllInstances()) {
                        switch (splitted[1]) {
                            case "exp":
                                if (arg <= 64) {
                                    cservs.setExpRate(arg);
                                    cservs.broadcastPacket(MaplePacketCreator.serverNotice(6, "Exp rate has been changed to " + arg + "x. Enjoy!"));
                                } else {
                                    mc.dropMessage("You cannot raise the exp. rate above 64.");
                                }
                                break;
                            case "drop":
                                if (arg <= 16) {
                                    cservs.setDropRate(arg);
                                    cservs.broadcastPacket(MaplePacketCreator.serverNotice(6, "Drop rate has been changed to " + arg + "x. Enjoy!"));
                                } else {
                                    mc.dropMessage("You cannot raise the drop rate above 16.");
                                }
                                break;
                            case "meso":
                                if (arg <= 16) {
                                    cservs.setMesoRate(arg);
                                    cservs.broadcastPacket(MaplePacketCreator.serverNotice(6, "Meso rate has been changed to " + arg + "x. Enjoy!"));
                                } else {
                                    mc.dropMessage("You cannot raise the meso rate above 16.");
                                }
                                break;
                            case "bossdrop":
                                if (arg <= 16) {
                                    cservs.setBossDropRate(arg);
                                    cservs.broadcastPacket(MaplePacketCreator.serverNotice(6, "Bossdrop rate has been changed to " + arg + "x. Enjoy!"));
                                } else {
                                    mc.dropMessage("You cannot raise the boss drop rate above 16.");
                                }
                                break;
                            case "petexp":
                                if (arg <= 16) {
                                    cservs.setPetExpRate(arg);
                                    cservs.broadcastPacket(MaplePacketCreator.serverNotice(6, "Pet exp rate has been changed to " + arg + "x. Enjoy!"));
                                } else {
                                    mc.dropMessage("You cannot raise the pet exp. rate above 16.");
                                }
                                break;
                            default:
                                bOk = false;
                                break;
                        }
                        final String rate = splitted[1];
                        TimerManager.getInstance().schedule(() -> {
                            switch (rate) {
                                case "exp":
                                    cservs.setExpRate(16);
                                    break;
                                case "drop":
                                    cservs.setDropRate(4);
                                    break;
                                case "meso":
                                    cservs.setMesoRate(4);
                                    break;
                                case "bossdrop":
                                    cservs.setBossDropRate(4);
                                    break;
                                case "petexp":
                                    cservs.setPetExpRate(4);
                                    break;
                            }
                            cservs.broadcastPacket(MaplePacketCreator.serverNotice(6, "The " + rate + " rate has been changed to " + (rate.equals("exp") ? "16" : "4") + "x. Enjoy!"));
                        }, time * 1000);
                    }
                    if (!bOk) {
                        mc.dropMessage("Syntax: !rate <exp|drop|meso|boss|pet> <amount> <seconds> <minutes> <hours>");
                    }
                } else {
                    mc.dropMessage("Syntax: !rate <exp|drop|meso|boss|pet> <amount> <seconds> <minutes> <hours>");
                }
                break;
            case "!rates":
                ChannelServer cserv = c.getChannelServer();
                mc.dropMessage("The current rates are:");
                mc.dropMessage("Experience: " + cserv.getExpRate() + "x | Pet Experience: " + cserv.getPetExpRate() + "x");
                mc.dropMessage("Drop: " + cserv.getDropRate() + "x | Boss Drop: " + cserv.getBossDropRate() + "x");
                mc.dropMessage("Meso: " + cserv.getMesoRate() + "x");
                break;
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("rate", "<exp|drop|meso|bossdrop|petexp> <amount> <seconds> <minutes> <hours>", "Changes the specified rate", 4),
                new CommandDefinition("rates", "", "Shows each rate", 4)
        };
    }
}