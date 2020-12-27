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

package guida.scripting.reactor;

import guida.client.Equip;
import guida.client.IItem;
import guida.client.Item;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.scripting.AbstractPlayerInteraction;
import guida.server.MapleItemInformationProvider;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleMonster;
import guida.server.life.MapleMonsterInformationProvider.DropEntry;
import guida.server.life.MapleNPC;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleReactor;
import guida.tools.MaplePacketCreator;
import guida.tools.Randomizer;

import java.awt.Point;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Lerk
 */
public class ReactorActionManager extends AbstractPlayerInteraction {

    private final MapleReactor reactor;
    private final MapleClient c;

    public ReactorActionManager(MapleClient c, MapleReactor reactor) {
        super(c);
        this.c = c;
        this.reactor = reactor;
    }

    // only used for meso = false, really. No minItems because meso is used to fill the gap
    public void dropItems() {
        dropItems(false, 0, 0, 0, 0);
    }

    public void dropItems(boolean meso, int mesoChance, int minMeso, int maxMeso) {
        dropItems(meso, mesoChance, minMeso, maxMeso, 0);
    }

    public void dropItems(boolean meso, int mesoChance, int minMeso, int maxMeso, int minItems) {
        List<DropEntry> chances = getDropChances();
        List<DropEntry> items = new LinkedList<>();
        int numItems = 0;

        if (meso && Math.random() < 1 / (double) mesoChance) {
            items.add(new DropEntry(0, mesoChance, 0));
        }

        // narrow list down by chances
        for (DropEntry d : chances) {
            if (c.getPlayer().canSeeItem(d.itemid) && Math.random() < 1 / (double) d.chance) {
                numItems++;
                items.add(d);
            }
        }

        // if a minimum number of drops is required, add meso
        while (items.size() < minItems) {
            items.add(new DropEntry(0, mesoChance));
            numItems++;
        }

        // randomize drop order
        java.util.Collections.shuffle(items);

        final Point dropPos = reactor.getPosition();

        dropPos.x -= 12 * numItems;

        for (DropEntry d : items) {
            if (d.itemid == 0) {
                int range = maxMeso - minMeso;
                int mesoDrop = (Randomizer.nextInt(range) + minMeso) * getClient().getChannelServer().getMesoRate();
                reactor.getMap().spawnMesoDrop(mesoDrop, dropPos, reactor, getPlayer(), meso, (byte) 0);
            } else {
                IItem drop;
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                if (ii.getInventoryType(d.itemid) != MapleInventoryType.EQUIP) {
                    drop = new Item(d.itemid, (byte) 0, (short) 1);
                } else {
                    drop = ii.randomizeStats((Equip) ii.getEquipById(d.itemid));
                }
                reactor.getMap().spawnItemDrop(reactor, getPlayer(), drop, dropPos, false, true);
            }
            dropPos.x += 25;

        }
    }

    private List<DropEntry> getDropChances() {
        return ReactorScriptManager.getInstance().getDrops(reactor.getId());
    }

    // summon one monster on reactor location
    public void spawnMonster(int id) {
        spawnMonster(id, 1, getPosition());
    }

    // summon one monster, remote location
    public void spawnMonster(int id, int x, int y) {
        spawnMonster(id, 1, new Point(x, y));
    }

    // multiple monsters, reactor location
    public void spawnMonster(int id, int qty) {
        spawnMonster(id, qty, getPosition());
    }

    // multiple monsters, remote location
    public void spawnMonster(int id, int qty, int x, int y) {
        spawnMonster(id, qty, new Point(x, y));
    }

    // handler for all spawnMonster
    private void spawnMonster(int id, int qty, Point pos) {
        for (int i = 0; i < qty; i++) {
            MapleMonster mob = MapleLifeFactory.getMonster(id);
            reactor.getMap().spawnMonsterOnGroundBelow(mob, pos);
            if (getPlayer().getEventInstance() != null) {
                getPlayer().getEventInstance().registerMonster(mob);
            }
        }
    }

    // returns slightly above the reactor's position for monster spawns
    public Point getPosition() {
        Point pos = reactor.getPosition();
        pos.y -= 10;
        return pos;
    }

    /**
     * Spawns an NPC at the reactor's location
     *
     * @param npcId
     */
    public void spawnNpc(int npcId) {
        spawnNpc(npcId, getPosition());
    }

    public void spawnNpc(int npcId, MapleCharacter owner) {
        spawnNpc(npcId, getPosition(), owner);
    }

    /**
     * Spawns an NPC at a custom position
     *
     * @param npcId
     * @param x
     * @param y
     */
    public void spawnNpc(int npcId, int x, int y) {
        spawnNpc(npcId, new Point(x, y));
    }

    /**
     * Spawns an NPC at a custom position
     *
     * @param npcId
     * @param pos
     */
    public void spawnNpc(int npcId, Point pos) {
        MapleNPC npc = MapleLifeFactory.getNPC(npcId);
        if (npc != null && !npc.getName().equals("MISSINGNO")) {
            npc.setPosition(pos);
            npc.setCy(pos.y);
            npc.setRx0(pos.x + 50);
            npc.setRx1(pos.x - 50);
            npc.setFh(reactor.getMap().getFootholds().findBelow(pos).getId());
            npc.setCustom(true);
            reactor.getMap().addMapObject(npc);
            reactor.getMap().broadcastMessage(MaplePacketCreator.spawnNPC(npc));
        }
    }

    public void spawnNpc(int npcId, Point pos, MapleCharacter owner) {
        MapleNPC npc = MapleLifeFactory.getNPC(npcId);
        if (npc != null && !npc.getName().equals("MISSINGNO")) {
            npc.setPosition(pos);
            npc.setCy(pos.y);
            npc.setRx0(pos.x + 50);
            npc.setRx1(pos.x - 50);
            npc.setFh(reactor.getMap().getFootholds().findBelow(pos).getId());
            npc.setCustom(true);
            npc.setOwner(owner);
            reactor.getMap().addMapObject(npc);
            reactor.getMap().broadcastMessage(MaplePacketCreator.spawnNPC(npc));
        }
    }

    public MapleReactor getReactor() {
        return reactor;
    }

    public void spawnFakeMonster(int id) {
        spawnFakeMonster(id, 1, getPosition());
    }

    // summon one monster, remote location
    public void spawnFakeMonster(int id, int x, int y) {
        spawnFakeMonster(id, 1, new Point(x, y));
    }

    // multiple monsters, reactor location
    public void spawnFakeMonster(int id, int qty) {
        spawnFakeMonster(id, qty, getPosition());
    }

    // multiple monsters, remote location
    public void spawnFakeMonster(int id, int qty, int x, int y) {
        spawnFakeMonster(id, qty, new Point(x, y));
    }

    // handler for all spawnFakeMonster
    private void spawnFakeMonster(int id, int qty, Point pos) {
        for (int i = 0; i < qty; i++) {
            MapleMonster mob = MapleLifeFactory.getMonster(id);
            reactor.getMap().spawnFakeMonsterOnGroundBelow(mob, pos);
        }
    }

    public void killAll() {
        reactor.getMap().killAllMonsters(false);
    }

    public void killMonster(int monsId) {
        reactor.getMap().killMonster(monsId);
    }

    public void warpMap(int mapId, int portal) {
        for (MapleCharacter mch : getClient().getPlayer().getMap().getCharacters()) {
            MapleMap target = getClient().getChannelServer().getMapFactory().getMap(mapId);
            mch.changeMap(target, target.getPortal(portal));
        }
    }
}