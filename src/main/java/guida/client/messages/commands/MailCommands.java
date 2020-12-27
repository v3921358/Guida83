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
import guida.net.channel.ChannelServer;
import guida.net.world.guild.MapleGuildCharacter;
import guida.tools.MaplePacketCreator;
import guida.tools.StringUtil;

import java.sql.SQLException;
import java.util.Collection;

public class MailCommands implements Command {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MailCommands.class);

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted[0].equalsIgnoreCase("@mail")) {
            int cost = 20000;
            if (splitted.length < 2) {
                mc.dropMessage("[GuidaMail] To use the Mail System:");
                mc.dropMessage("[GuidaMail] Use '@mail send <recipient name> <message>' to send a message.");
                mc.dropMessage("[GuidaMail] Use '@mail inbox' to check your received messages.");
            } else {
                if (splitted[1].equalsIgnoreCase("send")) {
                    if (splitted.length < 3) {
                        mc.dropMessage("[GuidaMail] Please send a mail in the following format: '@mail send <recipient> <message>'");
                    } else if (c.getPlayer().getMeso() < cost) {
                        mc.dropMessage("[GuidaMail] You don't have enough mesos. Delivery charge is 5,000 mesos per message.");
                    } else {
                        int accName = MapleClient.getAccIdFromCharName(splitted[2]);
                        String message = StringUtil.joinStringFrom(splitted, 3);
                        message = "[GuidaMail] " + message + "";
                        String recipient = splitted[2];
                        if (accName >= 0) {
                            c.getPlayer().sendNote(recipient, message);
                            c.getPlayer().gainMeso(-cost, true);
                            mc.dropMessage("[GuidaMail] Your GuidaMail has been successfully sent to " + recipient + ".");
                        } else {
                            mc.dropMessage("[GuidaMail] The player '" + recipient + "' does not exist. GuidaMail not sent.");
                        }
                    }
                } else if (splitted[1].equalsIgnoreCase("inbox")) {
                    c.getPlayer().showNote();
                    mc.dropMessage("[Mail] Welcome to your GuidaMail inbox!");
                } else {
                    mc.dropMessage("[Mail] " + splitted[1] + " is not a valid function for GuidaMail.");
                }
            }
        } else if (splitted[0].equalsIgnoreCase("@mailguild")) {
            if (c.getPlayer().getGuildId() > 0) {
                if (c.getPlayer().getGuildRank() == 1 || c.getPlayer().getGuildRank() == 2) {
                    if (splitted.length < 2) {
                        mc.dropMessage("[GuidaMail] Please use the following syntax: @mailguild <message>");
                    } else {
                        Collection<MapleGuildCharacter> members = c.getPlayer().getGuild().getMembers();
                        int cost = (members.size() - 1) * 2500;
                        if (members.size() < 2) {
                            mc.dropMessage("[GuidaMail] Sorry, but you don't have anyone in your guild to send the message to.");
                        } else if (c.getPlayer().getMeso() < cost) {
                            mc.dropMessage("[GuidaMail] Sorry, but you don't have enough mesos. You need " + cost + " mesos in total - 2,500 per guild member, excluding yourself.");
                        } else {
                            String sender = c.getPlayer().getName();
                            String message = StringUtil.joinStringFrom(splitted, 1);
                            for (MapleGuildCharacter mgc : members) {
                                String recipient = mgc.getName();
                                if (sender.equals(recipient)) {
                                    continue;
                                }
                                c.getPlayer().sendNote(recipient, message);
                            }
                            c.getPlayer().gainMeso(-cost, false);
                            mc.dropMessage("[GuidaMail] Message sent to the entire guild, at a cost of " + cost + " mesos.");
                        }
                    }
                } else {
                    mc.dropMessage("[GuidaMail] Sorry, only Guild Masters and Jr. Masters may send the entire guild a message.");
                }
            } else {
                mc.dropMessage("[GuidaMail] Sorry, but you don't seem to be in a guild.");
            }
        } else if (splitted[0].equals("!mailall")) {
            if (splitted.length >= 1) {
                try {
                    String text = StringUtil.joinStringFrom(splitted, 1);
                    ChannelServer cserv = c.getChannelServer();
                    for (MapleCharacter mch : cserv.getPlayerStorage().getAllCharacters()) {
                        MaplePacketCreator.sendUnkwnNote(mch.getName(), text, c.getPlayer().getName());
                    }
                } catch (SQLException e) {
                    log.error("SAVING NOTE", e);
                }
            } else {
                mc.dropMessage("[GuidaMail] Use it like this, !mailall <text>");
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("mail", "", "GuidaMail system.", 0),
                new CommandDefinition("mailguild", "", "GuidaMail to guild system.", 0),
                new CommandDefinition("mailall", "", "Sends note to all users", 4)
        };
    }
}