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

import guida.client.BuddylistEntry;
import guida.client.CharacterNameAndId;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MaplePet;
import guida.database.DatabaseConnection;
import guida.net.AbstractMaplePacketHandler;
import guida.net.channel.ChannelServer;
import guida.net.world.CharacterIdChannelPair;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.PlayerBuffValueHolder;
import guida.net.world.guild.MapleAlliance;
import guida.net.world.remote.WorldChannelInterface;
import guida.server.MapleInventoryManipulator;
import guida.server.maps.MapleMap;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

public class PlayerLoggedinHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerLoggedinHandler.class);

    @Override
    public boolean validateState(MapleClient c) {
        return !c.isLoggedIn();
    }

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (c.getAccID() != 0) {
            log.warn(MapleClient.getLogMessage(c.getPlayer(), c.getPlayer().getName() + " is attempting to remote hack."));
            c.disconnect();
            return;
        }
        final int cid = slea.readInt();
        MapleCharacter player = null;
        try {
            player = MapleCharacter.loadCharFromDB(cid, c, true);
            c.setPlayer(player);
        } catch (SQLException e) {
            log.error("Loading the char failed", e);
        }
        if (player == null) {
            return;
        }
        c.setAccID(player.getAccountID());
        c.loadForumUserId();
        int state = c.getLoginState();
        boolean allowLogin = true;
        ChannelServer channelServer = c.getChannelServer();
        synchronized (this) {
            try {
                WorldChannelInterface worldInterface = channelServer.getWorldInterface();
                if (state == MapleClient.LOGIN_SERVER_TRANSITION) {
                    for (String charName : c.loadCharacterNames(c.getWorld())) {
                        if (worldInterface.isConnected(charName, false)) {
                            log.warn(MapleClient.getLogMessage(player, "Attempting to double login with " + charName));
                            allowLogin = false;
                            break;
                        }
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
                channelServer.reconnectWorld();
                allowLogin = false;
            }
            if (state != MapleClient.LOGIN_SERVER_TRANSITION || !allowLogin || !c.checkAccount(player.getAccountID())) {
                c.setPlayer(null); // prevent char from getting deregistered
                c.disconnect();
                return;
            }
            c.updateLoginState(MapleClient.LOGIN_LOGGEDIN);
        }
        ChannelServer cserv = c.getChannelServer();
        cserv.addPlayer(player);
        if (player.getLastDeath() + 600000 > System.currentTimeMillis() && !player.isAlive()) {
            c.getPlayer().cancelAllBuffs();
            MapleMap to = c.getPlayer().getMap().getReturnMap();
            player.setStance((byte) 0);
            if (player.getMap().canExit() && to != null && to.canEnter() || player.isGM()) {
                player.setHp(50);
                player.changeMap(to, to.getRandomSpawnPoint());
            } else {
                c.sendPacket(MaplePacketCreator.serverNotice(5, "You will remain dead."));
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        }
        if (c.getPlayer().hasGMLevel(3)) {
            player.setHidden(true);
        }
        if (c.getPlayer().isPacketLogging()) {
            c.setPacketLog(true);
        }
        try {
            WorldChannelInterface wci = c.getChannelServer().getWorldInterface();
            List<PlayerBuffValueHolder> buffs = wci.getBuffsFromStorage(cid);
            if (buffs != null) {
                c.getPlayer().silentGiveBuffs(buffs);
            }
        } catch (RemoteException e) {
            c.getChannelServer().reconnectWorld();
        }
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT * FROM csgifts WHERE recipient = ?");
            ps.setInt(1, c.getPlayer().getId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int itemid = rs.getInt("itemid");
                String sender = rs.getString("sender");
                String message = rs.getString("message");
                int quantity = rs.getInt("quantity");
                if (itemid >= 5000000 && itemid <= 5000100) {
                    MaplePet pet = MaplePet.createPet(c.getPlayer().getId(), itemid);
                    if (pet == null) {
                        rs.close();
                        ps.close();
                        return;
                    }
                    MapleInventoryManipulator.addById(c, itemid, (short) 1, "Cash Item was gifted.", null, pet);
                } else {
                    MapleInventoryManipulator.addById(c, itemid, (short) quantity, "Cash Item was gifted.", null, null);
                }
                c.getPlayer().dropMessage("A gift has been sent to you by " + sender + "! " + sender + "'s message is: " + message);
            }
            rs.close();
            ps.close();
            ps = con.prepareStatement("DELETE FROM csgifts WHERE recipient = ?");
            ps.setInt(1, c.getPlayer().getId());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException SQE) {
            SQE.printStackTrace();
        }
        c.sendPacket(MaplePacketCreator.getCharInfo(player));
        c.sendPacket(MaplePacketCreator.charGender(c.getPlayer().getGender()));
        c.sendPacket(MaplePacketCreator.mountInfo(player));
        player.sendKeymap();
        c.sendPacket(MaplePacketCreator.sendAutoHpPot(c.getPlayer().getAutoHpPot()));
        c.getPlayer().sendMacros();
        c.sendPacket(MaplePacketCreator.sendAutoMpPot(c.getPlayer().getAutoMpPot()));
        c.sendPacket(MaplePacketCreator.alertGMStatus(true));
        //c.sendPacket(MaplePacketCreator.unknownStatus());
        player.getMap().addPlayer(player);
        c.getPlayer().getClient().sendPacket(MaplePacketCreator.serverMessage(c.getChannelServer().getServerMessage()));
        try {
            Collection<BuddylistEntry> buddies = player.getBuddylist().getBuddies();
            int[] buddyIds = player.getBuddylist().getBuddyIds();

            cserv.getWorldInterface().loggedOn(player.getName(), player.getId(), c.getChannel(), buddyIds);
            if (player.getParty() != null) {
                channelServer.getWorldInterface().updateParty(player.getParty().getId(), PartyOperation.LOG_ONOFF, new MaplePartyCharacter(player));
            }

            CharacterIdChannelPair[] onlineBuddies = cserv.getWorldInterface().multiBuddyFind(player.getId(), buddyIds);
            for (CharacterIdChannelPair onlineBuddy : onlineBuddies) {
                BuddylistEntry ble = player.getBuddylist().get(onlineBuddy.getCharacterId());
                ble.setChannel(onlineBuddy.getChannel());
                player.getBuddylist().put(ble);
            }
            c.sendPacket(MaplePacketCreator.updateBuddylist(buddies));

            try {
                c.getPlayer().showNote();
            } catch (SQLException e) {
                log.error("LOADING NOTE", e);
            }

            if (player.getGuildId() > 0) {
                WorldChannelInterface wi = channelServer.getWorldInterface();
                wi.setGuildMemberOnline(player.getMGC(), true, c.getChannel());
                c.sendPacket(MaplePacketCreator.showGuildInfo(player));
                int allianceId = player.getGuild().getAllianceId();
                if (allianceId > 0) {
                    MapleAlliance newAlliance = cserv.getWorldInterface().getAlliance(allianceId);
                    if (newAlliance == null) {
                        newAlliance = MapleAlliance.loadAlliance(allianceId);
                        if (newAlliance != null) {
                            cserv.getWorldInterface().addAlliance(allianceId, newAlliance);
                        } else {
                            player.getGuild().setAllianceId(0);
                        }
                    }
                    if (newAlliance != null) {
                        c.sendPacket(MaplePacketCreator.getAllianceInfo(newAlliance));
                        c.sendPacket(MaplePacketCreator.getGuildAlliances(newAlliance, c));
                        cserv.getWorldInterface().allianceMessage(allianceId, MaplePacketCreator.allianceMemberOnline(player, true), player.getId(), -1);
                    }
                }
            }
        } catch (RemoteException e) {
            log.info("REMOTE THROW", e);
            channelServer.reconnectWorld();
        }

        player.updatePartyMemberHP();

        CharacterNameAndId pendingBuddyRequest = player.getBuddylist().pollPendingRequest();
        if (pendingBuddyRequest != null) {
            player.getBuddylist().put(new BuddylistEntry(pendingBuddyRequest.getName(), pendingBuddyRequest.getId(), "Default Group", -1, false));
            c.sendPacket(MaplePacketCreator.requestBuddylistAdd(pendingBuddyRequest.getId(), pendingBuddyRequest.getName()));
        }

        player.checkMessenger();
        player.checkBerserk();
        player.checkDuey();
        player.checkForExpiredItems();
        player.getMonsterBook().checkAchievement(player.getClient());
        player.checkTutorial();
    }
}