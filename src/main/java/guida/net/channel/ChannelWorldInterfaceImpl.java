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

package guida.net.channel;

import guida.client.BuddyList;
import guida.client.BuddyList.BuddyAddResult;
import guida.client.BuddyList.BuddyOperation;
import guida.client.BuddylistEntry;
import guida.client.MapleCharacter;
import guida.client.MapleCharacterUtil;
import guida.database.DatabaseConnection;
import guida.net.ByteArrayMaplePacket;
import guida.net.MaplePacket;
import guida.net.channel.remote.ChannelWorldInterface;
import guida.net.world.MapleMessenger;
import guida.net.world.MapleMessengerCharacter;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.guild.MapleGuildSummary;
import guida.net.world.remote.CheaterData;
import guida.server.ShutdownServer;
import guida.server.TimerManager;
import guida.server.maps.MapleMap;
import guida.tools.CollectionUtil;
import guida.tools.MaplePacketCreator;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Matze
 */
public class ChannelWorldInterfaceImpl extends UnicastRemoteObject implements ChannelWorldInterface {

    private static final long serialVersionUID = 7815256899088644192L;
    private transient ChannelServer server;

    public ChannelWorldInterfaceImpl() throws RemoteException {
        super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
    }

    public ChannelWorldInterfaceImpl(ChannelServer server) throws RemoteException {
        super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
        this.server = server;
    }

    public void setChannelId(int id) throws RemoteException {
        server.setChannel(id);
    }

    public int getChannelId() throws RemoteException {
        return server.getChannel();
    }

    public String getIP() throws RemoteException {
        return server.getIP();
    }

    public void broadcastMessage(String sender, final byte[] message, boolean smega) throws RemoteException {
        MaplePacket packet = new ByteArrayMaplePacket(message);
        server.broadcastPacket(packet, smega);
    }

    public void broadcastMessage(String sender, final byte[] message) throws RemoteException {
        broadcastMessage(sender, message, false);
    }

    public void whisper(String sender, String target, int channel, String message) throws RemoteException {
        if (isConnected(target)) {
            server.getPlayerStorage().getCharacterByName(target).getClient().sendPacket(MaplePacketCreator.getWhisper(sender, channel, message));
        }
    }

    public boolean isConnected(String charName) throws RemoteException {
        return server.getPlayerStorage().getCharacterByName(charName) != null;
    }

    public boolean isConnected(String charName, boolean removePlayer) throws RemoteException {
        if (server.getPlayerStorage().getCharacterByName(charName) != null) {
            if (removePlayer) {
                server.removePlayer(server.getPlayerStorage().getCharacterByName(charName));
            }
            return true;
        }
        return false;
    }

    public void shutdown(int time) throws RemoteException {
        if (time / 60000 != 0) {
            server.broadcastPacket(MaplePacketCreator.serverNotice(0, "The server will shut down in " + time / 60000 + " minute(s). Please log off safely."));
        }
        TimerManager.getInstance().schedule(new ShutdownServer(server.getChannel()), time);
        for (MapleMap map : server.getMapFactory().getMaps()) {
            map.clearShownMapTimer();
            map.addMapTimer(time / 1000, time / 1000, new String[0], false, true, null);
            if (server.getShutdownTimer() != null) {
                server.setShutdownTimer(map.getShownMapTimer());
            }
        }
    }

    public void broadcastWorldMessage(String message) throws RemoteException {
        server.broadcastPacket(MaplePacketCreator.serverNotice(0, message));
    }

    public int getConnected() throws RemoteException {
        return server.getConnectedClients();
    }

    @Override
    public void loggedOff(String name, int characterId, int channel, int[] buddies) throws RemoteException {
        updateBuddies(characterId, channel, buddies, true);
    }

    @Override
    public void loggedOn(String name, int characterId, int channel, int[] buddies) throws RemoteException {
        updateBuddies(characterId, channel, buddies, false);
    }

