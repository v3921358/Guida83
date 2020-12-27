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

import guida.client.BuddyList;
import guida.client.BuddyList.BuddyAddResult;
import guida.client.BuddyList.BuddyOperation;
import guida.client.BuddylistEntry;
import guida.client.CharacterNameAndId;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.database.DatabaseConnection;
import guida.net.AbstractMaplePacketHandler;
import guida.net.channel.ChannelServer;
import guida.net.channel.remote.ChannelWorldInterface;
import guida.net.world.remote.WorldChannelInterface;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static guida.client.BuddyList.BuddyOperation.ADDED;
import static guida.client.BuddyList.BuddyOperation.DELETED;

public class BuddylistModifyHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(BuddylistModifyHandler.class);

    private void nextPendingRequest(MapleClient c) {
        CharacterNameAndId pendingBuddyRequest = c.getPlayer().getBuddylist().pollPendingRequest();
        if (pendingBuddyRequest != null) {
            c.sendPacket(MaplePacketCreator.requestBuddylistAdd(pendingBuddyRequest.getId(), pendingBuddyRequest.getName()));
        }
    }

    private CharacterIdNameBuddyCapacity getCharacterIdAndNameFromDatabase(String name) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("SELECT id, name, gm, buddyCapacity FROM characters WHERE name LIKE ?");
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();
        CharacterIdNameBuddyCapacity ret = null;
        if (rs.next()) {
            ret = new CharacterIdNameBuddyCapacity(rs.getInt("id"), rs.getString("name"), rs.getInt("gm"), rs.getInt("buddyCapacity"));
        }
        rs.close();
        ps.close();
        return ret;
    }

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        int mode = slea.readByte();
        MapleCharacter player = c.getPlayer();
        WorldChannelInterface worldInterface = c.getChannelServer().getWorldInterface();
        BuddyList buddylist = player.getBuddylist();
        if (mode == 1) { // add
            String addName = slea.readMapleAsciiString();
            String groupName = slea.readMapleAsciiString();
            if (groupName.length() > 16) {
                return;
            }
            BuddylistEntry ble = buddylist.get(addName);
            if (ble != null && !ble.isVisible()) {
                c.sendPacket(MaplePacketCreator.buddylistMessage((byte) 13));
            } else if (buddylist.isFull()) {
                c.sendPacket(MaplePacketCreator.buddylistMessage((byte) 11));
            } else {
                try {
                    CharacterIdNameBuddyCapacity charWithId = null;
                    int channel;
                    MapleCharacter otherChar = c.getChannelServer().getPlayerStorage().getCharacterByName(addName);
                    if (otherChar != null) {
                        if (!c.getPlayer().hasGMLevel(2) && otherChar.hasGMLevel(2)) {
                            c.sendPacket(MaplePacketCreator.buddylistMessage((byte) 14));
                            return;
                        }
                        channel = c.getChannel();
                        charWithId = new CharacterIdNameBuddyCapacity(otherChar.getId(), otherChar.getName(), 0, otherChar.getBuddylist().getCapacity());
                    } else {
                        channel = worldInterface.find(addName);
                        charWithId = getCharacterIdAndNameFromDatabase(addName);
                        if (charWithId != null && !c.getPlayer().hasGMLevel(2) && charWithId.getGMLevel() > 1) {
                            c.sendPacket(MaplePacketCreator.buddylistMessage((byte) 14));
                            return;
                        }
                    }

                    if (charWithId != null) {
                        BuddyAddResult buddyAddResult = null;
                        if (channel != -1) {
                            ChannelWorldInterface channelInterface = worldInterface.getChannelInterface(channel);
                            buddyAddResult = channelInterface.requestBuddyAdd(addName, c.getChannel(), player.getId(), player.getName());
                        } else {
                            Connection con = DatabaseConnection.getConnection();
                            PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) as buddyCount FROM buddies WHERE characterid = ? AND pending = 0");
                            ps.setInt(1, charWithId.getId());
                            ResultSet rs = ps.executeQuery();
                            if (!rs.next()) {
                                throw new RuntimeException("Result set expected");
                            } else {
                                int count = rs.getInt("buddyCount");
                                if (count >= charWithId.getBuddyCapacity()) {
                                    buddyAddResult = BuddyAddResult.BUDDYLIST_FULL;
                                }
                            }
                            rs.close();
                            ps.close();
                            ps = con.prepareStatement("SELECT pending FROM buddies WHERE characterid = ? AND buddyid = ?");
                            ps.setInt(1, charWithId.getId());
                            ps.setInt(2, player.getId());
                            rs = ps.executeQuery();
                            if (rs.next()) {
                                buddyAddResult = BuddyAddResult.ALREADY_ON_LIST;
                            }
                            rs.close();
                            ps.close();
                        }
                        if (buddyAddResult == BuddyAddResult.BUDDYLIST_FULL) {
                            c.sendPacket(MaplePacketCreator.buddylistMessage((byte) 12));
                        } else {
                            int displayChannel = -1;
                            int otherCid = charWithId.getId();
                            if (buddyAddResult == BuddyAddResult.ALREADY_ON_LIST && channel != -1) {
                                displayChannel = channel;
                                notifyRemoteChannel(c, channel, otherCid, ADDED);
                            } else if (buddyAddResult != BuddyAddResult.ALREADY_ON_LIST && channel == -1) {
                                Connection con = DatabaseConnection.getConnection();
                                PreparedStatement ps = con.prepareStatement("INSERT INTO buddies (`characterid`, `buddyid`, `groupname`, `pending`) VALUES (?, ?, ?, 1)");
                                ps.setInt(1, charWithId.getId());
                                ps.setInt(2, player.getId());
                                ps.setString(3, groupName);
                                ps.executeUpdate();
                                ps.close();
                            }
                            buddylist.put(new BuddylistEntry(charWithId.getName(), otherCid, groupName, displayChannel, true));
                            c.sendPacket(MaplePacketCreator.updateBuddylist(buddylist.getBuddies()));
                        }
                    } else {
                        c.sendPacket(MaplePacketCreator.buddylistMessage((byte) 15));
                    }
                } catch (RemoteException e) {
                    log.error("REMOTE THROW", e);
                    c.getChannelServer().reconnectWorld();
                } catch (SQLException e) {
                    log.error("SQL THROW", e);
                }
            }
        } else if (mode == 2) { // accept buddy
            int otherCid = slea.readInt();
            if (!buddylist.isFull()) {
                try {
                    int channel = worldInterface.find(otherCid);
                    String otherName = null;
                    ChannelServer cserv = ChannelServer.getInstance(channel);
                    MapleCharacter otherChar = null;
                    if (cserv != null) {
                        otherChar = cserv.getPlayerStorage().getCharacterById(otherCid);
                    }
                    if (otherChar == null) {
                        Connection con = DatabaseConnection.getConnection();
                        PreparedStatement ps = con.prepareStatement("SELECT name FROM characters WHERE id = ?");
                        ps.setInt(1, otherCid);
                        ResultSet rs = ps.executeQuery();
                        if (rs.next()) {
                            otherName = rs.getString("name");
                        }
                        rs.close();
                        ps.close();
                    } else {
                        otherName = otherChar.getName();
                    }
                    if (otherName != null && !otherName.isEmpty()) {
                        buddylist.put(new BuddylistEntry(otherName, otherCid, "Default Group", channel, true));
                        c.sendPacket(MaplePacketCreator.updateBuddylist(buddylist.getBuddies()));
                        notifyRemoteChannel(c, channel, otherCid, ADDED);
                    }
                } catch (RemoteException e) {
                    log.error("REMOTE THROW", e);
                } catch (SQLException e) {
                    log.error("SQL THROW", e);
                }
            }
            nextPendingRequest(c);
        } else if (mode == 3) { // delete
            int otherCid = slea.readInt();
            if (buddylist.containsVisible(otherCid)) {
                try {
                    notifyRemoteChannel(c, worldInterface.find(otherCid), otherCid, DELETED);
                } catch (RemoteException e) {
                    log.error("REMOTE THROW", e);
                    c.getChannelServer().reconnectWorld();
                }
            }
            buddylist.remove(otherCid);
            c.sendPacket(MaplePacketCreator.updateBuddylist(player.getBuddylist().getBuddies()));
            nextPendingRequest(c);
        }
    }

    private void notifyRemoteChannel(MapleClient c, int remoteChannel, int otherCid, BuddyOperation operation) throws RemoteException {
        WorldChannelInterface worldInterface = c.getChannelServer().getWorldInterface();
        MapleCharacter player = c.getPlayer();

        if (remoteChannel != -1) {
            ChannelWorldInterface channelInterface = worldInterface.getChannelInterface(remoteChannel);
            channelInterface.buddyChanged(otherCid, player.getId(), player.getName(), c.getChannel(), operation);
        }
    }

    private static class CharacterIdNameBuddyCapacity extends CharacterNameAndId {

        private final int buddyCapacity;

        public CharacterIdNameBuddyCapacity(int id, String name, int gm, int buddyCapacity) {
            super(id, name, gm);
            this.buddyCapacity = buddyCapacity;
        }

        public int getBuddyCapacity() {
            return buddyCapacity;
        }
    }
}