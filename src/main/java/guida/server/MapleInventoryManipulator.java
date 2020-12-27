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

package guida.server;

import guida.client.Equip;
import guida.client.IItem;
import guida.client.InventoryException;
import guida.client.Item;
import guida.client.ItemFlag;
import guida.client.MapleBuffStat;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.MaplePet;
import guida.tools.FileTimeUtil;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Matze
 */
public class MapleInventoryManipulator {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleInventoryManipulator.class);

    /* VIP Teleport Rocks method */
    public static void addById(MapleClient c, int itemId, short quantity, String logInfo) {
        addById(c, itemId, quantity, logInfo, null, null);
    }

    public static boolean addRing(MapleCharacter chr, int itemId, int ringId) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        MapleInventoryType type = ii.getInventoryType(itemId);
        IItem nEquip = ii.getEquipById(itemId, ringId);
        String logMsg = "Ring created by " + chr.getName();
        nEquip.log(logMsg, false);

        short newSlot = chr.getInventory(type).addItem(nEquip);
        if (newSlot == -1) {
            return false;
        }
        chr.getClient().sendPacket(MaplePacketCreator.addInventorySlot(type, nEquip));
        return true;
    }

    public static void addById(MapleClient c, int itemId, short quantity, String logInfo, String owner, MaplePet pet) {
        addById(c, itemId, quantity, logInfo, owner, pet, false);
    }

    public static void addById(MapleClient c, int itemId, short quantity, String logInfo, String owner, MaplePet pet, boolean gm) {
        if (quantity < 0) {
            return;
        }
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        MapleInventoryType type = ii.getInventoryType(itemId);
        if (c.hasPacketLog()) {
            c.writePacketLog("Gained " + quantity + " " + itemId + " (addById) ");
        }
        if (!type.equals(MapleInventoryType.EQUIP)) {
            short slotMax = ii.getSlotMax(c, itemId);
            List<IItem> existing = c.getPlayer().getInventory(type).listById(itemId);
            if (!ii.isThrowingStar(itemId) && !ii.isShootingBullet(itemId)) {
                if (!existing.isEmpty()) { // first update all existing slots to slotMax
                    Iterator<IItem> i = existing.iterator();
                    while (quantity > 0) {
                        if (i.hasNext()) {
                            Item eItem = (Item) i.next();
                            short oldQ = eItem.getQuantity();
                            if (oldQ < slotMax && (eItem.getOwner().equals(owner) || owner == null) && eItem.getExpiration().compareTo(FileTimeUtil.getDefaultTimestamp()) == 0) {
                                short newQ = (short) Math.min(oldQ + quantity, slotMax);
                                quantity -= newQ - oldQ;
                                eItem.setQuantity(newQ);
                                if (gm) {
                                    eItem.setGMFlag();
                                }
                                eItem.log("Added " + (newQ - oldQ) + " items to stack, new quantity is " + newQ + " (" + logInfo + " )", false);
                                c.sendPacket(MaplePacketCreator.updateInventorySlot(type, eItem));
                            }
                        } else {
                            break;
                        }
                    }
                }
                while (quantity > 0) { // add new slots if there is still something left
                    short newQ = (short) Math.min(quantity, slotMax);
                    if (newQ != 0) {
                        quantity -= newQ;
                        Item nItem = new Item(itemId, (short) 0, newQ);
                        if (ii.isPet(itemId)) {
                            nItem.setPet(pet != null ? pet : MaplePet.createPet(c.getPlayer().getId(), itemId));
                        }
                        if (gm) {
                            nItem.setGMFlag();
                        }
                        nItem.log("Created while adding by id. Quantity: " + newQ + " (" + logInfo + ")", false);
                        short newSlot = c.getPlayer().getInventory(type).addItem(nItem);
                        if (newSlot == -1) {
                            c.sendPacket(MaplePacketCreator.getInventoryFull());
                            c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                            return;
                        }
                        if (owner != null) {
                            nItem.setOwner(owner);
                        }
                        c.sendPacket(MaplePacketCreator.addInventorySlot(type, nItem));
                        if ((ii.isThrowingStar(itemId) || ii.isShootingBullet(itemId)) && quantity == 0) {
                            break;
                        }
                    } else {
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                }
            } else { // Throwing Stars and Bullets - Add all into one slot regardless of quantity.
                Item nItem = new Item(itemId, (short) 0, quantity);
                if (gm) {
                    nItem.setGMFlag();
                }
                nItem.log("Created while adding by id. Quantity: " + quantity + " (" + logInfo + " )", false);
                short newSlot = c.getPlayer().getInventory(type).addItem(nItem);
                if (newSlot == -1) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    return;
                }
                c.sendPacket(MaplePacketCreator.addInventorySlot(type, nItem));
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        } else {
            if (quantity == 1) {
                IItem nEquip = ii.getEquipById(itemId);
                if (gm) {
                    nEquip.setGMFlag();
                }
                nEquip.log("Created while adding by id. (" + logInfo + " )", false);
                if (owner != null) {
                    nEquip.setOwner(owner);
                }

                short newSlot = c.getPlayer().getInventory(type).addItem(nEquip);
                if (newSlot == -1) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    return;
                }
                c.sendPacket(MaplePacketCreator.addInventorySlot(type, nEquip));
            } else {
                throw new InventoryException("Trying to create equip with non-one quantity");
            }
        }
    }

    public static List<Pair<Short, IItem>> addByItemId(MapleClient c, int itemId, short quantity, String logInfo, boolean randomizeEquipStats) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        if (ii.getInventoryType(itemId).equals(MapleInventoryType.EQUIP)) {
            IItem nEquip = ii.getEquipById(itemId);
            if (randomizeEquipStats) {
                nEquip = ii.randomizeStats((Equip) nEquip);
            }
            return addByItem(c, nEquip, logInfo, false);
        }
        return addByItem(c, new Item(itemId, (short) 0, quantity), logInfo, false);
    }

    public static List<Pair<Short, IItem>> addByItem(MapleClient c, IItem item, String logInfo, boolean pickUp) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        List<Pair<Short, IItem>> items = new ArrayList<>(5);
        short quantity = item.getQuantity();
        if (quantity < 0) {
            return items;
        }
        if (pickUp && ii.canHaveOnlyOne(item.getItemId()) && c.getPlayer().haveItem(item.getItemId(), 1, true, true)) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            c.sendPacket(MaplePacketCreator.showItemUnavailable());
            return items;
        }
        if (!pickUp && !logInfo.contains("trade") && !canHold(c, Collections.singletonList(item))) {
            return items;
        }
        if (c.hasPacketLog()) {
            c.writePacketLog("Gained " + quantity + " " + item.getItemId() + " (addByItem)");
        }
        MapleInventoryType type = ii.getInventoryType(item.getItemId());
        if (!type.equals(MapleInventoryType.EQUIP)) {
            short slotMax = ii.getSlotMax(c, item.getItemId());
            List<IItem> existing = c.getPlayer().getInventory(type).listById(item.getItemId());
            if (!ii.isThrowingStar(item.getItemId()) && !ii.isShootingBullet(item.getItemId())) {
                if (!existing.isEmpty()) { // first update all existing slots to slotMax
                    Iterator<IItem> i = existing.iterator();
                    while (quantity > 0) {
                        if (i.hasNext()) {
                            Item eItem = (Item) i.next();
                            short oldQ = eItem.getQuantity();
                            if (oldQ < slotMax && item.getOwner().equals(eItem.getOwner()) && item.getExpiration().compareTo(eItem.getExpiration()) == 0) {
                                short newQ = (short) Math.min(oldQ + quantity, slotMax);
                                quantity -= newQ - oldQ;
                                eItem.setQuantity(newQ);
                                if (item.isByGM()) {
                                    eItem.setGMFlag();
                                }
                                eItem.log("Added " + (newQ - oldQ) + " items to stack, new quantity is " + newQ + " (" + logInfo + " )", false);
                                items.add(new Pair<>((short) 1, eItem));
                            }
                        } else {
                            break;
                        }
                    }
                } // add new slots if there is still something left
                while (quantity > 0) {
                    short newQ = (short) Math.min(quantity, slotMax);
                    quantity -= newQ;
                    Item nItem = new Item(item.getItemId(), (byte) 0, newQ);
                    nItem.setOwner(item.getOwner());
                    nItem.setExpiration(item.getExpiration());
                    if (ii.isPet(item.getItemId())) {
                        nItem.setPet(item.getPet() != null ? item.getPet() : MaplePet.createPet(c.getPlayer().getId(), item.getItemId()));
                    }
                    if (item.isByGM()) {
                        nItem.setGMFlag();
                    }
                    nItem.log("Created while adding from drop. Quantity: " + newQ + " (" + logInfo + " )", false);
                    short newSlot = c.getPlayer().getInventory(type).addItem(nItem);
                    if (newSlot == -1 && pickUp) {
                        c.sendPacket(MaplePacketCreator.getInventoryFull());
                        c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                        item.setQuantity((short) (quantity + newQ));
                        c.sendPacket(MaplePacketCreator.modifyInventory(true, items));
                        return Collections.emptyList();
                    }
                    items.add(new Pair<>((short) 0, nItem));
                }
            } else {
                // Throwing Stars and Bullets - Add all into one slot regardless of quantity.
                Item nItem = new Item(item.getItemId(), (byte) 0, quantity);
                nItem.setOwner(item.getOwner());
                nItem.setExpiration(item.getExpiration());
                nItem.log("Created while adding by id. Quantity: " + quantity + " (" + logInfo + " )", false);
                if (item.isByGM()) {
                    nItem.setGMFlag();
                }
                short newSlot = c.getPlayer().getInventory(type).addItem(nItem);
                if (newSlot == -1 && pickUp) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    c.sendPacket(MaplePacketCreator.modifyInventory(true, items));
                    return Collections.emptyList();
                }
                items.add(new Pair<>((short) 0, nItem));
            }
        } else {
            if (!logInfo.contains("Canceled trade") && !logInfo.contains("returned from shop") && (item.getFlag() & ItemFlag.TRADE_ONCE.getValue()) == 16) {
                item.setFlag((short) (item.getFlag() ^ ItemFlag.TRADE_ONCE.getValue()));
            }
            item.log("Adding from drop. (" + logInfo + " )", false);
            short newSlot = c.getPlayer().getInventory(type).addItem(item);
            if (newSlot == -1) {
                c.sendPacket(MaplePacketCreator.getInventoryFull());
                c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                return Collections.emptyList();
            }
            items.add(new Pair<>((short) 0, item));
        }
        if (pickUp) {
            c.sendPacket(MaplePacketCreator.modifyInventory(true, items));
            c.sendPacket(MaplePacketCreator.getShowItemGain(item.getItemId(), item.getQuantity()));
        }
        return items;
    }

    public static boolean checkSpace(MapleClient c, int itemid, int quantity, String owner) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        MapleInventoryType type = ii.getInventoryType(itemid);

        if (!type.equals(MapleInventoryType.EQUIP)) {
            short slotMax = ii.getSlotMax(c, itemid);
            List<IItem> existing = c.getPlayer().getInventory(type).listById(itemid);
            if (!ii.isThrowingStar(itemid) && !ii.isShootingBullet(itemid)) {
                if (!existing.isEmpty()) { // first update all existing slots to slotMax
                    for (IItem eItem : existing) {
                        short oldQ = eItem.getQuantity();
                        if (oldQ < slotMax && owner.equals(eItem.getOwner()) && eItem.getExpiration().compareTo(FileTimeUtil.getDefaultTimestamp()) == 0) {
                            short newQ = (short) Math.min(oldQ + quantity, slotMax);
                            quantity -= newQ - oldQ;
                        }
                        if (quantity <= 0) {
                            break;
                        }
                    }
                }
            }
            final int numSlotsNeeded;
            if (slotMax > 0) { // add new slots if there is still something left
                numSlotsNeeded = (int) Math.ceil((double) quantity / slotMax);
            } else if (ii.isThrowingStar(itemid) || ii.isShootingBullet(itemid)) {
                numSlotsNeeded = 1;
            } else {
                if (ii.getName(itemid) != null) {
                    numSlotsNeeded = 1;
                    log.error("ItemID: " + itemid + " has a slotMax of " + slotMax + ".");
                } else {
                    log.error("Something is giving a player an invalid item. Item ID: " + itemid);
                    return false;
                }
            }
            return !c.getPlayer().getInventory(type).isFull(numSlotsNeeded - 1);
        } else {
            return !c.getPlayer().getInventory(type).isFull();
        }
    }

    public static void removeFromSlot(MapleClient c, MapleInventoryType type, short slot, short quantity, boolean fromDrop) {
        removeFromSlot(c, type, slot, quantity, fromDrop, false);
    }

    public static void removeFromSlot(MapleClient c, MapleInventoryType type, short slot, int quantity, boolean fromDrop, boolean consume) {
        if (quantity < 0) {
            return;
        }
        IItem item = c.getPlayer().getInventory(type).getItem(slot);
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        boolean allowZero = consume && (ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId()));
        c.getPlayer().getInventory(type).removeItem(slot, quantity, allowZero);
        if (item.getQuantity() == 0 && !allowZero) {
            c.sendPacket(MaplePacketCreator.clearInventoryItem(type, item.getPosition(), fromDrop));
        } else {
            if (!consume) {
                item.log(c.getPlayer().getName() + " removed " + quantity + ". " + item.getQuantity() + " left.", false);
            }
            c.sendPacket(MaplePacketCreator.updateInventorySlot(type, item, fromDrop));
        }
    }

    public static void removeById(MapleClient c, MapleInventoryType type, int itemId, int quantity, boolean fromDrop, boolean consume) {
        if (quantity < 0) {
            return;
        }
        List<IItem> items = c.getPlayer().getInventory(type).listById(itemId);
        int remremove = quantity;
        for (IItem item : items) {
            if (remremove <= item.getQuantity()) {
                removeFromSlot(c, type, item.getPosition(), (short) remremove, fromDrop, consume);
                remremove = 0;
                break;
            } else {
                remremove -= item.getQuantity();
                removeFromSlot(c, type, item.getPosition(), item.getQuantity(), fromDrop, consume);
            }
        }
        if (remremove > 0) {
            throw new InventoryException("Not enough cheese available (Item ID: " + itemId + " | Remove Amount: " + quantity + " | Quantity that could not be removed: " + remremove + ")");
        }
    }

    public static Pair<Short, IItem> removeItemFromSlot(MapleClient c, MapleInventoryType type, short slot, short quantity, boolean consume) {
        if (quantity < 0) {
            return new Pair<>((short) -1, null);
        }
        IItem item = c.getPlayer().getInventory(type).getItem(slot);
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        boolean allowZero = consume && (ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId()));
        c.getPlayer().getInventory(type).removeItem(slot, quantity, allowZero);
        if (item.getQuantity() == 0 && !allowZero) {
            return new Pair<>((short) 3, item);
        } else {
            if (!consume) {
                item.log(c.getPlayer().getName() + " removed " + quantity + ". " + item.getQuantity() + " left.", false);
            }
            return new Pair<>((short) 1, item);
        }
    }

    public static List<Pair<Short, IItem>> removeItem(MapleClient c, MapleInventoryType type, int itemId, int quantity, boolean consume) {
        List<Pair<Short, IItem>> allItems = new ArrayList<>();
        List<IItem> items = c.getPlayer().getInventory(type).listById(itemId);
        int remremove = quantity;
        for (IItem item : items) {
            if (remremove <= item.getQuantity()) {
                allItems.add(removeItemFromSlot(c, type, item.getPosition(), (short) remremove, consume));
                remremove = 0;
                break;
            } else {
                remremove -= item.getQuantity();
                allItems.add(removeItemFromSlot(c, type, item.getPosition(), item.getQuantity(), consume));
            }
        }
        if (remremove > 0) {
            allItems.clear();
        }
        return allItems;
    }

    public static List<Pair<Short, IItem>> move(MapleClient c, MapleInventoryType type, short src, short dst) {
        List<Pair<Short, IItem>> allItems = new ArrayList<>();
        if (src < 0 || dst < 0) {
            return allItems;
        }
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        IItem source = c.getPlayer().getInventory(type).getItem(src);
        IItem initialTarget = c.getPlayer().getInventory(type).getItem(dst);
        if (source == null) {
            return allItems;
        }
        short olddstQ = -1;
        if (initialTarget != null) {
            olddstQ = initialTarget.getQuantity();
        }
        short oldsrcQ = source.getQuantity();
        short slotMax = ii.getSlotMax(c, source.getItemId());
        boolean op = c.getPlayer().getInventory(type).move(src, dst, slotMax);
        if (!op) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return allItems;
        }
        if (!type.equals(MapleInventoryType.EQUIP) && initialTarget != null && initialTarget.getItemId() == source.getItemId() && !ii.isThrowingStar(source.getItemId()) && !ii.isShootingBullet(source.getItemId()) && initialTarget.getOwner().equals(source.getOwner()) && initialTarget.getExpiration().compareTo(source.getExpiration()) == 0) {
            if (olddstQ + oldsrcQ > slotMax) {
                allItems.add(new Pair<>((short) 1, source));
            } else {
                allItems.add(new Pair<>((short) 3, source));
            }
            allItems.add(new Pair<>((short) 1, initialTarget));
        } else {
            allItems.add(new Pair<>((short) 2, source));
        }
        return allItems;
    }

    public static void equip(MapleClient c, short src, short dst) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        MapleCharacter chr = c.getPlayer();
        Equip source = (Equip) chr.getInventory(MapleInventoryType.EQUIP).getItem(src);
        Equip target = (Equip) chr.getInventory(MapleInventoryType.EQUIPPED).getItem(dst);

        if (source == null) {
            return;
        }

        final int itemId = source.getItemId();
        final int reqLevel = ii.getReqLevel(itemId);
        final int reqStr = ii.getReqStr(itemId);
        final int reqDex = ii.getReqDex(itemId);
        final int reqInt = ii.getReqInt(itemId);
        final int reqLuk = ii.getReqLuk(itemId);
        boolean cashSlot = false;

        if (dst < -99) {
            cashSlot = true;
        }

        final String type = ii.getType(itemId);
        if (!ii.isCashItem(itemId)) {
            final boolean isCorrectSlot = switch (dst) {
                case -1 -> type.equals("Cp");
                case -2 -> type.equals("Af");
                case -3 -> type.equals("Ay");
                case -4 -> type.equals("Ae");
                case -5 -> type.startsWith("Ma");
                case -6 -> type.equals("Pn");
                case -7 -> type.equals("So");
                case -8 -> type.equals("Gv");
                case -9 -> type.equals("Sr");
                case -10 -> type.equals("Si");
                case -11 -> type.startsWith("Wp");
                case -12, -13, -15, -16 -> type.equals("Ri");
                case -17 -> type.equals("Pe");
                case -18 -> type.equals("Tm");
                case -19 -> type.equals("Sd");
                case -49 -> type.equals("Me");
                case -50 -> type.equals("Be");
                default -> true;
            };
            if (!isCorrectSlot) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
        }
        if ((ii.isCrushRing(source.getItemId()) || ii.isFriendshipRing(source.getItemId())) && chr.isCCRequired()) {
            chr.dropMessage("Please change channels before equipping this ring.");
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        if ((ii.getName(itemId).contains("(Male)") && chr.getGender() != 0 || ii.getName(itemId).contains("(Female)") && chr.getGender() != 1 || reqLevel > chr.getLevel() || reqStr > chr.getTotalStr() || reqDex > chr.getTotalDex() || reqInt > chr.getTotalInt() || reqLuk > chr.getTotalLuk() || cashSlot && !ii.isCashItem(itemId)) && !chr.hasGMLevel(5)) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }

        if (dst == -6) { // unequip the overall
            IItem top = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -5);
            if (top != null && ii.isOverall(top.getItemId())) {
                if (chr.getInventory(MapleInventoryType.EQUIP).isFull()) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -5, chr.getInventory(MapleInventoryType.EQUIP).getNextFreeSlot());
            }
        } else if (dst == -5) { // unequip the bottom and top
            IItem top = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -5);
            IItem bottom = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -6);
            if (top != null && ii.isOverall(itemId)) {
                if (chr.getInventory(MapleInventoryType.EQUIP).isFull(bottom != null && ii.isOverall(itemId) ? 1 : 0)) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -5, chr.getInventory(MapleInventoryType.EQUIP).getNextFreeSlot());
            }
            if (bottom != null && ii.isOverall(itemId)) {
                if (chr.getInventory(MapleInventoryType.EQUIP).isFull()) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -6, chr.getInventory(MapleInventoryType.EQUIP).getNextFreeSlot());
            }
        } else if (dst == -10) { // check if weapon is two-handed
            IItem weapon = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -11);
            if (weapon != null && ii.isTwoHanded(weapon.getItemId())) {
                if (chr.getInventory(MapleInventoryType.EQUIP).isFull()) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -11, chr.getInventory(MapleInventoryType.EQUIP).getNextFreeSlot());
            }
        } else if (dst == -11) {
            IItem shield = chr.getInventory(MapleInventoryType.EQUIPPED).getItem((short) -10);
            if (shield != null && ii.isTwoHanded(itemId)) {
                if (chr.getInventory(MapleInventoryType.EQUIP).isFull()) {
                    c.sendPacket(MaplePacketCreator.getInventoryFull());
                    c.sendPacket(MaplePacketCreator.getShowInventoryFull());
                    return;
                }
                unequip(c, (byte) -10, chr.getInventory(MapleInventoryType.EQUIP).getNextFreeSlot());
            }
        }
        source = (Equip) chr.getInventory(MapleInventoryType.EQUIP).getItem(src);
        target = (Equip) chr.getInventory(MapleInventoryType.EQUIPPED).getItem(dst);
        chr.getInventory(MapleInventoryType.EQUIP).removeSlot(src);
        if (target != null) {
            chr.getInventory(MapleInventoryType.EQUIPPED).removeSlot(dst);
        }
        source.setPosition(dst);
        boolean equipTradeLock = ii.isEquipTradeBlocked(itemId) && (source.getFlag() & ItemFlag.UNTRADEABLE.getValue()) != 8;
        if (equipTradeLock) {
            source.setFlag((short) (source.getFlag() | ItemFlag.UNTRADEABLE.getValue()));
        }
        chr.getInventory(MapleInventoryType.EQUIPPED).addFromDB(source);
        if (target != null) {
            target.setPosition(src);
            chr.getInventory(MapleInventoryType.EQUIP).addFromDB(target);
        }
        if (chr.getBuffedValue(MapleBuffStat.BOOSTER) != null && ii.isWeapon(itemId)) {
            chr.cancelBuffStats(MapleBuffStat.BOOSTER);
        }
        if (type.startsWith("Wp")) {
            if (ii.getName(itemId).startsWith("Dragon") && ii.getReqLevel(itemId) == 110) {
                chr.finishAchievement(7);
            } else if (ii.getName(itemId).startsWith("Reverse") && ii.getReqLevel(itemId) == 120) {
                chr.finishAchievement(44);
            } else if (ii.getName(itemId).startsWith("Timeless") && ii.getReqLevel(itemId) == 120) {
                chr.finishAchievement(45);
            }
        }
        c.sendPacket(MaplePacketCreator.moveInventoryItem(MapleInventoryType.EQUIP, src, dst, (byte) 2));
        if (equipTradeLock) {
            final IItem item = chr.getInventory(MapleInventoryType.EQUIPPED).getItem(dst);
            final List<Pair<Short, IItem>> equipUpdate = new ArrayList<>(2);
            equipUpdate.add(new Pair<>((short) 3, item));
            equipUpdate.add(new Pair<>((short) 0, item));
            c.sendPacket(MaplePacketCreator.modifyInventory(true, equipUpdate));
        }
        if (itemId == 1822000 || itemId == 1832000) {
            updatePetEquip(chr, dst);
        }
        chr.equipChanged();
    }

    public static void unequip(MapleClient c, short src, short dst) {
        Equip source = (Equip) c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(src);
        Equip target = (Equip) c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem(dst);
        if (dst < 0) {
            log.warn("Unequipping to negative slot. ({}: {}->{})", c.getPlayer().getName(), src, dst);
        }
        if (source == null) {
            return;
        }
        if (target != null && src <= 0) { // do not allow switching with equip
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return;
        }
        c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).removeSlot(src);
        if (target != null) {
            c.getPlayer().getInventory(MapleInventoryType.EQUIP).removeSlot(dst);
        }
        if (source.getItemId() == 1822000 || source.getItemId() == 1832000) {
            updatePetEquip(c.getPlayer(), source.getPosition());
        }
        source.setPosition(dst);
        c.getPlayer().getInventory(MapleInventoryType.EQUIP).addFromDB(source);
        if (target != null) {
            target.setPosition(src);
            c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).addFromDB(target);
        }
        c.sendPacket(MaplePacketCreator.moveInventoryItem(MapleInventoryType.EQUIP, src, dst, (byte) 1));
        c.getPlayer().equipChanged();
    }

    private static void updatePetEquip(MapleCharacter chr, short pos) {
        MaplePet pet = switch (pos) {
            case -121, -129 -> chr.getPet(0);
            case -131, -132 -> chr.getPet(1);
            case -139, -140 -> chr.getPet(2);
            default -> null;
        };
        if (pet != null) {
            chr.updatePetEquips(pet);
            if (pos == -121 || pos == -131 || pos == -139) {
                chr.getMap().broadcastMessage(chr, MaplePacketCreator.updatePetNameTag(chr, pet.getName(), chr.getPetIndex(pet), pet.hasLabelRing()), false);
            }
        }
    }

    private static boolean forbiddenPQDrop(int itemid, int mapid) {
        switch (itemid) {
            case 4001023: // Key of Dimension
            case 4001022: // Pass of Dimension
                if (mapid == 221024500) {
                    return true;
                }
                break;
            case 4001007:
            case 4001008:
                if (mapid == 103000000) {
                    return true;
                }
                break;
        }
        return false;
    }

    public static void drop(MapleClient c, MapleInventoryType type, short src, short quantity) {
        drop(c, type, src, quantity, false);
    }

    public static void drop(MapleClient c, MapleInventoryType type, short src, short quantity, boolean bypass) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        if (src < 0) {
            type = MapleInventoryType.EQUIPPED;
        }
        IItem source = c.getPlayer().getInventory(type).getItem(src);
        if (quantity < 0 || source == null || quantity == 0 && !ii.isThrowingStar(source.getItemId()) && !ii.isShootingBullet(source.getItemId())) {
            String message = "Dropping " + quantity + " " + (source == null ? "?" : source.getItemId()) + " (" + type.name() + "/" + src + ")";
            log.info(MapleClient.getLogMessage(c, message));
            c.disconnect(); // disconnect the client as is inventory is inconsistent with the serverside inventory
            return;
        }
        if (source.isByGM() && c.getPlayer().isGMLegit()) {
            c.getPlayer().dropMessage(6, "As you are a GM-funded legit, you may not release this GM-created item.");
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        if (quantity > ii.getSlotMax(c, source.getItemId())) {
            try {
                c.getChannelServer().getWorldInterface().broadcastGMMessage(c.getPlayer().getName(), MaplePacketCreator.serverNotice(0, c.getPlayer().getName() + " is dropping Item ID : " + source.getItemId() + " more than slotMax.").getBytes());
            } catch (Throwable u) {
                u.printStackTrace();
            }
        }
        Point dropPos = new Point(c.getPlayer().getPosition());
        if (ii.isPet(source.getItemId())) {
            c.getPlayer().unequipPet(source.getPet(), false);
        }
        if (quantity < source.getQuantity() && !ii.isThrowingStar(source.getItemId()) && !ii.isShootingBullet(source.getItemId())) {
            IItem target = source.copy();
            target.setQuantity(quantity);
            target.log(c.getPlayer().getName() + " dropped part of a stack at " + dropPos.toString() + " on map " + c.getPlayer().getMapId() + ". Quantity of this (new) instance is now " + quantity, false);
            source.setQuantity((short) (source.getQuantity() - quantity));
            source.log(c.getPlayer().getName() + " dropped part of a stack at " + dropPos.toString() + " on map " + c.getPlayer().getMapId() + ". Quantity of this (leftover) instance is now " + source.getQuantity(), false);
            c.sendPacket(MaplePacketCreator.dropInventoryItemUpdate(type, source));
            boolean weddingRing = source.getItemId() == 1112803 || source.getItemId() == 1112806 || source.getItemId() == 1112807 || source.getItemId() == 1112809;
            if (forbiddenPQDrop(source.getItemId(), c.getPlayer().getMapId())) {
                c.getPlayer().getMap().disappearingItemDrop(c.getPlayer(), c.getPlayer(), target, dropPos);
                c.getPlayer().dropMessage(1, "You have dropped a PQ item in a place that allows smuggling without loss of NX, thus you have lost the items.");
            } else if (weddingRing) {
                c.getPlayer().getMap().disappearingItemDrop(c.getPlayer(), c.getPlayer(), target, dropPos);
            } else if (c.getPlayer().getMap().getEverlast()) {
                if (!c.getChannelServer().allowUndroppablesDrop() && (ii.isDropRestricted(target.getItemId()) && (target.getFlag() & ItemFlag.TRADE_ONCE.getValue()) != 16 || (target.getFlag() & ItemFlag.UNTRADEABLE.getValue()) == 8)) {
                    c.getPlayer().getMap().disappearingItemDrop(c.getPlayer(), c.getPlayer(), target, dropPos);
                } else {
                    c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), target, dropPos, true, false, true);
                }
            } else {
                if (!c.getChannelServer().allowUndroppablesDrop() && (ii.isDropRestricted(target.getItemId()) && (target.getFlag() & ItemFlag.TRADE_ONCE.getValue()) != 16 || (target.getFlag() & ItemFlag.UNTRADEABLE.getValue()) == 8)) {
                    c.getPlayer().getMap().disappearingItemDrop(c.getPlayer(), c.getPlayer(), target, dropPos);
                } else {
                    c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), target, dropPos, true, true, true);
                }
            }
        } else {
            source.log(c.getPlayer().getName() + " dropped this (with full quantity) at " + dropPos.toString() + " on map " + c.getPlayer().getMapId(), false);
            c.getPlayer().getInventory(type).removeSlot(src);
            c.sendPacket(MaplePacketCreator.dropInventoryItem((src < 0 ? MapleInventoryType.EQUIP : type), src));
            if (src < 0) {
                c.getPlayer().equipChanged();
            }
            if (forbiddenPQDrop(source.getItemId(), c.getPlayer().getMapId())) {
                c.getPlayer().getMap().disappearingItemDrop(c.getPlayer(), c.getPlayer(), source, dropPos);
                c.getPlayer().dropMessage(1, "You have dropped a PQ item in a place that allows smuggling without loss of NX, thus you have lost the items.");
            } else if (c.getPlayer().getMap().getEverlast()) {
                if (!c.getChannelServer().allowUndroppablesDrop() && (ii.isDropRestricted(source.getItemId()) && (source.getFlag() & ItemFlag.TRADE_ONCE.getValue()) != 16 || (source.getFlag() & ItemFlag.UNTRADEABLE.getValue()) == 8) && !bypass) {
                    c.getPlayer().getMap().disappearingItemDrop(c.getPlayer(), c.getPlayer(), source, dropPos);
                } else {
                    c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), source, dropPos, true, false, true);
                }
            } else {
                if (!c.getChannelServer().allowUndroppablesDrop() && (ii.isDropRestricted(source.getItemId()) && (source.getFlag() & ItemFlag.TRADE_ONCE.getValue()) != 16 || (source.getFlag() & ItemFlag.UNTRADEABLE.getValue()) == 8) && !bypass) {
                    c.getPlayer().getMap().disappearingItemDrop(c.getPlayer(), c.getPlayer(), source, dropPos);
                } else {
                    c.getPlayer().getMap().spawnItemDrop(c.getPlayer(), c.getPlayer(), source, dropPos, true, true, true);
                }
            }
        }
    }

    public static void exchange(MapleClient c, int meso, int[] items, boolean randomizeEquipStats) {
        if (meso != 0) {
            c.getPlayer().gainMeso(meso, true, false, true);
        }
        if (items.length > 0 && items.length % 2 == 0) {
            final List<Pair<Integer, Integer>> itemAmounts = new ArrayList<>();
            final List<Pair<Short, IItem>> allRemoveItems = new ArrayList<>();
            final List<Pair<Short, IItem>> allAddItems = new ArrayList<>();
            int itemId;
            int quantity;
            boolean failedToRemove = false;
            for (int i = 0; i < items.length; i++) {
                itemId = items[i];
                quantity = items[i + 1];
                if (itemId == 0) {
                    continue;
                }
                MapleInventoryType ivType = MapleItemInformationProvider.getInstance().getInventoryType(itemId);
                if (quantity == 0) {
                    quantity -= c.getPlayer().getInventory(ivType).countById(itemId);
                }
                itemAmounts.add(new Pair<>(itemId, quantity));
                if (quantity > 0) {
                    allAddItems.addAll(addByItemId(c, itemId, (short) quantity, "", randomizeEquipStats));
                } else {
                    allRemoveItems.addAll(removeItem(c, ivType, itemId, -quantity, true));
                    if (allRemoveItems.isEmpty()) {
                        failedToRemove = true;
                        break;
                    }
                }
                i++;
            }
            if (failedToRemove) {
                allRemoveItems.clear();
                allAddItems.clear();
            }
            c.sendPacket(MaplePacketCreator.modifyInventory(false, allRemoveItems));
            c.sendPacket(MaplePacketCreator.modifyInventory(false, allAddItems));
            if (!failedToRemove) {
                c.sendPacket(MaplePacketCreator.getShowItemGain(itemAmounts));
            }
        }
    }

    public static boolean canHold(MapleClient c, int[] items) {
        final ArrayList<IItem> itemList = new ArrayList<>();
        for (int i = 0; i < items.length; i += 2) {
            int itemId = items[i], quantity = items[i + 1];
            if (itemId == 0) {
                continue;
            }
            itemList.add(new Item(itemId, (short) 0, (short) quantity));
        }
        itemList.trimToSize();
        return canHold(c, itemList);
    }

    public static boolean canHold(MapleClient c, List<IItem> items) {
        int randomEquipId = 1000000;
        final Map<Integer, IItem> newItemList = new LinkedHashMap<>(items.size());
        for (IItem item : items) {
            if (item.getQuantity() > 0) {
                if (item.getType() == IItem.EQUIP) {
                    newItemList.put(randomEquipId++, item);
                    continue;
                }
                if (newItemList.containsKey(item.getItemId())) {
                    final IItem nItem = newItemList.get(item.getItemId()).copy();
                    if (nItem.getOwner().equals(item.getOwner()) && nItem.getExpiration().compareTo(item.getExpiration()) == 0) {
                        nItem.setQuantity((short) (nItem.getQuantity() + item.getQuantity()));
                        newItemList.put(item.getItemId(), nItem);
                    }
                } else {
                    newItemList.put(item.getItemId(), item);
                }
            }
        }
        if (newItemList.isEmpty()) {
            return true;
        }
        final Map<Byte, Byte> itemMap = new LinkedHashMap<>(5);
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        for (IItem item : newItemList.values()) {
            final int itemId = item.getItemId();
            short quantity = item.getQuantity();
            final short slotMax = ii.getSlotMax(c, itemId);
            final byte type = ii.getInventoryType(itemId).getType();
            byte amount = 0;
            if (type != 1 && !ii.isThrowingStar(itemId) && !ii.isShootingBullet(itemId)) {
                final List<IItem> existing = c.getPlayer().getInventory(MapleInventoryType.getByType(type)).listById(itemId);
                for (IItem eItem : existing) {
                    final short oldQ = eItem.getQuantity();
                    if (oldQ < slotMax && eItem.getOwner().equals(item.getOwner()) && eItem.getExpiration().compareTo(item.getExpiration()) == 0) {
                        quantity -= slotMax - oldQ;
                    }
                    if (quantity < 0) {
                        break;
                    }
                }
            }
            while (quantity > slotMax) {
                amount++;
                quantity -= slotMax;
            }
            if (quantity > 0) {
                amount++;
            }
            if (itemMap.containsKey(type)) {
                itemMap.put(type, (byte) (itemMap.get(type) + amount));
            } else {
                itemMap.put(type, amount);
            }
        }
        for (Map.Entry<Byte, Byte> entry : itemMap.entrySet()) {
            if (c.getPlayer().getInventory(MapleInventoryType.getByType(entry.getKey())).getFreeSlots() < entry.getValue()) {
                return false;
            }
        }
        return true;
    }
}