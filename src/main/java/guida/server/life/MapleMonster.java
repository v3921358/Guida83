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

package guida.server.life;

import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleDisease;
import guida.client.MapleJob;
import guida.client.MapleQuestStatus;
import guida.client.SkillFactory;
import guida.client.status.MonsterStatus;
import guida.client.status.MonsterStatusEffect;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.scripting.event.EventInstanceManager;
import guida.server.TimerManager;
import guida.server.life.MapleMonsterInformationProvider.DropEntry;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapObjectType;
import guida.tools.ArrayMap;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.Randomizer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;

public class MapleMonster extends AbstractLoadedMapleLife {

    private final Collection<AttackerEntry> attackers = new LinkedList<>();
    private final Collection<MonsterListener> listeners = new LinkedList<>();
    private final Map<MonsterStatus, MonsterStatusEffect> stati = new EnumMap<>(MonsterStatus.class);
    private final List<Pair<Integer, Integer>> usedSkills = new ArrayList<>();
    private final Map<Pair<Integer, Integer>, Integer> skillsUsed = new HashMap<>();
    private final Map<MonsterStatus, Integer> monsterBuffs = new EnumMap<>(MonsterStatus.class);
    private MapleMonsterStats stats;
    private MapleMonsterStats overrideStats;
    private int hp;
    private int mp;
    private WeakReference<MapleCharacter> controller = new WeakReference<>(null);
    private boolean controllerHasAggro, controllerKnowsAboutAggro;
    private EventInstanceManager eventInstance = null;
    private MapleCharacter highestDamageChar;
    private MapleMap map;
    private int VenomMultiplier = 0;
    private boolean fake = false;
    private boolean dropsDisabled = false;
    private boolean hpLock = false;
    private boolean stolen = false;
    private MapleMonster sponge = null;
    private boolean moveLock = false;
    private MapleCharacter summonedBy = null;

    public MapleMonster(int id, MapleMonsterStats stats) {
        super(id);
        initWithStats(stats);
    }

    public MapleMonster(MapleMonster monster) {
        super(monster);
        initWithStats(monster.stats);
    }

    private void initWithStats(MapleMonsterStats stats) {
        setStance((byte) 5);
        this.stats = stats;
        hp = stats.getHp();
        mp = stats.getMp();
    }

    public boolean isHpLocked() {
        return hpLock;
    }

    public void setHpLock(boolean b) {
        hpLock = b;
    }

    public void disableDrops() {
        dropsDisabled = true;
    }

    public boolean dropsDisabled() {
        return dropsDisabled;
    }

    public void setMap(MapleMap map) {
        this.map = map;
    }

    public int getDrop(MapleCharacter killer) {
        MapleMonsterInformationProvider mi = MapleMonsterInformationProvider.getInstance();
        int lastAssigned = -1;
        int minChance = 1;
        List<DropEntry> dl = mi.retrieveDropChances(getId());
        for (DropEntry d : dl) {
            if (d.chance > minChance) {
                minChance = d.chance;
            }
        }
        for (DropEntry d : dl) {
            d.assignedRangeStart = lastAssigned + 1;
            d.assignedRangeLength = (int) Math.ceil((double) 1 / (double) d.chance * minChance);
            lastAssigned += d.assignedRangeLength;
        }
        int c = Randomizer.nextInt(minChance);
        for (DropEntry d : dl) {
            int itemid = d.itemid;
            if (c >= d.assignedRangeStart && c < d.assignedRangeStart + d.assignedRangeLength) {
                if (d.questid != 0) {
                    if (killer.getQuest(d.questid).getStatus() == MapleQuestStatus.Status.STARTED) {
                        return itemid;
                    }
                } else {
                    return itemid;
                }
            }
        }
        return -1;
    }

    public int getMaxDrops(MapleCharacter chr) {
        ChannelServer cserv = chr.getClient().getChannelServer();
        int maxDrops;
        if (isPQMonster()) {
            maxDrops = 1; //PQ Monsters always drop a max of 1 item (pass) - I think? MonsterCarnival monsters don't count
        } else if (isExplosive()) {
            maxDrops = 10 * cserv.getBossDropRate();
        } else if (isBoss() && !isExplosive()) {
            maxDrops = 7 * cserv.getBossDropRate();
        } else if (getId() == 9400202) {
            maxDrops = 4;
        } else {
            maxDrops = 4 * cserv.getDropRate();
            if (stati.containsKey(MonsterStatus.TAUNT)) {
                int alterDrops = stati.get(MonsterStatus.TAUNT).getStati().get(MonsterStatus.TAUNT);
                maxDrops *= 1 + alterDrops / 100;
            }
        }
        return maxDrops;
    }

    public boolean isPQMonster() {
        int id = getId();
        return id >= 9300000 && id <= 9300003 || id >= 9300005 && id <= 9300010 || id >= 9300012 && id <= 9300017 || id >= 9300169 && id <= 9300171;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        if (hpLock) {
            return;
        }
        this.hp = hp;
    }

    public int getMaxHp() {
        if (overrideStats != null) {
            return overrideStats.getHp();
        }
        return stats.getHp();
    }

    public int getHpPercentage() {
        return (getHp() * 100) / getMaxHp();
    }

    public int getMp() {
        return mp;
    }

    public void setMp(int mp) {
        if (mp < 0) {
            mp = 0;
        }
        this.mp = mp;
    }

    public int getMaxMp() {
        if (overrideStats != null) {
            return overrideStats.getMp();
        }
        return stats.getMp();
    }

    public int getMpPercentage() {
        return (getMp() * 100) / getMaxMp();
    }

