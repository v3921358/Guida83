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
import guida.client.messages.IllegalCommandSyntaxException;
import guida.client.messages.MessageCallback;
import guida.database.DatabaseConnection;
import guida.net.channel.ChannelServer;
import guida.tools.MaplePacketCreator;
import guida.tools.ReadableMillisecondFormat;
import guida.tools.StringUtil;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Collection;

import static guida.client.messages.CommandProcessor.getNamedIntArg;
import static guida.client.messages.CommandProcessor.joinAfterString;

public class BanningCommands implements Command {

    private static final String[] greason = {"This account has been blocked or deleted", "Hacking", "Botting", "Advertising", "Harrassment", "Cursing", "Scamming", "Misconduct", "Illegal Charging (NX Cash)", "Illegal Charging/Funding", "Requested", "Impersonating a GM", "Using Illegal Programs", "Cursing, Scamming or Illegal Trading over a Megaphone"};

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        ChannelServer cserv = ChannelServer.getInstance(1);
        switch (splitted[0]) {
            case "!ban":
                if (splitted.length < 3) {
                    mc.dropMessage("!ban <Character Name> <Reason>");
                } else {
                    String playerName = splitted[2];
                    if (splitted[1].compareTo("-h") != 0) {
                        playerName = splitted[1];
                    }
                    MapleCharacter target = null;
                    Collection<ChannelServer> cservs = ChannelServer.getAllInstances();
                    for (ChannelServer cserver : cservs) {
                        target = cserver.getPlayerStorage().getCharacterByName(playerName);
                        if (target != null) {
                            playerName = target.getName();
                            break;
                        }
                    }
                    String originalReason = StringUtil.joinStringFrom(splitted, 3);
                    if (splitted[1].compareTo("-h") != 0) {
                        originalReason = StringUtil.joinStringFrom(splitted, 2);
                    }
                    String reason = c.getPlayer().getName() + " banned " + playerName + ": " + originalReason;
                    if (target != null) {
                        if (!target.hasGMLevel(c.getPlayer().getGMLevel())) {
                            String readableTargetName = MapleCharacterUtil.makeMapleReadable(target.getName());
                            reason += " (IP: " + target.getClient().getIP() + ")";
                            target.ban(reason);

                            if (splitted[1].compareTo("-h") != 0) {
                                try {
                                    cserv.getWorldInterface().broadcastMessage(null, MaplePacketCreator.serverNotice(6, readableTargetName + " has been permanently banned for " + originalReason + ".").getBytes());
                                } catch (RemoteException e) {
                                    cserv.reconnectWorld();
                                }
                            } else {
                                mc.dropMessage("[Hidden] " + readableTargetName + " has been permanently banned for " + originalReason + ".");
                            }
                            mc.dropMessage(readableTargetName + "'s IP is " + target.getClient().getIP() + ".");
                        } else {
                            mc.dropMessage("You can not ban anyone with a GM level greater than or equal to yours.");
                        }
                    } else {
                        int accountid;
                        int status = 0;
                        try {
                            Connection con = DatabaseConnection.getConnection();
                            PreparedStatement ps = con.prepareStatement("SELECT accountid, name, gm FROM characters WHERE name = ?");
                            ps.setString(1, playerName);
                            ResultSet rs = ps.executeQuery();
                            if (rs.next()) {
                                accountid = rs.getInt("accountid");
                                playerName = rs.getString("name");
                                if (rs.getInt("gm") >= c.getPlayer().getGMLevel()) {
                                    status = 2;
                                }
                                PreparedStatement psb = con.prepareStatement("SELECT banned FROM accounts WHERE id = ?");
                                psb.setInt(1, accountid);
                                ResultSet rsb = psb.executeQuery();
                                rsb.next();
                                if (rsb.getInt("banned") == 1) {
                                    status = 1;
                                }
                                rsb.close();
                                psb.close();
                            } else {
                                status = -1;
                            }
                            rs.close();
                            ps.close();
                        } catch (SQLException e) {
                            e.printStackTrace();
                        }
                        if (status != 0) {
                            if (status == 1) {
                                mc.dropMessage(playerName + "'s account is already banned.");
                            } else if (status == -1) {
                                mc.dropMessage("Player '" + playerName + "' does not exist.");
                            } else {
                                mc.dropMessage("Player has a GM Level that is greater than or equal to yours!");
                            }
                            return;
                        }
                        if (MapleCharacter.ban(playerName, reason, false)) {
                            mc.dropMessage(playerName + "'s account has been offline banned.");
                            if (splitted[1].compareTo("-h") != 0) {
                                try {
                                    cserv.getWorldInterface().broadcastMessage(c.getPlayer().getName(), MaplePacketCreator.serverNotice(6, playerName + " has been permanently banned for " + originalReason + ".").getBytes());
                                } catch (RemoteException e) {
                                    cserv.reconnectWorld();
                                }
                            } else {
                                mc.dropMessage("[Hidden] " + playerName + " has been permanently banned for " + originalReason + ".");
                            }
                        }
                    }
                }
                break;
            case "!tempban": {
                Calendar tempB = Calendar.getInstance();
                String originalReason = joinAfterString(splitted, ":");

                if (splitted.length < 4 || originalReason == null) {
                    throw new IllegalCommandSyntaxException(4);
                }

                int yChange = getNamedIntArg(splitted, 1, "y", 0);
                int mChange = getNamedIntArg(splitted, 1, "m", 0);
                int wChange = getNamedIntArg(splitted, 1, "w", 0);
                int dChange = getNamedIntArg(splitted, 1, "d", 0);
                int hChange = getNamedIntArg(splitted, 1, "h", 0);
                int iChange = getNamedIntArg(splitted, 1, "i", 0);
                int gReason = getNamedIntArg(splitted, 1, "r", 7);

                String reason = c.getPlayer().getName() + " tempbanned " + splitted[1] + ": " + originalReason;

                if (gReason > 14) {
                    mc.dropMessage("You have entered an incorrect ban reason ID, please try again.");
                    return;
                }

                DateFormat df = DateFormat.getInstance();
                tempB.set(tempB.get(Calendar.YEAR) + yChange, tempB.get(Calendar.MONTH) + mChange, tempB.get(Calendar.DATE) + wChange * 7 + dChange, tempB.get(Calendar.HOUR_OF_DAY) + hChange, tempB.get(Calendar.MINUTE) + iChange);

                cserv = c.getChannelServer();
                MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);

                if (victim == null) {
                    if (c.getPlayer().getGMLevel() <= MapleCharacter.getGMLevelByCharName(splitted[1])) {
                        mc.dropMessage("Player has a GM Level greater than or equal to yours.");
                        return;
                    }
                    int accId = MapleClient.getAccIdFromCharName(splitted[1]);
                    if (accId >= 0 && MapleCharacter.tempban(reason, tempB, gReason, accId)) {
                        cserv.getWorldInterface().broadcastMessage(c.getPlayer().getName(), MaplePacketCreator.serverNotice(6, "The character " + splitted[1] + " has been temporarily banned until " + df.format(tempB.getTime()) + " for: " + originalReason).getBytes());
                    } else {
                        mc.dropMessage("There was a problem offline banning character " + splitted[1] + ".");
                    }
                } else {
                    if (victim.hasGMLevel(c.getPlayer().getGMLevel())) {
                        mc.dropMessage("Player has a GM Level greater than or equal to yours.");
                        return;
                    }
                    victim.tempban(reason, tempB, gReason);
                    cserv.getWorldInterface().broadcastMessage(c.getPlayer().getName(), MaplePacketCreator.serverNotice(6, "The character " + splitted[1] + " has been temporarily banned until " + df.format(tempB.getTime()) + " for: " + originalReason).getBytes());
                }
                break;
            }
            case "!dc":
                if (splitted.length < 2) {
                    mc.dropMessage("!dc [-f] <Character Name>");
                } else {
                    cserv = c.getChannelServer();
                    int level = 0;
                    MapleCharacter victim;
                    if (splitted[1].charAt(0) == '-') {
                        level = StringUtil.countCharacters(splitted[1], 'f');
                        victim = cserv.getPlayerStorage().getCharacterByName(splitted[2]);
                    } else {
                        victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
                    }
                    switch (level) {
                        case 0 -> victim.getClient().disconnect();
                        case 1 -> victim.getClient().disconnect(true);
                        default -> mc.dropMessage("Please use dc -f instead.");
                    }
                }
                break;
            case "!unban":
                if (splitted.length < 2) {
                    mc.dropMessage("!unban <Character Name>");
                } else {
                    String playerName = splitted[1];
                    int accountid;
                    try {
                        Connection con = DatabaseConnection.getConnection();
                        PreparedStatement ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
                        ps.setString(1, playerName);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            accountid = rs.getInt("accountid");
                            PreparedStatement psb = con.prepareStatement("SELECT banned, tempban FROM accounts WHERE id = ?");
                            psb.setInt(1, accountid);
                            ResultSet rsb = psb.executeQuery();
                            rsb.next();
                            if (rsb.getInt("banned") != 1 && rsb.getLong("tempban") == 0) {
                                rs.close();
                                ps.close();
                                rsb.close();
                                psb.close();
                                mc.dropMessage(playerName + " account is not banned.");
                                return;
                            }
                            rsb.close();
                            psb.close();
                            psb = con.prepareStatement("UPDATE accounts SET banned = -1, banreason = null, tempban = '0000-00-00 00:00:00', greason = null WHERE id = ?");
                            psb.setInt(1, accountid);
                            psb.executeUpdate();
                            psb.close();
                            mc.dropMessage(playerName + "'s account has been successfully unbanned.");
                        } else {
                            mc.dropMessage(playerName + " does not exist!");
                        }
                        rs.close();
                        ps.close();
                    } catch (SQLException e) {
                        System.out.println("SQL Exception: " + e);
                    }
                }
                break;
            case "!uijail": {
                String vic = splitted[1];
                boolean active = splitted[2].equalsIgnoreCase("on");

                MapleCharacter victim = c.getChannelServer().getPlayerStorage().getCharacterByName(vic);
                if ((victim == null || victim.getGMLevel() >= c.getPlayer().getGMLevel()) && victim != c.getPlayer()) {
                    mc.dropMessage("Invalid character name!");
                    return;
                }
                if (victim == c.getPlayer() && splitted.length < 4) {
                    mc.dropMessage("WARNING: Using UIJail on self. Add a 3rd parameter to this command to confirm!");
                    return;
                } else if (victim == c.getPlayer()) {
                    c.getChannelServer().getWorldInterface().broadcastGMMessage(c.getPlayer().getName(), MaplePacketCreator.serverNotice(6, "[UIJail] " + c.getPlayer().getName() + " has used UIJail on self.").getBytes());
                }
                victim.getClient().sendPacket(MaplePacketCreator.hideUI(active));
                victim.getClient().sendPacket(MaplePacketCreator.lockWindows(active));
                break;
            }
            case "!getban": {
                if (splitted.length < 2) {
                    mc.dropMessage("Syntax Error : !getban <char name>");
                    return;
                }
                Connection con = DatabaseConnection.getConnection();
                PreparedStatement ps = null;
                ResultSet rs = null;
                try {
                    ps = con.prepareStatement("SELECT accountid FROM characters WHERE name LIKE ?");
                    ps.setString(1, splitted[1]);
                    rs = ps.executeQuery();
                    int accid = rs.getInt("accountid");
                    ps = con.prepareStatement("SELECT * FROM accounts WHERE accountid = ?");
                    ps.setInt(1, accid);
                    rs = ps.executeQuery();
                    boolean banned = rs.getInt("banned") > 0;
                    boolean tempban = rs.getTimestamp("tempban").getTime() > System.currentTimeMillis();
                    if (!banned && !tempban) {
                        mc.dropMessage("Character is not banned.");
                        rs.close();
                        ps.close();
                        return;
                    }
                    StringBuilder ban = new StringBuilder("[Type of ban : ");
                    if (banned) {
                        ban.append("Permanent]");
                    } else {
                        ban.append("Temporary]");
                    }
                    if (tempban) {
                        ban.append(" [Banned Until: ").append(rs.getTimestamp("tempban").toString()).append("] ");
                    }
                    ban.append("[Reason: ").append(rs.getInt("banreason")).append("] ");
                    if (tempban) {
                        ban.append("[Tempban Reason: ").append(greason[rs.getInt("greason")]).append("(").append(rs.getInt("greason")).append(")] ");
                    }
                    ban.append("[Time until unbanned: ");
                    if (banned) {
                        ban.append("NEVER]");
                    } else {
                        String time = new ReadableMillisecondFormat(System.currentTimeMillis() - rs.getTimestamp("tempban").getTime()).toString();
                        ban.append(time).append("]");
                    }
                    rs.close();
                    ps.close();
                    mc.dropMessage(ban.toString());
                } catch (SQLException sqe) {
                    mc.dropMessage("An error occured.");
                } finally {
                    if (rs != null) {
                        rs.close();
                    }
                    if (ps != null) {
                        ps.close();
                    }
                }
                break;
            }
            case "!unbanip": {
                Connection con = DatabaseConnection.getConnection();
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM ipbans WHERE ip = ?")) {
                    ps.setString(1, splitted[1]);
                    ps.executeUpdate();
                    ps.close();
                    mc.dropMessage("IP : " + splitted[1] + " is now unbanned");
                } catch (SQLException e) {
                    mc.dropMessage("An error occured");
                }
                break;
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("ban", "charname reason", "Permanently ip, mac and account ban the given character", 4),
                new CommandDefinition("tempban", "<name> [i / m / w / d / h] <amount> [r  [reason id] : Text Reason", "Tempbans the given account", 1),
                new CommandDefinition("dc", "[-f] name", "Disconnects player matching name provided. Use -f only if player is persistent!", 4),
                new CommandDefinition("unban", "<character name>", "Unbans the character's account", 4),
                new CommandDefinition("uijail", "<character name> <on/off>", "Disables/enables the character's UI. (on = disable)", 4),
                new CommandDefinition("getban", "<character name>", "Gets ban information about the character", 4),
                new CommandDefinition("unbanip", "<ip>", "Unbans the ip", 10)
        };
    }
}