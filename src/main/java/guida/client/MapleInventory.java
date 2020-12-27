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

import guida.server.MapleItemInformationProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Matze
 */
public class MapleInventory implements Iterable<IItem>, InventoryContainer {

    private final Map<Short, IItem> inventory;
    private final byte slotLimit;
    private final MapleInventoryType type;

    /**
     * Creates a new instance of MapleInventory
     */
    public MapleInventory(MapleInventoryType type, byte slotLimit) {
        inventory = new LinkedHashMap<>();
        this.slotLimit = slotLimit;
        this.type = type;
    }

    /**
     * Returns the item with its slot id if it exists within the inventory, otherwise null is returned
     */
    public IItem findById(int itemId) {
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                return item;
            }
        }
        return null;
    }

    public int countById(int itemId) {
        int possesed = 0;
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                possesed += item.getQuantity();
            }
        }
        return possesed;
    }

    public List<IItem> listById(int itemId) {
        List<IItem> ret = new LinkedList<>();
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                ret.add(item);
            }
        }
        return ret;
    }

    public Collection<IItem> list() {
        return inventory.values();
    }

    /**
     * Adds the item to the inventory and returns the assigned slot id
     */
    public short addItem(IItem item) {
        short slotId = getNextFreeSlot();
        if (slotId < 0) {
            return -1;
        }
        inventory.put(slotId, item);
        item.setPosition(slotId);
        return slotId;
    }

    public void addFromDB(IItem item) {
        if (item.getPosition() < 0 && !type.equals(MapleInventoryType.EQUIPPED)) {
            throw new RuntimeException("Item with negative position in non-equipped IV wtf?");
        }
        inventory.put(item.getPosition(), item);
    }

    public boolean move(short sSlot, short dSlot, short slotMax) {
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        Item source = (Item) inventory.get(sSlot);
        Item target = (Item) inventory.get(dSlot);
        if (source == null) {
            throw new InventoryException("Trying to move empty slot");
        }
        if (target == null) {
            source.setPosition(dSlot);
            inventory.put(dSlot, source);
            inventory.remove(sSlot);
        } else if (target.getItemId() == source.getItemId() && !ii.isThrowingStar(source.getItemId()) && !ii.isShootingBullet(source.getItemId()) && source.getOwner().equals(target.getOwner()) && source.getExpiration().compareTo(target.getExpiration()) == 0) {
            if (type.getType() == MapleInventoryType.EQUIP.getType()) {
                swap(target, source);
            }
            if (source.getQuantity() + target.getQuantity() > slotMax) {
                short rest = (short) (source.getQuantity() + target.getQuantity() - slotMax);
                if (rest + slotMax != source.getQuantity() + target.getQuantity()) {
                    return false;
                }
                source.setQuantity(rest);
                target.setQuantity(slotMax);
            } else {
                target.setQuantity((short) (source.getQuantity() + target.getQuantity()));
                inventory.remove(sSlot);
            }
        } else {
            swap(target, source);
        }
        return true;
    }

    private void swap(IItem source, IItem target) {
        inventory.remove(source.getPosition());
        inventory.remove(target.getPosition());
        short swapPos = source.getPosition();
        source.setPosition(target.getPosition());
        target.setPosition(swapPos);
        inventory.put(source.getPosition(), source);
        inventory.put(target.getPosition(), target);
    }

    public IItem getItem(short slot) {
        return inventory.get(slot);
    }

    public void removeItem(short slot) {
        removeItem(slot, (short) 1, false);
    }

    public void removeItem(short slot, int quantity, boolean allowZero) {
        IItem item = inventory.get(slot);
        if (item == null) {
            return;
        }
        item.setQuantity((short) (item.getQuantity() - quantity));
        if (item.getQuantity() < 0) {
            item.setQuantity((short) 0);
        }
        if (item.getQuantity() == 0 && !allowZero) {
            removeSlot(slot);
        }
    }

    public void removeSlot(short slot) {
        inventory.remove(slot);
    }

    public boolean isFull() {
        return inventory.size() >= slotLimit;
    }

    public boolean isFull(int margin) {
        return inventory.size() + margin >= slotLimit;
    }

    /**
     * Returns the next empty slot id, -1 if the inventory is full
     */
    public short getNextFreeSlot() {
        if (isFull()) {
            return -1;
        }
        for (short i = 1; i <= slotLimit; i++) {
            if (!inventory.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    public int getFreeSlots() {
        return slotLimit - inventory.size();
    }

    public MapleInventoryType getType() {
        return type;
    }

	/*public int countItemType(int charid, MapleInventoryType itemtype) {
        int it = itemtype.getType();
		try {
			Connection con = DatabaseConnection.getConnection();
			PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) AS c FROM inventoryitems WHERE characterid = ? AND inventorytype = ?");
			ps.setInt(1, charid);
			ps.setInt(2, it);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				int rawr = Integer.parseInt(rs.getString("c"));
				rs.close();
				ps.close();
				return rawr;
			}
			rs.close();
			ps.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}*/

    @Override
    public Iterator<IItem> iterator() {
        return Collections.unmodifiableCollection(inventory.values()).iterator();
    }

    @Override
    public Collection<MapleInventory> allInventories() {
        return Collections.singletonList(this);
    }

    public List<Short> findAllById(int itemId) {
        List<Short> slots = new LinkedList<>();
        for (IItem item : inventory.values()) {
            if (item.getItemId() == itemId) {
                slots.add(item.getPosition());
            }
        }
        return slots;
    }

    public IItem findByPetId(int petId) {
        for (IItem item : inventory.values()) {
            if (item.getPetId() == petId) {
                return item;
            }
        }
        return null;
    }
}