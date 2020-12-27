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

package guida.scripting;

import guida.client.Equip;
import guida.client.IItem;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.MapleJob;
import guida.client.MaplePet;
import guida.client.MapleQuestStatus;
import guida.net.MaplePacket;
import guida.net.world.MapleParty;
import guida.net.world.guild.MapleGuild;
import guida.scripting.event.EventManager;
import guida.scripting.npc.NPCScriptManager;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.MaplePortal;
import guida.server.TimerManager;
import guida.server.maps.MapMonitor;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleReactor;
import guida.server.quest.MapleQuest;
import guida.tools.MaplePacketCreator;
import guida.tools.StringUtil;

import java.awt.Point;
import java.rmi.RemoteException;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AbstractPlayerInteraction {

    private final MapleClient c;

    public AbstractPlayerInteraction(MapleClient c) {
        this.c = c;
    }

    protected MapleClient getClient() {
        return c;
    }

    public MapleCharacter getPlayer() {
        return c.getPlayer();
    }

    public EventManager getEventManager(String event) {
        return c.getChannelServer().getEventSM().getEventManager(event);
    }

    public void warp(int mapId) {
        warp(mapId, -1);
    }

    public void warp(int map, String portalName) {
        warp(map, getWarpMap(map).getPortal(portalName).getId());
    }

    public void warp(int mapId, int portal) {
        MapleMap target = getWarpMap(mapId);
        if (target.canEnter() && getPlayer().getMap().canExit() || getPlayer().isGM()) {
            getPlayer().changeMap(target, portal != -1 && target.getPortal(portal) != null ? target.getPortal(portal) : target.getRandomSpawnPoint());
        } else {
            c.sendPacket(MaplePacketCreator.serverNotice(5, "Either the map you are being warped to cannot be entered or the map you are in cannot be exited. As such, you will not be warped."));
        }
    }

    public void warp(MapleCharacter mc, int mapId, String ptl) {
        MapleMap target = getWarpMap(mapId);
        if (target.canEnter() && mc.getMap().canExit() || mc.isGM()) {
            mc.changeMap(target, target.getPortal(ptl));
        } else {
            mc.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "Either the map you are being warped to cannot be entered or the map you are in cannot be exited. As such, you will not be warped."));
        }
    }

    private MapleMap getWarpMap(int map) {
        MapleMap target;
        if (getPlayer().getEventInstance() == null) {
            target = c.getChannelServer().getMapFactory().getMap(map);
        } else {
            target = getPlayer().getEventInstance().getMapInstance(map);
        }
        return target;
    }

    public MapleMap getMap(int map) {
        return getWarpMap(map);
    }

    public boolean haveItem(int itemid) {
        return haveItem(itemid, 1);
    }

    public boolean haveItem(int itemid, int quantity) {
        return haveItem(itemid, quantity, false, false);
    }

    public boolean haveItem(int itemid, int quantity, boolean checkEquipped, boolean exact) {
        return getPlayer().haveItem(itemid, quantity, checkEquipped, exact);
    }

    public boolean canHold() {
        return canHold(0, true);
    }

    public boolean canHold(int itemid) { // Mainly for gach
        return canHold(itemid, true);
    }

    public boolean canHold(int itemid, boolean fullInvent) {
        if (!fullInvent) {
            return getPlayer().getInventory(MapleItemInformationProvider.getInstance().getInventoryType(itemid)).getNextFreeSlot() != -1;
        } else {
            for (int i = 1; i <= 5; i++) {
                if (getPlayer().getInventory(MapleInventoryType.getByType((byte) i)).getNextFreeSlot() == -1) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean canHold(int[] items) {
        return MapleInventoryManipulator.canHold(c, items);
    }

    public void showNPCAnimation(int npcId, String info) {
        getPlayer().getMap().broadcastMessage(MaplePacketCreator.npcAnimation(getPlayer().getMap().getNPCbyID(npcId), info));
    }

    public void completeCustomQuest(int id) {
        MapleQuest.getInstance(id).completeCustomQuest(getPlayer(), 0);
    }

    public MapleQuestStatus.Status getQuestStatus(int id) {
        return getPlayer().getQuest(id).getStatus();
    }

    public int getQuestStatusId(int id) {
        return getPlayer().getQuest(id).getStatus().getId();
    }

    public MapleJob getJob() {
        return getPlayer().getJob();
    }

    public void gainItem(int id, short quantity) {
        gainItem(id, quantity, false, getPlayer());
    }

    public void gainItem(int id, short quantity, boolean r) {
        gainItem(id, quantity, r, getPlayer());
    }

    public int getQuestInfoInt(int id) {
        return Integer.parseInt(getPlayer().getQuest(id).getQuestRecord());
    }

    public String getQuestInfo(int id, int startIndex) {
        return getQuestInfo(id, startIndex, false);
    }

    public String getQuestInfo(int id, int startIndex, boolean questEx) {
        if (questEx) {
            return getPlayer().getQuestEx(id).getQuestRecord().substring(startIndex, startIndex + 1);
        }
        return getPlayer().getQuest(id).getQuestRecord().substring(startIndex, startIndex + 1);
    }

    public String getQuestInfo(int id) {
        return getPlayer().getQuest(id).getQuestRecord();
    }

    public void setQuestInfo(int id, String info) {
        MapleQuest.getInstance(id).setQuestInfo(getPlayer(), info, true, false);
    }

    public void setQuestInfo(int id, int index, String info) {
        setQuestInfo(id, index, info, false);
    }

    public void setQuestInfo(int id, int index, String info, boolean questEx) {
        MapleQuest quest = MapleQuest.getInstance(id);
        char[] originalInfo = questEx ? getPlayer().getQuestEx(id).getQuestRecord().toCharArray() : getPlayer().getQuest(id).getQuestRecord().toCharArray();
        originalInfo[index] = info.charAt(0);
        StringBuilder newInfo = new StringBuilder();
        for (char element : originalInfo) {
            newInfo.append(element);
        }
        if (questEx) {
            quest.setQuestRecordExInfo(getPlayer(), newInfo.toString());
        } else {
            quest.setQuestInfo(getPlayer(), newInfo.toString(), true, false);
        }
    }

    public String getQuestExInfo(int id) {
        return getPlayer().getQuestEx(id).getQuestRecord();
    }

    public void setQuestExInfo(int id, String info) {
        MapleQuest.getInstance(id).setQuestRecordExInfo(getPlayer(), info);
    }

    public void startNPC(int id) {
        NPCScriptManager.getInstance().start(c, id);
    }

    public void setTimeOut(long time, final int mapId) {
        TimerManager.getInstance().schedule(() -> {
            MapleMap map = getPlayer().getMap();
            MapleMap outMap = c.getChannelServer().getMapFactory().getMap(mapId);
            for (MapleCharacter player : map.getCharacters()) {
                player.getClient().getPlayer().changeMap(outMap, outMap.getRandomSpawnPoint());
            }
        }, time);
    }

    /**
     * Gives item with the specified id or takes it if the quantity is negative. Note that this does NOT take items from the equipped inventory. randomStats for generating random stats on the generated equip.
     *
     * @param id
     * @param quantity
     * @param randomStats
     */
    public void gainItem(int id, short quantity, boolean randomStats, MapleCharacter player) {
        if (quantity >= 0) {
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            IItem item = ii.getEquipById(id);
            MapleInventoryType type = ii.getInventoryType(id);
            StringBuilder logInfo = new StringBuilder(player.getName());
            logInfo.append(" received ");
            logInfo.append(quantity);
            logInfo.append(" from a scripted PlayerInteraction (");
            logInfo.append(toString());
            logInfo.append(")");
            if (!MapleInventoryManipulator.checkSpace(player.getClient(), id, quantity, "")) {
                c.sendPacket(MaplePacketCreator.serverNotice(1, "Your inventory is full. Please remove an item from your " + type.name() + " inventory."));
                return;
            }
            if (type.equals(MapleInventoryType.EQUIP) && !ii.isThrowingStar(item.getItemId()) && !ii.isShootingBullet(item.getItemId())) {
                if (randomStats) {
                    c.sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(player.getClient(), ii.randomizeStats((Equip) item), logInfo.toString(), false)));
                } else {
                    c.sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(player.getClient(), item, logInfo.toString(), false)));
                }
            } else {
                MapleInventoryManipulator.addById(player.getClient(), id, quantity, logInfo.toString(), null, null);
            }
        } else {
            MapleInventoryManipulator.removeById(player.getClient(), MapleItemInformationProvider.getInstance().getInventoryType(id), id, -quantity, true, false);
        }
        player.getClient().sendPacket(MaplePacketCreator.getShowItemGain(id, quantity, true));
    }

    public void silentRemoveEquipped(short slot) {
        c.sendPacket(MaplePacketCreator.modifyInventory(false, Collections.singletonList(MapleInventoryManipulator.removeItemFromSlot(c, MapleInventoryType.EQUIPPED, slot, (short) 1, false))));
    }

    public void changeMusic(String songName) {
        getPlayer().getMap().broadcastMessage(MaplePacketCreator.musicChange(songName));
    }

    public void environmentChange(byte effect, String path) {
        c.sendPacket(MaplePacketCreator.environmentChange(path, effect));
    }

    public void playerMessage(String message) {
        c.sendPacket(MaplePacketCreator.playerMessage(message));
    }

    // default mapMessage to use type 5
    public void mapMessage(String message) {
        mapMessage(5, message);
    }

    public void guildMessage(String message) {
        guildMessage(5, message);
    }

    public void topMessage(String message) {
        c.sendPacket(MaplePacketCreator.topMessage(message));
    }

    public void playerMessage(int type, String message) {
        c.sendPacket(MaplePacketCreator.serverNotice(type, message));
    }

    public void mapMessage(int type, String message) {
        getPlayer().getMap().broadcastMessage(MaplePacketCreator.serverNotice(type, message));
    }

    public void guildMessage(int type, String message) {
        MapleGuild guild = getGuild();
        if (guild != null) {
            guild.guildMessage(MaplePacketCreator.serverNotice(type, message));
        }
    }

    public MapleGuild getGuild() {
        try {
            return c.getChannelServer().getWorldInterface().getGuild(getPlayer().getGuildId());
        } catch (RemoteException ex) {
            Logger.getLogger(AbstractPlayerInteraction.class.getName()).log(Level.SEVERE, null, ex);
            c.getChannelServer().reconnectWorld();
        }
        return null;
    }

    public void gainGP(int amount) {
        try {
            c.getChannelServer().getWorldInterface().gainGP(getPlayer().getGuildId(), amount);
        } catch (RemoteException e) {
            c.getChannelServer().reconnectWorld();
        }
    }

    public MapleParty getParty() {
        return getPlayer().getParty();
    }

    public boolean isPartyLeader() {
        return getParty() != null && getParty().getLeader().getId() == getPlayer().getId();
    }

    /**
     * PQ methods: give items/exp to all party members
     */
    public void givePartyItems(int id, short quantity, List<MapleCharacter> party) {
        for (MapleCharacter chr : party) {
            MapleClient cl = chr.getClient();
            if (quantity >= 0) {
                MapleInventoryManipulator.addById(cl, id, quantity, cl.getPlayer().getName() + " received " + quantity + " from event " + chr.getEventInstance().getName(), null, null);
            } else {
                MapleInventoryManipulator.removeById(cl, MapleItemInformationProvider.getInstance().getInventoryType(id), id, -quantity, true, false);
            }
            cl.sendPacket(MaplePacketCreator.getShowItemGain(id, quantity, true));
        }
    }

    /**
     * PQ gain EXP: Multiplied by channel rate here to allow global values to be input direct into NPCs
     */
    public void givePartyExp(int amount, List<MapleCharacter> party) {
        for (MapleCharacter chr : party) {
            chr.gainExp(amount * c.getChannelServer().getExpRate(), true, true);
        }
    }

    /**
     * remove all items of type from party; combination of haveItem and gainItem
     */
    public void removeFromParty(int id, List<MapleCharacter> party) {
        for (MapleCharacter chr : party) {
            MapleClient cl = chr.getClient();
            MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(id);
            MapleInventory iv = cl.getPlayer().getInventory(type);
            int possessed = iv.countById(id);

            if (possessed > 0) {
                MapleInventoryManipulator.removeById(cl, MapleItemInformationProvider.getInstance().getInventoryType(id), id, possessed, true, false);
                cl.sendPacket(MaplePacketCreator.getShowItemGain(id, (short) -possessed, true));
            }
        }
    }

    public void removeAll(int id) {
        removeAll(id, c);
    }

    /**
     * remove all items of type from character; combination of haveItem and gainItem
     */
    public void removeAll(int id, MapleClient cl) {
        MapleInventoryType type = MapleItemInformationProvider.getInstance().getInventoryType(id);
        MapleInventory iv = cl.getPlayer().getInventory(type);
        int possessed = iv.countById(id);

        if (possessed > 0) {
            MapleInventoryManipulator.removeById(cl, MapleItemInformationProvider.getInstance().getInventoryType(id), id, possessed, true, false);
            cl.sendPacket(MaplePacketCreator.getShowItemGain(id, (short) -possessed, true));
        }
    }

    public void gainCloseness(int closeness, int index) {
        MaplePet pet = getPlayer().getPet(index);
        if (pet != null) {
            pet.setCloseness(pet.getCloseness() + closeness);
            c.getPlayer().updatePet(pet);
        }
    }

    public void gainClosenessAll(int closeness) {
        for (MaplePet pet : getPlayer().getPets()) {
            pet.setCloseness(pet.getCloseness() + closeness);
            c.getPlayer().updatePet(pet);
        }
    }

    public int getMapId() {
        return getPlayer().getMap().getId();
    }

    public int getPlayerCount(int mapid) {
        return c.getChannelServer().getMapFactory().getMap(mapid).countCharsOnMap();
    }

    public int getCurrentPartyId(int mapid) {
        for (MapleCharacter chr : getMap(mapid).getCharacters()) {
            if (chr.getPartyId() != -1) {
                return chr.getPartyId();
            }
        }
        return -1;
    }

    public void sendPlayerHint(String hint, int width, int height) {
        c.sendPacket(MaplePacketCreator.sendPlayerHint(hint, width, height));
    }

    public void worldMessage(int type, String message) {
        MaplePacket packet = MaplePacketCreator.serverNotice(type, message);
        MapleCharacter chr = getPlayer();
        try {
            chr.getClient().getChannelServer().getWorldInterface().broadcastMessage(chr.getName(), packet.getBytes());
        } catch (RemoteException e) {
            chr.getClient().getChannelServer().reconnectWorld();
        }
    }

    public void createMapMonitor(int mapId, boolean closePortal, int portalMap, String portalName, int reactorMap, int reactor) {
        MaplePortal portal = null;
        if (closePortal) {
            portal = c.getChannelServer().getMapFactory().getMap(portalMap).getPortal(portalName);
            portal.setPortalStatus(MaplePortal.CLOSED);
        }
        MapleReactor r = null;
        if (reactor > -1) {
            r = c.getChannelServer().getMapFactory().getMap(reactorMap).getReactorById(reactor);
            r.setState((byte) 1);
            c.getChannelServer().getMapFactory().getMap(reactorMap).broadcastMessage(MaplePacketCreator.triggerReactor(r, 1));
        }
        new MapMonitor(c.getChannelServer().getMapFactory().getMap(mapId), closePortal ? portal : null, c.getChannel(), r);
    }

    public void createMapMonitor(int mapId, boolean closePortal, int portalMap, String portalName, int reactorMap, int reactor, long initialDelay) {
        MaplePortal portal = null;
        if (closePortal) {
            portal = c.getChannelServer().getMapFactory().getMap(portalMap).getPortal(portalName);
            portal.setPortalStatus(MaplePortal.CLOSED);
        }
        MapleReactor r = null;
        if (reactor > -1) {
            r = c.getChannelServer().getMapFactory().getMap(reactorMap).getReactorById(reactor);
            r.setState((byte) 1);
            c.getChannelServer().getMapFactory().getMap(reactorMap).broadcastMessage(MaplePacketCreator.triggerReactor(r, 1));
        }
        new MapMonitor(c.getChannelServer().getMapFactory().getMap(mapId), closePortal ? portal : null, c.getChannel(), r, initialDelay);
    }

    public int getBossLog(String bossid) {
        return getPlayer().getBossLog(bossid);
    }

    public void setBossLog(String bossid) {
        getPlayer().setBossLog(bossid);
    }

    public void showAnimationEffect(byte effect, String path) {
        if (effect == 18 && path.startsWith("Effect")) {
            c.getPlayer().setAutoChangeMapId(MapleItemInformationProvider.getInstance().getAutoChangeMapId(path));
        }
        c.sendPacket(MaplePacketCreator.showAnimationEffect(effect, path));
    }

    public void showAnimationEffect(byte effect, String path, int num) {
        c.sendPacket(MaplePacketCreator.showAnimationEffect(effect, path, num));
    }

    public void playPortalSE() {
        c.sendPacket(MaplePacketCreator.showAnimationEffect((byte) 7));
    }

    public void hideUI(boolean enable) {
        getPlayer().setUILocked(enable);
        c.sendPacket(MaplePacketCreator.hideUI(enable));
    }

    public void lockWindows(boolean enable) {
        c.sendPacket(MaplePacketCreator.lockWindows(enable));
    }

    public void giveBuff(int itemId, boolean desc) {
        MapleItemInformationProvider.getInstance().getItemEffect(itemId).applyTo(getPlayer());
        if (desc) {
            c.sendPacket(MaplePacketCreator.buffInfo(itemId));
        }
    }

    public String currentTime() {
        Calendar cal = Calendar.getInstance();
        return StringUtil.getLeftPaddedStr(String.valueOf(cal.get(Calendar.YEAR) - 2000), '0', 2) + "/" + StringUtil.getLeftPaddedStr(String.valueOf(cal.get(Calendar.MONTH)), '0', 2) + "/" + StringUtil.getLeftPaddedStr(String.valueOf(cal.get(Calendar.DATE)), '0', 2) + "/" + StringUtil.getLeftPaddedStr(String.valueOf(cal.get(Calendar.HOUR_OF_DAY)), '0', 2) + "/" + StringUtil.getLeftPaddedStr(String.valueOf(cal.get(Calendar.MINUTE)), '0', 2);
    }

    public int compareTime(String time1, String time2) {
        Calendar cal1 = Calendar.getInstance();
        Calendar cal2 = Calendar.getInstance();
        String[] time1Array = time1.split("/");
        String[] time2Array = time2.split("/");
        cal1.set(Integer.parseInt(2000 + time1Array[0]), Integer.parseInt(time1Array[1]), Integer.parseInt(time1Array[2]), Integer.parseInt(time1Array[3]), Integer.parseInt(time1Array[4]));
        cal2.set(Integer.parseInt(2000 + time2Array[0]), Integer.parseInt(time2Array[1]), Integer.parseInt(time2Array[2]), Integer.parseInt(time2Array[3]), Integer.parseInt(time2Array[4]));
        return (int) ((cal1.getTimeInMillis() - cal2.getTimeInMillis()) / 60000);
    }

    public void exchange(int meso, int[] items, boolean randomizeEquipStats) {
        MapleInventoryManipulator.exchange(c, meso, items, randomizeEquipStats);
    }

    public String getPartyVar(String name) {
        MapleParty party = getPlayer().getParty();
        return party != null ? party.getVar(name) : "";
    }

    public void addPartyVar(String name, String val) {
        MapleParty party = getPlayer().getParty();
        if (party != null) {
            party.addVar(name, val);
        }
    }

    public void removeNPC(int mapid, int npcid) {
        c.getChannelServer().getMapFactory().getMap(mapid).removeNPC(npcid);
    }

    public void removeNPC(int npcid) {
        c.getPlayer().getMap().removeNPC(npcid);
    }

    public void addNPC(int npcid, int x, int y, int mapid) {
        c.getChannelServer().getMapFactory().getMap(mapid).addNPC(npcid, new Point(x, y));
    }

    public void addNPC(int npcid, int x, int y) {
        c.getPlayer().getMap().addNPC(npcid, new Point(x, y));
    }
}