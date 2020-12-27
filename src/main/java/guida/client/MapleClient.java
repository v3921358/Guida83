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

package guida.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.ReplayingDecoder;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import guida.database.DatabaseConnection;
import guida.database.DatabaseException;
import guida.net.MaplePacket;
import guida.net.MaplePacketHandler;
import guida.net.PacketProcessor;
import guida.net.channel.ChannelServer;
import guida.net.login.LoginServer;
import guida.net.netty.ByteBufAccessor;
import guida.net.world.MapleMessengerCharacter;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.guild.MapleGuildCharacter;
import guida.net.world.remote.WorldChannelInterface;
import guida.scripting.npc.NPCConversationManager;
import guida.scripting.npc.NPCScriptManager;
import guida.scripting.quest.QuestActionManager;
import guida.scripting.quest.QuestScriptManager;
import guida.server.MapleSquad;
import guida.server.MapleSquadType;
import guida.server.TimerManager;
import guida.server.playerinteractions.MapleTrade;
import guida.tools.HexTool;
import guida.tools.IPAddressTool;
import guida.tools.MapleAESOFB;
import guida.tools.MapleCustomEncryption;
import guida.tools.MaplePacketCreator;
import guida.tools.Randomizer;
import org.mindrot.jbcrypt.BCrypt;

import javax.script.ScriptEngine;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;

public class MapleClient extends ChannelInboundHandlerAdapter {

