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

import guida.client.SkillCooldown.CancelCooldownAction;
import guida.client.anticheat.CheatTracker;
import guida.database.DatabaseConnection;
import guida.database.DatabaseException;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.net.channel.handler.DueyActionHandler.Actions;
import guida.net.world.MapleMessenger;
import guida.net.world.MapleMessengerCharacter;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.PlayerBuffValueHolder;
import guida.net.world.guild.MapleGuild;
import guida.net.world.guild.MapleGuildCharacter;
import guida.net.world.remote.WorldChannelInterface;
import guida.scripting.event.EventInstanceManager;
import guida.scripting.npc.NPCScriptManager;
import guida.server.MapleAchievements;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.MapleMonsterCarnival;
import guida.server.MaplePortal;
import guida.server.MapleSquad;
import guida.server.MapleSquadType;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MobSkill;
import guida.server.maps.AbstractAnimatedMapleMapObject;
import guida.server.maps.MapleDoor;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapFactory;
import guida.server.maps.MapleMapItem;
import guida.server.maps.MapleMapObject;
import guida.server.maps.MapleMapObjectType;
import guida.server.maps.MapleMist;
import guida.server.maps.MapleSummon;
import guida.server.maps.SavedLocationType;
import guida.server.maps.SummonMovementType;
import guida.server.movement.LifeMovementFragment;
import guida.server.playerinteractions.HiredMerchant;
import guida.server.playerinteractions.IMaplePlayerShop;
import guida.server.playerinteractions.MaplePlayerShopItem;
import guida.server.playerinteractions.MapleShop;
import guida.server.playerinteractions.MapleStorage;
import guida.server.playerinteractions.MapleTrade;
import guida.server.quest.MapleCustomQuest;
import guida.server.quest.MapleQuest;
import guida.tools.FileTimeUtil;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.Randomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Point;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class MapleCharacter extends AbstractAnimatedMapleMapObject implements InventoryContainer {
    public static final double MAX_VIEW_RANGE_SQ = 2500 * 2500;
    private static final Logger logger = LoggerFactory.getLogger(MapleCharacter.class);
    private static final MapleInventoryType[] invTypes = {MapleInventoryType.EQUIPPED, MapleInventoryType.EQUIP, MapleInventoryType.USE, MapleInventoryType.SETUP, MapleInventoryType.ETC, MapleInventoryType.CASH};
    private static int timedFakeID = 0;
    public enum FameStatus {

        OK,
        NOT_TODAY,
        NOT_THIS_MONTH
    }
    private final AtomicInteger exp = new AtomicInteger();
    private final AtomicInteger meso = new AtomicInteger();
    private final int[] savedLocations;
    private final SkillMacro[] skillMacros = new SkillMacro[5];
    private final MapleInventory[] inventory;
    private final Map<Integer, MapleQuestStatus> quests;
    private final Map<Integer, MapleQuestStatus> questRecordsEx;
    private final Set<MapleMonster> controlled = new LinkedHashSet<>();
    private final Set<MapleMapObject> visibleMapObjects = Collections.synchronizedSet(new LinkedHashSet<>());
    private final List<Pair<MapleMap, MapleMapObject>> ownedInteractableObjects = new ArrayList<>();
    private final Map<ISkill, SkillEntry> skills = new LinkedHashMap<>();
    private final Map<MapleBuffStat, MapleBuffStatValueHolder> effects = Collections.synchronizedMap(new EnumMap<>(MapleBuffStat.class));
    private final Map<Integer, MapleKeyBinding> keymap = new LinkedHashMap<>();
    private final MapleDoor[] doors = new MapleDoor[2];
    private final Map<Integer, MapleSummon> summons = new LinkedHashMap<>();
    private final Map<Integer, SkillCooldown> cooldowns = new LinkedHashMap<>();
    // anticheat related information
    private final CheatTracker anticheat;
    // misc information
    private final List<MapleDisease> diseases = Collections.synchronizedList(new ArrayList<>());
    private final List<Integer> finishedAchievements = new ArrayList<>();
    private final Map<Long, MapleStatEffect> buffsToCancel = new HashMap<>();
    private final List<Integer> vipRockMaps = new ArrayList<>(10);
    private final List<Integer> rockMaps = new ArrayList<>(5);
    private final List<MapleRing> crushRings = new ArrayList<>();
    private final List<MapleRing> friendshipRings = new ArrayList<>();
    private final List<MapleRing> marriageRings = new ArrayList<>(2);
    private final List<Pair<Short, IItem>> expiredItems = new ArrayList<>();
    private final List<Pair<Short, IItem>> expiredItemLocks = new ArrayList<>();
    private final List<IItem> inventoryItems = new ArrayList<>();
    private final List<Integer> ignoredItems = new ArrayList<>();
    private final long[] chatSpam = new long[5];
    private final NumberFormat nf = new DecimalFormat("#,###,###,###");
    public SummonMovementType getMovementType;
    private int world;
    private int accountid;
    private int rank;
    private int rankMove;
    private int jobRank;
    private int jobRankMove;
    private String name;
    private int level;
    private int str, dex, luk, int_;
    private int hp, maxhp;
    private int mp, maxmp;
    private int mpApUsed, hpApUsed;
    private int hair, face;
    private int remainingAp, remainingSp;
    private int fame;
    private long lastfametime;
    private List<Integer> lastmonthfameids;
    // local stats represent current stats of the player to avoid expensive operations
    private transient int localmaxhp, localmaxmp;
    private transient int localstr, localdex, localluk, localint_;
    private transient int magic, watk;
    private transient double speedMod, jumpMod;
    private transient int localmaxbasedamage;
    private int id;
    private MapleClient client;
    private MapleMap map;
    private int initialSpawnPoint;
    // mapid is only used when calling getMapId() with map == null, it is not updated when running in channelserver mode
    private int mapid;
    private MapleShop shop = null;
    private IMaplePlayerShop playerShop = null;
    private MapleStorage storage = null;
    private MapleTrade trade = null;
    private MapleSkinColor skinColor = MapleSkinColor.NORMAL;
    private MapleJob job = MapleJob.BEGINNER;
    private int gender;
    private int GMLevel;
    private boolean hidden = false;
    private boolean canDoor = true;
    private boolean isDJ;
    private int chair;
    private int itemEffect;
    private int APQScore;
    private int buffCount = 0, attackCount = 0;
    private MapleParty party;
    private EventInstanceManager eventInstance = null;
    private BuddyList buddylist;
    private ScheduledFuture<?> dragonBloodSchedule;
    private ScheduledFuture<?> mapTimeLimitTask = null;
    //guild related information
    private int guildid;
    private int guildrank, alliancerank;
    private MapleGuildCharacter mgc = null;
    // cash shop related information
    private int gameCardCash = 0, maplePoints = 0;
    private int[] wishlist = new int[10];
    private boolean inCS, inMTS;
    private long timeLimit = 0;
    private long startTime = 0;
    private MapleMessenger messenger = null;
    private int messengerposition = 4;
    private int slots = 0;
    private ScheduledFuture<?> fullnessSchedule, fullnessSchedule_1, fullnessSchedule_2;
    private ScheduledFuture<?> hpDecreaseTask;
    private ScheduledFuture<?> beholderHealingSchedule;
    private ScheduledFuture<?> beholderBuffSchedule;
    private ScheduledFuture<?> berserkSchedule;
    private ScheduledFuture<?> unknownSchedule;
    private boolean berserk = false;
    private String chalktext; // Chalkboard
    private int team;
    private int canTalk;
    private int zakumLvl; // zero means they havent started yet
    //marriage
    private int married;
    private int partnerid;
    private int marriageQuestLevel;
    private List<LifeMovementFragment> lastres;
    // enable/disable smegas - player command
    private boolean smegaEnabled = true;
    private long afkTimer = 0, loggedInTimer = 0;
    private int currentPage = 0, currentType = 0, currentTab = 1;
    private int energybar = 0;
    private ScheduledFuture<?> energyDecrease = null;
    private int hppot = 0, mppot = 0;
    private int bossPoints, bossRepeats;
    private long nextBQ = 0;
    private boolean hasMerchant = false;
    private boolean playerNPC;
    private int battleshipHP;
    private MapleMount mount;
    private boolean banned = false; //Prevent evading GM police with ccing
    private boolean needsParty = false;
    private int needsPartyMinLevel, needsPartyMaxLevel;
    //CPQ
    private boolean CPQChallenged = false;
    private int CP = 0, totalCP = 0;
    private MapleMonsterCarnival monsterCarnival;
    private int CPQRanking = 0;
    private int autoHpPot, autoMpPot;
    private boolean partyInvite;
    private boolean muted;
    private Calendar unmuteTime = null;
    private boolean godmode;
    private boolean questDebug = false;
    private boolean packetLogging;
    private boolean isGMLegit = false;
    private Timestamp lastLevelUpTime = null;
    private boolean whiteText = true;
    private long lastDJTime = 0;
    private boolean isMinerving = false;
    private MonsterBook monsterbook;
    private int bookCover;
    private MapleDojo dojo;
    private boolean hasBeacon = false;
    private int beaconOid = -1;
    private long lastMobKillTime = 0;
    private boolean noEnergyChargeDec = false;
    private Short hammerSlot = null;
    private ScheduledFuture<?> checkForExpiredItems;
    private boolean slotMerging = false;
    private boolean isMakingMerchant = false;
    private boolean passMerchTest = false;
    private boolean isUsingRemoteGachapon = false;
    private boolean tradeRequested = false;
    private long lastDisconnection = 0, lastDeath = 0;
    private MapleRNG rng = null;
    private String blessingChar = "";
    private boolean uiLocked = false;
    private long lastWarpTimestamp = 0;
    private short comboCounter = 0;
    private long lastComboAttack = 0;
    private int autoMapChange = -1;
    private List<MaplePet> pets = null;
    private Runnable confirmationCallback = null;
    private Runnable confirmationFailCallback = null;
    private boolean ccRequired = false;

    private MapleCharacter(boolean channelServ) {
        inventory = new MapleInventory[MapleInventoryType.values().length];
        for (MapleInventoryType type : MapleInventoryType.values()) {
            inventory[type.ordinal()] = new MapleInventory(type, (byte) 96);
        }
        quests = new LinkedHashMap<>();
        questRecordsEx = new LinkedHashMap<>();
        savedLocations = new int[SavedLocationType.values().length];
        for (int i = 0; i < SavedLocationType.values().length; i++) {
            savedLocations[i] = -1;
        }
        setPosition(new Point(0, 0));
        if (channelServ) {
            anticheat = new CheatTracker(this);
            setStance((byte) 0);
            setFoothold((short) 0);
            pets = new ArrayList<>(3);
        } else {
            anticheat = null;
        }
        rng = new MapleRNG();
    }

    public static void setLoggedInState(int charid, int state) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("UPDATE characters SET loggedinstate = ? WHERE id = ?");
        ps.setInt(1, state);
        ps.setInt(2, charid);
        ps.executeUpdate();
        ps.close();
    }

    public static MapleCharacter loadCharFromDB(int charid, MapleClient client, boolean channelserver) throws SQLException {
        MapleCharacter ret = new MapleCharacter(channelserver);
        ret.client = client;
        ret.id = charid;

        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("SELECT * FROM characters WHERE id = ?");
        ps.setInt(1, charid);
        ResultSet rs = ps.executeQuery();
        if (!rs.next()) {
            throw new RuntimeException("Loading the Char Failed (char not found)");
        }
        ret.name = rs.getString("name");
        ret.level = rs.getInt("level");
        ret.fame = rs.getInt("fame");
        ret.str = rs.getInt("str");
        ret.dex = rs.getInt("dex");
        ret.int_ = rs.getInt("int");
        ret.luk = rs.getInt("luk");
        ret.exp.set(rs.getInt("exp"));
        if (rs.getInt("loggedinstate") > 0 && channelserver) {
            rs.close();
            ps.close();
            client.disconnect();
            return null;
        } else {
            setLoggedInState(charid, 1);
        }
        ret.hp = rs.getInt("hp");
        ret.maxhp = rs.getInt("maxhp");
        ret.mp = rs.getInt("mp");
        ret.maxmp = rs.getInt("maxmp");

        ret.hpApUsed = rs.getInt("hpApUsed");
        ret.mpApUsed = rs.getInt("mpApUsed");
        ret.remainingSp = rs.getInt("sp");
        ret.remainingAp = rs.getInt("ap");

        ret.meso.set(rs.getInt("meso"));

        ret.GMLevel = rs.getInt("gm");
        ret.whiteText = ret.GMLevel > 1;

        ret.skinColor = MapleSkinColor.getById(rs.getInt("skincolor"));
        ret.gender = rs.getInt("gender");
        ret.job = MapleJob.getById(rs.getInt("job"));

        ret.canTalk = rs.getInt("cantalk"); //cantalk

        ret.married = rs.getInt("married"); //marriage
        ret.partnerid = rs.getInt("partnerid");
        ret.marriageQuestLevel = rs.getInt("marriagequest");

        ret.zakumLvl = rs.getInt("zakumLvl");

        ret.hair = rs.getInt("hair");
        ret.face = rs.getInt("face");

        ret.accountid = rs.getInt("accountid");

        ret.mapid = rs.getInt("map");
        ret.initialSpawnPoint = rs.getInt("spawnpoint");
        ret.world = rs.getInt("world");

        ret.rank = rs.getInt("rank");
        ret.rankMove = rs.getInt("rankMove");
        ret.jobRank = rs.getInt("jobRank");
        ret.jobRankMove = rs.getInt("jobRankMove");

        ret.guildid = rs.getInt("guildid");
        ret.guildrank = rs.getInt("guildrank");
        if (ret.guildid > 0) {
            ret.mgc = new MapleGuildCharacter(ret);
        }
        ret.alliancerank = rs.getInt("alliancerank");

        int buddyCapacity = rs.getInt("buddyCapacity");
        ret.buddylist = new BuddyList(buddyCapacity);

        ret.autoHpPot = rs.getInt("autoHpPot");
        ret.autoMpPot = rs.getInt("autoMpPot");

        ret.bossPoints = rs.getInt("bosspoints");
        ret.bossRepeats = rs.getInt("bossrepeats");

        ret.nextBQ = rs.getLong("nextBQ");
        ret.muted = rs.getInt("muted") == 1;
        ret.playerNPC = rs.getInt("playerNPC") > 0;
        Calendar c = Calendar.getInstance();
        c.setTime(new Date(rs.getLong("unmutetime")));
        ret.unmuteTime = c;
        ret.isGMLegit = rs.getInt("gmlegit") == 1;
        ret.isDJ = rs.getInt("dj") == 1;
        ret.packetLogging = rs.getInt("packetlogging") == 1;
        ret.lastLevelUpTime = rs.getTimestamp("lastLevelUpTime");
        ret.bookCover = rs.getInt("monsterbookcover");
        ret.monsterbook = new MonsterBook();
        if (channelserver) {
            ret.monsterbook.loadCards(charid);
        }
        ret.dojo = new MapleDojo(rs.getInt("dojopoints"), rs.getInt("dojobelt"));
        ret.hasMerchant = rs.getInt("HasMerchant") > 0;
        ret.lastDeath = rs.getLong("lastDeath");
        ret.lastDisconnection = rs.getLong("lastDC");
        if (channelserver) {
            if ((ret.level == 200 || ret.job.isA(MapleJob.NOBLESSE) && ret.level == 120) && ret.exp.get() != 0) {
                ret.exp.set(0);
            }
            ChannelServer cserv = client.getChannelServer();
            MapleMapFactory mapFactory = cserv.getMapFactory();
            ret.map = mapFactory.getMap(ret.mapid);
            if (ret.map == null) //char is on a map that doesn't exist warp it to henesys
            {
                ret.map = mapFactory.getMap(100000000);
            }
            int rMap = ret.map.getForcedReturnId();
            if (ret.mapid != 0 && rMap != 999999999 && ret.GMLevel <= 1) {
                boolean inSquad = false;
                MapleSquadType[] types = {MapleSquadType.HORNTAIL, MapleSquadType.ZAKUM};
                for (MapleSquadType type : types) {
                    MapleSquad squad = cserv.getMapleSquad(type);
                    if (squad != null) {
                        if (squad.isDisconnected(ret.id) && squad.canBeReWarped(ret.id, type)) {
                            if (ret.lastDisconnection + 180000 < System.currentTimeMillis()) {
                                continue;
                            }
                            squad.addRewarp(ret.id);
                            inSquad = true;
                            break;
                        }
                    }
                }
                if (!inSquad) {
                    ret.map = mapFactory.getMap(rMap);
                }
            }
            if (ret.map.isDojoMap() || ret.map.isDojoRestMap()) {
                ret.map = mapFactory.getMap(925020001);
            }
            MaplePortal portal = ret.map.getPortal(ret.initialSpawnPoint);
            if (portal == null) {
                portal = ret.map.getPortal(0); // char is on a spawnpoint that doesn't exist - select the first spawnpoint instead
                ret.initialSpawnPoint = 0;
            }
            ret.setPosition(portal.getPosition());

            int partyid = rs.getInt("party");
            if (partyid >= 0) {
                try {
                    MapleParty party = client.getChannelServer().getWorldInterface().getParty(partyid);
                    if (party != null && party.getMemberById(ret.id) != null) {
                        ret.party = party;
                    }
                } catch (RemoteException e) {
                    client.getChannelServer().reconnectWorld();
                }
            }

            int messengerid = rs.getInt("messengerid");
            int position = rs.getInt("messengerposition");
            if (messengerid > 0 && position < 4 && position > -1) {
                try {
                    WorldChannelInterface wci = client.getChannelServer().getWorldInterface();
                    MapleMessenger messenger = wci.getMessenger(messengerid);
                    if (messenger != null) {
                        ret.messenger = messenger;
                        ret.messengerposition = position;
                    }
                } catch (RemoteException e) {
                    client.getChannelServer().reconnectWorld();
                }
            }
        }

        rs.close();
        ps.close();

        ps = con.prepareStatement("SELECT * FROM inventory_eqp WHERE CharacterID = ? AND Position < 1");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        while (rs.next()) {
            int itemid = rs.getInt("ItemID");
            int ringId = rs.getInt("RingID");
            if (channelserver && ringId != -1) {
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                MapleRing ring = MapleRing.loadFromDb(ringId);
                if (ii.isCrushRing(itemid)) {
                    if (ring != null) {
                        ret.crushRings.add(ring);
                    }
                } else if (ii.isFriendshipRing(itemid)) {
                    if (ring != null) {
                        ret.friendshipRings.add(ring);
                    }
                } else if (ii.isWeddingRing(itemid)) {
                    if (ring != null) {
                        ret.marriageRings.add(ring);
                    }
                }
            }
            Equip equip = new Equip(itemid, rs.getShort("Position"), ringId);
            equip.setStr(rs.getShort("STR"));
            equip.setDex(rs.getShort("DEX"));
            equip.setInt(rs.getShort("INT"));
            equip.setLuk(rs.getShort("LUK"));
            equip.setHp(rs.getShort("MaxHP"));
            equip.setMp(rs.getShort("MaxMP"));
            equip.setWatk(rs.getShort("PAD"));
            equip.setMatk(rs.getShort("MAD"));
            equip.setWdef(rs.getShort("PDD"));
            equip.setMdef(rs.getShort("MDD"));
            equip.setAcc(rs.getShort("ACC"));
            equip.setAvoid(rs.getShort("EVA"));
            equip.setHands(rs.getShort("Hands"));
            equip.setSpeed(rs.getShort("Speed"));
            equip.setJump(rs.getShort("Jump"));
            equip.setViciousHammers(rs.getByte("ViciousHammers"));
            equip.setLevel(rs.getByte("Level"));
            equip.setUpgradeSlots(rs.getByte("RemainingSlots"));
            equip.setExpiration(rs.getTimestamp("ExpireDate"));
            equip.setOwner(rs.getString("Owner"));
            equip.setFlag(rs.getByte("Flag"));
            if (rs.getByte("IsGM") == 1) {
                equip.setGMFlag();
            }
            ret.getInventory(MapleInventoryType.EQUIPPED).addFromDB(equip);
        }
        rs.close();
        ps.close();

        if (channelserver) {
            ps = con.prepareStatement("SELECT * FROM inventory_eqp WHERE CharacterID = ? AND Position > 0");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                int itemid = rs.getInt("ItemID");
                int ringId = rs.getInt("RingID");
                if (ringId != -1) {
                    MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                    MapleRing ring = MapleRing.loadFromDb(ringId);
                    if (ii.isCrushRing(itemid)) {
                        if (ring != null) {
                            ret.crushRings.add(ring);
                        }
                    } else if (ii.isFriendshipRing(itemid)) {
                        if (ring != null) {
                            ret.friendshipRings.add(ring);
                        }
                    } else if (ii.isWeddingRing(itemid)) {
                        if (ring != null) {
                            ret.marriageRings.add(ring);
                        }
                    }
                }
                Equip equip = new Equip(itemid, (byte) rs.getInt("Position"), ringId);
                equip.setStr(rs.getShort("STR"));
                equip.setDex(rs.getShort("DEX"));
                equip.setInt(rs.getShort("INT"));
                equip.setLuk(rs.getShort("LUK"));
                equip.setHp(rs.getShort("MaxHP"));
                equip.setMp(rs.getShort("MaxMP"));
                equip.setWatk(rs.getShort("PAD"));
                equip.setMatk(rs.getShort("MAD"));
                equip.setWdef(rs.getShort("PDD"));
                equip.setMdef(rs.getShort("MDD"));
                equip.setAcc(rs.getShort("ACC"));
                equip.setAvoid(rs.getShort("EVA"));
                equip.setHands(rs.getShort("Hands"));
                equip.setSpeed(rs.getShort("Speed"));
                equip.setJump(rs.getShort("Jump"));
                equip.setViciousHammers(rs.getByte("ViciousHammers"));
                equip.setLevel(rs.getByte("Level"));
                equip.setUpgradeSlots(rs.getByte("RemainingSlots"));
                equip.setExpiration(rs.getTimestamp("ExpireDate"));
                equip.setOwner(rs.getString("Owner"));
                equip.setFlag(rs.getByte("Flag"));
                if (rs.getByte("IsGM") == 1) {
                    equip.setGMFlag();
                }
                ret.getInventory(MapleInventoryType.EQUIP).addFromDB(equip);
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT * FROM inventory_use WHERE CharacterID = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                Item item = new Item(rs.getInt("ItemID"), rs.getByte("Position"), rs.getShort("Quantity"), rs.getShort("Flag"));
                item.setOwner(rs.getString("Owner"));
                item.setExpiration(rs.getTimestamp("ExpireDate"));
                if (rs.getByte("IsGM") == 1) {
                    item.setGMFlag();
                }
                ret.getInventory(MapleInventoryType.USE).addFromDB(item);
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT * FROM inventory_setup WHERE CharacterID = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                Item item = new Item(rs.getInt("ItemID"), rs.getByte("Position"), rs.getShort("Quantity"), rs.getShort("Flag"));
                item.setOwner(rs.getString("Owner"));
                item.setExpiration(rs.getTimestamp("ExpireDate"));
                if (rs.getByte("IsGM") == 1) {
                    item.setGMFlag();
                }
                ret.getInventory(MapleInventoryType.SETUP).addFromDB(item);
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT * FROM inventory_etc WHERE CharacterID = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                Item item = new Item(rs.getInt("ItemID"), rs.getByte("Position"), rs.getShort("Quantity"), rs.getShort("Flag"));
                item.setOwner(rs.getString("Owner"));
                item.setExpiration(rs.getTimestamp("ExpireDate"));
                if (rs.getByte("IsGM") == 1) {
                    item.setGMFlag();
                }
                ret.getInventory(MapleInventoryType.ETC).addFromDB(item);
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT * FROM inventory_cash WHERE CharacterID = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                Item item = new Item(rs.getInt("ItemID"), rs.getByte("Position"), rs.getShort("Quantity"), rs.getShort("Flag"));
                item.setOwner(rs.getString("Owner"));
                item.setExpiration(rs.getTimestamp("ExpireDate"));
                if (rs.getByte("IsGM") == 1) {
                    item.setGMFlag();
                }
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                if (ii.isPet(item.getItemId())) {
                    MaplePet pet = MaplePet.loadFromDb(item.getItemId(), rs.getInt("PetID"));
                    if (pet == null) {
                        pet = MaplePet.createPet(charid, item.getItemId());
                    }
                    item.setPet(pet);
                    if (pet.getIndex() > 0) {
                        ret.pets.add(pet);
                    }
                }
                ret.getInventory(MapleInventoryType.CASH).addFromDB(item);

            }
            rs.close();
            ps.close();

            if (!ret.pets.isEmpty()) {
                ret.pets.sort((pet1, pet2) -> {
                    final int index1 = pet1.getIndex();
                    final int index2 = pet2.getIndex();
                    return Integer.compare(index1, index2);
                });

                for (MaplePet pet : ret.pets) {
                    ret.updatePetEquips(pet);
                    ret.startFullnessSchedule(PetDataFactory.getHunger(pet.getItemId()), pet, ret.getPetIndex(pet));
                }
            }

            ps = con.prepareStatement("SELECT name, maplePoints, gameCardCash FROM accounts WHERE id = ?");
            ps.setInt(1, ret.accountid);
            rs = ps.executeQuery();
            if (rs.next()) {
                ret.client.setAccountName(rs.getString("name"));
                ret.maplePoints = rs.getInt("maplePoints");
                ret.gameCardCash = rs.getInt("gameCardCash");
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT * FROM queststatus WHERE characterid = ? AND status > 0");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                int questId = rs.getInt("quest");
                MapleQuestStatus status = new MapleQuestStatus(questId, MapleQuestStatus.Status.getById(rs.getInt("status")), rs.getString("questRecord"));
                long cTime = rs.getLong("time");
                if (cTime > -1) {
                    status.setCompletionTime(cTime * 1000);
                }
                ret.quests.put(questId, status);
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT * FROM queststatusex WHERE characterid = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                int questId = rs.getInt("quest");
                MapleQuestStatus status = new MapleQuestStatus(questId, MapleQuestStatus.Status.getById(rs.getInt("status")), rs.getString("questRecord"));
                long cTime = rs.getLong("time");
                if (cTime > -1) {
                    status.setCompletionTime(cTime * 1000);
                }
                ret.questRecordsEx.put(questId, status);
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT skillid,skilllevel,masterlevel FROM skills WHERE characterid = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                final ISkill skill = SkillFactory.getSkill(rs.getInt("skillid"));
                if (skill != null) {
                    ret.skills.put(skill, new SkillEntry(rs.getInt("skilllevel"), rs.getInt("masterlevel")));
                }
            }
            if (ret.job.getId() == 2000) {
                ret.skills.put(SkillFactory.getSkill(20000012), new SkillEntry(0, 0));
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT skillid,starttime,length FROM cooldowns WHERE characterid = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getLong("length") + rs.getLong("starttime") - System.currentTimeMillis() <= 0) {
                    continue;
                }
                ret.giveCoolDowns(rs.getInt("skillid"), rs.getLong("starttime"), rs.getLong("length"));
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT id, name, level FROM characters WHERE accountid = ? AND id <> ? ORDER BY `level` DESC");
            ps.setInt(1, ret.accountid);
            ps.setInt(2, ret.id);
            rs = ps.executeQuery();
            if (rs.next()) {
                ret.blessingChar = rs.getString("name");
                int level = rs.getInt("level");
                int skillId = 12;
                int jobId = ret.job.getId();
                if (jobId >= 1000 && jobId < 2000) {
                    skillId = 10000012;
                } else if (jobId >= 2100 && jobId < 2200) {
                    skillId = 20000012;
                } else if (jobId >= 2200 && jobId < 2300) {
                    skillId = 20010012;
                }
                ret.skills.put(SkillFactory.getSkill(skillId), new SkillEntry(level / 10, -1));
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT * FROM skillmacros WHERE characterid = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                int skill1 = rs.getInt("skill1");
                int skill2 = rs.getInt("skill2");
                int skill3 = rs.getInt("skill3");
                String name = rs.getString("name");
                int shout = rs.getInt("shout");
                int position = rs.getInt("position");
                SkillMacro macro = new SkillMacro(skill1, skill2, skill3, name, shout, position);
                ret.skillMacros[position] = macro;
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT `key`,`type`,`action` FROM keymap WHERE characterid = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                int key = rs.getInt("key");
                int type = rs.getInt("type");
                int action = rs.getInt("action");
                ret.keymap.put(key, new MapleKeyBinding(type, action));
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT `locationtype`,`map` FROM savedlocations WHERE characterid = ?");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                String locationType = rs.getString("locationtype");
                int mapid = rs.getInt("map");
                ret.savedLocations[SavedLocationType.valueOf(locationType).ordinal()] = mapid;
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT mapId, type FROM telerockmaps WHERE characterId = ? ORDER BY type");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getInt("type") == 0) {
                    ret.rockMaps.add(rs.getInt("mapid"));
                } else {
                    ret.vipRockMaps.add(rs.getInt("mapid"));
                }
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT `characterid_to`,`when` FROM famelog WHERE characterid = ? AND DATEDIFF(NOW(),`when`) < 30");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            ret.lastfametime = 0;
            ret.lastmonthfameids = new ArrayList<>(31);
            while (rs.next()) {
                ret.lastfametime = Math.max(ret.lastfametime, rs.getTimestamp("when").getTime());
                ret.lastmonthfameids.add(rs.getInt("characterid_to"));
            }
            rs.close();
            ps.close();

            ret.buddylist.loadFromDb(charid);
            ret.storage = MapleStorage.loadOrCreateFromDB(ret.accountid);

            ps = con.prepareStatement("SELECT sn FROM wishlist WHERE charid = ? LIMIT 10");
            ps.setInt(1, charid);
            rs = ps.executeQuery();
            int wishlistEntry = 0;
            while (rs.next()) {
                ret.wishlist[wishlistEntry] = rs.getInt("sn");
                wishlistEntry++;
            }
            rs.close();
            ps.close();

            ps = con.prepareStatement("SELECT achievementid FROM achievements WHERE accountid = ?");
            ps.setInt(1, ret.accountid);
            rs = ps.executeQuery();
            while (rs.next()) {
                ret.finishedAchievements.add(rs.getInt("achievementid"));
            }
            rs.close();
            ps.close();

            if (ret.job.equals(MapleJob.CORSAIR)) {
                ISkill ship = SkillFactory.getSkill(5221006);
                ret.battleshipHP = ret.getSkillLevel(ship) * 4000 + (ret.level - 120) * 2000;
            }
            ret.loggedInTimer = System.currentTimeMillis();
        }
        ret.recalcLocalStats(channelserver);
        ret.silentEnforceMaxHpMp();

        return ret;
    }

    public static MapleCharacter getDefault(MapleClient client) {
        MapleCharacter ret = getDefault(client, 0, false);
        ret.id = 26023;
        return ret;
    }

    public static MapleCharacter getDefault(MapleClient client, int job) {
        return getDefault(client, job, false);
    }

    public static MapleCharacter getDefault(MapleClient client, int job, boolean cs) {
        MapleCharacter ret = new MapleCharacter(cs);
        ret.client = client;
        ret.hp = 50;
        ret.maxhp = 50;
        ret.mp = 5;
        ret.maxmp = 5;
        ret.map = null;
        ret.exp.set(0);
        ret.GMLevel = 0;
        /*case 3:
				ret.job = MapleJob.EVAN;
				break;*/
        switch (job) {
            case 0 -> ret.job = MapleJob.NOBLESSE;
            case 2 -> ret.job = MapleJob.LEGEND;
            default -> ret.job = MapleJob.BEGINNER;
        }
        ret.meso.set(0);
        ret.level = 1;
        ret.accountid = client.getAccID();
        ret.buddylist = new BuddyList(20);
        ret.CP = 0;
        ret.totalCP = 0;
        ret.team = -1;
        ret.packetLogging = false;
        ret.isDJ = false;
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT name, maplePoints, gameCardCash FROM accounts WHERE id = ?");
            ps.setInt(1, ret.accountid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                ret.client.setAccountName(rs.getString("name"));
                ret.maplePoints = rs.getInt("maplePoints");
                ret.gameCardCash = rs.getInt("gameCardCash");
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        ret.inCS = false;
        ret.inMTS = false;
        ret.bookCover = 0;
        ret.APQScore = 0;
        ret.alliancerank = 5;

        ret.keymap.put(2, new MapleKeyBinding(4, 10));
        ret.keymap.put(3, new MapleKeyBinding(4, 12));
        ret.keymap.put(4, new MapleKeyBinding(4, 13));
        ret.keymap.put(5, new MapleKeyBinding(4, 18));
        ret.keymap.put(6, new MapleKeyBinding(4, 24));
        ret.keymap.put(7, new MapleKeyBinding(4, 21));
        ret.keymap.put(16, new MapleKeyBinding(4, 8));
        ret.keymap.put(17, new MapleKeyBinding(4, 5));
        ret.keymap.put(18, new MapleKeyBinding(4, 0));
        ret.keymap.put(19, new MapleKeyBinding(4, 4));
        ret.keymap.put(23, new MapleKeyBinding(4, 1));
        ret.keymap.put(24, new MapleKeyBinding(4, 25));
        ret.keymap.put(25, new MapleKeyBinding(4, 19));
        ret.keymap.put(26, new MapleKeyBinding(4, 14));
        ret.keymap.put(27, new MapleKeyBinding(4, 15));
        ret.keymap.put(29, new MapleKeyBinding(5, 52));
        ret.keymap.put(31, new MapleKeyBinding(4, 2));
        ret.keymap.put(33, new MapleKeyBinding(4, 26));
        ret.keymap.put(34, new MapleKeyBinding(4, 17));
        ret.keymap.put(35, new MapleKeyBinding(4, 11));
        ret.keymap.put(37, new MapleKeyBinding(4, 3));
        ret.keymap.put(38, new MapleKeyBinding(4, 20));
        ret.keymap.put(39, new MapleKeyBinding(4, 27));
        ret.keymap.put(40, new MapleKeyBinding(4, 16));
        ret.keymap.put(41, new MapleKeyBinding(4, 23));
        ret.keymap.put(43, new MapleKeyBinding(4, 9));
        ret.keymap.put(44, new MapleKeyBinding(5, 50));
        ret.keymap.put(45, new MapleKeyBinding(5, 51));
        ret.keymap.put(46, new MapleKeyBinding(4, 6));
        ret.keymap.put(48, new MapleKeyBinding(4, 22));
        ret.keymap.put(50, new MapleKeyBinding(4, 7));
        ret.keymap.put(56, new MapleKeyBinding(5, 53));
        ret.keymap.put(57, new MapleKeyBinding(5, 54));
        ret.keymap.put(59, new MapleKeyBinding(6, 100));
        ret.keymap.put(60, new MapleKeyBinding(6, 101));
        ret.keymap.put(61, new MapleKeyBinding(6, 102));
        ret.keymap.put(62, new MapleKeyBinding(6, 103));
        ret.keymap.put(63, new MapleKeyBinding(6, 104));
        ret.keymap.put(64, new MapleKeyBinding(6, 105));
        ret.keymap.put(65, new MapleKeyBinding(6, 106));

        ret.recalcLocalStats(false);

        return ret;
    }

    public static int getIdByName(String name, int world) {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps;
        try {
            ps = con.prepareStatement("SELECT id FROM characters WHERE name = ? AND world = ?");
            ps.setString(1, name);
            ps.setInt(2, world);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                ps.close();
                return -1;
            }
            int id = rs.getInt("id");
            ps.close();
            return id;
        } catch (SQLException e) {
            logger.error("ERROR", e);
        }
        return -1;
    }

    private static int rand(int lbound, int ubound) {
        return (int) (Math.random() * (ubound - lbound + 1) + lbound);
    }

    public static boolean tempban(String reason, Calendar duration, int greason, int accountid) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET tempban = ?, banreason = ?, greason = ? WHERE id = ?");
            Timestamp TS = new Timestamp(duration.getTimeInMillis());
            ps.setTimestamp(1, TS);
            ps.setString(2, reason);
            ps.setInt(3, greason);
            ps.setInt(4, accountid);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (SQLException ex) {
            logger.error("Error while tempbanning", ex);
        }
        return false;
    }

    public static int getGMLevelByCharName(String name) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("SELECT gm FROM characters WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            int gm = 0;
            if (rs.next()) {
                gm = rs.getInt("gm");
            }
            rs.close();
            ps.close();
            return gm;
        } catch (SQLException ex) {
            logger.error("Error while retrieving GM Level", ex);
        }
        return 0;
    }

    public static boolean ban(String id, String reason, boolean account) {
        return ban(id, reason, account, false);
    }

    public static boolean ban(String id, String reason, boolean account, boolean isip) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps;
            if (isip) {
                ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
                ps.setString(1, id);
                ps.executeUpdate();
                ps.close();
                return true;
            }
            if (account) {
                ps = con.prepareStatement("SELECT id FROM accounts WHERE name = ?");
            } else {
                ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
            }
            boolean ret = false;
            ps.setString(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                int accountId = rs.getInt(1);
                PreparedStatement psb = con.prepareStatement("UPDATE accounts SET banned = 1, banreason = ? WHERE id = ?");
                psb.setString(1, reason);
                psb.setInt(2, accountId);
                psb.executeUpdate();
                psb.close();

                psb = con.prepareStatement("SELECT ip FROM iplog WHERE accountid = ? ORDER by login DESC LIMIT 1");
                psb.setInt(1, accountId);
                ResultSet rsb = psb.executeQuery();
                rsb.next();
                String to = "/" + rsb.getString("ip");
                rsb.close();
                psb.close();

                psb = con.prepareStatement("SELECT ip FROM ipbans WHERE ip = ?");
                psb.setString(1, to);
                rsb = psb.executeQuery();
                if (!rsb.next()) {
                    PreparedStatement psc = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
                    psc.setString(1, to);
                    psc.executeUpdate();
                    psc.close();
                }
                rsb.close();
                psb.close();

                psb = con.prepareStatement("SELECT macs FROM accounts WHERE id = ?");
                psb.setInt(1, accountId);
                rsb = psb.executeQuery();
                rsb.next();
                String macAddress = rsb.getString("macs");
                if (!macAddress.matches("")) {
                    String[] macs = macAddress.split(", ");
                    for (String mac : macs) {
                        PreparedStatement psc = con.prepareStatement("SELECT mac FROM macbans WHERE mac = ?");
                        psc.setString(1, mac);
                        ResultSet rsc = psc.executeQuery();
                        if (!rsc.next()) {
                            PreparedStatement psd = con.prepareStatement("INSERT INTO macbans (mac) VALUES (?)");
                            psd.setString(1, mac);
                            psd.executeUpdate();
                            psd.close();
                        }
                        rsc.close();
                        psc.close();
                    }
                }
                rsb.close();
                psb.close();
                ret = true;
            }
            rs.close();
            ps.close();
            return ret;
        } catch (SQLException ex) {
            logger.error("Error while banning", ex);
        }
        return false;
    }

    public static int getAccIdFromCharName(String name) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                ps.close();
                return -1;
            }
            int id_ = rs.getInt("accountid");
            ps.close();
            return id_;
        } catch (SQLException e) {
            logger.error("ERROR", e);
        }
        return -1;
    }

    public static int getAccountIdByName(String name) {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps;
        try {
            ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                ps.close();
                return -1;
            }
            int id = rs.getInt("accountid");
            ps.close();
            return id;
        } catch (SQLException e) {
            logger.error("ERROR", e);
        }
        return -1;
    }

    public void dropMessage(String message) {
        dropMessage(isGM() ? 6 : 5, message);
    }

    public void dropMessage(int type, String message) {
        if (message.length() > 0) {
            client.sendPacket(MaplePacketCreator.serverNotice(type, message));
        }
    }

    public void saveToDB(boolean update) {
        saveToDB(update, false);
    }

    public void saveToDB(boolean update, boolean logout) {
        Connection con = DatabaseConnection.getConnection();
        try {
            // clients should not be able to logger back before their old state is saved (see MapleClient#getLoginState) so we are save to switch to a very low isolation level here
            con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
            // connections are thread local now, no need to synchronize anymore =)
            con.setAutoCommit(false);
            PreparedStatement ps;
            if (update) {
                ps = con.prepareStatement("UPDATE characters SET level = ?, fame = ?, str = ?, dex = ?, luk = ?, `int` = ?, exp = ?, hp = ?, mp = ?, maxhp = ?, maxmp = ?, sp = ?, ap = ?, skincolor = ?, gender = ?, job = ?, hair = ?, face = ?, map = ?, meso = ?, hpApUsed = ?, mpApUsed = ?, spawnpoint = ?, party = ?, buddyCapacity = ?, autoHpPot = ?, autoMpPot = ?, messengerid = ?, messengerposition = ?, married = ?, partnerid = ?, cantalk = ?, zakumlvl = ?, marriagequest = ?, bosspoints = ?, bossrepeats = ?, nextBQ = ?, playerNPC = ?, alliancerank = ?, muted = ?, unmutetime = ?, packetlogging = ?, gmlegit = ?, dj = ?, monsterbookcover = ?, dojopoints = ?, dojobelt = ?, lastLevelUpTime = ?, lastDC = ?, lastDeath = ? WHERE id = ?");
            } else
            // 41 inserts
            {
                ps = con.prepareStatement("INSERT INTO characters (level, fame, str, dex, luk, `int`, exp, hp, mp, maxhp, maxmp, sp, ap, gm, skincolor, gender, job, hair, face, map, meso, hpApUsed, mpApUsed, spawnpoint, party, buddyCapacity, autoHpPot, autoMpPot, messengerid, messengerposition, married, partnerid, cantalk, zakumlvl, marriagequest, bosspoints, bossrepeats, nextBQ, playerNPC, alliancerank, muted, unmutetime, packetlogging, gmlegit, dj, monsterbookcover, dojopoints, dojobelt, accountid, name, world) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS);
            }
            ps.setInt(1, level);
            ps.setInt(2, fame);
            ps.setInt(3, str);
            ps.setInt(4, dex);
            ps.setInt(5, luk);
            ps.setInt(6, int_);
            int xp = exp.get();
            ps.setInt(7, Math.max(xp, 0));
            ps.setInt(8, hp);
            ps.setInt(9, mp);
            ps.setInt(10, maxhp);
            ps.setInt(11, maxmp);
            ps.setInt(12, remainingSp);
            ps.setInt(13, remainingAp);
            ps.setInt(14, skinColor.getId());
            ps.setInt(15, gender);
            ps.setInt(16, job.getId());
            ps.setInt(17, hair);
            ps.setInt(18, face);
            if (map == null) {
                int mapId = switch (job.getId()) {
                    case 1000 -> 130030000;
                    case 2001 -> 900010000;
                    case 2000 -> 914000000;
                    default -> 0;
                };
                ps.setInt(19, mapId);
            } else {
                ps.setInt(19, map.getId());
            }
            ps.setInt(20, meso.get());
            ps.setInt(21, hpApUsed);
            ps.setInt(22, mpApUsed);
            if (map == null || map.getId() == 610020000 || map.getId() == 610020001) {
                ps.setInt(23, 0);
            } else {
                MaplePortal closest = map.findClosestSpawnPoint(getPosition());
                ps.setInt(23, closest != null ? closest.getId() : 0);
            }
            ps.setInt(24, party != null ? party.getId() : -1);
            ps.setInt(25, buddylist.getCapacity());
            ps.setInt(26, autoHpPot != 0 && getItemQuantity(autoHpPot) >= 1 ? autoHpPot : 0);
            ps.setInt(27, autoMpPot != 0 && getItemQuantity(autoMpPot) >= 1 ? autoMpPot : 0);
            if (messenger != null) {
                ps.setInt(28, messenger.getId());
                ps.setInt(29, messengerposition);
            } else {
                ps.setInt(28, 0);
                ps.setInt(29, 4);
            }

            ps.setInt(30, married);
            ps.setInt(31, partnerid);
            ps.setInt(32, canTalk);
            ps.setInt(33, Math.min(zakumLvl, 2)); // Don't let zakumLevel exceed three
            ps.setInt(34, marriageQuestLevel);
            ps.setInt(35, bossPoints);
            ps.setInt(36, bossRepeats);
            ps.setLong(37, nextBQ);
            ps.setByte(38, (byte) (playerNPC ? 1 : 0));
            ps.setInt(39, alliancerank);
            ps.setByte(40, (byte) (muted ? 1 : 0));
            ps.setLong(41, unmuteTime == null ? 0 : unmuteTime.getTimeInMillis());
            ps.setByte(42, (byte) (packetLogging ? 1 : 0));
            ps.setByte(43, (byte) (isGMLegit ? 1 : 0));
            ps.setByte(44, (byte) (isDJ ? 1 : 0));
            ps.setInt(45, bookCover);
            if (dojo != null) {
                ps.setInt(46, dojo.getPoints());
                ps.setInt(47, dojo.getBelt());
            } else {
                ps.setInt(46, 0);
                ps.setInt(47, 0);
            }
            if (update) {
                ps.setTimestamp(48, lastLevelUpTime);
                ps.setLong(49, lastDisconnection);
                ps.setLong(50, lastDeath);
                ps.setInt(51, id);
            } else {
                ps.setInt(48, accountid);
                ps.setString(49, name);
                ps.setInt(50, world); // TODO store world somewhere ;)
            }
            int updateRows = ps.executeUpdate();
            if (!update) {
                ResultSet rs = ps.getGeneratedKeys();
                if (rs.next()) {
                    id = rs.getInt(1);
                } else {
                    throw new DatabaseException("Inserting char failed.");
                }
            } else if (updateRows < 1) {
                throw new DatabaseException("Character not in database (" + id + ")");
            }
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM telerockmaps WHERE characterId = ?");
            ps = con.prepareStatement("INSERT into telerockmaps (characterId, mapId, type) VALUES (?, ?, ?)");
            ps.setInt(1, id);
            for (int mapId : rockMaps) {
                ps.setInt(2, mapId);
                ps.setInt(3, 0);
                ps.addBatch();
            }
            for (int mapId : vipRockMaps) {
                ps.setInt(2, mapId);
                ps.setInt(3, 1);
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM skillmacros WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO skillmacros (characterid, skill1, skill2, skill3, name, shout, position) VALUES (?, ?, ?, ?, ?, ?, ?)");
            for (int i = 0; i < 5; i++) {
                SkillMacro macro = skillMacros[i];
                if (macro != null) {
                    ps.setInt(1, id);
                    ps.setInt(2, macro.getSkill1());
                    ps.setInt(3, macro.getSkill2());
                    ps.setInt(4, macro.getSkill3());
                    ps.setString(5, macro.getName());
                    ps.setInt(6, macro.getShout());
                    ps.setInt(7, i);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM inventory_eqp WHERE CharacterID = ?");
            ps = con.prepareStatement("INSERT INTO inventory_eqp (CharacterID, ItemID, Position, STR, DEX, `INT`, LUK, MaxHP, MaxMP, PAD, MAD, PDD, MDD, ACC, EVA, Hands, Speed, Jump, ViciousHammers, Level, RemainingSlots, ExpireDate, Owner, Flag, RingID, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : getInventory(MapleInventoryType.EQUIPPED).list()) {
                if (logout && MapleItemInformationProvider.getInstance().expiresOnLogOut(item.getItemId())) {
                    continue;
                }
                IEquip equip = (IEquip) item;
                ps.setInt(2, item.getItemId());
                ps.setShort(3, item.getPosition());
                ps.setShort(4, equip.getStr());
                ps.setShort(5, equip.getDex());
                ps.setShort(6, equip.getInt());
                ps.setShort(7, equip.getLuk());
                ps.setShort(8, equip.getHp());
                ps.setShort(9, equip.getMp());
                ps.setShort(10, equip.getWatk());
                ps.setShort(11, equip.getMatk());
                ps.setShort(12, equip.getWdef());
                ps.setShort(13, equip.getMdef());
                ps.setShort(14, equip.getAcc());
                ps.setShort(15, equip.getAvoid());
                ps.setShort(16, equip.getHands());
                ps.setShort(17, equip.getSpeed());
                ps.setShort(18, equip.getJump());
                ps.setByte(19, (byte) equip.getViciousHammers());
                ps.setByte(20, equip.getLevel());
                ps.setByte(21, equip.getUpgradeSlots());
                ps.setTimestamp(22, item.getExpiration());
                ps.setString(23, item.getOwner());
                ps.setByte(24, (byte) item.getFlag());
                ps.setInt(25, equip.getRingId());
                ps.setByte(26, (byte) (equip.isByGM() ? 1 : 0));
                ps.addBatch();
            }
            ps.executeBatch();

            for (IItem item : getInventory(MapleInventoryType.EQUIP).list()) {
                IEquip equip = (IEquip) item;
                ps.setInt(2, item.getItemId());
                ps.setShort(3, item.getPosition());
                ps.setShort(4, equip.getStr());
                ps.setShort(5, equip.getDex());
                ps.setShort(6, equip.getInt());
                ps.setShort(7, equip.getLuk());
                ps.setShort(8, equip.getHp());
                ps.setShort(9, equip.getMp());
                ps.setShort(10, equip.getWatk());
                ps.setShort(11, equip.getMatk());
                ps.setShort(12, equip.getWdef());
                ps.setShort(13, equip.getMdef());
                ps.setShort(14, equip.getAcc());
                ps.setShort(15, equip.getAvoid());
                ps.setShort(16, equip.getHands());
                ps.setShort(17, equip.getSpeed());
                ps.setShort(18, equip.getJump());
                ps.setByte(19, (byte) equip.getViciousHammers());
                ps.setByte(20, equip.getLevel());
                ps.setByte(21, equip.getUpgradeSlots());
                ps.setTimestamp(22, item.getExpiration());
                ps.setString(23, item.getOwner());
                ps.setByte(24, (byte) item.getFlag());
                ps.setInt(25, equip.getRingId());
                ps.setByte(26, (byte) (equip.isByGM() ? 1 : 0));
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM inventory_use WHERE CharacterID = ?");
            ps = con.prepareStatement("INSERT INTO inventory_use (CharacterID, ItemID, Position, Quantity, ExpireDate, Owner, Flag, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : getInventory(MapleInventoryType.USE).list()) {
                ps.setInt(2, item.getItemId());
                ps.setShort(3, item.getPosition());
                ps.setShort(4, item.getQuantity());
                ps.setTimestamp(5, item.getExpiration());
                ps.setString(6, item.getOwner());
                ps.setByte(7, (byte) item.getFlag());
                ps.setByte(8, (byte) (item.isByGM() ? 1 : 0));
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM inventory_setup WHERE CharacterID = ?");
            ps = con.prepareStatement("INSERT INTO inventory_setup (CharacterID, ItemID, Position, Quantity, ExpireDate, Owner, Flag, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : getInventory(MapleInventoryType.SETUP).list()) {
                ps.setInt(2, item.getItemId());
                ps.setShort(3, item.getPosition());
                ps.setShort(4, item.getQuantity());
                ps.setTimestamp(5, item.getExpiration());
                ps.setString(6, item.getOwner());
                ps.setByte(7, (byte) item.getFlag());
                ps.setByte(8, (byte) (item.isByGM() ? 1 : 0));
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM inventory_etc WHERE CharacterID = ?");
            ps = con.prepareStatement("INSERT INTO inventory_etc (CharacterID, ItemID, Position, Quantity, ExpireDate, Owner, Flag, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : getInventory(MapleInventoryType.ETC).list()) {
                ps.setInt(2, item.getItemId());
                ps.setShort(3, item.getPosition());
                ps.setShort(4, item.getQuantity());
                ps.setTimestamp(5, item.getExpiration());
                ps.setString(6, item.getOwner());
                ps.setByte(7, (byte) item.getFlag());
                ps.setByte(8, (byte) (item.isByGM() ? 1 : 0));
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM inventory_cash WHERE CharacterID = ?");
            ps = con.prepareStatement("INSERT INTO inventory_cash (CharacterID, ItemID, Position, Quantity, ExpireDate, Owner, Flag, PetID, IsGM) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (IItem item : getInventory(MapleInventoryType.CASH).list()) {
                ps.setInt(2, item.getItemId());
                ps.setShort(3, item.getPosition());
                ps.setShort(4, item.getQuantity());
                ps.setTimestamp(5, item.getExpiration());
                ps.setString(6, item.getOwner());
                ps.setByte(7, (byte) item.getFlag());
                ps.setInt(8, item.getPetId());
                ps.setByte(9, (byte) (item.isByGM() ? 1 : 0));
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            if (pets != null) {
                byte petIndex = 1;
                for (MaplePet pet : pets) {
                    pet.setIndex(petIndex);
                    pet.saveToDb();
                    petIndex++;
                }
            }

            deleteWhereCharacterId(con, "DELETE FROM queststatus WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO queststatus (`queststatusid`, `characterid`, `quest`, `status`, `questRecord`, `time`) VALUES (DEFAULT, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (MapleQuestStatus q : quests.values()) {
                ps.setInt(2, q.getQuestId());
                ps.setInt(3, q.getStatus().getId());
                ps.setString(4, q.getQuestRecord());
                ps.setInt(5, (int) (q.getCompletionTime() / 1000));
                ps.executeUpdate();
            }
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM queststatusex WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO queststatusex (`queststatusid`, `characterid`, `quest`, `status`, `questRecord`, `time`) VALUES (DEFAULT, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            for (MapleQuestStatus q : questRecordsEx.values()) {
                ps.setInt(2, q.getQuestId());
                ps.setInt(3, q.getStatus().getId());
                ps.setString(4, q.getQuestRecord());
                ps.setInt(5, (int) (q.getCompletionTime() / 1000));
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM skills WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO skills (characterid, skillid, skilllevel, masterlevel) VALUES (?, ?, ?, ?)");
            ps.setInt(1, id);
            for (Entry<ISkill, SkillEntry> skill : skills.entrySet()) {
                ps.setInt(2, skill.getKey().getId());
                ps.setInt(3, skill.getValue().skillevel);
                ps.setInt(4, skill.getValue().masterlevel);
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM cooldowns WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO cooldowns (characterid, skillid, starttime, length) VALUES (?, ?, ?, ?)");
            ps.setInt(1, id);
            for (Entry<Integer, SkillCooldown> cooldown : cooldowns.entrySet()) {
                ps.setInt(2, cooldown.getKey());
                ps.setLong(3, cooldown.getValue().getStartTime());
                ps.setLong(4, cooldown.getValue().getLength());
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM keymap WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO keymap (characterid, `key`, `type`, `action`) VALUES (?, ?, ?, ?)");
            ps.setInt(1, id);
            for (Entry<Integer, MapleKeyBinding> keybinding : keymap.entrySet()) {
                ps.setInt(2, keybinding.getKey());
                ps.setInt(3, keybinding.getValue().getType());
                ps.setInt(4, keybinding.getValue().getAction());
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM savedlocations WHERE characterid = ?");
            ps = con.prepareStatement("INSERT INTO savedlocations (characterid, `locationtype`, `map`) VALUES (?, ?, ?)");
            ps.setInt(1, id);
            for (SavedLocationType savedLocationType : SavedLocationType.values()) {
                if (savedLocations[savedLocationType.ordinal()] != -1) {
                    ps.setString(2, savedLocationType.name());
                    ps.setInt(3, savedLocations[savedLocationType.ordinal()]);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM buddies WHERE characterid = ? AND pending = 0");
            ps = con.prepareStatement("INSERT INTO buddies (`characterid`, `buddyid`, `groupname`, `pending`) VALUES (?, ?, ?, 0)");
            ps.setInt(1, id);
            for (BuddylistEntry entry : buddylist.getBuddies()) {
                if (entry.isVisible()) {
                    ps.setInt(2, entry.getCharacterId());
                    ps.setString(3, entry.getGroup());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            ps.close();

            ps = con.prepareStatement("UPDATE accounts SET `gameCardCash` = ?, `maplePoints` = ? WHERE id = ?");
            ps.setInt(1, gameCardCash);
            ps.setInt(2, maplePoints);
            ps.setInt(3, client.getAccID());
            ps.executeUpdate();
            ps.close();

            deleteWhereCharacterId(con, "DELETE FROM wishlist WHERE charid = ?");
            ps = con.prepareStatement("INSERT INTO wishlist(charid, sn) VALUES (?, ?) ");
            ps.setInt(1, id);
            for (int i = 0; i < getWishListSize(); i++) {
                ps.setInt(2, wishlist[i]);
                ps.addBatch();
            }
            ps.executeBatch();
            ps.close();

            if (storage != null) {
                storage.saveToDB();
            }

            if (monsterbook != null) {
                monsterbook.saveCards(id);
            }

            if (update) {
                ps = con.prepareStatement("DELETE FROM achievements WHERE accountid = ?");
                ps.setInt(1, accountid);
                ps.executeUpdate();
                ps.close();

                ps = con.prepareStatement("INSERT INTO achievements(charid, achievementid, accountid) VALUES(?, ?, ?)");
                ps.setInt(1, id);
                ps.setInt(3, accountid);
                for (Integer achid : finishedAchievements) {
                    ps.setInt(2, achid);
                    ps.addBatch();
                }
                ps.executeBatch();
                ps.close();
            }

            con.commit();
		/*} catch (MySQLTransactionRollbackException e) {
			logger.error(MapleClient.getLogMessage(this, "[charsave] Error saving character data - Deadlock found when trying to get lock"));
			try {
				con.rollback();
			} catch (SQLException e1) {
				logger.error(MapleClient.getLogMessage(this, "[charsave] Error Rolling Back"), e);
			}*/
        } catch (Exception e) {
            logger.error(MapleClient.getLogMessage(this, "[charsave] Error saving character data"), e);
            try {
                con.rollback();
            } catch (SQLException e1) {
                logger.error(MapleClient.getLogMessage(this, "[charsave] Error Rolling Back"), e);
            }
        } finally {
            try {
                con.setAutoCommit(true);
                con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            } catch (SQLException e) {
                logger.error(MapleClient.getLogMessage(this, "[charsave] Error going back to autocommit mode"), e);
            }
        }
    }

    private void deleteWhereCharacterId(Connection con, String sql) throws SQLException {
        PreparedStatement ps = con.prepareStatement(sql);
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public MapleQuestStatus getQuest(int questId) {
        if (!quests.containsKey(questId)) {
            return new MapleQuestStatus(questId, MapleQuestStatus.Status.NOT_STARTED, "");
        }
        return quests.get(questId);
    }

    public MapleQuestStatus getQuestEx(int questId) {
        if (!questRecordsEx.containsKey(questId)) {
            return new MapleQuestStatus(questId, MapleQuestStatus.Status.NOT_STARTED, "");
        }
        return questRecordsEx.get(questId);
    }

    public void updateQuest(MapleQuestStatus quest, boolean update, boolean showAnimation, boolean silent) {
        updateQuest(quest, update, false, showAnimation, silent);
    }

    public void updateQuest(MapleQuestStatus quest, boolean update, boolean questRecordEx, boolean showAnimation, boolean silent) {
        if (questRecordEx) {
            questRecordsEx.put(quest.getQuestId(), quest);
        } else {
            quests.put(quest.getQuestId(), quest);
        }
        if (silent) {
            return;
        }
        if (!update && !questRecordEx) {
            if (!(MapleQuest.getInstance(quest.getQuestId()) instanceof MapleCustomQuest)) {
                if (quest.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
                    client.sendPacket(MaplePacketCreator.updateQuestInfo((byte) 1, (short) quest.getQuestId(), quest.getQuestRecord()));
                    client.sendPacket(MaplePacketCreator.updateQuestInfo((short) quest.getQuestId(), false, quest.getNpc(), (byte) 8, (short) 0, true));
                } else if (quest.getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
                    client.sendPacket(MaplePacketCreator.completeQuest((short) quest.getQuestId()));
                    client.sendPacket(MaplePacketCreator.updateQuestInfo((short) quest.getQuestId(), false, quest.getNpc(), (byte) 8, (short) 0, false));
                    if (quest.getQuestId() != 3360 && showAnimation) {
                        client.sendPacket(MaplePacketCreator.showAnimationEffect((byte) 9));
                        map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(id, 9), false);
                    }
                } else if (quest.getStatus().equals(MapleQuestStatus.Status.NOT_STARTED)) {
                    client.sendPacket(MaplePacketCreator.forfeitQuest((short) quest.getQuestId()));
                }
            } else {
                if (quest.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
                    client.sendPacket(MaplePacketCreator.updateQuestInfo((byte) 1, (short) quest.getQuestId(), quest.getQuestRecord()));
                } else if (quest.getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
                    client.sendPacket(MaplePacketCreator.completeQuest((short) quest.getQuestId()));
                }
            }
        }
        if (update) {
            client.sendPacket(MaplePacketCreator.updateQuestInfo((byte) 1, (short) quest.getQuestId(), quest.getQuestRecord()));
        }
        if (questRecordEx) {
            client.sendPacket(MaplePacketCreator.updateQuestRecordExInfo((short) quest.getQuestId(), quest.getQuestRecord()));
        }
    }

    public void completeQuestOnly(MapleQuestStatus quest) {
        quests.put(quest.getQuestId(), quest);
        client.sendPacket(MaplePacketCreator.completeQuest((short) quest.getQuestId()));
    }

    public boolean isActiveBuffedValue(int skillid) {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                    return true;
                }
            }
        }
        return false;
    }

    public Integer getBuffedValue(MapleBuffStat effect) {
        MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return null;
        }
        return mbsvh.value;
    }

    public boolean isBuffFrom(MapleBuffStat stat, ISkill skill) {
        MapleBuffStatValueHolder mbsvh = effects.get(stat);
        if (mbsvh == null) {
            return false;
        }
        return mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skill.getId();
    }

    public int getBuffSource(MapleBuffStat stat) {
        MapleBuffStatValueHolder mbsvh = effects.get(stat);
        if (mbsvh == null) {
            return -1;
        }

        return mbsvh.effect.getSourceId();
    }

    public ArrayList<MapleStatEffect> getBuffEffects() {
        ArrayList<MapleStatEffect> almseret = new ArrayList<>();
        HashSet<Integer> hs = new HashSet<>();
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (mbsvh != null && mbsvh.effect != null) {
                    Integer nid = mbsvh.effect.isSkill() ? mbsvh.effect.getSourceId() : -mbsvh.effect.getSourceId();
                    if (!hs.contains(nid)) {
                        almseret.add(mbsvh.effect);
                        hs.add(nid);
                    }
                }
            }
        }
        return almseret;
    }

    public int getItemQuantity(int itemid, boolean checkEquipped) {
        MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
        MapleInventory iv = inventory[type.ordinal()];
        int possesed = iv.countById(itemid);
        if (checkEquipped) {
            possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
        }

        return possesed;
    }

    public void setBuffedValue(MapleBuffStat effect, int value) {
        MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return;
        }
        mbsvh.value = value;
    }

    public Long getBuffedStarttime(MapleBuffStat effect) {
        MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return null;
        }
        return mbsvh.startTime;
    }

    public MapleStatEffect getStatForBuff(MapleBuffStat effect) {
        MapleBuffStatValueHolder mbsvh = effects.get(effect);
        if (mbsvh == null) {
            return null;
        }
        return mbsvh.effect;
    }

    private void prepareDragonBlood(final MapleStatEffect bloodEffect) {
        if (dragonBloodSchedule != null) {
            dragonBloodSchedule.cancel(false);
        }

        dragonBloodSchedule = TimerManager.getInstance().register(() -> {
            addHP(-bloodEffect.getX());
            getClient().sendPacket(MaplePacketCreator.showOwnBuffEffect(bloodEffect.getSourceId(), 5, (byte) getLevel()));
            getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), bloodEffect.getSourceId(), 5, (byte) getLevel(), (byte) 3), false);
            checkBerserk();
        }, 4000, 4000);
    }

    public void startFullnessSchedule(final int decrease, final MaplePet pet, int petSlot) {
        ScheduledFuture<?> schedule = TimerManager.getInstance().register(() -> {
            int newFullness = pet.getFullness() - decrease;
            if (newFullness <= 5) {
                pet.setFullness(15);
                unequipPet(pet, true);
            } else {
                pet.setFullness(newFullness);
                updatePet(pet);
            }
        }, 60000, 60000);
        switch (petSlot) {
            case 0 -> fullnessSchedule = schedule;
            case 1 -> fullnessSchedule_1 = schedule;
            case 2 -> fullnessSchedule_2 = schedule;
        }
    }

    public void cancelFullnessSchedule(int petSlot) {
        switch (petSlot) {
            case 0:
                if (fullnessSchedule != null) {
                    fullnessSchedule.cancel(false);
                }
                break;
            case 1:
                if (fullnessSchedule_1 != null) {
                    fullnessSchedule_1.cancel(false);
                }
                break;
            case 2:
                if (fullnessSchedule_2 != null) {
                    fullnessSchedule_2.cancel(false);
                }
                break;
        }
    }

    public void startMapTimeLimitTask(final MapleMap from, final MapleMap to) {
        if (to.getTimeLimit() > 0 && from != null) {
            final MapleCharacter chr = this;
            mapTimeLimitTask = TimerManager.getInstance().register(() -> {
                MaplePortal pfrom = null;
                if (MapleItemInformationProvider.getInstance().isMiniDungeonMap(from.getId())) {
                    pfrom = from.getPortal("MD00");
                } else {
                    pfrom = from.getPortal(0);
                }
                if (pfrom != null) {
                    chr.changeMap(from, pfrom);
                }
            }, from.getTimeLimit() * 1000, from.getTimeLimit() * 1000);
        }
    }

    public void cancelMapTimeLimitTask() {
        if (mapTimeLimitTask != null) {
            mapTimeLimitTask.cancel(false);
        }
    }

    public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?> schedule) {
        if (effect.isDragonBlood()) {
            prepareDragonBlood(effect);
        } else if (effect.isBerserk()) {
            checkBerserk();
        } else if (effect.isBeholder()) {
            prepareBeholderEffect();
        }
        for (Pair<MapleBuffStat, Integer> statup : effect.getStatups()) {
            effects.put(statup.getLeft(), new MapleBuffStatValueHolder(effect, starttime, schedule, statup.getRight()));
        }

        recalcLocalStats();
    }

    private List<MapleBuffStat> getBuffStats(MapleStatEffect effect, long startTime) {
        List<MapleBuffStat> stats = new ArrayList<>();
        synchronized (effects) {
            for (Entry<MapleBuffStat, MapleBuffStatValueHolder> stateffect : effects.entrySet()) {
                MapleBuffStatValueHolder mbsvh = stateffect.getValue();
                if (mbsvh.effect.sameSource(effect) && (startTime == -1 || startTime == mbsvh.startTime)) {
                    stats.add(stateffect.getKey());
                }
            }
        }
        return stats;
    }

    private void deregisterBuffStats(List<MapleBuffStat> stats) {
        List<MapleBuffStatValueHolder> effectsToCancel = new ArrayList<>(stats.size());
        for (MapleBuffStat stat : stats) {
            MapleBuffStatValueHolder mbsvh = effects.remove(stat);
            if (mbsvh != null) {
                boolean addMbsvh = true;
                for (MapleBuffStatValueHolder contained : effectsToCancel) {
                    if (mbsvh.startTime == contained.startTime && contained.effect == mbsvh.effect) {
                        addMbsvh = false;
                        break;
                    }
                }
                if (addMbsvh) {
                    effectsToCancel.add(mbsvh);
                }
                if (stat == MapleBuffStat.SUMMON || stat == MapleBuffStat.PUPPET) {
                    int summonId = mbsvh.effect.getSourceId();
                    MapleSummon summon = summons.get(summonId);
                    if (summon != null) {
                        map.broadcastMessage(MaplePacketCreator.removeSpecialMapObject(summon, true));
                        map.removeMapObject(summon);
                        removeVisibleMapObject(summon);
                        summons.remove(summonId);
                        if (summon.getSkill() == 1321007) {
                            if (beholderHealingSchedule != null) {
                                beholderHealingSchedule.cancel(false);
                                beholderHealingSchedule = null;
                            }
                            if (beholderBuffSchedule != null) {
                                beholderBuffSchedule.cancel(false);
                                beholderBuffSchedule = null;
                            }
                        }
                    }
                } else if (stat == MapleBuffStat.DRAGONBLOOD) {
                    if (dragonBloodSchedule != null) {
                        dragonBloodSchedule.cancel(false);
                        dragonBloodSchedule = null;
                    }
                }
            }
        }
        for (MapleBuffStatValueHolder cancelEffectCancelTasks : effectsToCancel) {
            if (getBuffStats(cancelEffectCancelTasks.effect, cancelEffectCancelTasks.startTime).isEmpty()) {
                if (!cancelEffectCancelTasks.effect.isHomingBeacon() && cancelEffectCancelTasks.schedule != null) {
                    cancelEffectCancelTasks.schedule.cancel(false);
                }
            }
        }
    }

    /**
     * @param effect
     * @param overwrite when overwrite is set no data is sent and all the Buffstats in the StatEffect are deregistered
     * @param startTime
     */
    public void cancelEffect(MapleStatEffect effect, boolean overwrite, long startTime) {
        List<MapleBuffStat> buffstats;
        if (!overwrite) {
            buffstats = getBuffStats(effect, startTime);
        } else {
            List<Pair<MapleBuffStat, Integer>> statups = effect.getStatups();
            buffstats = new ArrayList<>(statups.size());
            for (Pair<MapleBuffStat, Integer> statup : statups) {
                buffstats.add(statup.getLeft());
            }
        }
        deregisterBuffStats(buffstats);
        if (effect.isMagicDoor()) {
            // remove for all on maps
            MapleDoor door = doors[0];
            if (door != null) {
                for (MapleCharacter chr : door.getTarget().getCharacters()) {
                    if (chr.map == door.getTarget()) {
                        door.sendDestroyData(chr.client);
                    }
                }
                for (MapleCharacter chr : door.getTown().getCharacters()) {
                    if (chr.map == door.getTown()) {
                        door.sendDestroyData(chr.client);
                    }
                }
                door.getTarget().removeMapObject(door);
                door.getTown().removeMapObject(doors[1]);
                clearDoors();
                silentPartyUpdate();
            }
        } else if (effect.isAranCombo()) {
            comboCounter = 0;
        }

        if (!overwrite) {
            cancelPlayerBuffs(buffstats);
        }
    }

    public void cancelBuffStats(MapleBuffStat stat) {
        List<MapleBuffStat> buffStatList = Collections.singletonList(stat);
        deregisterBuffStats(buffStatList);
        cancelPlayerBuffs(buffStatList);
    }

    public void cancelEffectFromBuffStat(MapleBuffStat stat) {
        MapleBuffStatValueHolder val = effects.get(stat);
        if (val != null) {
            cancelEffect(val.effect, false, -1);
        }
    }

    private void cancelPlayerBuffs(List<MapleBuffStat> buffstats) {
        if (client.getChannelServer().getPlayerStorage().getCharacterById(id) != null) { // are we still connected ?
            recalcLocalStats();
            enforceMaxHpMp();
            client.sendPacket(MaplePacketCreator.cancelBuff(buffstats));
            if (!buffstats.isEmpty() && !buffstats.get(0).equals(MapleBuffStat.HOMING_BEACON)) {
                map.broadcastMessage(this, MaplePacketCreator.cancelForeignBuff(id, buffstats), false);
            }
        }
    }

    public void dispel() {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (mbsvh.effect.isSkill()) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                }
            }
        }
    }

    public void cancelAllBuffs() {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
            }
        }
    }

    public void cancelMorphs() {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (mbsvh.effect.isMorph() && !mbsvh.effect.isPirateMorph()) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                }
            }
        }
    }

    public int getMorphId() {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (mbsvh.effect.isMorph()) {
                    return mbsvh.effect.getMorphId();
                }
            }
        }
        return 0;
    }

    public void silentGiveBuffs(List<PlayerBuffValueHolder> buffs) {
        for (PlayerBuffValueHolder mbsvh : buffs) {
            mbsvh.effect.silentApplyBuff(this, mbsvh.startTime);
        }
    }

    public List<PlayerBuffValueHolder> getAllBuffs() {
        List<PlayerBuffValueHolder> ret = new ArrayList<>();
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                ret.add(new PlayerBuffValueHolder(mbsvh.startTime, mbsvh.effect));
            }
        }
        return ret;
    }

    public void cancelMysticDoor() {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (mbsvh.effect.isMagicDoor()) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                }
            }
        }
    }

    public void handleOrbgain() {
        MapleStatEffect ceffect = null;
        boolean cygnus = job.equals(MapleJob.DAWNWARRIOR3);
        int advComboSkillLevel = getSkillLevel(SkillFactory.getSkill(cygnus ? 11110005 : 1120003));
        if (advComboSkillLevel > 0) {
            ceffect = SkillFactory.getSkill(cygnus ? 11110005 : 1120003).getEffect(advComboSkillLevel);
        } else {
            advComboSkillLevel = getSkillLevel(SkillFactory.getSkill(cygnus ? 11111001 : 1111002));
            if (advComboSkillLevel > 0) {
                ceffect = SkillFactory.getSkill(cygnus ? 11111001 : 1111002).getEffect(advComboSkillLevel);
            }
        }
        if (ceffect == null) {
            return;
        }

        if (getBuffedValue(MapleBuffStat.COMBO) < ceffect.getX() + 1) {
            int neworbcount = getBuffedValue(MapleBuffStat.COMBO) + 1;
            if (advComboSkillLevel > 0 && ceffect.makeChanceResult()) {
                if (neworbcount < ceffect.getX() + 1) {
                    neworbcount++;
                }
            }
            List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, neworbcount));
            setBuffedValue(MapleBuffStat.COMBO, neworbcount);
            int duration = ceffect.getDuration();
            if (getBuffedStarttime(MapleBuffStat.COMBO) == null) {
                return;
            }
            duration += (int) (getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis());
            client.sendPacket(MaplePacketCreator.giveBuff(this, cygnus ? 11111001 : 1111002, duration, stat));
            map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(this, stat, ceffect), false);
        }
    }

    public void handleOrbconsume() {
        boolean cygnus = job.isA(MapleJob.DAWNWARRIOR3);
        ISkill combo = SkillFactory.getSkill(cygnus ? 11111001 : 1111002);
        MapleStatEffect ceffect = combo.getEffect(getSkillLevel(combo));
        List<Pair<MapleBuffStat, Integer>> stat = Collections.singletonList(new Pair<>(MapleBuffStat.COMBO, 1));
        setBuffedValue(MapleBuffStat.COMBO, 1);
        int duration = ceffect.getDuration();
        if (getBuffedStarttime(MapleBuffStat.COMBO) == null) {
            return;
        }
        duration += (int) (getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis());
        client.sendPacket(MaplePacketCreator.giveBuff(this, cygnus ? 11111001 : 1111002, duration, stat));
        map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(this, stat, ceffect), false);
    }

    private void silentEnforceMaxHpMp() {
        setMp(mp);
        setHp(hp, true);
    }

    private void enforceMaxHpMp() {
        List<Pair<MapleStat, Integer>> stats = new ArrayList<>(2);
        if (mp > localmaxmp) {
            setMp(mp);
            stats.add(new Pair<>(MapleStat.MP, mp));
        }
        if (hp > localmaxhp) {
            setHp(hp);
            stats.add(new Pair<>(MapleStat.HP, hp));
        }
        if (!stats.isEmpty()) {
            client.sendPacket(MaplePacketCreator.updatePlayerStats(stats));
        }
    }

    public MapleMap getMap() {
        return map;
    }

    public void changeMap(int map) {
        MapleMap warpMap;
        if (getEventInstance() != null) {
            warpMap = getEventInstance().getMapInstance(map);
        } else {
            warpMap = client.getChannelServer().getMapFactory().getMap(map);
        }

        changeMap(warpMap, warpMap.getPortal(0));
    }

    /**
     * only for tests
     *
     * @param newmap
     */
    public void setMap(MapleMap newmap) {
        map = newmap;
    }

    public int getMapId() {
        if (map != null) {
            return map.getId();
        }
        return mapid;
    }

    public int getInitialSpawnpoint() {
        return initialSpawnPoint;
    }

    public List<LifeMovementFragment> getLastRes() {
        return lastres;
    }

    public void setLastRes(List<LifeMovementFragment> lastres) {
        this.lastres = lastres;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }

    public int getRank() {
        return rank;
    }

    public int getRankMove() {
        return rankMove;
    }

    public int getJobRank() {
        return jobRank;
    }

    public int getJobRankMove() {
        return jobRankMove;
    }

    public int getAPQScore() {
        return APQScore;
    }

    public int getFame() {
        return fame;
    }

    public int getStr() {
        return str;
    }

    public int getDex() {
        return dex;
    }

    public int getLuk() {
        return luk;
    }

    public int getInt() {
        return int_;
    }

    public MapleClient getClient() {
        return client;
    }

    public int getExp() {
        return exp.get();
    }

    public int getHp() {
        return hp;
    }

    public int getMaxHp() {
        return maxhp;
    }

    public int getMp() {
        return mp;
    }

    public int getMaxMp() {
        return maxmp;
    }

    public int getRemainingAp() {
        return remainingAp;
    }

    public int getRemainingSp() {
        return remainingSp;
    }

    public int getMpApUsed() {
        return mpApUsed;
    }

    public void setMpApUsed(int mpApUsed) {
        this.mpApUsed = mpApUsed;
    }

    public int getHpApUsed() {
        return hpApUsed;
    }

    public boolean isHidden() {
        if (id < 30000) {
            return true;
        }
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void setHpApUsed(int hpApUsed) {
        this.hpApUsed = hpApUsed;
    }

    public MapleSkinColor getSkinColor() {
        return skinColor;
    }

    public MapleJob getJob() {
        return job;
    }

    public int getGender() {
        return gender;
    }

    public int getHair() {
        return hair;
    }

    public int getFace() {
        return face;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStr(int str) {
        this.str = str;
        recalcLocalStats();
    }

    public void setDex(int dex) {
        this.dex = dex;
        recalcLocalStats();
    }

    public void setLuk(int luk) {
        this.luk = luk;
        recalcLocalStats();
    }

    public void setInt(int int_) {
        this.int_ = int_;
        recalcLocalStats();
    }

    public void setExp(int exp) {
        this.exp.set(exp);
    }

    public void setMaxHp(int hp) {
        maxhp = hp;
        recalcLocalStats();
    }

    public void setMaxMp(int mp) {
        maxmp = mp;
        recalcLocalStats();
    }

    public void setHair(int hair) {
        this.hair = hair;
    }

    public void setFace(int face) {
        this.face = face;
    }

    public void setFame(int fame) {
        this.fame = fame;
    }

    public void setAPQScore(int score) {
        APQScore = score;
    }

    public void setRemainingAp(int remainingAp) {
        this.remainingAp = remainingAp;
    }

    public void setRemainingSp(int remainingSp) {
        this.remainingSp = remainingSp;
    }

    public void setSkinColor(MapleSkinColor skinColor) {
        this.skinColor = skinColor;
    }

    public void setGender(int gender) {
        this.gender = gender;
    }

    public void setGM(int gmlevel) {
        GMLevel = gmlevel;
    }

    public CheatTracker getCheatTracker() {
        return anticheat;
    }

    public BuddyList getBuddylist() {
        return buddylist;
    }

    public int getAutoHpPot() {
        return autoHpPot;
    }

    public void setAutoHpPot(int itemId) {
        autoHpPot = itemId;
    }

    public int getAutoMpPot() {
        return autoMpPot;
    }

    public void setAutoMpPot(int itemId) {
        autoMpPot = itemId;
    }

    public void addFame(int famechange) {
        fame += famechange;
    }

    public void changeMap(final MapleMap to, final Point pos) {
        MaplePacket warpPacket = MaplePacketCreator.getWarpToMap(to, 0x80, this);
        changeMapInternal(to, pos, warpPacket);
    }

    public void changeMap(final MapleMap to, final MaplePortal pto) {
        MaplePacket warpPacket = MaplePacketCreator.getWarpToMap(to, pto.getId(), this);
        changeMapInternal(to, pto.getPosition(), warpPacket);
    }

    public boolean changeMapOffline(String victim, int mapId) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE characters SET map = ?, spawnpoint = ? WHERE name = ?");
            ps.setInt(1, mapId);
            ps.setInt(2, 0);
            ps.setString(3, victim);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            return false;
        }
        return true;
    }

    private void changeMapInternal(final MapleMap to, final Point pos, MaplePacket warpPacket) {
        if (autoMapChange == to.getId()) {
            autoMapChange = -1;
        }
        dispelSkill(5211002);
        dispelSkill(5211001);
        dispelSkill(5220002);
        warpPacket.setOnSend(() -> {
            map.removePlayer(MapleCharacter.this);
            if (getClient().getChannelServer().getPlayerStorage().getCharacterById(getId()) != null) {
                map = to;
                setPosition(pos);
                pos.y -= 12;
                for (MaplePet pet : pets) {
                    pet.setStance(0);
                    pet.setPos(pos);
                }
                to.addPlayer(MapleCharacter.this);
                if (party != null) {
                    silentPartyUpdate();
                    getClient().sendPacket(MaplePacketCreator.updateParty(getClient().getChannel(), party, PartyOperation.SILENT_UPDATE, null));
                    updatePartyMemberHP();
                }
                getClient().sendPacket(MaplePacketCreator.unknownStatus());
                if (to.getId() == 980000301) { //todo: all CPq map id's
                    setTeam(rand(0, 1));
                    getClient().sendPacket(MaplePacketCreator.startMonsterCarnival(getTeam()));
                }
                if (to.isDojoMap()) {
                    int stage = to.getDojoStage();
                    getDojo().setStage(stage);
                    getClient().sendPacket(MaplePacketCreator.setDojoEnergy(getDojo().getEnergy()));
                    getClient().sendPacket(MaplePacketCreator.showEffect("dojang/start/number/" + stage));
                    getClient().sendPacket(MaplePacketCreator.showEffect("dojang/start/stage"));
                    getClient().sendPacket(MaplePacketCreator.playSound("Dojang/start"));
                    TimerManager.getInstance().schedule(() -> {
                        if (getMap().getSpawnedMonstersOnMap() == 0 && getMap().isDojoMap()) {
                            getMap().spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(getMap().getDojoBoss()), new Point(3, 7));
                            getMap().enableDojoSpawn();
                            getClient().sendPacket(MaplePacketCreator.shakeScreen(0, 1)); // to add the scary effect >:D nah, it's GMSlike
                        }
                    }, 5000);
                }
            }
        });
        if (!to.canMove()) {
            dropMessage("This map has movement turned off.");
        }
        if (!to.canUseSkills()) {
            dropMessage("This map has skills turned off.");
        }
        client.sendPacket(warpPacket);
    }

    public void leaveMap() {
        controlled.clear();
        visibleMapObjects.clear();
        if (chair != 0) {
            chair = 0;
        }
        if (hpDecreaseTask != null) {
            hpDecreaseTask.cancel(false);
        }
    }

    public void startDecHPSchedule() {
        if (eventInstance != null && eventInstance.getName().toLowerCase().startsWith("foj")) {
            return;
        }
        hpDecreaseTask = TimerManager.getInstance().register(() -> decHPEvent(), 10000);
    }

    private void decHPEvent() {
        final short decHP = map.getHPDec();
        final MapleStatEffect effect = getStatForBuff(MapleBuffStat.THAW);
        if (effect != null && decHP + effect.getThaw() == 0 || getInventory(MapleInventoryType.EQUIPPED).findById(map.getHPDecProtect()) != null) {
            return;
        }
        addHP(-decHP);
    }

    public void changeJob(MapleJob newJob) {
        job = newJob;
        remainingSp++;
        if (newJob.getId() % 10 == 2) {
            remainingSp += 2;
        }
        updateSingleStat(MapleStat.AVAILABLESP, remainingSp);
        updateSingleStat(MapleStat.JOB, newJob.getId());
        switch (job.getId()) {
            case 100:
            case 1100:
            case 2100:
                maxhp = 444 + (level - 10) * 16;
                maxmp = 113 + (level - 10) * 12;
                break;
            case 110:
            case 111:
            case 112:
            case 1110:
            case 1111:
            case 1112:
                maxhp += rand(300, 350);
                break;
            case 120:
            case 121:
            case 122:
            case 130:
            case 131:
            case 132:
            case 2110:
                maxmp += rand(100, 150);
                break;
            case 200:
            case 1200:
                maxhp = 162 + (level - 8) * 16;
                maxmp = 253 + (level - 8) * 16;
                break;
            case 210:
            case 211:
            case 212:
            case 220:
            case 221:
            case 222:
            case 230:
            case 231:
            case 232:
            case 1210:
            case 1211:
            case 1212:
                maxmp += rand(450, 500);
                break;
            case 300:
            case 400:
            case 500:
            case 1300:
            case 1400:
            case 1500:
                maxhp = 344 + (level - 10) * 16;
                maxmp = 163 + (level - 10) * 12;
                break;
            case 310:
            case 311:
            case 312:
            case 320:
            case 321:
            case 322:
            case 410:
            case 411:
            case 412:
            case 420:
            case 421:
            case 422:
            case 510:
            case 511:
            case 512:
            case 520:
            case 521:
            case 522:
            case 1310:
            case 1311:
            case 1312:
            case 1410:
            case 1411:
            case 1412:
            case 1510:
            case 1511:
            case 1512:
                maxhp += rand(300, 350);
                maxmp += rand(150, 200);
                break;
        }
        maxhp = Math.min(30000, maxhp);
        maxmp = Math.min(30000, maxmp);
        List<Pair<MapleStat, Integer>> statup = new ArrayList<>(2);
        statup.add(new Pair<>(MapleStat.MAXHP, maxhp));
        statup.add(new Pair<>(MapleStat.MAXMP, maxmp));
        recalcLocalStats();
        client.sendPacket(MaplePacketCreator.updatePlayerStats(statup));
        map.broadcastMessage(this, MaplePacketCreator.showJobChange(id), false);
        silentPartyUpdate();
        guildUpdate();
        if (job.isA(MapleJob.NOBLESSE)) {
            client.sendPacket(MaplePacketCreator.enableTutor(false));
        }
    }

    public boolean checkAp(int usedap) {
        int ap = 30;
        ap += level * 5;

        if (job.getId() - job.getId() / 10 * 10 >= 1) {
            ap += 5;
        }
        if (job.getId() - job.getId() / 10 * 10 == 2) {
            ap += 5;
        }
        return usedap <= ap;
    }

    public boolean canUseApReset() {
        int totalApUsed = str + int_ + dex + luk;
        int freeAp = 0;
        if (hpApUsed < 0 && mpApUsed >= 0) {
            freeAp = -hpApUsed - mpApUsed;
        } else if (hpApUsed >= 0 && mpApUsed < 0) {
            freeAp = -mpApUsed - hpApUsed;
        } else if (hpApUsed < 0 && mpApUsed < 0) {
            freeAp = -mpApUsed + -hpApUsed;
        }
        return checkAp(totalApUsed + freeAp);

    }

    public void gainAp(int ap) {
        remainingAp += ap;
        updateSingleStat(MapleStat.AVAILABLEAP, remainingAp);
    }

    public void apReset() {
        apReset(true, true, true, true);
    }

    public void apReset(boolean resetStr, boolean resetDex, boolean resetInt, boolean resetLuk) {
        int newStr = str, newDex = dex, newInt = int_, newLuk = luk;
        int jobType = job.getId() / 100 % 10;
        if (resetStr) {
            newStr = jobType == 1 ? 35 : 4;
        }
        if (resetDex) {
            newDex = jobType == 3 || jobType == 4 ? 25 : jobType == 5 ? 20 : 4;
        }
        if (resetInt) {
            newInt = jobType == 2 ? 20 : 4;
        }
        if (resetLuk) {
            newLuk = 4;
        }
        int newAp = str + dex + int_ + luk + remainingAp - (newDex + newStr + newInt + newLuk);
        setStr(newStr);
        setDex(newDex);
        setInt(newInt);
        setLuk(newLuk);
        remainingAp = newAp;
        List<Pair<MapleStat, Integer>> statups = new ArrayList<>(5);
        statups.add(new Pair<>(MapleStat.STR, str));
        statups.add(new Pair<>(MapleStat.DEX, dex));
        statups.add(new Pair<>(MapleStat.INT, int_));
        statups.add(new Pair<>(MapleStat.LUK, luk));
        statups.add(new Pair<>(MapleStat.AVAILABLEAP, remainingAp));
        client.sendPacket(guida.tools.MaplePacketCreator.updatePlayerStats(statups));
    }

    public void changeSkillLevel(ISkill skill, int newLevel, int newMasterlevel) {
        if (skill == null) {
            return;
        }
        if (newLevel < 0) {
            skills.remove(skill);
        } else {
            skills.put(skill, new SkillEntry(newLevel, newMasterlevel));
        }
        client.sendPacket(MaplePacketCreator.updateSkill(skill.getId(), newLevel, newMasterlevel));
    }

    public void setHpPot(int itemid) {
        hppot = itemid;
    }

    public void setMpPot(int itemid) {
        mppot = itemid;
    }

    public int getHpPot() {
        return hppot;
    }

    public int getMpPot() {
        return mppot;
    }

    public void setHp(int newhp) {
        setHp(newhp, false, null);
    }

    public void setHp(int newhp, boolean silent) {
        setHp(newhp, silent, null);
    }

    public void setHp(int newhp, boolean silent, MapleMonster damager) {
        int oldHp = hp;
        int thp = newhp;
        if (thp < 0) {
            thp = 0;
        }
        if (thp > localmaxhp) {
            thp = localmaxhp;
        }
        hp = thp;

        if (!silent) {
            updatePartyMemberHP();
        }
        if (oldHp > hp && !isAlive()) {
            playerDead(damager);
        }
    }

    private void playerDead(MapleMonster killer) {
        if (eventInstance != null) {
            eventInstance.playerKilled(this);
        }
        if (killer != null && killer.getId() == 100100) {
            finishAchievement(51);
        }
        if (killer != null && killer.getSummonedBy() != null) {
            killer.getSummonedBy().finishAchievement(52);
        }
        lastDeath = System.currentTimeMillis();
        dispelSkill(0);
        cancelAllDebuffs();
        cancelMorphs();

        int[] charmID = {5130000, 4031283, 4140903}; // NOTE Also checks in this order
        MapleCharacter player = client.getPlayer();
        int possesed = 0;
        int i;

        //Check for charms
        for (i = 0; i < charmID.length; i++) {
            int quantity = getItemQuantity(charmID[i], false);
            if (quantity > 0) {
                possesed = quantity;
                break;
            }
        }

        if (possesed > 0 && !map.hasEvent() && !map.isDojoMap()) {
            possesed -= 1;
            client.sendPacket(MaplePacketCreator.serverNotice(5, "You have used the safety charm once, so your EXP points have not been decreased. (" + possesed + "time(s) left)"));
            MapleInventoryManipulator.removeById(client, MapleItemInformationProvider.getInstance().getInventoryType(charmID[i]), charmID[i], 1, true, false);
        } else if (map.hasEvent()) {
            client.sendPacket(MaplePacketCreator.serverNotice(5, "Since you were in an event map, your experience did not decrease."));
        } else if (player != null && player.job != MapleJob.BEGINNER && !map.isDojoMap()) {
            //Lose XP
            int XPdummy = ExpTable.getExpNeededForLevel(player.level + 1);
            if (player.map.isTown()) {
                XPdummy *= 0.01;
            }
            if (XPdummy == ExpTable.getExpNeededForLevel(player.level + 1)) {
                if (player.luk <= 100 && player.luk > 8) {
                    XPdummy *= 0.10 - player.luk * 0.0005;
                } else if (player.luk < 8) {
                    XPdummy *= 0.10;
                } else {
                    XPdummy *= 0.10 - 100 * 0.0005;
                }
            }
            if (player.getExp() - XPdummy > 0) {
                player.gainExp(-XPdummy, false, false);
            } else {
                player.gainExp(-player.getExp(), false, false);
            }
        }
        battleshipHP = getSkillLevel(SkillFactory.getSkill(5221006)) * 4000 + (level - 120) * 2000;
        client.sendPacket(MaplePacketCreator.enableActions());
    }

    public void updatePartyMemberHP() {
        if (party != null) {
            int channel = client.getChannel();
            for (MaplePartyCharacter partychar : party.getMembers()) {
                if (partychar.getMapId() == getMapId() && partychar.getChannel() == channel) {
                    MapleCharacter other = client.getChannelServer().getPlayerStorage().getCharacterByName(partychar.getName());
                    if (other != null) {
                        other.client.sendPacket(MaplePacketCreator.updatePartyMemberHP(id, hp, localmaxhp));
                    }
                }
            }
        }
    }

    public void receivePartyMemberHP() {
        if (party != null) {
            int channel = client.getChannel();
            for (MaplePartyCharacter partychar : party.getMembers()) {
                if (partychar.getMapId() == getMapId() && partychar.getChannel() == channel) {
                    MapleCharacter other = client.getChannelServer().getPlayerStorage().getCharacterByName(partychar.getName());
                    if (other != null) {
                        client.sendPacket(MaplePacketCreator.updatePartyMemberHP(other.id, other.hp, other.localmaxhp));
                    }
                }
            }
        }
    }

    public void setMp(int newmp) {
        int tmp = newmp;
        if (tmp < 0) {
            tmp = 0;
        }
        if (tmp > localmaxmp) {
            tmp = localmaxmp;
        }
        mp = tmp;
    }

    /**
     * Convenience function which adds the supplied parameter to the current hp then directly does a updateSingleStat.
     *
     * @param delta
     * @see MapleCharacter#setHp(int)
     */
    public void addHP(int delta) {
        setHp(hp + delta);
        updateSingleStat(MapleStat.HP, hp);
    }

    /**
     * Convenience function which adds the supplied parameter to the current mp then directly does a updateSingleStat.
     *
     * @param delta
     * @see MapleCharacter#setMp(int)
     */
    public void addMP(int delta) {
        setMp(mp + delta);
        updateSingleStat(MapleStat.MP, mp);
    }

    public void addMPHP(int hpDiff, int mpDiff) {
        addMPHP(hpDiff, mpDiff, null);
    }

    public void addMPHP(int hpDiff, int mpDiff, MapleMonster damager) {
        setHp(hp + hpDiff, false, damager);
        setMp(mp + mpDiff);
        List<Pair<MapleStat, Integer>> stats = new ArrayList<>();
        stats.add(new Pair<>(MapleStat.HP, hp));
        stats.add(new Pair<>(MapleStat.MP, mp));
        MaplePacket updatePacket = MaplePacketCreator.updatePlayerStats(stats);
        client.sendPacket(updatePacket);
    }

    /**
     * Updates a single stat of this MapleCharacter for the client. This method only creates and sends an update packet,
     * it does not update the stat stored in this MapleCharacter instance.
     *
     * @param stat
     * @param newval
     * @param itemReaction
     */
    public void updateSingleStat(MapleStat stat, int newval, boolean itemReaction) {
        Pair<MapleStat, Integer> statpair = new Pair<>(stat, newval);
        MaplePacket updatePacket = MaplePacketCreator.updatePlayerStats(Collections.singletonList(statpair), itemReaction);
        client.sendPacket(updatePacket);
    }

    public void updateSingleStat(MapleStat stat, int newval) {
        updateSingleStat(stat, newval, false);
    }

    public void gainExp(int gain, boolean show, boolean inChat) {
        gainExp(gain, show, inChat, true, false);
    }

    public void gainExp(int gain, boolean show, boolean inChat, boolean white, boolean monster) {
        gainExp(gain, show, inChat, white, monster, 0, 1);
    }

    public void gainExp(int gain, boolean show, boolean inChat, boolean white, boolean monster, int nerf, int shared) {
        if (inCS() || inMTS()) {
            return;
        }
        int originalGain = gain;
        int partyBonus = 0;
        if (monster) {
            int partySize = 0;
            if (party != null) {
                partySize = party.getPartyMembersOnMap(this).size();
            }
            if (partySize > 0) {
                partyBonus = gain / 10 * (partySize - 1);
            }
            if (shared < 2) {
                partyBonus = 0;
            }
            gain += partyBonus;
        }
        if (level < 200 && !job.isA(MapleJob.NOBLESSE) || level < 120 && job.isA(MapleJob.NOBLESSE)) {
            if (getExp() + gain >= ExpTable.getExpNeededForLevel(level + 1)) {
                setExp(exp.addAndGet(gain));
                levelUp();
                if (getExp() > ExpTable.getExpNeededForLevel(level + 1)) {
                    setExp(ExpTable.getExpNeededForLevel(level + 1));
                }
            } else {
                setExp(exp.addAndGet(gain));
            }
        } else if (getExp() != 0) {
            setExp(0);
        }
        updateSingleStat(MapleStat.EXP, getExp());
        if (show && gain != 0) {
            client.sendPacket(MaplePacketCreator.getShowExpGain(originalGain, partyBonus, inChat, white));
            if (nerf > 0) {
                client.sendPacket(MaplePacketCreator.getShowExpGain(-nerf, 0, inChat, white));
            }
        }
    }

    public void silentPartyUpdate() {
        if (party != null) {
            try {
                client.getChannelServer().getWorldInterface().updateParty(party.getId(), PartyOperation.SILENT_UPDATE, new MaplePartyCharacter(MapleCharacter.this));
            } catch (RemoteException e) {
                logger.error("REMOTE THROW", e);
                client.getChannelServer().reconnectWorld();
            }
        }
    }

    public boolean isGM() {
        return GMLevel > 0;
    }

    public int getGMLevel() {
        return GMLevel;
    }

    public boolean hasGMLevel(int level) {
        return GMLevel >= level;
    }

    public MapleInventory getInventory(MapleInventoryType type) {
        return inventory[type.ordinal()];
    }

    public MapleShop getShop() {
        return shop;
    }

    public void setShop(MapleShop shop) {
        this.shop = shop;
    }

    public int getMeso() {
        return meso.get();
    }

    public int getSavedLocation(SavedLocationType type) {
        return savedLocations[type.ordinal()];
    }

    public void saveLocation(SavedLocationType type) {
        savedLocations[type.ordinal()] = getMapId();
    }

    public void clearSavedLocation(SavedLocationType type) {
        savedLocations[type.ordinal()] = -1;
    }

    public void gainMeso(int gain, boolean show) {
        gainMeso(gain, show, false, false, true, false);
    }

    public void gainMeso(int gain, boolean show, boolean enableActions, boolean inChat) {
        gainMeso(gain, show, enableActions, inChat, true, false);
    }

    public void gainMeso(int gain, boolean show, boolean enableActions, boolean inChat, boolean packet, boolean loseMeso) {
        if (loseMeso) {
            gain *= 0.94;
        }
        if (meso.get() + gain < 0) {
            if (packet) {
                client.sendPacket(MaplePacketCreator.enableActions());
            }
            return;
        }
        int newVal = meso.addAndGet(gain);
        if (packet) {
            updateSingleStat(MapleStat.MESO, newVal, enableActions);
        }
        if (newVal >= 2147483646 && packet) {
            finishAchievement(8);
        }
        if (show) {
            client.sendPacket(MaplePacketCreator.getShowMesoGain(gain, inChat, loseMeso));
        }
    }

    /**
     * Adds this monster to the controlled list. The monster must exist on the Map.
     *
     * @param monster
     */
    public void controlMonster(MapleMonster monster, boolean aggro) {
        monster.setController(this);
        controlled.add(monster);
        client.sendPacket(MaplePacketCreator.controlMonster(monster, false, aggro));
    }

    public void stopControllingMonster(MapleMonster monster) {
        controlled.remove(monster);
    }

    public void checkMonsterAggro(MapleMonster monster) {
        if (!monster.isControllerHasAggro()) {
            if (monster.getController() == this) {
                monster.setControllerHasAggro(true);
            } else {
                monster.switchController(this, true);
            }
        }
    }

    public Collection<MapleMonster> getControlledMonsters() {
        return Collections.unmodifiableCollection(controlled);
    }

    public int getNumControlledMonsters() {
        return controlled.size();
    }

    @Override
    public String toString() {
        return "Character: " + name;
    }

    public int getAccountID() {
        return accountid;
    }

    public void mobKilled(int id) {
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus() == MapleQuestStatus.Status.COMPLETED || q.getMobSize() == 0) {
                continue;
            }
            MapleQuest quest = MapleQuest.getInstance(q.getQuestId());
            if (q.mobKilled(id) && !(quest instanceof MapleCustomQuest)) {
                client.sendPacket(MaplePacketCreator.updateQuestInfo((byte) 1, (short) q.getQuestId(), q.getQuestRecord()));
                if (quest.canComplete(this, null)) {
                    client.sendPacket(MaplePacketCreator.getShowQuestCompletion(q.getQuestId()));
                }
            }
        }
    }

    public final List<MapleQuestStatus> getStartedQuests() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
                ret.add(q);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    public final List<MapleQuestStatus> getCompletedQuests() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : quests.values()) {
            if (q.getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
                ret.add(q);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    public final List<MapleQuestStatus> getStartedQuestRecordEx() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : questRecordsEx.values()) {
            if (q.getStatus().equals(MapleQuestStatus.Status.STARTED)) {
                ret.add(q);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    public final List<MapleQuestStatus> getCompletedQuestRecordEx() {
        List<MapleQuestStatus> ret = new LinkedList<>();
        for (MapleQuestStatus q : questRecordsEx.values()) {
            if (q.getStatus().equals(MapleQuestStatus.Status.COMPLETED)) {
                ret.add(q);
            }
        }
        return Collections.unmodifiableList(ret);
    }

    public int getRequiredQuestItemAmount(int itemId) {
        int amount = 0;
        for (MapleQuestStatus q : getStartedQuests()) {
            amount += q.getRequiredItemAmount(itemId);
        }
        return amount;
    }

    public boolean canSeeItem(MapleMapObject mapobject) {
        if (!isGM()) {
            if (mapobject instanceof MapleMapItem) {
                MapleMapItem mapItem = (MapleMapItem) mapobject;
                if (mapItem.getMeso() == 0) {
                    int itemId = mapItem.getItem().getItemId();
                    if (itemId == 4032105 && job.getId() == 1510) {
                        return true;
                    }
                    boolean partyItem = false;
                    if (party != null) {
                        partyItem = party.getMemberById(mapItem.getOwner().id) != null;
                    }
                    MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                    if (ii.getName(itemId) != null && itemId != 4031013 && ii.isQuestItem(itemId) && (mapItem.getOwner() == this || partyItem)) {
                        int amount = getRequiredQuestItemAmount(itemId);
                        return amount != 0 && (amount <= 0 || !haveItem(itemId, amount, false, false));
                    }
                }
            }
        }
        return true;
    }

    public boolean canSeeItem(int itemId) {
        if (!isGM()) {
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            if (ii.getName(itemId) != null && itemId != 4031013 && ii.isQuestItem(itemId)) {
                int amount = getRequiredQuestItemAmount(itemId);
                return amount != 0 && (amount <= 0 || !haveItem(itemId, amount, false, false));
            }
        }
        return true;
    }

    public IMaplePlayerShop getPlayerShop() {
        return playerShop;
    }

    public void setPlayerShop(IMaplePlayerShop playerShop) {
        this.playerShop = playerShop;
    }

    public Map<ISkill, SkillEntry> getSkills() {
        return Collections.unmodifiableMap(skills);
    }

    public void dispelSkill(int skillid) {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (skillid == 0) {
                    if (mbsvh.effect.isSkill()) {
                        switch (mbsvh.effect.getSourceId()) {
                            case 1004:
                            case 1001004:
                            case 20001004:
                            case 1321007:
                            case 2121005:
                            case 2221005:
                            case 2311006:
                            case 2321003:
                            case 3111002:
                            case 3111005:
                            case 3211002:
                            case 3211005:
                            case 4111002:
                            case 14111000:
                                cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                        }
                    }
                } else if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
                    cancelEffect(mbsvh.effect, false, mbsvh.startTime);
                }
            }
        }
    }

    public int getSkillLevel(int skillId) {
        SkillEntry ret = skills.get(SkillFactory.getSkill(skillId));
        if (ret == null) {
            return 0;
        }
        return ret.skillevel;
    }

    public int getSkillLevel(ISkill skill) {
        SkillEntry ret = skills.get(skill);
        if (ret == null) {
            return 0;
        }
        return ret.skillevel;
    }

    public int getMasterLevel(ISkill skill) {
        SkillEntry ret = skills.get(skill);
        if (ret == null) {
            return -1;
        }
        return ret.masterlevel;
    }

    public int getTotalDex() {
        return localdex;
    }

    public int getTotalInt() {
        return localint_;
    }

    public int getTotalStr() {
        return localstr;
    }

    public int getTotalLuk() {
        return localluk;
    }

    public int getTotalMagic() {
        return magic;
    }

    public double getSpeedMod() {
        return speedMod;
    }

    public double getJumpMod() {
        return jumpMod;
    }

    public int getTotalWatk() {
        return watk;
    }

    public void levelUp() {
        lastLevelUpTime = new Timestamp(System.currentTimeMillis());
        ISkill improvingMaxHP = null;
        int improvingMaxHPLevel = 0;
        ISkill improvingMaxMP = null;
        int improvingMaxMPLevel = 0;
        if ((job.equals(MapleJob.BEGINNER) || job.equals(MapleJob.NOBLESSE) || job == MapleJob.LEGEND) && level < 10) {
            str += 5;
        } else {
            remainingAp += 5;
        }
        if (job.isA(MapleJob.NOBLESSE) && level < 70) {
            remainingAp += 1;
        }
        if (job == MapleJob.BEGINNER || job == MapleJob.NOBLESSE || job == MapleJob.LEGEND) {
            maxhp += rand(12, 16);
            maxmp += rand(10, 12);
        } else if (job.isA(MapleJob.WARRIOR) || job.isA(MapleJob.DAWNWARRIOR1)) {
            if (job.isA(MapleJob.WARRIOR)) {
                improvingMaxHP = SkillFactory.getSkill(1000001);
            } else {
                improvingMaxHP = SkillFactory.getSkill(11000000);
            }
            improvingMaxHPLevel = getSkillLevel(improvingMaxHP);
            maxhp += rand(24, 28);
            maxmp += rand(3, 6);
        } else if (job.isA(MapleJob.MAGICIAN) || job.isA(MapleJob.BLAZEWIZARD1)) {
            if (job.isA(MapleJob.MAGICIAN)) {
                improvingMaxMP = SkillFactory.getSkill(2000001);
            } else {
                improvingMaxMP = SkillFactory.getSkill(12000000);
            }
            improvingMaxMPLevel = getSkillLevel(improvingMaxMP);
            maxhp += rand(10, 14);
            maxmp += rand(22, 24);
        } else if (job.isA(MapleJob.BOWMAN) || job.isA(MapleJob.THIEF) || job.isA(MapleJob.GM) || job.isA(MapleJob.WINDARCHER1) || job.isA(MapleJob.NIGHTWALKER1)) {
            maxhp += rand(20, 24);
            maxmp += rand(14, 16);
        } else if (job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) {
            if (job.isA(MapleJob.PIRATE)) {
                improvingMaxHP = SkillFactory.getSkill(5100000);
            } else {
                improvingMaxHP = SkillFactory.getSkill(15100000);
            }
            improvingMaxHPLevel = getSkillLevel(improvingMaxHP);
            maxhp += rand(22, 26);
            maxmp += rand(18, 23);
        } else if (job.isA(MapleJob.ARAN)) {
            maxhp += rand(44, 48);
            maxmp += rand(4, 8);
        }
        if (improvingMaxHPLevel > 0) {
            maxhp += improvingMaxHP.getEffect(improvingMaxHPLevel).getX();
        }
        if (improvingMaxMPLevel > 0) {
            maxmp += improvingMaxMP.getEffect(improvingMaxMPLevel).getX();
        }
        maxmp += localint_ * 0.1;
        exp.addAndGet(-ExpTable.getExpNeededForLevel(level + 1));
        level += 1;

        maxhp = Math.min(30000, maxhp);
        maxmp = Math.min(30000, maxmp);

        List<Pair<MapleStat, Integer>> statup = new ArrayList<>(9);
        if (level < 11 && (job.equals(MapleJob.BEGINNER) || job.equals(MapleJob.NOBLESSE) || job == MapleJob.LEGEND)) {
            statup.add(new Pair<>(MapleStat.STR, str));
        }
        statup.add(new Pair<>(MapleStat.AVAILABLEAP, remainingAp));
        statup.add(new Pair<>(MapleStat.MAXHP, maxhp));
        statup.add(new Pair<>(MapleStat.MAXMP, maxmp));
        statup.add(new Pair<>(MapleStat.HP, maxhp));
        statup.add(new Pair<>(MapleStat.MP, maxmp));
        statup.add(new Pair<>(MapleStat.EXP, exp.get()));
        statup.add(new Pair<>(MapleStat.LEVEL, level));

        if (job != MapleJob.BEGINNER && job != MapleJob.NOBLESSE && job != MapleJob.LEGEND) {
            remainingSp += 3;
            statup.add(new Pair<>(MapleStat.AVAILABLESP, remainingSp));
        }

        setHp(maxhp);
        setMp(maxmp);

        // Disabled temporarily until the glitches get fixed
		/*if (level == 200) {
			if (playerNPC == false) {
				if (MaplePlayerNPC.autoPlayerNPCCreation == true) {
					this.getPlayerNPC().createPNE();
					this.createPlayerNPC();
					playerNPC = true;
				} else {
					updateLater();
				}
			}
		}*/

        client.sendPacket(MaplePacketCreator.updatePlayerStats(statup));
        if (guildid != 0 && GMLevel < 2) {
            try {
                client.getChannelServer().getWorldInterface().broadcastToGuild(guildid, MaplePacketCreator.serverNotice(5, "<Guild> " + name + " has leveled to " + level));
            } catch (RemoteException e) {
                client.getChannelServer().reconnectWorld();
            }
        }
        map.broadcastMessage(this, MaplePacketCreator.showLevelup(id), false);
        recalcLocalStats();
        silentPartyUpdate();
        guildUpdate();

        if (level >= 70) {
            finishAchievement(4);
        }
        if (level >= 120) {
            finishAchievement(5);
        }
        if (level == 200) {
            finishAchievement(22);
        }
        if (level == map.getLevelForceMove()) {
            changeMap(map.getReturnMap(), map.getRandomSpawnPoint());
        }
    }

    public void changeKeybinding(int key, MapleKeyBinding keybinding) {
        if (keybinding.getType() != 0) {
            keymap.put(key, keybinding);
        } else {
            keymap.remove(key);
        }
    }

    public void sendKeymap() {
        client.sendPacket(MaplePacketCreator.getKeymap(keymap));
    }

    public void sendMacros() {
        boolean macros = false;
        for (int i = 0; i < 5; i++) {
            if (skillMacros[i] != null) {
                macros = true;
                break;
            }
        }
        if (macros) {
            client.sendPacket(MaplePacketCreator.getMacros(skillMacros));
        }
    }

    public void updateMacros(int position, SkillMacro updateMacro) {
        skillMacros[position] = updateMacro;
    }

    public void tempban(String reason, Calendar duration, int greason) {
        if (lastmonthfameids == null) {
            throw new RuntimeException("Trying to ban a non-loaded character (testhack)");
        }
        tempban(reason, duration, greason, client.getAccID());
        banned = true;
        client.disconnect();
    }

    public void ban(String reason) {
        if (lastmonthfameids == null) {
            throw new RuntimeException("Trying to ban a non-loaded character (testhack)");
        }
        try {
            client.banMacs();
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET banned = ?, banreason = ? WHERE id = ?");
            ps.setInt(1, 1);
            ps.setString(2, reason);
            ps.setInt(3, accountid);
            ps.executeUpdate();
            ps.close();
            ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
            ps.setString(1, client.getIP());
            ps.executeUpdate();
            ps.close();
        } catch (SQLException ex) {
            logger.error("Error while banning", ex);
        }
        banned = true;
        client.disconnect();
    }

    /**
     * Oid of players is always = the cid
     */
    @Override
    public int getObjectId() {
        return id;
    }

    /**
     * Throws unsupported operation exception, oid of players is read only
     */
    @Override
    public void setObjectId(int id) {
        throw new UnsupportedOperationException();
    }

    public MapleStorage getStorage() {
        return storage;
    }

    public int getCurrentMaxHp() {
        return localmaxhp;
    }

    public int getCurrentMaxMp() {
        return localmaxmp;
    }

    public int getCurrentMaxBaseDamage() {
        return localmaxbasedamage;
    }

    public int calculateMaxBaseDamage(int watk) {
        int maxbasedamage;
        if (watk == 0) {
            maxbasedamage = 1;
        } else {
            IItem weapon_item = getInventory(MapleInventoryType.EQUIPPED).getItem((short) -11);
            boolean barefists = weapon_item == null && job.isA(MapleJob.PIRATE);

            if (weapon_item != null || job.isA(MapleJob.PIRATE)) {
                MapleWeaponType weapon = barefists ? MapleWeaponType.KNUCKLE : MapleItemInformationProvider.getInstance().getWeaponType(weapon_item.getItemId());
                int mainstat;
                int secondarystat;
                if (weapon == MapleWeaponType.BOW || weapon == MapleWeaponType.CROSSBOW) {
                    mainstat = localdex;
                    secondarystat = localstr;
                } else if ((job.isA(MapleJob.THIEF) || job.isA(MapleJob.NIGHTWALKER1)) && (weapon == MapleWeaponType.CLAW || weapon == MapleWeaponType.DAGGER)) {
                    mainstat = localluk;
                    secondarystat = localdex + localstr;
                } else if ((job.isA(MapleJob.PIRATE) || job.isA(MapleJob.THUNDERBREAKER1)) && weapon == MapleWeaponType.GUN) {
                    mainstat = localdex;
                    secondarystat = localstr;
                } else {
                    mainstat = localstr;
                    secondarystat = localdex;
                }
                maxbasedamage = (int) ((weapon.getMaxDamageMultiplier() * mainstat + secondarystat) * watk / 100.0);
                maxbasedamage += 10;
            } else {
                maxbasedamage = 0;
            }
            if (barefists) {
                maxbasedamage *= 2;
            }
        }

        return maxbasedamage;
    }

    public void addVisibleMapObject(MapleMapObject mo) {
        visibleMapObjects.add(mo);
    }

    public void removeVisibleMapObject(MapleMapObject mo) {
        visibleMapObjects.remove(mo);
    }

    public boolean isMapObjectVisible(MapleMapObject mo) {
        return visibleMapObjects.contains(mo);
    }

    public Collection<MapleMapObject> getVisibleMapObjects() {
        return Collections.unmodifiableCollection(visibleMapObjects);
    }

    public void addOwnedObject(MapleMap map, MapleMapObject mo) {
        ownedInteractableObjects.add(new Pair<>(map, mo));
    }

    public void removeOwnedMapObject(MapleMapObject mo) {
        List<Pair<MapleMap, MapleMapObject>> objects = new ArrayList<>(ownedInteractableObjects);
        for (Pair<MapleMap, MapleMapObject> m : objects) {
            if (m.getRight().getObjectId() == mo.getObjectId()) {
                ownedInteractableObjects.remove(m);
            }
        }
    }

    public void destroyAllOwnedInteractableObjects() {
        List<Pair<MapleMap, MapleMapObject>> objects = new ArrayList<>(ownedInteractableObjects);
        for (Pair<MapleMap, MapleMapObject> m : objects) {
            if (m.getRight() instanceof MapleMist) {
                MapleMist mist = (MapleMist) m.getRight();
                m.getLeft().removeMapObject(m.getRight());
                m.getLeft().broadcastMessage(mist.makeDestroyData());
            }
        }
        cancelMysticDoor();
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public void setSlot(int slotid) {
        slots = slotid;
    }

    public int getSlot() {
        return slots;
    }

    public boolean hasBattleShip() {
        synchronized (effects) {
            for (MapleBuffStatValueHolder mbsvh : effects.values()) {
                if (mbsvh.effect.getSourceId() == 5221006) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.removePlayerFromMap(getObjectId()));
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (!isHidden()) {
            client.sendPacket(MaplePacketCreator.spawnPlayerMapobject(this));
        }
    }

    private void recalcLocalStats() {
        recalcLocalStats(true);
    }

    private void recalcLocalStats(boolean channelserv) {
        int oldmaxhp = localmaxhp;
        localmaxhp = maxhp;
        localmaxmp = maxmp;
        localdex = dex;
        localint_ = int_;
        localstr = str;
        localluk = luk;
        int speed = 100;
        int jump = 100;
        magic = localint_;
        watk = 0;
        for (IItem item : getInventory(MapleInventoryType.EQUIPPED)) {
            IEquip equip = (IEquip) item;
            localmaxhp += equip.getHp();
            localmaxmp += equip.getMp();
            localdex += equip.getDex();
            localint_ += equip.getInt();
            localstr += equip.getStr();
            localluk += equip.getLuk();
            magic += equip.getMatk() + equip.getInt();
            watk += equip.getWatk();
            speed += equip.getSpeed();
            jump += equip.getJump();
        }
        IItem weapon = getInventory(MapleInventoryType.EQUIPPED).getItem((short) -11);
        if (weapon == null && job.isA(MapleJob.PIRATE)) // Barefists
        {
            watk += 8;
        }
        if (energybar == 10000) {
            watk += 20;
        }
        magic = Math.min(magic, 2000);
        if (channelserv) {
            Integer hbhp = getBuffedValue(MapleBuffStat.HYPERBODYHP);
            if (hbhp != null) {
                localmaxhp += hbhp.doubleValue() / 100 * localmaxhp;
            }
            Integer hbmp = getBuffedValue(MapleBuffStat.HYPERBODYMP);
            if (hbmp != null) {
                localmaxmp += hbmp.doubleValue() / 100 * localmaxmp;
            }
            localmaxhp = Math.min(30000, localmaxhp);
            localmaxmp = Math.min(30000, localmaxmp);
            Integer watkbuff = getBuffedValue(MapleBuffStat.WATK);
            if (watkbuff != null) {
                watk += watkbuff;
            }
            Integer matkbuff = getBuffedValue(MapleBuffStat.MATK);
            if (matkbuff != null) {
                magic += matkbuff;
            }
            if (job.isA(MapleJob.BOWMAN)) {
                ISkill expert = null;
                if (job.isA(MapleJob.CROSSBOWMASTER)) {
                    expert = SkillFactory.getSkill(3220004);
                } else if (job.isA(MapleJob.BOWMASTER)) {
                    expert = SkillFactory.getSkill(3120005);
                }
                if (expert != null) {
                    int boostLevel = getSkillLevel(expert);
                    if (boostLevel > 0) {
                        watk += expert.getEffect(boostLevel).getX();
                    }
                }
            }
            int[] bofs = {12, 10000012, 20000012};
            for (int bof : bofs) {
                ISkill skill = SkillFactory.getSkill(bof);
                int bofLevel = getSkillLevel(skill);
                if (bofLevel > 0) {
                    watk += skill.getEffect(bofLevel).getX();
                    magic += skill.getEffect(bofLevel).getY();
                }
            }

            Integer speedbuff = getBuffedValue(MapleBuffStat.SPEED);
            if (speedbuff != null) {
                speed += speedbuff;
            }
            Integer jumpbuff = getBuffedValue(MapleBuffStat.JUMP);
            if (jumpbuff != null) {
                jump += jumpbuff;
            }
            if (speed > 140) {
                speed = 140;
            }
            if (jump > 123) {
                jump = 123;
            }
            speedMod = speed / 100.0;
            jumpMod = jump / 100.0;
            Integer tmount = getBuffedValue(MapleBuffStat.MONSTER_RIDING);
            if (tmount != null) {
                jumpMod = 1.23;
                switch (tmount) {
                    case 1 -> speedMod = 1.5;
                    case 2 -> speedMod = 1.7;
                    case 3 -> speedMod = 1.8;
                    default -> logger.warn("Unhandeled monster riding level - " + tmount);
                }
            }

            Integer buff = getBuffedValue(MapleBuffStat.MAPLE_WARRIOR);
            if (buff != null) {
                localstr += buff.doubleValue() / 100 * localstr;
                localdex += buff.doubleValue() / 100 * localdex;
                localint_ += buff.doubleValue() / 100 * localint_;
                localluk += buff.doubleValue() / 100 * localluk;
            }
            buff = getBuffedValue(MapleBuffStat.ECHO_OF_HERO);
            if (buff != null) {
                final double d = buff.doubleValue() / 100;
                watk += watk / 100 * d;
                magic += magic / 100 * d;
            }
            buff = getBuffedValue(MapleBuffStat.ARAN_COMBO);
            if (buff != null) {
                watk += buff / 10;
            }
            localmaxbasedamage = calculateMaxBaseDamage(watk);
            if (oldmaxhp != 0 && oldmaxhp != localmaxhp) {
                updatePartyMemberHP();
            }
        }
    }

    public void equipChanged() {
        map.broadcastMessage(this, MaplePacketCreator.updateCharLook(this), false);
        recalcLocalStats();
        enforceMaxHpMp();
        if (client.getPlayer().messenger != null) {
            WorldChannelInterface wci = client.getChannelServer().getWorldInterface();
            try {
                wci.updateMessenger(client.getPlayer().messenger.getId(), client.getPlayer().name, client.getChannel());
            } catch (RemoteException e) {
                client.getChannelServer().reconnectWorld();
            }
        }
    }

    public final List<MaplePet> getPets() {
        return pets;
    }

    public MaplePet[] getPetsAsArray() {
        MaplePet[] petArray = new MaplePet[3];
        if (pets == null) {
            return petArray;
        }

        return pets.toArray(petArray);
    }

    public void addPet(MaplePet pet, boolean lead) {
        if (lead) {
            pets.add(0, pet);
        } else {
            pets.add(pet);
        }
        updatePetEquips(pet);
    }

    public void updatePetEquips(MaplePet pet) {
        final short[] labelPositions = {-121, -131, -139};
        final short[] quotePositions = {-129, -132, -140};
        final MapleInventory equipped = getInventory(MapleInventoryType.EQUIPPED);
        if (pet != null) {
            pet.setLabelRing(equipped.getItem(labelPositions[pets.indexOf(pet)]) != null && PetDataFactory.getNameTag(pet.getItemId()) != -1);
            pet.setQuoteRing(equipped.getItem(quotePositions[pets.indexOf(pet)]) != null && PetDataFactory.getChatBalloon(pet.getItemId()) != -1);
        } else {
            for (MaplePet cPet : pets) {
                cPet.setLabelRing(equipped.getItem(labelPositions[pets.indexOf(cPet)]) != null && PetDataFactory.getNameTag(cPet.getItemId()) != -1);
                cPet.setQuoteRing(equipped.getItem(quotePositions[pets.indexOf(cPet)]) != null && PetDataFactory.getChatBalloon(cPet.getItemId()) != -1);
            }
        }
    }

    public final MaplePet getPet(int index) {
        byte cIndex = 0;
        for (final MaplePet pet : pets) {
            if (cIndex == index) {
                return pet;
            }
            cIndex++;
        }
        return null;
    }

    public final byte getPetIndex(MaplePet pet) {
        return (byte) pets.indexOf(pet);
    }

    public final byte getPetIndex(int petId) {
        byte cIndex = 0;
        for (final MaplePet pet : pets) {
            if (pet.getUniqueId() == petId) {
                return cIndex;
            }
            cIndex++;
        }
        return -1;
    }

    public void updatePet(MaplePet pet) {
        final IItem item = getInventory(MapleInventoryType.CASH).findByPetId(pet.getUniqueId());
        final List<Pair<Short, IItem>> petChanges = new ArrayList<>(2);
        petChanges.add(new Pair<>((short) 3, item));
        petChanges.add(new Pair<>((short) 0, item));
        client.sendPacket(MaplePacketCreator.modifyInventory(false, petChanges));
    }

    public void updatePetPositions(int size, MaplePet removePet) {
        if (removePet != null) {
            pets.remove(removePet);
        }
        final List<Pair<MapleStat, Integer>> stats = new ArrayList<>(3);
        final MapleStat[] petMasks = {MapleStat.PET1, MapleStat.PET2, MapleStat.PET3};
        byte current = 0;
        final MaplePet[] allPets = getPetsAsArray();
        for (MaplePet pet : allPets) {
            stats.add(new Pair<>(petMasks[current], pet != null ? pet.getUniqueId() : 0));
            if (size != 0 && size - 1 == current) {
                break;
            }
            current++;
        }
        client.sendPacket(MaplePacketCreator.petStatUpdate(stats));
    }

    public void unequipPet(MaplePet pet, boolean hunger) {
        cancelFullnessSchedule(getPetIndex(pet));
        pet.setIndex((byte) 0);
        pet.setLabelRing(false);
        pet.setQuoteRing(false);
        pet.saveToDb();
        map.broadcastMessage(this, MaplePacketCreator.showPet(this, pet, true, hunger, false), true);
        updatePetPositions(pets.size(), pet);
        client.sendPacket(MaplePacketCreator.enableActions());
    }

    public FameStatus canGiveFame(MapleCharacter from) {
        if (lastfametime >= System.currentTimeMillis() - 60 * 60 * 24 * 1000) {
            return FameStatus.NOT_TODAY;
        } else if (lastmonthfameids.contains(from.id)) {
            return FameStatus.NOT_THIS_MONTH;
        }
        return FameStatus.OK;
    }

    public void hasGivenFame(MapleCharacter to) {
        lastfametime = System.currentTimeMillis();
        lastmonthfameids.add(to.id);
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO famelog (characterid, characterid_to) VALUES (?, ?)");
            ps.setInt(1, id);
            ps.setInt(2, to.id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            logger.error("ERROR writing famelog for char " + name + " to " + to.name, e);
        }
    }

    public MapleParty getParty() {
        return party;
    }

    public int getPartyId() {
        return party != null ? party.getId() : -1;
    }

    public boolean getPartyInvited() {
        return partyInvite;
    }

    public void setPartyInvited(boolean invite) {
        partyInvite = invite;
    }

    public boolean isMuted() {
        if (Calendar.getInstance().after(unmuteTime)) {
            muted = false;
        }
        return muted;
    }

    public void setMuted(boolean mute) {
        muted = mute;
    }

    public void setUnmuteTime(Calendar time) {
        unmuteTime = time;
    }

    public Calendar getUnmuteTime() {
        return unmuteTime;
    }

    public void setPacketLogging(boolean logging) {
        packetLogging = logging;
    }

    public boolean isPacketLogging() {
        return packetLogging;
    }

    public int getWorld() {
        return world;
    }

    public void setWorld(int world) {
        this.world = world;
    }

    public void setParty(MapleParty party) {
        this.party = party;
    }

    public MapleTrade getTrade() {
        return trade;
    }

    public void setTrade(MapleTrade trade) {
        this.trade = trade;
    }

    public EventInstanceManager getEventInstance() {
        return eventInstance;
    }

    public void setEventInstance(EventInstanceManager eventInstance) {
        this.eventInstance = eventInstance;
    }

    public void setDoor(int i, MapleDoor door) {
        doors[i] = door;
    }

    public void clearDoors() {
        doors[0] = null;
        doors[1] = null;
    }

    public MapleDoor[] getDoors() {
        return doors;
    }

    public boolean canDoor() {
        return canDoor;
    }

    public void disableDoor() {
        canDoor = false;
        TimerManager.getInstance().schedule(() -> canDoor = true, 5000);
    }

    public Map<Integer, MapleSummon> getSummons() {
        return summons;
    }

    public int getChair() {
        return chair;
    }

    public int getItemEffect() {
        return itemEffect;
    }

    public void setChair(int chair) {
        this.chair = chair;
    }

    public void setItemEffect(int itemEffect) {
        this.itemEffect = itemEffect;
    }

    @Override
    public Collection<MapleInventory> allInventories() {
        return Arrays.asList(inventory);
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.PLAYER;
    }

    public MapleGuild getGuild() {
        try {
            return client.getChannelServer().getWorldInterface().getGuild(guildid);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public int getGuildId() {
        return guildid;
    }

    public int getGuildRank() {
        return guildrank;
    }

    public void setGuildId(int _id) {
        guildid = _id;
        if (guildid > 0) {
            if (mgc == null) {
                mgc = new MapleGuildCharacter(this);
            } else {
                mgc.setGuildId(guildid);
            }
        } else {
            mgc = null;
        }
    }

    public void setGuildRank(int _rank) {
        guildrank = _rank;
        if (mgc != null) {
            mgc.setGuildRank(_rank);
        }
    }

    public MapleGuildCharacter getMGC() {
        return mgc;
    }

    public void guildUpdate() {
        if (guildid <= 0) {
            return;
        }

        mgc.setLevel(level);
        mgc.setJobId(job.getId());

        try {
            client.getChannelServer().getWorldInterface().memberLevelJobUpdate(mgc);
        } catch (RemoteException e) {
            logger.error("RemoteExcept while trying to update level/job in guild.", e);
            client.getChannelServer().reconnectWorld();
        }
    }

    public String guildCost() {
        return nf.format(MapleGuild.CREATE_GUILD_COST);
    }

    public String emblemCost() {
        return nf.format(MapleGuild.CHANGE_EMBLEM_COST);
    }

    public String capacityCost() {
        return nf.format(MapleGuild.INCREASE_CAPACITY_COST);
    }

    public void genericGuildMessage(int code) {
        client.sendPacket(MaplePacketCreator.genericGuildMessage((byte) code));
    }

    public void disbandGuild() {
        if (guildid <= 0 || guildrank != 1) {
            logger.warn(name + " tried to disband and he/she is either not in a guild or not leader.");
            return;
        }

        try {
            client.getChannelServer().getWorldInterface().disbandGuild(guildid);
        } catch (Exception e) {
            logger.error("Error while disbanding guild.", e);
        }
    }

    public void increaseGuildCapacity() {
        if (getMeso() < MapleGuild.INCREASE_CAPACITY_COST) {
            client.sendPacket(MaplePacketCreator.serverNotice(1, "You do not have enough mesos."));
            return;
        }

        if (guildid <= 0) {
            logger.info(name + " is trying to increase guild capacity without being in the guild.");
            return;
        }

        try {
            client.getChannelServer().getWorldInterface().increaseGuildCapacity(guildid);
        } catch (Exception e) {
            logger.error("Error while increasing capacity.", e);
            return;
        }

        gainMeso(-MapleGuild.INCREASE_CAPACITY_COST, true, false, true);
    }

    public void saveGuildStatus() {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("UPDATE characters SET guildid = ?, guildrank = ? WHERE id = ?");
            ps.setInt(1, guildid);
            ps.setInt(2, guildrank);
            ps.setInt(3, id);
            ps.execute();
            ps.close();
        } catch (SQLException se) {
            logger.error("SQL error: " + se.getLocalizedMessage(), se);
        }
    }

    public int getAllianceRank() {
        return alliancerank;
    }

    public int getNinjaAmbushDamage(MapleStatEffect mse) {
        return (int) ((str + luk) * 2 * mse.getDamage() / 100.0);
    }

    public long getLastDJTime() {
        return lastDJTime;
    }

    public void setLastDJTime(long dj) {
        lastDJTime = dj;
    }

    public void setAllianceRank(int rank) {
        alliancerank = rank;
    }

    /**
     * Allows you to change someone's NXCash, Maple Points, and Gift Tokens!
     * <p/>
     * Created by Acrylic/Penguins
     *
     * @param type:     1 = PayPal/PayByCash, 2 = Maple Points, 4 = Game Card Cash
     * @param quantity: how much to modify it by. Negatives subtract points, Positives add points.
     */
    public void modifyCSPoints(int type, int quantity, String comment) {
        switch (type) {
            case 1 -> tryDeductSharedCash(-quantity, comment);
            case 2 -> maplePoints += quantity;
            case 4 -> gameCardCash += quantity;
        }
    }

    public void modifyCSPoints(int type, int quantity) {
        modifyCSPoints(type, quantity, "");
    }

    public int getCSPoints(int type) {
        return switch (type) {
            case 1 -> getSharedCash();
            case 2 -> maplePoints;
            case 4 -> gameCardCash;
            default -> 0;
        };
    }

    public boolean tryDeductCSPoints(int type, int delta, String comment) {
        switch (type) {
            case 1:
                return tryDeductSharedCash(delta, comment);
            case 2:
                if (maplePoints < delta) {
                    return false;
                }
                maplePoints -= delta;
                return true;
            case 4:
                if (gameCardCash < delta) {
                    return false;
                }
                gameCardCash -= delta;
                return true;
        }
        return false;
    }

    private int getSharedCash() {
        Connection con = DatabaseConnection.getConnection();
        int ret = 0;
        try {
            PreparedStatement ps = con.prepareStatement("SELECT nx FROM accountgroupsnx WHERE forumuserid = ?");
            ps.setInt(1, client.getForumUserId());
            ps.execute();

            ResultSet rs = ps.getResultSet();
            if (rs.next()) {
                ret = rs.getInt("nx");
            }

            rs.close();
            ps.close();
        } catch (SQLException se) {
            logger.error("SQL error: " + se.getLocalizedMessage(), se);
        }
        return ret;
    }

    private void setSharedCash(int newCash, String comment) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps = con.prepareStatement("INSERT INTO accountgroupsnx (forumuserid, nx) VALUES (?, ?) ON DUPLICATE KEY UPDATE nx = ?");
            ps.setInt(1, client.getForumUserId());
            ps.setInt(2, newCash);
            ps.setInt(3, newCash);
            ps.execute();
            ps.close();

            ps = con.prepareStatement("INSERT INTO accountgroupnxlog (`forumuserid`, `accountid`, `characterid`, `newvalue`, `comment`) VALUES (?, ?, ?, ?, ?)");
            ps.setInt(1, client.getForumUserId());
            ps.setInt(2, client.getAccID());
            ps.setInt(3, id);
            ps.setInt(4, newCash);
            ps.setString(5, comment);
            ps.execute();
            ps.close();
        } catch (SQLException se) {
            logger.error("SQL error: " + se.getLocalizedMessage(), se);
        }
    }

    private boolean tryDeductSharedCash(int deduct, String comment) {
        Connection con = DatabaseConnection.getConnection();
        try {
            int nx = 0;
            con.setAutoCommit(false);
            PreparedStatement ps = con.prepareStatement("SELECT nx FROM accountgroupsnx WHERE forumuserid = ? FOR UPDATE");
            ps.setInt(1, client.getForumUserId());
            ps.execute();

            ResultSet rs = ps.getResultSet();
            if (rs.next()) {
                nx = rs.getInt("nx");
            }

            rs.close();
            ps.close();

            if (nx >= deduct) {
                ps = con.prepareStatement("INSERT INTO accountgroupsnx (forumuserid, nx) VALUES (?, ?) ON DUPLICATE KEY UPDATE nx = nx - ?");
                ps.setInt(1, client.getForumUserId());
                ps.setInt(2, Math.max(0, -deduct));
                ps.setInt(3, deduct);
                ps.execute();
                ps.close();

                ps = con.prepareStatement("INSERT INTO accountgroupnxlog (`forumuserid`, `accountid`, `characterid`, `change`, `newvalue`, `comment`) VALUES (?, ?, ?, ?, ?, ?)");
                ps.setInt(1, client.getForumUserId());
                ps.setInt(2, client.getAccID());
                ps.setInt(3, id);
                ps.setInt(4, -deduct);
                ps.setInt(5, nx - deduct);
                ps.setString(6, comment);
                ps.execute();
                ps.close();

                con.commit();
                return true;
            } else {
                con.rollback();
            }
        } catch (SQLException se) {
            try {
                logger.error("SQL error: " + se.getLocalizedMessage(), se);
                con.rollback();
            } catch (SQLException se2) {
                logger.error("SQL error: " + se2.getLocalizedMessage(), se2);
            }
        } finally {
            try {
                con.setAutoCommit(true);
            } catch (SQLException se) {
                logger.error("SQL error: " + se.getLocalizedMessage(), se);
            }
        }
        return false;
    }

    public boolean haveItem(int itemid, int quantity, boolean checkEquipped, boolean exact) {
        // if exact is true, then possessed must be EXACTLY equal to quantity. else, possessed can be >= quantity
        MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
        MapleInventory iv = inventory[type.ordinal()];
        int possessed = iv.countById(itemid);
        if (checkEquipped) {
            possessed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
        }
        return exact ? possessed == quantity : possessed >= quantity;
    }

    public boolean haveItem(int[] itemids, int quantity, boolean exact) {
        for (int itemid : itemids) {
            MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(itemid);
            MapleInventory iv = inventory[type.ordinal()];
            int possessed = iv.countById(itemid);
            possessed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
            if (possessed >= quantity) {
                if (exact) {
                    if (possessed == quantity) {
                        return true;
                    }
                } else {
                    return true;
                }
            }
        }
        return false;
    }

    public int getItemQuantity(int itemid) {
        return inventory[MapleItemInformationProvider.getInstance().getInventoryType(itemid).ordinal()].countById(itemid);
    }

    public int getEquippedRing(int type) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (IItem item : getInventory(MapleInventoryType.EQUIPPED)) {
            IEquip equip = (IEquip) item;
            if (equip.getRingId() > 0) {
                int itemId = equip.getItemId();
                if (ii.isCrushRing(itemId) && type == 0) {
                    return equip.getRingId();
                }
                if (ii.isFriendshipRing(itemId) && type == 1) {
                    return equip.getRingId();
                }
                if (ii.isWeddingRing(itemId) && type == 2) {
                    return equip.getRingId();
                }
            }
        }
        return 0;
    }

    public int getBuddyCapacity() {
        return buddylist.getCapacity();
    }

    public void setBuddyCapacity(int capacity) {
        buddylist.setCapacity(capacity);
        client.sendPacket(MaplePacketCreator.updateBuddyCapacity(capacity));
    }

    public MapleMessenger getMessenger() {
        return messenger;
    }

    public void setMessenger(MapleMessenger messenger) {
        this.messenger = messenger;
    }

    public void checkMessenger() {
        if (messenger != null && messengerposition < 4 && messengerposition > -1) {
            try {
                WorldChannelInterface wci = client.getChannelServer().getWorldInterface();
                MapleMessengerCharacter messengerplayer = new MapleMessengerCharacter(client.getPlayer(), messengerposition);
                wci.silentJoinMessenger(messenger.getId(), messengerplayer, messengerposition);
                wci.updateMessenger(client.getPlayer().messenger.getId(), client.getPlayer().name, client.getChannel());
            } catch (RemoteException e) {
                client.getChannelServer().reconnectWorld();
            }
        }
    }

    public int getMessengerPosition() {
        return messengerposition;
    }

    public void setMessengerPosition(int position) {
        messengerposition = position;
    }

    public boolean getNXCodeValid(String code) throws SQLException {

        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("SELECT `valid` FROM nxcode WHERE code = ?");
        ps.setString(1, code);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getInt("valid") != 0;
        }

        rs.close();
        ps.close();

        return false;
    }

    public int getNXCodeType(String code) throws SQLException {

        int type = -1;
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("SELECT `type` FROM nxcode WHERE code = ?");
        ps.setString(1, code);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            type = rs.getInt("type");
        }

        rs.close();
        ps.close();

        return type;
    }

    public int getNXCodeItem(String code) throws SQLException {

        int item = -1;
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("SELECT `item` FROM nxcode WHERE code = ?");
        ps.setString(1, code);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            item = rs.getInt("item");
        }
        rs.close();
        ps.close();

        return item;
    }

    public void setNXCodeUsed(String code) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("UPDATE nxcode SET `valid` = 0 WHERE code = ?");
        ps.setString(1, code);
        ps.executeUpdate();
        ps.close();
        PreparedStatement ps2 = con.prepareStatement("UPDATE nxcode SET `user` = ? WHERE code = ?");
        ps2.setString(1, name);
        ps2.setString(2, code);
        ps2.executeUpdate();
        ps2.close();
    }

    public void setInCS(boolean inCS) {
        this.inCS = inCS;
    }

    public boolean inCS() {
        return inCS;
    }

    public void setInMTS(boolean inMTS) {
        this.inMTS = inMTS;
    }

    public boolean inMTS() {
        return inMTS;
    }

    public void addCooldown(int skillId, long startTime, long length, ScheduledFuture<?> timer) {
        if (!hasGMLevel(5)) {
            if (cooldowns.containsKey(skillId)) {
                cooldowns.get(skillId).getTime().cancel(true);
                cooldowns.get(skillId).setTime(null);
                cooldowns.remove(skillId);
            }
            cooldowns.put(skillId, new SkillCooldown(skillId, startTime, length, timer));
        } else {
            timer.cancel(true);
            timer = null;
            client.sendPacket(MaplePacketCreator.skillCooldown(skillId, 0));
        }
    }

    public void giveCoolDowns(final int skillid, long starttime, long length) {
        int time = (int) (length + starttime - System.currentTimeMillis());
        ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(this, skillid), time);
        addCooldown(skillid, System.currentTimeMillis(), time, timer);
    }

    public void removeCooldown(int skillId) {
        if (cooldowns.containsKey(skillId)) {
            cooldowns.get(skillId).getTime().cancel(true);
            cooldowns.get(skillId).setTime(null);
            cooldowns.remove(skillId);
        }
        client.sendPacket(MaplePacketCreator.skillCooldown(skillId, 0));
    }

    public boolean skillisCooling(int skillId) {
        return cooldowns.containsKey(skillId);
    }

    public List<SkillCooldown> getAllCooldowns() {
        List<SkillCooldown> ret = new ArrayList<>();
        ret.addAll(cooldowns.values());
        return ret;
    }

    public void resetCooldowns() {
        for (SkillCooldown cooldown : cooldowns.values()) {
            cooldown.getTime().cancel(true);
        }
        cooldowns.clear();
    }

    public void giveDebuff(MapleDisease disease, MobSkill skill) {
        giveDebuff(disease, skill, false);
    }

    public void giveDebuff(final MapleDisease disease, MobSkill skill, boolean forever) {
        if (!isAlive() || isAlive() && diseases.contains(disease) || hasGMLevel(3)) {
            return;
        }
        switch (disease) {
            case GM_DISABLE_SKILL:
                dispelDebuff(MapleDisease.SEAL);
                break;
            case SEAL:
                if (diseases.contains(MapleDisease.GM_DISABLE_SKILL)) {
                    return;
                }
            case GM_DISABLE_MOVEMENT:
                dispelDebuff(MapleDisease.STUN);
                break;
            case STUN:
                if (diseases.contains(MapleDisease.GM_DISABLE_MOVEMENT)) {
                    return;
                }
        }
        if (diseases.size() < 2 && (!isActiveBuffedValue(2321005) || disease.equals(MapleDisease.GM_DISABLE_SKILL) || disease.equals(MapleDisease.GM_DISABLE_MOVEMENT))) {
            diseases.add(disease);
            List<Pair<MapleDisease, Integer>> debuff = Collections.singletonList(new Pair<>(disease, skill.getX()));
            final long mask = disease.getValue();
            client.sendPacket(MaplePacketCreator.giveDebuff(mask, debuff, skill));
            map.broadcastMessage(this, MaplePacketCreator.giveForeignDebuff(id, mask, skill), false);
            if (!forever) {
                TimerManager.getInstance().schedule(() -> dispelDebuff(disease), skill.getDuration());
            }
        }
    }

    public List<MapleDisease> getDiseases() {
        return diseases;
    }

    public void dispelDebuff(MapleDisease debuff) {
        if (diseases.remove(debuff)) {
            final long mask = debuff.getValue();
            client.sendPacket(MaplePacketCreator.cancelDebuff(mask));
            map.broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask), false);
        }
    }

    public void dispelDebuffs() {
        synchronized (diseases) {
            Iterator<MapleDisease> iter = diseases.iterator();
            while (iter.hasNext()) {
                final MapleDisease disease = iter.next();
                if (!disease.equals(MapleDisease.SEDUCE) && !disease.equals(MapleDisease.STUN) && !disease.equals(MapleDisease.GM_DISABLE_SKILL) && !disease.equals(MapleDisease.GM_DISABLE_MOVEMENT)) {
                    final long mask = disease.getValue();
                    client.sendPacket(MaplePacketCreator.cancelDebuff(mask));
                    map.broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask), false);
                    iter.remove();
                }
            }
        }
    }

    public void cancelAllDebuffs() {
        synchronized (diseases) {
            Iterator<MapleDisease> iter = diseases.iterator();
            while (iter.hasNext()) {
                final long mask = iter.next().getValue();
                client.sendPacket(MaplePacketCreator.cancelDebuff(mask));
                map.broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask), false);
                iter.remove();
            }
        }
    }

    public void setLevel(int level) {
        this.level = level - 1;
    }

    public void setMap(int PmapId) {
        mapid = PmapId;
    }

    public void sendNote(String to, String msg) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("INSERT INTO notes (`to`, `from`, `message`, `timestamp`) VALUES (?, ?, ?, ?)");
        ps.setString(1, to);
        ps.setString(2, name);
        ps.setString(3, msg);
        ps.setLong(4, System.currentTimeMillis());
        ps.executeUpdate();
        ps.close();
    }

    public void sendNote(int recId, String msg) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement p = con.prepareStatement("SELECT name FROM characters WHERE id = ?");
        p.setInt(1, recId);
        ResultSet rs = p.executeQuery();
        String to = rs.getString("name");
        rs.close();
        p.close();
        sendNote(to, msg);
    }

    public void showNote() throws SQLException {
        Connection con = DatabaseConnection.getConnection();

        PreparedStatement ps = con.prepareStatement("SELECT * FROM notes WHERE `to` = ?", ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
        ps.setString(1, name);
        ResultSet rs = ps.executeQuery();

        rs.last();
        int count = rs.getRow();
        rs.first();

        client.sendPacket(MaplePacketCreator.showNotes(rs, count));
        ps.close();
    }

    public void deleteNote(int id) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("DELETE FROM notes WHERE `id`=?");
        ps.setInt(1, id);
        ps.executeUpdate();
        ps.close();
    }

    public void checkBerserk() {
        if (berserkSchedule != null) {
            berserkSchedule.cancel(false);
        }
        if (!job.equals(MapleJob.DARKKNIGHT)) {
            return;
        }
        final MapleCharacter chr = this;
        final ISkill skill = SkillFactory.getSkill(1320006);
        final int skilllevel = getSkillLevel(skill);
        if (skilllevel > 0) {
            berserk = chr.hp * 100 / chr.localmaxhp <= skill.getEffect(getSkillLevel(skill)).getX();
            berserkSchedule = TimerManager.getInstance().register(() -> {
                getClient().sendPacket(MaplePacketCreator.showOwnBerserk((byte) getLevel(), skilllevel, berserk));
                getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBerserk(getId(), skilllevel, (byte) getLevel(), berserk), false);
            }, 5000, 3000);
        }
    }

    private void prepareBeholderEffect() {
        if (beholderHealingSchedule != null && beholderBuffSchedule != null) {
            beholderHealingSchedule.cancel(false);
            beholderBuffSchedule.cancel(false);
        }
        if (getSkillLevel(SkillFactory.getSkill(1320008)) > 0) {
            final MapleStatEffect healEffect = SkillFactory.getSkill(1320008).getEffect(getSkillLevel(SkillFactory.getSkill(1320008)));
            beholderHealingSchedule = TimerManager.getInstance().register(() -> {
                ISkill berserk = SkillFactory.getSkill(1320006);
                if (getSkillLevel(berserk) > 0) {
                    double berserkX = berserk.getEffect(getSkillLevel(berserk)).getX();
                    if (hp < localmaxhp * berserkX / 100) {
                        if (hp + healEffect.getHp() > localmaxhp * berserkX / 100) {
                            addHP((int) (localmaxhp * berserkX / 100 - hp));
                        } else {
                            addHP(healEffect.getHp());
                        }
                    }
                }
                getClient().sendPacket(MaplePacketCreator.showOwnBuffEffect(1321007, 2, (byte) getLevel()));
                getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.summonSkill(getId(), 1321007, 5), true);
                getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), 1321007, 2, (byte) getLevel(), (byte) 3), false);
            }, healEffect.getX() * 1000, healEffect.getX() * 1000);
        }
        if (getSkillLevel(SkillFactory.getSkill(1320009)) > 0) {
            final MapleStatEffect buffEffect = SkillFactory.getSkill(1320009).getEffect(getSkillLevel(SkillFactory.getSkill(1320009)));
            beholderBuffSchedule = TimerManager.getInstance().register(() -> {
                buffEffect.applyTo(MapleCharacter.this);
                getClient().sendPacket(MaplePacketCreator.showOwnBuffEffect(1321007, 2, (byte) getLevel()));
                getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.summonSkill(getId(), 1321007, Randomizer.nextInt(3) + 6), true);
                getMap().broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), 1321007, 2, (byte) getLevel(), (byte) 3), false);
            }, buffEffect.getX() * 1000, buffEffect.getX() * 1000);
        }
    }

    public void setChalkboard(String text) {
        chalktext = text;
    }

    public String getChalkboard() {
        return chalktext;
    }

    public List<MapleRing> getCrushRings() {
        Collections.sort(crushRings);
        return crushRings;
    }

    public List<MapleRing> getFriendshipRings() {
        Collections.sort(friendshipRings);
        return friendshipRings;
    }

    public List<MapleRing> getMarriageRings() {
        Collections.sort(marriageRings);
        return marriageRings;
    }

    public int getMarriageQuestLevel() {
        return marriageQuestLevel;
    }

    public void setMarriageQuestLevel(int nf) {
        marriageQuestLevel = nf;
    }

    public void addMarriageQuestLevel() {
        marriageQuestLevel += 1;
    }

    public void subtractMarriageQuestLevel() {
        marriageQuestLevel -= 1;
    }

    public void setCanTalk(int yesno) {
        canTalk = yesno;
    }

    public int getCanTalk() {
        return canTalk;
    }

    public void setZakumLevel(int level) {
        zakumLvl = level;
    }

    public int getZakumLevel() {
        return zakumLvl;
    }

    public void addZakumLevel() {
        zakumLvl += 1;
    }

    public void subtractZakumLevel() {
        zakumLvl -= 1;
    }

    public void setMarried(int mmm) {
        married = mmm;
    }

    public void setPartnerId(int pem) {
        partnerid = pem;
    }

    public int isMarried() {
        return married;
    }

    public MapleCharacter getPartner() {
        return client.getChannelServer().getPlayerStorage().getCharacterById(partnerid);
    }

    public int getPartnerId() {
        return partnerid;
    }

    public void changePage(int page) {
        currentPage = page;
    }

    public void changeTab(int tab) {
        currentTab = tab;
    }

    public void changeType(int type) {
        currentType = type;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getCurrentTab() {
        return currentTab;
    }

    public int getCurrentType() {
        return currentType;
    }

    public void unstick() {
        try {
            client.disconnect();
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE accounts SET loggedin = 0 WHERE id = ?");
            ps.setInt(1, accountid);
            ps.executeUpdate();
            ps.close();
            PreparedStatement ps2 = con.prepareStatement("UPDATE characters SET loggedin = 0 WHERE accountid = ?");
            ps2.setInt(1, id);
            ps2.executeUpdate();
            ps2.close();
        } catch (Exception e) {
            logger.error("Error unsticking character: ");
            e.printStackTrace();
        }
    }

    public void disposePlayerShop() {
        IMaplePlayerShop playershop = playerShop;
        if (playershop.isOwner(this)) {
            if (playershop.getShopType() == 1) {
                HiredMerchant hm = (HiredMerchant) playershop;
                if (hm.isSpawned()) {
                    hm.setOpen(true);
                } else if (hasHiredMerchantTicket()) {
                    setHasMerchant(true);
                    hm.setOpen(true);
                    hm.spawned();
                    map.addMapObject(hm);
                    map.broadcastMessage(MaplePacketCreator.spawnHiredMerchant(hm));
                    playerShop = null;
                    //saveToDB(true);
                } else {
                    client.disconnect();
                }
            } else if (playershop.getShopType() == 2) {
                for (MaplePlayerShopItem items : playershop.getItems()) {
                    if (items.getBundles() > 0) {
                        IItem item = items.getItem();
                        short x = item.getQuantity();
                        short y = (short) (x * items.getBundles());
                        if (y < 1) {
                            y = 1;
                        }
                        item.setQuantity(y);
                        if (MapleInventoryManipulator.canHold(client, Collections.singletonList(item))) {
                            if (!MapleInventoryManipulator.addByItem(client, item, "returned from shop", true).isEmpty()) {
                                items.setBundles((short) 0);
                            }

                            try {
                                playershop.updateItem(items);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                playershop.removeAllVisitors(3, 1);
                playershop.closeShop(); // wont happen unless some idiot hacks, hopefully ?
            }
        } else {
            playershop.removeVisitor(this);
        }
    }

    public boolean getSmegaEnabled() {
        return smegaEnabled;
    }

    public void setSmegaEnabled(boolean x) {
        smegaEnabled = x;
    }

    public void resetAfkTimer() {
        afkTimer = System.currentTimeMillis();
    }

    public long getIdleTimer() {
        return System.currentTimeMillis() - afkTimer;
    }

    public void resetMobKillTimer() {
        lastMobKillTime = System.currentTimeMillis();
    }

    public long getMobKillTimer() {
        return System.currentTimeMillis() - lastMobKillTime;
    }

    public long getLoggedInTimer() {
        return System.currentTimeMillis() - loggedInTimer;
    }

    public void toggleEnergyChargeForever() {
        noEnergyChargeDec = !noEnergyChargeDec;
    }

    public boolean isEnergyChargeForever() {
        return noEnergyChargeDec;
    }

    public int getEnergyBar() {
        return energybar;
    }

    public void handleEnergyChargeGain(int amt, final boolean gm) {
        final int skillId = job.isA(MapleJob.THUNDERBREAKER2) ? 15100004 : 5110001;
        ISkill energycharge = SkillFactory.getSkill(skillId);
        int energyChargeSkillLevel = getSkillLevel(energycharge);
        MapleStatEffect ceffect = energycharge.getEffect(energyChargeSkillLevel);
        TimerManager tMan = TimerManager.getInstance();
        if (energyDecrease != null) {
            energyDecrease.cancel(false);
        }
        if (energyChargeSkillLevel > 0) {
            if (energybar < 10000) {

                energybar = energybar + amt;
                if (energybar > 10000) {
                    energybar = 10000;
                }
                client.sendPacket(MaplePacketCreator.giveEnergyCharge(energybar));
                client.sendPacket(MaplePacketCreator.showOwnBuffEffect(skillId, 2, (byte) level));
                map.broadcastMessage(MaplePacketCreator.showBuffeffect(id, skillId, 2, (byte) level, (byte) 3));
                if (energybar == 10000) {
                    map.broadcastMessage(MaplePacketCreator.giveForeignEnergyCharge(id, energybar));
                }
                if (!noEnergyChargeDec) {
                    energyDecrease = tMan.register(() -> {

                        if (energybar < 10000 && !isEnergyChargeForever()) {
                            if (energybar - 200 < 0) {
                                energybar = 0;
                                if (energyDecrease != null) {
                                    energyDecrease.cancel(false);
                                }
                            } else {
                                energybar = energybar - 200;
                            }
                            getClient().sendPacket(MaplePacketCreator.giveEnergyCharge(energybar));
                        }

                    }, 10000, 10000);
                } else {
                    if (energyDecrease != null && !energyDecrease.isCancelled()) {
                        energyDecrease.cancel(false);
                    }
                    energyDecrease = null;
                }
            }
            if (energybar == 10000) {
                if (!noEnergyChargeDec) {
                    tMan.schedule(() -> {
                        getClient().sendPacket(MaplePacketCreator.giveEnergyCharge(0));
                        getMap().broadcastMessage(MaplePacketCreator.giveForeignEnergyCharge(id, energybar));
                        energybar = 0;
                    }, ceffect.getDuration());
                }
            }

        }
    }

    public void leaveParty() {
        WorldChannelInterface wci = client.getChannelServer().getWorldInterface();
        MaplePartyCharacter partyplayer = new MaplePartyCharacter(this);
        if (party != null) {
            try {
                if (partyplayer.equals(party.getLeader())) { // disband
                    wci.updateParty(party.getId(), PartyOperation.DISBAND, partyplayer);
                    if (eventInstance != null) {
                        eventInstance.disbandParty();
                    }
                } else {
                    wci.updateParty(party.getId(), PartyOperation.LEAVE, partyplayer);
                    if (eventInstance != null) {
                        eventInstance.leftParty(this);
                    }
                }
            } catch (RemoteException e) {
                client.getChannelServer().reconnectWorld();
            }
            party = null;
        }
    }

    public int getBossQuestRepeats() {
        return bossRepeats;
    }

    public void setBossQuestRepeats(int repeats) {
        bossRepeats = repeats;
    }

    public void updateBossQuestRepeats() {
        if (Calendar.getInstance().getTimeInMillis() > nextBQ) {
            bossRepeats = 0;
        }
    }

    public void updateNextBossQuest() {
        nextBQ = Calendar.getInstance().getTimeInMillis() + 1000 * 60 * 60 * 24;
    }

    public String getNextBossQuest() {
        return new Timestamp(nextBQ).toString();
    }

    public void setBossPoints(int points) {
        bossPoints = points;
    }

    public int getBossPoints() {
        return bossPoints;
    }

    public void setAchievementFinished(int id) {
        finishedAchievements.add(id);
    }

    public boolean achievementFinished(int achievementid) {
        return finishedAchievements.contains(achievementid);
    }

    public void finishAchievement(int id) {
        if (!achievementFinished(id) || MapleAchievements.getInstance().getById(id).isRepeatable()) {
            if (isAlive()) {
                MapleAchievements.getInstance().getById(id).finishAchievement(this);
            }
        }
    }

    public List<Integer> getFinishedAchievements() {
        return finishedAchievements;
    }

    public boolean hasMerchant() {
        return hasMerchant;
    }

    public void setHasMerchant(boolean set) {
        try {
            Connection con = DatabaseConnection.getConnection();
            PreparedStatement ps = con.prepareStatement("UPDATE characters SET hasmerchant = ? WHERE id = ?");
            ps.setInt(1, set ? 1 : 0);
            ps.setInt(2, id);
            ps.executeUpdate();
            ps.close();
        } catch (SQLException se) {
            se.printStackTrace();
        }
        hasMerchant = set;
    }

    public int getBossLog(String boss) {
        Connection con = DatabaseConnection.getConnection();
        try {
            int count = 0;
            PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM bosslog WHERE characterid = ? AND bossid = ? AND lastattempt >= subtime(CURRENT_TIMESTAMP, '1 0:0:0.0')");
            ps.setInt(1, id);
            ps.setString(2, boss);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            } else {
                count = -1;
            }
            rs.close();
            ps.close();
            return count;
        } catch (Exception Ex) {
            logger.error("Error while read bosslog.", Ex);
            return -1;
        }
    }

    public void setBossLog(String boss) {
        Connection con = DatabaseConnection.getConnection();
        try {
            PreparedStatement ps;
            ps = con.prepareStatement("insert into bosslog (characterid, bossid) values (?,?)");
            ps.setInt(1, id);
            ps.setString(2, boss);
            ps.executeUpdate();
            ps.close();
        } catch (Exception Ex) {
            logger.error("Error while insert bosslog.", Ex);
        }
    }

    public String getJobName() {
        return job.getJobNameAsString();
    }

	/*public void createPlayerNPC() {
		getPlayerNPC().createPlayerNPC(this, getPlayerNPCMapId());
	}

	public int getPlayerNPCMapId() {
		int jobId = getJob().getId();
		if (jobId >= 100 && jobId <= 132) {
			return 102000003;
		} else if (jobId >= 200 && jobId <= 232) {
			return 101000003;
		} else if (jobId >= 300 && jobId <= 322) {
			return 100000201;
		} else if (jobId >= 400 && jobId <= 422) {
			return 103000003;
		} else if (jobId >= 500 && jobId <= 522) {
			return 120000000;
		} else {
			return 104000000;
		}
	} */

	/*public MaplePlayerNPC getPlayerNPC() {
		MaplePlayerNPC pnpc = new MaplePlayerNPC(this);
		return pnpc;
	}*/

	/*public void updateLater() {
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO waiting_players (id, name, job, mapid) VALUES (?, ?, ?, ?)");
			ps.setInt(1, id);
			ps.setString(2, name);
			ps.setString(3, getJobName());
			ps.setInt(4, getPlayerNPCMapId());
			ps.executeUpdate();
			ps.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	} */

    public void handleBattleShipHpLoss(int damage) {
        ISkill ship = SkillFactory.getSkill(5221006);
        int maxshipHP = getSkillLevel(ship) * 4000 + (level - 120) * 2000;
        MapleStatEffect effect = ship.getEffect(getSkillLevel(ship));
        battleshipHP -= damage;
        if (battleshipHP <= 0) {
            dispelSkill(5221006);
            ScheduledFuture<?> timer = TimerManager.getInstance().schedule(new CancelCooldownAction(this, 5221006), effect.getCooldown() * 1000);
            addCooldown(5221006, System.currentTimeMillis(), effect.getCooldown() * 1000, timer);
            battleshipHP = maxshipHP;
            client.sendPacket(MaplePacketCreator.skillCooldown(5221006, effect.getCooldown()));
            try {
                dropMessage("Your Battle Ship has been destroyed by the monster with incredible force!");
            } catch (NullPointerException npe) {
                npe.printStackTrace();
            }
        }
        client.sendPacket(MaplePacketCreator.updateBattleShipHP(id, battleshipHP));
    }

	/*public boolean hasPlayerNPC() {
		return playerNPC;
	}*/

    public int getBattleShipHP() {
        return battleshipHP;
    }

    public int setBattleShipHP(int set) {
        return battleshipHP = set;
    }

    public List<Integer> getTeleportRockMaps(int type) {
        if (type == 0) {
            return rockMaps;
        } else {
            return vipRockMaps;
        }
    }

    public void addTeleportRockMap(Integer mapId, int type) {
        if (type == 0 && rockMaps.size() < 5 && !rockMaps.contains(mapId)) {
            rockMaps.add(mapId);
        } else if (vipRockMaps.size() < 10 && !vipRockMaps.contains(mapId)) {
            vipRockMaps.add(mapId);
        }
    }

    public void deleteTeleportRockMap(Integer mapId, int type) {
        if (type == 0) {
            rockMaps.remove(mapId);
        } else {
            vipRockMaps.remove(mapId);
        }
    }

    public MapleMount getMount() {
        return mount;
    }

    public void setMount(MapleMount newmount) {
        mount = newmount;
    }

    public void checkDuey() {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = con.prepareStatement("SELECT * FROM dueypackages WHERE receiverid = ? AND alerted = 0");
            ps.setInt(1, id);
            rs = ps.executeQuery();
            if (rs.next()) {
                PreparedStatement ps2 = con.prepareStatement("UPDATE dueypackages SET alerted = 1 WHERE receiverid = ?");
                ps2.setInt(1, id);
                ps2.executeUpdate();
                ps2.close();
                client.sendPacket(MaplePacketCreator.sendDueyMessage(Actions.TOCLIENT_PACKAGE_MSG.getCode()));
            }
        } catch (SQLException SQLe) {
            SQLe.printStackTrace();
        } finally {
            try {
                if (ps != null) {
                    ps.close();
                }
                if (rs != null) {
                    rs.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isCPQChallenged() {
        return CPQChallenged;
    }

    public void setCPQChallenged(boolean CPQChallenged) {
        this.CPQChallenged = CPQChallenged;
    }

    public int getCP() {
        return CP;
    }

    public void gainCP(int gain) {
        if (gain > 0) {
            totalCP = totalCP + gain;
        }
        CP = CP + gain;
        if (party != null) {
            monsterCarnival.setCP(monsterCarnival.getCP(team) + gain, team);
            if (gain > 0) {
                monsterCarnival.setTotalCP(monsterCarnival.getTotalCP(team) + gain, team);
            }
        }
        if (CP > totalCP) {
            totalCP = CP;
        }
        client.sendPacket(MaplePacketCreator.CPUpdate(false, CP, totalCP, team));
        if (party != null && team != -1) {
            map.broadcastMessage(MaplePacketCreator.CPUpdate(true, monsterCarnival.getCP(team), monsterCarnival.getTotalCP(team), team));
        } else {
            logger.warn(name + " is either not in a party or .. team: " + team);
        }
    }

    public void setTotalCP(int a) {
        totalCP = a;
    }

    public void setCP(int a) {
        CP = a;
    }

    public int getTotalCP() {
        return totalCP;
    }

    public void resetCP() {
        CP = 0;
        totalCP = 0;
        monsterCarnival = null;
    }

    public MapleMonsterCarnival getMonsterCarnival() {
        return monsterCarnival;
    }

    public void setMonsterCarnival(MapleMonsterCarnival monsterCarnival) {
        this.monsterCarnival = monsterCarnival;
    }

    public int getTeam() {
        return team;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public int getCPQRanking() {
        return CPQRanking;
    }

    public void setCPQRanking(int newCPQRanking) {
        CPQRanking = newCPQRanking;
    }

    public boolean isBanned() {
        return banned;
    }

    public boolean needsParty() {
        return needsParty;
    }

    public int getNeedsPartyMaxLevel() {
        return needsPartyMaxLevel;
    }

    public int getNeedsPartyMinLevel() {
        return needsPartyMinLevel;
    }

    public void setNeedsParty(boolean bool, int minlvl, int maxlvl) {
        needsParty = bool;
        needsPartyMinLevel = minlvl;
        needsPartyMaxLevel = maxlvl;
    }

    public boolean hasPlayerShopTicket() {
        int[] itemIds = new int[6]; // list of playerstore coupons
        for (int itemId = 0; itemId < itemIds.length; itemId++) {
            itemIds[itemId] = itemId + 5140000;
        }
        return haveItem(itemIds, 1, false);
    }

    public boolean hasHiredMerchantTicket() {
        int[] itemIds = new int[13]; // list of hired merchant store coupons
        for (int itemId = 0; itemId < itemIds.length; itemId++) {
            itemIds[itemId] = itemId + 5030000;
        }
        return haveItem(itemIds, 1, false);
    }

    public boolean hasGodmode() {
        return godmode;
    }

    public void setGodmode(boolean onoff) {
        godmode = onoff;
    }

    public void addToCancelBuffPackets(MapleStatEffect effect, long startTime) {
        buffsToCancel.put(startTime, effect);
    }

    public void cancelSavedBuffs() {
        Object[] keysarray = buffsToCancel.keySet().toArray();
        long key = 0;
        for (Object o : keysarray) {
            key = (Long) o;
            cancelEffect(buffsToCancel.get(key), false, key);
        }
        buffsToCancel.clear();
    }

    public boolean isQuestDebug() {
        return questDebug && isGM();
    }

    public void toggleQuestDebug() {
        questDebug = !questDebug;
    }

    public boolean isGMLegit() {
        return isGMLegit;
    }

    public void setDJ(boolean dj) {
        isDJ = dj;
    }

    public boolean isDJ() {
        return isDJ;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getTimeLimit() {
        return timeLimit;
    }

    public void setStartTime(long time) {
        startTime = time;
    }

    public void setTimeLimit(long limit) {
        timeLimit = limit;
    }

    public void setTimer(long time) {
        timeLimit = time * 60;
        startTime = System.currentTimeMillis();
    }

    public void setTimerSec(long time) {
        timeLimit = time;
        startTime = System.currentTimeMillis();
    }

    public void setWhiteText(boolean white) {
        whiteText = white;
    }

    public boolean getWhiteText() {
        return whiteText;
    }

    public void setSlotMerging(boolean merge) {
        slotMerging = merge;
    }

    public boolean isSlotMerging() {
        return slotMerging;
    }

    public boolean isMinerving() {
        return isMinerving;
    }

    public void minerving(boolean m) {
        isMinerving = m;
    }

    public int[] getWishList() {
        return wishlist;
    }

    public int getWishListSize() {
        int size = 0;
        for (int i = 0; i < 10; i++) {
            if (wishlist[i] != 0) {
                size++;
            }
        }
        return size;
    }

    public void setWishList(int[] list) {
        wishlist = list;
    }

    public void unknownStateTask() {
        if (unknownSchedule != null) {
            unknownSchedule.cancel(true);
        }
        unknownSchedule = TimerManager.getInstance().register(() -> getClient().sendPacket(MaplePacketCreator.unknownStatus()), 420000, 420000);
    }

    public MonsterBook getMonsterBook() {
        return monsterbook;
    }

    public void setMonsterBookCover(int bookCover) {
        this.bookCover = bookCover;
    }

    public int getMonsterBookCover() {
        return bookCover;
    }

    public void checkCygnusCreationStatus() {
        String qr = getQuestEx(1055).getQuestRecord();
        if (!job.isA(MapleJob.NOBLESSE) && level > 20 && (qr.matches("") || !qr.split(";")[1].substring(6).matches("true"))) {
            TimerManager.getInstance().schedule(() -> {
                if (isAlive() && (getMapId() < 913040000 || getMapId() > 913040006)) {
                    startCygnusMovie();
                }
            }, 10000);
        }
    }

    public void startCygnusMovie() {
        updateQuest(new MapleQuestStatus(1055, MapleQuestStatus.Status.STARTED, "clear=start"), false, true, false, false);
        client.sendPacket(MaplePacketCreator.hideUI(true));
        client.sendPacket(MaplePacketCreator.lockWindows(true));
        updateQuest(new MapleQuestStatus(1055, MapleQuestStatus.Status.STARTED, "map=" + getMapId() + ";clear=start"), false, true, false, false);
        MapleMap introMap = client.getChannelServer().getMapFactory().getMap(913040000);
        changeMap(introMap, introMap.getPortal(0));
    }

    public void maxSkills() {
        Collection<ISkill> allSkills = SkillFactory.getAllSkills();
        for (ISkill skill : allSkills) {
            changeSkillLevel(skill, skill.getMaxLevel(), skill.getMaxLevel());
        }
    }

    public MapleDojo getDojo() {
        if (dojo == null) // new characters
        {
            dojo = new MapleDojo(0, 0);
        }
        return dojo;
    }

    public void offBeacon() {
        offBeacon(false);
    }

    public void offBeacon(boolean bf) {
        hasBeacon = false;
        beaconOid = -1;
        if (bf) {
            cancelEffectFromBuffStat(MapleBuffStat.HOMING_BEACON);
        }
    }

    public boolean hasBeacon() {
        return hasBeacon;
    }

    public void setBeacon(int oid) {
        beaconOid = oid;
    }

    public int getBeacon() {
        return beaconOid;
    }

    public Short getHammerSlot() {
        return hammerSlot;
    }

    public void setHammerSlot(Short hammerSlot) {
        this.hammerSlot = hammerSlot;
    }

    public void stopAllTimers() {
        if (dragonBloodSchedule != null) {
            dragonBloodSchedule.cancel(true);
            dragonBloodSchedule = null;
        }
        if (mapTimeLimitTask != null) {
            mapTimeLimitTask.cancel(true);
            mapTimeLimitTask = null;
        }
        if (fullnessSchedule != null) {
            fullnessSchedule.cancel(false);
            fullnessSchedule = null;
        }
        if (fullnessSchedule_1 != null) {
            fullnessSchedule_1.cancel(false);
            fullnessSchedule_1 = null;
        }
        if (fullnessSchedule_2 != null) {
            fullnessSchedule_2.cancel(false);
            fullnessSchedule_2 = null;
        }
        if (hpDecreaseTask != null) {
            hpDecreaseTask.cancel(true);
            hpDecreaseTask = null;
        }
        if (beholderHealingSchedule != null) {
            beholderHealingSchedule.cancel(true);
            beholderHealingSchedule = null;
        }
        if (beholderBuffSchedule != null) {
            beholderBuffSchedule.cancel(true);
            beholderBuffSchedule = null;
        }
        if (berserkSchedule != null) {
            berserkSchedule.cancel(true);
            berserkSchedule = null;
        }
        if (unknownSchedule != null) {
            unknownSchedule.cancel(true);
            unknownSchedule = null;
        }
        if (energyDecrease != null) {
            energyDecrease.cancel(true);
            energyDecrease = null;
        }
        if (checkForExpiredItems != null) {
            checkForExpiredItems.cancel(false);
            checkForExpiredItems = null;
        }
        TimerManager.getInstance().purgeTasks();
    }

    public void checkForExpiredItems() {
        if (checkForExpiredItems != null) {
            checkForExpiredItems.cancel(false);
        }
        checkForExpiredItems = TimerManager.getInstance().register(() -> {

            for (MapleInventoryType singleInv : invTypes) {
                inventoryItems.addAll(getInventory(singleInv).list());
                for (IItem item : inventoryItems) {
                    if (System.currentTimeMillis() >= item.getExpiration().getTime()) {
                        if ((item.getFlag() & ItemFlag.LOCK.getValue()) == 1) {
                            item.setFlag((short) (item.getFlag() ^ ItemFlag.LOCK.getValue()));
                            item.setExpiration(FileTimeUtil.getDefaultTimestamp());
                            expiredItemLocks.add(new Pair<>((short) 0, item));
                        } else {
                            expiredItems.add(MapleInventoryManipulator.removeItemFromSlot(getClient(), singleInv, item.getPosition(), item.getQuantity(), false));
                        }
                    }
                }
                inventoryItems.clear();
            }
            if (!expiredItemLocks.isEmpty()) {
                getClient().sendPacket(MaplePacketCreator.modifyInventory(false, expiredItemLocks));
                getClient().sendPacket(MaplePacketCreator.showItemExpirations(expiredItemLocks, true));
                expiredItemLocks.clear();
            }
            if (!expiredItems.isEmpty()) {
                getClient().sendPacket(MaplePacketCreator.modifyInventory(false, expiredItems));
                getClient().sendPacket(MaplePacketCreator.showItemExpirations(expiredItems, false));
                expiredItems.clear();
                equipChanged();
            }
        }, 1800000, 60000);
    }

    public void setMakingMerch(boolean s) {
        isMakingMerchant = s;
    }

    public boolean isMakingMerch() {
        return isMakingMerchant;
    }

    public void setPassMerchTest(boolean b) {
        passMerchTest = b;
    }

    public boolean passedMerchTest() {
        return passMerchTest;
    }

    public boolean isUsingRemoteGachaponTicket() {
        return isUsingRemoteGachapon;
    }

    public void setUsingRemoteGachaponTicket(boolean using) {
        isUsingRemoteGachapon = using;
    }

    public long getLastDeath() {
        return lastDeath;
    }

    public void setLastDeath(long time) {
        lastDeath = time;
    }

    public long getLastDisconnection() {
        return lastDisconnection;
    }

    public void setLastDisconnection(long time) {
        lastDisconnection = time;
    }

    public MapleCharacter makeFakeCopy() {
        MapleClient c = new MapleClient();
        MapleCharacter chr = MapleCharacter.getDefault(c, 0, true);
        c.setPlayer(chr);

        chr.name = "TimedCommandFakeCopyOfAGM" + timedFakeID;
        chr.setPosition(getPosition());
        chr.GMLevel = GMLevel;
        chr.hair = hair;
        chr.face = face;
        chr.skinColor = skinColor;
        chr.gender = gender;
        chr.job = job;
        timedFakeID++;
        return chr;
    }

    public void setTradeRequested(boolean request) {
        tradeRequested = request;
    }

    public boolean isTradeRequested() {
        return tradeRequested;
    }

    public MapleRNG getRNG() {
        return rng;
    }

    public String getBlessingChar() {
        return blessingChar;
    }

    public boolean isUILocked() {
        return uiLocked;
    }

    public void setUILocked(boolean enabled) {
        uiLocked = enabled;
    }

    public long getLastWarpTime() {
        return lastWarpTimestamp;
    }

    public void setLastWarpTime(long time) {
        lastWarpTimestamp = time;
    }

    public long getChatSpam(int type) {
        return chatSpam[type];
    }

    public void setChatSpam(int type) {
        chatSpam[type] = GMLevel >= 2 ? 0 : System.currentTimeMillis();
    }

    public final List<Integer> getAllCompletedMedalQuests() {
        final List<Integer> medalQuests = new ArrayList<>();
        for (MapleQuestStatus quest : getCompletedQuests()) {
            if (MapleQuest.getInstance(quest.getQuestId()).getMedalId() != 0) {
                medalQuests.add(quest.getQuestId());
            }
        }
        return medalQuests;
    }

    public void setComboCounter(short count) {
        comboCounter = count;
    }

    public void setLastComboAttack(long time) {
        lastComboAttack = time;
    }

    public short getComboCounter() {
        return comboCounter;
    }

    public long getLastComboAttack() {
        return lastComboAttack;
    }

    public int getAutoChangeMapId() {
        return autoMapChange;
    }

    public void setAutoChangeMapId(int mapId) {
        autoMapChange = mapId;
    }

    public void checkTutorial() {
        final boolean isAran = job.equals(MapleJob.LEGEND);
        final String qr = getQuestEx(isAran ? 21019 : 20021).getQuestRecord();
        if (qr.length() != 0 && (job.equals(MapleJob.NOBLESSE) && level < 11 && qr.length() != 0 || job.equals(MapleJob.LEGEND) && !qr.contains("miss=o"))) {
            TimerManager.getInstance().schedule(() -> {
                getClient().sendPacket(MaplePacketCreator.enableTutor(true));
                if (!isAran && getLevel() > 9) {
                    getClient().sendPacket(MaplePacketCreator.playerMessage("Noblesse over Lv. 10 can change your Job to a Knight-in-Training. Do it in Ereve!"));
                }
            }, 5000);
        }
    }

    public List<Integer> getIgnoredItems() {
        return ignoredItems;
    }

    public boolean isIgnoredItem(int itemid) {
        if (getInventory(MapleInventoryType.EQUIPPED).findById(1812007) == null) {
            return false;
        }
        for (int item : ignoredItems) {
            if (item == itemid) {
                return true;
            }
        }
        return false;
    }

    public double getEleAmp() {
        double mod = 1.0;
        boolean isAFpMage = job.isA(MapleJob.FP_MAGE);
        boolean isAIlMage = job.isA(MapleJob.IL_MAGE);
        boolean isAFw = job.isA(MapleJob.BLAZEWIZARD3);
        if (isAFpMage || isAIlMage || isAFw) {
            ISkill amp;
            if (isAFpMage) {
                amp = SkillFactory.getSkill(2110001);
            } else if (isAIlMage) {
                amp = SkillFactory.getSkill(2210001);
            } else {
                amp = SkillFactory.getSkill(12110001);
            }
            int ampLevel = getSkillLevel(amp);
            if (ampLevel > 0) {
                MapleStatEffect ampStat = amp.getEffect(ampLevel);
                mod = ampStat.getY() / 100.0;
            }
        }
        return mod;
    }

    public void addBuffCount() {
        buffCount++;
    }

    public int getBuffCount() {
        return buffCount;
    }

    public void addAttackCount() {
        attackCount++;
    }

    public int getAttackCount() {
        return attackCount;
    }

    public void resetBuffAndAttackCounts() {
        attackCount = 0;
        buffCount = 0;
    }

    public void confirmationCallback() {
        NPCScriptManager.getInstance().dispose(client);
        if (confirmationCallback != null) {
            confirmationCallback.run();
        }
        confirmationCallback = null;
        confirmationFailCallback = null;
    }

    public void confirmationFailCallback() {
        NPCScriptManager.getInstance().dispose(client);
        if (confirmationFailCallback != null) {
            confirmationFailCallback.run();
        }
        confirmationFailCallback = null;
        confirmationCallback = null;
    }

    public void announce(MaplePacket packet) {
        getClient().sendPacket(packet);
    }

    public void callConfirmationNpc(Runnable r, Runnable fr, int id, int cfmid, String... text) {
        confirmationCallback = r;
        confirmationFailCallback = fr;
        NPCScriptManager.getInstance().start(client, id, "confirm_" + cfmid, text);
    }

    public String getPossessivePronoun() {
        return gender == 0 ? "his" : "her";
    }

    public String getPronoun() {
        return gender == 0 ? "he" : "she";
    }

    public boolean isCCRequired() {
        return ccRequired;
    }

    public void setCCRequired(boolean cc) {
        ccRequired = cc;
    }

    public int getMobRidingSkillId() {
        return job.getId() / 1000 * 10000000 + 1004;
    }

    private static class MapleBuffStatValueHolder {

        public final MapleStatEffect effect;
        public final long startTime;
        public final ScheduledFuture<?> schedule;
        public int value;

        public MapleBuffStatValueHolder(MapleStatEffect effect, long startTime, ScheduledFuture<?> schedule, int value) {
            super();
            this.effect = effect;
            this.startTime = startTime;
            this.schedule = schedule;
            this.value = value;
        }
    }

    public static class SkillEntry {

        public final int skillevel;
        public final int masterlevel;

        public SkillEntry(int skillevel, int masterlevel) {
            this.skillevel = skillevel;
            this.masterlevel = masterlevel;
        }

        @Override
        public String toString() {
            return skillevel + ":" + masterlevel;
        }
    }
}