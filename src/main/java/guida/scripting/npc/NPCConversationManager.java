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

package guida.scripting.npc;

import guida.client.Equip;
import guida.client.IItem;
import guida.client.ISkill;
import guida.client.Item;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MapleRing;
import guida.client.MapleSkinColor;
import guida.client.MapleStat;
import guida.client.SkillFactory;
import guida.database.DatabaseConnection;
import guida.net.channel.ChannelServer;
import guida.net.channel.handler.DueyActionHandler;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.guild.MapleAlliance;
import guida.net.world.guild.MapleGuild;
import guida.net.world.remote.WorldChannelInterface;
import guida.scripting.AbstractPlayerInteraction;
import guida.server.GachaponItems;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.MapleMonsterCarnival;
import guida.server.MapleSquad;
import guida.server.MapleSquadType;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MapleMonsterStats;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapFactory;
import guida.server.quest.MapleQuest;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.Randomizer;

import java.awt.Point;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Matze
 */
public class NPCConversationManager extends AbstractPlayerInteraction {

    private final MapleClient c;
    private final int npc;
    private String getText;
    private MapleCharacter chr;
    private boolean hasMinMaxLim = false;
    private int minimumLimit = 0;
    private int maximumLimit = 0;

    public NPCConversationManager(MapleClient c, int npc) {
        super(c);
        this.c = c;
        this.npc = npc;
    }

    public NPCConversationManager(MapleClient c, int npc, MapleCharacter chr) {
        super(c);
        this.c = c;
        this.npc = npc;
        this.chr = chr;
    }

    public NPCConversationManager(MapleClient c, int npc, List<MaplePartyCharacter> otherParty, int b) { //CPQ
        super(c);
        this.c = c;
        this.npc = npc;
    }

