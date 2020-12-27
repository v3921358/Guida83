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

package guida.tools;

import guida.client.BuddylistEntry;
import guida.client.IEquip;
import guida.client.IEquip.ScrollResult;
import guida.client.IItem;
import guida.client.ISkill;
import guida.client.Item;
import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleDisease;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.MapleKeyBinding;
import guida.client.MapleMount;
import guida.client.MaplePet;
import guida.client.MapleQuestStatus;
import guida.client.MapleRing;
import guida.client.MapleStat;
import guida.client.SkillCooldown;
import guida.client.SkillMacro;
import guida.client.status.MonsterStatus;
import guida.database.DatabaseConnection;
import guida.net.ByteArrayMaplePacket;
import guida.net.MaplePacket;
import guida.net.SendPacketOpcode;
import guida.net.channel.handler.SummonDamageHandler.SummonAttackEntry;
import guida.net.login.LoginServer;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.guild.MapleAlliance;
import guida.net.world.guild.MapleGuild;
import guida.net.world.guild.MapleGuildCharacter;
import guida.net.world.guild.MapleGuildSummary;
import guida.server.CashItemInfo;
import guida.server.MTSItemInfo;
import guida.server.MapleCSInventoryItem;
import guida.server.MapleDueyActions;
import guida.server.MapleItemInformationProvider;
import guida.server.MaplePlayerNPC;
import guida.server.MapleStatEffect;
import guida.server.life.MapleMonster;
import guida.server.life.MapleNPC;
import guida.server.life.MobSkill;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMist;
import guida.server.maps.MapleReactor;
import guida.server.maps.MapleSummon;
import guida.server.movement.LifeMovementFragment;
import guida.server.playerinteractions.HiredMerchant;
import guida.server.playerinteractions.IMaplePlayerShop;
import guida.server.playerinteractions.MaplePlayerShop;
import guida.server.playerinteractions.MaplePlayerShopItem;
import guida.server.playerinteractions.MapleShopItem;
import guida.server.playerinteractions.MapleTrade;
import guida.tools.data.output.LittleEndianWriter;
import guida.tools.data.output.MaplePacketLittleEndianWriter;

import java.awt.Point;
import java.awt.Rectangle;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Provides all MapleStory packets needed in one place.
 *
 * @author Frz
 * @version 1.0
 * @since Revision 259
 */
public class MaplePacketCreator {