    public int getExp() {
        if (overrideStats != null) {
            return overrideStats.getExp();
        }
        return stats.getExp();
    }

    public int getLevel() {
        return stats.getLevel();
    }

    public int getRemoveAfter() {
        if (overrideStats != null) {
            return overrideStats.getRemoveAfter();
        }
        return stats.getRemoveAfter();
    }

    public int getVenomMulti() {
        return VenomMultiplier;
    }

    public void setVenomMulti(int multiplier) {
        VenomMultiplier = multiplier;
    }

    public boolean isBoss() {
        return stats.isBoss() || getId() == 8810018;
    }

    public boolean isHalloweenBoss() {
        return getId() >= 9500325 && getId() <= 9500332 || getId() == 9400571 || getId() == 9400572 || getId() >= 9500174 && getId() <= 9500176;
    }

    public boolean isFfaLoot() {
        return stats.isFfaLoot();
    }

    public boolean isExplosive() {
        return stats.isExplosive();
    }

    public int getAnimationTime(String name) {
        return stats.getAnimationTime(name);
    }

    public List<Integer> getRevives() {
        return stats.getRevives();
    }

    public void setOverrideStats(MapleMonsterStats overrideStats) {
        this.overrideStats = overrideStats;
    }

    public byte getTagColor() {
        return stats.getTagColor();
    }

    public byte getTagBgColor() {
        return stats.getTagBgColor();
    }

    public boolean getUndead() {
        return stats.getUndead();
    }

    public void setSponge(MapleMonster mob) {
        sponge = mob;
    }

    public MapleMonster getSponge() {
        return sponge;
    }

    public boolean getFly() {
        return stats.getFly();
    }

    public boolean getMobile() {
        return stats.getMobile();
    }

    public boolean canRegen() {
        return !stats.getNoRegen();
    }