    public static final int LOGIN_NOTLOGGEDIN = 0;
    public static final int LOGIN_SERVER_TRANSITION = 1;
    public static final int LOGIN_LOGGEDIN = 2;
    public static final int LOGIN_WAITING = 3;
    public static final int ENTERING_PIN = 4;
    public static final int PIN_CORRECT = 5;
    public static final int VIEW_ALL_CHAR = 6;
    public static final String CLIENT_KEY = "CLIENT";
    public final static short CLIENT_VERSION = 83;
    //public static final byte[] autoban = { 68, 69, 76, 69, 84, 69, 32, 70, 82, 79, 77, 32, 99, 104, 97, 114, 97, 99, 116, 101, 114, 115 };
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleClient.class);
    private final MapleAESOFB send;
    private final MapleAESOFB receive;
    private final Channel iochannel;
    private final Object disconnectLock = new Object();
    private final Set<String> macs = new HashSet<>();
    private final Map<String, ScriptEngine> engines = new HashMap<>();
    private boolean disconnected = false;
    private MapleCharacter player;
    private int channel = 1;
    private int accId = 0;
    private Timestamp createDate;
    private boolean loggedIn = false;
    private boolean serverTransition = false;
    private int birthday = 0;
    private Calendar tempban = null;
    private byte gender;
    private int pin = 10000;
    private String pic = "";
    private byte passwordTries = 0;
    private byte pinTries = 0;
    private String accountName;
    private String banreason;
    private int world;
    private long lastPong;
    private boolean GM;
    private byte greason = 1;
    private int forumUserId;
    private boolean hasPacketLog = false;
    private BufferedWriter packetLog = null;
    //private ScheduledFuture<?> idleTask = null;
    private int lastAction = 0;
    private FileOutputStream pL_fos = null;
    private OutputStreamWriter pL_osw = null;
    private ScheduledFuture<?> idleDisconnect = null;
    private CountDownLatch disconnectLatch = null;

    public MapleClient() {
        send = receive = null;
        iochannel = null;
    }

    public MapleClient(Channel ch) {
        this.send = new MapleAESOFB(Randomizer.nextBytes(4), (short) (0xFFFF - CLIENT_VERSION));
        this.receive = new MapleAESOFB(Randomizer.nextBytes(4), CLIENT_VERSION);
        this.iochannel = ch;
        this.disconnectLatch = new CountDownLatch(1);
    }

    /**
     * Gets the special server IP if the client matches a certain subnet.
     *
     * @param clientIPAddress The IP address of the client as a dotted quad.
     * @param channel         The requested channel to match with the subnet.
     * @return <code>0.0.0.0</code> if no subnet matched, or the IP if the subnet matched.
     */
    public static String getChannelServerIPFromSubnet(String clientIPAddress, int channel) {
        long ipAddress = IPAddressTool.dottedQuadToLong(clientIPAddress);
        Properties subnetInfo = LoginServer.getInstance().getSubnetInfo();

        if (subnetInfo.contains("guida.net.login.subnetcount")) {
            int subnetCount = Integer.parseInt(subnetInfo.getProperty("guida.net.login.subnetcount"));
            for (int i = 0; i < subnetCount; i++) {
                String[] connectionInfo = subnetInfo.getProperty("guida.net.login.subnet." + i).split(":");
                long subnet = IPAddressTool.dottedQuadToLong(connectionInfo[0]);
                long channelIP = IPAddressTool.dottedQuadToLong(connectionInfo[1]);
                int channelNumber = Integer.parseInt(connectionInfo[2]);

                if ((ipAddress & subnet) == (channelIP & subnet) && channel == channelNumber) {
                    return connectionInfo[1];
                }
            }
        }

        return "0.0.0.0";
    }

    public static String getLogMessage(MapleClient cfor, String message) {
        return getLogMessage(cfor, message, new Object[0]);
    }

    public static String getLogMessage(MapleCharacter cfor, String message) {
        return getLogMessage(cfor == null ? null : cfor.getClient(), message);
    }

    public static String getLogMessage(MapleCharacter cfor, String message, Object... parms) {
        return getLogMessage(cfor == null ? null : cfor.getClient(), message, parms);
    }

    public static String getLogMessage(MapleClient cfor, String message, Object... parms) {
        StringBuilder builder = new StringBuilder();
        if (cfor != null) {
            if (cfor.player != null) {
                builder.append("<");
                builder.append(MapleCharacterUtil.makeMapleReadable(cfor.player.getName()));
                builder.append(" (cid: ");
                builder.append(cfor.player.getId());
                builder.append(")> ");
            }
            if (cfor.accountName != null) {
                builder.append("(Account: ");
                builder.append(MapleCharacterUtil.makeMapleReadable(cfor.accountName));
                builder.append(") ");
            }
        }
        builder.append("\r\n");
        builder.append(message);
        for (Object parm : parms) {
            int start = builder.indexOf("{}");
            builder.replace(start, start + 2, parm.toString());
        }
        return builder.toString();
    }

    public static int getAccIdFromCharName(String charName) {
        Connection con = DatabaseConnection.getConnection();

        try {
            PreparedStatement ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
            ps.setString(1, charName);
            ResultSet rs = ps.executeQuery();

            int ret = -1;
            if (rs.next()) {
                ret = rs.getInt("accountid");
            }
            rs.close();
            ps.close();
            return ret;
        } catch (SQLException e) {
            log.error("SQL THROW");
        }
        return -1;
    }

    public static void changeChannel(MapleClient c, int channel, boolean gm) {
        final MapleCharacter chr = c.player;
        if (!gm) {
            if (!chr.isAlive()) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (chr.isBanned()) {
                chr.dropMessage("You have been banned by a GameMaster, you will not be able to do anything!");
                return;
            }
            if (!ChannelServer.getInstance(channel).getMapFactory().getMap(chr.getMapId()).canEnter()) {
                chr.dropMessage("The channel you are trying to enter has a blocked map, change maps and cc again or choose a different channel.");
                return;
            }
        }
        final String ip = c.getChannelServer().getIP(channel);
        final String[] socket = ip.split(":");
        if (chr.getTrade() != null) {
            MapleTrade.cancelTrade(chr);
        }
        if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
            chr.cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
        }
        if (chr.getBuffedValue(MapleBuffStat.PUPPET) != null) {
            chr.cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
        }
        if (chr.getBuffedValue(MapleBuffStat.SUMMON) != null) {
            chr.cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
        }
        if (!chr.getDiseases().isEmpty()) {
            chr.cancelAllDebuffs();
        }
        chr.stopAllTimers();
        if (chr.getCheatTracker() != null) {
            chr.getCheatTracker().dispose();
        }
        try {
            final WorldChannelInterface wci = c.getChannelServer().getWorldInterface();
            wci.addBuffsToStorage(chr.getId(), chr.getAllBuffs());
            if (chr.getMessenger() != null) {
                wci.silentLeaveMessenger(chr.getMessenger().getId(), new MapleMessengerCharacter(chr));
            }
        } catch (RemoteException e) {
            c.getChannelServer().reconnectWorld();
        }
        c.setPacketLog(false);
        chr.saveToDB(true);
        chr.resetCooldowns();
        chr.getMap().removePlayer(chr);
        chr.destroyAllOwnedInteractableObjects();
        c.getChannelServer().removePlayer(chr);
        c.updateLoginState(MapleClient.LOGIN_SERVER_TRANSITION);
        try {
            MapleCharacter.setLoggedInState(chr.getId(), 0);
            c.player = null;
            c.sendPacket(MaplePacketCreator.getChannelChange(InetAddress.getByName(socket[0]), Integer.parseInt(socket[1])));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        disconnected(false);
        super.channelInactive(ctx);
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        iochannel.pipeline().addBefore("MapleClient", "IdleStateHandler", new IdleStateHandler(20, 20, 0));
        iochannel.pipeline().addBefore("MapleClient", "PacketDecoder", new PacketDecoder());
        iochannel.pipeline().addBefore("MapleClient", "PacketEncoder", new PacketEncoder());
        iochannel.writeAndFlush(Unpooled.wrappedBuffer(MaplePacketCreator.getHello(CLIENT_VERSION, send.getIv(), receive.getIv(), false).getBytes()));
        super.channelActive(ctx);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            sendPing();
        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        byte[] byteArr = (byte[]) msg;
        ByteBufAccessor bba = new ByteBufAccessor(Unpooled.wrappedBuffer(byteArr).order(ByteOrder.LITTLE_ENDIAN));
        short header = bba.readShort();
        MaplePacketHandler h = PacketProcessor.getProcessor().getHandler(header);
        if (h == null) {
            log.info("Unhandled packet{}.\n{}\n{}", (player != null ? " from " + player.getName() : ""),
                    HexTool.toString(byteArr), HexTool.toStringFromAscii(byteArr));
        } else if (!h.validateState(this)) {
            log.debug("Client failed state validation by packet handler {}.", h.getClass().getSimpleName());
        } else {
            if (hasPacketLog()) {
                switch (h.getClass().getSimpleName()) {
                    case "NPCAnimation":
                    case "NoOpHandler":
                    case "MovePlayerHandler":
                    case "SpecialMoveHandler":
                    case "HealOvertimeHandler":
                    case "KeepAliveHandler":
                    case "MoveLifeHandler":
                    case "AutoAggroHandler":
                        break;
                    default:
                        final StringBuilder plogs = new StringBuilder("Received packet handled by ");
                        plogs.append(h.getClass().getSimpleName())
                                .append(" (")
                                .append(byteArr.length)
                                .append(")\r\n")
                                .append(HexTool.toString(byteArr))
                                .append("\r\n")
                                .append(HexTool.toStringFromAscii(byteArr))
                                .append("\r\n\r\n");
                        writePacketLog(plogs.toString());
                        break;
                }
            }
            h.handlePacket(bba, this);
        }
        super.channelRead(ctx, msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof IOException) {
            log.debug("IOException caught.", cause);
        } else {
            log.error("Exception caught.", cause);
        }
    }

    public ChannelFuture sendPacket(final MaplePacket mp) {
        if (iochannel == null) {
            return null;
        }
        return iochannel.writeAndFlush(mp).addListener((ChannelFutureListener) future -> {
            if (mp.getOnSend() != null) {
                mp.getOnSend().run();
            }
        });
    }

    public String getIP() {
        return ((InetSocketAddress) iochannel.remoteAddress()).getAddress().getHostAddress();
    }

    public MapleCharacter getPlayer() {
        return player;
    }

    public void setPlayer(MapleCharacter player) {
        this.player = player;
    }

    public void sendCharList(int server) {
        sendPacket(MaplePacketCreator.getCharList(this, server));
    }

    public List<MapleCharacter> loadCharacters(int serverId) { // TODO make this less costly zZz
        ArrayList<MapleCharacter> chars = new ArrayList<>();
        for (CharNameAndId cni : loadCharactersInternal(serverId)) {
            try {
                chars.add(MapleCharacter.loadCharFromDB(cni.id, this, false));
            } catch (SQLException e) {
                log.error("Loading characters failed", e);
            }
        }
        return chars;
    }

    public List<String> loadCharacterNames(int serverId) {
        List<String> chars = new LinkedList<>();
        for (CharNameAndId cni : loadCharactersInternal(serverId)) {
            chars.add(cni.name);
        }
        return chars;
    }

    private List<CharNameAndId> loadCharactersInternal(int serverId) {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps;
        List<CharNameAndId> chars = new LinkedList<>();
        try {
            ps = con.prepareStatement("SELECT id, name FROM characters WHERE accountid = ? AND world = ?");
            ps.setInt(1, accId);
            ps.setInt(2, serverId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                chars.add(new CharNameAndId(rs.getString("name"), rs.getInt("id")));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            log.error("THROW", e);
        }
        return chars;
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    private Calendar getTempBanCalendar(ResultSet rs) throws SQLException {
        Calendar lTempban = Calendar.getInstance();
        long blubb = rs.getTimestamp("tempban").getTime();
        if (blubb == 0) { // basically if timestamp in db is 0000-00-00
            lTempban.setTimeInMillis(0);
            return lTempban;
        }
        Calendar today = Calendar.getInstance();
        lTempban.setTimeInMillis(rs.getTimestamp("tempban").getTime());
        if (today.getTimeInMillis() < lTempban.getTimeInMillis()) {
            return lTempban;
        }

        lTempban.setTimeInMillis(0);
        return lTempban;
    }

    public Calendar getTempBanCalendar() {
        return tempban;
    }

    public byte getBanReason() {
        return greason;
    }

    public String getPermabanReason() {
        return banreason;
    }

    public boolean hasBannedIP() {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM ipbans WHERE ? LIKE CONCAT(ip, '%')");
            ps.setString(1, getIP());
            ResultSet rs = ps.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                rs.close();
                ps.close();
                return true;
            }
            rs.close();
            ps.close();
        } catch (SQLException ex) {
            log.error("Error checking ip bans", ex);
            return true;
        }
        return false;
    }

    public boolean hasBannedMac() {
        if (macs.isEmpty()) {
            return false;
        }
        int i = 0;
        try {
            Connection con = DatabaseConnection.getConnection();
            StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM macbans WHERE mac IN (");
            for (i = 0; i < macs.size(); i++) {
                sql.append("?");
                if (i != macs.size() - 1) {
                    sql.append(", ");
                }
            }
            sql.append(")");
            PreparedStatement ps = con.prepareStatement(sql.toString());
            i = 0;
            for (String mac : macs) {
                i++;
                ps.setString(i, mac);
            }
            ResultSet rs = ps.executeQuery();
            rs.next();
            if (rs.getInt(1) > 0) {
                rs.close();
                ps.close();
                return true;
            }
            rs.close();
            ps.close();
        } catch (SQLException ex) {
            log.error("Error checking mac bans", ex);
            return true;
        }
        return false;
    }

    private void loadMacsIfNescessary() throws SQLException {
        if (macs.isEmpty()) {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT macs FROM accounts WHERE id = ?");
            ps.setInt(1, accId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String accMACs = rs.getString("macs");
                if (accMACs != null && accMACs.length() > 16) {
                    String[] macData = accMACs.split(", ");
                    for (String mac : macData) {
                        if (mac.length() != 0) {
                            macs.add(mac);
                        }
                    }
                }
            } else {
                throw new RuntimeException("No valid account associated with this client.");
            }
            rs.close();
            ps.close();
        }
    }

    public void banMacs() {
        Connection con = DatabaseConnection.getConnection();
        try {
            loadMacsIfNescessary();
            List<String> filtered = new LinkedList<>();
            PreparedStatement ps = con.prepareStatement("SELECT filter FROM macfilters");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                filtered.add(rs.getString("filter"));
            }
            rs.close();
            ps.close();
            ps = con.prepareStatement("INSERT INTO macbans (mac) VALUES (?)");
            for (String mac : macs) {
                boolean matched = false;
                for (String filter : filtered) {
                    if (mac.matches(filter) || mac.equalsIgnoreCase(filter)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    ps.setString(1, mac);
                    try {
                        ps.executeUpdate();
                    } catch (SQLException e) {
                        // can fail because of UNIQUE key, we dont care
                    }
                }
            }
            ps.close();
        } catch (SQLException e) {
            log.error("Error banning MACs", e);
        }
    }

    /**
     * Returns 0 on success, a state to be used for
     * {@link MaplePacketCreator#getLoginFailed(int)} otherwise.
     *
     * @param success
     * @return The state of the login.
     */
    public int finishLogin(boolean success) {
        if (success) {
            synchronized (MapleClient.class) {
                if (getLoginState() > MapleClient.LOGIN_NOTLOGGEDIN && getLoginState() != MapleClient.LOGIN_WAITING) { // already loggedin
                    loggedIn = false;
                    return 7;
                }
                updateLoginState(MapleClient.ENTERING_PIN);
            }
            return 0;
        } else {
            return 10;
        }
    }

    public int login(String login, String pwd) {
        int loginok = 5;
        boolean ipMacBanned;
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT id, password, gender, pin, pic, banned, tempban, greason, gm, createDate, banreason, macs, forumuserid FROM accounts WHERE name = ?");
            ps.setString(1, login);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                accId = rs.getInt("id");
                String passhash = rs.getString("password");
                gender = rs.getByte("gender");
                pin = rs.getInt("pin");
                pic = rs.getString("pic");
                int banned = rs.getInt("banned");
                tempban = getTempBanCalendar(rs);
                greason = rs.getByte("greason");
                banreason = rs.getString("banreason");
                int iGM = rs.getInt("gm");
                GM = iGM > 0;
                createDate = rs.getTimestamp("createDate");
                forumUserId = rs.getInt("forumuserid");
                String accMACs = rs.getString("macs");

                if (accMACs != null && accMACs.length() > 16) {
                    String[] macData = accMACs.split(", ");
                    for (String mac : macData) {
                        if (mac.length() != 0) {
                            macs.add(mac);
                        }
                    }
                }
                ipMacBanned = hasBannedIP() || hasBannedMac();
                if (banned == 0 && !ipMacBanned || banned == -1) {
                    PreparedStatement ips = con.prepareStatement("INSERT INTO iplog (accountid, ip) VALUES (?, ?)");
                    ips.setInt(1, accId);
                    ips.setString(2, getIP());
                    ips.executeUpdate();
                    ips.close();
                }
                ps.close();

                if (banned == 1) {
                    loginok = 3;
                } else {
                    if (banned == -1) {
                        unban();
                        loginok = 0;
                    }
                    if (getLoginState() > MapleClient.LOGIN_NOTLOGGEDIN) { // already loggedin
                        loggedIn = false;
                        loginok = 7;
                    } else {
                        // Check if the passwords are correct here.
                        if (BCrypt.checkpw(pwd, passhash)) {
                            loginok = 0;
                        } else {
                            loggedIn = false;
                            loginok = 4;
                            passwordTries += 1;
                            if (passwordTries == 3) {
                                if (tempban.getTimeInMillis() == 0) {
                                    authenticationFailureBan();
                                }
                                disconnect();
                            }
                        }
                    }
                }
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            log.error("ERROR", e);
        }
        return loginok;
    }

    public void unban() {
        try {
            Connection con = DatabaseConnection.getConnection();
            loadMacsIfNescessary();
            PreparedStatement ps;
            if (!macs.isEmpty()) {
                int i;
                StringBuilder sql = new StringBuilder("DELETE FROM macbans WHERE mac IN (");
                for (i = 0; i < macs.size(); i++) {
                    sql.append("?");
                    if (i != macs.size() - 1) {
                        sql.append(", ");
                    }
                }
                sql.append(")");
                ps = con.prepareStatement(sql.toString());
                i = 0;
                for (String mac : macs) {
                    i++;
                    ps.setString(i, mac);
                }
                ps.executeUpdate();
                ps.close();
            }
            ps = con.prepareStatement("DELETE FROM ipbans WHERE ip LIKE CONCAT(?, '%')");
            ps.setString(1, getIP());
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("UPDATE accounts SET banned = 0 WHERE id = ?");
            ps.setInt(1, accId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            log.error("Error while unbanning", e);
        }
    }

    public void authenticationFailureBan() {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET tempban = ?, greason = ? WHERE id = ?");
            Timestamp TS = new Timestamp(System.currentTimeMillis() + 600000L);
            ps.setTimestamp(1, TS);
            ps.setInt(2, 99);
            ps.setInt(3, accId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            log.error("Error while tempbanning", ex);
        }
    }

    private String formatMacAddress(String mac) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length(); i++) {
            sb.append(mac, i, i + 2);
            if (i % 2 == 0 && i != mac.length() - 2) {
                sb.append("-");
            }
            i++;
        }
        return sb.toString();
    }

    public void updateMacs(String macData, String macData2) {
        if (macData2.contains("_")) {
            String additionalMac = formatMacAddress(macData2.split("_")[0]);
            if (additionalMac.length() > 11 && !macData.contains(additionalMac)) {
                if (macData.length() > 16) {
                    macData += ", ";
                }
                macData += additionalMac;
            }
        }
        for (String mac : macData.split(", ")) {
            if (!mac.equals("00-00-00-00-00-00")) {
                macs.add(mac);
            }
        }
        if (macs.isEmpty()) {
            disconnect();
            return;
        }
        StringBuilder newMacData = new StringBuilder();
        Iterator<String> iter = macs.iterator();
        while (iter.hasNext()) {
            String cur = iter.next();
            newMacData.append(cur);
            if (iter.hasNext()) {
                newMacData.append(", ");
            }
        }
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET macs = ? WHERE id = ?");
            ps.setString(1, newMacData.toString());
            ps.setInt(2, accId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            log.error("Error saving MACs", e);
        }
    }

    public void insertLog() {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("DELETE FROM accounts_ip WHERE accountId = ?");
            ps.setInt(1, accId);
            ps.execute();
            ps.close();
            ps = con.prepareStatement("INSERT INTO accounts_ip (accountId, ip) VALUES (?, ?)");
            ps.setInt(1, accId);
            ps.setString(2, getIP());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean checkAccount(int id) {
        Connection con = DatabaseConnection.getConnection();
        String ip = "";
        try {
            PreparedStatement ps = con.prepareStatement("SELECT ip FROM accounts_ip WHERE accountId = ?");
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ip = rs.getString("ip");
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return ip.compareTo(getIP()) == 0;
    }

    public void setAccID(int id) {
        accId = id;
    }

    public int getAccID() {
        return accId;
    }

    public void loadForumUserId() {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT forumuserid FROM accounts WHERE id = ?");
            ps.setInt(1, accId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                forumUserId = rs.getInt("forumuserid");
            }

            rs.close();
            ps.close();
        } catch (SQLException s) {
            log.error("Error loading forum user id", s);
        }
    }

    public int getPin() {
        return pin;
    }

    public void setPin(int pin) {
        this.pin = pin;
    }

    public String getPIC() {
        return pic;
    }

    public void setPIC(String pic) {
        if (pic.length() > 5) {
            this.pic = pic;
        } else {
            disconnect();
        }
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        try {
            ps = con.prepareStatement("UPDATE accounts SET pic = ? WHERE id = ?");
            ps.setString(1, pic);
            ps.setInt(2, accId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            log.error("Error saving PIC", e);
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
        }
    }

    public int getGender() {
        return gender;
    }

    public void setGender(byte gender) {
        this.gender = gender;
    }

    public Timestamp getCreateDate() {
        return createDate;
    }

    public void updateGenderandPin() {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET gender = ?, pin = ? WHERE id = ?");
            ps.setInt(1, getGender());
            ps.setInt(2, pin);
            ps.setInt(3, accId);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            log.error("ERROR", e);
        }
    }

    public void setPasswordTries(byte tries) {
        passwordTries = tries;
    }

    public byte getPinTries() {
        return pinTries;
    }

    public void setPinTries(byte tries) {
        pinTries = tries;
    }

    public int getForumUserId() {
        return forumUserId;
    }

    public int getTotalChars() {
        int characters = 0;
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM characters WHERE accountid = ?");
            ps.setInt(1, accId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                characters = rs.getInt(1);
            }
            rs.close();
            ps.close();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return characters;
    }

    public boolean hasCharacter(int charId) {
        Connection con = DatabaseConnection.getConnection();
        boolean ret = false;
        try {
            PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM characters WHERE id = ? AND accountid = ?");
            ps.setInt(1, charId);
            ps.setInt(2, accId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ret = rs.getInt(1) > 0;
            }
            rs.close();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public void cancelIdleDisconnect() {
        if (idleDisconnect != null) {
            idleDisconnect.cancel(true);
        }
        idleDisconnect = null;
    }

    public void updateLoginState(int newstate) {
        if (newstate == 1 || newstate >= 4 && newstate <= 6) {
            if (idleDisconnect != null) {
                idleDisconnect.cancel(true);
            }
            idleDisconnect = TimerManager.getInstance().schedule(() -> {
                int state = getLoginState();
                if (state == 1 || state >= 4 && state <= 6) {
                    disconnect();
                }
            }, 300000);
        }
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET loggedin = ?, lastlogin = CURRENT_TIMESTAMP() WHERE id = ?");
            ps.setInt(1, newstate);
            ps.setInt(2, accId);
            ps.executeUpdate();
            ps.close();
            if (player != null) { // So we can tell who's actually online
                ps = con.prepareStatement("UPDATE characters SET loggedin = ? WHERE id = ?");
                ps.setInt(1, newstate);
                ps.setInt(2, player.getId());
                ps.executeUpdate();
                ps.close();
            } else if (newstate == 0) {
                ps = con.prepareStatement("UPDATE characters SET loggedin = 0 WHERE accountid = ?");
                ps.setInt(1, accId);
                ps.executeUpdate();
                ps.close();
            }
        } catch (SQLException e) {
            // log.error("ERROR", e);
            // We all know this only errors when we shutdown the server because the database connections have been closed.
            // No need to spam the Channel Server console like crazy.
        }
        if (newstate == MapleClient.LOGIN_NOTLOGGEDIN || newstate == MapleClient.LOGIN_WAITING) {
            loggedIn = false;
            serverTransition = false;
        } else if (newstate == MapleClient.ENTERING_PIN || newstate == MapleClient.PIN_CORRECT || newstate == MapleClient.VIEW_ALL_CHAR) {
            loggedIn = true;
            serverTransition = false;
        } else {
            serverTransition = newstate == MapleClient.LOGIN_SERVER_TRANSITION;
            loggedIn = !serverTransition;
        }
    }

    public int getLoginState() {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps;
            ps = con.prepareStatement("SELECT loggedin, lastlogin, DATE_FORMAT(birthday, \"%Y%m%d\") AS dfbdymd FROM accounts WHERE id = ?");
            ps.setInt(1, accId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                ps.close();
                throw new DatabaseException("Everything sucks");
            }
            birthday = rs.getString("dfbdymd") != null ? Integer.parseInt(rs.getString("dfbdymd")) : 0;
            int state = rs.getInt("loggedin");
            if (state == MapleClient.LOGIN_SERVER_TRANSITION) {
                Timestamp ts = rs.getTimestamp("lastlogin");
                long t = ts.getTime();
                long now = System.currentTimeMillis();
                if (t + 30000 < now) { // connecting to chanserver timeout
                    state = MapleClient.LOGIN_NOTLOGGEDIN;
                    updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN);
                }
            }
            rs.close();
            ps.close();
            loggedIn = state == MapleClient.LOGIN_LOGGEDIN || state == MapleClient.ENTERING_PIN || state == MapleClient.PIN_CORRECT || state == MapleClient.VIEW_ALL_CHAR;
            return state;
        } catch (SQLException e) {
            loggedIn = false;
            log.error("ERROR", e);
            throw new DatabaseException("Error getting login state: ", e);
        }
    }

    public boolean checkBirthDate(int idate) {
        return idate == birthday;
    }

    private void disconnected(boolean force) {
        try {
            if (iochannel == null || disconnected || (!force && getChannelServer() != null && getChannelServer().hasFinishedShutdown())) {
                return;
            }
            synchronized (disconnectLock) {
                if (disconnected) {
                    return;
                }
                disconnected = true;
                cancelIdleDisconnect();
                MapleCharacter chr = player;
                if (chr != null && loggedIn) {
                    chr.setLastDisconnection(System.currentTimeMillis());
                    if (chr.getTrade() != null) {
                        try {
                            MapleTrade.cancelTrade(chr);
                        } catch (Throwable t) {
                            log.error("Error cancelling trade in disconnected(). Continuing..", t);
                        }
                    }
                    if (!chr.getAllBuffs().isEmpty()) {
                        chr.cancelAllBuffs();
                    }
                    try {
                        if (chr.getEventInstance() != null) {
                            chr.getEventInstance().playerDisconnected(chr);
                        }
                    } catch (Throwable t) {
                        log.error("Error in event instance playerDisconnected. Continuing..", t);
                    }
                    if (NPCScriptManager.getInstance() != null) {
                        NPCScriptManager.getInstance().dispose(this);
                    }
                    if (QuestScriptManager.getInstance() != null) {
                        QuestScriptManager.getInstance().dispose(this);
                    }
                    if (chr.getPlayerShop() != null) {
                        chr.disposePlayerShop();
                    }
                    chr.stopAllTimers();

                    chr.getCheatTracker().dispose();
                    //String sockAddr = session.getRemoteAddress().toString();
                    //LoginServer.getInstance().removeConnectedIp(sockAddr.substring(1, sockAddr.lastIndexOf(':')));

                    try {
                        if (chr.getMessenger() != null) {
                            MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(chr);
                            getChannelServer().getWorldInterface().leaveMessenger(chr.getMessenger().getId(), messengerplayer);
                            chr.setMessenger(null);
                        }
                    } catch (RemoteException e) {
                        getChannelServer().reconnectWorld();
                        chr.setMessenger(null);
                    }

                    chr.saveToDB(true, true);
                    chr.getMap().removePlayer(chr);
                    chr.destroyAllOwnedInteractableObjects();
                    chr.resetCooldowns();

                    try {
                        WorldChannelInterface wci = getChannelServer().getWorldInterface();
                        if (chr.getParty() != null) {
                            MaplePartyCharacter chrp = new MaplePartyCharacter(chr);
                            chrp.setOnline(false);
                            wci.updateParty(chr.getParty().getId(), PartyOperation.LOG_ONOFF, chrp);
                        }
                        if (!serverTransition && loggedIn) {
                            wci.loggedOff(chr.getName(), chr.getId(), channel, chr.getBuddylist().getBuddyIds());
                        } else { // Change channel
                            wci.loggedOn(chr.getName(), chr.getId(), channel, chr.getBuddylist().getBuddyIds());
                        }
                        if (chr.getGuildId() > 0) {
                            wci.setGuildMemberOnline(chr.getMGC(), false, -1);
                        }
                        MapleCharacter.setLoggedInState(chr.getId(), 0);
                    } catch (RemoteException e) {
                        getChannelServer().reconnectWorld();
                    } catch (NullPointerException ignored) {
                    } catch (Exception e) {
                        log.error(getLogMessage(this, "ERROR"), e);
                    } finally {
                        if (getChannelServer() != null) {
                            MapleSquadType[] types = {MapleSquadType.ZAKUM, MapleSquadType.HORNTAIL};
                            for (MapleSquadType type : types) {
                                MapleSquad squad = getChannelServer().getMapleSquad(type);
                                if (squad != null) {
                                    if (squad.containsMember(chr)) {
                                        //zaksquad.banMember(chr, false);
                                        if (!chr.getDiseases().contains(MapleDisease.SEDUCE)) {
                                            squad.playerDisconnected(player.getId());
                                        } else {
                                            squad.removeDisconnected(player.getId());
                                        }
                                        break;
                                    }
                                }
                            }
                            getChannelServer().removePlayer(chr);
                        }
                    }
                }
                if (!serverTransition && loggedIn) {
                    updateLoginState(MapleClient.LOGIN_NOTLOGGEDIN);
                }
                setPacketLog(false);
            }
        } catch (Throwable t) {
            log.error("Error while finalising client disconnect.", t);
        } finally {
            disconnectLatch.countDown();
        }
    }

    public void disconnect() {
        disconnect(false);
    }

    public void disconnect(boolean force) {
        if (force) {
            disconnected(true);
            iochannel.close();
        } else {
            iochannel.disconnect();
        }
    }

    public CountDownLatch getDisconnectLatch() {
        return disconnectLatch;
    }

    /**
     * Undefined when not logged to a channel
     *
     * @return the channel the client is connected to
     */
    public int getChannel() {
        return channel;
    }

    /**
     * Convinence method to get the ChannelServer object this client is logged
     * on to.
     *
     * @return The ChannelServer instance of the client.
     */
    public ChannelServer getChannelServer() {
        return ChannelServer.getInstance(channel);
    }

    public boolean deleteCharacter(int cid) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT id, name, level, job, guildid, guildrank FROM characters WHERE id = ? AND accountid = ?");
            ps.setInt(1, cid);
            ps.setInt(2, accId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                rs.close();
                ps.close();
                return false;
            }
            if (rs.getInt("guildid") > 0) {
                MapleGuildCharacter mgc = new MapleGuildCharacter(cid, rs.getInt("level"), rs.getString("name"), 0, rs.getInt("job"), rs.getInt("guildrank"), 5, rs.getInt("guildid"), false);
                try {
                    LoginServer.getInstance().getWorldInterface().deleteGuildCharacter(mgc);
                } catch (RemoteException re) {
                    log.error("Unable to remove member from guild list.");
                    return false;
                }
            }
            rs.close();
            ps.close();

            // ok this is actually our character, delete it
            ps = con.prepareStatement("DELETE FROM characters WHERE id = ?");
            ps.setInt(1, cid);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException e) {
            log.error("ERROR", e);
        }
        return false;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

    public void setChannel(int channel) {
        this.channel = channel;
    }

    public int getWorld() {
        return world;
    }

    public void setWorld(int world) {
        this.world = world;
    }

    public void pongReceived() {
        lastPong = System.currentTimeMillis();
    }

    public void sendPing() {
        final long then = System.currentTimeMillis();
        sendPacket(MaplePacketCreator.getPing());
        TimerManager.getInstance().schedule(() -> {
            try {
                if (lastPong - then < 0) {
                    if (iochannel.isActive()) {
                        //log.info(getLogMessage(MapleClient.this, "Auto DC : Ping Timeout"));
                        disconnect();
                    }
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }, 15000); // note: idletime gets added to this too
    }

    public Set<String> getMacs() {
        return Collections.unmodifiableSet(macs);
    }

    public boolean isGM() {
        return GM;
    }

    public void setScriptEngine(String name, ScriptEngine e) {
        engines.put(name, e);
    }

    public ScriptEngine getScriptEngine(String name) {
        return engines.get(name);
    }

	/*public ScheduledFuture<?> getIdleTask() {
        return idleTask;
	}

	public void setIdleTask(ScheduledFuture<?> idleTask) {
		this.idleTask = idleTask;
	}*/

    public void removeScriptEngine(String name) {
        engines.remove(name);
    }

    public NPCConversationManager getCM() {
        return NPCScriptManager.getInstance().getCM(this);
    }

    public QuestActionManager getQM() {
        return QuestScriptManager.getInstance().getQM(this);
    }

    public boolean hasPacketLog() {
        return hasPacketLog;
    }

    public void setPacketLog(boolean b) {
        hasPacketLog = b;

        try {
            if (!b && pL_fos != null) {
                closePacketLog();
            } else if (b && pL_fos == null) {
                initPacketLog();
            }
        } catch (Throwable t) {
            log.error("Failed to create/remove packet log.", t);
            t.printStackTrace();
            try {
                getChannelServer().getWorldInterface().broadcastGMMessage(player.getName(), MaplePacketCreator.serverNotice(0, "Failed to create/remove " + player.getName() + " packet log.").getBytes());
            } catch (Throwable u) {
                log.error("Failed to broadcast error while creating/remove packet log.", u);
                u.printStackTrace();
            }
        }
    }

    private void closePacketLog() throws Throwable {
        packetLog.close();
        packetLog = null;
        pL_osw.close();
        pL_osw = null;
        pL_fos.close();
        pL_fos = null;
    }

    private void initPacketLog() throws Throwable {
        int index = 0;
        String file = "packetlog/" + player.getName() + "/";
        File log2 = new File(file + index + ".txt");
        if (log2.getParentFile() != null) {
            log2.getParentFile().mkdirs();
        }
        while (log2.exists()) {
            index++;
            log2 = new File(file + index + ".txt");
        }
        if (log2.createNewFile()) {
            pL_fos = new FileOutputStream(log2, true);
            pL_osw = new OutputStreamWriter(pL_fos);
            packetLog = new BufferedWriter(pL_osw);
        }
    }

    public void writePacketLog(String s) {
        try {
            if (packetLog != null) {
                packetLog.write(s);
            } else {
                log.error("Failed to write to packet log because packetLog == null");

                try {
                    getChannelServer().getWorldInterface().broadcastGMMessage(player.getName(), MaplePacketCreator.serverNotice(0, "Failed to write to " + player.getName() + " packet log (packetLog == null).").getBytes());
                } catch (Throwable u) {
                    log.error("Failed to broadcast error while writing to packet log (packetLog == null).", u);
                    u.printStackTrace();
                }
            }
        } catch (Throwable t) {
            log.error("Failed to write to packet log", t);
            t.printStackTrace();
            try {
                getChannelServer().getWorldInterface().broadcastGMMessage(player.getName(), MaplePacketCreator.serverNotice(0, "Failed to write to " + player.getName() + " packet log.").getBytes());
            } catch (Throwable u) {
                log.error("Failed to broadcast error while writing to packet log.", u);
                u.printStackTrace();
            }

        }
    }

    public int getLastAction() {
        return lastAction;
    }

    public void setLastAction(int action) {
        lastAction = action;
    }

    private static class CharNameAndId {

        public final String name;
        public final int id;

        public CharNameAndId(String name, int id) {
            super();
            this.name = name;
            this.id = id;
        }
    }

    // TODO: Separate the stuff dealing with game /accounts/ and the actual network client.

    private class PacketDecoder extends ReplayingDecoder<Void> {

        @Override
        protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> objects) throws Exception {
            byte[] header = new byte[4];
            byteBuf.readBytes(header);
            if (!MapleClient.this.receive.checkPacket(header)) {
                log.warn(getLogMessage(MapleClient.this, "Invalid packet header; disconnecting."));
                channelHandlerContext.disconnect();
                return;
            }
            int packetLen = MapleAESOFB.getPacketLength(header);
            byte[] packet = new byte[packetLen];
            byteBuf.readBytes(packet);
            MapleClient.this.receive.crypt(packet);
            MapleCustomEncryption.decryptData(packet);
            objects.add(packet);
        }
    }

    private class PacketEncoder extends MessageToByteEncoder<MaplePacket> {

        @Override
        protected void encode(ChannelHandlerContext ctx, MaplePacket msg, ByteBuf out) throws Exception {
            byte[] input = msg.getBytes();
            byte[] packet = Arrays.copyOf(input, input.length);
            out.writeBytes(MapleClient.this.send.getPacketHeader(packet.length));
            MapleCustomEncryption.encryptData(packet);
            MapleClient.this.send.crypt(packet);
            out.writeBytes(packet);
        }
    }
}