    private void updateBuddies(int characterId, int channel, int[] buddies, boolean offline) {
        IPlayerStorage playerStorage = server.getPlayerStorage();
        for (int buddy : buddies) {
            MapleCharacter chr = playerStorage.getCharacterById(buddy);
            if (chr != null) {
                BuddylistEntry ble = chr.getBuddylist().get(characterId);
                if (ble != null && ble.isVisible()) {
                    int mcChannel;
                    if (offline) {
                        ble.setChannel(-1);
                        mcChannel = -1;
                    } else {
                        ble.setChannel(channel);
                        mcChannel = channel - 1;
                    }
                    chr.getBuddylist().put(ble);
                    chr.getClient().sendPacket(MaplePacketCreator.updateBuddyChannel(ble.getCharacterId(), mcChannel));
                }
            }
        }
    }

    @Override
    public void updateParty(MapleParty party, PartyOperation operation, MaplePartyCharacter target) throws RemoteException {
        for (MaplePartyCharacter partychar : party.getMembers()) {
            if (partychar.getChannel() == server.getChannel()) {
                MapleCharacter chr = server.getPlayerStorage().getCharacterByName(partychar.getName());
                if (chr != null) {
                    if (operation == PartyOperation.DISBAND) {
                        chr.setParty(null);
                    } else {
                        chr.setParty(party);
                    }
                    chr.getClient().sendPacket(MaplePacketCreator.updateParty(chr.getClient().getChannel(), party, operation, target));
                    if (operation == PartyOperation.DISBAND && chr.getMap().isPartyOnly()) {
                        chr.getClient().sendPacket(MaplePacketCreator.playerMessage("You have been kicked out of the map because you left the party."));
                        chr.changeMap(chr.getMap().getReturnMap(), chr.getMap().getRandomSpawnPoint());
                    }
                }
            }
        }
        switch (operation) {
            case LEAVE:
            case EXPEL:
                if (target.getChannel() == server.getChannel()) {
                    MapleCharacter chr = server.getPlayerStorage().getCharacterByName(target.getName());
                    if (chr != null) {
                        chr.getClient().sendPacket(MaplePacketCreator.updateParty(chr.getClient().getChannel(), party, operation, target));
                        chr.setParty(null);
                        if (chr.getMap().isPartyOnly()) {
                            chr.getClient().sendPacket(MaplePacketCreator.playerMessage("You have been kicked out of the map because you left the party."));
                            chr.changeMap(chr.getMap().getReturnMap(), chr.getMap().getRandomSpawnPoint());
                        }
                    }
                }
        }
    }

    @Override
    public void partyChat(MapleParty party, String chattext, String namefrom) throws RemoteException {
        for (MaplePartyCharacter partychar : party.getMembers()) {
            if (partychar.getChannel() == server.getChannel() && !partychar.getName().equals(namefrom)) {
                MapleCharacter chr = server.getPlayerStorage().getCharacterByName(partychar.getName());
                if (chr != null) {
                    chr.getClient().sendPacket(MaplePacketCreator.multiChat(namefrom, chattext, 1));
                }
            }
        }
    }

    public boolean isAvailable() throws RemoteException {
        return true;
    }

    public int getLocation(String name) throws RemoteException {
        MapleCharacter chr = server.getPlayerStorage().getCharacterByName(name);
        if (chr != null) {
            return server.getPlayerStorage().getCharacterByName(name).getMapId();
        }
        return -1;
    }

    public List<CheaterData> getCheaters() throws RemoteException {
        List<CheaterData> cheaters = new ArrayList<>();
        List<MapleCharacter> allplayers = new ArrayList<>(server.getPlayerStorage().getAllCharacters());

        for (int x = allplayers.size() - 1; x >= 0; x--) {
            MapleCharacter cheater = allplayers.get(x);
            if (cheater.getCheatTracker().getPoints() > 0) {
                cheaters.add(new CheaterData(cheater.getCheatTracker().getPoints(), MapleCharacterUtil.makeMapleReadable(cheater.getName()) + " (" + cheater.getCheatTracker().getPoints() + ") " + cheater.getCheatTracker().getSummary()));
            }
        }
        Collections.sort(cheaters);
        return CollectionUtil.copyFirst(cheaters, 10);
    }

