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

package guida.server.maps;

import guida.client.Equip;
import guida.client.IItem;
import guida.client.Item;
import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleDisease;
import guida.client.MapleInventoryType;
import guida.client.MaplePet;
import guida.client.SkillFactory;
import guida.client.anticheat.CheatingOffense;
import guida.client.messages.MessageCallback;
import guida.client.status.MonsterStatus;
import guida.client.status.MonsterStatusEffect;
import guida.database.DatabaseConnection;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.net.world.MaplePartyCharacter;
import guida.scripting.maps.MapScriptManager;
import guida.server.MapleItemInformationProvider;
import guida.server.MapleOxQuiz;
import guida.server.MaplePlayerNPC;
import guida.server.MaplePortal;
import guida.server.MapleStatEffect;
import guida.server.TimerManager;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MapleNPC;
import guida.server.life.MobSkill;
import guida.server.life.MobSkillFactory;
import guida.server.life.SpawnPoint;
import guida.server.playerinteractions.IMaplePlayerShop;
import guida.tools.MaplePacketCreator;
import guida.tools.Randomizer;
import guida.tools.StringUtil;

import java.awt.Point;
import java.awt.Rectangle;
import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MapleMap {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleMap.class);
    private static final int MAX_OID = 20000;
    private static final List<MapleMapObjectType> rangedMapobjectTypes = Arrays.asList(MapleMapObjectType.NPC, MapleMapObjectType.ITEM, MapleMapObjectType.MONSTER, MapleMapObjectType.DOOR, MapleMapObjectType.SUMMON, MapleMapObjectType.REACTOR);
    /**
     * Holds a mapping of all oid -> MapleMapObject on this map. mapobjects is NOT a synchronized collection since it
     * has to be synchronized together with runningOid that's why all access to mapobjects have to be done trough an
     * explicit synchronized block
     */
    private final Map<Integer, MapleMapObject> mapobjects = new LinkedHashMap<>();
    private final Collection<SpawnPoint> monsterSpawn = new LinkedList<>();
    private final AtomicInteger spawnedMonstersOnMap = new AtomicInteger(0);
    private final Collection<MapleCharacter> characters = new LinkedHashSet<>();
    private final Map<Integer, MaplePortal> portals = new HashMap<>();
    private final Map<Integer, Point> seats = new HashMap<>();
    private final List<MaplePortal> spawnPoints = new ArrayList<>();
    private final List<Rectangle> areas = new ArrayList<>();
    private final List<MaplePlayerNPC> playerNPCs = new ArrayList<>();
    private final ArrayList<MapleMapTimer> hiddenMapTimer = new ArrayList<>();
    private final int mapId, returnMapId, channel, dropLife = 180000;
    private final float origMobRate;
    private final Lock objectLock = new ReentrantLock();
    private MapleFootholdTree footholds = null;
    private MapleMapEffect mapEffect = null;
    private ScheduledFuture<?> mapEffectSch = null;
    private MapleMapTimer mapTimer = null;
    private MapleOxQuiz ox = null;
    private ScheduledFuture<?> spawnWorker = null;
    private short decHP = 0, createMobInterval = 9000;
    private int runningOid = 100, forcedReturnMap = 999999999, timeLimit, protectItem = 0, fieldLimit = 0/*, maxRegularSpawn = 0*/;
    private int levelLimit, lvForceMove;
    private float monsterRate;
    private boolean everlast = false, allowShops, partyOnly;
    private boolean dropsDisabled = false, clock, boat, docked, town, hasEvent, muted, lootable = true;
    private String mapName, streetName, onUserEnter, onFirstUserEnter;
    private boolean canEnter = true, canExit = true, cannotInvincible = false, canVipRock = true, allowSkills = true, canMovement = true;
    private ScheduledFuture<?> dojoSpawn = null;
    private MapleBuffZone buffZone;

    public MapleMap(int mapId, int channel, int returnMapId, float monsterRate, boolean isInstance) {
        this.mapId = mapId;
        this.channel = channel;
        this.returnMapId = returnMapId;
        this.monsterRate = monsterRate;
        origMobRate = monsterRate;
        if (monsterRate > 0 && isInstance) {
            spawnWorker = TimerManager.getInstance().register(new RespawnWorker(), createMobInterval);
        }
        if (getPlayerNPCMap() != -1) {
            loadPlayerNPCs();
        }
    }

    public boolean canEnter() {
        return canEnter;
    }

    public boolean canExit() {
        return canExit;
    }

    public void setCanEnter(boolean b) {
        canEnter = b;
    }

    public void setCanExit(boolean b) {
        canExit = b;
    }

    public void toggleDrops() {
        dropsDisabled = !dropsDisabled;
    }

    public int getId() {
        return mapId;
    }

    public MapleMap getReturnMap() {
        return ChannelServer.getInstance(channel).getMapFactory().getMap(returnMapId);
    }

    public int getReturnMapId() {
        return returnMapId;
    }

    public int getForcedReturnId() {
        return forcedReturnMap;
    }

    public MapleMap getForcedReturnMap() {
        return ChannelServer.getInstance(channel).getMapFactory().getMap(forcedReturnMap);
    }

    public void setForcedReturnMap(int map) {
        forcedReturnMap = map;
    }

    public int getTimeLimit() {
        return timeLimit;
    }

    public void setTimeLimit(int timeLimit) {
        this.timeLimit = timeLimit;
    }

    public boolean getMuted() {
        return muted;
    }

    public void setMuted(boolean isMuted) {
        muted = isMuted;
    }

    public boolean isLootable() {
        return lootable;
    }

    public void setLootable(boolean loot) {
        lootable = loot;
    }

    public boolean canUseSkills() {
        return allowSkills;
    }

    public void setAllowSkills(boolean allow) {
        allowSkills = allow;
        final List<MapleCharacter> allChars = getCharacters();
        if (!allow) {
            MobSkill ms = MobSkillFactory.getMobSkill(120, 1);
            for (MapleCharacter mc : allChars) {
                if (!mc.isGM()) {
                    mc.giveDebuff(MapleDisease.GM_DISABLE_SKILL, ms, true);
                }
            }
        } else {
            for (MapleCharacter mc : allChars) {
                mc.dispelDebuff(MapleDisease.GM_DISABLE_SKILL);
            }
        }
    }

    public boolean canMove() {
        return canMovement;
    }

    public void setCanMove(boolean move) {
        canMovement = move;
        final List<MapleCharacter> allChars = getCharacters();
        if (!move) {
            MobSkill ms = MobSkillFactory.getMobSkill(123, 1);
            for (MapleCharacter mc : allChars) {
                if (!mc.hasGMLevel(2)) {
                    mc.giveDebuff(MapleDisease.GM_DISABLE_MOVEMENT, ms, true);
                }
            }
        } else {
            for (MapleCharacter mc : allChars) {
                mc.dispelDebuff(MapleDisease.GM_DISABLE_MOVEMENT);
            }
        }
    }

    public void addMapObject(MapleMapObject mapobject) {
        objectLock.lock();
        try {
            mapobject.setObjectId(runningOid);
            mapobjects.put(runningOid, mapobject);
            incrementRunningOid();
        } finally {
            objectLock.unlock();
        }
    }

    private void spawnAndAddRangedMapObject(MapleMapObject mapobject, DelayedPacketCreation packetbakery, SpawnCondition condition) {
        objectLock.lock();
        try {
            mapobject.setObjectId(runningOid);
            for (MapleCharacter chr : characters) {
                if (condition == null || condition.canSpawn(chr)) {
                    if (chr.getPosition().distanceSq(mapobject.getPosition()) <= MapleCharacter.MAX_VIEW_RANGE_SQ) {
                        if (chr.canSeeItem(mapobject)) {
                            packetbakery.sendPackets(chr.getClient());
                            chr.addVisibleMapObject(mapobject);
                        }
                    }
                }
            }
            mapobjects.put(runningOid, mapobject);
            incrementRunningOid();
        } finally {
            objectLock.unlock();
        }
    }

    public void spawnMesoDrop(final int meso, Point position, final MapleMapObject dropper, final MapleCharacter owner, final boolean ffaLoot, final byte type) {
        spawnMesoDrop(meso, position, dropper, owner, ffaLoot, type, false);
    }

    public void spawnMesoDrop(final int meso, Point position, final MapleMapObject dropper, final MapleCharacter owner, final boolean ffaLoot, final byte type, final boolean isPlayerDrop) {
        TimerManager tMan = TimerManager.getInstance();
        final Point droppos = calcDropPos(position, position);
        final MapleMapItem mdrop = new MapleMapItem(meso, droppos, dropper, owner, type);
        mdrop.setPlayerDrop(isPlayerDrop);
        spawnAndAddRangedMapObject(mdrop, c -> c.sendPacket(MaplePacketCreator.dropMesoFromMapObject(meso, mdrop.getObjectId(), type == 4 ? 0 : dropper.getObjectId(), type == 4 ? 0 : type == 1 ? owner.getPartyId() : owner.getId(), dropper.getPosition(), droppos, (byte) 1, type, isPlayerDrop)), null);
        tMan.schedule(new ExpireMapItemJob(mdrop), dropLife);
    }

    private void incrementRunningOid() {
        runningOid++;
        for (int numIncrements = 1; numIncrements < MAX_OID; numIncrements++) {
            if (runningOid > MAX_OID) {
                runningOid = 100;
            }
            if (mapobjects.containsKey(runningOid)) {
                runningOid++;
            } else {
                return;
            }
        }
        throw new RuntimeException("Out of OIDs on map " + mapId + " (channel: " + channel + ")");
    }

    public void mapMessage(int type, String message) {
        broadcastMessage(MaplePacketCreator.serverNotice(type, message));
    }

    public void removeMapObject(int num) {
        objectLock.lock();
        try {
            final MapleMapObject obj = mapobjects.remove(num);
            if (obj != null) {
                for (MapleCharacter character : characters) {
                    character.removeVisibleMapObject(obj);
                }
            }
        } finally {
            objectLock.unlock();
        }
    }

    public void removeMapObject(MapleMapObject obj) {
        objectLock.lock();
        try {
            mapobjects.remove(obj.getObjectId());
            for (MapleCharacter character : characters) {
                character.removeVisibleMapObject(obj);
            }
        } finally {
            objectLock.unlock();
        }
    }

    public MapleMonster getMonsterById(int id) {
        objectLock.lock();
        try {
            for (MapleMapObject obj : mapobjects.values()) {
                if (obj.getType() == MapleMapObjectType.MONSTER) {
                    if (((MapleMonster) obj).getId() == id) {
                        return (MapleMonster) obj;
                    }
                }
            }
        } finally {
            objectLock.unlock();
        }
        return null;
    }

    public void warpEveryone(int to) {
        List<MapleCharacter> players;
        objectLock.lock();
        try {
            players = new ArrayList<>(getCharacters());
        } finally {
            objectLock.unlock();
        }

        for (MapleCharacter chr : players) {
            chr.changeMap(to);
        }
    }

    private Point calcPointBelow(Point initial) {
        MapleFoothold fh = footholds.findBelow(initial);
        if (fh == null) {
            return null;
        }
        int dropY = fh.getY1();
        if (!fh.isWall() && fh.getY1() != fh.getY2()) {
            double s1 = Math.abs(fh.getY2() - fh.getY1());
            double s2 = Math.abs(fh.getX2() - fh.getX1());
            double s4 = Math.abs(initial.x - fh.getX1());
            double alpha = Math.atan(s2 / s1);
            double beta = Math.atan(s1 / s2);
            double s5 = Math.cos(alpha) * s4 / Math.cos(beta);
            if (fh.getY2() < fh.getY1()) {
                dropY = fh.getY1() - (int) s5;
            } else {
                dropY = fh.getY1() + (int) s5;
            }
        }
        return new Point(initial.x, dropY);
    }

    private Point calcDropPos(Point initial, Point fallback) {
        Point ret = calcPointBelow(new Point(initial.x, initial.y - 99));
        if (ret == null) {
            return fallback;
        }
        return ret;
    }

    private void dropFromMonster(MapleCharacter dropOwner, MapleMonster monster) {
        if (dropsDisabled || monster.dropsDisabled()) {
            return;
        }

        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();

        final int maxDrops = monster.getMaxDrops(dropOwner);
        final boolean explosive = monster.isExplosive();

        List<Integer> toDrop = new ArrayList<>();

        for (int i = 0; i < maxDrops; i++) {
            toDrop.add(monster.getDrop(dropOwner));
        }

        if (dropOwner.getEventInstance() == null && !monster.isDojoMinion()) {
            int chance = Randomizer.nextInt(100);
            if (chance < 30 && !monster.isHalloweenBoss()) { // 20% chance of getting a maple leaf
                toDrop.add(4001126);
            }
            chance = Randomizer.nextInt(100);
            if (monster.getId() == 8800002 && chance < 50) { // Zakum
                toDrop.add(2388023);
            } else if (chance == 7 || chance == 8 || chance == 99 || chance == 23) { // 1% Chance of getting a monster card (Actual Value = 0.5 - 2%)
                int cardid = ii.getMobCardId(monster.getId());
                if (cardid != -1) {
                    toDrop.add(cardid);
                }
            } else if (monster.isBoss() && chance >= 80) {
                int cardid = ii.getMobCardId(monster.getId());
                if (cardid != -1) {
                    toDrop.add(cardid);
                }
            }
            chance = Randomizer.nextInt(100);
            if (mapId / 100000 == 2400 && chance < 10) {
                toDrop.add(4001393);
            }
        }
        if (monster.isDojoMinion()) {
            toDrop.add(2022432); //dojo power elixir
            toDrop.add(2022433); //dojo all cure
        }
        if (monster.getId() == 8810018) {
            toDrop.add(2290096); //force add one MW per HT
        /*} else if (monster.getId() == 9400511 || monster.getId() == 9400510 || monster.getId() == 9400749 || monster.getId() == 9400749) {
			for (int i = 0; i < 10; i++) { //force drop eggs for easter
				toDrop.add(4220125);
			}*/
        } else if (monster.getId() == 9001011) {
            int proofOfExam = switch (dropOwner.getJob().getId()) {
                case 1100 -> 4032096;
                case 1200 -> 4032097;
                case 1300 -> 4032098;
                case 1400 -> 4032099;
                case 1500 -> 4032100;
                default -> 0;
            };
            if (proofOfExam != 0 && !dropOwner.haveItem(proofOfExam, 30, false, false)) {
                toDrop.add(proofOfExam);
            }
        }
        Set<Integer> alreadyDropped = new HashSet<>();
        int htpendants = 0;
        int htstones = 0;
        for (int i = 0; i < toDrop.size(); i++) {
            if (toDrop.get(i) == 1122000) {
                if (htpendants > 3) {
                    toDrop.set(i, -1);
                } else {
                    htpendants++;
                }
            } else if (toDrop.get(i) == 4001094) {
                if (htstones > 2) {
                    toDrop.set(i, -1);
                } else {
                    htstones++;
                }
            } else if (alreadyDropped.contains(toDrop.get(i)) && !explosive) {
                toDrop.remove(i);
                i--;
            } else {
                alreadyDropped.add(toDrop.get(i));
            }
        }
        if (monster.getId() == 9400608) {
            for (int i = 0; i < 5; i++) {
                toDrop.add(4001168);
            }
        }
        if (monster.getId() == 9400633) { // astaroth
            for (int i = 0; i < 10; i++) {
                toDrop.add(2022428);
            }
        } else if (monster.isHalloweenBoss()) {
            final int GREEN = 2022105;
            final int RED = 2022106;
            final int BLUE = 2022107;
            final int[][] candyDrops = {
                    {9500325, RED, -1, -1},
                    {9500327, RED, GREEN, -1},
                    {9500329, RED, GREEN, -1},
                    {9400571, RED, GREEN, -1},
                    {9500328, RED, GREEN, -1},
                    {9500330, RED, GREEN, -1},
                    {9400572, RED, GREEN, -1},
                    {9400575, RED, GREEN, -1},
                    {9400576, RED, GREEN, -1},
                    {9500173, RED, GREEN, BLUE},
                    {9500174, RED, GREEN, BLUE},
                    {9500331, BLUE, -1, -1},
                    {9500332, BLUE, -1, -1}
            };
            toDrop.clear();
            for (int[] candyDrop : candyDrops) {
                if (candyDrop[0] == monster.getId()) {
                    while (toDrop.size() < 6) {
                        for (int k = 1; k < 4; k++) {
                            if (candyDrop[k] != -1) {
                                toDrop.add(candyDrop[k]);
                            }
                        }
                    }
                }
            }
            if (toDrop.size() > maxDrops) {
                toDrop = toDrop.subList(0, maxDrops);
            }
        }

        if (toDrop.isEmpty()) {
            return; // Nothing to drop. Don't need to place items.
        }

        final Point[] toPoint = new Point[toDrop.size()];
        int shiftDirection = 0;
        int shiftCount = 0;

        int curX = Math.min(Math.max(monster.getPosition().x - 25 * (toDrop.size() / 2), footholds.getMinDropX() + 25), footholds.getMaxDropX() - toDrop.size() * 25);
        int curY = Math.max(monster.getPosition().y, footholds.getY1());
        while (shiftDirection < 3 && shiftCount < 1000) {
            if (shiftDirection == 1) {
                curX += 25;
            } else if (shiftDirection == 2) {
                curX -= 25;
            }
            for (int i = 0; i < toDrop.size(); i++) {
                MapleFoothold wall = footholds.findWall(new Point(curX, curY), new Point(curX + toDrop.size() * 25, curY));
                if (wall != null) {
                    if (wall.getX1() < curX) {
                        shiftDirection = 1;
                        shiftCount++;
                        break;
                    } else if (wall.getX1() == curX) {
                        if (shiftDirection == 0) {
                            shiftDirection = 1;
                        }
                        shiftCount++;
                        break;
                    } else {
                        shiftDirection = 2;
                        shiftCount++;
                        break;
                    }
                } else if (i == toDrop.size() - 1) {
                    shiftDirection = 3;
                }
                final Point dropPos = calcDropPos(new Point(curX + i * 25, curY), new Point(monster.getPosition()));
                toPoint[i] = new Point(curX + i * 25, curY);
                final int drop = toDrop.get(i);
                byte dropType = 0;
                if (explosive) {
                    dropType = 3;
                } else if (monster.isFfaLoot()) {
                    dropType = 2;
                } else if (dropOwner.getParty() != null) {
                    dropType = 1;
                }
                if (drop == -1) {
                    final int mesoRate = ChannelServer.getInstance(channel).getMesoRate();
                    double mesoDecrease = Math.pow(0.93, monster.getExp() / 300.0);
                    if (mesoDecrease > 1.0) {
                        mesoDecrease = 1.0;
                    }
                    if (mesoDecrease <= 0.0 && (monster.getId() == 8810018 || monster.getId() == 8800002)) {
                        mesoDecrease = Math.random();
                    }
                    int tempmeso = Math.min(30000, (int) (mesoDecrease * monster.getExp() * (1.0 + Math.random() * 20) / 10.0));
                    if (dropOwner.getBuffedValue(MapleBuffStat.MESOUP) != null) {
                        tempmeso = (int) (tempmeso * dropOwner.getBuffedValue(MapleBuffStat.MESOUP).doubleValue() / 100.0);
                    }
                    if (tempmeso < 1 && (monster.getId() == 8810018 || monster.getId() == 8800002)) {
                        tempmeso = Randomizer.nextInt(30000);
                    }
                    final int meso = tempmeso;

                    if (meso > 0 && !monster.isDojoMinion()) {
                        final MapleMonster dropMonster = monster;
                        final MapleCharacter dropChar = dropOwner;
                        final byte fDropType = dropType;
                        TimerManager.getInstance().schedule(() -> spawnMesoDrop(meso * mesoRate, dropPos, dropMonster, dropChar, explosive, fDropType), monster.getAnimationTime("die1") + i);
                    }
                } else {
                    IItem idrop;
                    MapleInventoryType type = ii.getInventoryType(drop);
                    if (type.equals(MapleInventoryType.EQUIP)) {
                        idrop = ii.randomizeStats((Equip) ii.getEquipById(drop));
                    } else {
                        idrop = new Item(drop, (byte) 0, (short) 1);
                        if (ii.isArrowForBow(drop) || ii.isArrowForCrossBow(drop)) { // Randomize quantity for certain items
                            idrop.setQuantity((short) (1 + 100 * Math.random()));
                        } else if (idrop.getItemId() == 4001106 && monster.getId() == 9400218) {
                            idrop.setQuantity((short) 50);
                        } else if (ii.isThrowingStar(drop) || ii.isShootingBullet(drop)) {
                            idrop.setQuantity((short) 1);
                        }
                    }

                    idrop.log("Created as a drop from monster " + monster.getObjectId() + " (" + monster.getId() + ") at " + dropPos.toString() + " on map " + mapId, false);

                    final MapleMapItem mdrop = new MapleMapItem(idrop, dropPos, monster, dropOwner, dropType);
                    final MapleMapObject dropMonster = monster;
                    final MapleCharacter dropChar = dropOwner;
                    final TimerManager tMan = TimerManager.getInstance();

                    tMan.schedule(() -> {
                        spawnAndAddRangedMapObject(mdrop, c -> {
                            c.sendPacket(MaplePacketCreator.dropItemFromMapObject(drop, mdrop.getObjectId(), dropMonster.getObjectId(), mdrop.getDropType() == 1 ? dropChar.getPartyId() : dropChar.getId(), dropMonster.getPosition(), dropPos, (byte) 1, mdrop.getItem().getExpiration(), mdrop.getDropType(), false));
                            activateItemReactors(mdrop);
                        }, null);
                        tMan.schedule(new ExpireMapItemJob(mdrop), dropLife);
                    }, monster.getAnimationTime("die1") + i);
                }
            }
        }
    }

    public boolean damageMonster(MapleCharacter chr, MapleMonster monster, int damage) {
        if (!isDojoMap()) { // it'd be easy with bamboo rain
            if (damage > 10000) {
                chr.finishAchievement(20);
            }
            if (damage >= 99999) {
                chr.finishAchievement(21);
            }
            if (damage >= 199999) {
                chr.finishAchievement(43);
            }
        }

        if (monster.getId() == 8800000) {
            for (MapleMapObject object : getAllMonsters()) {
                MapleMonster mons = getMonsterByOid(object.getObjectId());
                if (mons != null && mons.getId() >= 8800003 && mons.getId() <= 8800010) {
                    return true;
                }
            }
        }

        if (monster.isAlive()) {
            if (damage > 0) {
                monster.damage(chr, damage, true);
                if (monster.getSponge() != null) {
                    damageMonster(chr, monster.getSponge(), damage);
                }
                if (!monster.isAlive()) {
                    killMonster(monster, chr, true, false, 1);
                }
            }
            return true;
        }
        return false;
    }

    public void killMonster(final MapleMonster monster, final MapleCharacter chr, final boolean withDrops) {
        killMonster(monster, chr, withDrops, false, 1);
    }

    public void killMonster(final MapleMonster monster, final MapleCharacter chr, final boolean withDrops, final boolean secondTime) {
        killMonster(monster, chr, withDrops, secondTime, 1);
    }

    public void killMonster(int monsId) {
        for (MapleMapObject mmo : getAllMonsters()) {
            if (((MapleMonster) mmo).getId() == monsId) {
                killMonster((MapleMonster) mmo, characters.iterator().next(), false);
            }
        }
    }

    public void killMonster(int monsId, MapleCharacter trigger) {
        for (MapleMapObject mmo : getAllMonsters()) {
            if (((MapleMonster) mmo).getId() == monsId) {
                killMonster((MapleMonster) mmo, trigger, false);
            }
        }
    }

    public void killMonster(final MapleMonster monster, final MapleCharacter chr, final boolean withDrops, final boolean secondTime, int animation) {
        if (chr == null || monster == null) {
            return;
        }
        if (chr.getCheatTracker().checkHPLoss()) {
            chr.getCheatTracker().registerOffense(CheatingOffense.ATTACK_WITHOUT_GETTING_HIT);
        }
        if (monster.getId() == 8810018 && !secondTime) {
            TimerManager.getInstance().schedule(() -> {
                killMonster(monster, chr, withDrops, true, 1);
                killAllMonsters(false);
            }, 3000);
            return;
        }
        final List<MapleCharacter> chars = getCharacters();
        if (monster.getBuffToGive() > -1) {
            broadcastMessage(MaplePacketCreator.showOwnBuffEffect(monster.getBuffToGive(), 11, (byte) chr.getLevel()));
            MapleItemInformationProvider mii = MapleItemInformationProvider.getInstance();
            MapleStatEffect statEffect = mii.getItemEffect(monster.getBuffToGive());
            for (MapleCharacter character : chars) {
                if (character.isAlive()) {
                    statEffect.applyTo(character);
                    broadcastMessage(MaplePacketCreator.showBuffeffect(character.getId(), monster.getBuffToGive(), 11, (byte) character.getLevel(), (byte) 3));
                }
            }
        }
        if (isDojoMap() && monster.getId() > 9300183 && monster.getId() < 9300216) { // that'll do =|
            disableDojoSpawn();
            monster.disableDrops();
            int newpoints = (getDojoStage() - getDojoStage() / 5) / 5 + 2;
            for (MapleCharacter character : chars) {
                if (character.isAlive()) {
                    character.getDojo().setPoints(character.getDojo().getPoints() + newpoints);
                    character.getClient().sendPacket(MaplePacketCreator.playSound("Dojang/clear"));
                    character.getClient().sendPacket(MaplePacketCreator.showEffect("dojang/end/clear"));
                    character.getClient().sendPacket(MaplePacketCreator.playerMessage("You received " + newpoints + " training points. Your total training score is now " + character.getDojo().getPoints() + "."));
                    character.getClient().sendPacket(MaplePacketCreator.dojoWarpUp());
                }
            }
        }
        ChannelServer cserv = ChannelServer.getInstance(channel);
        switch (monster.getId()) {
            case 8810018:
                for (MapleCharacter c : chars) {
                    c.finishAchievement(26);
                }
                try {
                    cserv.getWorldInterface().broadcastMessage(null, MaplePacketCreator.serverNotice(6, "To the crew that have finally conquered Horned Tail after numerous attempts, I salute thee! You are the true heroes of Leafre!!").getBytes());
                } catch (RemoteException re) {
                    cserv.reconnectWorld();
                }
                break;
            // This part is responsible for Area Boss respawns.
            case 2220000:
                cserv.getEventSM().getEventManager("AreaBossMano").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 3220000:
                cserv.getEventSM().getEventManager("AreaBossStumpy").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 3220001:
                cserv.getEventSM().getEventManager("AreaBossDeo").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 4220001:
                cserv.getEventSM().getEventManager("AreaBossSeruf").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 5220001:
                cserv.getEventSM().getEventManager("AreaBossKingClang").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 5220002:
                if (mapId == 100040105) {
                    cserv.getEventSM().getEventManager("AreaBossFaust1").schedule("start", ((long) 60 * 1000 * 45));
                } else if (mapId == 100040106) {
                    cserv.getEventSM().getEventManager("AreaBossFaust2").schedule("start", ((long) 60 * 1000 * 45));
                }
                break;
            case 5220003:
                if (mapId == 220050000) {
                    cserv.getEventSM().getEventManager("AreaBossTimer2").schedule("start", ((long) 60 * 1000 * 45));
                } else if (mapId == 220050100) {
                    cserv.getEventSM().getEventManager("AreaBossTimer1").schedule("start", ((long) 60 * 1000 * 45));
                } else if (mapId == 220050200) {
                    cserv.getEventSM().getEventManager("AreaBossTimer3").schedule("start", ((long) 60 * 1000 * 45));
                }
                break;
            case 6220000:
                cserv.getEventSM().getEventManager("AreaBossDyle").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 6220001:
                cserv.getEventSM().getEventManager("AreaBossZeno").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 7220000:
                cserv.getEventSM().getEventManager("AreaBossTaeRoon").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 7220001:
                cserv.getEventSM().getEventManager("AreaBossNineTailedFox").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 7220002:
                cserv.getEventSM().getEventManager("AreaBossKingSageCat").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 8220000:
                if (mapId == 200010300) {
                    cserv.getEventSM().getEventManager("AreaBossEliza1").schedule("start", ((long) 60 * 1000 * 45));
                }
                break;
            case 8220002:
                cserv.getEventSM().getEventManager("AreaBossKimera").schedule("start", ((long) 60 * 1000 * 45));
                break;
            case 8220003:
                cserv.getEventSM().getEventManager("AreaBossLeviathan").schedule("start", ((long) 60 * 1000 * 120));
                break;
            case 9500365:
                cserv.getEventSM().getEventManager("AgentBox").schedule("start", ((long) 60 * 1000 * 15));
                break;
        }

        spawnedMonstersOnMap.decrementAndGet();
        monster.setHp(0);

        MapleCharacter dropOwner = monster.killBy(chr, channel);

        broadcastMessage(MaplePacketCreator.killMonster(monster.getObjectId(), animation));
        removeMapObject(monster);

        if (monster.getId() >= 8800003 && monster.getId() <= 8800010) {
            boolean makeZakReal = true;
            List<MapleMapObject> objects = getAllMonsters();
            for (MapleMapObject object : objects) {
                MapleMonster mons = getMonsterByOid(object.getObjectId());
                if (mons != null && mons.getId() >= 8800003 && mons.getId() <= 8800010) {
                    makeZakReal = false;
                }
            }
            if (makeZakReal) {
                for (MapleMapObject object : objects) {
                    MapleMonster mons = getMonsterByOid(object.getObjectId());
                    if (mons != null && mons.getId() == 8800000) {
                        final Point pos = mons.getPosition();
                        killAllMonsters(true);
                        spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(8800000), pos);
                    }
                }
            }
        }

        if (withDrops && !monster.dropsDisabled()) {
            if (dropOwner == null) {
                dropOwner = chr;
            }
            dropFromMonster(dropOwner, monster);
        }
        monster.dispose();
    }

    public void killMonster(final MapleMonster monster) {
        killMonster(monster, 3, false);
    }

    public void killMonster(final MapleMonster monster, int anim, boolean revive) {
        spawnedMonstersOnMap.decrementAndGet();
        monster.setHp(0);
        if (revive) {
            monster.spawnRevives(this);
        }
        broadcastMessage(MaplePacketCreator.killMonster(monster.getObjectId(), anim));
        removeMapObject(monster);
        monster.dispose();
    }

    public void killAllMonsters(boolean drop) {
        killAllMonsters(drop, false);
    }

    public void killAllMonsters(boolean drop, boolean revive) {
        for (MapleMapObject monstermo : getAllMonsters()) {
            MapleMonster monster = (MapleMonster) monstermo;
            spawnedMonstersOnMap.decrementAndGet();
            monster.setHp(0);
            if (revive) {
                monster.spawnRevives(this);
            }
            broadcastMessage(MaplePacketCreator.killMonster(monster.getObjectId(), true), monster.getPosition());
            removeMapObject(monster);
            if (drop) {
                dropFromMonster(getCharacters().get(Randomizer.nextInt(characters.size())), monster);
            }
            monster.dispose();
        }
    }

    public void destroyReactor(int oid) {
        final MapleReactor reactor = getReactorByOid(oid);
        broadcastMessage(MaplePacketCreator.destroyReactor(reactor));
        reactor.setAlive(false);
        removeMapObject(reactor);
        reactor.setTimerActive(false);
        if (reactor.getDelay() > 0) {
            TimerManager.getInstance().schedule(() -> respawnReactor(reactor), reactor.getDelay());
        }
    }

    public void destroyReactors() {
        List<MapleMapObject> reactors = getAllReactors();
        for (MapleMapObject reactor : reactors) {
            destroyReactor(reactor.getObjectId());
        }
    }

    /*
     * command to reset all item-reactors in a map to state 0 for GM/NPC use - not tested (broken reactors get removed
     * from mapobjects when destroyed) Should create instances for multiple copies of non-respawning reactors...
     */
    public void resetReactors() {
        for (MapleMapObject o : getAllReactors()) {
            MapleReactor reactor = (MapleReactor) o;
            reactor.setState((byte) 0);
            reactor.setTimerActive(false);
            broadcastMessage(MaplePacketCreator.triggerReactor(reactor, 0));
        }
    }

    public void resetPortals() {
        for (MaplePortal portal : portals.values()) {
            portal.setSpawned(false);
            portal.setPortalStatus(MaplePortal.OPEN);
        }
    }

    /*
     * command to shuffle the positions of all reactors in a map for PQ purposes (such as ZPQ/LMPQ)
     */
    public void shuffleReactors() {
        List<Point> points = new ArrayList<>();
        final List<MapleMapObject> reactors = getAllReactors();
        for (MapleMapObject o : reactors) {
            points.add(o.getPosition());
        }
        Collections.shuffle(points);
        for (MapleMapObject o : reactors) {
            o.setPosition(points.remove(points.size() - 1));
        }
    }

    /**
     * Automatically finds a new controller for the given monster from the chars on the map...
     *
     * @param monster
     */
    public void updateMonsterController(MapleMonster monster) {
        if (monster.getController() != null) {
            // monster has a controller already, check if he's still on this map
            if (monster.getController().getMap() != this) {
                log.warn("Monstercontroller wasn't on same map");
                monster.getController().stopControllingMonster(monster);
            } else {
                // controller is on the map, monster has an controller, everything is fine
                return;
            }
        }
        int mincontrolled = -1;
        MapleCharacter newController = null;
        objectLock.lock();
        try {
            for (MapleCharacter chr : characters) {
                if (!chr.isHidden() && (chr.getControlledMonsters().size() < mincontrolled || mincontrolled == -1)) {
                    //if (!chr.getName().equals("FaekChar")) { // TODO remove me for production release
                    mincontrolled = chr.getControlledMonsters().size();
                    newController = chr;
                    //}
                }
            }
        } finally {
            objectLock.unlock();
        }
        if (newController != null) { // was a new controller found? (if not no one is on the map)
            if (monster.isFirstAttack()) {
                newController.controlMonster(monster, true);
                monster.setControllerHasAggro(true);
                monster.setControllerKnowsAboutAggro(true);
            } else {
                newController.controlMonster(monster, false);
            }
        }
    }

    public boolean containsNPC(int npcid) {
        for (MapleMapObject obj : getAllNPCs()) {
            if (((MapleNPC) obj).getId() == npcid) {
                return true;
            }
        }
        return false;
    }

    public int getNPCbyID(int npcid) {
        for (MapleMapObject obj : getAllNPCs()) {
            if (((MapleNPC) obj).getId() == npcid) {
                return obj.getObjectId();
            }
        }
        return 0;
    }

    public MapleMapObject getMapObject(int oid) {
        return mapobjects.get(oid);
    }

    /**
     * returns a monster with the given oid, if no such monster exists returns null
     *
     * @param oid
     * @return
     */
    public MapleMonster getMonsterByOid(int oid) {
        MapleMapObject mmo = getMapObject(oid);
        if (mmo instanceof MapleMonster) {
            return (MapleMonster) mmo;
        }
        return null;
    }

    public MapleReactor getReactorByOid(int oid) {
        MapleMapObject mmo = getMapObject(oid);
        if (mmo instanceof MapleReactor) {
            return (MapleReactor) mmo;
        }
        return null;
    }

    public MapleReactor getReactorByName(String name) {
        for (MapleMapObject obj : getAllReactors()) {
            MapleReactor reactor = (MapleReactor) obj;
            if (reactor.getName().equals(name)) {
                return reactor;
            }
        }
        return null;
    }

    public void spawnMonsterOnGroundBelow(MapleMonster monster, Point pos) {
        Point spos = new Point(pos.x, pos.y - 1);
        spos = calcPointBelow(spos);
        spos.y -= 1;
        monster.setPosition(spos);
        spawnMonster(monster);
    }

    public void spawnMonsterOnGroundBelowForce(final MapleMonster monster, Point pos) {
        Point spos = new Point(pos.x, pos.y - 1);
        spos = calcPointBelow(spos);
        spos.y -= 1;
        monster.setPosition(spos);
        monster.setMap(this);
        doRemoveAfter(monster);
        spawnAndAddRangedMapObject(monster, c -> c.sendPacket(MaplePacketCreator.spawnMonster(monster, true, 0, 0)), null);
        updateMonsterController(monster);
        spawnedMonstersOnMap.incrementAndGet();
    }

    private void doRemoveAfter(final MapleMonster monster) {
        int removeAfter = monster.getRemoveAfter();
        if (removeAfter > 0) {
            TimerManager.getInstance().schedule(() -> {
                if (monster == null || !monster.isAlive()) {
                    return;
                }
                killMonster(monster, 1, true);
                if (monster.isBoss() && !characters.isEmpty()) {
                    MaplePacket noticeAutoKill = MaplePacketCreator.topMessage(monster.getName() + " has been automatically killed");
                    broadcastMessage(noticeAutoKill);
                }
            }, removeAfter * 1000);
        }
    }

    public void spawnFakeMonsterOnGroundBelow(MapleMonster mob, Point pos) {
        Point spos = new Point(pos.x, pos.y - 1);
        spos = calcPointBelow(spos);
        spos.y -= 1;
        mob.setPosition(spos);
        spawnFakeMonster(mob);
    }

    public void spawnRevives(final MapleMonster monster, final int link) {
        monster.setMap(this);

        if (monster.getId() / 100 == 88100) { // ht
            MapleMonster mob;
            for (MapleMapObject obj : getAllMonsters()) {
                mob = getMonsterByOid(obj.getObjectId());
                if (mob != null && mob.getId() == 8810018) {
                    monster.setSponge(mob);
                    break;
                }
            }
        }
        spawnAndAddRangedMapObject(monster, c -> {
            c.sendPacket(MaplePacketCreator.spawnMonster(monster, true, 0, link)); // TODO effect
        }, null);
        updateMonsterController(monster);
        spawnedMonstersOnMap.incrementAndGet();
    }

    public void spawnMonster(final MapleMonster monster) {
        if (characters.isEmpty() && !isPQMap()) { // Without this monsters on PQ maps never spawn
            return;
        }
        monster.setMap(this);
        doRemoveAfter(monster);
        spawnAndAddRangedMapObject(monster, c -> c.sendPacket(MaplePacketCreator.spawnMonster(monster, true, 0, 0)), null);
        updateMonsterController(monster);
        spawnedMonstersOnMap.incrementAndGet();
    }

    public void spawnMonsterWithEffect(final MapleMonster monster, final int effect, Point pos) {
        monster.setMap(this);
        doRemoveAfter(monster);
        Point spos = new Point(pos.x, pos.y - 1);
        spos = calcPointBelow(spos);
        spos.y -= 1;
        monster.setPosition(spos);
        monster.disableDrops();
        spawnAndAddRangedMapObject(monster, c -> c.sendPacket(MaplePacketCreator.spawnMonster(monster, true, effect, 0)), null);
        updateMonsterController(monster);
        spawnedMonstersOnMap.incrementAndGet();
    }

    public void spawnFakeMonster(final MapleMonster monster) {
        monster.setMap(this);
        monster.setFake(true);
        spawnAndAddRangedMapObject(monster, c -> c.sendPacket(MaplePacketCreator.spawnMonster(monster, true, -4, 0)), null);
        spawnedMonstersOnMap.incrementAndGet();
    }

    public void spawnReactor(final MapleReactor reactor) {
        reactor.setMap(this);
        spawnAndAddRangedMapObject(reactor, c -> c.sendPacket(reactor.makeSpawnData()), null);
    }

    private void respawnReactor(final MapleReactor reactor) {
        reactor.setState((byte) 0);
        reactor.setAlive(true);
        spawnReactor(reactor);
    }

    public void spawnDoor(final MapleDoor door) {
        spawnAndAddRangedMapObject(door, c -> {
            c.sendPacket(MaplePacketCreator.spawnDoor(door.getOwner().getId(), door.getTargetPosition(), false));
            if (door.getOwner().getParty() != null && (door.getOwner() == c.getPlayer() || c.getPlayer() != null && door.getOwner().getParty().containsMember(new MaplePartyCharacter(c.getPlayer())))) {
                c.sendPacket(MaplePacketCreator.partyPortal(door.getTown().getId(), door.getTarget().getId(), door.getTargetPosition()));
            }
            c.sendPacket(MaplePacketCreator.spawnPortal(door.getTown().getId(), door.getTarget().getId(), door.getTargetPosition()));
            c.sendPacket(MaplePacketCreator.enableActions());
        }, chr -> chr.getMapId() == door.getTarget().getId() || chr == door.getOwner() && chr.getParty() == null
        );
    }

    public void spawnSummon(final MapleSummon summon) {
        spawnAndAddRangedMapObject(summon, c -> {
            int skillLevel = summon.getOwner().getSkillLevel(SkillFactory.getSkill(summon.getSkill()));
            c.sendPacket(MaplePacketCreator.spawnSpecialMapObject(summon, skillLevel, true));
        }, null);
    }

    public void spawnMist(final MapleMist mist, final int duration, boolean fake) {
        if (hasEvent) {
            return;
        }
        addMapObject(mist);
        if (mist.getOwner() != null) {
            mist.getOwner().addOwnedObject(this, mist);
        }
        broadcastMessage(fake ? mist.makeFakeSpawnData(30) : mist.makeSpawnData());
        TimerManager tMan = TimerManager.getInstance();
        final ScheduledFuture<?> poisonSchedule;
        if (!fake && mist.isPoisonMist()) {
            Runnable poisonTask = () -> {
                List<MapleMapObject> affectedMonsters = getMapObjectsInRect(mist.getBox(), Collections.singletonList(MapleMapObjectType.MONSTER));
                for (MapleMapObject mo : affectedMonsters) {
                    if (mist.makeChanceResult() && mist.getOwner() != null) {
                        MonsterStatusEffect poisonEffect = new MonsterStatusEffect(Collections.singletonMap(MonsterStatus.POISON, 1), mist.getSourceSkill(), false);
                        ((MapleMonster) mo).applyStatus(mist.getOwner(), poisonEffect, true, duration);
                    }
                }
            };
            poisonSchedule = tMan.register(poisonTask, 2000, 2500);
        } else {
            poisonSchedule = null;
        }
        tMan.schedule(() -> {
            removeMapObject(mist);
            if (poisonSchedule != null) {
                poisonSchedule.cancel(false);
            }
            broadcastMessage(mist.makeDestroyData());
        }, duration);
    }

    public void disappearingItemDrop(final MapleMapObject dropper, final MapleCharacter owner, final IItem item, Point pos) {
        final Point droppos = calcDropPos(pos, pos);
        final MapleMapItem drop = new MapleMapItem(item, droppos, dropper, owner, (byte) 0);
        broadcastMessage(MaplePacketCreator.dropItemFromMapObject(item.getItemId(), drop.getObjectId(), 0, 0, dropper.getPosition(), droppos, (byte) 3, item.getExpiration(), (byte) 0, true), drop.getPosition());
    }

    public void spawnItemDrop(final MapleMapObject dropper, final MapleCharacter owner, final IItem item, Point pos, final boolean ffaDrop, final boolean expire) {
        spawnItemDrop(dropper, owner, item, pos, ffaDrop, expire, false);
    }

    public void spawnItemDrop(final MapleMapObject dropper, final MapleCharacter owner, final IItem item, Point pos, final boolean ffaDrop, final boolean expire, final boolean isPlayerDrop) {
        TimerManager tMan = TimerManager.getInstance();
        final Point droppos = calcDropPos(pos, pos);
        byte dropType = 0;
        if (ffaDrop) {
            dropType = 2;
        } else if (owner.getParty() != null) {
            dropType = 1;
        }
        final MapleMapItem drop = new MapleMapItem(item, droppos, dropper, owner, dropType);
        drop.setPlayerDrop(isPlayerDrop);
        spawnAndAddRangedMapObject(drop, c -> c.sendPacket(MaplePacketCreator.dropItemFromMapObject(item.getItemId(), drop.getObjectId(), 0, drop.getDropType() == 1 ? owner.getPartyId() : isPlayerDrop ? 0 : owner.getId(), dropper.getPosition(), droppos, (byte) 1, item.getExpiration(), drop.getDropType(), isPlayerDrop)), null);
        broadcastMessage(MaplePacketCreator.dropItemFromMapObject(item.getItemId(), drop.getObjectId(), 0, drop.getDropType() == 1 ? owner.getPartyId() : isPlayerDrop ? 0 : owner.getId(), dropper.getPosition(), droppos, (byte) 0, item.getExpiration(), dropType, isPlayerDrop), drop.getPosition(), drop);

        if (expire) {
            tMan.schedule(new ExpireMapItemJob(drop), dropLife);
        }

        activateItemReactors(drop);
    }

	/*private class TimerDestroyWorker implements Runnable {

		@Override
		public void run() {
			if (mapTimer != null) {
				int warpMap = mapTimer.warpToMap();
				int minWarp = mapTimer.minLevelToWarp();
				int maxWarp = mapTimer.maxLevelToWarp();
				mapTimer = null;
				if (warpMap != -1) {
					MapleMap map2wa2 = ChannelServer.getInstance(channel).getMapFactory().getMap(warpMap);
					String warpmsg = "You will now be warped to " + map2wa2.getStreetName() + " : " + map2wa2.getMapName();
					broadcastMessage(MaplePacketCreator.serverNotice(6, warpmsg));
					Collection<MapleCharacter> cmc = new LinkedHashSet<MapleCharacter>(getCharacters());
					for (MapleCharacter chr : cmc) {
						try {
							if (chr.getLevel() >= minWarp && chr.getLevel() <= maxWarp) {
								chr.changeMap(map2wa2, map2wa2.getRandomSpawnPoint());
							} else {
								chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "You are not at least level " + minWarp + " or you are higher than level " + maxWarp + "."));
							}
						} catch (Exception ex) {
							String errormsg = "There was a problem warping you. Please contact a GM";
							chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, errormsg));
						}
					}
				}
			}
		}
	 }*/

    public void addMapTimer(int durationmin, int durationmax) {
        addMapTimer(durationmin, durationmax, new String[0], false, true, null);
    }

    public void addMapTimer(int durationmin, int durationmax, String[] commands, boolean repeat, boolean shown, MapleCharacter faek) {
        if (shown && mapTimer != null) {
            return;
        }
        if (shown) {
            mapTimer = new MapleMapTimer(durationmin, durationmax, commands, repeat, true, faek, this, channel);
        } else {
            hiddenMapTimer.add(new MapleMapTimer(durationmin, durationmax, commands, repeat, false, faek, this, channel));
        }
    }

    public void clearShownMapTimer() {
        if (mapTimer != null) {
            mapTimer.getSF0F().cancel(false);
        }
        mapTimer = null;
        broadcastMessage(MaplePacketCreator.removeMapTimer());
    }

    public void clearHiddenMapTimer(int id) {
        hiddenMapTimer.get(id).getSF0F().cancel(false);
        hiddenMapTimer.remove(id);
    }

    public void clearHiddenMapTimer(MapleMapTimer mmt) {
        mmt.getSF0F().cancel(false);
        hiddenMapTimer.remove(mmt);
    }

    public void clearHiddenMapTimers() {
        for (MapleMapTimer mmt : hiddenMapTimer) {
            mmt.getSF0F().cancel(false);
        }
        hiddenMapTimer.clear();

    }

    public MapleMapTimer getShownMapTimer() {
        return mapTimer;
    }

    public String[] mapTimerDebug() {
        List<String> ls = new ArrayList<>();
        final SimpleDateFormat f = new SimpleDateFormat("HH:mm:ss, d MMMMM yyyy");
        if (mapTimer != null) {
            ls.add("SHOWN repeat:" + mapTimer.getRepeat() + " startTime:" + mapTimer.getStartTime().toString() + " timeLeft:" + mapTimer.getTimeLeft() + " commands:" + StringUtil.joinStringFrom(mapTimer.getCommands(), 0, ";"));
        }
        int id = 0;
        for (MapleMapTimer mt : hiddenMapTimer) {
            ls.add("HIDDEN" + id + " repeat:" + mt.getRepeat() + " startTime:" + f.format(mt.getStartTime().getTime()) + " timeLeft:" + mt.getTimeLeft() + " commands:" + StringUtil.joinStringFrom(mt.getCommands(), 0, ";"));
            id++;
        }
        return ls.toArray(new String[0]);
    }

    private void activateItemReactors(MapleMapItem drop) {
        IItem item = drop.getItem();
        final TimerManager tMan = TimerManager.getInstance(); // check for reactors on map that might use this item
        for (MapleMapObject o : getAllReactors()) {
            MapleReactor reactor = (MapleReactor) o;
            if (reactor.getReactorType() == 100) {
                if (reactor.getReactItem().getLeft() == item.getItemId() && reactor.getReactItem().getRight() <= item.getQuantity()) {
                    Rectangle area = reactor.getArea();
                    if (area.contains(drop.getPosition())) {
                        MapleClient ownerClient = null;
                        if (drop.getOwner() != null) {
                            ownerClient = drop.getOwner().getClient();
                        }
                        if (!reactor.isTimerActive() || getMapObject(reactor.getReactingWith().getObjectId()) == null) {
                            tMan.schedule(new ActivateItemReactor(drop, reactor, ownerClient), 5000);
                            reactor.setTimerActive(true);
                            reactor.setReactingWith(drop);
                        }
                    }
                }
            }
        }
    }

    public void ariantPQStart() {
        int i = 1;
        for (MapleCharacter chars2 : getCharacters()) {
            broadcastMessage(MaplePacketCreator.updateAriantPQRanking(chars2.getName(), 0, false));
            broadcastMessage(MaplePacketCreator.serverNotice(0, MaplePacketCreator.updateAriantPQRanking(chars2.getName(), 0, false).toString()));
            if (getCharacters().size() > i) {
                broadcastMessage(MaplePacketCreator.updateAriantPQRanking(null, 0, true));
                broadcastMessage(MaplePacketCreator.serverNotice(0, MaplePacketCreator.updateAriantPQRanking(chars2.getName(), 0, true).toString()));
            }
            i++;
        }
    }

    public void startMapEffect(String msg, int itemId) {
        if (mapEffect != null) {
            return;
        }
        mapEffect = new MapleMapEffect(msg, itemId);
        broadcastMessage(mapEffect.makeStartData());
        mapEffectSch = TimerManager.getInstance().schedule(() -> {
            broadcastMessage(mapEffect.makeDestroyData());
            mapEffect = null;
            mapEffectSch = null;
        }, 30000);
    }

    public void stopMapEffect() {
        if (mapEffect == null) {
            return;
        }
        if (mapEffectSch != null) {
            mapEffectSch.cancel(false);
            mapEffectSch = null;
        }
        broadcastMessage(mapEffect.makeDestroyData());

        mapEffect = null;
    }

    /**
     * Adds a player to this map and sends necessary data
     *
     * @param chr
     */
    public void addPlayer(MapleCharacter chr) {
        objectLock.lock();
        try {
            characters.add(chr);
            mapobjects.put(chr.getObjectId(), chr);
        } finally {
            objectLock.unlock();
        }
        for (MaplePlayerNPC pnpc : playerNPCs) {
            chr.getClient().sendPacket(MaplePacketCreator.getPlayerNPC(pnpc));
        }
        if (!chr.isHidden()) {
            broadcastMessage(chr, MaplePacketCreator.spawnPlayerMapobject(chr, false), true);
            broadcastMessage(chr, MaplePacketCreator.playerGuildName(chr), false);
            broadcastMessage(chr, MaplePacketCreator.playerGuildInfo(chr), false);
            for (MapleCharacter c : getCharacters()) {
                if (c.hasGMLevel(5) && !c.isHidden()) {
                    chr.finishAchievement(11);
                    break;
                }
            }
        } else {
            chr.getClient().sendPacket(MaplePacketCreator.giveGMHide(true));
        }
        sendObjectPlacement(chr);
        if (mapId >= 914000200 && mapId <= 914000220) {
            chr.getClient().sendPacket(MaplePacketCreator.tempStatsUpdate());
        } else {
            chr.getClient().sendPacket(MaplePacketCreator.resetStats());
        }
        if (chr.isUILocked() && onUserEnter.length() == 0) {
            chr.getClient().sendPacket(MaplePacketCreator.hideUI(false));
            chr.getClient().sendPacket(MaplePacketCreator.lockWindows(false));
        }
        if (mapId == 1 || mapId == 2 || mapId == 809000101 || mapId == 809000201) {
            chr.getClient().sendPacket(MaplePacketCreator.showEquipEffect());
        }
        if (decHP > 0) {
            chr.startDecHPSchedule();
        }
        if (onUserEnter.length() != 0) {
            MapScriptManager.getInstance().getMapScript(chr.getClient(), onUserEnter, false);
        }
        if (onFirstUserEnter.length() != 0) {
            if (getCharacters().size() == 1) {
                MapScriptManager.getInstance().getMapScript(chr.getClient(), onFirstUserEnter, true);
            }
        }
        final List<MaplePet> pets = chr.getPets();
        for (MaplePet pet : pets) {
            chr.getClient().sendPacket(MaplePacketCreator.showPet(chr, pet, false, false, false));
        }
        chr.updatePetPositions(0, null);

        MapleStatEffect summonStat = chr.getStatForBuff(MapleBuffStat.SUMMON);
        if (summonStat != null) {
            MapleSummon summon = chr.getSummons().get(summonStat.getSourceId());
            summon.setPosition(chr.getPosition());
            summon.sendSpawnData(chr.getClient());
            chr.addVisibleMapObject(summon);
            addMapObject(summon);
        }
        if (mapEffect != null) {
            mapEffect.sendStartData(chr.getClient());
        }
        if (timeLimit > 0 && getForcedReturnMap() != null) {
            chr.getClient().sendPacket(MaplePacketCreator.getClock(timeLimit));
            chr.startMapTimeLimitTask(this, getForcedReturnMap());
        }
        if (chr.getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
            if (FieldLimit.CANNOTUSEMOUNTS.check(fieldLimit)) {
                chr.cancelBuffStats(MapleBuffStat.MONSTER_RIDING);
            }
        }
        if (mapTimer != null) {
            mapTimer.sendSpawnData(chr.getClient());
        }
        if (chr.getEventInstance() != null && chr.getEventInstance().isTimerStarted()) {
            chr.getClient().sendPacket(MaplePacketCreator.getClock((int) (chr.getEventInstance().getTimeLeft() / 1000)));
        }
        if (hasClock()) {
            Calendar cal = Calendar.getInstance();
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int min = cal.get(Calendar.MINUTE);
            int second = cal.get(Calendar.SECOND);
            chr.getClient().sendPacket(MaplePacketCreator.getClockTime(hour, min, second));
        }

        if (hasBoat() == 2) {
            chr.getClient().sendPacket(MaplePacketCreator.boatPacket(1548));
        } else if (hasBoat() == 1 && (chr.getMapId() != 200090000 || chr.getMapId() != 200090010)) {
            chr.getClient().sendPacket(MaplePacketCreator.boatPacket(520));
        }

        if (chr.hasGMLevel(5) && !chr.isHidden()) {
            for (MapleCharacter c : getCharacters()) {
                c.finishAchievement(11);
            }
        }
        chr.receivePartyMemberHP();
        if (!canMovement && !chr.isGM()) {
            chr.giveDebuff(MapleDisease.GM_DISABLE_MOVEMENT, MobSkillFactory.getMobSkill(123, 1), true);
        }
        if (!allowSkills && !chr.isGM()) {
            chr.giveDebuff(MapleDisease.GM_DISABLE_SKILL, MobSkillFactory.getMobSkill(120, 1), true);
        }
        if (mapId == 677000005 && countMobOnMap(9400609) == 0) {
            broadcastMessage(MaplePacketCreator.serverNotice(6, "Andras has appeared."));
            spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(9400609), new Point(389, 96));
        }
    }

    public void removeNPC(int npcid) {
        MapleNPC npc = getNPC(npcid);
        broadcastMessage(MaplePacketCreator.removeNPC(npc.getObjectId()));
        removeMapObject(npc);
    }

    public MapleNPC getNPC(int npcid) {
        for (MapleMapObject obj : getAllNPCs()) {
            MapleNPC npc = (MapleNPC) obj;
            if (npc.getId() == npcid) {
                return npc;
            }
        }
        return null;
    }

    public void addNPC(int npcid, int x, int y) {
        addNPC(npcid, new Point(x, y));
    }

    public void addNPC(int npcid, Point pos) {
        MapleNPC npc = MapleLifeFactory.getNPC(npcid);
        if (npc == null) {
            log.error("Trying to spawn an NPC that doesn't exist on map " + mapId);
            return;
        }
        npc.setPosition(pos);
        npc.setCy(pos.y);
        npc.setRx0(pos.x - 50);
        npc.setRx1(pos.x + 50);
        npc.setFh(footholds.findBelow(pos).getId());
        npc.setCustom(true);
        addMapObject(npc);
        broadcastMessage(MaplePacketCreator.spawnNPC(npc));
    }

    public void removePlayer(MapleCharacter chr) {
        objectLock.lock();
        try {
            characters.remove(chr);
        } finally {
            objectLock.unlock();
        }
        removeMapObject(chr.getObjectId());
        broadcastMessage(MaplePacketCreator.removePlayerFromMap(chr.getId()));
        if (chr.getNumControlledMonsters() > 0) {
            final List<MapleMonster> monsters = new ArrayList<>(chr.getControlledMonsters());
            for (MapleMonster monster : monsters) {
                monster.setController(null);
                monster.setControllerHasAggro(false);
                monster.setControllerKnowsAboutAggro(false);
                updateMonsterController(monster);
            }
        }
        chr.leaveMap();
        chr.cancelMapTimeLimitTask();

        for (MapleSummon summon : chr.getSummons().values()) {
            if (summon.isPuppet()) {
                chr.cancelBuffStats(MapleBuffStat.PUPPET);
            } else {
                removeMapObject(summon);
            }
        }
        chr.dispelDebuff(MapleDisease.GM_DISABLE_SKILL);
        chr.dispelDebuff(MapleDisease.GM_DISABLE_MOVEMENT);
        chr.offBeacon(true);
    }

    /**
     * Broadcast a message to everyone in the map
     *
     * @param packet
     */
    public void broadcastMessage(MaplePacket packet) {
        broadcastMessage(null, packet, Double.POSITIVE_INFINITY, null, null);
    }

    /**
     * Nonranged. Repeat to source according to parameter.
     *
     * @param source
     * @param packet
     * @param repeatToSource
     */
    public void broadcastMessage(MapleCharacter source, MaplePacket packet, boolean repeatToSource) {
        broadcastMessage(repeatToSource ? null : source, packet, Double.POSITIVE_INFINITY, source.getPosition(), null);
    }

    /**
     * Ranged and repeat according to parameters.
     *
     * @param source
     * @param packet
     * @param repeatToSource
     * @param ranged
     */
    public void broadcastMessage(MapleCharacter source, MaplePacket packet, boolean repeatToSource, boolean ranged) {
        broadcastMessage(repeatToSource ? null : source, packet, ranged ? MapleCharacter.MAX_VIEW_RANGE_SQ : Double.POSITIVE_INFINITY, source.getPosition(), null);
    }

    /**
     * Always ranged from Point.
     *
     * @param packet
     * @param rangedFrom
     */
    public void broadcastMessage(MaplePacket packet, Point rangedFrom) {
        broadcastMessage(null, packet, MapleCharacter.MAX_VIEW_RANGE_SQ, rangedFrom, null);
    }

    public void broadcastMessage(MaplePacket packet, Point rangedFrom, MapleMapObject mo) {
        broadcastMessage(null, packet, MapleCharacter.MAX_VIEW_RANGE_SQ, rangedFrom, mo);
    }

    /**
     * Always ranged from point. Does not repeat to source.
     *
     * @param source
     * @param packet
     * @param rangedFrom
     */
    public void broadcastMessage(MapleCharacter source, MaplePacket packet, Point rangedFrom) {
        broadcastMessage(source, packet, MapleCharacter.MAX_VIEW_RANGE_SQ, rangedFrom, null);
    }

    private void broadcastMessage(MapleCharacter source, MaplePacket packet, double rangeSq, Point rangedFrom, MapleMapObject mo) {
        objectLock.lock();
        try {
            for (MapleCharacter chr : characters) {
                if (chr != source) {
                    if (rangeSq < Double.POSITIVE_INFINITY) {
                        if (rangedFrom.distanceSq(chr.getPosition()) <= rangeSq && chr.canSeeItem(mo)) {
                            chr.getClient().sendPacket(packet);
                        }
                    } else {
                        chr.getClient().sendPacket(packet);
                    }
                }
            }
        } finally {
            objectLock.unlock();
        }
    }

    private boolean isNonRangedType(MapleMapObjectType type) {
        return switch (type) {
            case PLAYER, MIST, HIRED_MERCHANT -> true;
            default -> false;
        };
    }

    private void sendObjectPlacement(MapleCharacter chr) {
        final List<MapleMapObject> objects = getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Arrays.asList(MapleMapObjectType.PLAYER, MapleMapObjectType.MIST, MapleMapObjectType.HIRED_MERCHANT, MapleMapObjectType.MONSTER));
        for (MapleMapObject o : objects) {
            if (isNonRangedType(o.getType())) {
                o.sendSpawnData(chr.getClient());
            } else if (o instanceof MapleMonster) {
                updateMonsterController((MapleMonster) o);
            }
        }

        if (chr != null) {
            for (MapleMapObject o : getMapObjectsInRange(chr.getPosition(), MapleCharacter.MAX_VIEW_RANGE_SQ, rangedMapobjectTypes)) {
                if (o instanceof MapleReactor) {
                    if (((MapleReactor) o).isAlive()) {
                        o.sendSpawnData(chr.getClient());
                        chr.addVisibleMapObject(o);
                    }
                } else {
                    if (chr.canSeeItem(o)) {
                        o.sendSpawnData(chr.getClient());
                        chr.addVisibleMapObject(o);
                    }
                }
            }
        } else {
            log.info("sendObjectPlacement invoked with null char");
        }
    }

    public final List<MapleMapObject> getAllItems() {
        return getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Collections.singletonList(MapleMapObjectType.ITEM));
    }

    public final List<MapleMapObject> getAllNPCs() {
        return getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Collections.singletonList(MapleMapObjectType.NPC));
    }

    public final List<MapleMapObject> getAllReactors() {
        return getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Collections.singletonList(MapleMapObjectType.REACTOR));
    }

    public final List<MapleMapObject> getAllMonsters() {
        return getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Collections.singletonList(MapleMapObjectType.MONSTER));
    }

    public final List<MapleMapObject> getAllDoors() {
        return getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Collections.singletonList(MapleMapObjectType.DOOR));
    }

    public List<MapleMapObject> getMapObjectsInRange(Point from, double rangeSq, List<MapleMapObjectType> types) {
        List<MapleMapObject> ret = new LinkedList<>();
        objectLock.lock();
        try {
            for (MapleMapObject l : mapobjects.values()) {
                if (types.contains(l.getType()) && from.distanceSq(l.getPosition()) <= rangeSq) {
                    ret.add(l);
                }
            }
        } finally {
            objectLock.unlock();
        }
        return ret;
    }

    public List<IMaplePlayerShop> getPlayerShops() {
        List<IMaplePlayerShop> ret = new LinkedList<>();
        for (MapleMapObject l : getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Arrays.asList(MapleMapObjectType.HIRED_MERCHANT, MapleMapObjectType.SHOP))) {
            ret.add((IMaplePlayerShop) l);
        }
        return ret;
    }

    public List<MapleMapObject> getMapObjectsInRect(Rectangle box, List<MapleMapObjectType> types) {
        List<MapleMapObject> ret = new LinkedList<>();
        objectLock.lock();
        try {
            for (MapleMapObject l : mapobjects.values()) {
                if (types.contains(l.getType())) {
                    if (box.contains(l.getPosition())) {
                        ret.add(l);
                    }
                }
            }
        } finally {
            objectLock.unlock();
        }
        return ret;
    }

    public List<MapleCharacter> getPlayersInRect(Rectangle box, List<MapleCharacter> chr) {
        List<MapleCharacter> character = new LinkedList<>();
        objectLock.lock();
        try {
            for (MapleCharacter a : characters) {
                if (chr.contains(a.getClient().getPlayer())) {
                    if (box.contains(a.getPosition())) {
                        character.add(a);
                    }
                }
            }
        } finally {
            objectLock.unlock();
        }
        return character;
    }

    public List<MapleCharacter> getPlayersInRect(Rectangle box) {
        List<MapleCharacter> character = new LinkedList<>();
        objectLock.lock();
        try {
            for (MapleCharacter a : characters) {
                if (box.contains(a.getPosition())) {
                    character.add(a);
                }
            }
        } finally {
            objectLock.unlock();
        }
        return character;
    }

    public void addSeat(int seat, Point p) {
        seats.put(seat, p);
    }

    public Point getSeat(int seat) {
        return seats.get(seat);
    }

    public void addPortal(MaplePortal myPortal) {
        portals.put(myPortal.getId(), myPortal);
        if (myPortal.getType() == MaplePortal.SPAWN_POINT) {
            spawnPoints.add(myPortal);
        }
    }

    public MaplePortal getPortal(String portalname) {
        for (MaplePortal port : portals.values()) {
            if (port.getName().equals(portalname)) {
                return port;
            }
        }
        return null;
    }

    public MaplePortal getPortal(int portalid) {
        return portals.get(portalid);
    }

    public List<MaplePortal> getSpawnPoints() {
        return spawnPoints;
    }

    public MaplePortal getRandomSpawnPoint() {
        return spawnPoints.get(Randomizer.nextInt(spawnPoints.size()));
    }

    public void addMapleArea(Rectangle rec) {
        areas.add(rec);
    }

    public List<Rectangle> getAreas() {
        return new ArrayList<>(areas);
    }

    public Rectangle getArea(int index) {
        return areas.get(index);
    }

    public void setFootholds(MapleFootholdTree footholds) {
        this.footholds = footholds;
    }

    public MapleFootholdTree getFootholds() {
        return footholds;
    }

    public void addMonsterSpawn(MapleMonster monster, int mobTime) {
        Point newpos = calcPointBelow(monster.getPosition());
        newpos.y -= 1;
        SpawnPoint sp = new SpawnPoint(monster, newpos, mobTime);

        monsterSpawn.add(sp);
    }

    public void addRawMonsterSpawn(SpawnPoint sp) {
        monsterSpawn.add(sp);
    }

    public float getMonsterRate() {
        return monsterRate;
    }

    public List<MapleCharacter> getCharacters() {
        final List<MapleCharacter> chars = new LinkedList<>();
        objectLock.lock();
        try {
            chars.addAll(characters);
        } finally {
            objectLock.unlock();
        }
        return chars;
    }

    public MapleCharacter getCharacterById(int id) {
        objectLock.lock();
        try {
            for (MapleCharacter c : characters) {
                if (c.getId() == id) {
                    return c;
                }
            }
        } finally {
            objectLock.unlock();
        }
        return null;
    }

    private void updateMapObjectVisibility(MapleCharacter chr, MapleMapObject mo) {
        if (!chr.isMapObjectVisible(mo)) { // monster entered view range
            if (mo instanceof MapleSummon || mo.getPosition().distanceSq(chr.getPosition()) <= MapleCharacter.MAX_VIEW_RANGE_SQ) {
                if (chr.canSeeItem(mo)) {
                    chr.addVisibleMapObject(mo);
                    mo.sendSpawnData(chr.getClient());
                }
            }
        } else { // monster left view range
            if (!(mo instanceof MapleSummon) && mo.getPosition().distanceSq(chr.getPosition()) > MapleCharacter.MAX_VIEW_RANGE_SQ) {
                chr.removeVisibleMapObject(mo);
                mo.sendDestroyData(chr.getClient());
            }
        }
    }

    public void moveMonster(MapleMonster monster, Point reportedPos) {
        monster.setPosition(reportedPos);
        MapleFoothold fh = footholds.findBelow(reportedPos);
        if (fh != null) {
            monster.setFh(fh.getId());
        }
        objectLock.lock();
        try {
            for (MapleCharacter chr : characters) {
                updateMapObjectVisibility(chr, monster);
            }
        } finally {
            objectLock.unlock();
        }
    }

    public void movePlayer(MapleCharacter player, Point newPosition) {
        player.setPosition(newPosition);
        final MapleFoothold fh = footholds.findBelow(newPosition);
        player.setFoothold(fh != null ? fh.getId() : 0);
        Collection<MapleMapObject> visibleObjects = player.getVisibleMapObjects();
        MapleMapObject[] visibleObjectsNow = visibleObjects.toArray(new MapleMapObject[0]);
        for (MapleMapObject mo : visibleObjectsNow) {
            if (mo != null) {
                if (mapobjects.get(mo.getObjectId()) == mo) {
                    updateMapObjectVisibility(player, mo);
                } else {
                    player.removeVisibleMapObject(mo);
                }
            }
        }
        for (MapleMapObject mo : getMapObjectsInRange(player.getPosition(), MapleCharacter.MAX_VIEW_RANGE_SQ, rangedMapobjectTypes)) {
            if (mo != null) {
                if (!player.isMapObjectVisible(mo) && player.canSeeItem(mo)) {
                    mo.sendSpawnData(player.getClient());
                    player.addVisibleMapObject(mo);
                }
            }
        }
        if (mapId == 240040611) {
            if (!getMapObjectsInRange(player.getPosition(), 25000, Collections.singletonList(MapleMapObjectType.REACTOR)).isEmpty()) {
                MapleReactor reactor = getReactorById(2408004);
                if (reactor.getState() == 0) {
                    reactor.hitReactor(player.getClient());
                }
            }
        }
        if (!canMovement && !player.isGM()) {
            // No more movement!
            if (!player.getDiseases().contains(MapleDisease.GM_DISABLE_MOVEMENT)) {
                player.giveDebuff(MapleDisease.GM_DISABLE_MOVEMENT, MobSkillFactory.getMobSkill(123, 1), true);
            }
        }
    }

    public MaplePortal findClosestSpawnPoint(Point from) {
        MaplePortal closest = null;
        double shortestDistance = Double.POSITIVE_INFINITY;
        for (MaplePortal portal : spawnPoints) {
            double distance = portal.getPosition().distanceSq(from);
            if (distance < shortestDistance) {
                closest = portal;
                shortestDistance = distance;
            }
        }
        return closest;
    }

    public void setSpawnRateMulti(int sr) {
        if (sr == 0) {
            return;
        }
        boolean decSpawn = sr < 0;
        if (decSpawn) {
            if (origMobRate < 1) {
                monsterRate *= -sr;
            } else if (origMobRate >= 1) {
                monsterRate /= -sr;
            }
        } else {
            if (origMobRate < 1) {
                monsterRate /= sr;
            } else if (origMobRate >= 1) {
                monsterRate *= sr;
            }
        }

    }

    public float getSpawnRate() {
        return monsterRate;
    }

    public float getOrigSpawnRate() {
        return origMobRate;
    }

    public void setSpawnRate(float sr) {
        monsterRate = sr;
    }

    public void resetSpawnRate() {
        monsterRate = origMobRate;
    }

    public boolean isSpawnRateModified() {
        return monsterRate != origMobRate;
    }

    public void spawnDebug(MessageCallback mc) {
        mc.dropMessage("Spawndebug...");
        mc.dropMessage("Mapobjects in map: " + mapobjects.size() + " \"spawnedMonstersOnMap\": " + spawnedMonstersOnMap + " spawnpoints: " + monsterSpawn.size() + " maxRegularSpawn: " + getMaxCurrentSpawn() + " spawnRate: " + monsterRate + " original spawnRate: " + origMobRate);
        mc.dropMessage("actual monsters: " + getAllMonsters().size());
    }

    private int getMaxCurrentSpawn() {
        if (origMobRate < 1) {
            return (int) (monsterSpawn.size() / monsterRate) - spawnedMonstersOnMap.get();
        } else {
            return (int) (monsterSpawn.size() * monsterRate) - spawnedMonstersOnMap.get();
        }
    }

    public Collection<MaplePortal> getPortals() {
        return portals.values();
    }

    public String getMapName() {
        return mapName;
    }

    public void setMapName(String mapName) {
        this.mapName = mapName;
    }

    public String getStreetName() {
        return streetName;
    }

    public void setClock(boolean hasClock) {
        clock = hasClock;
    }

    public boolean hasClock() {
        return clock;
    }

    public void setTown(boolean isTown) {
        town = isTown;
    }

    public boolean isTown() {
        return town;
    }

    public void setAllowShops(boolean allowShops) {
        this.allowShops = allowShops;
    }

    public boolean allowShops() {
        return allowShops;
    }

    public void setStreetName(String streetName) {
        this.streetName = streetName;
    }

    public void setEverlast(boolean everlast) {
        this.everlast = everlast;
    }

    public boolean getEverlast() {
        return everlast;
    }

    public int getSpawnedMonstersOnMap() {
        return spawnedMonstersOnMap.get();
    }

    public void resetSpawn() {
        if (spawnWorker != null) {
            spawnWorker.cancel(true);
        }
        if (monsterRate > 0) {
            spawnWorker = TimerManager.getInstance().register(new RespawnWorker(), createMobInterval);
        }
    }

    public final void setCreateMobInterval(final short createMobInterval) {
        this.createMobInterval = createMobInterval;
    }

    public final void loadMonsterRate() {
		/*final int spawnSize = monsterSpawn.size();
		maxRegularSpawn = Math.round(spawnSize * monsterRate);
		if (maxRegularSpawn < 2) {
			maxRegularSpawn = 2;
		} else if (maxRegularSpawn > spawnSize) {
			maxRegularSpawn = spawnSize - (spawnSize / 15);
		}
		Collection<SpawnPoint> newSpawn = new LinkedList<SpawnPoint>();
		Collection<SpawnPoint> newBossSpawn = new LinkedList<SpawnPoint>();
		for (final SpawnPoint s : monsterSpawn) {
			if (s.isBoss()) {
				newBossSpawn.add(s);
			} else {
				newSpawn.add(s);
			}
		}
		monsterSpawn.clear();
		monsterSpawn.addAll(newBossSpawn);
		monsterSpawn.addAll(newSpawn);*/
        if (!monsterSpawn.isEmpty()) {
            if (spawnWorker != null) {
                spawnWorker.cancel(true);
            }
            spawnWorker = TimerManager.getInstance().register(() -> respawn(), createMobInterval);
        }
    }

    public void respawn() {
        if (characters.isEmpty()) {
            return;
        }

		/*final int numShouldSpawn = maxRegularSpawn - spawnedMonstersOnMap.get();
		if (numShouldSpawn > 0) {
			int spawned = 0;

			final List<SpawnPoint> randomSpawn = new ArrayList<SpawnPoint>(monsterSpawn);
			Collections.shuffle(randomSpawn);

			for (SpawnPoint spawnPoint : randomSpawn) {
				if (spawnPoint.shouldSpawn()) {
					spawnPoint.spawnMonster(this);
					spawned++;
				}
				if (spawned >= numShouldSpawn) {
					break;
				}
			}
		}*/

        if (mapId == 230040400 || mapId == 240010500) {
            int ispawnedMonstersOnMap = spawnedMonstersOnMap.get();
            int numShouldSpawn = (int) Math.round(Math.random() * (2 + characters.size() / 1.5 + ((int) (monsterSpawn.size() / monsterRate) - ispawnedMonstersOnMap) / 4.0));
            if (numShouldSpawn + ispawnedMonstersOnMap > (int) (monsterSpawn.size() / monsterRate)) {
                numShouldSpawn = (int) (monsterSpawn.size() / monsterRate) - ispawnedMonstersOnMap;
            }
            if (numShouldSpawn > 0) {
                List<SpawnPoint> randomSpawn = new ArrayList<>(monsterSpawn);
                Collections.shuffle(randomSpawn);
                int spawned = 0;
                for (SpawnPoint spawnPoint : randomSpawn) {
                    if (spawnPoint.shouldSpawn()) {
                        spawnPoint.spawnMonster(this);
                        spawned++;
                    }
                    if (spawned >= numShouldSpawn) {
                        break;
                    }
                }
            }
        } else {
            int numShouldSpawn = getMaxCurrentSpawn();
            if (numShouldSpawn > 0) {
                int spawned = 0;
                for (SpawnPoint spawnPoint : monsterSpawn) {
                    if (!spawnPoint.isBoss()) {
                        continue;
                    }
                    if (spawnPoint.shouldSpawn()) {
                        spawnPoint.spawnMonster(this);
                        spawned++;
                    }
                    if (spawned >= numShouldSpawn) {
                        break;
                    }
                }
                List<SpawnPoint> randomSpawn = new ArrayList<>(monsterSpawn);
                Collections.shuffle(randomSpawn);
                if (spawned < numShouldSpawn) {
                    for (SpawnPoint spawnPoint : randomSpawn) {
                        if (spawnPoint.isBoss()) {
                            continue;
                        }
                        if (spawnPoint.shouldSpawn()) {
                            spawnPoint.spawnMonster(this);
                            spawned++;
                        }
                        if (spawned >= numShouldSpawn) {
                            break;
                        }
                    }
                }
            }
        }
    }

    public short getHPDec() {
        return decHP;
    }

    public void setHPDec(short delta) {
        decHP = delta;
    }

    public int getHPDecProtect() {
        return protectItem;
    }

    public void setHPDecProtect(int delta) {
        protectItem = delta;
    }

    public int hasBoat() {
        if (boat && docked) {
            return 2;
        } else if (boat) {
            return 1;
        } else {
            return 0;
        }
    }

    public void setBoat(boolean hasBoat) {
        boat = hasBoat;
    }

    public void setDocked(boolean isDocked) {
        docked = isDocked;
    }

    public void setEvent(boolean hasEvent) {
        this.hasEvent = hasEvent;
    }

    public boolean hasEvent() {
        return hasEvent;
    }

    public int countCharsOnMap() {
        return characters.size();
    }

    public int countMobOnMap(int monsterid) {
        int count = 0;
        for (MapleMapObject mmo : getAllMonsters()) {
            if (((MapleMonster) mmo).getId() == monsterid) {
                count++;
            }
        }
        return count;
    }

    public void setOx(MapleOxQuiz set) {
        ox = set;
    }

    public MapleOxQuiz getOx() {
        return ox;
    }

    public MapleReactor getReactorById(int id) {
        for (MapleMapObject obj : getAllReactors()) {
            MapleReactor reactor = (MapleReactor) obj;
            if (reactor.getId() == id) {
                return reactor;
            }
        }
        return null;
    }

    public boolean isPQMap() { //Does NOT include CPQ maps
        return mapId > 922010000 && mapId < 922011100 || mapId >= 103000800 && mapId < 103000890;
    }

    public boolean isCPQMap() {
        return switch (mapId) {
            case 980000101, 980000201, 980000301, 980000401, 980000501, 980000601 -> true;
            default -> false;
        };
    }

    public boolean isBlueCPQMap() {
        return switch (mapId) {
            case 980000501, 980000601 -> true;
            default -> false;
        };
    }

    public boolean isPurpleCPQMap() {
        return switch (mapId) {
            case 980000301, 980000401 -> true;
            default -> false;
        };
    }

    public void addClock(int seconds) {
        broadcastMessage(MaplePacketCreator.getClock(seconds));
    }

    public boolean cannotInvincible() {
        return cannotInvincible;
    }

    public void setCannotInvincible(boolean b) {
        cannotInvincible = b;
    }

    public void setFieldLimit(int fl) {
        fieldLimit = fl;
        canVipRock = !FieldLimit.CANNOTVIPROCK.check(fl);
    }

    public int getFieldLimit() {
        return fieldLimit;
    }

    public boolean canVipRock() {
        return canVipRock;
    }

    public IMaplePlayerShop getMaplePlayerShopByOwnerName(String name) {
        for (MapleMapObject l : getMapObjectsInRange(new Point(0, 0), Double.POSITIVE_INFINITY, Arrays.asList(MapleMapObjectType.HIRED_MERCHANT, MapleMapObjectType.SHOP))) {
            IMaplePlayerShop aps = (IMaplePlayerShop) l;
            if (aps.getOwnerName().equalsIgnoreCase(name)) {
                return aps;
            }
        }
        return null;
    }

    public void setFirstUserEnter(String onFirstUserEnter) {
        this.onFirstUserEnter = onFirstUserEnter;
    }

    public void setUserEnter(String onUserEnter) {
        this.onUserEnter = onUserEnter;
    }

    public void setPartyOnly(boolean party) {
        partyOnly = party;
    }

    public boolean isPartyOnly() {
        return partyOnly;
    }

    public void setLevelLimit(int limit) {
        levelLimit = limit;
    }

    public int getLevelLimit() {
        return levelLimit;
    }

    public void setLevelForceMove(int limit) {
        lvForceMove = limit;
    }

    public int getLevelForceMove() {
        return lvForceMove;
    }

    public boolean isDojoMap() {
        return mapId / 1000000 == 925 && getDojoStage() != 0 && getDojoStage() % 6 != 0;
    }

    public int getDojoStage() {
        return Integer.parseInt(String.valueOf(mapId).substring(5, 7));
    }

    public int getDojoBoss() {
        return 9300183 + getDojoStage() - getDojoStage() / 6;
    }

    public boolean isDojoRestMap() {
        return mapId / 1000000 == 925 && getDojoStage() != 0 && getDojoStage() % 6 == 0;
    }

    public int getNextDojoMap() {
        String f = "%02d";
        String z = String.format(f, getDojoStage() + 1);
        return Integer.parseInt("92502" + z + "00");
    }

    public void enableDojoSpawn() {
        dojoSpawn = TimerManager.getInstance().register(new DojoSpawn(), 7000, 7000);
    }

    public void disableDojoSpawn() {
        if (dojoSpawn != null) {
            dojoSpawn.cancel(false);
            dojoSpawn = null;
        }
    }

    private int getPlayerNPCMap() {
        return switch (mapId) {
            case 100000204, 100000205 -> 300;
            case 101000004, 101000005 -> 100;
            case 102000004, 102000005 -> 200;
            case 103000008, 103000009 -> 400;
            default -> -1;
        };
    }

    private void loadPlayerNPCs() {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            ps = con.prepareStatement("SELECT * FROM characters WHERE level = 200 AND (job >= " + getPlayerNPCMap() + " AND job < " + (getPlayerNPCMap() - 100) + ") AND gm < 2 ORDER BY lastLevelUpTime LIMIT 10");
            rs = ps.executeQuery();
            while (rs.next()) {
                playerNPCs.add(new MaplePlayerNPC(rs.getInt("id"), rs.getString("name"), rs.getInt("hair"), rs.getInt("face"), rs.getInt("skin"), rs.getInt("gender"), rs.getInt("job")));
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            System.out.println("Error loading PlayerNPCs for Map : " + mapId);
        }
    }

    public void setBuffZone(MapleBuffZone zone) {
        buffZone = zone;
    }

    public MapleBuffZone getBuffZone() {
        return buffZone;
    }

    private interface DelayedPacketCreation {

        void sendPackets(MapleClient c);
    }

    private interface SpawnCondition {

        boolean canSpawn(MapleCharacter chr);
    }

    private class ExpireMapItemJob implements Runnable {

        private final MapleMapItem mapitem;

        public ExpireMapItemJob(MapleMapItem mapitem) {
            this.mapitem = mapitem;
        }

        @Override
        public void run() {
            if (mapitem != null && mapitem == getMapObject(mapitem.getObjectId())) {
                synchronized (mapitem) {
                    if (mapitem.isPickedUp()) {
                        return;
                    }
                    broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 0, 0), mapitem.getPosition());
                    removeMapObject(mapitem);
                    mapitem.setPickedUp(true);
                }
            }
        }
    }

    private class ActivateItemReactor implements Runnable {

        private final MapleMapItem mapitem;
        private final MapleReactor reactor;
        private final MapleClient c;

        public ActivateItemReactor(MapleMapItem mapitem, MapleReactor reactor, MapleClient c) {
            this.mapitem = mapitem;
            this.reactor = reactor;
            this.c = c;
        }

        @Override
        public void run() {
            reactor.setTimerActive(false);
            if (mapitem != null && mapitem == getMapObject(mapitem.getObjectId())) {
                synchronized (mapitem) {
                    TimerManager tMan = TimerManager.getInstance();
                    if (mapitem.isPickedUp()) {
                        return;
                    }
                    broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 0, 0), mapitem.getPosition());
                    removeMapObject(mapitem);
                    reactor.hitReactor(c);
                    if (reactor.getDelay() > 0) { //This shit is negative.. Fix?
                        tMan.schedule(() -> {
                            reactor.setState((byte) 0);
                            broadcastMessage(MaplePacketCreator.triggerReactor(reactor, 0));
                        }, reactor.getDelay());
                    }
                }
            }
        }
    }

    private class RespawnWorker implements Runnable {

        @Override
        public void run() {
            respawn();
        }
    }

    private class DojoSpawn implements Runnable {

        @Override
        public void run() {
            if (countMobOnMap(getDojoBoss()) == 1) {
                for (MapleMapObject mmo : getAllMonsters()) {
                    MapleMonster mob = (MapleMonster) mmo;
                    if (mob.getId() == getDojoBoss()) {
                        for (int mid : mob.getDojoBossSpawns()) {
                            if (mid != 0) {
                                spawnMonsterOnGroundBelow(MapleLifeFactory.getMonster(mid), mob.getPosition());
                            }
                        }
                    }
                }
            }
        }
    }
}