    /**
     * @param from   the player that dealt the damage
     * @param damage
     */
    public void damage(MapleCharacter from, int damage, boolean updateAttackTime) {
        AttackerEntry attacker = null;

        if (from.getParty() != null) {
            attacker = new PartyAttackerEntry(from.getParty().getId(), from.getClient().getChannelServer());
        } else {
            attacker = new SingleAttackerEntry(from, from.getClient().getChannelServer());
        }

        boolean replaced = false;
        for (AttackerEntry aentry : attackers) {
            if (aentry.equals(attacker)) {
                attacker = aentry;
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            attackers.add(attacker);
        }

        int rDamage = Math.max(0, Math.min(damage, hp));
        if (hpLock) {
            rDamage = 0;
        }
        attacker.addDamage(from, rDamage, updateAttackTime);
        hp -= rDamage;
        int remhppercentage = (int) Math.ceil(hp * 100.0 / getMaxHp());
        if (remhppercentage < 1) {
            remhppercentage = 1;
        }
        long okTime = System.currentTimeMillis() - 4000;
        if (hasBossHPBar()) {
            from.getMap().broadcastMessage(makeBossHPBarPacket(), getPosition());
        } else if (!isBoss() || isDojoBoss()) {
            LinkedList<AttackerEntry> attackersCopy = new LinkedList<>(attackers);
            for (AttackerEntry mattacker : attackersCopy) {
                for (AttackingMapleCharacter cattacker : mattacker.getAttackers()) {
                    // current attacker is on the map of the monster
                    if (cattacker.getAttacker().getMap() == from.getMap()) {
                        if (cattacker.getLastAttackTime() >= okTime) {
                            cattacker.getAttacker().getClient().sendPacket(MaplePacketCreator.showMonsterHP(getObjectId(), remhppercentage));
                        }
                    }
                }
            }
            attackersCopy.clear();
        }
    }

    public void heal(int level, int hp, int mp) {
        int hp2Heal = this.hp + hp;
        int mp2Heal = this.mp + mp;

        if (hp2Heal >= getMaxHp()) {
            hp2Heal = getMaxHp();
        }
        if (mp2Heal >= getMaxMp()) {
            mp2Heal = getMaxMp();
        }

        setHp(hp2Heal);
        setMp(mp2Heal);
        map.broadcastMessage(MaplePacketCreator.healMonster(getObjectId(), hp));
        map.broadcastMessage(MaplePacketCreator.monsterSkillEffect(getObjectId(), 114, level));
    }

    public boolean isAttackedBy(MapleCharacter chr) {
        for (AttackerEntry aentry : attackers) {
            if (aentry.contains(chr)) {
                return true;
            }
        }
        return false;
    }

    private void giveExpToCharacter(MapleCharacter attacker, int exp, boolean highestDamage, int numExpSharers, int leechProt) {
        if (getId() == 9300027) {
            exp = 1;
        }
        if (highestDamage) {
            if (eventInstance != null) {
                eventInstance.monsterKilled(attacker, getId());
            }
            highestDamageChar = attacker;
        }
        if (attacker.getHp() > 0) {
            int personalExp = exp;
            if (exp > 0) {
                if (stati.containsKey(MonsterStatus.TAUNT)) {
                    int alterExp = stati.get(MonsterStatus.TAUNT).getStati().get(MonsterStatus.TAUNT);
                    personalExp *= 1.0 + alterExp / 100.0;
                }
                Integer holySymbol = attacker.getBuffedValue(MapleBuffStat.HOLY_SYMBOL);
                if (holySymbol != null) {
                    if (numExpSharers == 1) {
                        personalExp *= 1.0 + holySymbol.doubleValue() / 500.0;
                    } else {
                        personalExp *= 1.0 + holySymbol.doubleValue() / 100.0;
                    }
                }
            }
            if (exp < 0) {
                personalExp = Integer.MAX_VALUE;
            }
            personalExp /= attacker.getDiseases().contains(MapleDisease.CURSE) ? 2 : 1;
            attacker.gainExp(personalExp, true, false, attacker.getId() == (highestDamageChar != null ? highestDamageChar.getId() : 0), true, leechProt, numExpSharers);
            attacker.mobKilled(getId());
        }
    }

    public MapleCharacter killBy(MapleCharacter killer, int channel) {
        long totalBaseExpL = getExp();
        if (killer.getMapId() >= 100000000 && (killer.getJob().getId() == 0 || killer.getLevel() > 9) || killer.getJob().getId() != 0 && killer.getJob().getId() != 1000 && killer.getJob().getId() != 2000) {
            totalBaseExpL *= ChannelServer.getInstance(channel).getExpRate();
        }
        int totalBaseExp = (int) Math.min(Integer.MAX_VALUE, totalBaseExpL);
        AttackerEntry highest = null;
        int highdamage = 0;
        for (AttackerEntry attackEntry : attackers) {
            if (attackEntry.getDamage() > highdamage) {
                highest = attackEntry;
                highdamage = attackEntry.getDamage();
            }
        }

        int baseExp;
        for (AttackerEntry attackEntry : attackers) {
            baseExp = (int) Math.ceil(totalBaseExp * (double) attackEntry.getDamage() / getMaxHp());
            attackEntry.killedMob(killer.getMap(), baseExp, attackEntry == highest);
        }
        if (getController() != null) { // this can/should only happen when a hidden gm attacks the monster
            getController().getClient().sendPacket(MaplePacketCreator.stopControllingMonster(getObjectId()));
            getController().stopControllingMonster(this);
        }

        int achievement = 0;

        switch (getId()) {
            case 100100:
                achievement = 18;
                break;
            case 9400121:
                achievement = 3;
                break;
            case 8500002:
                achievement = 10;
                break;
            case 8800002:
                if (map.getId() == 280030000) {
                    for (MapleCharacter ch : map.getCharacters()) {
                        ch.finishAchievement(16);
                    }
                }
                break;
            case 8510000:
            case 8520000:
                achievement = 19;
                break;
        }

        if (achievement != 0) {
            if (killer.getParty() != null) {
                for (MaplePartyCharacter mpc : killer.getParty().getMembers()) {
                    if (mpc.getMapId() == killer.getMapId() && mpc.getPlayer() != null) {
                        mpc.getPlayer().finishAchievement(achievement);
                    }
                }
            } else {
                killer.finishAchievement(achievement);
            }
        }

        if (isBoss()) {
            killer.finishAchievement(6);
        }

        spawnRevives(killer.getMap());

        if (eventInstance != null) {
            eventInstance.unregisterMonster(this);
        }
        for (MonsterListener listener : listeners.toArray(new MonsterListener[0])) {
            listener.monsterKilled(this, highestDamageChar);
        }
        return highestDamageChar;
    }

    public void spawnRevives(final MapleMap map) {
        boolean canSpawn = true;
        if (eventInstance != null) {
            if (eventInstance.getName().contains("BossQuest")) {
                canSpawn = false;
            }
        }
        if (!canSpawn) {
            return;
        }
        final List<Integer> toSpawn = stats.getRevives();

        if (toSpawn != null) {
            MapleMonster mob;
            for (int i = toSpawn.size() - 1; i >= 0; i--) { // Impost order for HT sponge
                mob = MapleLifeFactory.getMonster(toSpawn.get(i));
                if (eventInstance != null) {
                    eventInstance.registerMonster(mob);
                }
                mob.setPosition(getPosition());
                if (dropsDisabled()) {
                    mob.disableDrops();
                }
                map.spawnRevives(mob, getObjectId());
            }
        }
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public MapleCharacter getController() {
        return controller.get();
    }

    public void setController(MapleCharacter controller) {
        this.controller = new WeakReference<>(controller);
    }

    public void switchController(MapleCharacter newController, boolean immediateAggro) {
        if (newController.isHidden()) {
            return;
        }
        MapleCharacter controllers = getController();
        if (controllers == newController) {
            return;
        }
        if (controllers != null) {
            controllers.stopControllingMonster(this);
            controllers.getClient().sendPacket(MaplePacketCreator.stopControllingMonster(getObjectId()));
        }
        newController.controlMonster(this, immediateAggro);
        setController(newController);
        if (immediateAggro) {
            setControllerHasAggro(true);
        }
        setControllerKnowsAboutAggro(false);
    }

    public void addListener(MonsterListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MonsterListener listener) {
        listeners.remove(listener);
    }

    public boolean isControllerHasAggro() {
        if (fake) {
            return false;
        }
        return controllerHasAggro;
    }

    public void setControllerHasAggro(boolean controllerHasAggro) {
        if (fake) {
            return;
        }
        this.controllerHasAggro = controllerHasAggro;
    }

    public boolean isControllerKnowsAboutAggro() {
        if (fake) {
            return false;
        }
        return controllerKnowsAboutAggro;
    }

    public void setControllerKnowsAboutAggro(boolean controllerKnowsAboutAggro) {
        if (fake) {
            return;
        }
        this.controllerKnowsAboutAggro = controllerKnowsAboutAggro;
    }

    public MaplePacket makeBossHPBarPacket() {
        return MaplePacketCreator.showBossHP(getId(), hp, getMaxHp(), getTagColor(), getTagBgColor());
    }

    public boolean hasBossHPBar() {
        return isBoss() && getTagColor() > 0 || isHT();
    }

    private boolean isHT() {
        return getId() == 8810018;
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (!isAlive()) {
            return;
        }
        client.sendPacket(MaplePacketCreator.spawnMonster(this, false, fake ? -4 : 0, 0));
        if (!stati.isEmpty()) {
            for (MonsterStatusEffect mse : stati.values()) {
                client.sendPacket(MaplePacketCreator.applyMonsterStatus(getObjectId(), mse.getStati(), mse.getSkill().getId(), false, 0));
            }
        }
        if (hasBossHPBar()) {
            client.sendPacket(makeBossHPBarPacket());
        }
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.killMonster(getObjectId(), false));
    }

    @Override
    public String toString() {
        return getName() + "(" + getId() + ") at " + getPosition().x + "/" + getPosition().y + " with " + hp + "/" + getMaxHp() + "hp, " + mp + "/" + getMaxMp() + " mp (alive: " + isAlive() + " oid: " + getObjectId() + " hplocked: " + hpLock + ")";
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.MONSTER;
    }

    public EventInstanceManager getEventInstance() {
        return eventInstance;
    }

    public void setEventInstance(EventInstanceManager eventInstance) {
        this.eventInstance = eventInstance;
    }

    public boolean isMobile() {
        return stats.isMobile();
    }

    public ElementalEffectiveness getEffectiveness(Element e) {
        if (!stati.isEmpty() && stati.get(MonsterStatus.DOOM) != null) {
            return ElementalEffectiveness.NORMAL; // like blue snails
        }
        return stats.getEffectiveness(e);
    }

    public boolean applyStatus(MapleCharacter from, final MonsterStatusEffect status, boolean poison, long duration) {
        return applyStatus(from, status, poison, duration, false);
    }

    public boolean applyStatus(MapleCharacter from, final MonsterStatusEffect status, boolean poison, long duration, boolean venom) {
        switch (stats.getEffectiveness(status.getSkill().getElement())) {
            case IMMUNE:
            case STRONG:
                return false;
            case NORMAL:
            case WEAK:
                break;
            default:
                throw new RuntimeException("Unknown elemental effectiveness: " + stats.getEffectiveness(status.getSkill().getElement()));
        }

        // compos don't have an elemental (they have 2 - so we have to hack here...)
        ElementalEffectiveness effectiveness = null;
        switch (status.getSkill().getId()) {
            case 2111006 -> {
                effectiveness = stats.getEffectiveness(Element.POISON);
                if (effectiveness == ElementalEffectiveness.IMMUNE || effectiveness == ElementalEffectiveness.STRONG) {
                    return false;
                }
            }
            case 2211006 -> {
                effectiveness = stats.getEffectiveness(Element.ICE);
                if (effectiveness == ElementalEffectiveness.IMMUNE || effectiveness == ElementalEffectiveness.STRONG) {
                    return false;
                }
            }
            case 4120005, 4220005, 14110004 -> {
                effectiveness = stats.getEffectiveness(Element.POISON);
                if (effectiveness == ElementalEffectiveness.WEAK) {
                    return false;
                }
            }
        }

        if (poison && hp <= 1) {
            return false;
        }

        if (isBoss() && !status.getStati().containsKey(MonsterStatus.SPEED)) {
            return false;
        }

        for (MonsterStatus stat : status.getStati().keySet()) {
            MonsterStatusEffect oldEffect = stati.get(stat);
            if (oldEffect != null) {
                oldEffect.removeActiveStatus(stat);
                if (oldEffect.getStati().isEmpty()) {
                    if (oldEffect.getCancelTask() != null) {
                        oldEffect.getCancelTask().cancel(false);
                        oldEffect.setCancelTask(null);
                    }
                    oldEffect.cancelPoisonSchedule();
                }
            }
        }
        TimerManager timerManager = TimerManager.getInstance();
        final Runnable cancelTask = () -> {
            if (isAlive()) {
                MaplePacket packet = MaplePacketCreator.cancelMonsterStatus(getObjectId(), status.getStati());
                map.broadcastMessage(packet, getPosition());
                if (getController() != null && !getController().isMapObjectVisible(MapleMonster.this)) {
                    getController().getClient().sendPacket(packet);
                }
            }
            for (MonsterStatus stat : status.getStati().keySet()) {
                stati.remove(stat);
            }
            setVenomMulti(0);
            status.cancelPoisonSchedule();
        };
        if (!map.hasEvent()) {
            if (poison || status.getSkill().getId() == 2221003 || status.getSkill().getId() == 2121003) {
                int poisonLevel = from.getSkillLevel(status.getSkill());
                int poisonDamage = Math.min(Short.MAX_VALUE, (int) (getMaxHp() / (70.0 - poisonLevel) + 0.999));
                status.setValue(MonsterStatus.POISON, poisonDamage);
                status.setPoisonSchedule(timerManager.register(new PoisonTask(poisonDamage, from, status, cancelTask, false, false), 1000, 1000));
            } else if (venom) {
                if (from.getJob() == MapleJob.NIGHTLORD || from.getJob() == MapleJob.SHADOWER) {
                    int poisonLevel = 0;
                    int matk = 0;
                    if (from.getJob() == MapleJob.NIGHTLORD) {
                        poisonLevel = from.getSkillLevel(SkillFactory.getSkill(4120005));
                        if (poisonLevel <= 0) {
                            return false;
                        }
                        matk = SkillFactory.getSkill(4120005).getEffect(poisonLevel).getMatk();
                    } else if (from.getJob() == MapleJob.SHADOWER) {
                        poisonLevel = from.getSkillLevel(SkillFactory.getSkill(4220005));
                        if (poisonLevel <= 0) {
                            return false;
                        }
                        matk = SkillFactory.getSkill(4220005).getEffect(poisonLevel).getMatk();
                    } else if (from.getJob().isA(MapleJob.NIGHTWALKER3)) {
                        poisonLevel = from.getSkillLevel(SkillFactory.getSkill(14110004));
                        if (poisonLevel <= 0) {
                            return false;
                        }
                        matk = SkillFactory.getSkill(14110004).getEffect(poisonLevel).getMatk();
                    } else {
                        return false;
                    }
                    int luk = from.getLuk();
                    int maxDmg = (int) Math.ceil(Math.min(Short.MAX_VALUE, 0.2 * luk * matk));
                    int minDmg = (int) Math.ceil(Math.min(Short.MAX_VALUE, 0.1 * luk * matk));
                    int gap = maxDmg - minDmg;
                    if (gap == 0) {
                        gap = 1;
                    }
                    int poisonDamage = 0;
                    for (int i = 0; i < VenomMultiplier; i++) {
                        poisonDamage = poisonDamage + Randomizer.nextInt(gap) + minDmg;
                    }
                    poisonDamage = Math.min(Short.MAX_VALUE, poisonDamage);
                    status.setValue(MonsterStatus.POISON, poisonDamage);
                    status.setPoisonSchedule(timerManager.register(new PoisonTask(poisonDamage, from, status, cancelTask, false, false), 1000, 1000));
                } else {
                    return false;
                }
            } else if (status.getSkill().getId() == 4111003 || status.getSkill().getId() == 14111001) { // shadow web
                int webDamage = (int) (getMaxHp() / 50.0 + 0.999);
                // actually shadow web works different but similar...
                status.setPoisonSchedule(timerManager.schedule(new PoisonTask(webDamage, from, status, cancelTask, true, false), 3500));
            } else if (status.getSkill().getId() == 4121004 || status.getSkill().getId() == 4221004) {
                int ambushDamage = from.getNinjaAmbushDamage(status.getSkill().getEffect(from.getSkillLevel(status.getSkill())));
                status.setPoisonSchedule(timerManager.schedule(new PoisonTask(ambushDamage, from, status, cancelTask, false, true), 1000));
            }
            for (MonsterStatus stat : status.getStati().keySet()) {
                stati.put(stat, status);
            }
            MaplePacket packet = MaplePacketCreator.applyMonsterStatus(getObjectId(), status.getStati(), status.getSkill().getId(), false, 0);
            map.broadcastMessage(packet, getPosition());
            if (getController() != null && !getController().isMapObjectVisible(this)) {
                getController().getClient().sendPacket(packet);
            }
            ScheduledFuture<?> schedule = timerManager.schedule(cancelTask, duration + status.getSkill().getAnimationTime());
            status.setCancelTask(schedule);
        }
        return true;
    }

    public void applyMonsterBuff(final MonsterStatus status, final int x, final int skillId, int duration, final MobSkill skill) {
        TimerManager timerManager = TimerManager.getInstance();
        final Runnable cancelTask = () -> {
            if (isAlive()) {
                MaplePacket packet = MaplePacketCreator.cancelMonsterStatus(getObjectId(), Collections.singletonMap(status, x));
                map.broadcastMessage(packet, getPosition());
                if (getController() != null && !getController().isMapObjectVisible(MapleMonster.this)) {
                    getController().getClient().sendPacket(packet);
                }
                removeMonsterBuff(status);
            }
        };
        MaplePacket packet = MaplePacketCreator.applyMonsterStatus(getObjectId(), Collections.singletonMap(status, x), skillId, true, 0, skill);
        map.broadcastMessage(packet, getPosition());
        if (getController() != null && !getController().isMapObjectVisible(this)) {
            getController().getClient().sendPacket(packet);
        }
        timerManager.schedule(cancelTask, duration);
        addMonsterBuff(status, x);
    }

    public void dispelBuff(MonsterStatus status) {
        MaplePacket packet = MaplePacketCreator.cancelMonsterStatus(getObjectId(), Collections.singletonMap(status, monsterBuffs.get(status)));
        map.broadcastMessage(packet, getPosition());
        if (getController() != null && !getController().isMapObjectVisible(MapleMonster.this)) {
            getController().getClient().sendPacket(packet);
        }
        removeMonsterBuff(status);
    }

    public void dispelBuffs() {
        for (MonsterStatus status : monsterBuffs.keySet()) {
            dispelBuff(status);
        }
    }

    public void dispelPowerUps() {
        for (MonsterStatus status : monsterBuffs.keySet()) {
            if (isPowerUp(status)) {
                dispelBuff(status);
            }
        }
    }

    public void setTempEffectiveness(final Element e, final ElementalEffectiveness ee, long milli) {
        stats.setEffectiveness(e, ee);
        TimerManager.getInstance().schedule(() -> stats.removeEffectiveness(e), milli);
    }

    public void stolen() {
        stolen = true;
    }

    public boolean isStolen() {
        return stolen;
    }

    public void addMonsterBuff(MonsterStatus status, Integer x) {
        monsterBuffs.put(status, x);
    }

    public void removeMonsterBuff(MonsterStatus status) {
        monsterBuffs.remove(status);
    }

    public boolean isBuffed(MonsterStatus status) {
        return monsterBuffs.containsKey(status);
    }

    public void setFake(boolean fake) {
        this.fake = fake;
    }

    public boolean isFake() {
        return fake;
    }

    public MapleMap getMap() {
        return map;
    }

    public List<Pair<Integer, Integer>> getSkills() {
        return stats.getSkills();
    }

    public boolean hasSkill(int skillId, int level) {
        return stats.hasSkill(skillId, level);
    }

    public boolean canUseSkill(MobSkill toUse) {
        if (toUse == null) {
            return false;
        }
        List<Pair<Integer, Integer>> skills = new ArrayList<>(usedSkills);
        for (Pair<Integer, Integer> skill : skills) {
            if (skill.getLeft() == toUse.getSkillId() && skill.getRight() == toUse.getSkillLevel()) {
                return false;
            }
        }
        skills.clear();
        if (toUse.getLimit() > 0) {
            if (skillsUsed.containsKey(new Pair<>(toUse.getSkillId(), toUse.getSkillLevel()))) {
                int times = skillsUsed.get(new Pair<>(toUse.getSkillId(), toUse.getSkillLevel()));
                if (times >= toUse.getLimit()) {
                    return false;
                }
            }
        }
        if (toUse.getSkillId() == 200 && map.getSpawnedMonstersOnMap() > 100) {
            return false;
        }
        int percHpLeft = (int) Math.ceil(hp * 100.0 / getMaxHp());
        if (percHpLeft < 1) {
            percHpLeft = 1;
        }
        return !(toUse.getSkillId() == 128 && percHpLeft > 50 || toUse.getHP() < percHpLeft);
    }

    public void usedSkill(final int skillId, final int level, long cooltime) {
        usedSkills.add(new Pair<>(skillId, level));

        if (skillsUsed.containsKey(new Pair<>(skillId, level))) {
            int times = skillsUsed.get(new Pair<>(skillId, level)) + 1;
            skillsUsed.remove(new Pair<>(skillId, level));
            skillsUsed.put(new Pair<>(skillId, level), times);
        } else {
            skillsUsed.put(new Pair<>(skillId, level), 1);
        }

        final MapleMonster mons = this;
        TimerManager tMan = TimerManager.getInstance();
        tMan.schedule(() -> mons.clearSkill(skillId, level), cooltime);
    }

    public void clearSkill(int skillId, int level) {
        int index = -1;
        List<Pair<Integer, Integer>> usedSkills_ = new ArrayList<>(usedSkills);
        for (Pair<Integer, Integer> skill : usedSkills_) {
            if (skill.getLeft() == skillId && skill.getRight() == level) {
                index = usedSkills_.indexOf(skill);
                break;
            }
        }
        if (index != -1) {
            usedSkills.remove(index);
        }
    }

    public int getNoSkills() {
        return stats.getNoSkills();
    }

    public boolean containsStatus(MonsterStatus status) {
        return stati.containsKey(status);
    }

    public boolean isFirstAttack() {
        return stats.isFirstAttack();
    }

    public int getBuffToGive() {
        return stats.getBuffToGive();
    }

    public void setMoveLocked(boolean b) {
        moveLock = b;
    }

    public boolean isMoveLocked() {
        return moveLock;
    }

    public MapleMonsterBanInfo getBanInfo() {
        return stats.getBanInfo();
    }

    public boolean isDojoBoss() {
        return getId() > 9300183 && getId() < 9300216;
    }

    public boolean isDojoMinion() {
        return getId() > 9300216 && getId() < 9300270;
    }

    public int numDojoBossSpawns(int id) {
        if (!isDojoBoss()) {
            return 0;
        }
        switch (id - 9300000) {
            case 187:
            case 188:
            case 191:
            case 196:
            case 199:
            case 202:
            case 204:
            case 208:
            case 209:
            case 210:
            case 212:
            case 215:
                return 1;
            default:
                return 2;
        }
    }

    public int[] getDojoBossSpawns() {
        int id = getId();
        int x = 0;
        int[] end = new int[2];
        for (int i = 9300184; i <= id; i++) {
            x += numDojoBossSpawns(i);
        }
        if (numDojoBossSpawns(id) == 1) {
            end[0] = 9300216 + x;
            end[1] = 0;
        } else {
            end[0] = 9300215 + x;
            end[1] = 9300216 + x;
        }
        return end;
    }

    private boolean isPowerUp(MonsterStatus status) {
        return status == MonsterStatus.WEAPON_ATTACK_UP || status == MonsterStatus.WEAPON_DEFENSE_UP || status == MonsterStatus.MAGIC_ATTACK_UP || status == MonsterStatus.MAGIC_DEFENSE_UP || status == MonsterStatus.MAGIC_IMMUNITY || status == MonsterStatus.WEAPON_IMMUNITY;
    }

    public MapleCharacter getSummonedBy() {
        return summonedBy;
    }

    public void setSummonedBy(MapleCharacter chr) {
        summonedBy = chr;
    }

    public String getName() {
        return stats.getName();
    }

    public void giveDebuffToAttackers(MapleDisease disease, MobSkill skill) {
        for (AttackerEntry attacker : attackers) {
            attacker.giveDebuff(disease, skill);
        }
    }

    public void dispose() {
        highestDamageChar = null;
        attackers.clear();
        listeners.clear();
        stati.clear();
        usedSkills.clear();
        skillsUsed.clear();
        monsterBuffs.clear();
    }

    private interface AttackerEntry {

        List<AttackingMapleCharacter> getAttackers();

        void addDamage(MapleCharacter from, int damage, boolean updateAttackTime);

        int getDamage();

        boolean contains(MapleCharacter chr);

        void killedMob(MapleMap map, int baseExp, boolean mostDamage);

        void giveDebuff(MapleDisease disease, MobSkill skill);
    }

    private static class AttackingMapleCharacter {

        private final MapleCharacter attacker;
        private long lastAttackTime;

        public AttackingMapleCharacter(MapleCharacter attacker, long lastAttackTime) {
            super();
            this.attacker = attacker;
            this.lastAttackTime = lastAttackTime;
        }

        public long getLastAttackTime() {
            return lastAttackTime;
        }

        public void setLastAttackTime(long lastAttackTime) {
            this.lastAttackTime = lastAttackTime;
        }

        public MapleCharacter getAttacker() {
            return attacker;
        }
    }

    private static class OnePartyAttacker {

        public MapleParty lastKnownParty;
        public int damage;
        public long lastAttackTime;

        public OnePartyAttacker(MapleParty lastKnownParty, int damage) {
            super();
            this.lastKnownParty = lastKnownParty;
            this.damage = damage;
            lastAttackTime = System.currentTimeMillis();
        }
    }

    private final class PoisonTask implements Runnable {

        private final int poisonDamage;
        private final MapleCharacter chr;
        private final MonsterStatusEffect status;
        private final Runnable cancelTask;
        private final boolean shadowWeb;
        private final MapleMap map;
        private final boolean ninjaAmbush;
        private boolean done = false;
        private boolean downCount = false;

        private PoisonTask(int poisonDamage, MapleCharacter chr, MonsterStatusEffect status, Runnable cancelTask, boolean shadowWeb, boolean ninjaAmbush) {
            this.poisonDamage = poisonDamage;
            this.chr = chr;
            this.status = status;
            this.cancelTask = cancelTask;
            this.shadowWeb = shadowWeb;
            map = chr.getMap();
            this.ninjaAmbush = ninjaAmbush;
        }

        @Override
        public void run() {
            int damage = poisonDamage;
            if (damage >= hp) {
                damage = hp - 1;
                if (!shadowWeb && !ninjaAmbush) {
                    cancelTask.run();
                    if (status != null && status.getCancelTask() != null) {
                        status.getCancelTask().cancel(false);
                        status.setCancelTask(null);
                    }
                } else if (ninjaAmbush && !downCount) {
                    downCount = true;
                    TimerManager.getInstance().schedule(() -> done = true, status.getSkill().getEffect(chr.getSkillLevel(status.getSkill())).getDuration());
                }
            }
            if (hp > 1 && damage > 0) {
                damage(chr, damage, ninjaAmbush);
                if (shadowWeb) {
                    map.broadcastMessage(MaplePacketCreator.damageMonster(getObjectId(), damage), getPosition());
                }
                if (ninjaAmbush && !done) {
                    int ambushDamage = chr.getNinjaAmbushDamage(status.getSkill().getEffect(chr.getSkillLevel(status.getSkill())));
                    status.setPoisonSchedule(TimerManager.getInstance().schedule(new PoisonTask(ambushDamage, chr, status, cancelTask, false, true), 1000));
                }
            }
        }
    }

    private class SingleAttackerEntry implements AttackerEntry {

        private final int chrid;
        private final ChannelServer cserv;
        private int damage;
        private long lastAttackTime;

        public SingleAttackerEntry(MapleCharacter from, ChannelServer cserv) {
            chrid = from.getId();
            this.cserv = cserv;
        }

        @Override
        public void addDamage(MapleCharacter from, int damage, boolean updateAttackTime) {
            if (chrid == from.getId()) {
                this.damage += damage;
            } else {
                throw new IllegalArgumentException("Not the attacker of this entry");
            }
            if (updateAttackTime) {
                lastAttackTime = System.currentTimeMillis();
            }
        }

        @Override
        public List<AttackingMapleCharacter> getAttackers() {
            MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(chrid);
            if (chr != null) {
                return Collections.singletonList(new AttackingMapleCharacter(chr, lastAttackTime));
            } else {
                return Collections.emptyList();
            }
        }

        @Override
        public boolean contains(MapleCharacter chr) {
            return chrid == chr.getId();
        }

        @Override
        public int getDamage() {
            return damage;
        }

        @Override
        public void killedMob(MapleMap map, int baseExp, boolean highestDamage) {
            MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(chrid);
            if (chr != null && chr.getMap() == map) {
                giveExpToCharacter(chr, baseExp, highestDamage, 1, 0);
                if (chr.getBeacon() == getObjectId()) {
                    chr.offBeacon(true);
                }
                if (highestDamage) {
                    chr.resetMobKillTimer();
                }
            }
        }

        @Override
        public void giveDebuff(MapleDisease disease, MobSkill skill) {
            MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(chrid);
            if (chr != null && chr.getMap() == map) {
                chr.giveDebuff(disease, skill);
            }
        }

        @Override
        public int hashCode() {
            return chrid;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final SingleAttackerEntry other = (SingleAttackerEntry) obj;
            return chrid == other.chrid;
        }
    }

    private class PartyAttackerEntry implements AttackerEntry {

        private final Map<Integer, OnePartyAttacker> attackers;
        private final int partyid;
        private final ChannelServer cserv;
        private int totDamage;

        public PartyAttackerEntry(int partyid, ChannelServer cserv) {
            this.partyid = partyid;
            this.cserv = cserv;
            attackers = new HashMap<>(6);
        }

        public List<AttackingMapleCharacter> getAttackers() {
            List<AttackingMapleCharacter> ret = new ArrayList<>(attackers.size());
            for (Entry<Integer, OnePartyAttacker> entry : attackers.entrySet()) {
                MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(entry.getKey());
                if (chr != null) {
                    ret.add(new AttackingMapleCharacter(chr, entry.getValue().lastAttackTime));
                }
            }
            return ret;
        }

        private Map<MapleCharacter, OnePartyAttacker> resolveAttackers() {
            Map<MapleCharacter, OnePartyAttacker> ret = new HashMap<>(attackers.size());
            for (Entry<Integer, OnePartyAttacker> aentry : attackers.entrySet()) {
                MapleCharacter chr = cserv.getPlayerStorage().getCharacterById(aentry.getKey());
                if (chr != null) {
                    ret.put(chr, aentry.getValue());
                }
            }
            return ret;
        }

        @Override
        public boolean contains(MapleCharacter chr) {
            return attackers.containsKey(chr.getId());
        }

        @Override
        public int getDamage() {
            return totDamage;
        }

        public void addDamage(MapleCharacter from, int damage, boolean updateAttackTime) {
            OnePartyAttacker oldPartyAttacker = attackers.get(from.getId());
            if (oldPartyAttacker != null) {
                oldPartyAttacker.damage += damage;
                oldPartyAttacker.lastKnownParty = from.getParty();
                if (updateAttackTime) {
                    oldPartyAttacker.lastAttackTime = System.currentTimeMillis();
                }
            } else {
                // TODO actually this causes wrong behaviour when the party changes between attacks
                // only the last setup will get exp - but otherwise we'd have to store the full party
                // constellation for every attack/everytime it changes, might be wanted/needed in the
                // future but not now
                OnePartyAttacker onePartyAttacker = new OnePartyAttacker(from.getParty(), damage);
                attackers.put(from.getId(), onePartyAttacker);
                if (!updateAttackTime) {
                    onePartyAttacker.lastAttackTime = 0;
                }
            }
            totDamage += damage;
        }

        @Override
        public void killedMob(MapleMap map, int baseExp, boolean mostDamage) {
            Map<MapleCharacter, OnePartyAttacker> attackers_ = resolveAttackers();

            MapleCharacter highest = null;
            int highestDamage = 0;

            Map<MapleCharacter, Integer> expMap = new ArrayMap<>(6);
            for (Entry<MapleCharacter, OnePartyAttacker> attacker : attackers_.entrySet()) {
                MapleCharacter cbkn = attacker.getKey();
                if (cbkn.getBeacon() == getObjectId() && cbkn.getMap() == map) {
                    cbkn.offBeacon(true);
                }
                MapleParty party = attacker.getValue().lastKnownParty;
                double averagePartyLevel = 0;

                List<MapleCharacter> expApplicable = new ArrayList<>();
                for (MaplePartyCharacter partychar : party.getMembers()) {
                    if (attacker.getKey().getLevel() - partychar.getLevel() <= 5 || getLevel() - partychar.getLevel() <= 5) {
                        MapleCharacter pchr = cserv.getPlayerStorage().getCharacterByName(partychar.getName());
                        if (pchr != null) {
                            if (pchr.isAlive() && pchr.getMap() == map && !pchr.inCS() && !pchr.inMTS()) {
                                expApplicable.add(pchr);
                                averagePartyLevel += pchr.getLevel();
                            }
                        }
                    }
                }
                double expBonus = 1.0;
                if (expApplicable.size() > 1) {
                    expBonus = 1.10 + 0.05 * expApplicable.size();
                    averagePartyLevel /= expApplicable.size();
                }

                int iDamage = attacker.getValue().damage;
                if (iDamage > highestDamage) {
                    highest = attacker.getKey();
                    highestDamage = iDamage;
                }
                double innerBaseExp = baseExp * (double) iDamage / totDamage;
                double expFraction = innerBaseExp * expBonus / (expApplicable.size() + 1);

                for (MapleCharacter expReceiver : expApplicable) {
                    Integer oexp = expMap.get(expReceiver);
                    int iexp;
                    if (oexp == null) {
                        iexp = 0;
                    } else {
                        iexp = oexp;
                    }
                    double expWeight = expReceiver == attacker.getKey() ? 2.0 : 1.0;
                    double levelMod = expReceiver.getLevel() / averagePartyLevel;
                    if (levelMod > 1.0 || attackers.containsKey(expReceiver.getId())) {
                        levelMod = 1.0;
                    }
                    iexp += (int) Math.round(expFraction * expWeight * levelMod);
                    expMap.put(expReceiver, iexp);
                }
            }
            if (highest != null) {
                highest.resetMobKillTimer();
            }
            for (Entry<MapleCharacter, Integer> expReceiver : expMap.entrySet()) {
                boolean white = mostDamage && expReceiver.getKey() == highest;
                int nerfXp = expReceiver.getValue();
                boolean nerfedexp = expReceiver.getKey().getMobKillTimer() >= 25000;
                nerfedexp = nerfedexp && !isBoss();
                if (nerfedexp) {
                    nerfXp /= 10;
                }
                giveExpToCharacter(expReceiver.getKey(), nerfXp, white, expMap.size(), nerfedexp ? nerfXp * 9 : 0);
            }
        }

        @Override
        public void giveDebuff(MapleDisease disease, MobSkill skill) {
            int i = 0;
            for (AttackingMapleCharacter attacker : getAttackers()) {
                if (i < skill.getCount()) {
                    attacker.getAttacker().giveDebuff(disease, skill);
                    i++;
                }
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + partyid;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final PartyAttackerEntry other = (PartyAttackerEntry) obj;
            return partyid == other.partyid;
        }
    }
}