    @Override
    public BuddyAddResult requestBuddyAdd(String addName, int channelFrom, int cidFrom, String nameFrom) {
        MapleCharacter addChar = server.getPlayerStorage().getCharacterByName(addName);
        if (addChar != null) {
            BuddyList buddylist = addChar.getBuddylist();
            if (buddylist.isFull()) {
                return BuddyAddResult.BUDDYLIST_FULL;
            }
            if (!buddylist.contains(cidFrom)) {
                buddylist.addBuddyRequest(addChar.getClient(), cidFrom, nameFrom, channelFrom);
            } else {
                if (buddylist.containsVisible(cidFrom)) {
                    return BuddyAddResult.ALREADY_ON_LIST;
                }
            }
        }
        return BuddyAddResult.OK;
    }

    public boolean isConnected(int characterId) throws RemoteException {
        return server.getPlayerStorage().getCharacterById(characterId) != null;
    }

    @Override
    public void buddyChanged(int cid, int cidFrom, String name, int channel, BuddyOperation operation) {
        MapleCharacter addChar = server.getPlayerStorage().getCharacterById(cid);
        if (addChar != null) {
            BuddyList buddylist = addChar.getBuddylist();
            switch (operation) {
                case ADDED:
                    if (buddylist.contains(cidFrom)) {
                        buddylist.put(new BuddylistEntry(name, cidFrom, "Default Group", channel, true));
                        addChar.getClient().sendPacket(MaplePacketCreator.updateBuddyChannel(cidFrom, channel - 1));
                    }
                    break;
                case DELETED:
                    if (buddylist.contains(cidFrom)) {
                        buddylist.put(new BuddylistEntry(name, cidFrom, "Default Group", -1, buddylist.get(cidFrom).isVisible()));
                        addChar.getClient().sendPacket(MaplePacketCreator.updateBuddyChannel(cidFrom, -1));
                    }
                    break;
            }
        }
    }

    @Override
    public void buddyChat(int[] recipientCharacterIds, int cidFrom, String nameFrom, String chattext) throws RemoteException {
        IPlayerStorage playerStorage = server.getPlayerStorage();
        for (int characterId : recipientCharacterIds) {
            MapleCharacter chr = playerStorage.getCharacterById(characterId);
            if (chr != null) {
                if (chr.getBuddylist().containsVisible(cidFrom)) {
                    chr.getClient().sendPacket(MaplePacketCreator.multiChat(nameFrom, chattext, 0));
                }
            }
        }
    }

    @Override
    public int[] multiBuddyFind(int charIdFrom, int[] characterIds) throws RemoteException {
        List<Integer> ret = new ArrayList<>(characterIds.length);
        IPlayerStorage playerStorage = server.getPlayerStorage();
        for (int characterId : characterIds) {
            MapleCharacter chr = playerStorage.getCharacterById(characterId);
            if (chr != null) {
                if (chr.getBuddylist().containsVisible(charIdFrom)) {
                    ret.add(characterId);
                }
            }
        }
        int[] retArr = new int[ret.size()];
        int pos = 0;
        for (Integer i : ret) {
            retArr[pos++] = i;
        }
        return retArr;
    }

    @Override
    public void sendPacket(List<Integer> targetIds, MaplePacket packet, int exception) throws RemoteException {
        MapleCharacter c;
        for (int i : targetIds) {
            if (i == exception) {
                continue;
            }
            c = server.getPlayerStorage().getCharacterById(i);
            if (c != null) {
                c.getClient().sendPacket(packet);
            }
        }
    }

    @Override
    public void setGuildAndRank(List<Integer> cids, int guildid, int rank, int exception) throws RemoteException {
        for (int cid : cids) {
            if (cid != exception) {
                setGuildAndRank(cid, guildid, rank);
            }
        }
    }

