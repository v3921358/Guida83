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
import guida.database.DatabaseConnection;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.net.world.remote.WorldLocation;
import guida.server.MaplePortal;
import guida.server.maps.MapleMap;
import guida.tools.MaplePacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class WarpCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        ChannelServer cserv = c.getChannelServer();
        if (splitted[0].equals("!warp")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            if (victim != null) {
                if (splitted.length == 2) {
                    MapleMap target = victim.getMap();
                    c.getPlayer().setHidden(true);
                    c.getPlayer().changeMap(target, target.findClosestSpawnPoint(victim.getPosition()));
                } else {
                    MapleMap target = c.getChannelServer().getMapFactory().getMap(Integer.parseInt(splitted[2]));
                    if (splitted.length >= 4) {
                        int channel = Integer.parseInt(splitted[3]);
                        if (channel < 1 || channel > ChannelServer.getAllInstances().size()) {
                            mc.dropMessage("Invalid channel id " + channel);
                            return;
                        }
                        victim.getMap().removePlayer(c.getPlayer());
                        victim.setMap(target);
                        MapleClient.changeChannel(victim.getClient(), channel, true);
                    } else {
                        victim.changeMap(target, target.getRandomSpawnPoint());
                    }
                }
            } else {
                try {
                    victim = c.getPlayer();
                    WorldLocation loc = c.getChannelServer().getWorldInterface().getLocation(splitted[1]);
                    if (loc != null) {
                        mc.dropMessage("You will be cross-channel warped. This may take a few seconds.");
                        MapleMap target = c.getChannelServer().getMapFactory().getMap(loc.map);
                        victim.getMap().removePlayer(victim);
                        victim.setMap(target);
                        MapleClient.changeChannel(victim.getClient(), loc.channel, true);
                    } else {
                        MapleMap target = cserv.getMapFactory().getMap(Integer.parseInt(splitted[1]));
                        victim.setHidden(true);
                        victim.changeMap(target, target.getRandomSpawnPoint());
                    }
                } catch (Exception e) {
                    mc.dropMessage("Something went wrong: " + e.getMessage());
                }
            }
        } else if (splitted[0].equals("!warphere")) {
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            if (victim != null) {
                victim.changeMap(c.getPlayer().getMap(), c.getPlayer().getMap().findClosestSpawnPoint(c.getPlayer().getPosition()));
            } else {
                mc.dropMessage("The character is either not on this channel or offline.");
            }
        } else if (splitted[0].equals("!map")) {
            if (splitted.length == 1) {
                mc.dropMessage("Syntax: !map <Map ID> <Portal ID>");
            } else {
                MapleMap target = null;
                try {
                    target = cserv.getMapFactory().getMap(Integer.parseInt(splitted[1]));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (target == null) {
                    mc.dropMessage("You have entered an incorrect Map ID.");
                } else {
                    MaplePortal targetPortal = null;
                    if (splitted.length > 2) {
                        try {
                            targetPortal = target.getPortal(Integer.parseInt(splitted[2]));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (targetPortal == null) {
                        targetPortal = target.getRandomSpawnPoint();
                    }
                    c.getPlayer().changeMap(target, targetPortal);
                }
            }
        } else if (splitted[0].equals("!warpall")) {
            int mapid = Integer.parseInt(splitted[1]);
            MapleMap map = c.getChannelServer().getMapFactory().getMap(mapid);
            MapleMap warpFrom = c.getPlayer().getMap();
            warpFrom.broadcastMessage(MaplePacketCreator.serverNotice(6, "You will now be warped to " + map.getStreetName() + " : " + map.getMapName()));
            for (MapleCharacter chr : warpFrom.getCharacters()) {
                chr.changeMap(map, map.getPortal(0));
            }
        } else if (splitted[0].equals("!offlinewarp")) {
            if (splitted.length < 3) {
                mc.dropMessage("Please include the name of the character and the id of the map you'd like to warp them to!");
                return;
            }
            MapleCharacter victim = cserv.getPlayerStorage().getCharacterByName(splitted[1]);
            if (victim != null) {
                mc.dropMessage(splitted[1] + " is online. Please use the normal functions!");
            } else {
                WorldLocation loc = c.getChannelServer().getWorldInterface().getLocation(splitted[1]);
                if (loc != null) {
                    mc.dropMessage(splitted[1] + " is online. Please use the normal functions!");
                } else {
                    int toMapId = Integer.parseInt(splitted[2]);
                    MapleMap toMap = c.getChannelServer().getMapFactory().getMap(toMapId);
                    if (toMap == null) {
                        mc.dropMessage("Map ID: " + toMapId + " does not exist");
                        return;
                    }
                    if (c.getPlayer().changeMapOffline(splitted[1], toMapId)) {
                        mc.dropMessage(splitted[1] + " has been warped.");
                    } else {
                        mc.dropMessage(splitted[1] + " was not warped. Please ensure you typed their name correctly.");
                    }
                }
            }
        } else if (splitted[0].equalsIgnoreCase("!warpalive") || splitted[0].equalsIgnoreCase("!warpdead")) {
            int mapid = Integer.parseInt(splitted[1]);
            MapleMap map = c.getChannelServer().getMapFactory().getMap(mapid);
            boolean alive = splitted[0].equalsIgnoreCase("!warpalive");
            String warpmsg = "You will now be warped to " + map.getStreetName() + " : " + map.getMapName();
            MaplePacket warppk = MaplePacketCreator.serverNotice(6, warpmsg);
            for (MapleCharacter warpies : c.getPlayer().getMap().getCharacters()) {
                if (alive && warpies.isAlive() && !warpies.isGM()) {
                    warpies.getClient().sendPacket(warppk);
                    warpies.changeMap(map, map.getPortal(0));
                } else if (!alive && !warpies.isAlive() && !warpies.isGM()) {
                    warpies.getClient().sendPacket(warppk);
                    warpies.changeMap(map, map.getPortal(0));
                }
            }
        } else if (splitted[0].equals("!warpid") || splitted[0].equals("!warpr")) {
            if (c.getPlayer().getGMLevel() == 1) {
                ChannelServer.getInstance(1).setFlagMap(c.getPlayer().getId(), c.getPlayer().getMapId());
            }
            boolean byId = splitted[0].equals("!warpid");
            if (byId && splitted.length < 2) {
                mc.dropMessage("Syntax Error : !warpid <report id>");
                return;
            }
            String charName = "";
            int rid = byId ? Integer.parseInt(splitted[1]) : -1;
            Connection con = DatabaseConnection.getConnection();
            String sql = "SELECT characters.name FROM reports LEFT JOIN characters ON reports.victimId = characters.id ORDER BY reports.id DESC LIMIT 1";
            if (byId) {
                sql = "SELECT characters.name FROM reports LEFT JOIN characters ON reports.victimId = characters.id WHERE reports.id = ?";
            }
            PreparedStatement ps = con.prepareStatement(sql);
            if (byId) {
                ps.setInt(1, rid);
            }
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                charName = rs.getString(1);
            } else {
                mc.dropMessage("Report ID doesn't exist.");
            }
            rs.close();
            ps.close();
            if (charName.length() >= 4) {
                execute(c, mc, new String[] {"!warp", charName});
            }
        } else if (splitted[0].equals("!flagmap")) {
            cserv = ChannelServer.getInstance(1);
            if (splitted.length > 1 && splitted[1].equalsIgnoreCase("set") && c.getPlayer().hasGMLevel(2)) {
                cserv.setFlagMap(c.getPlayer().getId(), c.getPlayer().getMapId());
            } else {
                if (cserv.getFlagMap(c.getPlayer().getId()) != -1) {
                    MapleMap to = c.getChannelServer().getMapFactory().getMap(cserv.getFlagMap(c.getPlayer().getId()));
                    c.getPlayer().changeMap(to, to.getRandomSpawnPoint());
                    if (c.getPlayer().getGMLevel() == 1) {
                        cserv.setFlagMap(c.getPlayer().getId(), -1);
                    }
                } else {
                    mc.dropMessage("No map was flagged with your character.");
                }
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("warp", "playername", "Warps yourself to the player", 4),
                new CommandDefinition("warphere", "playername", "Warps the player with the given name to yourself", 4),
                new CommandDefinition("offlinewarp", "playername mapid", "Warps a player whilst they are offline", 4),
                new CommandDefinition("map", "mapid", "Warps you to the given mapid (use /m instead)", 4),
                new CommandDefinition("warpall", "mapid", "Warps you and everyone in your map to the mapid specified", 4),
                new CommandDefinition("warpalive", "mapid", "Warps all alive people on your map to the designated map", 4),
                new CommandDefinition("warpdead", "mapid", "Warps all dead people on your map to the designated map", 4),
                new CommandDefinition("warpr", "", "Warps to the last reported player", 1),
                new CommandDefinition("warpid", "", "Warps to victim of that reportid", 1),
                new CommandDefinition("flagmap", "set", "Sets/warps to the currently stored map variable", 1)
        };
    }
}