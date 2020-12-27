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

package guida.net.world.guild;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.database.DatabaseConnection;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.net.channel.remote.ChannelWorldInterface;
import guida.net.world.WorldRegistryImpl;
import guida.tools.MaplePacketCreator;

import java.io.FileReader;
import java.io.InputStreamReader;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class MapleGuild implements java.io.Serializable {

    // these are a few configuration variables for the guilds on your server
    public final static int CREATE_GUILD_COST = 5000000; // 5 mil to start a guild
    public final static int CHANGE_EMBLEM_COST = 15000000;
    public final static int INCREASE_CAPACITY_COST = 5000000; // every 5 slots
    public final static boolean ENABLE_BBS = true;
    public static final long serialVersionUID = 6322150443228168192L;
    // end of configuration, go beyond this point at your own risk
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleGuild.class);

    private enum BCOp {
        NONE,
        DISBAND,
        EMBELMCHANGE
    }
    private final List<MapleGuildCharacter> members;
    private final String[] rankTitles = new String[5]; // 1 = master, 2 = jr, 5 = lowest member
    private final Map<Integer, List<Integer>> notifications = new LinkedHashMap<>();
    private String name;
    private int id;
    private int gp;
    private int logo;
    private int logoColor;
    private int leader;
    private int capacity;
    private int logoBG;
    private int logoBGColor;
    private String notice;
    private int signature;
    private boolean bDirty = true;
    private int allianceid;

    public MapleGuild(int guildid) { // retrieves the guild from database, with guildid
        members = new ArrayList<>();

        Connection con;

        try {
            con = DatabaseConnection.getConnection();
        } catch (Exception e) {
            log.error("unable to connect to database to load guild information.", e);
            return;
        }

        try {
            // first read the guild information
            PreparedStatement ps = con.prepareStatement("SELECT * FROM guilds WHERE guildid = " + guildid);
            ResultSet rs = ps.executeQuery();
            if (!rs.first()) { // no result... most likely to be someone from a disbanded guild that got rolled back
                // log.error("no result returned from guildid, id = " + guildid);
                id = -1;
                rs.close();
                ps.close();
                return;
            }

            id = guildid;
            name = rs.getString("name");
            gp = rs.getInt("GP");
            logo = rs.getInt("logo");
            logoColor = rs.getInt("logoColor");
            logoBG = rs.getInt("logoBG");
            logoBGColor = rs.getInt("logoBGColor");
            capacity = rs.getInt("capacity");
            for (int i = 1; i <= 5; i++) {
                rankTitles[i - 1] = rs.getString("rank" + i + "title");
            }
            leader = rs.getInt("leader");
            notice = rs.getString("notice");
            signature = rs.getInt("signature");
            allianceid = rs.getInt("allianceid");
            ps.close();
            rs.close();

            // add guild members
            ps = con.prepareStatement("SELECT id, name, level, job, guildrank, alliancerank FROM characters WHERE guildid = ? ORDER BY guildrank ASC, name ASC");
            ps.setInt(1, guildid);
            rs = ps.executeQuery();

            if (!rs.first()) { // no one in guild?!
                rs.close();
                ps.close();
                log.error("No members in guild. Impossible...");
                return;
            }

            do {
                members.add(new MapleGuildCharacter(rs.getInt("id"), rs.getInt("level"), rs.getString("name"), -1, rs.getInt("job"), rs.getInt("guildrank"), rs.getInt("alliancerank"), guildid, false));
            } while (rs.next());

            updateOnline();

            rs.close();
            ps.close();
        } catch (SQLException se) {
            log.error("unable to read guild information from sql", se);
        }
    }

    // function to create guild, returns the guild id if successful, 0 if not
    public static int createGuild(int leaderId, String name) {
        Connection con = DatabaseConnection.getConnection();
        try {
            Properties dbProp = new Properties();
            InputStreamReader is = new FileReader("db.properties");
            dbProp.load(is);
            is.close();

            PreparedStatement ps = con.prepareStatement("SELECT guildid FROM guilds WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();

            if (rs.first()) { // name taken
                rs.close();
                ps.close();
                return 0;
            }
            ps.close();
            rs.close();

            PreparedStatement ps2 = con.prepareStatement("INSERT INTO guilds (`leader`, `name`, `signature`) VALUES (?, ?, ?)");
            ps2.setInt(1, leaderId);
            ps2.setString(2, name);
            ps2.setInt(3, (int) System.currentTimeMillis());
            ps2.execute();
            ps2.close();

            PreparedStatement ps3 = con.prepareStatement("SELECT guildid FROM guilds WHERE leader = ?");
            ps3.setInt(1, leaderId);
            ResultSet rs2 = ps3.executeQuery();
            rs2.first();
            int ret = rs2.getInt("guildid");
            rs2.close();
            ps3.close();

            return ret;
        } catch (SQLException se) {
            log.error("SQL THROW", se);
            return 0;
        } catch (Exception e) {
            log.error("CREATE GUILD THROW", e);
            return 0;
        }
    }

    // null indicates successful invitation being sent
    // keep in mind that this will be called by a handler most of the time
    // so this will be running mostly on a channel server, unlike the rest
    // of the class
    public static MapleGuildResponse sendInvite(MapleClient c, String targetName) {
        MapleCharacter mc = c.getChannelServer().getPlayerStorage().getCharacterByName(targetName);
        if (mc == null) {
            return MapleGuildResponse.NOT_IN_CHANNEL;
        }

        if (mc.getGuildId() > 0) {
            return MapleGuildResponse.ALREADY_IN_GUILD;
        }

        mc.getClient().sendPacket(MaplePacketCreator.guildInvite(c.getPlayer().getGuildId(), c.getPlayer().getName()));

        return null;
    }

    public static void displayGuildRanks(MapleClient c, int npcid) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT `name`, `GP`, `logoBG`, `logoBGColor`, `logo`, `logoColor` FROM guilds ORDER BY `GP` DESC LIMIT 50");
            ResultSet rs = ps.executeQuery();
            c.sendPacket(MaplePacketCreator.showGuildRanks(npcid, rs));
            ps.close();
            rs.close();
        } catch (Exception e) {
            log.error("Failed to display guild ranks. ", e);
        }
    }

    public void buildNotifications() {
        // any function that calls this should be wrapped in
        // synchronized(notifications) to make sure that it doesn't
        // change before that function finishes with the updated notifications
        if (!bDirty) {
            return;
        }

        Set<Integer> chs = WorldRegistryImpl.getInstance().getChannelServer();

        if (notifications.keySet().size() != chs.size()) {
            notifications.clear();
            for (Integer ch : chs) {
                notifications.put(ch, new java.util.LinkedList<>());
            }
        } else {
            for (List<Integer> l : notifications.values()) {
                l.clear();
            }
        }

        synchronized (members) {
            for (MapleGuildCharacter mgc : members) {
                if (!mgc.isOnline()) {
                    continue;
                }

                List<Integer> ch = notifications.get(mgc.getChannel());
                if (ch == null) {
                    log.warn("Unable to connect to channel " + mgc.getChannel());
                } else {
                    ch.add(mgc.getId());
                }
            }
        }

        bDirty = false;
    }

    public void writeToDB() {
        writeToDB(false);
    }

    public void writeToDB(boolean bDisband) {
        Connection con;
        try {
            con = DatabaseConnection.getConnection();
        } catch (Exception e) {
            log.error("unable to connect to database to write guild information.", e);
            return;
        }

        try {
            if (!bDisband) {
                StringBuilder builder = new StringBuilder();
                builder.append("UPDATE guilds SET GP = ?, logo = ?, logoColor = ?, logoBG = ?, logoBGColor = ?, ");
                for (int i = 0; i < 5; i++) {
                    builder.append("rank").append(i + 1).append("title = ?, ");
                }

                builder.append("capacity = ?, notice = ? WHERE guildid = ?");

                PreparedStatement ps = con.prepareStatement(builder.toString());
                ps.setInt(1, gp);
                ps.setInt(2, logo);
                ps.setInt(3, logoColor);
                ps.setInt(4, logoBG);
                ps.setInt(5, logoBGColor);
                for (int i = 6; i < 11; i++) {
                    ps.setString(i, rankTitles[i - 6]);
                }
                ps.setInt(11, capacity);
                ps.setString(12, notice);
                ps.setInt(13, id);

                ps.execute();
                ps.close();
            } else {
                PreparedStatement ps = con.prepareStatement("UPDATE characters SET guildid = 0, guildrank = 5 WHERE guildid = ?");
                ps.setInt(1, id);
                ps.execute();
                ps.close();

                ps = con.prepareStatement("DELETE FROM guilds WHERE guildid = ?");
                ps.setInt(1, id);
                ps.execute();
                ps.close();

                this.broadcast(MaplePacketCreator.guildDisband(id));
            }
        } catch (SQLException se) {
            log.error(se.getLocalizedMessage(), se);
        }
    }

    public int getId() {
        return id;
    }

    public int getLeaderId() {
        return leader;
    }

    public int getGP() {
        return gp;
    }

    public int getLogo() {
        return logo;
    }

    public void setLogo(int l) {
        logo = l;
    }

    public int getLogoColor() {
        return logoColor;
    }

    public void setLogoColor(int c) {
        logoColor = c;
    }

    public int getLogoBG() {
        return logoBG;
    }

    public void setLogoBG(int bg) {
        logoBG = bg;
    }

    public int getLogoBGColor() {
        return logoBGColor;
    }

    public void setLogoBGColor(int c) {
        logoBGColor = c;
    }

    public String getNotice() {
        if (notice == null) {
            return "";
        }
        return notice;
    }

    public String getName() {
        return name;
    }

    public java.util.Collection<MapleGuildCharacter> getMembers() {
        return java.util.Collections.unmodifiableCollection(members);
    }

    public int getCapacity() {
        return capacity;
    }

    public int getSignature() {
        return signature;
    }

    public void broadcast(MaplePacket packet) {
        broadcast(packet, -1, BCOp.NONE);
    }

    public void broadcast(MaplePacket packet, int exception) {
        broadcast(packet, exception, BCOp.NONE);
    }

    // multi-purpose function that reaches every member of guild
    // (except the character with exceptionId)
    // in all channels with as little access to rmi as possible
    private void broadcast(MaplePacket packet, int exceptionId, BCOp bcop) {
        WorldRegistryImpl wr = WorldRegistryImpl.getInstance();
        Set<Integer> chs = wr.getChannelServer();

        synchronized (notifications) {
            if (bDirty) {
                buildNotifications();
            }

            // now call the channelworldinterface
            try {
                ChannelWorldInterface cwi;
                for (Integer ch : chs) {
                    cwi = wr.getChannel(ch);
                    if (!notifications.get(ch).isEmpty()) {
                        if (bcop == BCOp.DISBAND) {
                            cwi.setGuildAndRank(notifications.get(ch), 0, 5, exceptionId);
                        } else if (bcop == BCOp.EMBELMCHANGE) {
                            cwi.changeEmblem(id, notifications.get(ch), new MapleGuildSummary(this));
                        } else {
                            cwi.sendPacket(notifications.get(ch), packet, exceptionId);
                        }
                    }
                }
            } catch (RemoteException re) {
                log.error("Failed to contact channel(s) for broadcast.", re);
            }
        }
    }

    public void guildMessage(MaplePacket serverNotice) {
        for (MapleGuildCharacter mgc : members) {
            for (ChannelServer cs : ChannelServer.getAllInstances()) {
                if (cs.getPlayerStorage().getCharacterById(mgc.getId()) != null) {
                    MapleCharacter chr = cs.getPlayerStorage().getCharacterById(mgc.getId());
                    chr.getClient().sendPacket(serverNotice);
                    break;
                }
            }
        }
    }

    public void setOnline(int cid, boolean online, int channel) {
        boolean bBroadcast = true;
        for (MapleGuildCharacter mgc : members) {
            if (mgc.getId() == cid) {
                if (mgc.isOnline() && online) {
                    bBroadcast = false;
                }
                mgc.setOnline(online);
                mgc.setChannel(channel);
                break;
            }
        }

        if (bBroadcast) {
            this.broadcast(MaplePacketCreator.guildMemberOnline(id, cid, online), cid);
        }
        bDirty = true; // member formation has changed, update notifications
    }

    public void updateOnline() {
        WorldRegistryImpl wr = WorldRegistryImpl.getInstance();
        try {
            for (MapleGuildCharacter mgc : members) {
                for (ChannelWorldInterface cwi : wr.getAllChannelServers()) {
                    if (cwi.playerIsOnline(mgc.getId())) {
                        setOnline(mgc.getId(), true, cwi.getChannelId());
                    }
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public void guildChat(String name, int cid, String msg) {
        this.broadcast(MaplePacketCreator.multiChat(name, msg, 2), cid);
    }

    public String getRankTitle(int rank) {
        return rankTitles[rank - 1];
    }

    public int addGuildMember(MapleGuildCharacter mgc) {
        // first of all, insert it into the members
        // keeping alphabetical order of lowest ranks ;)
        synchronized (members) {
            if (members.size() >= capacity) {
                return 0;
            }

            for (int i = members.size() - 1; i >= 0; i--) {
                // we will stop going forward when
                // 1. we're done with rank 5s, or
                // 2. the name comes alphabetically before the new member
                if (members.get(i).getGuildRank() < 5 || members.get(i).getName().compareTo(mgc.getName()) < 0) {
                    // then we should add it at the i+1 location
                    members.add(i + 1, mgc);
                    bDirty = true;
                    break;
                }
            }
        }

        this.broadcast(MaplePacketCreator.newGuildMember(mgc));

        return 1;
    }

    public void leaveGuild(MapleGuildCharacter mgc) {
        this.broadcast(MaplePacketCreator.memberLeft(mgc, false));

        synchronized (members) {
            members.remove(mgc);
            bDirty = true;
        }
    }

    public void expelMember(MapleGuildCharacter initiator, String name, int cid) {
        synchronized (members) {
            java.util.Iterator<MapleGuildCharacter> itr = members.iterator();
            MapleGuildCharacter mgc;
            while (itr.hasNext()) {
                mgc = itr.next();
                if (mgc.getId() == cid && initiator.getGuildRank() < mgc.getGuildRank()) {
                    this.broadcast(MaplePacketCreator.memberLeft(mgc, true));
                    itr.remove();
                    bDirty = true;
                    // i hate global for not saying who expelled
                    this.broadcast(MaplePacketCreator.serverNotice(5, initiator.getName() + " has expelled " + mgc.getName() + "."));

                    try {
                        if (mgc.isOnline()) {
                            WorldRegistryImpl.getInstance().getChannel(mgc.getChannel()).setGuildAndRank(cid, 0, 5);
                        } else {
                            String sendTo = mgc.getName();
                            String sendFrom = initiator.getName();
                            String msg = "You have been expelled from the guild.";

                            try {
                                initiator.getName();
                                MaplePacketCreator.sendUnkwnNote(sendTo, msg, sendFrom);
                            } catch (SQLException e) {
                                log.error("SAVING NOTE", e);
                            }
                            WorldRegistryImpl.getInstance().getChannel(1).setOfflineGuildStatus((short) 0, (byte) 5, cid);
                        }
                    } catch (RemoteException re) {
                        re.printStackTrace();
                        return;
                    }
                    return;
                }
            }

            log.error("Unable to find member with name " + name + " and id " + cid);
        }
    }

    public void changeRank(int cid, int newRank) {
        for (MapleGuildCharacter mgc : members) {
            if (cid == mgc.getId()) {
                try {
                    if (mgc.isOnline()) {
                        WorldRegistryImpl.getInstance().getChannel(mgc.getChannel()).setGuildAndRank(cid, id, newRank);
                    } else {
                        WorldRegistryImpl.getInstance().getChannel(1).setOfflineGuildStatus((short) id, (byte) newRank, cid);
                    }
                } catch (RemoteException re) {
                    re.printStackTrace();
                    return;
                }

                mgc.setGuildRank(newRank);
                this.broadcast(MaplePacketCreator.changeRank(mgc));
                return;
            }
        }

        // it should never get to this point unless cid was incorrect o_O
        log.info("INFO: unable to find the correct id for changeRank({}, {})", cid, newRank);
    }

    public void setGuildNotice(String notice) {
        this.notice = notice;
        writeToDB();

        this.broadcast(MaplePacketCreator.guildNotice(id, notice));
    }

    public void memberLevelJobUpdate(MapleGuildCharacter mgc) {
        for (MapleGuildCharacter member : members) {
            if (mgc.equals(member)) {
                member.setJobId(mgc.getJobId());
                member.setLevel(mgc.getLevel());
                this.broadcast(MaplePacketCreator.guildMemberLevelJobUpdate(mgc));
                break;
            }
        }
    }

    public void changeRankTitle(String[] ranks) {
        System.arraycopy(ranks, 0, rankTitles, 0, 5);
        this.broadcast(MaplePacketCreator.rankTitleChange(id, ranks));
        this.writeToDB();
    }

    public void disbandGuild() {
        this.writeToDB(true);
        this.broadcast(null, -1, BCOp.DISBAND);
    }

    public void setGuildEmblem(short bg, byte bgcolor, short logo, byte logocolor) {
        logoBG = bg;
        logoBGColor = bgcolor;
        this.logo = logo;
        logoColor = logocolor;
        this.writeToDB();

        this.broadcast(null, -1, BCOp.EMBELMCHANGE);
    }

    public MapleGuildCharacter getMGC(int cid) {
        for (MapleGuildCharacter mgc : members) {
            if (mgc.getId() == cid) {
                return mgc;
            }
        }
        return null;
    }

    public boolean increaseCapacity() {
        if (capacity >= 100) {
            return false;
        }

        capacity += 5;
        this.writeToDB();

        this.broadcast(MaplePacketCreator.guildCapacityChange(id, capacity));

        return true;
    }

    public void gainGP(int amount) {
        gp += amount;
        this.writeToDB();
        this.broadcast(MaplePacketCreator.updateGP(id, gp));
        this.broadcast(MaplePacketCreator.serverNotice(5, "The guild has gained " + amount + " GP"));
    }

    public int getAllianceId() {
        return allianceid;
    }

    public void setAllianceId(int aid) {
        allianceid = aid;
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("UPDATE guilds SET allianceId = ? WHERE guildid = ?");
            ps.setInt(1, aid);
            ps.setInt(2, id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}