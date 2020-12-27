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

package guida.net.channel.handler;

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MaplePet;
import guida.client.anticheat.CheatingOffense;
import guida.net.AbstractMaplePacketHandler;
import guida.net.channel.ChannelServer;
import guida.net.world.MaplePartyCharacter;
import guida.server.AutobanManager;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.maps.MapleMapItem;
import guida.server.maps.MapleMapObject;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

/**
 * @author Matze
 */
public class ItemPickupHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (!c.getPlayer().getMap().isLootable() && !c.getPlayer().isGM()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        /*slea.readByte();
		slea.readInt();
		slea.readInt();*/
        slea.skip(9);
        final int oid = slea.readInt();
        final MapleMapObject ob = c.getPlayer().getMap().getMapObject(oid);
        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        c.getPlayer().resetAfkTimer();
        if (ob == null) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        if (ob instanceof MapleMapItem) {
            final MapleMapItem mapitem = (MapleMapItem) ob;
            synchronized (mapitem) {
                if (mapitem.isPickedUp()) {
                    c.sendPacket(MaplePacketCreator.enableActions());
                    return; // In GMS, it doesn't show anything if the item is already picked up
                }
                final double distance = c.getPlayer().getPosition().distanceSq(mapitem.getPosition());
                c.getPlayer().getCheatTracker().checkPickupAgain();
                if (distance > 90000.0) {
                    AutobanManager.getInstance().addPoints(c, 100, 300000, "Itemvac");
                    c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.ITEMVAC);
                } else if (distance > 30000.0) {
                    c.getPlayer().getCheatTracker().registerOffense(CheatingOffense.SHORT_ITEMVAC);
                }
                int mesos = mapitem.getMeso();
                if (mesos > 0) {
                    boolean dropOwner = mapitem.getDropper() == c.getPlayer();
                    boolean droppedByPlayer = mapitem.getDropper() instanceof MapleCharacter;
                    if (c.getPlayer().getParty() != null && !dropOwner && !droppedByPlayer) {
                        final ChannelServer cserv = c.getChannelServer();
                        int partynum = 0;
                        for (MaplePartyCharacter partymem : c.getPlayer().getParty().getMembers()) {
                            if (partymem.isOnline() && partymem.getChannel() == c.getChannel() && partymem.getMapId() == c.getPlayer().getMap().getId() && partymem.getPlayer() != null && !partymem.getPlayer().inCS() && !partymem.getPlayer().inMTS()) {
                                partynum++;
                            }
                        }
                        if (partynum == 0) {
                            partynum = 1;
                        }
                        for (MaplePartyCharacter partymem : c.getPlayer().getParty().getMembers()) {
                            if (partymem.isOnline() && partymem.getChannel() == c.getChannel() && partymem.getMapId() == c.getPlayer().getMap().getId() && partymem.getPlayer() != null && !partymem.getPlayer().inCS() && !partymem.getPlayer().inMTS()) {
                                MapleCharacter somecharacter = cserv.getPlayerStorage().getCharacterById(partymem.getId());
                                if (somecharacter != null) {
                                    somecharacter.gainMeso(mesos / partynum, true, true, false);
                                    removeItem(c.getPlayer(), mapitem, ob);
                                }
                            }
                        }
                    } else {
                        c.getPlayer().gainMeso(mesos, true, true, false, true, !dropOwner && droppedByPlayer && mesos > 16);
                        removeItem(c.getPlayer(), mapitem, ob);
                    }
                } else {
                    final int itemId = mapitem.getItem().getItemId();
                    if (itemId == 2022428 && c.getPlayer().haveItem(2022428, 1, false, false)) { // custom one of a kind (pink box)
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    if (ii.isPet(itemId)) {
                        MaplePet nPet = mapitem.getItem().getPet();
                        int petId = nPet != null ? nPet.getUniqueId() : -1;
                        if (nPet != null && mapitem.getOwner() != c.getPlayer()) {
                            nPet = MaplePet.updateExisting(c.getPlayer().getId(), mapitem.getItem().getPet());
                        } else if (petId == -1) {
                            nPet = MaplePet.createPet(c.getPlayer().getId(), itemId);
                        }
                        MapleInventoryManipulator.addById(c, itemId, mapitem.getItem().getQuantity(), "Pet was picked up", null, nPet);
                        c.sendPacket(MaplePacketCreator.enableActions());
                        removeItem(c.getPlayer(), mapitem, ob);
                    } else if (ii.isConsumeOnPickup(itemId)) {
                        if (ii.isMonsterCard(itemId) && !c.getPlayer().getMonsterBook().isCardMaxed(itemId)) {
                            c.getPlayer().getMonsterBook().addCard(c, itemId);
                        }
                        ii.getItemEffect(itemId).applyTo(c.getPlayer());
                        c.sendPacket(MaplePacketCreator.getShowItemGain(itemId, mapitem.getItem().getQuantity()));
                        removeItem(c.getPlayer(), mapitem, ob);
                    } else {
                        if (ii.isCrushRing(itemId) || ii.isFriendshipRing(itemId)) {
                            c.getPlayer().setCCRequired(true);
                        }
                        if (!c.getPlayer().canSeeItem(ob)) {
                            c.sendPacket(MaplePacketCreator.enableActions());
                            c.sendPacket(MaplePacketCreator.showItemUnavailable());
                            return;
                        }
                        if (!MapleInventoryManipulator.addByItem(c, mapitem.getItem(), "Picked up by " + c.getPlayer().getName(), true).isEmpty()) {
                            removeItem(c.getPlayer(), mapitem, ob);
                        } else {
                            c.getPlayer().getCheatTracker().pickupComplete();
                        }
                    }
                }
            }
        }
    }

    private void removeItem(MapleCharacter chr, MapleMapItem mapitem, MapleMapObject ob) {
        chr.getMap().broadcastMessage(MaplePacketCreator.removeItemFromMap(mapitem.getObjectId(), 2, chr.getId()), mapitem.getPosition());
        chr.getCheatTracker().pickupComplete();
        chr.getMap().removeMapObject(ob);
        mapitem.setPickedUp(true);
    }
}