    @Override
    public void setGuildAndRank(int cid, int guildid, int rank) throws RemoteException {
        MapleCharacter mc = server.getPlayerStorage().getCharacterById(cid);
        if (mc == null) {
            // System.out.println("ERROR: cannot find player in given channel");
            return;
        }

        boolean bDifferentGuild;
        if (guildid == -1 && rank == -1) { //just need a respawn
            bDifferentGuild = true;
        } else {
            bDifferentGuild = guildid != mc.getGuildId();
            mc.setGuildId(guildid);
            mc.setGuildRank(rank);
            mc.saveGuildStatus();
        }

        if (bDifferentGuild) {
            mc.getMap().broadcastMessage(mc, MaplePacketCreator.playerGuildName(mc), false);
            mc.getMap().broadcastMessage(mc, MaplePacketCreator.playerGuildInfo(mc), false);
        }
    }

    @Override
    public void setOfflineGuildStatus(int guildid, byte guildrank, int cid) throws RemoteException {
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(this.getClass());
        try {
            java.sql.Connection con = DatabaseConnection.getConnection();
            java.sql.PreparedStatement ps = con.prepareStatement("UPDATE characters SET guildid = ?, guildrank = ? WHERE id = ?");
            ps.setInt(1, guildid);
            ps.setInt(2, guildrank);
            ps.setInt(3, cid);
            ps.execute();
            ps.close();
        } catch (SQLException se) {
            log.error("SQLException: " + se.getLocalizedMessage(), se);
        }
    }

    @Override
    public void reloadGuildCharacters() throws RemoteException {
        for (MapleCharacter mc : server.getPlayerStorage().getAllCharacters()) {
            if (mc.getGuildId() > 0) {
                //multiple world ops, but this method is ONLY used
                //in !clearguilds gm command, so it shouldn't be a problem
                server.getWorldInterface().setGuildMemberOnline(mc.getMGC(), true, server.getChannel());
                server.getWorldInterface().memberLevelJobUpdate(mc.getMGC());
            }
        }

        ChannelServer.getInstance(getChannelId()).reloadGuildSummary();
    }

    @Override
    public void changeEmblem(int gid, List<Integer> affectedPlayers, MapleGuildSummary mgs) throws RemoteException {
        ChannelServer.getInstance(getChannelId()).updateGuildSummary(gid, mgs);
        sendPacket(affectedPlayers, MaplePacketCreator.guildEmblemChange(gid, mgs.getLogoBG(), mgs.getLogoBGColor(), mgs.getLogo(), mgs.getLogoColor()), -1);
        this.setGuildAndRank(affectedPlayers, -1, -1, -1); //respawn player
    }

    public String listGMs() throws RemoteException {
        StringBuilder builder = new StringBuilder();
        for (MapleCharacter c : ChannelServer.getInstance(getChannelId()).getPlayerStorage().getAllCharacters()) {
            if (c.isGM()) {
                builder.append(c.getName()).append(" ");
            }
        }
        return builder.toString();
    }

    public void messengerInvite(String sender, int messengerid, String target, int fromchannel) throws RemoteException {
        if (isConnected(target)) {
            MapleMessenger messenger = server.getPlayerStorage().getCharacterByName(target).getMessenger();
            if (messenger == null) {
                MapleCharacter to = server.getPlayerStorage().getCharacterByName(target);
                to.getClient().sendPacket(MaplePacketCreator.messengerInvite(sender, messengerid));
                MapleCharacter from = ChannelServer.getInstance(fromchannel).getPlayerStorage().getCharacterByName(sender);
                from.getClient().sendPacket(MaplePacketCreator.messengerNote(target, 4, to.isGM() ? 0 : 1));
            } else {
                MapleCharacter from = ChannelServer.getInstance(fromchannel).getPlayerStorage().getCharacterByName(sender);
                from.getClient().sendPacket(MaplePacketCreator.messengerChat(sender + " : " + target + " is already using Maple Messenger"));
            }
        }
    }

