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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.database.DatabaseConnection;
import guida.net.AbstractMaplePacketHandler;
import guida.net.world.remote.WorldChannelInterface;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Xterminator
 */
public class AlertGMHandler extends AbstractMaplePacketHandler {

    private static final String[] reasons = {"Curse/Inapproporate Content", "Advertising", "Fraud", "Unknown", "Cash Trade", "Unknown", "Impersonating GM", "Exposing Personal Info", "Use of Illegal Programs"};

    public static String getNameById(int id) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT name FROM characters where id = ?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String ret = rs.getString("name");
                rs.close();
                ps.close();
                return ret;
            }
            rs.close();
            ps.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return "<Couldn't retreive name, player id is " + id + ">";
    }

    public static String getReason(int reason) {
        return reasons[reason];
    }

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        String chatlog = "None";
        byte conversationLog = slea.readByte();
        String charName = slea.readMapleAsciiString();
        byte reasonId = slea.readByte();
        String description = slea.readMapleAsciiString();
        if (conversationLog == 1) {
            chatlog = slea.readMapleAsciiString();
        }
        MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterByName(charName);
        if (player == null) {
            c.sendPacket(MaplePacketCreator.alertGMStatus((byte) 66));
            return;
        }
        if (player.isGM()) {
            c.sendPacket(MaplePacketCreator.alertGMStatus((byte) 65));
            return;
        }
        if (c.getPlayer().getMeso() < 300) {
            c.sendPacket(MaplePacketCreator.alertGMStatus((byte) 67));
            return;
        }
        boolean reported = addReportEntry(c.getPlayer().getId(), player.getId(), reasonId, description, chatlog);
        String sendToGMInformation = "'" + c.getPlayer().getName() + "' reported '" + charName + "' for " + reasons[reasonId - 1] + ". User Description: " + description;
        if (reported) {
            c.sendPacket(MaplePacketCreator.alertGMStatus((byte) 2));
        } else {
            c.sendPacket(MaplePacketCreator.alertGMStatus((byte) 65));
        }
        WorldChannelInterface wci = c.getChannelServer().getWorldInterface();
        try {
            wci.broadcastGMMessage(null, MaplePacketCreator.serverNotice(5, sendToGMInformation).getBytes());
        } catch (RemoteException e) {
            c.getChannelServer().reconnectWorld();
        }
    }

    public boolean addReportEntry(int reporterId, int victimId, byte reason, String userDescription, String chatlog) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("INSERT INTO reports (reportTime, reporterId, victimId, reason, userDescription, chatlog, status) VALUES (CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, 'UNHANDLED')");
            ps.setInt(1, reporterId);
            ps.setInt(2, victimId);
            ps.setInt(3, reason);
            ps.setString(4, userDescription);
            ps.setString(5, chatlog);
            ps.executeUpdate();
            ps.close();
        } catch (Exception ex) {
            return false;
        }
        return true;
    }
}