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

package guida.net.world;

import guida.database.DatabaseConnection;
import guida.net.MaplePacket;
import guida.net.channel.remote.ChannelWorldInterface;
import guida.net.login.remote.LoginWorldInterface;
import guida.net.world.guild.MapleAlliance;
import guida.net.world.guild.MapleGuild;
import guida.net.world.guild.MapleGuildCharacter;
import guida.net.world.remote.WorldChannelInterface;
import guida.net.world.remote.WorldLoginInterface;
import guida.net.world.remote.WorldRegistry;

import javax.rmi.ssl.SslRMIClientSocketFactory;
import javax.rmi.ssl.SslRMIServerSocketFactory;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Matze
 */
public class WorldRegistryImpl extends UnicastRemoteObject implements WorldRegistry {

    private static final long serialVersionUID = -5170574938159280746L;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorldRegistryImpl.class);
    private static volatile WorldRegistryImpl instance = null;
    private final Map<Integer, ChannelWorldInterface> channelServer = new LinkedHashMap<>();
    private final List<LoginWorldInterface> loginServer = new LinkedList<>();
    private final Map<Integer, MapleParty> parties = new HashMap<>();
    private final AtomicInteger runningPartyId = new AtomicInteger();
    private final Map<Integer, MapleMessenger> messengers = new HashMap<>();
    private final AtomicInteger runningMessengerId = new AtomicInteger();
    private final Map<Integer, MapleGuild> guilds = new LinkedHashMap<>();
    private final Map<Integer, MapleAlliance> alliances = new LinkedHashMap<>();
    private final PlayerBuffStorage buffStorage = new PlayerBuffStorage();

    private WorldRegistryImpl() throws RemoteException {
        super(0, new SslRMIClientSocketFactory(), new SslRMIServerSocketFactory());
        DatabaseConnection.setProps(WorldServer.getInstance().getDbProp());

        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps;
        try {
            ps = con.prepareStatement("SELECT MAX(party) + 1 FROM characters");
            ResultSet rs = ps.executeQuery();
            rs.next();
            runningPartyId.set(rs.getInt(1));
            rs.close();
            ps.close();
        } catch (SQLException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        runningMessengerId.set(1);
    }

    public static WorldRegistryImpl getInstance() {
        if (instance == null) {
            try {
                instance = new WorldRegistryImpl();
            } catch (RemoteException e) {
                // can't do much anyway we are fucked ^^
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    private int getFreeChannelId() {
        for (int i = 0; i < 30; i++) {
            if (!channelServer.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    public WorldChannelInterface registerChannelServer(String authKey, ChannelWorldInterface cb) throws RemoteException {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM channels WHERE `key` = SHA1(?) AND world = ?");
            ps.setString(1, authKey);
            ps.setInt(2, WorldServer.getInstance().getWorldId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int channelId = rs.getInt("number");
                if (channelId < 1) {
                    channelId = getFreeChannelId();
                    if (channelId == -1) {
                        throw new RuntimeException("Maximum channels reached");
                    }
                } else {
                    if (channelServer.containsKey(channelId)) {
                        ChannelWorldInterface oldch = channelServer.get(channelId);
                        try {
                            oldch.shutdown(0);
                        } catch (ConnectException ce) {
                            ce.printStackTrace();
                        }
                    }
                }
                channelServer.put(channelId, cb);
                cb.setChannelId(channelId);
                WorldChannelInterface ret = new WorldChannelInterfaceImpl(cb, rs.getInt("channelid"));
                rs.close();
                ps.close();
                return ret;
            }
            rs.close();
            ps.close();
        } catch (SQLException ex) {
            log.error("Encountered database error while authenticating channelserver", ex);
        }
        throw new RuntimeException("Couldn't find a channel with the given key (" + authKey + ")");
    }

    public void deregisterChannelServer(int channel) throws RemoteException {
        channelServer.remove(channel);
        for (LoginWorldInterface wli : loginServer) {
            wli.channelOffline(channel);
        }
        log.info("Channel {} is offline.", channel);
    }

    public WorldLoginInterface registerLoginServer(String authKey, LoginWorldInterface cb) throws RemoteException {
        WorldLoginInterface ret = null;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT * FROM loginserver WHERE `key` = SHA1(?) AND world = ?");
            ps.setString(1, authKey);
            ps.setInt(2, WorldServer.getInstance().getWorldId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                loginServer.add(cb);
                for (ChannelWorldInterface cwi : channelServer.values()) {
                    cb.channelOnline(cwi.getChannelId(), cwi.getIP());
                }
            }
            rs.close();
            ps.close();
            ret = new WorldLoginInterfaceImpl();
        } catch (Exception e) {
            log.error("Encountered database error while authenticating loginserver", e);
        }
        return ret;
    }

    public void deregisterLoginServer(LoginWorldInterface cb) throws RemoteException {
        loginServer.remove(cb);
    }

    public List<LoginWorldInterface> getLoginServer() {
        return new ArrayList<>(loginServer);
    }

    public ChannelWorldInterface getChannel(int channel) {
        return channelServer.get(channel);
    }

    public Set<Integer> getChannelServer() {
        return new HashSet<>(channelServer.keySet());
    }

    public Collection<ChannelWorldInterface> getAllChannelServers() {
        return channelServer.values();
    }

    public MapleParty createParty(MaplePartyCharacter chrfor) {
        MapleParty party = new MapleParty(runningPartyId.getAndIncrement(), chrfor);
        parties.put(party.getId(), party);
        return party;
    }

    public MapleParty getParty(int partyid) {
        return parties.get(partyid);
    }

    public MapleParty disbandParty(int partyid) {
        return parties.remove(partyid);
    }

    public int createGuild(int leaderId, String name) {
        return MapleGuild.createGuild(leaderId, name);
    }

    public MapleGuild getGuild(int id) {
        synchronized (guilds) {
            if (guilds.get(id) != null) {
                return guilds.get(id);
            }

            MapleGuild g = new MapleGuild(id);
            if (g.getId() == -1) {
                return null;
            }
            guilds.put(id, g);

            return g;
        }
    }

    public void clearGuilds() {
        synchronized (guilds) {
            guilds.clear();
        }
        try { //reload all the online characters in guilds
            for (ChannelWorldInterface cwi : getAllChannelServers()) {
                cwi.reloadGuildCharacters();
            }
        } catch (RemoteException re) {
            log.error("RemoteException occurred while attempting to reload guilds.", re);
        }
    }

    public void setGuildMemberOnline(MapleGuildCharacter mgc, boolean bOnline, int channel) {
        MapleGuild g = getGuild(mgc.getGuildId());
        g.setOnline(mgc.getId(), bOnline, channel);
    }

    public int addGuildMember(MapleGuildCharacter mgc) {
        MapleGuild g = guilds.get(mgc.getGuildId());
        if (g != null) {
            return g.addGuildMember(mgc);
        }
        return 0;
    }

    public void leaveGuild(MapleGuildCharacter mgc) {
        MapleGuild g = guilds.get(mgc.getGuildId());
        if (g != null) {
            g.leaveGuild(mgc);
        }
    }

    public boolean setGuildAllianceId(int gId, int aId) {
        MapleGuild guild = guilds.get(gId);
        if (guild != null) {
            guild.setAllianceId(aId);
            return true;
        }
        return false;
    }

    public void guildChat(int gid, String name, int cid, String msg) {
        MapleGuild g = guilds.get(gid);
        if (g != null) {
            g.guildChat(name, cid, msg);
        }
    }

    public void changeRank(int gid, int cid, int newRank) {
        MapleGuild g = guilds.get(gid);
        if (g != null) {
            g.changeRank(cid, newRank);
        }
    }

    public void expelMember(MapleGuildCharacter initiator, String name, int cid) {
        MapleGuild g = guilds.get(initiator.getGuildId());
        if (g != null) {
            g.expelMember(initiator, name, cid);
        }
    }

    public void setGuildNotice(int gid, String notice) {
        MapleGuild g = guilds.get(gid);
        if (g != null) {
            g.setGuildNotice(notice);
        }
    }

    public void memberLevelJobUpdate(MapleGuildCharacter mgc) {
        MapleGuild g = guilds.get(mgc.getGuildId());
        if (g != null) {
            g.memberLevelJobUpdate(mgc);
        }
    }

    public void changeRankTitle(int gid, String[] ranks) {
        MapleGuild g = guilds.get(gid);
        if (g != null) {
            g.changeRankTitle(ranks);
        }
    }

    public void setGuildEmblem(int gid, short bg, byte bgcolor, short logo, byte logocolor) {
        MapleGuild g = guilds.get(gid);
        if (g != null) {
            g.setGuildEmblem(bg, bgcolor, logo, logocolor);
        }
    }

    public void disbandGuild(int gid) {
        synchronized (guilds) {
            MapleGuild g = guilds.get(gid);
            g.disbandGuild();
            guilds.remove(gid);
        }
    }

    public boolean increaseGuildCapacity(int gid) {
        MapleGuild g = guilds.get(gid);
        if (g != null) {
            return g.increaseCapacity();
        }
        return false;
    }

    public void gainGP(int gid, int amount) {
        MapleGuild g = guilds.get(gid);
        if (g != null) {
            g.gainGP(amount);
        }
    }

    public MapleMessenger createMessenger(MapleMessengerCharacter chrfor) {
        int messengerid = runningMessengerId.getAndIncrement();
        MapleMessenger messenger = new MapleMessenger(messengerid, chrfor);
        messengers.put(messenger.getId(), messenger);
        return messenger;
    }

    public MapleMessenger getMessenger(int messengerid) {
        return messengers.get(messengerid);
    }

    public PlayerBuffStorage getPlayerBuffStorage() {
        return buffStorage;
    }

    public void broadcastToGuild(int g, MaplePacket packet) {
        MapleGuild guild = guilds.get(g);
        if (guild != null) {
            guild.broadcast(packet);
        }
    }

    public MapleAlliance getAlliance(int id) {
        synchronized (alliances) {
            if (alliances.containsKey(id)) {
                return alliances.get(id);
            }
            return null;
        }
    }

    public void addAlliance(int id, MapleAlliance alliance) {
        synchronized (alliances) {
            if (!alliances.containsKey(id)) {
                alliances.put(id, alliance);
            }
        }
    }

    public void disbandAlliance(int id) {
        synchronized (alliances) {
            MapleAlliance alliance = alliances.get(id);
            if (alliance != null) {
                for (Integer gid : alliance.getGuilds()) {
                    guilds.get(gid).setAllianceId(0);
                }
                alliances.remove(id);
            }
        }
    }

    public void allianceMessage(int id, MaplePacket packet, int exception, int guildex) {
        MapleAlliance alliance = alliances.get(id);
        if (alliance != null) {
            for (Integer gid : alliance.getGuilds()) {
                if (guildex == gid) {
                    continue;
                }
                MapleGuild guild = guilds.get(gid);
                if (guild != null) {
                    guild.broadcast(packet, exception);
                }
            }
        }
    }

    public boolean addGuildtoAlliance(int aId, int guildId) {
        MapleAlliance alliance = alliances.get(aId);
        if (alliance != null) {
            alliance.addGuild(guildId);
            return true;
        }
        return false;
    }

    public boolean removeGuildFromAlliance(int aId, int guildId) {
        MapleAlliance alliance = alliances.get(aId);
        if (alliance != null) {
            alliance.removeGuild(guildId);
            return true;
        }
        return false;
    }

    public boolean setAllianceRanks(int aId, String[] ranks) {
        MapleAlliance alliance = alliances.get(aId);
        if (alliance != null) {
            alliance.setRankTitle(ranks);
            return true;
        }
        return false;
    }

    public boolean setAllianceNotice(int aId, String notice) {
        MapleAlliance alliance = alliances.get(aId);
        if (alliance != null) {
            alliance.setNotice(notice);
            return true;
        }
        return false;
    }

    public boolean increaseAllianceCapacity(int aId, int inc) {
        MapleAlliance alliance = alliances.get(aId);
        if (alliance != null) {
            alliance.increaseCapacity(inc);
            return true;
        }
        return false;
    }
}