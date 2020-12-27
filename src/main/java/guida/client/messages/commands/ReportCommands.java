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
import guida.client.messages.IllegalCommandSyntaxException;
import guida.client.messages.MessageCallback;
import guida.database.DatabaseConnection;
import guida.net.channel.handler.AlertGMHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static guida.client.messages.CommandProcessor.getOptionalIntArg;

public class ReportCommands implements Command {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReportCommands.class);

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        switch (splitted[0]) {
            case "!listreports" -> {
                int page = getOptionalIntArg(splitted, 1, 0);
                mc.dropMessage("== Reports page: " + page + " ==");
                try {
                    ps = con.prepareStatement("SELECT * FROM reports WHERE status NOT LIKE \"R%\" ORDER BY id DESC LIMIT ?, 15");
                    ps.setInt(1, page * 15);
                    ResultSet rs = ps.executeQuery();
                    mc.dropMessage("Report ID | Reason | Reporter | Victim | User Description | Status");
                    while (rs.next()) {
                        mc.dropMessage(rs.getInt("id") + " | " + AlertGMHandler.getReason(rs.getInt("reason") - 1) + " | " + AlertGMHandler.getNameById(rs.getInt("reporterId")) + " | " + AlertGMHandler.getNameById(rs.getInt("victimId")) + " | " + rs.getString("userDescription") + " | " + rs.getString("status"));
                    }
                    rs.close();
                    ps.close();
                } catch (SQLException ex) {
                    log.error("Report SQL Error", ex);
                }
            }
            case "!getreport" -> {
                if (splitted.length < 2) {
                    throw new IllegalCommandSyntaxException(2);
                }
                int reportid = Integer.parseInt(splitted[1]);
                try {
                    ps = con.prepareStatement("SELECT * FROM reports WHERE id = ?");
                    ps.setInt(1, reportid);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        mc.dropMessage(AlertGMHandler.getReason(rs.getInt("reason") - 1) + " | " + AlertGMHandler.getNameById(rs.getInt("reporterId")) + " | " + AlertGMHandler.getNameById(rs.getInt("victimId")) + " | " + rs.getString("status"));
                        String[] chatlog = rs.getString("chatlog").split("\r\n");

                        mc.dropMessage("== Chatlog start:");
                        for (String x : chatlog) {
                            mc.dropMessage(x.trim());
                        }
                        mc.dropMessage("== Chatlog end");
                    }
                    rs.close();
                    ps.close();
                } catch (SQLException ex) {
                    log.error("Report SQL Error", ex);
                }
                break;
            }
            case "!editreportstatus", "!resolvereport" -> {
                if (splitted.length < 2) {
                    throw new IllegalCommandSyntaxException(2);
                }
                int reportid = Integer.parseInt(splitted[1]);
                String status = splitted[2] != null ? splitted[2] : "RESOLVED";
                try {
                    ps = con.prepareStatement("UPDATE reports SET status = ? WHERE id = ?");
                    ps.setString(1, status);
                    ps.setInt(2, reportid);
                    ps.executeUpdate();
                    ps.close();
                    mc.dropMessage("Updated report.");
                } catch (SQLException ex) {
                    log.error("Report SQL Error", ex);
                }
                break;
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("listreports", "<page>", "Lists reports", 1),
                new CommandDefinition("getreport", "id", "Gets the report from the specified id", 1),
                new CommandDefinition("editreportstatus", "id status", "Edits the status of a report", 1),
                new CommandDefinition("resolvereport", "id", "Sets the status to RESOLVED", 1)
        };
    }
}