    public void addMessengerPlayer(MapleMessenger messenger, String namefrom, int fromchannel, int position) throws RemoteException {
        for (MapleMessengerCharacter messengerchar : messenger.getMembers()) {
            if (messengerchar.getChannel() == server.getChannel() && !messengerchar.getName().equals(namefrom)) {
                MapleCharacter chr = server.getPlayerStorage().getCharacterByName(messengerchar.getName());
                if (chr != null) {
                    MapleCharacter from = ChannelServer.getInstance(fromchannel).getPlayerStorage().getCharacterByName(namefrom);
                    chr.getClient().sendPacket(MaplePacketCreator.addMessengerPlayer(namefrom, from, position, fromchannel - 1));
                    from.getClient().sendPacket(MaplePacketCreator.addMessengerPlayer(chr.getName(), chr, messengerchar.getPosition(), messengerchar.getChannel() - 1));
                }
            } else if (messengerchar.getChannel() == server.getChannel() && messengerchar.getName().equals(namefrom)) {
                MapleCharacter chr = server.getPlayerStorage().getCharacterByName(messengerchar.getName());
                if (chr != null) {
                    chr.getClient().sendPacket(MaplePacketCreator.joinMessenger(messengerchar.getPosition()));
                }
            }
        }
    }

    public void removeMessengerPlayer(MapleMessenger messenger, int position) throws RemoteException {
        for (MapleMessengerCharacter messengerchar : messenger.getMembers()) {
            if (messengerchar.getChannel() == server.getChannel()) {
                MapleCharacter chr = server.getPlayerStorage().getCharacterByName(messengerchar.getName());
                if (chr != null) {
                    chr.getClient().sendPacket(MaplePacketCreator.removeMessengerPlayer(position));
                }
            }
        }
    }

    public void messengerChat(MapleMessenger messenger, String chattext, String namefrom) throws RemoteException {
        for (MapleMessengerCharacter messengerchar : messenger.getMembers()) {
            if (messengerchar.getChannel() == server.getChannel() && !messengerchar.getName().equals(namefrom)) {
                MapleCharacter chr = server.getPlayerStorage().getCharacterByName(messengerchar.getName());
                if (chr != null) {
                    chr.getClient().sendPacket(MaplePacketCreator.messengerChat(chattext));
                }
            }
        }
    }

    public void declineChat(String target, String namefrom) throws RemoteException {
        if (isConnected(target)) {
            MapleMessenger messenger = server.getPlayerStorage().getCharacterByName(target).getMessenger();
            if (messenger != null) {
                server.getPlayerStorage().getCharacterByName(target).getClient().sendPacket(MaplePacketCreator.messengerNote(namefrom, 5, 0));
            }
        }
    }

    public void updateMessenger(MapleMessenger messenger, String namefrom, int position, int fromchannel) throws RemoteException {
        for (MapleMessengerCharacter messengerchar : messenger.getMembers()) {
            if (messengerchar.getChannel() == server.getChannel() && !messengerchar.getName().equals(namefrom)) {
                MapleCharacter chr = server.getPlayerStorage().getCharacterByName(messengerchar.getName());
                if (chr != null) {
                    MapleCharacter from = ChannelServer.getInstance(fromchannel).getPlayerStorage().getCharacterByName(namefrom);
                    chr.getClient().sendPacket(MaplePacketCreator.updateMessengerPlayer(namefrom, from, position, fromchannel - 1));
                }
            }
        }
    }

    public void spouseChat(String from, String target, String message) throws RemoteException {
        if (isConnected(target)) {
            server.getPlayerStorage().getCharacterByName(target).getClient().sendPacket(MaplePacketCreator.spouseChat(from, message, 5));
        }
    }

    public void broadcastGMMessage(String sender, final byte[] message) throws RemoteException {
        server.broadcastGMPacket(new ByteArrayMaplePacket(message));
    }

    public boolean playerIsOnline(int id) {
        return server.getPlayerStorage().getCharacterById(id) != null;
    }
}