    public static MapleAlliance createAlliance(MapleCharacter chr1, MapleCharacter chr2, String name) {
        int id = 0;
        int guild1 = chr1.getGuildId();
        int guild2 = chr2.getGuildId();
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("INSERT INTO `alliances` (`name`, `guild1`, `guild2`) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setInt(2, guild1);
            ps.setInt(3, guild2);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            rs.next();
            id = rs.getInt(1);
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
        MapleAlliance alliance = new MapleAlliance(name, id, guild1, guild2);
        try {
            WorldChannelInterface wci = chr1.getClient().getChannelServer().getWorldInterface();
            wci.setGuildAllianceId(guild1, id);
            wci.setGuildAllianceId(guild2, id);
            chr1.setAllianceRank(1);
            chr1.saveGuildStatus();
            chr2.setAllianceRank(2);
            chr2.saveGuildStatus();
            wci.addAlliance(id, alliance);
            wci.allianceMessage(id, MaplePacketCreator.makeNewAlliance(alliance, chr1.getClient()), -1, -1);
        } catch (RemoteException e) {
            chr1.getClient().getChannelServer().reconnectWorld();
            chr2.getClient().getChannelServer().reconnectWorld();
            return null;
        }
        return alliance;
    }

    public void dispose() {
        NPCScriptManager.getInstance().dispose(this);
    }

    /* 2nd Message Type Byte of the NPC_CHAT Packet
     * 00 = NPC Facing Left on Left Side of Chat Box
     * 01 = NPC Facing Left on Left Side of Chat Box No End Chat
     * 02 / 06 / 0A / 0E = Player Facing Left on Right Side of Chat Box
     * 03 / 07 / 0B / 0F = Player Facing Left on Right Side of Chat Box No End Chat
     * 04 = Custom NPC Facing Left on Right Side of Chat Box
     * 05 = NPC Facing Left on Right Side of Chat Box No End Chat
     * 08 = NPC Facing Right on Left Side of Chat Box
     * 09 = NPC Facing Right on Left Side of Chat Box No End Chat
     * 0C = NPC Facing Right on Right Side of Chat Box
     * 0D = NPC Facing Right on Right Side of Chat Box No End Chat
     */
    public void sendNext(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, (byte) 0, text, "00 01"));
    }

    public void sendNext(String text, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, type, text, "00 01"));
    }

    public void sendNext(String text, byte type, int npc) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, type, npc, text, "00 01"));
    }

    public void sendPrev(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, (byte) 0, text, "01 00"));
    }

    public void sendPrev(String text, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, type, text, "01 00"));
    }

    public void sendNextPrev(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, (byte) 0, text, "01 01"));
    }

    public void sendNextPrev(String text, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, type, text, "01 01"));
    }

    public void sendOk(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, (byte) 0, text, "00 00"));
    }

    public void sendOk(String text, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 0, type, text, "00 00"));
    }

    public void sendYesNo(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 1, (byte) 0, text, ""));
    }

    public void sendYesNo(String text, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 1, type, text, ""));
    }

    public void sendSimple(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 4, (byte) 0, text, ""));
    }

    public void sendSimple(String text, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 4, type, text, ""));
    }

    public void sendAcceptDecline(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 12, (byte) 0, text, ""));
    }

    public void sendAcceptDecline(String text, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalk(npc, (byte) 12, type, text, ""));
    }

    public void sendStyle(String text, int[] styles) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalkStyle(npc, (byte) 0, text, styles));
    }

    public void sendStyle(String text, int[] styles, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalkStyle(npc, type, text, styles));
    }

    public void sendGetNumber(String text, int def, int min, int max) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalkNum(npc, (byte) 0, text, def, min, max));
        setGetLimits(min, max);
    }

    public void sendGetNumber(String text, int def, int min, int max, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalkNum(npc, type, text, def, min, max));
        setGetLimits(min, max);
    }

    public void sendGetText(String text, String def, short min, short max) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalkText(npc, (byte) 0, text, def, min, max));
        setGetLimits(min, max);
    }

    public void sendGetText(String text, String def, short min, short max, byte type) {
        getClient().sendPacket(MaplePacketCreator.getNPCTalkText(npc, type, text, def, min, max));
        setGetLimits(min, max);
    }

    public void sendGetQuestion(String text) {
        getClient().sendPacket(MaplePacketCreator.getNPCAskQuestion(npc, text));
    }

    public void setGetText(String text) {
        getText = text;
    }

    public String getText() {
        return getText;
    }

    public void setGetLimits(int min, int max) {
        hasMinMaxLim = true;
        minimumLimit = min;
        maximumLimit = max;
    }

    public boolean hasGetLimits() {
        return hasMinMaxLim;
    }

    public int getMinimumGetLimit() {
        return minimumLimit;
    }

    public int getMaximumGetLimit() {
        return maximumLimit;
    }

    public void unsetGetLimits() {
        hasMinMaxLim = false;
    }

    public void changeJob(MapleJob job) {
        getPlayer().changeJob(job);
    }

    public void startQuest(int id) {
        startQuest(id, false);
    }

    public void startQuest(int id, boolean force) {
        MapleQuest.getInstance(id).start(getPlayer(), npc, force);
    }

    public void completeQuest(int id) {
        completeQuest(id, false);
    }

    public void completeQuest(int id, boolean force) {
        MapleQuest.getInstance(id).complete(getPlayer(), npc, force, true);
    }

    public void gainMeso(int gain) {
        getPlayer().gainMeso(gain, true, false, true);
    }

    public void gainExp(int gain) {
        getPlayer().gainExp(gain, true, true);
    }

    public int getNpc() {
        return npc;
    }

    public void teachSkill(int id, int level, int masterlevel) {
        getPlayer().changeSkillLevel(SkillFactory.getSkill(id), level, masterlevel);
    }

    public MapleClient getC() {
        return getClient();
    }

    public void showEffect(String effect) {
        getPlayer().getMap().broadcastMessage(MaplePacketCreator.showEffect(effect));
    }

    public void playSound(String sound) {
        getClient().getPlayer().getMap().broadcastMessage(MaplePacketCreator.playSound(sound));
    }

    @Override
    public String toString() {
        return "Conversation with NPC: " + npc;
    }

    public void updateBuddyCapacity(int capacity) {
        getPlayer().setBuddyCapacity(capacity);
    }

    public int getBuddyCapacity() {
        return getPlayer().getBuddyCapacity();
    }

    public void setHair(int hair) {
        getPlayer().setHair(hair);
        getPlayer().updateSingleStat(MapleStat.HAIR, hair);
        getPlayer().equipChanged();
    }

    public void setFace(int face) {
        getPlayer().setFace(face);
        getPlayer().updateSingleStat(MapleStat.FACE, face);
        getPlayer().equipChanged();
    }

    public void setSkin(int color) {
        getPlayer().setSkinColor(MapleSkinColor.getById(color));
        getPlayer().updateSingleStat(MapleStat.SKIN, color);
        getPlayer().equipChanged();
    }

    public void warpParty(int mapId) {
        MapleMap target = getMap(mapId);
        for (MaplePartyCharacter chrs : getPlayer().getParty().getMembers()) {
            MapleCharacter curChar = c.getChannelServer().getPlayerStorage().getCharacterByName(chrs.getName());
            if (curChar.getEventInstance() == null && c.getPlayer().getEventInstance() == null || curChar.getEventInstance() == getPlayer().getEventInstance()) {
                curChar.changeMap(target, target.getPortal(0));
            }
        }
    }

    public void warpPartyWithExp(int mapId, int exp) {
        MapleMap target = getMap(mapId);
        for (MaplePartyCharacter chrs : getPlayer().getParty().getMembers()) {
            MapleCharacter curChar = c.getChannelServer().getPlayerStorage().getCharacterByName(chrs.getName());
            if (curChar.getEventInstance() == null && c.getPlayer().getEventInstance() == null || curChar.getEventInstance() == getPlayer().getEventInstance()) {
                curChar.changeMap(target, target.getPortal(0));
                curChar.gainExp(exp, true, false, true, false);
            }
        }
    }

    public void givePartyExp(int exp) {
        for (MaplePartyCharacter chrs : getPlayer().getParty().getMembers()) {
            MapleCharacter curChar = chrs.getPlayer();
            if (curChar != null && chrs.isOnline()) {
                curChar.gainExp(exp * c.getChannelServer().getExpRate(), true, true);
            }
        }
    }

    public void givePartyAchievement(int aId) {
        if (getPlayer().getParty() == null) {
            getPlayer().finishAchievement(aId);
            return;
        }
        for (MaplePartyCharacter chrs : getPlayer().getParty().getMembers()) {
            MapleCharacter curChar = chrs.getPlayer();
            if (curChar != null && chrs.isOnline()) {
                curChar.finishAchievement(aId);
            }
        }
    }

    public void warpPartyWithExpMeso(int mapId, int exp, int meso) {
        MapleMap target = getMap(mapId);
        for (MaplePartyCharacter chrs : getPlayer().getParty().getMembers()) {
            MapleCharacter curChar = c.getChannelServer().getPlayerStorage().getCharacterByName(chrs.getName());
            if (curChar.getEventInstance() == null && c.getPlayer().getEventInstance() == null || curChar.getEventInstance() == getPlayer().getEventInstance()) {
                curChar.changeMap(target, target.getPortal(0));
                curChar.gainExp(exp, true, false, true, false);
                curChar.gainMeso(meso, true);
            }
        }
    }

    public List<MapleCharacter> getPartyMembers() {
        return c.getPlayer().getParty().getPartyMembers();
    }

    public int itemQuantity(int itemid) {
        MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
        MapleInventory iv = getPlayer().getInventory(type);
        return iv.countById(itemid);
    }

    public MapleSquad createMapleSquad(MapleSquadType type) {
        MapleSquad squad = new MapleSquad(c.getChannel(), getPlayer());
        if (getSquadState(type) == 0) {
            c.getChannelServer().addMapleSquad(squad, type);
        } else {
            return null;
        }
        return squad;
    }

    public MapleCharacter getSquadMember(MapleSquadType type, int index) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        MapleCharacter ret = null;
        if (squad != null) {
            ret = squad.getMembers().get(index);
        }
        return ret;
    }

    public int getSquadState(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        if (squad != null) {
            return squad.getStatus();
        } else {
            return 0;
        }
    }

    public void setSquadState(MapleSquadType type, int state) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        if (squad != null) {
            squad.setStatus(state);
        }
    }

    public void changeRewarpType(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        if (squad != null) {
            squad.setRewarpType(!squad.isGroupRewarp());
            sendOk("Your squad will now be using a '" + (squad.isGroupRewarp() ? "Squad Based Rewarp Counter" : "Player Based Rewarp Counter") + "'.");
        }
    }

    public boolean checkSquadLeader(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        return squad != null && squad.getLeader().getId() == getPlayer().getId();
    }

    public void removeMapleSquad(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        if (squad != null) {
            if (squad.getLeader().getId() == getPlayer().getId()) {
                squad.clear();
                c.getChannelServer().removeMapleSquad(squad, type);
            }
        }
    }

    public int numSquadMembers(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        int ret = 0;
        if (squad != null) {
            ret = squad.getSquadSize();
        }
        return ret;
    }

    public boolean isSquadMember(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        boolean ret = false;
        if (squad.containsMember(getPlayer())) {
            ret = true;
        }
        return ret;
    }

    public void addSquadMember(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        if (squad != null) {
            squad.addMember(getPlayer());
        }
    }

    public void removeSquadMember(MapleSquadType type, MapleCharacter chr, boolean ban) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        if (squad != null) {
            squad.banMember(chr, ban);
        }
    }

    public void removeSquadMember(MapleSquadType type, int index, boolean ban) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        if (squad != null) {
            MapleCharacter chrs = squad.getMembers().get(index);
            squad.banMember(chrs, ban);
        }
    }

    public boolean canAddSquadMember(MapleSquadType type) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        return squad != null && !squad.isBanned(getPlayer());
    }

    public void warpSquadMembers(MapleSquadType type, int mapId) {
        MapleSquad squad = c.getChannelServer().getMapleSquad(type);
        MapleMap map = c.getChannelServer().getMapFactory().getMap(mapId);
        if (squad != null) {
            if (checkSquadLeader(type)) {
                for (MapleCharacter chrs : squad.getMembers()) {
                    chrs.changeMap(map, map.getPortal(0));
                }
            }
        }
    }

    public MapleSquad getMapleSquad(MapleSquadType type) {
        return c.getChannelServer().getMapleSquad(type);
    }

    public void setSquadBossLog(MapleSquadType type, String boss) {
        if (getMapleSquad(type) != null) {
            MapleSquad squad = getMapleSquad(type);
            for (MapleCharacter chrs : squad.getMembers()) {
                chrs.setBossLog(boss);
            }
        }
    }

    public MapleCharacter getCharByName(String name) {
        try {
            return c.getChannelServer().getPlayerStorage().getCharacterByName(name);
        } catch (Exception e) {
            return null;
        }
    }

    public void resetReactors() {
        getPlayer().getMap().resetReactors();
    }

    public void displayGuildRanks() {
        MapleGuild.displayGuildRanks(getClient(), npc);
    }

    public MapleCharacter getCharacter() {
        return chr;
    }

    public int getDayOfWeek() {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
    }

    public void giveNPCBuff(MapleCharacter chr, int itemId) {
        MapleItemInformationProvider.getInstance().getItemEffect(itemId).applyTo(chr);
    }

    public void giveWonkyBuff(MapleCharacter chr) {
        long what = Math.round(Math.random() * 4);
        int what1 = (int) what;
        int[] Buffs = {2022090, 2022091, 2022092, 2022093};
        int buffToGive = Buffs[what1];
        MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
        MapleStatEffect statEffect = mii.getItemEffect(buffToGive);
        statEffect.applyTo(chr);
    }

    public boolean hasSkill(int skillid) {
        ISkill theSkill = SkillFactory.getSkill(skillid);
        if (theSkill != null) {
            return c.getPlayer().getSkillLevel(theSkill) > 0;
        } else {
            return false;
        }
    }

    public void spawnMonster(int mobid, int HP, int MP, int level, int EXP, int boss, int undead, int amount, int x, int y) {
        MapleMonsterStats newStats = new MapleMonsterStats();
        Point spawnPos = new Point(x, y);
        if (HP >= 0) {
            newStats.setHp(HP);
        }
        if (MP >= 0) {
            newStats.setMp(MP);
        }
        if (level >= 0) {
            newStats.setLevel(level);
        }
        if (EXP >= 0) {
            newStats.setExp(EXP);
        }
        newStats.setBoss(boss == 1);
        newStats.setUndead(undead == 1);
        for (int i = 0; i < amount; i++) {
            MapleMonster npcmob = MapleLifeFactory.getMonster(mobid);
            npcmob.setOverrideStats(newStats);
            npcmob.setHp(npcmob.getMaxHp());
            npcmob.setMp(npcmob.getMaxMp());
            getPlayer().getMap().spawnMonsterOnGroundBelow(npcmob, spawnPos);
        }
    }

    public String getGMList() {
        return getClient().getChannelServer().getGMList();
    }

    public int getExpRate() {
        return getClient().getChannelServer().getExpRate();
    }

    public int getDropRate() {
        return getClient().getChannelServer().getDropRate();
    }

    public int getBossDropRate() {
        return getClient().getChannelServer().getBossDropRate();
    }

    public int getMesoRate() {
        return getClient().getChannelServer().getMesoRate();
    }

    public boolean removePlayerFromInstance() {
        if (getClient().getPlayer().getEventInstance() != null) {
            getClient().getPlayer().getEventInstance().removePlayer(getClient().getPlayer());
            return true;
        }
        return false;
    }

    public boolean isPlayerInstance() {
        return getClient().getPlayer().getEventInstance() != null;
    }

    public void openDuey() {
        c.sendPacket(MaplePacketCreator.sendDuey((byte) 8, DueyActionHandler.loadItems(c.getPlayer())));
    }

    public void finishAchievement(int id) {
        getPlayer().finishAchievement(id);
    }

    public void changeStat(short slot, int type, short amount) {
        Equip sel = (Equip) c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(slot);
        switch (type) {
            case 0 -> sel.setStr(amount);
            case 1 -> sel.setDex(amount);
            case 2 -> sel.setInt(amount);
            case 3 -> sel.setLuk(amount);
            case 4 -> sel.setHp(amount);
            case 5 -> sel.setMp(amount);
            case 6 -> sel.setWatk(amount);
            case 7 -> sel.setMatk(amount);
            case 8 -> sel.setWdef(amount);
            case 9 -> sel.setMdef(amount);
            case 10 -> sel.setAcc(amount);
            case 11 -> sel.setAvoid(amount);
            case 12 -> sel.setHands(amount);
            case 13 -> sel.setSpeed(amount);
            case 14 -> sel.setJump(amount);
        }
        c.getPlayer().equipChanged();
    }

    public void removeHiredMerchantItem(int id) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("DELETE FROM hiredmerchant WHERE id = ?");
            ps.setInt(1, id);
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            System.err.println("Error removing hired merchant item! " + e);
            e.printStackTrace();
        }
    }

    public long getHiredMerchantMesos() {
        Connection con = DatabaseConnection.getConnection();
        long mesos;
        try {
            PreparedStatement ps = con.prepareStatement("SELECT MerchantMesos FROM characters WHERE id = ?");
            ps.setInt(1, getPlayer().getId());
            ResultSet rs = ps.executeQuery();
            rs.next();
            mesos = rs.getLong("MerchantMesos");
            rs.close();
            ps.close();
        } catch (SQLException se) {
            return 0;
        }
        if (mesos > Integer.MAX_VALUE) {
            mesos = Integer.MAX_VALUE;
        }
        return mesos;
    }

    public void setHiredMerchantMesos(long set) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE characters SET MerchantMesos = ? WHERE id = ?");
            ps.setLong(1, set);
            ps.setInt(2, getPlayer().getId());
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Pair<Integer, IItem>> getStoredMerchantItems() {
        Connection con = DatabaseConnection.getConnection();
        List<Pair<Integer, IItem>> items = new ArrayList<>();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT * FROM hiredmerchant WHERE ownerid = ? AND onSale = false");
            ps.setInt(1, getPlayer().getId());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getInt("type") == 1) {
                    Equip eq = new Equip(rs.getInt("itemid"), (byte) 0, -1);
                    eq.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
                    eq.setLevel((byte) rs.getInt("level"));
                    eq.setStr((short) rs.getInt("str"));
                    eq.setDex((short) rs.getInt("dex"));
                    eq.setInt((short) rs.getInt("int"));
                    eq.setLuk((short) rs.getInt("luk"));
                    eq.setHp((short) rs.getInt("hp"));
                    eq.setMp((short) rs.getInt("mp"));
                    eq.setWatk((short) rs.getInt("watk"));
                    eq.setMatk((short) rs.getInt("matk"));
                    eq.setWdef((short) rs.getInt("wdef"));
                    eq.setMdef((short) rs.getInt("mdef"));
                    eq.setAcc((short) rs.getInt("acc"));
                    eq.setAvoid((short) rs.getInt("avoid"));
                    eq.setHands((short) rs.getInt("hands"));
                    eq.setSpeed((short) rs.getInt("speed"));
                    eq.setJump((short) rs.getInt("jump"));
                    eq.setViciousHammers(rs.getInt("hammer"));
                    eq.setFlag((short) rs.getInt("flag"));
                    eq.setExpiration(rs.getTimestamp("ExpireDate"));
                    eq.setOwner(rs.getString("owner"));
                    items.add(new Pair<>(rs.getInt("id"), eq));
                } else if (rs.getInt("type") == 2) {
                    Item newItem = new Item(rs.getInt("itemid"), (byte) 0, rs.getShort("quantity"), rs.getShort("flag"));
                    newItem.setExpiration(rs.getTimestamp("ExpireDate"));
                    newItem.setOwner(rs.getString("owner"));
                    items.add(new Pair<>(rs.getInt("id"), newItem));
                }
            }
            ps.close();
            rs.close();
        } catch (SQLException se) {
            se.printStackTrace();
            return null;
        }
        return items;
    }

    public int getAverageLevel(int mapid) {
        int count = 0, total = 0;
        for (MapleCharacter player : c.getChannelServer().getMapFactory().getMap(mapid).getCharacters()) {
            total += player.getLevel();
            count++;
        }
        return total / count;
    }

    public void sendCPQMapLists() {
        StringBuilder builder = new StringBuilder();
        builder.append("Pick a field:\\r\\n");
        for (int i = 0; i < 6; i++) {
            if (fieldTaken(i)) {
                if (fieldLobbied(i)) {
                    builder.append("#b#L").append(i).append("#Monster Carnival Field ").append(i + 1).append(" Avg Lvl: ").append(getAverageLevel(980000100 + i * 100)).append("#l\\r\\n");
                }
            } else {
                builder.append("#b#L").append(i).append("#Monster Carnival Field ").append(i + 1).append("#l\\r\\n");
            }
        }
        sendSimple(builder.toString());
    }

    public boolean fieldLobbied(int field) {
        return c.getChannelServer().getMapFactory().getMap(980000100 + field * 100).countCharsOnMap() >= 2;
    }

    public boolean fieldTaken(int field) {
        MapleMapFactory mf = c.getChannelServer().getMapFactory();
        return !mf.getMap(980000100 + field * 100).getCharacters().isEmpty() || !mf.getMap(980000101 + field * 100).getCharacters().isEmpty() || !mf.getMap(980000102 + field * 100).getCharacters().isEmpty();
    }

    public void CPQLobby(int field) {
        try {
            MapleMap map;
            ChannelServer cs = c.getChannelServer();
            map = cs.getMapFactory().getMap(980000100 + 100 * field);
            for (MaplePartyCharacter mpc : c.getPlayer().getParty().getMembers()) {
                MapleCharacter mc;
                mc = cs.getPlayerStorage().getCharacterByName(mpc.getName());
                if (mc != null) {
                    mc.changeMap(map, map.getPortal(0));
                    String msg = "You will now receive challenges from other parties. If you do not accept a challenge in 3 minutes, you will be kicked out.";
                    mc.getClient().sendPacket(MaplePacketCreator.serverNotice(5, msg));
                    mc.getClient().sendPacket(MaplePacketCreator.getClock(3 * 60));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void challengeParty(int field) {
        MapleCharacter leader = null;
        MapleMap map = c.getChannelServer().getMapFactory().getMap(980000100 + 100 * field);
        for (MapleCharacter player : map.getCharacters()) {
            if (player.getParty().getLeader().getId() == player.getId()) {
                leader = player;
                break;
            }
        }
        if (leader != null) {
            if (!leader.isCPQChallenged()) {
                List<MaplePartyCharacter> challengers = new LinkedList<>();
                challengers.addAll(c.getPlayer().getParty().getMembers());
                NPCScriptManager.getInstance().start("cpqchallenge", leader.getClient(), npc, challengers);
            } else {
                sendOk("The other party is currently taking on a different challenge.");
            }
        } else {
            sendOk("Could not find leader!");
        }
    }

    public void startCPQ(final MapleCharacter challenger, int field) {
        try {
            if (challenger != null) {
                if (challenger.getParty() == null) {
                    throw new RuntimeException("ERROR: CPQ Challenger's party was null!");
                }
                for (MaplePartyCharacter mpc : challenger.getParty().getMembers()) {
                    MapleCharacter mc;
                    mc = c.getChannelServer().getPlayerStorage().getCharacterByName(mpc.getName());
                    if (mc != null) {
                        mc.changeMap(c.getPlayer().getMap(), c.getPlayer().getMap().getPortal(0));
                        mc.getClient().sendPacket(MaplePacketCreator.getClock(10));
                    }
                }
            }
            final int mapid = c.getPlayer().getMap().getId() + 1;
            TimerManager.getInstance().schedule(() -> {
                MapleMap map;
                ChannelServer cs = c.getChannelServer();
                map = cs.getMapFactory().getMap(mapid);
                new MapleMonsterCarnival(getPlayer().getParty(), challenger.getParty(), mapid);
                map.broadcastMessage(MaplePacketCreator.serverNotice(5, "The Monster Carnival has begun!"));
            }, 10000);
            mapMessage(5, "The Monster Carnival will begin in 10 seconds!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int partyMembersInMap() {
        int inMap = 0;
        for (MapleCharacter char2 : getPlayer().getMap().getCharacters()) {
            if (char2.getParty() == getPlayer().getParty()) {
                inMap++;
            }
        }
        return inMap;
    }

    public boolean gotoEvent() {
        ChannelServer cserv = c.getChannelServer();
        int level = getPlayer().getLevel();
        if (level >= cserv.level[0] && level <= cserv.level[1] && cserv.eventmap != 0) {
            MapleMap map = cserv.getMapFactory().getMap(cserv.eventmap);
            c.getPlayer().changeMap(map, map.getPortal(0));
            return true;
        }
        return false;
    }

    public boolean partyMemberHasItem(int iid) {
        List<MapleCharacter> lmc = getPartyMembers();
        if (lmc == null) {
            return this.haveItem(iid);
        }
        for (MapleCharacter mc : lmc) {
            if (mc.haveItem(iid, 1, false, false)) {
                return true;
            }
        }
        return false;
    }

    public void partyNotice(String message) {
        List<MapleCharacter> lmc = getPartyMembers();
        if (lmc == null) {
            this.playerMessage(5, message);
        } else {
            for (MapleCharacter mc : lmc) {
                mc.dropMessage(5, message);
            }
        }
    }

    public boolean isMarried() {
        return getPlayer().isMarried() == 1;
    }

    public void spawnMonster(int mobid, int x, int y) {
        getPlayer().getMap().spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(mobid), new Point(x, y));
    }

    public void marry(int ringid) {
        MapleParty party = getPlayer().getParty();
        if (party == null) {
            return;
        }
        if (getPlayer() != party.getPartyMembers().get(0)) {
            return;
        }
        MapleCharacter spouse = party.getPartyMembers().get(1);
        MapleRing.createRing(ringid, getPlayer(), spouse, "Marriage Ring <3");
        spouse.setMarried(1);
        getPlayer().setMarried(1);
        spouse.setPartnerId(getPlayer().getId());
        getPlayer().setPartnerId(spouse.getId());
    }

    public void divorce() {
        getPlayer().setMarried(0);
        MapleCharacter spouse = getPlayer().getPartner();
        if (spouse == null) {
            Connection con = DatabaseConnection.getConnection();
            MapleRing.removeRingFromDb(getPlayer());
            try {
                PreparedStatement ps = con.prepareStatement("SELECT name FROM characters WHERE id = ?");
                ps.setInt(1, getPlayer().getPartnerId());
                ResultSet rs = ps.executeQuery();
                String spouseName = rs.getString("name");
                rs.close();
                ps.close();
                if (c.getChannelServer().getWorldInterface().isConnected(spouseName, false)) {
                    for (ChannelServer cservs : ChannelServer.getAllInstances()) {
                        if (cservs.getPlayerStorage().getCharacterByName(spouseName) != null) {
                            spouse = cservs.getPlayerStorage().getCharacterByName(spouseName);
                            break;
                        }
                    }
                }
                if (spouse == null) {
                    PreparedStatement ps2 = con.prepareStatement("UPDATE characters set married = 0, partnerid = 0 where id = ?");
                    ps2.setInt(1, getPlayer().getPartnerId());
                    ps2.executeUpdate();
                    ps2.close();
                } else {
                    spouse.setMarried(0);
                    spouse.setPartnerId(0);
                    getPlayer().setPartnerId(0);
                }
            } catch (SQLException sql) {
                sql.printStackTrace();
            } catch (RemoteException e) {
                e.printStackTrace();
                c.getChannelServer().reconnectWorld();
            }
        }
        getPlayer().setPartnerId(0);
    }

    public String generatePasscode() {
        StringBuilder passcode = new StringBuilder();
        String[] letters = {"A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z"};
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "0"};
        for (int i = 0; i < 10; i++) {
            if (Randomizer.nextBoolean()) {
                passcode.append(letters[Randomizer.nextInt(letters.length)]);
            } else {
                passcode.append(numbers[Randomizer.nextInt(numbers.length)]);
            }
        }
        setQuestInfo(7061, passcode.toString());

        return passcode.toString();
    }

    public boolean isDojoOccupied() {
        MapleMapFactory mmf = c.getChannelServer().getMapFactory();
        for (int i = 1; i <= 38; i++) {
            String f = "%02d";
            String z = String.format(f, i);
            int mapid = Integer.parseInt("92502" + z + "00");
            MapleMap dojomap = mmf.getMap(mapid);
            if (dojomap.countCharsOnMap() >= 1) {
                return true;
            }
        }
        return false;
    }

    public void cleanUpDojo() {
        MapleMapFactory mmf = c.getChannelServer().getMapFactory();
        for (int i = 1; i <= 38; i++) {
            String f = "%02d";
            String z = String.format(f, i);
            int mapid = Integer.parseInt("92502" + z + "00");
            MapleMap dojomap = mmf.getMap(mapid);
            dojomap.killAllMonsters(false);
            dojomap.disableDojoSpawn();
        }
    }

    public int getDojoPoints() {
        return c.getPlayer().getDojo().getPoints();
    }

    public int getDojoBelt() {
        return c.getPlayer().getDojo().getBelt();
    }

    public void increaseDojoBelt() {
        c.getPlayer().getDojo().setBelt(c.getPlayer().getDojo().getBelt() + 1);
    }

    public String getNPCVar(String name) {
        return MapleLifeFactory.getNPCVar(npc, name);
    }

    public void addNPCVar(String name, String val) {
        MapleLifeFactory.addNPCVar(npc, name, val);
    }

    public boolean canBeUsedAllianceName(String name) {
        if (name.contains(" ") || name.length() > 12) {
            return false;
        }
        try {
            PreparedStatement ps = DatabaseConnection.getConnection().prepareStatement("SELECT name FROM alliances WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ps.close();
                rs.close();
                return false;
            }
            ps.close();
            rs.close();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public void processGachapon(int[] id, boolean remote) {
        int itemId = id[Randomizer.nextInt(id.length - 1)];
        int[] items = {5220000, -1, itemId, 1};
        if (remote) {
            items[0] = 5451000;
        }
        exchange(0, items, true);
        sendNext("You have obtained a #b#t" + itemId + "##k.");
    }

    public void makeRandGachaponItem(int itemId, int town) {
        int randItem = GachaponItems.makeRandGachaponItem(c, itemId, town);
        sendNext("You have obtained a #b#t" + randItem + "##k.");
    }

    public void disbandAlliance(MapleClient c, int allianceId) {
        PreparedStatement ps = null;
        try {
            ps = DatabaseConnection.getConnection().prepareStatement("DELETE FROM `alliances` WHERE id = ?");
            ps.setInt(1, allianceId);
            ps.executeUpdate();
            ps.close();
            c.getChannelServer().getWorldInterface().allianceMessage(c.getPlayer().getGuild().getAllianceId(), MaplePacketCreator.disbandAlliance(allianceId), -1, -1);
            c.getChannelServer().getWorldInterface().disbandAlliance(allianceId);
        } catch (RemoteException r) {
            c.getChannelServer().reconnectWorld();
        } catch (SQLException sqle) {
            sqle.printStackTrace();
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

    public void removeNPC(MapleMap map) {
        map.getNPC(npc).sendDestroyData(c);
    }

    public void gainDonorItem(int itemid, String charName) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        IItem item = ii.getEquipById(itemid);
        Equip eqp = (Equip) item;
        final short stat = 5;
        eqp.setStr(stat);
        eqp.setDex(stat);
        eqp.setInt(stat);
        eqp.setLuk(stat);
        eqp.setJump(stat);
        eqp.setSpeed(stat);
        item = eqp.copy();
        item.setExpiration(new Timestamp(System.currentTimeMillis() + 30 * 86400000L));
        item.setOwner(charName);
        c.sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(c, item, "Donator item created for " + charName, false)));
    }
}