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
import guida.net.AbstractMaplePacketHandler;
import guida.net.channel.ChannelServer;
import guida.net.world.guild.MapleAlliance;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;

/**
 * @author Anujan
 */
public final class AllianceOperationsHandler extends AbstractMaplePacketHandler {

    public final void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        MapleAlliance alliance = null;
        if (c.getPlayer().getGuild() != null && c.getPlayer().getGuild().getAllianceId() > 0) {
            try {
                alliance = c.getChannelServer().getWorldInterface().getAlliance(c.getPlayer().getGuild().getAllianceId());
            } catch (RemoteException re) {
                c.getChannelServer().reconnectWorld();
            }
        }
        if (alliance == null) {
            c.getPlayer().dropMessage("You are not in an alliance.");
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        } else if (c.getPlayer().getMGC().getAllianceRank() > 2 || !alliance.getGuilds().contains(c.getPlayer().getGuildId())) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        try {
            switch (slea.readByte()) {
                case 1:
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.sendShowInfo(c.getPlayer().getGuild().getAllianceId(), c.getPlayer().getId()), -1, -1);
                    return;
                case 2: { // Leave Alliance
                    if (c.getPlayer().getGuild().getAllianceId() == 0 || c.getPlayer().getGuildId() < 1 || c.getPlayer().getGuildRank() != 1) {
                        return;
                    }
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.sendChangeGuild(c.getPlayer().getGuildId(), c.getPlayer().getId(), c.getPlayer().getGuildId(), 2), -1, -1);
                    break;
                }
                case 3: // send alliance invite
                    String charName = slea.readMapleAsciiString();
                    int channel = c.getChannelServer().getWorldInterface().find(charName);
                    if (channel == -1) {
                        c.getPlayer().dropMessage("The player is not online.");
                    } else {
                        MapleCharacter victim = ChannelServer.getInstance(channel).getPlayerStorage().getCharacterByName(charName);
                        if (victim.getGuildId() == 0) {
                            c.getPlayer().dropMessage("The person you are trying to invite does not have a guild.");
                        } else if (victim.getGuildRank() != 1) {
                            c.getPlayer().dropMessage("The player is not the leader of his/her guild.");
                        } else {
                            c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.sendInvitation(c.getPlayer().getGuild().getAllianceId(), c.getPlayer().getId(), slea.readMapleAsciiString()), -1, -1);
                        }
                    }
                    break;
                case 4: {
                    int guildid = slea.readInt();
                    if (c.getPlayer().getGuild().getAllianceId() != 0 || c.getPlayer().getGuildRank() != 1 || c.getPlayer().getGuildId() < 1) {
                        return;
                    }
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.sendChangeGuild(guildid, c.getPlayer().getId(), c.getPlayer().getGuildId(), 0), -1, -1);
                    break;
                }
                case 6: { // Expel Guild
                    int guildid = slea.readInt();
                    int allianceid = slea.readInt();
                    if (c.getPlayer().getGuild().getAllianceId() == 0 || c.getPlayer().getGuild().getAllianceId() != allianceid) {
                        return;
                    }
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.sendChangeGuild(allianceid, c.getPlayer().getId(), guildid, 1), -1, -1);
                    break;
                }
                case 7: { // Change Alliance Leader
                    if (c.getPlayer().getGuild().getAllianceId() == 0 || c.getPlayer().getGuildId() < 1) {
                        return;
                    }
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.sendChangeLeader(c.getPlayer().getGuild().getAllianceId(), c.getPlayer().getId(), slea.readInt()), -1, -1);
                    break;
                }
                case 8:
                    String[] ranks = new String[5];
                    for (int i = 0; i < 5; i++) {
                        ranks[i] = slea.readMapleAsciiString();
                    }
                    c.getChannelServer().getWorldInterface().setAllianceRanks(alliance.getId(), ranks);
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.changeAllianceRankTitle(alliance.getId(), ranks), -1, -1);
                    break;
                case 9: {
                    int int1 = slea.readInt();
                    byte byte1 = slea.readByte();
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.sendChangeRank(c.getPlayer().getGuild().getAllianceId(), c.getPlayer().getId(), int1, byte1), -1, -1);
                    break;
                }
                case 10:
                    String notice = slea.readMapleAsciiString();
                    c.getChannelServer().getWorldInterface().setAllianceNotice(alliance.getId(), notice);
                    c.getChannelServer().getWorldInterface().allianceMessage(alliance.getId(), MaplePacketCreator.allianceNotice(alliance.getId(), notice), -1, -1);
                    break;
                default:
                    c.getPlayer().dropMessage("Feature not available");
                    break;
            }
            alliance.saveToDB();
        } catch (RemoteException re) {
            c.getChannelServer().reconnectWorld();
        }
    }
}