    public static final List<Pair<MapleStat, Integer>> EMPTY_STATUPDATE = Collections.emptyList();
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MaplePacketCreator.class);

    /**
     * Sends a hello packet.
     *
     * @param mapleVersion The maple client version.
     * @param sendIv       the IV used by the server for sending
     * @param recvIv       the IV used by the server for receiving
     * @param testServer
     * @return
     */
    public static MaplePacket getHello(short mapleVersion, byte[] sendIv, byte[] recvIv, boolean testServer) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);

        mplew.writeShort(14);
        mplew.writeShort(mapleVersion);
        mplew.writeMapleAsciiString("1");
        mplew.write(recvIv);
        mplew.write(sendIv);
        mplew.write(testServer ? 5 : 8);

        return mplew.getPacket();
    }

    /**
     * Sends a ping packet.
     *
     * @return The packet.
     */
    public static MaplePacket getPing() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);

        mplew.writeShort(SendPacketOpcode.PING.getValue());

        return mplew.getPacket();
    }

    /**
     * MapleTV Stuff
     * All credits to Cheetah And MrMysterious for this.
     *
     * @return various.
     */
    public static MaplePacket enableTV() {
        // [0F 01] [00 00 00 00] [00] <-- 0x112 in v63,
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.ENABLE_TV.getValue()); // enableTV = 0x10F
        mplew.writeInt(0);
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket removeTV() {
        // 11 01 <-- 0x10E in v62
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.REMOVE_TV.getValue()); // removeTV = 0x111 <-- v63
        return mplew.getPacket();
    }

    public static MaplePacket sendTV(MapleCharacter chr, List<String> messages, int type, MapleCharacter partner) {
        // [10 01] [01] [00 00 03 B1 4F 00 00 00 67 75 00 00 01 75 4B 0F 00 0C E3 FA 10 00 FF FF
        // 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00] [0B 00 64 75 73 74 72 65 6D 6F 76
        // 65 72] [00 00] [07 00] [70 61 63 6B 65 74 73] 00 00 00 00 00 00 00 00 0F 00 00 00

        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SEND_TV.getValue()); // SEND_TV = 0x11D
        mplew.write(partner != null ? 2 : 0);
        mplew.write(type); // type   Heart = 2  Star = 1  Normal = 0
        addCharLook(mplew, chr, false);
        mplew.writeMapleAsciiString(chr.getName());
        if (partner != null) {
            mplew.writeMapleAsciiString(partner.getName());
        } else {
            mplew.writeShort(0); // could be partner
        }
        for (int i = 0; i < messages.size(); i++) { // for (String message : messages) {
            if (i == 4 && messages.get(4).length() > 15) {
                mplew.writeMapleAsciiString(messages.get(4).substring(0, 15)); // hmm ?
            } else {
                mplew.writeMapleAsciiString(messages.get(i));
            }
        }
        mplew.writeInt(1337); // time limit shit lol 'Your thing still start in blah blah seconds'
        if (partner != null) {
            addCharLook(mplew, partner, false);
        }
        return mplew.getPacket();
    }

    /**
     * Gets a login failed packet.
     * <p/>
     * Possible values for <code>reason</code>:<br>
     * 3: ID deleted or blocked<br>
     * 4: Incorrect password<br>
     * 5: Not a registered id<br>
     * 6: System error<br>
     * 7: Already logged in<br>
     * 8: System error<br>
     * 9: System error<br>
     * 10: Cannot process so many connections<br>
     * 11: Only users older than 20 can use this channel<br>
     * 13: Unable to log on as master at this ip<br>
     * 14: Wrong gateway or personal info and weird korean button<br>
     * 15: Processing request with that korean button!<br>
     * 16: Please verify your account through email...<br>
     * 17: Wrong gateway or personal info<br>
     * 21: Please verify your account through email...<br>
     * 23: License agreement<br>
     * 25: Maple Europe notice<br>
     * 27: Some weird full client notice, probably for trial versions<br>
     *
     * @param reason The reason logging in failed.
     * @return The login failed packet.
     */
    public static MaplePacket getLoginFailed(int reason) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(16);

        mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
        mplew.writeInt(reason);
        mplew.write(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket getPermBan(byte reason) {
        // 00 00 02 00 01 01 01 01 01 00
        return getTempBan(Long.MAX_VALUE - 1, reason);
    }

    public static MaplePacket getTempBan(long timestampTill, byte reason) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(17);

        mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
        mplew.writeInt(2);
        mplew.writeShort(0);
        mplew.write(reason);
        mplew.writeLong(timestampTill);

        return mplew.getPacket();
    }

    /**
     * Gets a successful authentication and PIN Request packet.
     *
     * @param c The account name.
     * @return The successful authentication packet.
     */
    public static MaplePacket getAuthSuccess(MapleClient c) {
        boolean noPIC = c.getPIC().length() == 0 && c.getPin() != 10000;
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.LOGIN_STATUS.getValue());
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeInt(c.getAccID());
        mplew.write(c.getGender());
        mplew.write(0); // GM Byte! Set to 1, disallows trading + merchants but commands WORK! /m, /summon, /c, etc. Code the 0x7E GM command handler if you want to. :)
        mplew.writeShort(0);
        mplew.writeMapleAsciiString(c.getAccountName());
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(c.getCreateDate().getTime()));
        mplew.writeInt(noPIC ? 2 : 0);
        mplew.write(noPIC ? 0 : 1); // has PIC? sends PIN screen otherwise
        mplew.write(noPIC ? 0 : 1);
        mplew.write(HexTool.getByteArrayFromHexString("19 15 A6 96 50 31 53 D0"));

        return mplew.getPacket();
    }

    public static MaplePacket reauthenticateClient(MapleClient c) {
        boolean noPIC = c.getPIC().length() == 0 && c.getPin() != 10000;
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REAUTHENTICATE.getValue());
        mplew.write(0);
        mplew.writeInt(c.getAccID());
        mplew.write(c.getGender());
        mplew.write(0); // GM Byte! Set to 1, disallows trading + merchants but commands WORK! /m, /summon, /c, etc. Code the 0x7E GM command handler if you want to. :)
        mplew.writeShort(0);
        mplew.writeMapleAsciiString(c.getAccountName());
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(c.getCreateDate().getTime()));
        mplew.writeInt(noPIC ? 0 : 2);
        mplew.write(HexTool.getByteArrayFromHexString("19 15 A6 96 50 31 53 D0"));

        return mplew.getPacket();
    }

    public static MaplePacket sendLoginMethod() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        mplew.writeShort(SendPacketOpcode.LOGIN_METHOD.getValue());
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket genderChanged() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        mplew.writeShort(SendPacketOpcode.GENDER_SET.getValue());
        mplew.write(1);
        mplew.write(1);

        return mplew.getPacket();
    }

    /**
     * Gets a packet detailing a PIN operation.
     * <p/>
     * Possible values for <code>mode</code>:<br>
     * 0 - PIN was accepted<br>
     * 1 - Register a new PIN<br>
     * 2 - Invalid pin / Reenter<br>
     * 3 - Connection failed due to system error<br>
     * 4 - Enter the pin
     *
     * @param mode The mode.
     * @return PIN Operation packet
     */
    public static MaplePacket pinOperation(byte mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        mplew.writeShort(SendPacketOpcode.PIN_OPERATION.getValue());
        mplew.write(mode);

        return mplew.getPacket();
    }

    public static MaplePacket pinAssigned() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        mplew.writeShort(SendPacketOpcode.PIN_ASSIGNED.getValue());
        mplew.write(0);

        return mplew.getPacket();
    }

    /**
     * Gets a packet detailing a server and its channels.
     *
     * @param serverId    The index of the server to create information about.
     * @param serverName  The name of the server.
     * @param channelLoad Load of the channel - 1200 seems to be max.
     * @return The server info packet.
     */
    public static MaplePacket getServerList(int serverId, String serverName, Map<Integer, Integer> channelLoad) {
        /*
         * 0B 00 00 06 00 53 63 61 6E 69 61 00 00 00 64 00 64 00 00 13 08 00 53 63 61 6E 69 61 2D 31 5E 04 00 00 00 00
         * 00 08 00 53 63 61 6E 69 61 2D 32 25 01 00 00 00 01 00 08 00 53 63 61 6E 69 61 2D 33 F6 00 00 00 00 02 00 08
         * 00 53 63 61 6E 69 61 2D 34 BC 00 00 00 00 03 00 08 00 53 63 61 6E 69 61 2D 35 E7 00 00 00 00 04 00 08 00 53
         * 63 61 6E 69 61 2D 36 BC 00 00 00 00 05 00 08 00 53 63 61 6E 69 61 2D 37 C2 00 00 00 00 06 00 08 00 53 63 61
         * 6E 69 61 2D 38 BB 00 00 00 00 07 00 08 00 53 63 61 6E 69 61 2D 39 C0 00 00 00 00 08 00 09 00 53 63 61 6E 69
         * 61 2D 31 30 C3 00 00 00 00 09 00 09 00 53 63 61 6E 69 61 2D 31 31 BB 00 00 00 00 0A 00 09 00 53 63 61 6E 69
         * 61 2D 31 32 AB 00 00 00 00 0B 00 09 00 53 63 61 6E 69 61 2D 31 33 C7 00 00 00 00 0C 00 09 00 53 63 61 6E 69
         * 61 2D 31 34 B9 00 00 00 00 0D 00 09 00 53 63 61 6E 69 61 2D 31 35 AE 00 00 00 00 0E 00 09 00 53 63 61 6E 69
         * 61 2D 31 36 B6 00 00 00 00 0F 00 09 00 53 63 61 6E 69 61 2D 31 37 DB 00 00 00 00 10 00 09 00 53 63 61 6E 69
         * 61 2D 31 38 C7 00 00 00 00 11 00 09 00 53 63 61 6E 69 61 2D 31 39 EF 00 00 00 00 12 00
         */
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
        mplew.write(serverId);
        mplew.writeMapleAsciiString(serverName);
        mplew.write(LoginServer.getInstance().getFlag());
        mplew.writeMapleAsciiString(LoginServer.getInstance().getEventMessage());
        mplew.write(100); // rate modifier, don't ask O.O!
        mplew.write(0); // event xp * 2.6 O.O!
        mplew.write(100); // rate modifier, don't ask O.O!
        mplew.write(0); // drop rate * 2.6
        mplew.write(0);
        int lastChannel = 1;
        Set<Integer> channels = channelLoad.keySet();
        for (int i = 30; i > 0; i--) {
            if (channels.contains(i)) {
                lastChannel = i;
                break;
            }
        }
        mplew.write(lastChannel);

        int load;
        for (int i = 1; i <= lastChannel; i++) {
            if (channels.contains(i)) {
                load = channelLoad.get(i);
            } else {
                load = 1200;
            }
            mplew.writeMapleAsciiString(serverName + "-" + i);
            mplew.writeInt(load);
            mplew.write(serverId);
            mplew.writeShort(i - 1);
        }
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    /**
     * Gets a packet saying that the server list is over.
     *
     * @return The end of server list packet.
     */
    public static MaplePacket getEndOfServerList() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERLIST.getValue());
        mplew.write(-1);

        return mplew.getPacket();
    }

    public static MaplePacket getRecommendedServer(int serverId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.RECOMMENDED_SERVER.getValue());
        mplew.writeInt(serverId);

        return mplew.getPacket();
    }

    public static MaplePacket getEndOfServerList(boolean enabled, int serverId, String message) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.RECOMMENDED_SERVER_MESSAGE.getValue());
        mplew.write(enabled ? 1 : 0);
        if (enabled) {
            mplew.writeInt(serverId);
            mplew.writeMapleAsciiString(message);
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet detailing a server status message.
     * <p/>
     * Possible values for <code>status</code>:<br>
     * 0 - Normal<br>
     * 1 - Highly populated<br>
     * 2 - Full
     *
     * @param status The server status.
     * @return The server status packet.
     */
    public static MaplePacket getServerStatus(int status) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERSTATUS.getValue());
        mplew.writeShort(status);

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client the IP of the channel server.
     *
     * @param inetAddr The InetAddress of the requested channel server.
     * @param port     The port the channel is on.
     * @param clientId The ID of the client.
     * @return The server IP packet.
     */
    public static MaplePacket getServerIP(InetAddress inetAddr, int port, int clientId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVER_IP.getValue());
        mplew.writeShort(0);
        byte[] addr = inetAddr.getAddress();
        mplew.write(addr);
        mplew.writeShort(port);
        // 0x13 = numchannels?
        mplew.writeInt(clientId); // this gets repeated to the channel server
        // leos.write(new byte[] { (byte) 0x13, (byte) 0x37, 0x42, 1, 0, 0, 0, 0, 0 });
        mplew.write(new byte[] {0, 0, 0, 0, 0});
        // 0D 00 00 00 3F FB D9 0D 8A 21 CB A8 13 00 00 00 00 00 00

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client the IP of the new channel.
     *
     * @param inetAddr The InetAddress of the requested channel server.
     * @param port     The port the channel is on.
     * @return The server IP packet.
     */
    public static MaplePacket getChannelChange(InetAddress inetAddr, int port) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHANGE_CHANNEL.getValue());
        mplew.write(1);
        byte[] addr = inetAddr.getAddress();
        mplew.write(addr);
        mplew.writeShort(port);

        return mplew.getPacket();
    }

    public static MaplePacket sendPICResponse(int status) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHAR_SELECT_PIC_RESPONSE.getValue());
        mplew.write(status);

        return mplew.getPacket();
    }

    /**
     * Gets a packet with a list of characters.
     *
     * @param c        The MapleClient to load characters of.
     * @param serverId The ID of the server requested.
     * @return The character list packet.
     */
    public static MaplePacket getCharList(MapleClient c, int serverId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHARLIST.getValue());
        mplew.write(0);
        List<MapleCharacter> chars = c.loadCharacters(serverId);
        mplew.write((byte) chars.size());
        for (MapleCharacter chr : chars) {
            addCharEntry(mplew, chr, false);
        }
        mplew.write(c.getPIC().length() == 0 ? 0 : 1);
        mplew.writeInt(LoginServer.getInstance().getMaxCharacters());

        return mplew.getPacket();
    }

    /**
     * Adds character stats to an existing MaplePacketLittleEndianWriter.
     *
     * @param mplew The MaplePacketLittleEndianWrite instance to write the stats to.
     * @param chr   The character to add the stats of.
     */
    private static void addCharStats(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        int jobId = chr.getJob().getId();
        mplew.writeInt(chr.getId()); // character id
        mplew.writeAsciiString(chr.getName());
        for (int x = chr.getName().length(); x < 13; x++) { // fill to maximum name length
            mplew.write(0);
        }
        mplew.write(chr.getGender()); // gender (0 = male, 1 = female)
        mplew.write(chr.getSkinColor().getId()); // skin color
        mplew.writeInt(chr.getFace()); // face
        mplew.writeInt(chr.getHair()); // hair
        final MaplePet[] pets = chr.getPetsAsArray();
        for (MaplePet pet : pets) {
            mplew.writeLong(pet != null ? pet.getUniqueId() : 0);
        }
        mplew.write(chr.getLevel()); // level
        mplew.writeShort(jobId); // job
        mplew.writeShort(chr.getStr()); // str
        mplew.writeShort(chr.getDex()); // dex
        mplew.writeShort(chr.getInt()); // int
        mplew.writeShort(chr.getLuk()); // luk
        mplew.writeShort(chr.getHp()); // hp (?)
        mplew.writeShort(chr.getMaxHp()); // maxhp
        mplew.writeShort(chr.getMp()); // mp (?)
        mplew.writeShort(chr.getMaxMp()); // maxmp
        mplew.writeShort(chr.getRemainingAp()); // remaining ap
        mplew.writeShort(chr.getRemainingSp()); // remaining sp
        mplew.writeInt(chr.getExp()); // current exp
        mplew.writeShort(chr.getFame()); // fame
        mplew.writeInt(0);
        mplew.writeInt(chr.getMapId()); // current map id
        mplew.write(chr.getInitialSpawnpoint()); // spawnpoint
        mplew.writeInt(0);
    }

    /**
     * Adds the aesthetic aspects of a character to an existing
     * MaplePacketLittleEndianWriter.
     *
     * @param mplew The MaplePacketLittleEndianWrite instance to write the stats to.
     * @param chr   The character to add the looks of.
     * @param mega  Unknown
     */
    private static void addCharLook(MaplePacketLittleEndianWriter mplew, MapleCharacter chr, boolean mega) {
        mplew.write(chr.getGender());
        mplew.write(chr.getSkinColor().getId());
        mplew.writeInt(chr.getFace());
        mplew.write(mega ? 0 : 1);
        mplew.writeInt(chr.getHair());
        MapleInventory equip = chr.getInventory(MapleInventoryType.EQUIPPED);
        Map<Short, Integer> myEquip = new LinkedHashMap<>();
        Map<Short, Integer> maskedEquip = new LinkedHashMap<>();
        for (IItem item : equip.list()) {
            short pos = (short) (item.getPosition() * -1);
            if (pos < 100 && myEquip.get(pos) == null) {
                myEquip.put(pos, item.getItemId());
            } else if (pos > 100 && pos != 111) {
                pos -= 100;
                if (myEquip.get(pos) != null) {
                    maskedEquip.put(pos, myEquip.get(pos));
                }
                myEquip.put(pos, item.getItemId());
            } else if (myEquip.get(pos) != null) {
                maskedEquip.put(pos, item.getItemId());
            }
        }
        for (Entry<Short, Integer> entry : myEquip.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }
        mplew.write(-1); // end of visible itens
        // masked items
        for (Entry<Short, Integer> entry : maskedEquip.entrySet()) {
            mplew.write(entry.getKey());
            mplew.writeInt(entry.getValue());
        }
        // ending markers
        mplew.write(-1);
        IItem cWeapon = equip.getItem((short) -111);
        mplew.writeInt(cWeapon != null ? cWeapon.getItemId() : 0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
    }

    private static void addCrushRingLook(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.write(chr.getEquippedRing(0) != 0 ? 1 : 0);
        for (MapleRing ring : chr.getCrushRings()) {
            if (ring.getRingId() == chr.getEquippedRing(0)) {
                mplew.writeInt(ring.getRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getPartnerRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getItemId());
            }
        }
    }

    private static void addFriendshipRingLook(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.write(chr.getEquippedRing(1) != 0 ? 1 : 0);
        for (MapleRing ring : chr.getFriendshipRings()) {
            if (ring.getRingId() == chr.getEquippedRing(1)) {
                mplew.writeInt(ring.getRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getPartnerRingId());
                mplew.writeInt(0);
                mplew.writeInt(ring.getItemId());
            }
        }
    }

    private static void addMarriageRingLook(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.write(chr.getEquippedRing(2) != 0 ? 1 : 0);
        for (MapleRing ring : chr.getMarriageRings()) {
            if (ring.getRingId() == chr.getEquippedRing(2)) {
                mplew.writeInt(ring.getPartnerChrId());
                mplew.writeInt(chr.getId());
                mplew.writeInt(ring.getItemId());
            }
        }
    }

    private static void addInventoryInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeInt(chr.getMeso()); // mesos
        mplew.write(96); // equip slots
        mplew.write(96); // use slots
        mplew.write(96); // set-up slots
        mplew.write(96); // etc slots
        mplew.write(96); // cash slots
        mplew.writeLong(94354848000000000L);
        MapleInventory iv = chr.getInventory(MapleInventoryType.EQUIPPED);
        Collection<IItem> equippedC = iv.list();
        List<Item> equipped = new ArrayList<>(equippedC.size());
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (IItem item : equippedC) {
            equipped.add((Item) item);
        }
        Collections.sort(equipped);
        for (Item item : equipped) {
            if (!ii.isCashItem(item.getItemId())) {
                addItemInfo(mplew, item, false);
            }
        }
        mplew.writeShort(0); // start of equipped nx
        for (Item item : equipped) {
            if (ii.isCashItem(item.getItemId())) {
                addItemInfo(mplew, item, false);
            }
        }
        mplew.writeShort(0); // start of equip inventory
        iv = chr.getInventory(MapleInventoryType.EQUIP);
        for (IItem item : iv.list()) {
            addItemInfo(mplew, item, false);
        }
        mplew.writeShort(0);
        mplew.writeShort(0);
        //mplew.write(0); // start of use inventory
        iv = chr.getInventory(MapleInventoryType.USE);
        for (IItem item : iv.list()) {
            addItemInfo(mplew, item, false);
        }
        mplew.write(0); // start of set-up inventory
        iv = chr.getInventory(MapleInventoryType.SETUP);
        for (IItem item : iv.list()) {
            addItemInfo(mplew, item, false);
        }
        mplew.write(0); // start of etc inventory
        iv = chr.getInventory(MapleInventoryType.ETC);
        for (IItem item : iv.list()) {
            addItemInfo(mplew, item, false);
        }
        mplew.write(0); // start of cash inventory
        iv = chr.getInventory(MapleInventoryType.CASH);
        for (IItem item : iv.list()) {
            addItemInfo(mplew, item, false);
        }
    }

    private static void addSkillRecord(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.write(0); // start of skills
        Map<ISkill, MapleCharacter.SkillEntry> skills = chr.getSkills();
        mplew.writeShort(skills.size());
        for (Entry<ISkill, MapleCharacter.SkillEntry> skill : skills.entrySet()) {
            mplew.writeInt(skill.getKey().getId());
            mplew.writeInt(skill.getValue().skillevel);
            mplew.writeLong(FileTimeUtil.getFileTimestamp(FileTimeUtil.getDefaultTimestamp().getTime()));
            if (skill.getKey().isFourthJob()) {
                mplew.writeInt(skill.getValue().masterlevel);
            }
        }
        mplew.writeShort(chr.getAllCooldowns().size());
        for (SkillCooldown cooling : chr.getAllCooldowns()) {
            mplew.writeInt(cooling.getSkillId());
            int timeLeft = (int) (cooling.getLength() + cooling.getStartTime() - System.currentTimeMillis());
            mplew.writeShort(timeLeft / 1000);
        }
    }

    /**
     * Adds an entry for a character to an existing
     * MaplePacketLittleEndianWriter.
     *
     * @param mplew The MaplePacketLittleEndianWrite instance to write the stats to.
     * @param chr   The character to add.
     */
    private static void addCharEntry(MaplePacketLittleEndianWriter mplew, MapleCharacter chr, boolean viewingAll) {
        addCharStats(mplew, chr);
        addCharLook(mplew, chr, false);
        if (!viewingAll) {
            mplew.write(0); // Something with the delete character confirmation?
        }
        int jobId = chr.getJob().getId();
        if (chr.isGM() || chr.getLevel() < 10 || jobId >= 900 && jobId <= 910) {
            mplew.write(0);
        } else {
            mplew.write(1); // world rank enabled (next 4 ints are not sent if disabled)
            mplew.writeInt(chr.getRank()); // world rank
            mplew.writeInt(chr.getRankMove()); // move (negative is downwards)
            mplew.writeInt(chr.getJobRank()); // job rank
            mplew.writeInt(chr.getJobRankMove()); // move (negative is downwards)
        }
    }

    /**
     * Adds a quest info entry for a character to an existing
     * MaplePacketLittleEndianWriter.
     *
     * @param mplew The MaplePacketLittleEndianWrite instance to write the stats to.
     * @param chr   The character to add quest info about.
     */
    private static void addQuestRecord(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        List<MapleQuestStatus> started = chr.getStartedQuests();
        mplew.writeShort(started.size());
        for (MapleQuestStatus q : started) {
            mplew.writeShort(q.getQuestId());
            mplew.writeMapleAsciiString(q.getQuestRecord());
        }
        List<MapleQuestStatus> completed = chr.getCompletedQuests();
        mplew.writeShort(completed.size());
        for (MapleQuestStatus q : completed) {
            mplew.writeShort(q.getQuestId());
            mplew.writeLong(FileTimeUtil.getFileTimestamp(q.getCompletionTime()));
        }
    }

    private static void addMiniGameRecordInfo(MaplePacketLittleEndianWriter mplew) {
        mplew.writeShort(0); // amount of records
		/*for (MiniGame record : records) {
			mplew.writeInt();
			mplew.writeInt();
			mplew.writeInt();
			mplew.writeInt();
			mplew.writeInt();
		}*/
    }

    private static void addCrushRingRecordInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeShort(chr.getCrushRings().size());
        for (MapleRing ring : chr.getCrushRings()) {
            mplew.writeInt(ring.getPartnerChrId());
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(ring.getPartnerName(), '\0', 13));
            mplew.writeInt(ring.getRingId());
            mplew.writeInt(0);
            mplew.writeInt(ring.getPartnerRingId());
            mplew.writeInt(0);
        }
    }

    private static void addFriendshipRingRecordInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeShort(chr.getFriendshipRings().size());
        for (MapleRing ring : chr.getFriendshipRings()) {
            mplew.writeInt(ring.getPartnerChrId());
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(ring.getPartnerName(), '\0', 13));
            mplew.writeInt(ring.getRingId());
            mplew.writeInt(0);
            mplew.writeInt(ring.getPartnerRingId());
            mplew.writeInt(0);
            mplew.writeInt(ring.getItemId());
        }
    }

    private static void addMarriageRingRecordInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeShort(chr.getMarriageRings().size());
        int gender = chr.getGender();
        int marriageId = 30000;
        for (MapleRing ring : chr.getMarriageRings()) {
            mplew.writeInt(marriageId);
            mplew.writeInt(gender == 0 ? chr.getId() : ring.getPartnerChrId());
            mplew.writeInt(gender == 0 ? ring.getPartnerChrId() : chr.getId());
            mplew.writeShort(3);
            mplew.writeInt(ring.getItemId());
            mplew.writeInt(ring.getItemId());
            mplew.writeAsciiString(gender == 0 ? StringUtil.getRightPaddedStr(chr.getName(), '\0', 13) : StringUtil.getRightPaddedStr(ring.getPartnerName(), '\0', 13));
            mplew.writeAsciiString(gender == 0 ? StringUtil.getRightPaddedStr(ring.getPartnerName(), '\0', 13) : StringUtil.getRightPaddedStr(chr.getName(), '\0', 13));
            marriageId++;
        }
    }

    private static void addTeleportRockRecord(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        List<Integer> maps = chr.getTeleportRockMaps(0);
        for (int map : maps) {
            mplew.writeInt(map);
        }
        for (int i = maps.size(); i < 5; i++) {
            mplew.writeInt(999999999);
        }
        maps = chr.getTeleportRockMaps(1);
        for (int map : maps) {
            mplew.writeInt(map);
        }
        for (int i = maps.size(); i < 10; i++) {
            mplew.writeInt(999999999);
        }
    }

    public static void addMonsterBookInfo(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        mplew.writeInt(chr.getMonsterBookCover());
        mplew.write(0);
        Map<Integer, Integer> cards = chr.getMonsterBook().getCards();
        mplew.writeShort(cards.size());
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (Entry<Integer, Integer> all : cards.entrySet()) {
            mplew.writeShort(ii.getCardShortId(all.getKey()));
            mplew.write(all.getValue());
        }
    }

    private static void addQuestRecordEx(MaplePacketLittleEndianWriter mplew, MapleCharacter chr) {
        List<MapleQuestStatus> started = chr.getStartedQuestRecordEx();
        mplew.writeShort(started.size());
        for (MapleQuestStatus q : started) {
            mplew.writeShort(q.getQuestId());
            mplew.writeMapleAsciiString(q.getQuestRecord());
        }
        List<MapleQuestStatus> completed = chr.getCompletedQuestRecordEx();
        mplew.writeShort(completed.size());
        for (MapleQuestStatus q : completed) {
            mplew.writeShort(q.getQuestId());
            mplew.writeLong(FileTimeUtil.getFileTimestamp(q.getCompletionTime()));
        }
    }

    /**
     * Gets character info for a character.
     *
     * @param chr The character to get info about.
     * @return The character info packet.
     */
    public static MaplePacket getCharInfo(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue());
        mplew.writeInt(chr.getClient().getChannel() - 1);
        mplew.write(1);
        mplew.write(1);
        mplew.writeShort(0);
        chr.getRNG().seedRNG(mplew);
        mplew.writeLong(-1);
        mplew.write(0);
        addCharStats(mplew, chr);
        mplew.write(chr.getBuddylist().getCapacity());
        if (chr.getBlessingChar().length() != 0) {
            mplew.write(1);
            mplew.writeMapleAsciiString(chr.getBlessingChar());
        } else {
            mplew.write(0);
        }
        addInventoryInfo(mplew, chr);
        addSkillRecord(mplew, chr);
        addQuestRecord(mplew, chr);
        addMiniGameRecordInfo(mplew);
        addCrushRingRecordInfo(mplew, chr);
        addFriendshipRingRecordInfo(mplew, chr);
        addMarriageRingRecordInfo(mplew, chr);
        addTeleportRockRecord(mplew, chr);
        addMonsterBookInfo(mplew, chr);
        mplew.writeShort(0);
        addQuestRecordEx(mplew, chr);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(System.currentTimeMillis()));

        return mplew.getPacket();
    }

    /**
     * Gets an empty stat update.
     *
     * @return The empy stat update packet.
     */
    public static MaplePacket enableActions() {
        return updatePlayerStats(EMPTY_STATUPDATE, true);
    }

    /**
     * Gets an update for specified stats.
     *
     * @param stats The stats to update.
     * @return The stat update packet.
     */
    public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats) {
        return updatePlayerStats(stats, false);
    }

    /**
     * Gets an update for specified stats.
     *
     * @param stats        The list of stats to update.
     * @param itemReaction Result of an item reaction(?)
     * @return The stat update packet.
     */
    public static MaplePacket updatePlayerStats(List<Pair<MapleStat, Integer>> stats, boolean itemReaction) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
        if (itemReaction) {
            mplew.write(1);
        } else {
            mplew.write(0);
        }
        int updateMask = 0;
        for (Pair<MapleStat, Integer> statupdate : stats) {
            updateMask |= statupdate.getLeft().getValue();
        }
        if (stats.size() > 1) {
            stats.sort((o1, o2) -> {
                int val1 = o1.getLeft().getValue();
                int val2 = o2.getLeft().getValue();
                return Integer.compare(val1, val2);
            });
        }
        mplew.writeInt(updateMask);
        for (Pair<MapleStat, Integer> statupdate : stats) {
            if (statupdate.getLeft().getValue() >= 1) {
                if (statupdate.getLeft().getValue() == 0x1) {
                    mplew.writeShort(statupdate.getRight().shortValue());
                } else if (statupdate.getLeft().getValue() <= 0x4) {
                    mplew.writeInt(statupdate.getRight());
                } else if (statupdate.getLeft().getValue() < 0x20) {
                    mplew.write(statupdate.getRight().shortValue());
                } else if (statupdate.getLeft().getValue() < 0xFFFF) {
                    mplew.writeShort(statupdate.getRight().shortValue());
                } else {
                    mplew.writeInt(statupdate.getRight());
                }
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket tempStatsUpdate() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SET_STATS.getValue());
        mplew.writeInt(3871);
        mplew.writeShort(999);
        mplew.writeShort(999);
        mplew.writeShort(999);
        mplew.writeShort(999);
        mplew.writeShort(255);
        mplew.writeShort(999);
        mplew.writeShort(999);
        mplew.write(120);
        mplew.write(140);

        return mplew.getPacket();
    }

    public static MaplePacket resetStats() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.RESET_STATS.getValue());

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to change maps.
     *
     * @param to         The <code>MapleMap</code> to warp to.
     * @param spawnPoint The spawn portal number to spawn at.
     * @param chr        The character warping to <code>to</code>
     * @return The map change packet.
     */
    public static MaplePacket getWarpToMap(MapleMap to, int spawnPoint, MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue());
        mplew.writeInt(chr.getClient().getChannel() - 1);
        mplew.writeInt(2);
        mplew.write(0); // New
        mplew.writeInt(to.getId());
        mplew.write(spawnPoint);
        mplew.writeShort(chr.getHp());
        mplew.write(0);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(System.currentTimeMillis()));

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to change maps.
     *
     * @param to         The <code>MapleMap</code> to warp to.
     * @param spawnPoint The spawn portal number to spawn at.
     * @param chr        The character warping to <code>to</code>
     * @return The map change packet.
     */
    public static MaplePacket getWarpToMap(int to, int spawnPoint, MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WARP_TO_MAP.getValue());
        mplew.writeInt(chr.getClient().getChannel() - 1);
        mplew.writeShort(2);
        mplew.writeShort(0);
        mplew.writeInt(to);
        mplew.write(spawnPoint);
        mplew.writeShort(chr.getHp());
        mplew.write(0);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(System.currentTimeMillis()));

        return mplew.getPacket();
    }

    /**
     * Gets a packet to spawn a portal.
     *
     * @param townId   The ID of the town the portal goes to.
     * @param targetId The ID of the target.
     * @param pos      Where to put the portal.
     * @return The portal spawn packet.
     */
    public static MaplePacket spawnPortal(int townId, int targetId, Point pos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
        mplew.writeInt(townId);
        mplew.writeInt(targetId);
        if (pos != null) {
            mplew.writeShort(pos.x);
            mplew.writeShort(pos.y);
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet to spawn a door.
     *
     * @param oid  The door's object ID.
     * @param pos  The position of the door.
     * @param town
     * @return The remove door packet.
     */
    public static MaplePacket spawnDoor(int oid, Point pos, boolean town) {
        // [D3 00] [01] [93 AC 00 00] [6B 05] [37 03]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_DOOR.getValue());
        mplew.write(town ? 1 : 0);
        mplew.writeInt(oid);
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);

        return mplew.getPacket();
    }

    /**
     * Gets a packet to remove a door.
     *
     * @param oid  The door's ID.
     * @param town
     * @return The remove door packet.
     */
    public static MaplePacket removeDoor(int oid, boolean town) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        if (town) {
            mplew.writeShort(SendPacketOpcode.SPAWN_PORTAL.getValue());
            mplew.writeInt(999999999);
            mplew.writeInt(999999999);
        } else {
            mplew.writeShort(SendPacketOpcode.REMOVE_DOOR.getValue());
            mplew.write(/*town ? 1 : */0);
            mplew.writeInt(oid);
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet to spawn a special map object.
     *
     * @param summon
     * @param skillLevel The level of the skill used.
     * @param animated   Animated spawn?
     * @return The spawn packet for the map object.
     */
    public static MaplePacket spawnSpecialMapObject(MapleSummon summon, int skillLevel, boolean animated) {
        // 72 00 29 1D 02 00 FD FE 30 00 19 7D FF BA 00 04 01 00 03 01 00
        // 85 00 [6A 4D 27 00] [35 1F 00 00] [2D 5D 20 00] [0C] [8C 16] [CA 01] [03] [00] [00] [01] [01] [00]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_SPECIAL_MAPOBJECT.getValue());
        mplew.writeInt(summon.getOwner().getId());
        mplew.writeInt(summon.getObjectId()); // Supposed to be Object ID, but this works too! <3
        mplew.writeInt(summon.getSkill());
        mplew.write(10);
        mplew.write(skillLevel);
        mplew.writeShort(summon.getPosition().x);
        mplew.writeShort(summon.getPosition().y);
        mplew.write(3); // test
        mplew.write(0); // test
        mplew.write(0); // test
        mplew.write(summon.getMovementType().getValue()); // 0 = don't move, 1 = follow (4th mage summons?), 2/4 = only tele follow, 3 = bird follow
        mplew.write(1); // 0 and the summon can't attack - but puppets don't attack with 1 either ^.-
        mplew.write(animated ? 0 : 1);

        return mplew.getPacket();
    }

    /**
     * Gets a packet to remove a special map object.
     *
     * @param summon
     * @param animated Animated removal?
     * @return The packet removing the object.
     */
    public static MaplePacket removeSpecialMapObject(MapleSummon summon, boolean animated) {
        // [86 00] [6A 4D 27 00] 33 1F 00 00 02
        // 92 00 36 1F 00 00 0F 65 85 01 84 02 06 46 28 00 06 81 02 01 D9 00 BD FB D9 00 BD FB 38 04 2F 21 00 00 10 C1 2A 00 06 00 06 01 00 01 BD FB FC 00 BD FB 6A 04 88 1D 00 00 7D 01 AF FB
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_SPECIAL_MAPOBJECT.getValue());
        mplew.writeInt(summon.getOwner().getId());
        mplew.writeInt(summon.getObjectId());
        mplew.write(animated ? 4 : 1); // ?
        return mplew.getPacket();
    }

    /**
     * Adds item info to existing MaplePacketLittleEndianWriter.
     *
     * @param mplew            The MaplePacketLittleEndianWriter to write to.
     * @param item             The item to add info about.
     * @param leaveOutPosition Leave out the position info?
     */
    private static void addItemInfo(MaplePacketLittleEndianWriter mplew, IItem item, boolean leaveOutPosition) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        boolean ring = false;
        IEquip equip = null;
        final MaplePet pet = item.getPet();
        if (item.getType() == IItem.EQUIP) {
            equip = (IEquip) item;
            if (equip.getRingId() > -1) {
                ring = true;
            }
        }
        short pos = item.getPosition();
        boolean masking = false;
        if (!leaveOutPosition) {
            if (pos <= (byte) -1) {
                pos *= -1;
                if (pos > 100 || ring) {
                    masking = true;
                    mplew.writeShort(pos - 100);
                } else {
                    mplew.writeShort(pos);
                }
            } else {
                if (item.getType() == IItem.EQUIP) {
                    mplew.writeShort(pos);
                } else {
                    mplew.write(pos);
                }
            }
        }
        if (ii.isPet(item.getItemId())) {
            mplew.write(3);
        } else {
            mplew.write(item.getType()); //2 for safety charms
        }
        mplew.writeInt(item.getItemId());
        if (ring) {
            if (ii.isWeddingRing(item.getItemId())) {
                mplew.write(0);
            } else {
                mplew.write(1);
                mplew.writeLong(equip.getRingId());
            }
        } else {
            mplew.write(ii.isCashItem(item.getItemId()) ? 1 : 0);
            if (ii.isPet(item.getItemId())) {
                mplew.writeLong(pet.getUniqueId());
            } else if (ii.isCashItem(item.getItemId())) {
                mplew.writeLong(1000000);
            }
        }
        mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getExpiration().getTime()));
        if (item.getType() == IItem.EQUIP) {
            mplew.write(equip.getUpgradeSlots());
            mplew.write(equip.getLevel());
            mplew.writeShort(equip.getStr()); // str
            mplew.writeShort(equip.getDex()); // dex
            mplew.writeShort(equip.getInt()); // int
            mplew.writeShort(equip.getLuk()); // luk
            mplew.writeShort(equip.getHp()); // hp
            mplew.writeShort(equip.getMp()); // mp
            mplew.writeShort(equip.getWatk()); // watk
            mplew.writeShort(equip.getMatk()); // matk
            mplew.writeShort(equip.getWdef()); // wdef
            mplew.writeShort(equip.getMdef()); // mdef
            mplew.writeShort(equip.getAcc()); // accuracy
            mplew.writeShort(equip.getAvoid()); // avoid
            mplew.writeShort(equip.getHands()); // hands
            mplew.writeShort(equip.getSpeed()); // speed
            mplew.writeShort(equip.getJump()); // jump
            mplew.writeMapleAsciiString(equip.getOwner());
            mplew.writeShort(equip.getFlag());
            mplew.write(0);
            mplew.write(0); // item level
            mplew.writeInt(0); // item exp
            //mplew.writeInt(-1);
            mplew.writeInt(equip.getViciousHammers()); // vicious hammers
            if (!ii.isCashItem(equip.getItemId())) {
                mplew.writeLong(-1);
            }
            if (masking) {
                mplew.writeLong(FileTimeUtil.getFileTimestamp(System.currentTimeMillis()));
            } else {
                mplew.writeLong(94354848000000000L);
            }
            mplew.writeInt(-1);
        } else if (ii.isPet(item.getItemId())) {
            final String petname = pet.getName();
            mplew.writeAsciiString(petname);
            for (int i = petname.length(); i < 13; i++) {
                mplew.write(0);
            }
            mplew.write(pet.getLevel());
            mplew.writeShort(pet.getCloseness());
            mplew.write(pet.getFullness());
            mplew.writeLong(FileTimeUtil.getFileTimestamp(FileTimeUtil.getDefaultPetTimestamp().getTime()));
            mplew.writeInt(1); // perm
            mplew.writeInt(0); // if above is 0, this value should be the time it expires in millis i.e., 18000 = 5 hours
            mplew.writeShort(0);
        } else {
            mplew.writeShort(item.getQuantity());
            mplew.writeMapleAsciiString(item.getOwner());
            mplew.writeShort(item.getFlag());
            if (ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId())) {
                mplew.writeLong(-1);
            }
        }
    }

    /**
     * Gets the response to a relog request.
     *
     * @return The relog response packet.
     */
    public static MaplePacket getRelogResponse() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter(3);

        mplew.writeShort(SendPacketOpcode.RELOG_RESPONSE.getValue());
        mplew.write(1);

        return mplew.getPacket();
    }

    /**
     * Gets a server message packet.
     *
     * @param message The message to convey.
     * @return The server message packet.
     */
    public static MaplePacket serverMessage(String message) {
        return serverMessage(4, 0, message, true, false, null);
    }

    /**
     * Gets a server notice packet.
     * <p/>
     * Possible values for <code>type</code>:<br>
     * 0: [Notice]<br>
     * 1: Popup<br>
     * 2: Megaphone<br>
     * 3: Super Megaphone<br>
     * 4: Scrolling message at top<br>
     * 5: Pink Text<br>
     * 6: Lightblue Text
     *
     * @param type    The type of the notice.
     * @param message The message to convey.
     * @return The server notice packet.
     */
    public static MaplePacket serverNotice(int type, String message) {
        return serverMessage(type, 0, message, false, false, null);
    }

    /**
     * Gets a server notice packet.
     * <p/>
     * Possible values for <code>type</code>:<br>
     * 0: [Notice]<br>
     * 1: Popup<br>
     * 2: Megaphone<br>
     * 3: Super Megaphone<br>
     * 4: Scrolling message at top<br>
     * 5: Pink Text<br>
     * 6: Lightblue Text
     *
     * @param type    The type of the notice.
     * @param channel The channel this notice was sent on.
     * @param message The message to convey.
     * @return The server notice packet.
     */
    public static MaplePacket serverNotice(int type, int channel, String message) {
        return serverMessage(type, channel, message, false, false, null);
    }

    public static MaplePacket serverNotice(int type, int channel, String message, boolean smegaEar) {
        return serverMessage(type, channel, message, false, smegaEar, null);
    }

    public static MaplePacket itemSuperMegaphone(int type, int channel, String message, boolean smegaEar, IItem item) {
        return serverMessage(type, channel, message, false, smegaEar, item);
    }

    /**
     * Gets a server message packet.
     * <p/>
     * Possible values for <code>type</code>:<br>
     * 0: [Notice]<br>
     * 1: Popup<br>
     * 2: Megaphone<br>
     * 3: Super Megaphone<br>
     * 4: Scrolling message at top<br>
     * 5: Pink Text<br>
     * 6: Lightblue Text
     *
     * @param type          The type of the notice.
     * @param channel       The channel this notice was sent on.
     * @param message       The message to convey.
     * @param servermessage Is this a scrolling ticker?
     * @return The server notice packet.
     */
    private static MaplePacket serverMessage(int type, int channel, String message, boolean servermessage, boolean allowWhisper, IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        // 44 00 03 07 00 54 65 73 74 69 6e 67 ff 00
        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
        mplew.write(type);
        if (servermessage) {
            mplew.write(message.length() > 0 ? 1 : 0);
        }
        if (message.length() > 0) {
            mplew.writeMapleAsciiString(message);
        }
        if (type == 3 || type == 8) {
            mplew.write(channel - 1);
            mplew.write(allowWhisper ? 1 : 0);
        }
        if (type == 8) {
            mplew.write(item != null ? 1 : 0);
            if (item != null) {
                addItemInfo(mplew, item, true);
            }
        }
        if (type == 6) {
            mplew.writeInt(0);
        }

        return mplew.getPacket();
    }

    public static MaplePacket playerMessage(String message) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(9);
        mplew.writeMapleAsciiString(message);

        return mplew.getPacket();
    }

    public static MaplePacket topMessage(String message) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TOP_MSG.getValue());
        mplew.writeMapleAsciiString(message);

        return mplew.getPacket();
    }

    public static MaplePacket boxMessage(String message) { // same as a notice box
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOX_MSG.getValue());
        mplew.writeMapleAsciiString(message);

        return mplew.getPacket();
    }

    public static MaplePacket portalBlock(byte status) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BLOCK_PORTAL.getValue());
        mplew.write(status);

        return mplew.getPacket();
    }

    /**
     * Gets a general error message packet.
     * <p/>
     * Possible values for <code>type</code>:<br>
     * 1: You cannot move that channel. Please try again later.<br>
     * 2: You cannot go into the cash shop. Please try again later.<br>
     * 3: The Item-Trading Shop is currently unavailable. Please try again later.<br>
     * 4: You cannot go into the trade shop, due to the limitation of user count.<br>
     * 5: You do not meet the minimum level requirement to access the Trade Shop.<br>
     *
     * @param type The type of the notice.
     * @return The general error message packet.
     */
    public static MaplePacket errorMessage(byte type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GENERAL_ERROR_MESSAGES.getValue());
        mplew.write(type);

        return mplew.getPacket();
    }

    /**
     * Gets an avatar megaphone packet.
     *
     * @param chr     The character using the avatar megaphone.
     * @param channel The channel the character is on.
     * @param itemId  The ID of the avatar-mega.
     * @param message The message that is sent.
     * @param ear
     * @return The avatar mega packet.
     */
    public static MaplePacket getAvatarMega(MapleCharacter chr, int channel, int itemId, List<String> message, boolean ear) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.AVATAR_MEGA.getValue());
        mplew.writeInt(itemId);
        mplew.writeMapleAsciiString(chr.getName());
        for (String s : message) {
            mplew.writeMapleAsciiString(s);
        }
        mplew.writeInt(channel - 1); // channel
        mplew.write(ear ? 1 : 0);
        addCharLook(mplew, chr, true);

        return mplew.getPacket();
    }

    /**
     * Gets an triple megaphone packet.
     *
     * @param messages the 3 messages in an array.
     * @param channel  The channel the character is on.
     * @param showEar  whether to show the whisper ear.
     */
    public static MaplePacket getTripleMegaphone(byte numLines, String[] messages, int channel, boolean showEar) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SERVERMESSAGE.getValue());
        mplew.write(10);
        mplew.writeMapleAsciiString(messages[0]);
        mplew.write(numLines);
        for (int i = 1; i < numLines; i++) {
            mplew.writeMapleAsciiString(messages[i]);
        }
        mplew.write(channel - 1);
        mplew.write(showEar ? 1 : 0);
        for (int i = 0; i < 8; i++) {
            mplew.write(channel - 1);
        }
        mplew.write(0);
        mplew.write(1);

        return mplew.getPacket();
    }

    /**
     * Gets a NPC spawn packet.
     *
     * @param life The NPC to spawn.
     * @return The NPC spawn packet.
     */
    public static MaplePacket spawnNPC(MapleNPC life) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_NPC.getValue());
        mplew.writeInt(life.getObjectId());
        mplew.writeInt(life.getId());
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getCy());
        mplew.write(life.getF() == 1 ? 0 : 1);
        mplew.writeShort(life.getFh());
        mplew.writeShort(life.getRx0());
        mplew.writeShort(life.getRx1());
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket spawnNPCRequestController(MapleNPC life, boolean show) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_NPC_REQUEST_CONTROLLER.getValue());
        mplew.write(1);
        mplew.writeInt(life.getObjectId());
        mplew.writeInt(life.getId());
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getCy());
        mplew.write(life.getF() == 1 ? 0 : 1);
        mplew.writeShort(life.getFh());
        mplew.writeShort(life.getRx0());
        mplew.writeShort(life.getRx1());
        mplew.write(show ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket removeNPC(int oid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_NPC.getValue());
        mplew.writeInt(oid);

        return mplew.getPacket();
    }

    /**
     * Handles monster spawning
     *
     * @param life     The mob to perform operations with.
     * @param newSpawn New spawn (fade in?)
     * @param effect   The spawn effect to use.
     * @param link     Spawned by another mob
     * @return The spawn/control packet.
     */
    public static MaplePacket spawnMonster(MapleMonster life, boolean newSpawn, int effect, int link) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER.getValue());
        mplew.writeInt(life.getObjectId());
        mplew.write(life.isFake() ? 5 : 1);
        mplew.writeInt(life.getId());
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.write(0);
        mplew.write(0x88);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getPosition().y);
        mplew.write(life.getStance());
        mplew.writeShort(life.getFh());
        mplew.writeShort(life.getOriginalFh());
        if (effect != 0 || link != 0) {
            mplew.write(effect != 0 ? effect : -3);
            mplew.writeInt(link);
        } else {
            if (effect > 0) {
                mplew.write(effect);
                mplew.write(0);
                mplew.writeShort(0);
            }
            mplew.write(newSpawn ? -2 : -1);
        }
        mplew.write(-1);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket makeMonsterInvisible(MapleMonster life) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
        mplew.write(0);
        mplew.writeInt(life.getObjectId());

        return mplew.getPacket();
    }

    /**
     * Gets a control monster packet.
     *
     * @param life     The monster to give control to.
     * @param newSpawn Is it a new spawn?
     * @param aggro    Aggressive monster?
     * @return The monster control packet.
     */
    public static MaplePacket controlMonster(MapleMonster life, boolean newSpawn, boolean aggro) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
        mplew.write(aggro ? 2 : 1);
        mplew.writeInt(life.getObjectId());
        mplew.write(life.isFake() ? 5 : 1);
        mplew.writeInt(life.getId());
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.write(0);
        mplew.writeShort(0);
        mplew.write(8);
        mplew.writeInt(0);
        mplew.writeShort(life.getPosition().x);
        mplew.writeShort(life.getPosition().y);
        mplew.write(life.getStance());
        mplew.writeShort(life.getFh());
        mplew.writeShort(life.getOriginalFh());
        mplew.write(life.isFake() ? -4 : newSpawn ? -2 : -1);
        mplew.write(-1);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    /**
     * Gets a stop control monster packet.
     *
     * @param oid The ObjectID of the monster to stop controlling.
     * @return The stop control monster packet.
     */
    public static MaplePacket stopControllingMonster(int oid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_MONSTER_CONTROL.getValue());
        mplew.write(0);
        mplew.writeInt(oid);

        return mplew.getPacket();
    }

    /**
     * Gets a response to a move monster packet.
     *
     * @param objectid  The ObjectID of the monster being moved.
     * @param moveid    The movement ID.
     * @param currentMp The current MP of the monster.
     * @param useSkills Can the monster use skills?
     * @return The move response packet.
     */
    public static MaplePacket moveMonsterResponse(int objectid, short moveid, int currentMp, boolean useSkills) {
        return moveMonsterResponse(objectid, moveid, currentMp, useSkills, 0, 0);
    }

    /**
     * Gets a response to a move monster packet.
     *
     * @param objectid   The ObjectID of the monster being moved.
     * @param moveid     The movement ID.
     * @param currentMp  The current MP of the monster.
     * @param useSkills  Can the monster use skills?
     * @param skillId    The skill ID for the monster to use.
     * @param skillLevel The level of the skill to use.
     * @return The move response packet.
     */
    public static MaplePacket moveMonsterResponse(int objectid, short moveid, int currentMp, boolean useSkills, int skillId, int skillLevel) {
        // A1 00 18 DC 41 00 01 00 00 1E 00 00 00
        // A1 00 22 22 22 22 01 00 00 00 00 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MOVE_MONSTER_RESPONSE.getValue());
        mplew.writeInt(objectid);
        mplew.writeShort(moveid);
        mplew.write(useSkills ? 1 : 0);
        mplew.writeShort(currentMp);
        mplew.write(skillId);
        mplew.write(skillLevel);

        return mplew.getPacket();
    }

    /**
     * Gets a general chat packet.
     *
     * @param cidfrom The character ID who sent the chat.
     * @param text    The text of the chat.
     * @param whiteBG
     * @param show
     * @return The general chat packet.
     */
    public static MaplePacket getChatText(int cidfrom, String text, boolean whiteBG, int show) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHATTEXT.getValue());
        mplew.writeInt(cidfrom);
        mplew.write(whiteBG ? 1 : 0);
        mplew.writeMapleAsciiString(text);
        mplew.write(show);

        return mplew.getPacket();
    }

    /**
     * For testing only! Gets a packet from a hexadecimal string.
     *
     * @param hex The hexadecimal packet to create.
     * @return The MaplePacket representing the hex string.
     */
    public static MaplePacket getPacketFromHexString(String hex) {
        byte[] b = HexTool.getByteArrayFromHexString(hex);
        return new ByteArrayMaplePacket(b);
    }

    public static MaplePacket showItemExpirations(List<Pair<Short, IItem>> items, boolean itemLock) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(itemLock ? 11 : 8);
        mplew.write(items.size());
        for (Pair<Short, IItem> item : items) {
            mplew.writeInt(item.getRight().getItemId());
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet for showing information.
     *
     * @param type     Valid Types: 0 to 11 as of v0.75
     * @param typeExt  Subtype (used only if type = 0)
     * @param itemId   Item ID
     * @param quantity Amount of item(s) gained
     * @param mesos    Amount of meso(s) gained
     * @param iNetMeso Amount of Internet Cafe meso(s) gained
     * @param GP       Amount of GP gained
     * @return The showStatusInfo packet
     */
    public static MaplePacket showStatusInfo(byte type, byte typeExt, int itemId, int quantity, boolean loseMeso, int mesos, short iNetMeso, int GP) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(type);
        switch (type) {
            case 0:
                mplew.write(typeExt);
                if (typeExt == 0 || typeExt == 2) {
                    mplew.writeInt(itemId); // You have gained an item ({item name})
                    mplew.writeInt(quantity);
                } else if (typeExt == 1) {
                    mplew.write(loseMeso ? 1 : 0); // if != 0, A portion was not found after falling on the ground.
                    mplew.writeInt(mesos); // You have gained mesos (+0).
                    mplew.writeShort(iNetMeso); // Internet Cafe Meso Bonus
                }
                break;
            case 1: // updateQuestInfo
                break;
            case 2: // [{Item Name}] has passed its expiration date and will be removed from your inventory.
                mplew.writeInt(itemId);
                break;
            case 3: // getShowExpGain
                break;
            case 4: // getShowFameGain
                break;
            case 5: // meso in chat
                mplew.writeInt(mesos);
                break;
            case 6: // GP Gained
                mplew.writeInt(GP);
                break;
            case 7: // buffInfo
                mplew.writeInt(itemId);
                break;
            case 8: // showItemExpirations (The item [{Item Name}] has been expired, and therefore, deleted from your inventory.)
                break;
            case 9: // playerMessage
                break;
            case 10: // updateQuestExInfo
                break;
            case 11: // showItemExpirations ([{Item Name}]'s seal has expired.)
                break;
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show an EXP increase.
     *
     * @param gain   The amount of EXP gained.
     * @param inChat In the chat box?
     * @param white  White text or yellow?
     * @return The exp gained packet.
     */
    public static MaplePacket getShowExpGain(int gain, int partyBonus, boolean inChat, boolean white) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(3); // 3 = exp, 4 = fame, 5 = mesos, 6 = guildpoints
        mplew.write(white ? 1 : 0);
        mplew.writeInt(gain);
        mplew.write(inChat ? 1 : 0);
        mplew.writeInt(0); // event exp
        mplew.write(0); // a bonus exp of # for every 3rd monster defeated
        mplew.write(0); // party exp divider (Obsolete as of v0.83?)
        mplew.writeInt(0); // wedding exp
        // if bonus exp of # for every 3rd monster defeated > 0
        // mplew.write(0); // bonus exp for hunting for over # hours
        if (inChat) {
            mplew.write(0); // 'Spirit' Week Event
            //if (above byte > 0)
            // mplew.write(0); // Next # of quests will include additional exp
            // mplew.write(0); // ??
        }
        mplew.write(0); //
        mplew.writeInt(partyBonus); // Bonus EXP for Party
        mplew.writeInt(0); // Equip Item Bonus EXP
        mplew.writeInt(0); // Internet Cafe EXP Bonus
        mplew.writeInt(0); // Rainbow Week Bonus EXP

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show a fame gain.
     *
     * @param gain How many fame gained.
     * @return The meso gain packet.
     */
    public static MaplePacket getShowFameGain(int gain) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(4);
        mplew.writeInt(gain);

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show a meso gain.
     *
     * @param mesos  How many mesos gained.
     * @param inChat Show in the chat window?
     * @return The meso gain packet.
     */
    public static MaplePacket getShowMesoGain(int mesos, boolean inChat, boolean loseMeso) {
        return showStatusInfo((byte) (inChat ? 5 : 0), (byte) (inChat ? 0 : 1), 0, 0, loseMeso, mesos, (short) 0, 0);
    }

    /**
     * Gets a packet telling the client to show a item gain.
     *
     * @param itemId   The ID of the item gained.
     * @param quantity How many items gained.
     * @return The item gain packet.
     */
    public static MaplePacket getShowItemGain(int itemId, short quantity) {
        return getShowItemGain(itemId, quantity, false);
    }

    public static MaplePacket getShowItemGain(List<Pair<Integer, Integer>> itemAmount) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
        mplew.write(3);
        mplew.write(itemAmount.size());
        for (Pair<Integer, Integer> items : itemAmount) {
            mplew.writeInt(items.getLeft());
            mplew.writeInt(items.getRight());
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show an item gain.
     *
     * @param itemId   The ID of the item gained.
     * @param quantity The number of items gained.
     * @param inChat   Show in the chat window?
     * @return The item gain packet.
     */
    public static MaplePacket getShowItemGain(int itemId, short quantity, boolean inChat) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        if (inChat) {
            mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
            mplew.write(3);
            mplew.write(1);
            mplew.writeInt(itemId);
            mplew.writeInt(quantity);
        } else {
            return showStatusInfo((byte) 0, (byte) 0, itemId, quantity, false, 0, (short) 0, 0);
        }

        return mplew.getPacket();
    }

    /**
     * Gets a packet for showing effects and animations.
     * 0 = level up animation
     * 7 = portal
     * 8 = job change
     * 9 = quest complete
     * 13 = monsterbook add
     * 15 = item level up?
     * 17 = ?? animation
     * 19 = nothing
     * 26 = You have revived on the current map through the effect of the Spirit Stone.
     *
     * @param effect Valid Effects: 0 to 26 as of v0.83
     * @param skill  Skill ID
     * @param pLevel Player Level
     * @param sLevel Skill Level
     * @param bool   Only used for some skills such as berserk and monster magnet
     * @param itemId Item ID
     * @param path   Path to the animation or effect
     * @return The showStatusInfo packet
     */
    public static MaplePacket showMiscEffects(byte effect, int skill, byte pLevel, byte sLevel, boolean bool, int itemId, String path) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_GAIN_INCHAT.getValue());
        mplew.write(effect);
        switch (effect) {
            case 1:
                mplew.writeInt(skill);
                mplew.write(pLevel);
                mplew.write(sLevel);
                if (skill == 1320006 || skill == 1121001 || skill == 1221001 || skill == 1321001) {
                    mplew.write((byte) (bool ? 1 : 0));
                }
                break;
            case 2: // Shows skill animation
                mplew.writeInt(skill);
                mplew.write(sLevel);
                break;
            case 3: // getShowItemGain
                // There is a weird check that read a string + int if it's a certain item, other than that it's fine
                break;
            case 4: // pet animations
                mplew.write(pLevel); // Effect (0 - 3)
                mplew.write(sLevel); // Pet Index
                break;
            case 5: // skill effect animation?
                mplew.writeInt(skill);
                break;
            case 6: // exp charms message
                mplew.write(skill); // safety charm? if so takes next 2 bytes otherwise read last int
                mplew.write(pLevel); // times left
                mplew.write(sLevel); // days left
                if (skill == 0) {
                    mplew.writeInt(itemId); // item id
                }
                break;
            case 10: // HP Gain effect
                mplew.write(pLevel); // amount of HP
                break;
            case 11: // item buff effect
                mplew.writeInt(itemId); // item id
                break;
            case 12: // ??
                mplew.writeMapleAsciiString(path); // ?
                break;
            case 14: // ??
                mplew.writeInt(0); // ?
                mplew.write(0); // ?
                mplew.writeMapleAsciiString(path); // ?
                break;
            case 16: // forge success/fail animation (0 = success, 1 = fail)
                mplew.writeInt(bool ? 1 : 0); //
                break;
            case 18: // scenes
                mplew.writeMapleAsciiString(path); // path to scene
                break;
            case 20: // ?
                mplew.writeInt(0); // ?
                break;
            case 21: // Wheel of Destiny
                mplew.write(pLevel); // Amount of wheels used
                break;
            case 22:
                mplew.writeMapleAsciiString(path); // ?
                break;
            case 23: // animations
                mplew.writeMapleAsciiString(path); // path to animation
                mplew.writeInt(skill); // ?
                break;
            case 24: // ?
                mplew.writeInt(0);
                mplew.writeMapleAsciiString(path);
                break;
            case 25: // ?
                mplew.writeMapleAsciiString(path);
                break;
        }

        return mplew.getPacket();
    }

    public static MaplePacket killMonster(int oid, boolean animation) {
        return killMonster(oid, animation ? 1 : 0);
    }

    /**
     * Gets a packet telling the client that a monster was killed.
     *
     * @param oid       The objectID of the killed monster.
     * @param animation 0 = dissapear, 1 = fade out, 2+ = special
     * @return The kill monster packet.
     */
    public static MaplePacket killMonster(int oid, int animation) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_MONSTER.getValue());
        mplew.writeInt(oid);
        mplew.write(animation); // Not a boolean, really an int type

        return mplew.getPacket();
    }

    /**
     * Gets a packet telling the client to show mesos coming out of a map
     * object.
     *
     * @param amount     The amount of mesos.
     * @param itemoid    The ObjectID of the dropped mesos.
     * @param dropperoid The OID of the dropper.
     * @param ownerid    The ID of the drop owner.
     * @param dropfrom   Where to drop from.
     * @param dropto     Where the drop lands.
     * @param mod        ?
     * @return The drop mesos packet.
     */
    public static MaplePacket dropMesoFromMapObject(int amount, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod, byte type, boolean isPlayerDrop) {
        return dropItemFromMapObjectInternal(amount, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, true, null, type, isPlayerDrop);
    }

    /**
     * Gets a packet telling the client to show an item coming out of a map
     * object.
     *
     * @param itemid     The ID of the dropped item.
     * @param itemoid    The ObjectID of the dropped item.
     * @param dropperoid The OID of the dropper.
     * @param ownerid    The ID of the drop owner.
     * @param dropfrom   Where to drop from.
     * @param dropto     Where the drop lands.
     * @param mod        ?
     * @return The drop mesos packet.
     */
    public static MaplePacket dropItemFromMapObject(int itemid, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod, Timestamp expiration, byte type, boolean isPlayerDrop) {
        return dropItemFromMapObjectInternal(itemid, itemoid, dropperoid, ownerid, dropfrom, dropto, mod, false, expiration, type, isPlayerDrop);
    }

    /**
     * Internal function to get a packet to tell the client to drop an item onto
     * the map.
     *
     * @param itemid     The ID of the item to drop.
     * @param itemoid    The ObjectID of the dropped item.
     * @param dropperoid The OID of the dropper.
     * @param ownerid    The ID of the drop owner.
     * @param dropfrom   Where to drop from.
     * @param dropto     Where the drop lands.
     * @param mod        ?
     * @param mesos      Is the drop mesos?
     * @return The item drop packet.
     */
    private static MaplePacket dropItemFromMapObjectInternal(int itemid, int itemoid, int dropperoid, int ownerid, Point dropfrom, Point dropto, byte mod, boolean mesos, Timestamp expiration, byte type, boolean isPlayerDrop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DROP_ITEM_FROM_MAPOBJECT.getValue());
        mplew.write(mod); // 1 with animation, 2 without
        mplew.writeInt(itemoid);
        mplew.write(mesos ? 1 : 0); // 1 = mesos, 0 = item
        mplew.writeInt(itemid); // or meso
        mplew.writeInt(ownerid); // party id if in party
        mplew.write(type); // 0 = wait time for anyone who isn't the owner (normal drops), 1 = wait time for owner's party, 2 = FFA, 3 = Explosive & FFA, 4 = ffa meso
        mplew.writeShort(dropto.x);
        mplew.writeShort(dropto.y);
        if (mod != 2) {
            mplew.writeInt(ownerid);
            mplew.writeShort(dropfrom.x);
            mplew.writeShort(dropfrom.y);
            mplew.writeShort(0);
        } else {
            mplew.writeInt(dropperoid);
        }
        if (!mesos && expiration != null) {
            mplew.writeLong(FileTimeUtil.getFileTimestamp(expiration.getTime()));
        }
        mplew.write(isPlayerDrop ? 0 : 1);

        return mplew.getPacket();
    }

    public static MaplePacket spawnPlayerMapobject(MapleCharacter chr) {
        return spawnPlayerMapobject(chr, true);
    }

    /* (non-javadoc)
     * TODO: make MapleCharacter a mapobject, remove the need for passing oid
     * here.
     */

    /**
     * Gets a packet spawning a player as a mapobject to other clients.
     *
     * @param chr The character to spawn to other clients.
     * @return The spawn player packet.
     */
    public static MaplePacket spawnPlayerMapobject(MapleCharacter chr, boolean showGuildInfo) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_PLAYER.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(chr.getLevel());
        mplew.writeMapleAsciiString(chr.getName());
        MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
        boolean guildInfo = gs != null && showGuildInfo;
        mplew.writeMapleAsciiString(guildInfo ? gs.getName() : "");
        mplew.writeShort(guildInfo ? gs.getLogoBG() : 0);
        mplew.write(guildInfo ? gs.getLogoBGColor() : 0);
        mplew.writeShort(guildInfo ? gs.getLogo() : 0);
        mplew.write(guildInfo ? gs.getLogoColor() : 0);
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.writeShort(508);
        mplew.writeInt(chr.getBuffedValue(MapleBuffStat.MORPH) != null ? 2 : 0);
        long buffmask = 0;
        Integer buffvalue = null;
        if (chr.getBuffedValue(MapleBuffStat.DARKSIGHT) != null && !chr.isHidden()) {
            buffmask |= MapleBuffStat.DARKSIGHT.getValue();
        }
        if (chr.getBuffedValue(MapleBuffStat.COMBO) != null) {
            buffmask |= MapleBuffStat.COMBO.getValue();
            buffvalue = chr.getBuffedValue(MapleBuffStat.COMBO);
        }
        if (chr.getBuffedValue(MapleBuffStat.SHADOWPARTNER) != null) {
            buffmask |= MapleBuffStat.SHADOWPARTNER.getValue();
        }
        if (chr.getBuffedValue(MapleBuffStat.SOULARROW) != null) {
            buffmask |= MapleBuffStat.SOULARROW.getValue();
        }
        if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
            buffvalue = chr.getBuffedValue(MapleBuffStat.MORPH);
        }
        mplew.writeInt((int) (buffmask >> 32 & 0xffffffffL));
        if (buffvalue != null) {
            if (chr.getBuffedValue(MapleBuffStat.MORPH) != null) {
                mplew.writeShort(buffvalue);
            } else {
                mplew.write(buffvalue.byteValue());
            }
        }
        mplew.writeInt((int) (buffmask & 0xffffffffL));
        mplew.writeInt(0);
        mplew.writeShort(0);
        int CHAR_MAGIC_SPAWN = Randomizer.nextInt();
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.write(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.write(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeShort(0);
        mplew.write(0);
        IItem mount = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -18);
        if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
            if (mount != null) {
                mplew.writeInt(mount.getItemId());
                mplew.writeInt(chr.getMobRidingSkillId());
                mplew.writeInt(6972673);
            } else {
                mplew.writeInt(1932000);
                mplew.writeInt(5221006);
                mplew.writeInt(0x34F9D6ED);
            }
        } else {
            mplew.writeLong(0);
            mplew.writeInt(CHAR_MAGIC_SPAWN);
        }
        mplew.writeLong(0);
        mplew.write(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeShort(0);
        mplew.writeInt(Randomizer.nextInt());
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeLong(0);
        mplew.writeInt(0);
        mplew.write(0);
        mplew.writeInt(CHAR_MAGIC_SPAWN);
        mplew.writeShort(0);
        mplew.write(0);
        mplew.writeShort(chr.hasGMLevel(2) ? 1411 : chr.getJob().getId());
        addCharLook(mplew, chr, false);
        mplew.writeInt(0);
        mplew.writeInt(chr.getItemEffect());
        mplew.writeInt(chr.getChair());
        mplew.writeShort(chr.getPosition().x);
        mplew.writeShort(chr.getPosition().y);
        mplew.write(chr.getStance());
        mplew.writeShort(chr.getFoothold());
        mplew.write(0);
        final List<MaplePet> pets = chr.getPets();
        for (MaplePet pet : pets) {
            mplew.write(1);
            mplew.writeInt(pet.getItemId());
            mplew.writeMapleAsciiString(pet.getName());
            mplew.writeLong(pet.getUniqueId());
            mplew.writeShort(pet.getPos().x);
            mplew.writeShort(pet.getPos().y);
            mplew.write(pet.getStance());
            mplew.writeShort(pet.getFh());
            mplew.write(pet.hasLabelRing() ? 1 : 0);
            mplew.write(pet.hasQuoteRing() ? 1 : 0);
        }
        mplew.write(0);
        mplew.writeInt(1); // mob level
        mplew.writeInt(0); // mob exp
        mplew.writeInt(0); // mob tiredness
        if (chr.getPlayerShop() != null && chr.getPlayerShop().isOwner(chr) && chr.getPlayerShop() instanceof MaplePlayerShop) {
            addAnnounceBox(mplew, chr.getPlayerShop());
        } else {
            mplew.write(0);
        }
        final String chalkBoardText = chr.getChalkboard();
        mplew.write(chalkBoardText != null ? 1 : 0);
        if (chalkBoardText != null) {
            mplew.writeMapleAsciiString(chalkBoardText);
        }
        addCrushRingLook(mplew, chr);
        addFriendshipRingLook(mplew, chr);
        addMarriageRingLook(mplew, chr);
        mplew.write(0);
        mplew.write(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket playerGuildName(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHAR_GUILD_NAME.getValue());
        mplew.writeInt(chr.getId());
        MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
        mplew.writeMapleAsciiString(gs != null ? gs.getName() : "");

        return mplew.getPacket();
    }

    public static MaplePacket playerGuildInfo(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHAR_GUILD_INFO.getValue());
        mplew.writeInt(chr.getId());
        MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
        mplew.writeShort(gs != null ? gs.getLogoBG() : 0);
        mplew.write(gs != null ? gs.getLogoBGColor() : 0);
        mplew.writeShort(gs != null ? gs.getLogo() : 0);
        mplew.write(gs != null ? gs.getLogoColor() : 0);

        return mplew.getPacket();
    }

    /*
     * Adds a announcement box to an existing MaplePacketLittleEndianWriter.
     *
     * @param mplew The MaplePacketLittleEndianWriter to add an announcement box to.
     * @param shop  The shop to announce.
     */
	/*private static void addAnnounceBox(MaplePacketLittleEndianWriter mplew, MaplePlayerShop shop, int availability) {
		// 00: no game
		// 01: omok game
		// 02: card game
		// 04: shop
		mplew.write(4);
		mplew.writeInt(shop.getObjectId()); // gameid/shopid
		mplew.writeMapleAsciiString(shop.getDescription()); // desc
		// 00: public
		// 01: private
		mplew.write(0);
		// 00: red 4x3
		// 01: green 5x4
		// 02: blue 6x5
		// omok:
		// 00: normal
		mplew.write(0);
		// first slot: 1/2/3/4
		// second slot: 1/2/3/4
		mplew.write(1);
		mplew.write(availability);
		// 0: open
		// 1: in progress
		mplew.write(0);
	}*/
    public static MaplePacket facialExpression(MapleCharacter from, int expression) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.FACIAL_EXPRESSION.getValue());
        mplew.writeInt(from.getId());
        mplew.writeInt(expression);

        return mplew.getPacket();
    }

    private static void serializeMovementList(LittleEndianWriter lew, List<LifeMovementFragment> moves) {
        lew.write(moves.size());
        for (LifeMovementFragment move : moves) {
            move.serialize(lew);
        }
    }

    public static MaplePacket movePlayer(int cid, int oid, List<LifeMovementFragment> moves) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MOVE_PLAYER.getValue());
        mplew.writeInt(cid);
        mplew.writeInt(oid);
        serializeMovementList(mplew, moves);

        return mplew.getPacket();
    }

    public static MaplePacket moveSummon(int cid, int oid, Point startPos, List<LifeMovementFragment> moves) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MOVE_SUMMON.getValue());
        mplew.writeInt(cid);
        mplew.writeInt(oid);
        mplew.writeShort(startPos.x);
        mplew.writeShort(startPos.y);
        serializeMovementList(mplew, moves);

        return mplew.getPacket();
    }

    public static MaplePacket moveMonster(int useskill, int skill, int skill_1, int skill_2, int skill_3, int oid, Point startPos, List<LifeMovementFragment> moves) {
        /*
         * A0 00 C8 00 00 00 00 FF 00 00 00 00 48 02 7D FE 02 00 1C 02 7D FE 9C FF 00 00 2A 00 03 BD 01 00 DC 01 7D FE
         * 9C FF 00 00 2B 00 03 7B 02
         */
        //MaplePacketCreator.moveMonster(0, -1, 0, 0, 0, monster.getObjectId(), monster.getPosition(), c.getPlayer().getLastRes())
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MOVE_MONSTER.getValue());
        mplew.writeInt(oid);
        mplew.write(useskill); // 0
        mplew.write(0);
        mplew.write(skill); // -1
        mplew.write(skill_1); // 0
        mplew.write(skill_2); // 0
        mplew.write(skill_3); // 0
        mplew.write(0); // 0
        mplew.writeShort(startPos.x);
        mplew.writeShort(startPos.y);
        serializeMovementList(mplew, moves);

        return mplew.getPacket();
    }

    public static MaplePacket summonAttack(int cid, int summonSkillId, int newStance, List<SummonAttackEntry> allDamage) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SUMMON_ATTACK.getValue());
        mplew.writeInt(cid);
        mplew.writeInt(summonSkillId);
        mplew.writeShort(newStance);
        mplew.write(allDamage.size());
        for (SummonAttackEntry attackEntry : allDamage) {
            mplew.writeInt(attackEntry.getMonsterOid()); // oid
            mplew.write(6); // who knows
            mplew.writeInt(attackEntry.getDamage()); // damage
        }

        return mplew.getPacket();
    }

    public static MaplePacket closeRangeAttack(int cid, byte level, int skill, int skillLevel, int stance, int numAttackedAndDamage, List<Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>>> damage, int speed, int unk) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CLOSE_RANGE_ATTACK.getValue());
        if (skill == 4211006) { // meso explosion
            addMesoExplosion(mplew, cid, level, skill, skillLevel, stance, numAttackedAndDamage, 0, damage, speed, unk);
        } else {
            addAttackBody(mplew, cid, level, skill, skillLevel, stance, numAttackedAndDamage, 0, damage, speed, 0);
        }

        return mplew.getPacket();
    }

    public static MaplePacket rangedAttack(int cid, byte level, int skill, int skillLevel, int stance, int numAttackedAndDamage, int projectile, List<Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>>> damage, int speed, int projectilePos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.RANGED_ATTACK.getValue());
        addAttackBody(mplew, cid, level, skill, skillLevel, stance, numAttackedAndDamage, projectile, damage, speed, projectilePos);

        return mplew.getPacket();
    }

    public static MaplePacket magicAttack(int cid, byte level, int skill, int skillLevel, int stance, int numAttackedAndDamage, List<Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>>> damage, int charge, int speed) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MAGIC_ATTACK.getValue());
        addAttackBody(mplew, cid, level, skill, skillLevel, stance, numAttackedAndDamage, 0, damage, speed, 0);
        if (charge != -1) {
            mplew.writeInt(charge);
        }

        return mplew.getPacket();
    }

    private static void addAttackBody(LittleEndianWriter lew, int cid, byte level, int skill, int skillLevel, int stance, int numAttackedAndDamage, int projectile, List<Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>>> damage, int speed, int projectilePos) {
        lew.writeInt(cid);
        lew.write(numAttackedAndDamage);
        lew.write(level);
        if (skill > 0) {
            lew.write(skillLevel);
            lew.writeInt(skill);
        } else {
            lew.write(0);
        }
        lew.write(0);
        lew.writeShort(stance);
        lew.write(speed);
        lew.write(3);
        lew.writeInt(projectile);
        for (Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>> oned : damage) {
            if (oned.getRight() != null) {
                lew.writeInt(oned.getLeft().getLeft());
                lew.write(oned.getLeft().getRight());
                for (Pair<Integer, Boolean> eachd : oned.getRight()) {
                    // highest bit set = crit
                    /*
                     * damage += 0x80000000;
                     */
                    lew.writeInt(skill == 3221007 || eachd.getRight() ? eachd.getLeft() | 0x80000000 : eachd.getLeft());
                }
            }
        }
        if (projectilePos != 0) {
            lew.writeInt(projectilePos);
        }
    }

    private static void addMesoExplosion(LittleEndianWriter lew, int cid, byte level, int skill, int skillLevel, int stance, int numAttackedAndDamage, int projectile, List<Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>>> damage, int speed, int unk) {
        lew.writeInt(cid);
        lew.write(numAttackedAndDamage);
        lew.write(level);
        lew.write(skillLevel);
        lew.writeInt(skill);
        lew.write(0);
        lew.writeShort(stance);
        lew.write(speed);
        lew.write(10);
        lew.writeInt(projectile);
        for (Pair<Pair<Integer, Byte>, List<Pair<Integer, Boolean>>> oned : damage) {
            if (oned.getRight() != null) {
                lew.writeInt(oned.getLeft().getLeft());
                lew.write(unk);
                lew.write(oned.getRight().size());
                for (Pair<Integer, Boolean> eachd : oned.getRight()) {
                    lew.writeInt(eachd.getLeft());
                }
            }
        }
    }

    public static MaplePacket getNPCShop(MapleClient c, int sid, List<MapleShopItem> items) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OPEN_NPC_SHOP.getValue());
        mplew.writeInt(sid);
        mplew.writeShort(items.size());
        for (MapleShopItem item : items) {
            mplew.writeInt(item.getItemId());
            mplew.writeInt(item.getPrice());
            mplew.writeInt(0); // Perfect pitches
            mplew.writeInt(0); // Days usable
            mplew.writeInt(0); // ??
            if (!ii.isThrowingStar(item.getItemId()) && !ii.isShootingBullet(item.getItemId())) {
                mplew.writeShort(1); // stacksize
                mplew.writeShort(item.getBuyable());
            } else {
                mplew.writeShort(0);
                mplew.writeInt(0);
                // o.O getPrice sometimes returns the unitPrice not the price
                mplew.writeShort(BitTools.doubleToShortBits(ii.getPrice(item.getItemId())));
                mplew.writeShort(ii.getSlotMax(c, item.getItemId()));
            }
        }

        return mplew.getPacket();
    }

    /**
     * code (8 = sell, 0 = buy, 0x20 = due to an error the trade did not happen
     * o.o)
     *
     * @param code
     * @return
     */
    public static MaplePacket confirmShopTransaction(byte code) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CONFIRM_SHOP_TRANSACTION.getValue());
        // mplew.writeShort(0xE6); // 47 E4
        mplew.write(code); // recharge == 8?

        return mplew.getPacket();
    }

    public static MaplePacket updateMaxInventorySlots(MapleInventoryType type, byte slots) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_INVENTORY_SLOTS.getValue());
        mplew.write(type.getType());
        mplew.write(slots);

        return mplew.getPacket();
    }

    /*
     * 19 reference 00 01 00 = new while adding 01 01 00 = add from drop 00 01 01 = update count 00 01 03 = clear slot
     * 01 01 02 = move to empty slot 01 02 03 = move and merge 01 02 01 = move and merge with rest
     */
    public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item) {
        return addInventorySlot(type, item, false);
    }

    public static MaplePacket addInventorySlot(MapleInventoryType type, IItem item, boolean userUpdate) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(userUpdate ? 1 : 0);
        mplew.write(HexTool.getByteArrayFromHexString("01 00")); // add mode
        mplew.write(type.getType()); // iv type
        mplew.writeShort(item.getPosition());
        addItemInfo(mplew, item, true);

        return mplew.getPacket();
    }

    public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item) {
        return updateInventorySlot(type, item, false);
    }

    public static MaplePacket updateInventorySlot(MapleInventoryType type, IItem item, boolean userUpdate) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(userUpdate ? 1 : 0);
        mplew.write(HexTool.getByteArrayFromHexString("01 01")); // update
        mplew.write(type.getType()); // iv type
        mplew.writeShort(item.getPosition()); // slot id
        mplew.writeShort(item.getQuantity());

        return mplew.getPacket();
    }

    public static MaplePacket modifyInventory(boolean userUpdate, List<Pair<Short, IItem>> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        int equipsRemoved = 0;

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(userUpdate ? 1 : 0);
        mplew.write(items.size());
        for (Pair<Short, IItem> item : items) {
            IItem cItem = item.getRight();
            mplew.write(item.getLeft());
            mplew.write(cItem.getItemId() / 1000000);
            switch (item.getLeft()) {
                case 0: // add item or update equip
                    mplew.writeShort(cItem.getPosition());
                    addItemInfo(mplew, cItem, true);
                    break;
                case 1: // update quantity
                    mplew.writeShort(cItem.getPosition());
                    mplew.writeShort(cItem.getQuantity());
                    break;
                case 2: // move items
                    mplew.writeShort(cItem.getPrevPosition());
                    mplew.writeShort(cItem.getPosition());
                    if (cItem.getPrevPosition() > 0 && cItem.getPosition() < 0) {
                        mplew.write(2);
                    } else if (cItem.getPrevPosition() < 0 && cItem.getPosition() > 0) {
                        mplew.write(1);
                    }
                    break;
                case 3: // remove Item
                    mplew.writeShort(cItem.getPosition());
                    if (cItem.getPosition() < 0) {
                        equipsRemoved++;
                    }
                    break;
            }
        }
        mplew.writeShort(equipsRemoved);

        return mplew.getPacket();
    }

    public static MaplePacket moveInventoryItem(MapleInventoryType type, short src, short dst, byte equipIndicator) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("01 01 02"));
        mplew.write(type.getType());
        mplew.writeShort(src);
        mplew.writeShort(dst);
        if (equipIndicator != -1) {
            mplew.write(equipIndicator);
        }

        return mplew.getPacket();
    }

    public static MaplePacket clearInventoryItem(MapleInventoryType type, short slot, boolean fromDrop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(fromDrop ? 1 : 0);
        mplew.write(HexTool.getByteArrayFromHexString("01 03"));
        mplew.write(type.getType());
        mplew.writeShort(slot);

        return mplew.getPacket();
    }

    public static MaplePacket scrolledItem(IItem scroll, IItem item, boolean destroyed) {
        // 18 00 01 02 03 02 08 00 03 01 F7 FF 01
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(1); // fromdrop always true
        mplew.write(destroyed ? 2 : 3);
        mplew.write(scroll.getQuantity() > 0 ? 1 : 3);
        mplew.write(MapleInventoryType.USE.getType());
        mplew.writeShort(scroll.getPosition());
        if (scroll.getQuantity() > 0) {
            mplew.writeShort(scroll.getQuantity());
        }
        mplew.write(3);
        if (!destroyed) {
            mplew.write(MapleInventoryType.EQUIP.getType());
            mplew.writeShort(item.getPosition());
            mplew.write(0);
        }
        mplew.write(MapleInventoryType.EQUIP.getType());
        mplew.writeShort(item.getPosition());
        if (!destroyed) {
            addItemInfo(mplew, item, true);
        }
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket slotMergeComplete(boolean oneChange, byte type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SLOT_MERGE_COMPLETE.getValue());
        mplew.write(oneChange ? 1 : 0);
        mplew.write(type);

        return mplew.getPacket();
    }

    public static MaplePacket itemSortComplete(boolean oneChange, byte type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SORT_ITEM_COMPLETE.getValue());
        mplew.write(oneChange ? 1 : 0);
        mplew.write(type);

        return mplew.getPacket();
    }

    public static MaplePacket getScrollEffect(int chr, ScrollResult scrollSuccess, boolean legendarySpirit) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_SCROLL_EFFECT.getValue());
        mplew.writeInt(chr);
        switch (scrollSuccess) {
            case SUCCESS -> {
                mplew.writeShort(1);
                mplew.writeShort(legendarySpirit ? 1 : 0);
            }
            case FAIL -> {
                mplew.writeShort(0);
                mplew.writeShort(legendarySpirit ? 1 : 0);
            }
            case CURSE -> {
                mplew.write(0);
                mplew.write(1);
                mplew.writeShort(legendarySpirit ? 1 : 0);
            }
            default -> throw new IllegalArgumentException("effect in illegal range");
        }

        return mplew.getPacket();
    }

    public static MaplePacket removePlayerFromMap(int cid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_PLAYER_FROM_MAP.getValue());
        // mplew.writeShort(0x65); // 47 63
        mplew.writeInt(cid);

        return mplew.getPacket();
    }

    /**
     * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
     * 4 - explode<br/> cid is ignored for 0 and 1
     *
     * @param oid
     * @param animation
     * @param cid
     * @return
     */
    public static MaplePacket removeItemFromMap(int oid, int animation, int cid) {
        return removeItemFromMap(oid, animation, cid, false, 0);
    }

    /**
     * animation: 0 - expire<br/> 1 - without animation<br/> 2 - pickup<br/>
     * 4 - explode<br/> cid is ignored for 0 and 1.<br /><br />Flagging pet
     * as true will make a pet pick up the item.
     *
     * @param oid
     * @param animation
     * @param cid
     * @param pet
     * @param slot
     * @return
     */
    public static MaplePacket removeItemFromMap(int oid, int animation, int cid, boolean pet, int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_ITEM_FROM_MAP.getValue());
        mplew.write(animation); // expire
        mplew.writeInt(oid);
        if (animation >= 2) {
            mplew.writeInt(cid);
            if (pet) {
                mplew.write(slot);
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket updateCharLook(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_LOOK.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(1);
        addCharLook(mplew, chr, false);
        addCrushRingLook(mplew, chr);
        addFriendshipRingLook(mplew, chr);
        addMarriageRingLook(mplew, chr);

        return mplew.getPacket();
    }

    public static MaplePacket dropInventoryItem(MapleInventoryType type, short src) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("01 01 03"));
        mplew.write(type.getType());
        mplew.writeShort(src);
        if (src < 0) {
            mplew.write(1);
        }

        return mplew.getPacket();
    }

    public static MaplePacket dropInventoryItemUpdate(MapleInventoryType type, IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("01 01 01"));
        mplew.write(type.getType());
        mplew.writeShort(item.getPosition());
        mplew.writeShort(item.getQuantity());

        return mplew.getPacket();
    }

    public static MaplePacket damagePlayer(int skill, int monsteridfrom, int cid, int damage, int fake, int direction, boolean pgmr, int pgmr_1, boolean is_pg, int oid, int pos_x, int pos_y) {
        // 82 00 30 C0 23 00 FF 00 00 00 00 B4 34 03 00 01 00 00 00 00 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DAMAGE_PLAYER.getValue());
        // mplew.writeShort(0x84); // 47 82
        mplew.writeInt(cid);
        mplew.write(skill);
        mplew.writeInt(damage);
        mplew.writeInt(monsteridfrom);
        mplew.write(direction);
        if (pgmr) {
            mplew.write(pgmr_1);
            mplew.write(is_pg ? 1 : 0);
            mplew.writeInt(oid);
            mplew.write(6);
            mplew.writeShort(pos_x);
            mplew.writeShort(pos_y);
            mplew.write(0);
        } else {
            mplew.writeShort(0);
        }
        mplew.writeInt(damage);
        if (fake > 0) {
            mplew.writeInt(fake);
        }
        return mplew.getPacket();
    }

    public static MaplePacket charNameResponse(String charname, boolean nameUsed) {
        // 0D 00 0C 00 42 6C 61 62 6C 75 62 62 31 32 33 34 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHAR_NAME_RESPONSE.getValue());
        mplew.writeMapleAsciiString(charname);
        mplew.write(nameUsed ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket addNewCharEntry(MapleCharacter chr, boolean worked) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ADD_NEW_CHAR_ENTRY.getValue());
        mplew.write(worked ? 0 : 1);
        addCharEntry(mplew, chr, false);

        return mplew.getPacket();
    }

    public static MaplePacket addNewCharEntryStatus(byte status) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ADD_NEW_CHAR_ENTRY.getValue());
        mplew.write(status);

        return mplew.getPacket();
    }

    /**
     * state 0 = del ok state 12 = invalid bday
     *
     * @param cid
     * @param state
     * @return
     */
    public static MaplePacket deleteCharResponse(int cid, int state) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DELETE_CHAR_RESPONSE.getValue());
        mplew.writeInt(cid);
        mplew.write(state);

        return mplew.getPacket();
    }

    public static MaplePacket charInfo(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHAR_INFO.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(chr.getLevel());
        mplew.writeShort(chr.getJob() != null ? chr.getJob().getId() : 0);
        mplew.writeShort(chr.getFame());
        mplew.write(chr.isMarried()); // heart red or gray

        String guildName = "-";
        String allianceName = "-";
        MapleGuildSummary gs = chr.getClient().getChannelServer().getGuildSummary(chr.getGuildId());
        if (chr.getGuildId() > 0 && gs != null) {
            guildName = gs.getName();
            try {
                MapleAlliance alliance = chr.getClient().getChannelServer().getWorldInterface().getAlliance(gs.getAllianceId());
                if (alliance != null) {
                    allianceName = alliance.getName();
                }
            } catch (RemoteException re) {
                chr.getClient().getChannelServer().reconnectWorld();
            }
        }

        mplew.writeMapleAsciiString(guildName);
        mplew.writeMapleAsciiString(allianceName); // Alliance

        mplew.write(0);
        final List<MaplePet> pets = chr.getPets();
        for (MaplePet pet : pets) {
            mplew.write(1);
            mplew.writeInt(pet.getItemId()); // petid
            mplew.writeMapleAsciiString(pet.getName());
            mplew.write(pet.getLevel()); // pet level
            mplew.writeShort(pet.getCloseness()); // pet closeness
            mplew.write(pet.getFullness()); // pet fullness
            mplew.writeShort(0);
            mplew.writeInt(0);
        }
        mplew.write(0);
		/*if (chr.getMount() != null && chr.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18) != null) {
			mplew.write(chr.getMount().getItemId()); // mount
			mplew.writeInt(chr.getMount().getLevel()); // level
			mplew.writeInt(chr.getMount().getExp()); // exp
			mplew.writeInt(chr.getMount().getTiredness()); // tiredness
		}*/

        mplew.write(0); // end of mounts
        int wishlistSize = chr.getWishListSize();
        mplew.write(wishlistSize);
        if (wishlistSize > 0) {
            int[] wishlist = chr.getWishList();
            for (int i = 0; i < wishlistSize; i++) {
                mplew.writeInt(wishlist[i]);
            }
        }
        int normalcard = chr.getMonsterBook().getNormalCard(), specialcard = chr.getMonsterBook().getSpecialCard();
        mplew.writeInt(chr.getMonsterBook().getBookLevel());
        mplew.writeInt(normalcard);
        mplew.writeInt(specialcard);
        mplew.writeInt(normalcard + specialcard);
        mplew.writeInt(chr.getMonsterBook().getCardMobID(chr.getMonsterBookCover()));
        IItem medal = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -49);
        mplew.writeInt(medal != null ? medal.getItemId() : 0);
        final List<Integer> quests = chr.getAllCompletedMedalQuests();
        mplew.writeShort(quests.size());
        for (int questId : quests) {
            mplew.writeShort(questId);
        }

        return mplew.getPacket();
    }

    public static MaplePacket forfeitQuest(short quest) {
        return updateQuestInfo((byte) 0, quest, "");
    }

    public static MaplePacket completeQuest(short quest) {
        return updateQuestInfo((byte) 2, quest, "");
    }

    public static MaplePacket updateQuestInfo(byte mode, short quest, String info) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(1);
        mplew.writeShort(quest);
        mplew.write(mode);
        if (mode == 0) {
            mplew.writeMapleAsciiString(info);
            mplew.writeLong(0);
        } else if (mode == 1) {
            mplew.writeMapleAsciiString(info);
        } else if (mode == 2) {
            mplew.writeLong(FileTimeUtil.getFileTimestamp(System.currentTimeMillis()));
        }

        return mplew.getPacket();
    }

    public static MaplePacket updateQuestRecordExInfo(short quest, String info) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_STATUS_INFO.getValue());
        mplew.write(10);
        mplew.writeShort(quest);
        mplew.writeMapleAsciiString(info);

        return mplew.getPacket();
    }

    public static MaplePacket getShowQuestCompletion(int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_QUEST_COMPLETION.getValue());
        mplew.writeShort(id);

        return mplew.getPacket();
    }

    public static MaplePacket updateQuestInfo(short quest, boolean message, byte progress) {
        return updateQuestInfo(quest, message, 0, progress, 0, false);
    }

    /**
     * Gets a update quest packet.
     * <p/>
     * Possible error message values for <code>type</code>:<br>
     * 9: The quest ended due to an unknown error.<br>
     * 10: {Item Type} item inventory is full.<br>
     * 11: You do not have enough mesos.<br>
     * 13: Unable to retrieve it due to the equipment currently being worn by the character.<br>
     * 14: You may not possess more than one of this item.<br>
     * 15: The [Item Name] quest expired because the time limit ended<br>
     *
     * @param quest     The quest ID
     * @param message   Is this an error message?
     * @param npc       The NPC ID
     * @param type      The type of the packet
     * @param nextquest The quest to start after quest completion
     * @param start     Is this a start quest packet?
     * @return The update quest packet
     */
    public static MaplePacket updateQuestInfo(short quest, boolean message, int npc, byte type, int nextquest, boolean start) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_QUEST_INFO.getValue());
        mplew.write(type);
        mplew.writeShort(quest);
        if (!message) {
            mplew.writeInt(npc);
            mplew.writeShort(nextquest);
            if (start) {
                mplew.writeShort(0);
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket npcShowHide(int oid, boolean show) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_SHOWHIDE.getValue());
        mplew.writeInt(oid);
        mplew.write(show ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket npcAnimation(int oid, String info) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_ANIMATION.getValue());
        mplew.writeInt(oid);
        mplew.writeMapleAsciiString(info);

        return mplew.getPacket();
    }

    private static <E extends ValueHolder<Long>> long getLongMask(List<Pair<E, Integer>> statups) {
        long mask = 0;
        for (Pair<E, Integer> statup : statups) {
            mask |= statup.getLeft().getValue();
        }
        return mask;
    }

    private static <E extends ValueHolder<Long>> long getLongMaskFromList(List<E> statups) {
        long mask = 0;
        for (E statup : statups) {
            mask |= statup.getValue();
        }
        return mask;
    }

    public static MaplePacket giveBuffTest(int buffid, int bufflength, long mask) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        mplew.writeLong(0);
        mplew.writeLong(mask);
        mplew.writeShort(1);
        mplew.writeInt(buffid);
        mplew.writeInt(bufflength);
        mplew.writeShort(0); // ??? wk charges have 600 here o.o
        mplew.write(0); // combo 600, too
        mplew.write(0); // new in v0.56
        mplew.write(0);

        return mplew.getPacket();
    }

    /**
     * It is important that statups is in the correct order (see decleration
     * order in MapleBuffStat) since this method doesn't do automagical
     * reordering.
     *
     * @param buffid
     * @param bufflength
     * @param statups
     * @return
     */
    public static MaplePacket giveBuff(MapleCharacter c, int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        boolean er = false;
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            if (statup.getLeft().isFirst()) {
                er = true;
                break;
            }
        }
        boolean mount = bufflength == 1004 || bufflength == 10001004 || bufflength == 5221006 || bufflength == 20001004;
        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        long mask = getLongMask(statups);
        mplew.writeLong(mount || er ? mask : 0);
        mplew.writeLong(mount || er ? 0 : mask);
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            if (statup.getRight().shortValue() >= 1000 && statup.getRight().shortValue() != 1002) {
                mplew.writeShort(statup.getRight().shortValue() + c.getGender() * 100);
            } else {
                mplew.writeShort(statup.getRight().shortValue());
            }
            mplew.writeInt(buffid);
            mplew.writeInt(bufflength);
        }
        if (mount) {
            mplew.writeInt(0);
        } else {
            mplew.writeShort(0); // ??? wk charges have 600 here o.o
        }
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    // 00 00 00 00 00 01 00 00
    // 00 00 00 00 00 00 00 00
    // 00 00 01 00 00 00 7E 83 4F 00 00 00 00 00 00 F3 F3 53 00 00 00
    public static MaplePacket giveHomingBeacon(int buffid, int moid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        mplew.writeLong(MapleBuffStat.HOMING_BEACON.getValue());
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(1);
        mplew.writeInt(buffid);
        mplew.writeInt(0);
        mplew.write(0);
        mplew.writeInt(moid);
        mplew.writeShort(0);
        return mplew.getPacket();
    }

    public static MaplePacket buffInfo(int itemId) {
        return showStatusInfo((byte) 7, (byte) 0, itemId, 0, false, 0, (short) 0, 0);
    }

    public static MaplePacket showAnimationEffect(byte effect) {
        return showAnimationEffect(effect, null);
    }

    public static MaplePacket showAnimationEffect(byte effect, String path) {
        return showMiscEffects(effect, 1, (byte) 0, (byte) 0, false, 0, path);
    }

    public static MaplePacket showAnimationEffect(byte effect, String path, int num) {
        return showMiscEffects(effect, num, (byte) 0, (byte) 0, false, 0, path);
    }

    public static MaplePacket showMonsterRiding(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        long mask = getLongMask(statups);
        mplew.writeLong(mask);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(buffid);
        mplew.writeInt(bufflength);
        mplew.write(1);
        mplew.write(1);
        mplew.writeInt(0);
        mplew.write(0);
        mplew.write(3);

        return mplew.getPacket();
    }

    public static MaplePacket giveGMHide(boolean hidden) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GM.getValue());
        mplew.write(16);
        mplew.write(hidden ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket showMonsterRiding(int cid, List<Pair<MapleBuffStat, Integer>> statups, int itemId, int skillId) {
        // 8A 00 24 46 32 00 80 04 00 00 00 00 00 00 F4 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
        mplew.writeInt(cid);
        long mask = getLongMask(statups);
        mplew.writeLong(mask);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(itemId);
        mplew.writeInt(skillId);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket giveDash(List<Pair<MapleBuffStat, Integer>> statups, int skillId, int duration) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        mplew.writeLong(getLongMask(statups));
        mplew.writeLong(0);
        mplew.writeShort(0);
        for (Pair<MapleBuffStat, Integer> stat : statups) {
            mplew.writeInt(stat.getRight().shortValue());
            mplew.writeInt(skillId);
            mplew.writeInt(0);
            mplew.write(0);
            mplew.writeShort(duration);
        }
        mplew.writeShort(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket showDashEffecttoOthers(int cid, List<Pair<MapleBuffStat, Integer>> statups, int skillId, int duration) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
        mplew.writeInt(cid);
        mplew.writeLong(getLongMask(statups));
        mplew.writeLong(0);
        mplew.writeShort(0);
        for (Pair<MapleBuffStat, Integer> stat : statups) {
            mplew.writeInt(stat.getRight().shortValue());
            mplew.writeInt(skillId);
            mplew.writeInt(0);
            mplew.write(0);
            mplew.writeShort(duration);
        }
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket giveForeignBuff(MapleCharacter c, List<Pair<MapleBuffStat, Integer>> statups, MapleStatEffect effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
        mplew.writeInt(c.getId());
        long mask = getLongMask(statups);
        mplew.writeLong(0);
        mplew.writeLong(mask);
        for (Pair<MapleBuffStat, Integer> statup : statups) {
            if (effect.isPirateMorph()) {
                mplew.writeShort(statup.getRight().shortValue() + c.getGender() * 100);
            } else {
                mplew.writeShort(statup.getRight().shortValue());
            }
        }
        if (!effect.isMorph()) {
            mplew.writeShort(20); // skill duration
        }
        mplew.writeShort(0);
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket giveDebuff(long mask, List<Pair<MapleDisease, Integer>> statups, MobSkill skill) {
        // [1D 00] [00 00 00 00 00 00 00 00] [00 00 02 00 00 00 00 00] [00 00] [7B 00] [04 00] [B8 0B 00 00] [00 00] [84 03] [01]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        mplew.writeLong(0);
        mplew.writeLong(mask);
        for (Pair<MapleDisease, Integer> statup : statups) {
            mplew.writeShort(statup.getRight().shortValue());
            mplew.writeShort(skill.getSkillId());
            mplew.writeShort(skill.getSkillLevel());
            mplew.writeInt(skill.getDuration());
        }
        mplew.writeShort(0); // ??? wk charges have 600 here o.o
        mplew.writeShort(900); //Delay
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket giveForeignDebuff(int cid, long mask, MobSkill skill) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
        mplew.writeInt(cid);
        mplew.writeLong(0);
        mplew.writeLong(mask);
        mplew.writeShort(skill.getSkillId());
        mplew.writeShort(skill.getSkillLevel());
        mplew.writeShort(0);
        mplew.writeShort(900);
        return mplew.getPacket();
    }

    public static MaplePacket cancelForeignDebuff(int cid, long mask) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
        mplew.writeInt(cid);
        mplew.writeLong(0);
        mplew.writeLong(mask);

        return mplew.getPacket();
    }

    public static MaplePacket cancelForeignBuff(int cid, List<MapleBuffStat> statups) {
        // 8A 00 24 46 32 00 80 04 00 00 00 00 00 00 F4 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        boolean elr = false;

        mplew.writeShort(SendPacketOpcode.CANCEL_FOREIGN_BUFF.getValue());
        mplew.writeInt(cid);
        long mask = getLongMaskFromList(statups);
        for (MapleBuffStat mbs : statups) {
            if (mbs.equalsTo(MapleBuffStat.ELEMENTAL_RESET) && mask == MapleBuffStat.ELEMENTAL_RESET.getValue() || mbs.isFirst()) {
                elr = true;
                break;
            }
        }
        if (mask == MapleBuffStat.BATTLESHIP.getValue() || elr) {
            mplew.writeLong(mask);
            mplew.writeLong(0);
        } else {
            mplew.writeLong(0);
            mplew.writeLong(mask);
        }

        return mplew.getPacket();
    }

    public static MaplePacket cancelBuff(List<MapleBuffStat> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        boolean elr = false;

        mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
        long mask = getLongMaskFromList(statups);
        for (MapleBuffStat mbs : statups) {
            if (mbs.isFirst()) {
                elr = true;
                break;
            }
        }
        if (mask == MapleBuffStat.BATTLESHIP.getValue() || elr) {
            mplew.writeLong(mask);
            mplew.writeLong(0);
        } else {
            mplew.writeLong(0);
            mplew.writeLong(mask);
        }
        mplew.write(3);

        return mplew.getPacket();
    }

    public static MaplePacket cancelDebuff(long mask) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CANCEL_BUFF.getValue());
        mplew.writeLong(0);
        mplew.writeLong(mask);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket getPlayerShopChat(MapleCharacter c, String chat, byte slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("06 08"));
        mplew.write(slot);
        mplew.writeMapleAsciiString(c.getName() + " : " + chat);

        return mplew.getPacket();
    }

    public static MaplePacket getPlayerShopChat(MapleCharacter c, String chat, boolean owner) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("06 08"));
        mplew.write(owner ? 0 : 1);
        mplew.writeMapleAsciiString(c.getName() + " : " + chat);

        return mplew.getPacket();
    }

    public static MaplePacket getPlayerShopNewVisitor(MapleCharacter c, int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(4);
        mplew.write(slot);
        addCharLook(mplew, c, false);
        mplew.writeMapleAsciiString(c.getName());
        return mplew.getPacket();
    }

    public static MaplePacket getPlayerShopRemoveVisitor(int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(10);
        mplew.write(slot);
        return mplew.getPacket();
    }

    public static MaplePacket getTradePartnerAdd(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("04 01"));
        addCharLook(mplew, c, false);
        mplew.writeMapleAsciiString(c.getName());

        return mplew.getPacket();
    }

    public static MaplePacket getTradeInvite(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("02 03"));
        mplew.writeMapleAsciiString(c.getName());
        mplew.write(HexTool.getByteArrayFromHexString("B7 50 00 00"));

        return mplew.getPacket();
    }

    public static MaplePacket getTradeMesoSet(byte number, int meso) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(16);
        mplew.write(number);
        mplew.writeInt(meso);

        return mplew.getPacket();
    }

    public static MaplePacket getTradeItemAdd(byte number, IItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(15);
        mplew.write(number);
        mplew.write(item.getPosition());
        addItemInfo(mplew, item, true);

        return mplew.getPacket();
    }

    public static MaplePacket getPlayerShopItemUpdate(MaplePlayerShop shop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(23);
        mplew.write(shop.getItems().size());
        for (MaplePlayerShopItem item : shop.getItems()) {
            mplew.writeShort(item.getBundles());
            mplew.writeShort(item.getItem().getQuantity());
            mplew.writeInt(item.getPrice());
            addItemInfo(mplew, item.getItem(), true);
        }

        return mplew.getPacket();
    }

    /**
     * @param c
     * @param shop
     * @param owner
     * @return
     */
    public static MaplePacket getPlayerShop(MapleClient c, MaplePlayerShop shop, boolean owner) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("05 04 04"));
        mplew.write(owner ? 0 : 1);
        mplew.write(0);
        addCharLook(mplew, shop.getMCOwner(), false);
        mplew.writeMapleAsciiString(shop.getMCOwner().getName());
        mplew.write(1);
        addCharLook(mplew, shop.getMCOwner(), false);
        mplew.writeMapleAsciiString(shop.getMCOwner().getName());
        mplew.write(-1);
        mplew.writeMapleAsciiString(shop.getDescription());
        List<MaplePlayerShopItem> items = shop.getItems();
        mplew.write(16);
        mplew.write(items.size());
        for (MaplePlayerShopItem item : items) {
            mplew.writeShort(item.getBundles());
            mplew.writeShort(item.getItem().getQuantity());
            mplew.writeInt(item.getPrice());
            addItemInfo(mplew, item.getItem(), true);
        }
        return mplew.getPacket();
    }

    public static MaplePacket getTradeStart(MapleClient c, MapleTrade trade, byte number) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("05 03 02"));
        mplew.write(number);
        if (number == 1) {
            mplew.write(0);
            addCharLook(mplew, trade.getPartner().getChr(), false);
            mplew.writeMapleAsciiString(trade.getPartner().getChr().getName());
        }
        mplew.write(number);
		/*if (number == 1) {
		mplew.write(0);
		mplew.writeInt(c.getPlayer().getId());
		}*/
        addCharLook(mplew, c.getPlayer(), false);
        mplew.writeMapleAsciiString(c.getPlayer().getName());
        mplew.write(-1);

        return mplew.getPacket();
    }

    public static MaplePacket getTradeConfirmation() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(17);

        return mplew.getPacket();
    }

    public static MaplePacket getTradeCompletion(byte number) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(10);
        mplew.write(number);
        mplew.write(7);

        return mplew.getPacket();
    }

    public static MaplePacket getTradeCancel(byte number) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(10);
        mplew.write(number);
        mplew.write(2);

        return mplew.getPacket();
    }

    public static MaplePacket addCharBox(MapleCharacter c, int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
        mplew.writeInt(c.getId());
        addAnnounceBox(mplew, c.getPlayerShop());
        return mplew.getPacket();
    }

    public static MaplePacket removeCharBox(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
        mplew.writeInt(c.getId());
        mplew.write(0);
        return mplew.getPacket();
    }

    public static MaplePacket getNPCTalk(int npc, byte msgType, byte msgTypeEx, String talk, String endBytes) {
        return getNPCTalk(npc, msgType, msgTypeEx, 0, talk, endBytes);
    }

    public static MaplePacket getNPCTalk(int npc, byte msgType, byte msgTypeEx, int npcEx, String talk, String endBytes) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
        mplew.write(4);
        mplew.writeInt(npc);
        mplew.write(msgType);
        mplew.write(msgTypeEx);
        if (msgTypeEx == 4) {
            mplew.writeInt(npcEx);
        }
        mplew.writeMapleAsciiString(talk);
        mplew.write(HexTool.getByteArrayFromHexString(endBytes));

        return mplew.getPacket();
    }

    public static MaplePacket getNPCTalkStyle(int npc, byte TypeExt, String talk, int[] styles) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
        mplew.write(4);
        mplew.writeInt(npc);
        mplew.write(7);
        mplew.write(TypeExt);
        mplew.writeMapleAsciiString(talk);
        mplew.write(styles.length);
        for (int style : styles) {
            mplew.writeInt(style);
        }

        return mplew.getPacket();
    }

    public static MaplePacket getNPCTalkNum(int npc, byte TypeExt, String talk, int def, int min, int max) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
        mplew.write(4);
        mplew.writeInt(npc);
        mplew.write(3);
        mplew.write(TypeExt);
        mplew.writeMapleAsciiString(talk);
        mplew.writeInt(def);
        mplew.writeInt(min);
        mplew.writeInt(max);

        return mplew.getPacket();
    }

    public static MaplePacket getNPCAskQuestion(int npc, String text) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
        mplew.write(4);
        mplew.writeInt(npc);
        mplew.write(15);
        mplew.write(0);
        mplew.writeInt(1);
        mplew.writeInt(0);
        mplew.writeMapleAsciiString(text);

        return mplew.getPacket();
    }

    public static MaplePacket getNPCTalkText(int npc, byte TypeExt, String talk, String defaultText, short min, short max) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.NPC_TALK.getValue());
        mplew.write(4);
        mplew.writeInt(npc);
        mplew.write(2);
        mplew.write(TypeExt);
        mplew.writeMapleAsciiString(talk);
        mplew.writeMapleAsciiString(defaultText);
        mplew.writeShort(min);
        mplew.writeShort(max);

        return mplew.getPacket();
    }

    public static MaplePacket showLevelup(int cid) {
        return showForeignEffect(cid, 0);
    }

    public static MaplePacket showJobChange(int cid) {
        return showForeignEffect(cid, 8);
    }

    public static MaplePacket showForeignEffect(int cid, int effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
        mplew.writeInt(cid);
        mplew.write(effect);

        return mplew.getPacket();
    }

    public static MaplePacket showBuffeffect(int cid, int skillid, int effectid, byte level, byte direction) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
        mplew.writeInt(cid); // ?
        mplew.write(effectid);
        mplew.writeInt(skillid);
        mplew.write(level);
        mplew.write(1); // buff level
        if (direction != (byte) 3) {
            mplew.write(direction);
        }

        return mplew.getPacket();
    }

    public static MaplePacket showOwnBuffEffect(int skillid, int effectid, byte level) {
        return showMiscEffects((byte) effectid, skillid, level, (byte) 1, false, 0, "");
    }

    public static MaplePacket showOwnBerserk(byte level, int skilllevel, boolean berserk) {
        return showMiscEffects((byte) 1, 1320006, level, (byte) skilllevel, berserk, 0, "");
    }

    public static MaplePacket showBerserk(int cid, int skilllevel, byte level, boolean berserk) {
        // [99 00] [5D 94 27 00] [01] [46 24 14 00] [14] [01]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
        mplew.writeInt(cid);
        mplew.write(1);
        mplew.writeInt(1320006);
        mplew.write(level);
        mplew.write(skilllevel);
        mplew.write(berserk ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket updateSkill(int skillid, int level, int masterlevel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_SKILLS.getValue());
        mplew.write(1);
        mplew.writeShort(1); // Number of skills to update
        mplew.writeInt(skillid);
        mplew.writeInt(level);
        mplew.writeInt(masterlevel);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(FileTimeUtil.getDefaultTimestamp().getTime()));
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket charGender(int gender) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHAR_GENDER.getValue());
        mplew.write(gender);

        return mplew.getPacket();
    }

    public static MaplePacket getKeymap(Map<Integer, MapleKeyBinding> keybindings) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.KEYMAP.getValue());
        mplew.write(0);

        for (int x = 0; x < 90; x++) {
            MapleKeyBinding binding = keybindings.get(x);
            if (binding != null) {
                mplew.write(binding.getType());
                mplew.writeInt(binding.getAction());
            } else {
                mplew.write(0);
                mplew.writeInt(0);
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket sendAutoHpPot(int itemId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.AUTO_HP_POT.getValue());
        mplew.writeInt(itemId);

        return mplew.getPacket();
    }

    public static MaplePacket sendAutoMpPot(int itemId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.AUTO_MP_POT.getValue());
        mplew.writeInt(itemId);

        return mplew.getPacket();
    }

    public static MaplePacket alertGMStatus(boolean enabled) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALERT_GM_STATUS.getValue());
        mplew.write(enabled ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket getWhisper(String sender, int channel, String text) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(18);
        mplew.writeMapleAsciiString(sender);
        mplew.writeShort(channel - 1);
        mplew.writeMapleAsciiString(text);

        return mplew.getPacket();
    }

    /**
     * @param target name of the target character
     * @param reply  error code: 0x0 = cannot find char, 0x1 = success
     * @return the MaplePacket
     */
    public static MaplePacket getWhisperReply(String target, byte reply) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(10);
        mplew.writeMapleAsciiString(target);
        mplew.write(reply);

        return mplew.getPacket();
    }

    public static MaplePacket getFindReplyWithMap(String target, int mapid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(9);
        mplew.writeMapleAsciiString(target);
        mplew.write(1);
        mplew.writeInt(mapid);
        mplew.write(new byte[8]); // ?? official doesn't send zeros here but whatever

        return mplew.getPacket();
    }

    public static MaplePacket getBuddyFindReplyWithMap(String target, int mapid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(72);
        mplew.writeMapleAsciiString(target);
        mplew.write(1);
        mplew.writeInt(mapid);
        mplew.write(new byte[8]); // ?? official doesn't send zeros here but whatever

        return mplew.getPacket();
    }

    public static MaplePacket getFindReply(String target, int channel) {
        // Received UNKNOWN (1205941596.79689): (25)
        // 54 00 09 07 00 64 61 76 74 73 61 69 01 86 7F 3D 36 D5 02 00 00 22 00
        // 00 00
        // T....davtsai..=6...."...
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(9);
        mplew.writeMapleAsciiString(target);
        mplew.write(3);
        mplew.writeInt(channel - 1);

        return mplew.getPacket();
    }

    public static MaplePacket getBuddyFindReply(String target, int channel) {
        // Received UNKNOWN (1205941596.79689): (25)
        // 54 00 09 07 00 64 61 76 74 73 61 69 01 86 7F 3D 36 D5 02 00 00 22 00
        // 00 00
        // T....davtsai..=6...."...
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(72);
        mplew.writeMapleAsciiString(target);
        mplew.write(3);
        mplew.writeInt(channel - 1);

        return mplew.getPacket();
    }

    public static MaplePacket getInventoryFull() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MODIFY_INVENTORY_ITEM.getValue());
        mplew.write(1);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket getShowInventoryFull() {
        return showStatusInfo((byte) 0, (byte) -1, 0, 0, false, 0, (short) 0, 0);
    }

    public static MaplePacket showItemUnavailable() {
        return showStatusInfo((byte) 0, (byte) -2, 0, 0, false, 0, (short) 0, 0);
    }

    public static MaplePacket getStorage(int npcId, byte slots, Collection<IItem> items, int meso) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
        mplew.write(22);
        mplew.writeInt(npcId);
        mplew.write(slots);
        mplew.writeShort(126);
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.writeInt(meso);
        mplew.writeShort(0);
        mplew.write((byte) items.size());
        for (IItem item : items) {
            addItemInfo(mplew, item, true);
        }
        mplew.writeShort(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket getStorageFull() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
        mplew.write(17);

        return mplew.getPacket();
    }

    public static MaplePacket mesoStorage(byte slots, int meso) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
        mplew.write(19);
        mplew.write(slots);
        mplew.writeShort(2);
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.writeInt(meso);

        return mplew.getPacket();
    }

    public static MaplePacket storeStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
        mplew.write(13);
        mplew.write(slots);
        mplew.writeShort(type.getBitfieldEncoding());
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.write(items.size());
        for (IItem item : items) {
            addItemInfo(mplew, item, true);
        }

        return mplew.getPacket();
    }

    public static MaplePacket takeOutStorage(byte slots, MapleInventoryType type, Collection<IItem> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OPEN_STORAGE.getValue());
        mplew.write(9);
        mplew.write(slots);
        mplew.writeShort(type.getBitfieldEncoding());
        mplew.writeShort(0);
        mplew.writeInt(0);
        mplew.write(items.size());
        for (IItem item : items) {
            addItemInfo(mplew, item, true);
        }

        return mplew.getPacket();
    }

    /**
     * @param oid
     * @param remhppercentage in %
     * @return
     */
    public static MaplePacket showMonsterHP(int oid, int remhppercentage) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_MONSTER_HP.getValue());
        mplew.writeInt(oid);
        mplew.write(remhppercentage);

        return mplew.getPacket();
    }

    public static MaplePacket showBossHP(int oid, int currHP, int maxHP, byte tagColor, byte tagBgColor) {
        //53 00 05 21 B3 81 00 46 F2 5E 01 C0 F3 5E 01 04 01
        //00 81 B3 21 = 8500001 = Pap monster ID
        //01 5E F3 C0 = 23,000,000 = Pap max HP
        //04, 01 - boss bar color/background color as provided in WZ
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
        mplew.write(5);
        mplew.writeInt(oid);
        mplew.writeInt(currHP);
        mplew.writeInt(maxHP);
        mplew.write(tagColor);
        mplew.write(tagBgColor);

        return mplew.getPacket();
    }

    /**
     * status can be: <br>
     * 0: ok, use giveFameResponse<br>
     * 1: the username is incorrectly entered<br>
     * 2: users under level 15 are unable to toggle with fame.<br>
     * 3: can't raise or drop fame anymore today.<br>
     * 4: can't raise or drop fame for this character for this month anymore.<br>
     * 5: received fame, use receiveFame()<br>
     * 6: level of fame neither has been raised nor dropped due to an unexpected
     * error
     *
     * @param status
     * @return
     */
    public static MaplePacket giveFameErrorResponse(int status) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
        mplew.write(status);

        return mplew.getPacket();
    }

    public static MaplePacket receiveFame(int mode, String charnameFrom) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
        mplew.write(5);
        mplew.writeMapleAsciiString(charnameFrom);
        mplew.write(mode);

        return mplew.getPacket();
    }

    public static MaplePacket giveFameResponse(int mode, String charname, int newfame) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.FAME_RESPONSE.getValue());
        mplew.write(0);
        mplew.writeMapleAsciiString(charname);
        mplew.write(mode);
        mplew.writeInt(newfame);

        return mplew.getPacket();
    }

    public static MaplePacket partyCreated(int partyId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
        mplew.write(8);
        mplew.writeInt(partyId);
        mplew.writeInt(999999999);
        mplew.writeInt(999999999);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket partyInvite(MapleCharacter from) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
        mplew.write(4);
        mplew.writeInt(from.getParty().getId());
        mplew.writeMapleAsciiString(from.getName());
        mplew.write(0);

        return mplew.getPacket();
    }

    /**
     * 10: A beginner can't create a party.
     * 1/11/14/19: Your request for a party didn't work due to an unexpected error.
     * 13: You have yet to join a party.
     * 16: Already have joined a party.
     * 17: The party you're trying to join is already in full capacity.
     * 19: Unable to find the requested character in this channel.
     *
     * @param message
     * @return
     */
    public static MaplePacket partyStatusMessage(int message) {
        // 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
        mplew.write(message);
        return mplew.getPacket();
    }

    /**
     * 23: 'Char' have denied request to the party.
     *
     * @param message
     * @param charname
     * @return
     */
    public static MaplePacket partyStatusMessage(int message, String charname) {
        // 32 00 08 DA 14 00 00 FF C9 9A 3B FF C9 9A 3B 22 03 6E 67
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
        mplew.write(message);
        mplew.writeMapleAsciiString(charname);

        return mplew.getPacket();
    }

    private static void addPartyStatus(int forchannel, MapleParty party, LittleEndianWriter lew, boolean leaving) {
        List<MaplePartyCharacter> partymembers = new ArrayList<>(party.getMembers());
        while (partymembers.size() < 6) {
            partymembers.add(new MaplePartyCharacter());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeInt(partychar.getId());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeAsciiString(StringUtil.getRightPaddedStr(partychar.getName(), '\0', 13));
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeInt(partychar.getJobId());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            lew.writeInt(partychar.getLevel());
        }
        for (MaplePartyCharacter partychar : partymembers) {
            if (partychar.isOnline()) {
                lew.writeInt(partychar.getChannel() - 1);
            } else {
                lew.writeInt(-2);
            }
        }
        lew.writeInt(party.getLeader().getId());
        for (MaplePartyCharacter partychar : partymembers) {
            if (partychar.getChannel() == forchannel) {
                lew.writeInt(partychar.getMapId());
            } else {
                lew.writeInt(999999999);
            }
        }
        for (MaplePartyCharacter partychar : partymembers) {
            if (partychar.getChannel() == forchannel && !leaving) {
                lew.writeInt(partychar.getDoorTown());
                lew.writeInt(partychar.getDoorTarget());
                lew.writeInt(partychar.getDoorPosition().x);
                lew.writeInt(partychar.getDoorPosition().y);
            } else {
                lew.writeInt(999999999);
                lew.writeInt(999999999);
                lew.writeInt(-1);
                lew.writeInt(-1);
            }
        }
    }

    public static MaplePacket updateParty(int forChannel, MapleParty party, PartyOperation op, MaplePartyCharacter target) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
        switch (op) {
            case DISBAND, EXPEL, LEAVE -> {
                mplew.write(12);
                mplew.writeInt(party.getId());
                mplew.writeInt(target.getId());
                if (op == PartyOperation.DISBAND) {
                    mplew.write(0);
                    mplew.writeInt(party.getId());
                } else {
                    mplew.write(1);
                    if (op == PartyOperation.EXPEL) {
                        mplew.write(1);
                    } else {
                        mplew.write(0);
                    }
                    mplew.writeMapleAsciiString(target.getName());
                    addPartyStatus(forChannel, party, mplew, false);
                    // addLeavePartyTail(mplew);
                }
            }
            case JOIN -> {
                mplew.write(15);
                mplew.writeInt(party.getId());
                mplew.writeMapleAsciiString(target.getName());
                addPartyStatus(forChannel, party, mplew, false);
            }
            case SILENT_UPDATE, LOG_ONOFF -> {
                mplew.write(7);
                mplew.writeInt(party.getId());
                addPartyStatus(forChannel, party, mplew, false);
            }
            case CHANGE_LEADER -> {
                mplew.write(27);
                mplew.writeInt(target.getId());
                mplew.write(0);
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket partyPortal(int townId, int targetId, Point position) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PARTY_OPERATION.getValue());
        mplew.write(37);
        mplew.write(0);
        mplew.writeInt(townId);
        mplew.writeInt(targetId);
        mplew.writeShort(position.x);
        mplew.writeShort(position.y);

        return mplew.getPacket();
    }

    public static MaplePacket updatePartyMemberHP(int cid, int curhp, int maxhp) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_PARTYMEMBER_HP.getValue());
        mplew.writeInt(cid);
        mplew.writeInt(curhp);
        mplew.writeInt(maxhp);

        return mplew.getPacket();
    }

    /**
     * mode: 0 buddychat; 1 partychat; 2 guildchat; 3 Alliance chat
     *
     * @param name
     * @param chattext
     * @param mode
     * @return
     */
    public static MaplePacket multiChat(String name, String chattext, int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MULTICHAT.getValue());
        mplew.write(mode);
        mplew.writeMapleAsciiString(name);
        mplew.writeMapleAsciiString(chattext);

        return mplew.getPacket();
    }

    public static MaplePacket applyMonsterStatus(int oid, Map<MonsterStatus, Integer> stats, int skill, boolean monsterSkill, int delay) {
        return applyMonsterStatus(oid, stats, skill, monsterSkill, delay, null);
    }

    public static MaplePacket applyMonsterStatusTest(int oid, int mask, int delay, MobSkill mobskill, int value) {
        // 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01
        // 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 01 00 79 00 01 00 B4 78 00 00 00 00 84 03
        // B4 00 A8 90 03 00 00 00 04 00 01 00 8C 00 03 00 14 00 4C 04 02
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
        mplew.writeInt(oid);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(mask);
        mplew.writeShort(1);
        mplew.writeShort(mobskill.getSkillId());
        mplew.writeShort(mobskill.getSkillLevel());
        mplew.writeShort(0); // as this looks similar to giveBuff this might actually be the buffTime but it's not displayed anywhere
        mplew.writeShort(delay); // delay in ms
        mplew.writeInt(1); // ?

        return mplew.getPacket();
    }

    public static MaplePacket applyMonsterStatusTest2(int oid, int mask, int skill, int value) {
        // 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01
        // 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 01 00 79 00 01 00 B4 78 00 00 00 00 84 03
        // B4 00 A8 90 03 00 00 00 04 00 01 00 8C 00 03 00 14 00 4C 04 02
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
        mplew.writeInt(oid);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(mask);
        mplew.writeShort(value);
        mplew.writeInt(skill);
        mplew.writeShort(0); // as this looks similar to giveBuff this might actually be the buffTime but it's not displayed anywhere
        mplew.writeShort(0); // delay in ms
        mplew.writeInt(1); // ?

        return mplew.getPacket();
    }

    public static MaplePacket applyMonsterStatus(int oid, Map<MonsterStatus, Integer> stats, int skill, boolean monsterSkill, int delay, MobSkill mobskill) {
        // 9B 00 67 40 6F 00 80 00 00 00 01 00 FD FE 30 00 08 00 64 00 01
        // 1D 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 10 00 01 00 79 00 01 00 B4 78 00 00 00 00 84 03
        // B4 00 A8 90 03 00 00 00 04 00 01 00 8C 00 03 00 14 00 4C 04 02
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.APPLY_MONSTER_STATUS.getValue());
        mplew.writeInt(oid);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        int mask = 0;
        for (MonsterStatus stat : stats.keySet()) {
            mask |= stat.getValue();
        }
        mplew.writeInt(mask);
        for (Integer val : stats.values()) {
            mplew.writeShort(val);
            if (monsterSkill) {
                mplew.writeShort(mobskill.getSkillId());
                mplew.writeShort(mobskill.getSkillLevel());
            } else {
                mplew.writeInt(skill);
            }
            mplew.writeShort(-1); // as this looks similar to giveBuff this
            // might actually be the buffTime but it's not displayed anywhere

        }
        mplew.writeInt(delay); // delay in ms
        //mplew.writeInt(0); // ?
        mplew.write(stats.size()); // size

        return mplew.getPacket();
    }

    public static MaplePacket cancelMonsterStatus(int oid, Map<MonsterStatus, Integer> stats) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CANCEL_MONSTER_STATUS.getValue());
        mplew.writeInt(oid);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeInt(0);
        int mask = 0;
        for (MonsterStatus stat : stats.keySet()) {
            mask |= stat.getValue();
        }
        mplew.writeInt(mask);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket getClock(int time) { // time in seconds
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
        mplew.write(2); // clock type. if you send 3 here you have to send another byte (which does not matter at all) before the timestamp
        mplew.writeInt(time);

        return mplew.getPacket();
    }

    public static MaplePacket getClockTime(int hour, int min, int sec) { // Current Time
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CLOCK.getValue());
        mplew.write(1); // Clock-Type
        mplew.write(hour);
        mplew.write(min);
        mplew.write(sec);
        return mplew.getPacket();
    }

    public static MaplePacket spawnMist(int oid, int ownerCid, int skill, int level, MapleMist mist) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        Rectangle position = mist.getBox();
        mplew.writeShort(SendPacketOpcode.SPAWN_MIST.getValue());
        mplew.writeInt(oid);
        mplew.writeInt(mist.isMobMist() ? 0 : mist.isPoisonMist() ? 1 : 2);
        mplew.writeInt(ownerCid);
        mplew.writeInt(skill);
        mplew.write(level);
        mplew.writeShort(mist.getSkillDelay());
        mplew.writeInt(position.x);
        mplew.writeInt(position.y);
        mplew.writeInt(position.x + position.width);
        mplew.writeInt(position.y + position.height);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket removeMist(int oid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_MIST.getValue());
        mplew.writeInt(oid);

        return mplew.getPacket();
    }

    public static MaplePacket damageSummon(int cid, int summonSkillId, int damage, int unkByte, int monsterIdFrom) {
        // 77 00 29 1D 02 00 FA FE 30 00 00 10 00 00 00 BF 70 8F 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DAMAGE_SUMMON.getValue());
        mplew.writeInt(cid);
        mplew.writeInt(summonSkillId);
        mplew.write(unkByte);
        mplew.writeInt(damage);
        mplew.writeInt(monsterIdFrom);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket damageMonster(int oid, int damage) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.DAMAGE_MONSTER.getValue());
        mplew.writeInt(oid);
        mplew.write(0);
        mplew.writeInt(damage);
        mplew.write(0);
        mplew.write(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket healMonster(int oid, int heal) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_HEAL.getValue());
        mplew.writeInt(oid);
        mplew.write(1);
        mplew.writeInt(-heal);

        return mplew.getPacket();
    }

    public static MaplePacket monsterSkillEffect(int oid, int skillId, int skillLevel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_EFFECT.getValue());
        mplew.writeInt(oid);
        mplew.writeShort(skillId);
        mplew.writeInt(skillLevel);

        return mplew.getPacket();
    }

    public static MaplePacket updateBuddylist(Collection<BuddylistEntry> buddylist) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
        mplew.write(7);
        mplew.write(buddylist.size());
        for (BuddylistEntry buddy : buddylist) {
            if (buddy.isVisible()) {
                mplew.writeInt(buddy.getCharacterId()); // cid
                mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getName(), '\0', 13));
                mplew.write(0);
                int bChan = buddy.getChannel();
                if (bChan == -1) {
                    mplew.writeInt(-1);
                } else {
                    mplew.writeInt(buddy.getChannel() - 1);
                }
                mplew.writeAsciiString(StringUtil.getRightPaddedStr(buddy.getGroup(), '\0', 16));
                mplew.write(0);
            }
        }
        for (int x = 0; x < buddylist.size(); x++) {
            mplew.writeInt(0);
        }

        return mplew.getPacket();
    }

    public static MaplePacket buddylistMessage(byte message) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
        mplew.write(message);

        return mplew.getPacket();
    }

    public static MaplePacket requestBuddylistAdd(int cidFrom, String nameFrom) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
        mplew.write(9);
        mplew.writeInt(cidFrom);
        mplew.writeMapleAsciiString(nameFrom);
        mplew.writeInt(cidFrom);
        mplew.writeAsciiString(StringUtil.getRightPaddedStr(nameFrom, '\0', 13));
        mplew.write(1);
        mplew.writeInt(0);
        mplew.writeAsciiString("Default Group");
        mplew.write(0);
        mplew.writeInt(26478);

        return mplew.getPacket();
    }

    public static MaplePacket updateBuddyChannel(int characterid, int channel) {
        // 2B 00 14 30 C0 23 00 00 11 00 00 00
        // 2B 00 14 30 C0 23 00 00 0D 00 00 00
        // 2B 00 14 30 75 00 00 00 11 00 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
        mplew.write(20);
        mplew.writeInt(characterid);
        mplew.write(0);
        mplew.writeInt(channel);

        return mplew.getPacket();
    }

    public static MaplePacket updateBuddyCapacity(int capacity) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BUDDYLIST.getValue());
        mplew.write(21);
        mplew.write(capacity);

        return mplew.getPacket();
    }

    public static MaplePacket itemEffect(int characterid, int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_ITEM_EFFECT.getValue());

        mplew.writeInt(characterid);
        mplew.writeInt(itemid);

        return mplew.getPacket();
    }

    public static MaplePacket showChair(int characterid, int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_CHAIR.getValue());

        mplew.writeInt(characterid);
        mplew.writeInt(itemid);

        return mplew.getPacket();
    }

    public static MaplePacket cancelChair() {
        return cancelChair(-1);
    }

    public static MaplePacket cancelChair(int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CANCEL_CHAIR.getValue());

        if (id == -1) {
            mplew.write(0);
        } else {
            mplew.write(1);
            mplew.writeShort(id);
        }

        return mplew.getPacket();
    }

    // is there a way to spawn reactors non-animated?
    public static MaplePacket spawnReactor(MapleReactor reactor) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        Point pos = reactor.getPosition();

        mplew.writeShort(SendPacketOpcode.REACTOR_SPAWN.getValue());
        mplew.writeInt(reactor.getObjectId());
        mplew.writeInt(reactor.getId());
        mplew.write(reactor.getState());
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);
        mplew.write(0);
        mplew.writeMapleAsciiString("");

        return mplew.getPacket();
    }

    public static MaplePacket triggerReactor(MapleReactor reactor, int stance) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        Point pos = reactor.getPosition();

        mplew.writeShort(SendPacketOpcode.REACTOR_HIT.getValue());
        mplew.writeInt(reactor.getObjectId());
        mplew.write(reactor.getState());
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);
        mplew.writeShort(stance);
        mplew.write(0);
        mplew.write(5); // frame delay, set to 5 since there doesn't appear to be a fixed formula for it

        return mplew.getPacket();
    }

    public static MaplePacket destroyReactor(MapleReactor reactor) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        Point pos = reactor.getPosition();

        mplew.writeShort(SendPacketOpcode.REACTOR_DESTROY.getValue());
        mplew.writeInt(reactor.getObjectId());
        mplew.write(reactor.getState());
        mplew.writeShort(pos.x);
        mplew.writeShort(pos.y);

        return mplew.getPacket();
    }

    public static MaplePacket musicChange(String song) {
        return environmentChange(song, 6);
    }

    public static MaplePacket showEffect(String effect) {
        return environmentChange(effect, 3);
    }

    public static MaplePacket playSound(String sound) {
        return environmentChange(sound, 4);
    }

    public static MaplePacket environmentChange(String env, int mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
        mplew.write(mode);
        mplew.writeMapleAsciiString(env);

        return mplew.getPacket();
    }

    public static MaplePacket startMapEffect(String msg, int itemid, boolean active) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
        mplew.write(active ? 0 : 1);

        mplew.writeInt(itemid);
        if (active) {
            mplew.writeMapleAsciiString(msg);
        }

        return mplew.getPacket();
    }

    public static MaplePacket removeMapEffect() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MAP_EFFECT.getValue());
        mplew.write(0);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket showGuildInfo(MapleCharacter c) {
        //whatever functions calling this better make sure
        //that the character actually HAS a guild
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(26); //signature for showing guild info
        if (c == null) { //show empty guild (used for leaving, expelled)
            mplew.write(0);
            return mplew.getPacket();
        }
        MapleGuildCharacter initiator = c.getMGC();
        MapleGuild g = c.getClient().getChannelServer().getGuild(initiator);
        if (g == null || g.getMGC(c.getId()) == null) { //failed to read from DB - don't show a guild
            mplew.write(0);
            log.warn(MapleClient.getLogMessage(c, "Couldn't load a guild"));
            return mplew.getPacket();
        } else {
            //MapleGuild holds the absolute correct value of guild rank
            //after it is initiated
            MapleGuildCharacter mgc = g.getMGC(c.getId());
            c.setGuildRank(mgc.getGuildRank());
        }
        mplew.write(1); //bInGuild
        mplew.writeInt(c.getGuildId()); //not entirely sure about this one
        mplew.writeMapleAsciiString(g.getName());
        for (int i = 1; i <= 5; i++) {
            mplew.writeMapleAsciiString(g.getRankTitle(i));
        }
        Collection<MapleGuildCharacter> members = g.getMembers();
        mplew.write(members.size());
        //then it is the size of all the members
        for (MapleGuildCharacter mgc : members) { // and each of their character ids o_O
            mplew.writeInt(mgc.getId());
        }
        for (MapleGuildCharacter mgc : members) {
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13));
            mplew.writeInt(mgc.getJobId());
            mplew.writeInt(mgc.getLevel());
            mplew.writeInt(mgc.getGuildRank());
            mplew.writeInt(mgc.isOnline() ? 1 : 0);
            mplew.writeInt(g.getSignature());
            mplew.writeInt(mgc.getAllianceRank());
        }
        mplew.writeInt(g.getCapacity());
        mplew.writeShort(g.getLogoBG());
        mplew.write(g.getLogoBGColor());
        mplew.writeShort(g.getLogo());
        mplew.write(g.getLogoColor());
        mplew.writeMapleAsciiString(g.getNotice());
        mplew.writeInt(g.getGP());
        mplew.writeInt(g.getAllianceId());

        return mplew.getPacket();
    }

    private static void getGuildInfo(MaplePacketLittleEndianWriter mplew, MapleGuild guild) {
		/*3F 00 0F 04 00 00 00 0A 00 47 75 69 6C 64 55 6E 69 6F 6E 06 00 4D 61 73 74 65 72 09 00 4A 72 2E 4D 61 73 74 65 72 06 00 4D 65 6D 62 65 72 06 00 4D 65 6D 62 65 72 06 00 4D 65 6D 62 65 72 02 19 00 00 00 6F 00 00 00 02 00 00 00 00 00 19 00 00 00 0E 00 4C 61 73 74 53 74 6F 72 79 53 74 61 66 66 0E 00 4C 61 73 74 53 74 6F 72 79 4F 77 6E 65 72 09 00 44 65 76 65 6C 6F 70 65 72 0A 00 47 61 6D 65 4D 61 73 74 65 72 08 00 47 72 61 70 68 69 63 73 06 00 49 6E 74 65 72 6E 04 84 77 00 00 03 77 00 00 A8 76 00 00 2A 7C 00 00 69 52 75 6E 53 68 69 74 00 00 00 00 00 8E 03 00 00 A9 00 00 00 01 00 00 00 01 00 00 00 C0 1D 4D 7D 01 00 00 00 3C 33 43 72 79 73 74 61 6C 3C 33 00 00 38 01 00 00 C8 00 00 00 03 00 00 00 00 00 00 00 C0 1D 4D 7D 05 00 00 00 52 65 6E 00 00 00 00 00 00 00 00 00 00 8E 03 00 00 FA 00 00 00 03 00 00 00 01 00 00 00 C0 1D 4D 7D 05 00 00 00 52 6F 66 6C 73 00 00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 05 00 00 00 00 00 00 00 C0 1D 4D 7D 05 00 00 00 0A 00 00 00 E8 03 10 29 23 0A 61 00 49 20 64 6F 6E 27 74 20 63 61 72 65 20 77 68 61 74 20 73 6F 20 65 76 65 72 20 64 6F 6E 27 74 20 68 61 6E 64 20 6F 75 74 20 69 74 65 6D 73 2C 20 64 6F 6E 27 74 20 73 63 72 6F 6C 6C 20 61 6E 79 74 68 69 6E 67 20 65 69 74 68 65 72 2E 20 49 27 6D 20 67 65 74 74 69 6E 67 20 70 69 73 73 65 64 2E 00 00 00 00 04 00 00 00 6F 00 00 00 0A 00 50 72 6F 6D 65 74 68 65 75 73 06 00 4D 61 73 74 65 72 0A 00 4A 72 2E 20 4D 61 73 74 65 72 06 00 4D 65 6D 62 65 72 06 00 4D 65 6D 62 65 72 06 00 4D 65 6D 62 65 72 01 18 7D 00 00 47 65 6E 65 72 69 63 00 00 00 00 00 00 00 00 00 00 01 00 00 00 01 00 00 00 01 00 00 00 68 A9 70 D9 05 00 00 00 0A 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04 00 00 00
		?........GuildUnion..Master..Jr.Master..Member..Member..Member.....o...............LastStoryStaff..LastStoryOwner..Developer..GameMaster..Graphics..Intern.?w...w..¨v..*|..iRunShit.....?...©...........À.M}....<3Crystal<3..8...È...........À.M}....Ren..........?...ú...........À.M}....Rofls........................À.M}........è..)#.a.I don't care what so ever don't hand out items, don't scroll anything either. I'm getting pissed.........o.....Prometheus..Master..Jr. Master..Member..Member..Member..}..Generic......................h©pÙ........................
		 */
        mplew.writeInt(guild.getId());
        mplew.writeMapleAsciiString(guild.getName());
        for (int i = 1; i <= 5; i++) {
            mplew.writeMapleAsciiString(guild.getRankTitle(i));
        }
        Collection<MapleGuildCharacter> members = guild.getMembers();
        mplew.write(members.size());
        //then it is the size of all the members
        for (MapleGuildCharacter mgc : members) { //and each of their character ids o_O
            mplew.writeInt(mgc.getId());
        }
        for (MapleGuildCharacter mgc : members) {
            mplew.writeAsciiString(StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13));
            mplew.writeInt(mgc.getJobId());
            mplew.writeInt(mgc.getLevel());
            mplew.writeInt(mgc.getGuildRank());
            mplew.writeInt(mgc.isOnline() ? 1 : 0);
            mplew.writeInt(guild.getSignature());
            mplew.writeInt(mgc.getAllianceRank());
        }
        mplew.writeInt(guild.getCapacity());
        mplew.writeShort(guild.getLogoBG());
        mplew.write(guild.getLogoBGColor());
        mplew.writeShort(guild.getLogo());
        mplew.write(guild.getLogoColor());
        mplew.writeMapleAsciiString(guild.getNotice());
        mplew.writeInt(guild.getGP());
        mplew.writeInt(guild.getAllianceId());
    }

    public static MaplePacket guildMemberOnline(int gid, int cid, boolean bOnline) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(61);
        mplew.writeInt(gid);
        mplew.writeInt(cid);
        mplew.write(bOnline ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket guildInvite(int gid, String charName) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(5);
        mplew.writeInt(gid);
        mplew.writeMapleAsciiString(charName);

        return mplew.getPacket();
    }

    /**
     * 'Char' has denied your guild invitation.
     *
     * @param charname
     * @return
     */
    public static MaplePacket denyGuildInvitation(String charname) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(55);
        mplew.writeMapleAsciiString(charname);

        return mplew.getPacket();
    }

    public static MaplePacket genericGuildMessage(byte code) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(code);

        return mplew.getPacket();
    }

    public static MaplePacket newGuildMember(MapleGuildCharacter mgc) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(39);
        mplew.writeInt(mgc.getGuildId());
        mplew.writeInt(mgc.getId());
        mplew.writeAsciiString(StringUtil.getRightPaddedStr(mgc.getName(), '\0', 13));
        mplew.writeInt(mgc.getJobId());
        mplew.writeInt(mgc.getLevel());
        mplew.writeInt(mgc.getGuildRank()); //should be always 5 but whatevs
        mplew.writeInt(mgc.isOnline() ? 1 : 0); //should always be 1 too
        mplew.writeInt(1); //? could be guild signature, but doesn't seem to matter
        mplew.writeInt(3);

        return mplew.getPacket();
    }

    //someone leaving, mode == 0x2c for leaving, 0x2f for expelled
    public static MaplePacket memberLeft(MapleGuildCharacter mgc, boolean bExpelled) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(bExpelled ? 47 : 44);

        mplew.writeInt(mgc.getGuildId());
        mplew.writeInt(mgc.getId());
        mplew.writeMapleAsciiString(mgc.getName());

        return mplew.getPacket();
    }

    //rank change
    public static MaplePacket changeRank(MapleGuildCharacter mgc) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(64);
        mplew.writeInt(mgc.getGuildId());
        mplew.writeInt(mgc.getId());
        mplew.write(mgc.getGuildRank());

        return mplew.getPacket();
    }

    public static MaplePacket guildNotice(int gid, String notice) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(68);

        mplew.writeInt(gid);
        mplew.writeMapleAsciiString(notice);

        return mplew.getPacket();
    }

    public static MaplePacket guildMemberLevelJobUpdate(MapleGuildCharacter mgc) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(60);

        mplew.writeInt(mgc.getGuildId());
        mplew.writeInt(mgc.getId());
        mplew.writeInt(mgc.getLevel());
        mplew.writeInt(mgc.getJobId());

        return mplew.getPacket();
    }

    public static MaplePacket rankTitleChange(int gid, String[] ranks) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(62);
        mplew.writeInt(gid);

        for (int i = 0; i < 5; i++) {
            mplew.writeMapleAsciiString(ranks[i]);
        }

        return mplew.getPacket();
    }

    public static MaplePacket guildDisband(int gid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(50);
        mplew.writeInt(gid);
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket guildEmblemChange(int gid, short bg, byte bgcolor, short logo, byte logocolor) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(66);
        mplew.writeInt(gid);
        mplew.writeShort(bg);
        mplew.write(bgcolor);
        mplew.writeShort(logo);
        mplew.write(logocolor);

        return mplew.getPacket();
    }

    public static MaplePacket guildCapacityChange(int gid, int capacity) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(58);
        mplew.writeInt(gid);
        mplew.write(capacity);

        return mplew.getPacket();
    }

    public static void addThread(MaplePacketLittleEndianWriter mplew, ResultSet rs) throws SQLException {
        mplew.writeInt(rs.getInt("localthreadid"));
        mplew.writeInt(rs.getInt("postercid"));
        mplew.writeMapleAsciiString(rs.getString("name"));
        mplew.writeLong(FileTimeUtil.getFileTimestamp(rs.getLong("timestamp")));
        mplew.writeInt(rs.getInt("icon"));
        mplew.writeInt(rs.getInt("replycount"));
    }

    public static MaplePacket BBSThreadList(ResultSet rs, int start) throws SQLException {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
        mplew.write(6);
        if (!rs.last()) {
            //no result at all
            mplew.write(0);
            mplew.writeInt(0);
            mplew.writeInt(0);
            return mplew.getPacket();
        }
        int threadCount = rs.getRow();
        if (rs.getInt("localthreadid") == 0) { //has a notice
            mplew.write(1);
            addThread(mplew, rs);
            threadCount--; //one thread didn't count (because it's a notice)
        } else {
            mplew.write(0);
        }
        if (!rs.absolute(start + 1)) { //seek to the thread before where we start
            rs.first(); //uh, we're trying to start at a place past possible
            start = 0;
        }
        mplew.writeInt(threadCount);
        mplew.writeInt(Math.min(10, threadCount - start));
        for (int i = 0; i < Math.min(10, threadCount - start); i++) {
            addThread(mplew, rs);
            rs.next();
        }

        return mplew.getPacket();
    }

    public static MaplePacket showThread(int localthreadid, ResultSet threadRS, ResultSet repliesRS) throws SQLException, RuntimeException {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BBS_OPERATION.getValue());
        mplew.write(7);
        mplew.writeInt(localthreadid);
        mplew.writeInt(threadRS.getInt("postercid"));
        mplew.writeLong(FileTimeUtil.getFileTimestamp(threadRS.getLong("timestamp")));
        mplew.writeMapleAsciiString(threadRS.getString("name"));
        mplew.writeMapleAsciiString(threadRS.getString("startpost"));
        mplew.writeInt(threadRS.getInt("icon"));
        if (repliesRS != null) {
            int replyCount = threadRS.getInt("replycount");
            mplew.writeInt(replyCount);
            int i;
            for (i = 0; i < replyCount && repliesRS.next(); i++) {
                mplew.writeInt(repliesRS.getInt("replyid"));
                mplew.writeInt(repliesRS.getInt("postercid"));
                mplew.writeLong(FileTimeUtil.getFileTimestamp(repliesRS.getLong("timestamp")));
                mplew.writeMapleAsciiString(repliesRS.getString("content"));
            }
            if (i != replyCount || repliesRS.next()) {
                //in the unlikely event that we lost count of replyid
                throw new RuntimeException(String.valueOf(threadRS.getInt("threadid")));
                /*
                 we need to fix the database and stop the packet sending or else it'll probably error 38 whoever tries to read it
                 there is ONE case not checked, and that's when the thread has a replycount of 0 and there is one or more replies to the
                 thread in bbs_replies
                 */
            }
        } else {
            mplew.writeInt(0); //0 replies
        }
        return mplew.getPacket();
    }

    public static MaplePacket showGuildRanks(int npcid, ResultSet rs) throws SQLException {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(73);
        mplew.writeInt(npcid);
        if (!rs.last()) { //no guilds o.o
            mplew.writeInt(0);
            return mplew.getPacket();
        }
        mplew.writeInt(rs.getRow()); //number of entries
        rs.beforeFirst();
        while (rs.next()) {
            mplew.writeMapleAsciiString(rs.getString("name"));
            mplew.writeInt(rs.getInt("GP"));
            mplew.writeInt(rs.getInt("logo"));
            mplew.writeInt(rs.getInt("logoColor"));
            mplew.writeInt(rs.getInt("logoBG"));
            mplew.writeInt(rs.getInt("logoBGColor"));
        }

        return mplew.getPacket();
    }

    public static MaplePacket updateGP(int gid, int GP) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GUILD_OPERATION.getValue());
        mplew.write(72);
        mplew.writeInt(gid);
        mplew.writeInt(GP);

        return mplew.getPacket();
    }

    public static MaplePacket skillEffect(MapleCharacter from, int skillId, int level, short stance, int speed) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SKILL_EFFECT.getValue());
        mplew.writeInt(from.getId());
        mplew.writeInt(skillId);
        mplew.write(level);
        mplew.writeShort(stance);
        mplew.write(speed);

        return mplew.getPacket();
    }

    public static MaplePacket skillCancel(MapleCharacter from, int skillId) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CANCEL_SKILL_EFFECT.getValue());
        mplew.writeInt(from.getId());
        mplew.writeInt(skillId);

        return mplew.getPacket();
    }

    public static MaplePacket showMagnet(int mobid, byte success) { // Monster Magnet
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_MAGNET.getValue());
        mplew.writeInt(mobid);
        mplew.write(success);

        return mplew.getPacket();
    }

    /**
     * Sends a player hint.
     *
     * @param hint   The hint it's going to send.
     * @param width  How tall the box is going to be.
     * @param height How long the box is going to be.
     * @return The player hint packet.
     */
    public static MaplePacket sendPlayerHint(String hint, int width, int height) {
        if (width < 1) {
            width = hint.length() * 10;
            if (width < 40) {
                width = 40;
            }
        }
        if (height < 5) {
            height = 5;
        }
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_HINT.getValue());
        mplew.writeMapleAsciiString(hint);
        mplew.writeShort(width);
        mplew.writeShort(height);
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket messengerInvite(String from, int messengerid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
        mplew.write(3);
        mplew.writeMapleAsciiString(from);
        mplew.write(0);
        mplew.writeInt(messengerid);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket addMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
        mplew.write(0);
        mplew.write(position);
        addCharLook(mplew, chr, true);
        mplew.writeMapleAsciiString(from);
        mplew.write(channel);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket removeMessengerPlayer(int position) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
        mplew.write(2);
        mplew.write(position);

        return mplew.getPacket();
    }

    public static MaplePacket updateMessengerPlayer(String from, MapleCharacter chr, int position, int channel) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
        mplew.write(7);
        mplew.write(position);
        addCharLook(mplew, chr, true);
        mplew.writeMapleAsciiString(from);
        mplew.write(channel);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket joinMessenger(int position) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
        mplew.write(1);
        mplew.write(position);

        return mplew.getPacket();
    }

    public static MaplePacket messengerChat(String text) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
        mplew.write(6);
        mplew.writeMapleAsciiString(text);

        return mplew.getPacket();
    }

    public static MaplePacket messengerNote(String text, int mode, int mode2) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MESSENGER.getValue());
        mplew.write(mode);
        mplew.writeMapleAsciiString(text);
        mplew.write(mode2);

        return mplew.getPacket();
    }

    public static MaplePacket warpCS(MapleClient c) {
        return warpCS(c, false);
    }

    public static MaplePacket warpMTS(MapleClient c) {
        return warpCS(c, true);
    }

    public static MaplePacket warpCS(MapleClient c, boolean MTS) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        MapleCharacter chr = c.getPlayer();
        mplew.writeShort(MTS ? SendPacketOpcode.MTS_OPEN.getValue() : SendPacketOpcode.CS_OPEN.getValue());
        mplew.writeLong(-1);
        mplew.write(0);
        addCharStats(mplew, chr);
        mplew.write(chr.getBuddylist().getCapacity());
        if (chr.getBlessingChar().length() != 0) {
            mplew.write(1);
            mplew.writeMapleAsciiString(chr.getBlessingChar());
        } else {
            mplew.write(0);
        }
        addInventoryInfo(mplew, chr);
        addSkillRecord(mplew, chr);
        addQuestRecord(mplew, chr);
        addMiniGameRecordInfo(mplew);
        addCrushRingRecordInfo(mplew, chr);
        addFriendshipRingRecordInfo(mplew, chr);
        addMarriageRingRecordInfo(mplew, chr);
        addTeleportRockRecord(mplew, chr);
        addMonsterBookInfo(mplew, chr);
        mplew.writeShort(0);
        addQuestRecordEx(mplew, chr);
        if (!MTS) {
            mplew.write(1);
        }
        mplew.writeMapleAsciiString(chr.getClient().getAccountName());
        if (MTS) {
            mplew.writeInt(5000);
            mplew.write(HexTool.getByteArrayFromHexString("0A 00 00 00 64 00 00 00 18 00 00 00 A8 00 00 00 B0 ED 4E 3C FD 68 C9 01"));
        } else {
            for (int i = 0; i < 15; i++) {
                mplew.writeLong(0);
            }
            mplew.writeInt(0);
            mplew.writeShort(0);
			/*mplew.writeShort(CashDataProvider.getModifiedData().size());
			for (int sn : CashDataProvider.getModifiedData()) {
				mplew.writeInt(sn);
				mplew.writeInt(0x400);
				mplew.write(CashDataProvider.getItem(sn).isOnSale() ? 1 : 0);
			}*/
            mplew.write(0);
            for (int i = 1; i <= 8; i++) {
                for (int j = 0; j < 2; j++) {
                    mplew.writeInt(i);
                    mplew.writeInt(j);
                    mplew.writeInt(50200004);
                    mplew.writeInt(i);
                    mplew.writeInt(j);
                    mplew.writeInt(50200069);
                    mplew.writeInt(i);
                    mplew.writeInt(j);
                    mplew.writeInt(50200117);
                    mplew.writeInt(i);
                    mplew.writeInt(j);
                    mplew.writeInt(50100008);
                    mplew.writeInt(i);
                    mplew.writeInt(j);
                    mplew.writeInt(50000047);
                }
            }
            mplew.writeInt(0);
            mplew.writeShort(0);
            mplew.write(0);
            mplew.writeInt(75);
        }

        return mplew.getPacket();
    }

    public static void toCashItem(MaplePacketLittleEndianWriter mplew, int sn, int type1, int type2) {
        // E1 9C 98 00 00 06 00 00 00 - Globe Cap
        mplew.writeInt(sn);
        mplew.write(0);
        mplew.write(type1);
        mplew.writeShort(0);
        mplew.write(type2);
    }

    public static void toCashItem(MaplePacketLittleEndianWriter mplew, int sn, int type0, int type1, int type2) {
        mplew.writeInt(sn);
        mplew.write(type0);
        mplew.write(type1);
        mplew.writeShort(0);
        mplew.write(type2);
    }

    public static MaplePacket showNXMapleTokens(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_UPDATE.getValue());
        mplew.writeInt(chr.getCSPoints(1)); // Paypal/PayByCash NX
        mplew.writeInt(chr.getCSPoints(2)); // Maple Points
        mplew.writeInt(chr.getCSPoints(4)); // Game Card NX

        return mplew.getPacket();
    }

    public static MaplePacket showBoughtCSItem(int accountId, CashItemInfo item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(87);
        mplew.writeLong(12687098);
        mplew.writeLong(accountId);
        mplew.writeInt(item.getItemId());
        mplew.writeInt(item.getSN());
        mplew.writeShort(item.getCount());
        mplew.write(0);
        mplew.write(HexTool.getByteArrayFromHexString("00 50 4C 40 00 9C F4 78"));
        mplew.writeInt(0);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getExpiration().getTime()));
        mplew.writeLong(0);

        return mplew.getPacket();
    }

    public static MaplePacket showBoughtCSPackage(int accountId, List<CashItemInfo> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(137);
        mplew.write(items.size());
        for (CashItemInfo item : items) {
            mplew.writeLong(12687098);
            mplew.writeLong(accountId);
            mplew.writeInt(item.getItemId());
            mplew.writeInt(item.getSN());
            mplew.writeShort(item.getCount());
            mplew.write(0);
            mplew.write(HexTool.getByteArrayFromHexString("00 50 4C 40 00 9C F4 78"));
            mplew.writeInt(0);
            mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getExpiration().getTime()));
            mplew.writeLong(0);
        }
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket showBoughtCSQuestItem(short position, int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(141);
        mplew.writeInt(1);
        mplew.writeShort(1);
        mplew.writeShort(position);
        mplew.writeInt(itemid);

        return mplew.getPacket();
    }

    public static MaplePacket showCouponRedeemedItem(int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.writeShort(58);
        mplew.writeInt(0);
        mplew.writeInt(1);
        mplew.writeShort(1);
        mplew.writeShort(26);
        mplew.writeInt(itemid);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket enableCSUse0() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.write(HexTool.getByteArrayFromHexString("12 00 00 00 00 00 00"));

        return mplew.getPacket();
    }

    public static MaplePacket enableCSUse1() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(75);
        mplew.writeShort(0);
        mplew.writeShort(4);
        mplew.writeShort(6);

        return mplew.getPacket();
    }

    public static MaplePacket enableCSUse2() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(77);
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket sendWishList(int[] wishlist) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(79);
        for (int i = 0; i < 10; i++) {
            mplew.writeInt(wishlist[i]);
        }

        return mplew.getPacket();
    }

    public static MaplePacket updateWishList(int[] wishlist) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(85);
        for (int i = 0; i < 10; i++) {
            mplew.writeInt(wishlist[i]);
        }

        return mplew.getPacket();
    }

    public static MaplePacket wrongCouponCode() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(92);
        mplew.write(176);

        return mplew.getPacket();
    }

    public static MaplePacket getFindReplyWithCS(String target) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(9);
        mplew.writeMapleAsciiString(target);
        mplew.write(2);
        mplew.writeInt(-1);

        return mplew.getPacket();
    }

    public static MaplePacket getBuddyFindReplyWithCS(String target) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.WHISPER.getValue());
        mplew.write(72);
        mplew.writeMapleAsciiString(target);
        mplew.write(2);
        mplew.writeInt(-1);

        return mplew.getPacket();
    }

    public static MaplePacket showPet(MapleCharacter chr, MaplePet pet, boolean remove, boolean hunger, boolean replacePet) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPAWN_PET.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(chr.getPetIndex(pet));
        if (remove) {
            mplew.write(0);
            mplew.write(hunger ? 1 : 0);
        } else {
            mplew.write(1);
            mplew.write(replacePet ? 1 : 0);
            mplew.writeInt(pet.getItemId());
            mplew.writeMapleAsciiString(pet.getName());
            mplew.writeLong(pet.getUniqueId());
            mplew.writeShort(pet.getPos().x);
            mplew.writeShort(pet.getPos().y);
            mplew.write(pet.getStance());
            mplew.writeShort(pet.getFh());
            mplew.write(pet.hasLabelRing() ? 1 : 0);
            mplew.write(pet.hasQuoteRing() ? 1 : 0);
        }

        return mplew.getPacket();
    }

    public static MaplePacket movePet(int cid, int pid, int slot, List<LifeMovementFragment> moves) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MOVE_PET.getValue());
        mplew.writeInt(cid);
        mplew.write(slot);
        mplew.writeInt(pid);
        serializeMovementList(mplew, moves);

        return mplew.getPacket();
    }

    public static MaplePacket petChat(int cid, int un, String text, int slot, boolean quoteRing) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PET_CHAT.getValue());
        mplew.writeInt(cid);
        mplew.write(slot);
        mplew.writeShort(un);
        mplew.writeMapleAsciiString(text);
        mplew.write(quoteRing ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket commandResponse(int cid, byte command, int slot, boolean success, boolean food, boolean quoteRing) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PET_COMMAND.getValue());
        mplew.writeInt(cid);
        mplew.write(slot);
        if (!food) {
            mplew.write(0);
        }
        mplew.write(command);
        if (success) {
            mplew.write(1);
        } else {
            mplew.write(0);
        }
        mplew.write(quoteRing ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket showOwnPetLevelUp(byte effect, int index) {
        return showMiscEffects((byte) 4, 0, effect, (byte) index, false, 0, "");
    }

    public static MaplePacket showPetLevelUp(MapleCharacter chr, int index) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_FOREIGN_EFFECT.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(4);
        mplew.write(0);
        mplew.write(index);

        return mplew.getPacket();
    }

    public static MaplePacket updatePetNameTag(MapleCharacter chr, String newname, int slot, boolean nameTag) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PET_NAMECHANGE.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(slot);
        mplew.writeMapleAsciiString(newname);
        mplew.write(nameTag ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket petStatUpdate(List<Pair<MapleStat, Integer>> stats) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
        mplew.write(0);
        int updateMask = 0;
        for (Pair<MapleStat, Integer> statupdate : stats) {
            updateMask |= statupdate.getLeft().getValue();
        }
        mplew.writeInt(updateMask);
        for (Pair<MapleStat, Integer> petId : stats) {
            mplew.writeLong(petId.getRight());
        }
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket weirdStatUpdate() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_STATS.getValue());
        mplew.write(0);
        mplew.write(8);
        mplew.write(0);
        mplew.write(24);
        mplew.writeLong(0);
        mplew.writeLong(0);
        mplew.writeLong(0);
        mplew.write(0);
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket showEquipEffect() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_EQUIP_EFFECT.getValue());

        return mplew.getPacket();
    }

    public static MaplePacket summonSkill(int cid, int summonSkillId, int newStance) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SUMMON_SKILL.getValue());
        mplew.writeInt(cid);
        mplew.writeInt(summonSkillId);
        mplew.write(newStance);

        return mplew.getPacket();
    }

    public static MaplePacket skillCooldown(int sid, int time) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.COOLDOWN.getValue());
        mplew.writeInt(sid);
        mplew.writeShort(time);

        return mplew.getPacket();
    }

    public static MaplePacket skillBookSuccess(MapleCharacter chr, int skillid, int maxlevel, boolean canuse, boolean success) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.USE_SKILL_BOOK.getValue());
        mplew.writeInt(chr.getId());
        mplew.write(1);
        mplew.writeInt(skillid);
        mplew.writeInt(maxlevel);
        mplew.write(canuse ? 1 : 0);
        mplew.write(success ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket getMacros(SkillMacro[] macros) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SKILL_MACRO.getValue());
        int count = 0;
        for (int i = 0; i < 5; i++) {
            if (macros[i] != null) {
                count++;
            }
        }
        mplew.write(count); // number of macros
        for (int i = 0; i < 5; i++) {
            SkillMacro macro = macros[i];
            if (macro != null) {
                mplew.writeMapleAsciiString(macro.getName());
                mplew.write(macro.getShout());
                mplew.writeInt(macro.getSkill1());
                mplew.writeInt(macro.getSkill2());
                mplew.writeInt(macro.getSkill3());
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket getPlayerNPC(MaplePlayerNPC npc) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_NPC.getValue());
        mplew.write(1);
        mplew.writeInt(npc.getNpcId());
        mplew.writeMapleAsciiString(npc.getPlayerNPCName());
        mplew.write(npc.getGender());
        mplew.write(npc.getSkin());
        mplew.writeInt(npc.getEyes());
        mplew.write(1);
        mplew.writeInt(npc.getHair());
        int[][] equips = npc.getEquips();
        for (int i = 0; i < equips.length; i++) {
            if (equips[i][0] > 0 && equips[i][1] > 0) {
                mplew.write(i);
                if (equips[i][1] <= 0 || i == 11 && equips[i][0] > 0) {
                    mplew.writeInt(equips[i][0]);
                } else {
                    mplew.writeInt(equips[i][1]);
                }
            }
        }
        mplew.write(-1);
        for (int i = 0; i < equips.length; i++) {
            if (equips[i][1] > 0 && equips[i][0] > 0 && i != 11) {
                mplew.write(i);
                mplew.writeInt(equips[i][0]);
            }
        }
        mplew.write(-1);
        mplew.writeInt(equips[11][1]);
        for (int i = 0; i < 3; i++) {
            mplew.writeInt(0);
        }
        System.out.println("PlayerNPC Packet sent : " + mplew.toString());
        return mplew.getPacket();
    }

    public static MaplePacket showNotes(ResultSet notes, int count) throws SQLException {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_NOTES.getValue());
        mplew.write(3);
        mplew.write(count);
        for (int i = 0; i < count; i++) {
            mplew.writeInt(notes.getInt("id"));
            mplew.writeMapleAsciiString(notes.getString("from"));
            mplew.writeMapleAsciiString(notes.getString("message"));
            mplew.writeLong(FileTimeUtil.getFileTimestamp(notes.getLong("timestamp")));
            mplew.write(0);
            notes.next();
        }

        return mplew.getPacket();
    }

    public static void sendUnkwnNote(String to, String msg, String from) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("INSERT INTO notes (`to`, `from`, `message`, `timestamp`) VALUES (?, ?, ?, ?)");
        ps.setString(1, to);
        ps.setString(2, from);
        ps.setString(3, msg);
        ps.setLong(4, System.currentTimeMillis());
        ps.executeUpdate();
        ps.close();
    }

    public static MaplePacket updateAriantPQRanking(String name, int score, boolean empty) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ARIANT_PQ_START.getValue());
        //E9 00 pid
        //01 unknown
        //09 00 53 69 6E 50 61 74 6A 65 68 maple ascii string name
        //00 00 00 00 score
        mplew.write(empty ? 0 : 1);
        if (!empty) {
            mplew.writeMapleAsciiString(name);
            mplew.writeInt(score);
        }

        return mplew.getPacket();
    }

    public static MaplePacket catchMonster(int mobid, int itemid, byte success) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CATCH_MONSTER.getValue());
        //BF 00
        //38 37 2B 00 mob id
        //32 A3 22 00 item id
        //00 success??
        mplew.writeInt(mobid);
        mplew.writeInt(itemid);
        mplew.write(success);

        return mplew.getPacket();
    }

    public static MaplePacket showAriantScoreBoard() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ARIANT_SCOREBOARD.getValue());

        return mplew.getPacket();
    }

    public static MaplePacket showAllCharacterStatus(byte status) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALL_CHARLIST.getValue());
        mplew.write(status);

        return mplew.getPacket();
    }

    public static MaplePacket showAllCharacter(int worlds, int chars) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.ALL_CHARLIST.getValue());
        mplew.write(1);
        mplew.writeInt(worlds);
        mplew.writeInt(chars);
        return mplew.getPacket();
    }

    public static MaplePacket showAllCharacterInfo(int worldid, List<MapleCharacter> chars) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.ALL_CHARLIST.getValue());
        mplew.write(0);
        mplew.write(worldid);
        mplew.write(chars.size());
        for (MapleCharacter chr : chars) {
            addCharEntry(mplew, chr, true);
        }
        return mplew.getPacket();
    }

    public static MaplePacket useChalkboard(MapleCharacter chr, boolean close) {
        // [7B 00] [65 48 55 00] [01] [09 00 61 73 64 66 67 68 6A 6B 6C]
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CHALKBOARD.getValue());
        mplew.writeInt(chr.getId());
        if (close) {
            mplew.write(0);
        } else {
            mplew.write(1);
            mplew.writeMapleAsciiString(chr.getChalkboard());
        }

        return mplew.getPacket();
    }

    public static MaplePacket removeItemFromDuey(boolean remove, int Package) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DUEY.getValue());
        mplew.write(23);
        mplew.writeInt(Package);
        mplew.write(remove ? 3 : 4);
        return mplew.getPacket();
    }

    public static MaplePacket sendDueyMessage(byte operation) {
        return sendDuey(operation, null);
    }

    public static MaplePacket sendDuey(byte operation, List<MapleDueyActions> packages) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DUEY.getValue());
        mplew.write(operation);
        if (operation == 8) {
            mplew.write(0);
            mplew.write(packages.size());
            for (MapleDueyActions dp : packages) {
                mplew.writeInt(dp.getPackageId());
                mplew.writeAsciiString(dp.getSender());
                for (int i = dp.getSender().length(); i < 13; i++) {
                    mplew.write(0);
                }
                mplew.writeInt(dp.getMesos());
                mplew.writeLong(FileTimeUtil.getFileTimestamp(dp.sentTimeInMilliseconds()));
                for (int i = 0; i < 52; i++) { //message is supposed to be here...
                    mplew.writeInt(0);
                }
                mplew.write(0);
                if (dp.getItem() != null) {
                    mplew.write(1);
                    addItemInfo(mplew, dp.getItem(), true);
                } else {
                    mplew.write(0);
                }
            }
            mplew.write(0);
        }
        return mplew.getPacket();
    }

    public static MaplePacket sendMTS(List<MTSItemInfo> items, int tab, int type, int page, int pages) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(21); //operation
        mplew.writeInt(pages * 16); // Number of items. Used in page size calculation
        mplew.writeInt(items.size()); // number of items client reads
        mplew.writeInt(tab);
        mplew.writeInt(type);
        mplew.writeInt(page);
        mplew.write(1);
        mplew.write(1);

        for (MTSItemInfo item : items) {
            addItemInfo(mplew, item.getItem(), true);
            mplew.writeInt(item.getID()); //id
            mplew.writeInt(item.getTaxes()); //this + below = price
            mplew.writeInt(item.getPrice()); //price
            mplew.writeInt(0);
            mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getEndingDate().getTime()));
            mplew.writeMapleAsciiString(item.getSeller()); //account name (what was nexon thinking?)
            mplew.writeMapleAsciiString(item.getSeller()); //char name
            for (int ii = 0; ii < 28; ii++) {
                mplew.write(0);
            }
        }
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket showMTSCash(MapleCharacter p) {
        //16 01 00 00 00 00 00 00 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION2.getValue());
        mplew.writeInt(p.getCSPoints(4));
        mplew.writeInt(p.getCSPoints(2));
        return mplew.getPacket();
    }

    public static MaplePacket MTSWantedListingOver(int nx, int items) {
        //Listing period on [WANTED] items
        //(just a message stating you have gotten your NX/items back, only displays if nx or items != 0)
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(61);
        mplew.writeInt(nx);
        mplew.writeInt(items);
        return mplew.getPacket();
    }

    public static MaplePacket MTSConfirmSell() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(29);
        return mplew.getPacket();
    }

    public static MaplePacket MTSConfirmBuy() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(51);
        return mplew.getPacket();
    }

    public static MaplePacket MTSFailBuy() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(52);
        mplew.write(66);
        return mplew.getPacket();
    }

    public static MaplePacket MTSConfirmTransfer(int quantity, int pos) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(39);
        mplew.writeInt(quantity);
        mplew.writeInt(pos);
        return mplew.getPacket();
    }

    public static MaplePacket notYetSoldInv(List<MTSItemInfo> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(35);

        mplew.writeInt(items.size());

        if (!items.isEmpty()) {
            for (MTSItemInfo item : items) {
                addItemInfo(mplew, item.getItem(), true);
                mplew.writeInt(item.getID()); //id
                mplew.writeInt(item.getTaxes()); //this + below = price
                mplew.writeInt(item.getPrice()); //price
                mplew.writeInt(0);
                mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getEndingDate().getTime()));
                mplew.writeMapleAsciiString(item.getSeller()); //account name (what was nexon thinking?)
                mplew.writeMapleAsciiString(item.getSeller()); //char name
                for (int ii = 0; ii < 28; ii++) {
                    mplew.write(0);
                }
            }
        } else {
            mplew.writeInt(0);
        }

        return mplew.getPacket();
    }

    public static MaplePacket transferInventory(List<MTSItemInfo> items) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MTS_OPERATION.getValue());
        mplew.write(33);
        mplew.writeInt(items.size());
        if (!items.isEmpty()) {
            for (MTSItemInfo item : items) {
                addItemInfo(mplew, item.getItem(), true);
                mplew.writeInt(item.getID()); //id
                mplew.writeInt(item.getTaxes()); //taxes
                mplew.writeInt(item.getPrice()); //price
                mplew.writeInt(0);
                mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getEndingDate().getTime()));
                mplew.writeMapleAsciiString(item.getSeller()); //account name (what was nexon thinking?)
                mplew.writeMapleAsciiString(item.getSeller()); //char name
                for (int i = 0; i < 28; i++) {
                    mplew.write(0);
                }
            }
        }
        mplew.write(208 + items.size());
        mplew.write(HexTool.getByteArrayFromHexString("FF FF FF 00"));

        return mplew.getPacket();
    }

    public static MaplePacket enableMTS() {
        return enableCSUse0();
    }

    public static MaplePacket showZakumShrineTimeLeft(int timeleft) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ZAKUM_SHRINE.getValue());
        mplew.write(0);
        mplew.writeInt(timeleft);

        return mplew.getPacket();
    }

    public static MaplePacket boatDockStatus() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOAT_DOCK.getValue());
        mplew.writeShort(1); // 1 = almost about to leave, 3 = not here

        return mplew.getPacket();
    }

    public static MaplePacket boatPacket(int effect) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOAT_EFFECT.getValue());
        mplew.writeShort(effect); //1034: balrog boat comes, 1548: boat comes in ellinia/orbis station, 520: boat leaves ellinia/orbis station

        return mplew.getPacket();
    }

    public static MaplePacket startMonsterCarnival(int team) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_START.getValue());
        mplew.write(team);
        mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"));
        return mplew.getPacket();
    }

    public static MaplePacket playerDiedMessage(String name, int lostCP, int team) { //CPQ
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_DIED.getValue());
        mplew.write(team); //team
        mplew.writeMapleAsciiString(name);
        mplew.write(lostCP);
        return mplew.getPacket();
    }

    public static MaplePacket CPUpdate(boolean party, int curCP, int totalCP, int team) { //CPQ
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        if (!party) {
            mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_OBTAINED_CP.getValue());
        } else {
            mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_PARTY_CP.getValue());
            mplew.write(team); //team?
        }
        mplew.writeShort(curCP);
        mplew.writeShort(totalCP);
        return mplew.getPacket();
    }

    public static MaplePacket playerSummoned(String name, int tab, int number) {
        //E5 00
        //02 tabnumber
        //04 number
        //09 00 57 61 72 50 61 74 6A 65 68 name
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.MONSTER_CARNIVAL_SUMMON.getValue());
        mplew.write(tab);
        mplew.write(number);
        mplew.writeMapleAsciiString(name);
        return mplew.getPacket();
    }

    public static MaplePacket refreshTeleportRockMapList(MapleCharacter chr, byte type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TELEPORT_ROCK.getValue());
        mplew.write(3);
        mplew.write(type);
        List<Integer> maps = chr.getTeleportRockMaps(type);
        int limit = 5;
        if (type == 1) {
            limit = 10;
        }
        for (int map : maps) {
            mplew.writeInt(map);
        }
        for (int i = maps.size(); i < limit; i++) {
            mplew.writeInt(999999999);
        }

        return mplew.getPacket();
    }

    public static MaplePacket giveEnergyCharge(int barammount) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        mplew.writeLong(MapleBuffStat.ENERGY_CHARGE.getValue());
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeShort(barammount);
        mplew.writeShort(0);
        mplew.writeLong(0);
        mplew.write(0);
        mplew.writeInt(50);

        return mplew.getPacket();
    }

    public static MaplePacket giveForeignEnergyCharge(int cid, int barammount) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
        mplew.writeInt(cid);
        mplew.writeLong(MapleBuffStat.ENERGY_CHARGE.getValue());
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeShort(barammount);
        mplew.writeShort(0);
        mplew.writeLong(0);
        mplew.write(0);
        mplew.writeInt(50);

        return mplew.getPacket();
    }

	/*public static MaplePacket giveEnergyCharge(int baramount) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());

		mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 08 00 00 00")); // buffmask
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.writeShort(baramount);

		for (int i = 0; i < 4; i++) { //  May contain animation stuff..
			mplew.writeInt(0);
		}

		return mplew.getPacket();
	}*/

	/*packet.add<int16_t>(SMSG_SKILL_USE);

	BuffsPacketHelper::addBytes(packet, pskill.types);

	packet.add<int16_t>(0);
	for (size_t i = 0; i < pskill.vals.size(); i++) {
		packet.add<int16_t>(pskill.vals[i]);   X
		2 packet.add<int16_t>(0);            2
		4 packet.add<int32_t>(skillid);     4
		4 packet.add<int32_t>(0); // No idea, hate pirates, seems to be server tick count in ms      4
		1 packet.add<int8_t>(0);                       1
		2 packet.add<int16_t>(castedtime);                 2
	}
	2 packet.add<int16_t>(0); 2
	1 packet.add<int8_t>(0); // Number of times you've been buffed total - only certain skills have this part
	player->getSession()->send(packet);*/
	/*public static MaplePacket giveForeignEnergyCharge(int cid, int baramount) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
		mplew.writeInt(cid);

		mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 08 00 00 00")); // buffmask
		mplew.writeLong(0);
		mplew.writeShort(0);
		mplew.writeShort(baramount); // 0=no bar, 10000=full bar
		for (int i = 0; i < 3; i++) { //  May contain animation stuff..
			mplew.writeInt(0);
		}
		mplew.writeShort(0);
		mplew.write(0);

		return mplew.getPacket();
	}*/

    public static MaplePacket spouseChat(String from, String message, int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPOUSE_CHAT.getValue());
        mplew.write(type);
        if (type == 4) {
            mplew.write(1);
        } else {
            mplew.writeMapleAsciiString(from);
            mplew.write(5);
        }
        mplew.writeMapleAsciiString(message);

        return mplew.getPacket();
    }

    public static MaplePacket spouseChatOffline() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SPOUSE_CHAT.getValue());
        mplew.writeInt(4);

        return mplew.getPacket();
    }

    public static MaplePacket reportReply(byte type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REPORT_PLAYER_MSG.getValue());
        mplew.write(type);

        return mplew.getPacket();
    }

    public static MaplePacket sendMapleTip(String msg) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MAPLE_TIP.getValue());
        mplew.write(1);
        mplew.writeMapleAsciiString(msg);
        mplew.writeShort(0); // Second Line

        return mplew.getPacket();
    }

    private static void addAnnounceBox(MaplePacketLittleEndianWriter mplew, IMaplePlayerShop shop) {
        mplew.write(4);
        mplew.writeInt(((MaplePlayerShop) shop).getObjectId());
        mplew.writeMapleAsciiString(shop.getDescription());
        mplew.write(0);
        mplew.write(shop.getItemId() % 10);
        mplew.write(1);
        mplew.write(shop.getFreeSlot() > -1 ? 4 : 1);
        mplew.write(0);
    }

    public static MaplePacket sendPlayerShopBox(MapleCharacter c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.UPDATE_CHAR_BOX.getValue());
        mplew.writeInt(c.getId());
        addAnnounceBox(mplew, c.getPlayerShop());
        return mplew.getPacket();
    }

    public static MaplePacket hiredMerchantBox(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(50);
        mplew.write(7);
        return mplew.getPacket();
    }

    public static MaplePacket getMaplePlayerStore(MapleCharacter chr, boolean firstTime) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue()); // header.

        IMaplePlayerShop ips = chr.getPlayerShop();
        int type = ips.getShopType();
        if (type == 1) {
            mplew.write(HexTool.getByteArrayFromHexString("05 05 04"));
        } else if (type == 2) {
            mplew.write(HexTool.getByteArrayFromHexString("05 04 04"));
        } else if (type == 3) {
            mplew.write(HexTool.getByteArrayFromHexString("05 02 02"));
        } else if (type == 4) {
            mplew.write(HexTool.getByteArrayFromHexString("05 01 02"));
        }

        mplew.write(ips.isOwner(chr) ? 0 : 1);
        mplew.write(0);
        if (type == 2 || type == 3 || type == 4) {
            addCharLook(mplew, ((MaplePlayerShop) ips).getMCOwner(), false);
            mplew.writeMapleAsciiString(ips.getOwnerName());
        } else {
            mplew.writeInt(ips.getItemId());
            mplew.writeMapleAsciiString("Hired Merchant");
        }
        for (Pair<Byte, MapleCharacter> storechr : ips.getVisitors()) {
            mplew.write(storechr.getLeft());
            addCharLook(mplew, storechr.getRight(), false);
            mplew.writeMapleAsciiString(storechr.getRight().getName());
        }
        mplew.write(-1);
        if (type == 1) {
            mplew.writeShort(0);
            mplew.writeMapleAsciiString(ips.getOwnerName());
            if (ips.isOwner(chr)) {
                mplew.writeInt(Integer.MAX_VALUE); // contains timing, suck my dick we dont need this
                mplew.write(firstTime ? 1 : 0);
                mplew.write(HexTool.getByteArrayFromHexString("00 00 00 00 00"));
            }
        }
        mplew.writeMapleAsciiString(ips.getDescription());
        mplew.write(16);
        if (type == 1) {
            mplew.writeInt(0);
        }
        mplew.write(ips.getItems().size());
        if (ips.getItems().isEmpty()) {
            if (type == 1) {
                mplew.write(0);
            } else {
                mplew.writeInt(0);
            }
        } else {
            for (MaplePlayerShopItem item : ips.getItems()) {
                mplew.writeShort(item.getBundles());
                mplew.writeShort(item.getItem().getQuantity());
                mplew.writeInt(item.getPrice());
                addItemInfo(mplew, item.getItem(), true);
            }
        }
        return mplew.getPacket();
    }

    public static MaplePacket shopChat(String message, int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(HexTool.getByteArrayFromHexString("06 08"));
        mplew.write(slot);
        mplew.writeMapleAsciiString(message);
        return mplew.getPacket();
    }

    public static MaplePacket shopErrorMessage(int error, int type) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(10);
        mplew.write(type);
        mplew.write(error);
        return mplew.getPacket();
    }

    public static MaplePacket spawnHiredMerchant(HiredMerchant hm) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.SPAWN_HIRED_MERCHANT.getValue());
        mplew.writeInt(hm.getOwnerId());
        mplew.writeInt(hm.getItemId());
        mplew.writeShort((short) hm.getPosition().getX());
        mplew.writeShort((short) hm.getPosition().getY());
        mplew.writeShort(0);
        mplew.writeMapleAsciiString(hm.getOwnerName());
        mplew.write(5);
        mplew.writeInt(hm.getObjectId());
        mplew.writeMapleAsciiString(hm.getDescription());
        mplew.write(hm.getItemId() % 10);
        mplew.write(HexTool.getByteArrayFromHexString("01 04"));
        return mplew.getPacket();
    }

    public static MaplePacket destroyHiredMerchant(int id) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DESTROY_HIRED_MERCHANT.getValue());
        mplew.writeInt(id);
        return mplew.getPacket();
    }

    public static MaplePacket shopItemUpdate(IMaplePlayerShop shop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(25);
        if (shop.getShopType() == 1) {
            mplew.writeInt(0);
        }
        mplew.write(shop.getItems().size());
        for (MaplePlayerShopItem item : shop.getItems()) {
            mplew.writeShort(item.getBundles());
            mplew.writeShort(item.getItem().getQuantity());
            mplew.writeInt(item.getPrice());
            addItemInfo(mplew, item.getItem(), true);
        }
        return mplew.getPacket();
    }

    public static MaplePacket shopVisitorAdd(MapleCharacter chr, int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(4);
        mplew.write(slot);
        addCharLook(mplew, chr, false);
        mplew.writeMapleAsciiString(chr.getName());
        return mplew.getPacket();
    }

    public static MaplePacket shopVisitorLeave(int slot) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.PLAYER_INTERACTION.getValue());
        mplew.write(10);
        if (slot > 0) {
            mplew.write(slot);
        }

        return mplew.getPacket();
    }

    public static MaplePacket updateHiredMerchant(HiredMerchant shop) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_HIRED_MERCHANT.getValue());
        mplew.writeInt(shop.getOwnerId());
        mplew.write(5);
        mplew.writeInt(shop.getObjectId());
        mplew.writeMapleAsciiString(shop.getDescription());
        mplew.write(shop.getItemId() % 10);
        mplew.write(shop.getFreeSlot() > -1 ? 3 : 2);
        mplew.write(4);

        return mplew.getPacket();
    }

    public static MaplePacket giveSpeedInfusion(int buffid, int bufflength, List<Pair<MapleBuffStat, Integer>> statups) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_BUFF.getValue());
        mplew.writeLong(getLongMask(statups));
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(statups.get(0).getRight());
        mplew.writeInt(buffid);
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeShort(bufflength);
        mplew.write(0x58);
        mplew.write(2);

        return mplew.getPacket();
    }

    public static MaplePacket giveForeignInfusion(int cid, List<Pair<MapleBuffStat, Integer>> statups, int duration, int sid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.GIVE_FOREIGN_BUFF.getValue());
        mplew.writeInt(cid);
        mplew.writeLong(getLongMask(statups));
        mplew.writeLong(0);
        mplew.writeShort(0);
        mplew.writeInt(statups.get(0).getRight());
        mplew.writeInt(sid);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeShort(duration);
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket sendMaplePolice(int reason, String reasoning, int duration) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.GM_POLICE.getValue());
        mplew.writeInt(duration);
        mplew.write(4);
        mplew.write(reason);
        mplew.writeMapleAsciiString(reasoning);
        return mplew.getPacket();
    }

    public static MaplePacket updateBattleShipHP(int chr, int hp) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SHOW_MONSTER_HP.getValue());
        mplew.writeInt(chr);
        mplew.write(hp);

        return mplew.getPacket();
    }

    public static MaplePacket updateMount(MapleCharacter chr, MapleMount mount, boolean levelup) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_MOUNT.getValue());
        mplew.writeInt(chr.getId());
        mplew.writeInt(mount.getLevel());
        mplew.writeInt(mount.getExp());
        mplew.writeInt(mount.getTiredness());
        mplew.write(levelup ? (byte) 1 : (byte) 0);

        return mplew.getPacket();
    }

    public static MaplePacket mountInfo(MapleCharacter chr) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UPDATE_MOUNT.getValue());
        mplew.writeInt(chr.getId());
        mplew.writeInt(1);
        mplew.writeInt(0);
        mplew.writeInt(0);
        mplew.write(0);

        return mplew.getPacket();
    }

    public static MaplePacket getAllianceInfo(MapleAlliance alliance) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x0C);
        mplew.write(1);
        mplew.writeInt(alliance.getId());
        mplew.writeMapleAsciiString(alliance.getName());
        for (int i = 1; i <= 5; i++) {
            mplew.writeMapleAsciiString(alliance.getRankTitle(i));
        }
        mplew.write(alliance.getGuilds().size());
        mplew.writeInt(2); // probably capacity
        for (Integer guild : alliance.getGuilds()) {
            mplew.writeInt(guild);
        }
        mplew.writeMapleAsciiString(alliance.getNotice());

        return mplew.getPacket();
    }

    public static MaplePacket makeNewAlliance(MapleAlliance alliance, MapleClient c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x0F);
        mplew.writeInt(alliance.getId());
        mplew.writeMapleAsciiString(alliance.getName());
        for (int i = 1; i <= 5; i++) {
            mplew.writeMapleAsciiString(alliance.getRankTitle(i));
        }
        mplew.write(alliance.getGuilds().size());
        for (Integer guild : alliance.getGuilds()) {
            mplew.writeInt(guild);
        }
        mplew.writeInt(2); // probably capacity
        mplew.writeShort(0);
        for (Integer guildd : alliance.getGuilds()) {
            try {
                getGuildInfo(mplew, c.getChannelServer().getWorldInterface().getGuild(guildd));
            } catch (RemoteException re) {
                c.getChannelServer().reconnectWorld();
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket getGuildAlliances(MapleAlliance alliance, MapleClient c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x0D);
        mplew.writeInt(alliance.getGuilds().size());
        for (Integer guild : alliance.getGuilds()) {
            try {
                getGuildInfo(mplew, c.getChannelServer().getWorldInterface().getGuild(guild));
            } catch (RemoteException re) {
                c.getChannelServer().reconnectWorld();
            }
        }

        return mplew.getPacket();
    }

    public static MaplePacket addGuildToAlliance(MapleAlliance alliance, int newGuild, MapleClient c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x12);
        mplew.writeInt(alliance.getId());
        mplew.writeMapleAsciiString(alliance.getName());
        for (int i = 1; i <= 5; i++) {
            mplew.writeMapleAsciiString(alliance.getRankTitle(i));
        }
        mplew.write(alliance.getGuilds().size());
        for (Integer guild : alliance.getGuilds()) {
            mplew.writeInt(guild);
        }
        mplew.writeInt(2);
        mplew.writeMapleAsciiString(alliance.getNotice());
        mplew.writeInt(newGuild);
        try {
            getGuildInfo(mplew, c.getChannelServer().getWorldInterface().getGuild(newGuild));
        } catch (RemoteException e) {
            c.getChannelServer().reconnectWorld();
        }

        return mplew.getPacket();
    }

    public static MaplePacket allianceMemberOnline(MapleCharacter mc, boolean online) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x0E);
        mplew.writeInt(mc.getGuild().getAllianceId());
        mplew.writeInt(mc.getGuildId());
        mplew.writeInt(mc.getId());
        mplew.write(online ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket allianceNotice(int id, String notice) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x1C);
        mplew.writeInt(id);
        mplew.writeMapleAsciiString(notice);

        return mplew.getPacket();
    }

    public static MaplePacket changeAllianceRankTitle(int alliance, String[] ranks) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x1A);
        mplew.writeInt(alliance);
        for (int i = 0; i < 5; i++) {
            mplew.writeMapleAsciiString(ranks[i]);
        }

        return mplew.getPacket();
    }

    public static MaplePacket updateAllianceJobLevel(MapleCharacter mc) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x18);
        mplew.writeInt(mc.getGuild().getAllianceId());
        mplew.writeInt(mc.getGuildId());
        mplew.writeInt(mc.getId());
        mplew.writeInt(mc.getLevel());
        mplew.writeInt(mc.getJob().getId());

        return mplew.getPacket();
    }

    public static MaplePacket removeGuildFromAlliance(MapleAlliance alliance, int expelledGuild, MapleClient c) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(0x10);
        mplew.writeInt(alliance.getId());
        mplew.writeMapleAsciiString(alliance.getName());
        for (int i = 1; i <= 5; i++) {
            mplew.writeMapleAsciiString(alliance.getRankTitle(i));
        }
        mplew.write(alliance.getGuilds().size());
        for (Integer guild : alliance.getGuilds()) {
            mplew.writeInt(guild);
        }
        mplew.writeInt(2);
        mplew.writeMapleAsciiString(alliance.getNotice());
        mplew.writeInt(expelledGuild);
        try {
            getGuildInfo(mplew, c.getChannelServer().getWorldInterface().getGuild(expelledGuild));
        } catch (RemoteException re) {
            c.getChannelServer().reconnectWorld();
        }
        mplew.write(1);

        return mplew.getPacket();
    }

    public static MaplePacket disbandAlliance(int alliance) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(29);
        mplew.writeInt(alliance);

        return mplew.getPacket();
    }

    public static MaplePacket sendShowInfo(int allianceid, int playerid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(2);
        mplew.writeInt(allianceid);
        mplew.writeInt(playerid);

        return mplew.getPacket();
    }

    public static MaplePacket sendInvitation(int allianceid, int playerid, final String guildname) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(5);
        mplew.writeInt(allianceid);
        mplew.writeInt(playerid);
        mplew.writeMapleAsciiString(guildname);

        return mplew.getPacket();
    }

    public static MaplePacket sendChangeGuild(int allianceid, int playerid, int guildid, int option) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(7);
        mplew.writeInt(allianceid);
        mplew.writeInt(guildid);
        mplew.writeInt(playerid);
        mplew.write(option);

        return mplew.getPacket();
    }

    public static MaplePacket sendChangeLeader(int allianceid, int playerid, int victim) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(8);
        mplew.writeInt(allianceid);
        mplew.writeInt(playerid);
        mplew.writeInt(victim);

        return mplew.getPacket();
    }

    public static MaplePacket sendChangeRank(int allianceid, int playerid, int int1, byte byte1) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALLIANCE_OPERATION.getValue());
        mplew.write(9);
        mplew.writeInt(allianceid);
        mplew.writeInt(playerid);
        mplew.writeInt(int1);
        mplew.writeInt(byte1);

        return mplew.getPacket();
    }

    public static MaplePacket getAddCSInventory(MapleClient c, MapleCSInventoryItem item) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(103); //71 in other? find what makes this
        mplew.writeInt(item.getUniqueId());
        mplew.writeInt(0);
        mplew.writeInt(c.getAccID());
        mplew.writeInt(0);
        mplew.writeInt(item.getItemId());
        mplew.writeInt(item.getSn());
        mplew.write(1);
        mplew.writeLong(0);
        mplew.writeInt(0);
        mplew.writeShort(0);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getExpire().getTime()));
        mplew.writeLong(0);
        mplew.writeMapleAsciiString(item.getSender());
        mplew.writeInt(item.getItemId());
        mplew.writeShort(1);

        return mplew.getPacket();
    }

    //03 01 6D 01 00 00 00 01 00 15 00 D8 82 3D 00
	/*public static MaplePacket transferFromCSToInv(IItem item, int position) {
		//23 01 4D 01 00 01 CA 4A 0F 00 01 A7 C0 62 00 00 00 00 00 00 CD 77 3C AB 25 CA 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 65 69 6E 6E 08 00 00 40 E0 FD 3B 37 4F 01 FF FF FF FF
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.write(0x4A);
		mplew.write(position);//in csinventory
		addItemInfo(mplew, item, true, false, true);
		return mplew.getPacket();
	}

	public static MaplePacket transferFromInvToCS(MapleCharacter c, MapleCSInventoryItem item) {
		//23 01
		//4F
		//A7 C0 62 00 = unique id
		//00 00 00 00 = 0
		//01 FA 3C 00 = some id?
		//00 00 00 00 = 0
		//CA 4A 0F 00 = item id
		//2B 2D 31 01 = sn
		//01 00 00 00 00 00 00 00 00 00 00 00 00 00 00
		//00 CD 77 3C AB 25 CA 01
		//00 00 00 00 00 00 00 00
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.write(0x4B);
		mplew.writeInt(item.getUniqueId());
		mplew.writeInt(0);
		mplew.writeInt(c.getAccountID());
		mplew.writeInt(0);
		mplew.writeInt(item.getItemId());
		mplew.writeInt(item.getSn());
		mplew.write(HexTool.getByteArrayFromHexString("01 00 00 00 00 00 00 00 00 00 00 00 00 00 00"));
		mplew.writeLong(getKoreanTimestamp(item.getExpire().getTime()));
		mplew.writeLong(0);
		return mplew.getPacket();
	}

	public static MaplePacket getCSInventory(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.write(0x2F);
		MapleCSInventory csinv = chr.getCSInventory();
		mplew.writeShort(csinv.getCSItems().size());
		for (MapleCSInventoryItem citem : csinv.getCSItems().values()) {
			mplew.writeInt(citem.getUniqueId());
			mplew.writeInt(0);
			mplew.writeInt(chr.getAccountID());
			mplew.writeInt(0);
			mplew.writeInt(citem.getItemId());
			mplew.writeInt(0);
			mplew.writeShort(1);
			mplew.writeMapleNameString(citem.getSender());
			mplew.write(0x40);
			mplew.writeLong(getKoreanTimestamp(citem.getExpire().getTime()));
			mplew.writeInt(citem.getSn());
			mplew.writeInt(0);
		}
		mplew.writeShort(4);
		mplew.writeShort(3);
		return mplew.getPacket();
	}

	public static MaplePacket getCSGifts(MapleCharacter chr) {
		MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

		mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
		mplew.write(0x31);

		Collection<MapleCSInventoryItem> inv = chr.getCSInventory().getCSGifts().values();
		mplew.writeShort(inv.size());
		for (MapleCSInventoryItem gift : inv) {
			mplew.writeInt(gift.getUniqueId());
			mplew.writeInt(0);
			mplew.writeInt(gift.getItemId());
			mplew.writeMapleNameString(gift.getSender());
			//mplew.writeShort(10);
			mplew.write(0x10);
			mplew.writeAsciiString(gift.getMessage());
			mplew.write(HexTool.getByteArrayFromHexString("00 00 D8 7D 3C 15 C0 EA 65 06 DF 54 40 00 8D 53 40 00 D8 7D 3C 15 D0 EA 65 06 6F 53 40 00 D8 7D 3C 15 DC EA 65 06 4C 53 40 00 D4 7D 3C 15 E8 EA 65 06 CC 52 40 00 D4 7D 3C 15 F8 EA"));
		}
		return mplew.getPacket();
	}*/

    public static MaplePacket showBoughtCSItem(MapleClient c, MapleCSInventoryItem item) {
        //23 01 3E A7 C0 62 00 00 00 00 00 01 FA 3C 00 00 00 00 00 CA 4A 0F 00 2B 2D 31 01 01 00 00 00 60 4B 40 00 F4 AC 77 00 00 00 00 00 CD 77 3C AB 25 CA 01 00 00 00 00 00 00 00 00
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.CS_OPERATION.getValue());
        mplew.write(59);
        mplew.writeInt(item.getUniqueId());
        mplew.writeInt(0);
        mplew.writeInt(c.getAccID());
        mplew.writeInt(0);
        mplew.writeInt(item.getItemId());
        mplew.writeInt(item.getSn());
        mplew.writeShort(item.getQuantity());
        mplew.writeMapleNameString(item.getSender());
        mplew.write(64);
        mplew.writeLong(FileTimeUtil.getFileTimestamp(item.getExpire().getTime()));
        mplew.writeInt(item.getSn());
        mplew.writeLong(0);

        return mplew.getPacket();
    }

    public static MaplePacket showOXQuiz(int questionSet, int questionId, boolean askQuestion) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.OX_QUIZ.getValue());
        mplew.write(askQuestion ? 1 : 0);
        mplew.write(questionSet);
        mplew.writeShort(questionId);

        return mplew.getPacket();
    }

    public static MaplePacket leftKnockBack() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.LEFT_KNOCK_BACK.getValue());

        return mplew.getPacket();
    }

    public static MaplePacket rollSnowball(int roll0, int roll1) { //apparently this is wrong
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ROLL_SNOWBALL.getValue());
        mplew.write(0);
        mplew.writeLong(0);
        mplew.write(roll0);
        mplew.writeShort(0);
        mplew.write(roll1);
        mplew.write(0);
        mplew.writeLong(0);
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket hitSnowBall(int team, int damage) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.HIT_SNOWBALL.getValue());
        mplew.write(team); // 0 is down, 1 is up
        mplew.writeInt(damage);

        return mplew.getPacket();
    }

    public static MaplePacket snowballMessage(int team, int message) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.SNOWBALL_MESSAGE.getValue());
        mplew.write(team); // 0 is down, 1 is up
        mplew.writeInt(message);

        return mplew.getPacket();
    }

    public static MaplePacket sendMinerva(List<Pair<HiredMerchant, MaplePlayerShopItem>> result, int itemid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MINERVA_RESULT.getValue()); // header 0x43
        mplew.write(6);
        mplew.writeInt(0);
        mplew.writeInt(itemid);
        mplew.writeInt(result.size());
        for (Pair<HiredMerchant, MaplePlayerShopItem> phm : result) {
            HiredMerchant merch = phm.getLeft();
            mplew.writeMapleAsciiString(merch.getOwnerName());
            mplew.writeInt(merch.getMap().getId());
            mplew.writeMapleAsciiString(merch.getDescription());
            MaplePlayerShopItem item = phm.getRight();
            mplew.writeInt(item.getItem().getQuantity());
            mplew.writeInt(item.getBundles());
            mplew.writeInt(item.getPrice());
            mplew.writeInt(merch.getOwnerId());
            mplew.write(0);
            mplew.write(merch.getChannel());
            addItemInfo(mplew, item.getItem(), true);
        }
        return mplew.getPacket();
    }

    public static MaplePacket alertGMStatus(byte mode) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ALERT_GM.getValue());
        mplew.write(mode);
        if (mode == 2) {
            mplew.write(1);
            mplew.writeInt(7);
        }

        return mplew.getPacket();
    }

    public static MaplePacket unknownStatus() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.UNKNOWN_STATE.getValue());
        mplew.writeShort(Randomizer.nextInt(Short.MAX_VALUE));
        mplew.writeShort(0);

        return mplew.getPacket();
    }

    public static MaplePacket hideUI(boolean enable) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.HIDE_UI.getValue());
        mplew.write(enable ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket lockWindows(boolean enable) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.LOCK_WINDOWS.getValue());
        mplew.write(enable ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket addCard(boolean full, int cardid, int level) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTERBOOK_ADD.getValue());
        mplew.write(full ? 0 : 1);
        mplew.writeInt(cardid);
        mplew.writeInt(level);

        return mplew.getPacket();
    }

    public static MaplePacket changeCover(int cardid) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MONSTERBOOK_CHANGE_COVER.getValue());
        mplew.writeInt(cardid);

        return mplew.getPacket();
    }

    public static MaplePacket setDojoEnergy(int energy) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.MULUNGENERGY.getValue());
        mplew.writeMapleAsciiString("energy");
        mplew.writeMapleAsciiString(Integer.toString(Math.min(Math.max(0, energy), 10000)));

        return mplew.getPacket();
    }

    public static MaplePacket dojoWarpUp() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();
        mplew.writeShort(SendPacketOpcode.DOJO_WARP_UP.getValue());
        mplew.write(0);
        mplew.write(6);
        return mplew.getPacket();
    }

    public static MaplePacket shakeScreen(int type, int delay) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.BOSS_ENV.getValue());
        mplew.write(1);
        mplew.write(type);
        mplew.writeInt(delay);

        return mplew.getPacket();
    }

    public static MaplePacket sendHammerSlot(int hammers) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.VICIOUS_HAMMER.getValue());
        mplew.write(57);
        mplew.writeInt(0);
        mplew.writeInt(hammers);

        return mplew.getPacket();
    }

    public static MaplePacket sendHammerEnd() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.VICIOUS_HAMMER.getValue());
        mplew.write(61);
        mplew.writeInt(0);

        return mplew.getPacket();
    }

    public static MaplePacket enableTutor(boolean enable) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TUTOR_ENABLE.getValue());
        mplew.write(enable ? 1 : 0);

        return mplew.getPacket();
    }

    public static MaplePacket showTutorActions(byte mode, String text, int arg3, int arg4) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.TUTOR_ACTIONS.getValue());
        mplew.write(mode);
        if (mode == 0) {
            mplew.writeMapleAsciiString(text);
        }
        mplew.writeInt(arg3);
        mplew.writeInt(arg4);

        return mplew.getPacket();
    }

    public static MaplePacket showAranComboCounter(int count) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ARAN_COMBO_COUNTER.getValue());
        mplew.writeInt(count);

        return mplew.getPacket();
    }

    public static MaplePacket showAranSkillInfo() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.ARAN_SKILL_INFO.getValue());

        return mplew.getPacket();
    }

    public static MaplePacket writePacketFromHex(String hex) {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.write(HexTool.getByteArrayFromHexString(hex));

        return mplew.getPacket();
    }

    public static MaplePacket removeMapTimer() {
        MaplePacketLittleEndianWriter mplew = new MaplePacketLittleEndianWriter();

        mplew.writeShort(SendPacketOpcode.REMOVE_CLOCK.getValue());

        return mplew.getPacket();
    }
}