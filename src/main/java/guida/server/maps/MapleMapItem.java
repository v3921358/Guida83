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

import guida.client.IItem;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.tools.MaplePacketCreator;

import java.awt.Point;

/**
 * @author Matze
 */
public class MapleMapItem extends AbstractMapleMapObject {

    protected final IItem item;
    protected final MapleMapObject dropper;
    protected final MapleCharacter owner;
    protected final int meso;
    protected boolean pickedUp = false;
    protected byte type;
    protected boolean isPlayerDrop = false;

    public MapleMapItem(IItem item, Point position, MapleMapObject dropper, MapleCharacter owner, byte type) {
        setPosition(position);
        this.item = item;
        this.dropper = dropper;
        this.owner = owner;
        meso = 0;
        this.type = type;
    }

    public MapleMapItem(int meso, Point position, MapleMapObject dropper, MapleCharacter owner, byte type) {
        setPosition(position);
        item = null;
        this.meso = meso;
        this.dropper = dropper;
        this.owner = owner;
        this.type = type;
    }

    public IItem getItem() {
        return item;
    }

    public MapleMapObject getDropper() {
        return dropper;
    }

    public MapleCharacter getOwner() {
        return owner;
    }

    public int getMeso() {
        return meso;
    }

    public boolean isPickedUp() {
        return pickedUp;
    }

    public void setPickedUp(boolean pickedUp) {
        this.pickedUp = pickedUp;
    }

    public byte getDropType() {
        return type;
    }

    public void setDropType(byte type) {
        this.type = type;
    }

    public boolean isPlayerDrop() {
        return isPlayerDrop;
    }

    public void setPlayerDrop(boolean playerDrop) {
        isPlayerDrop = playerDrop;
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.removeItemFromMap(getObjectId(), 1, 0));
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.ITEM;
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (meso > 0) {
            client.sendPacket(MaplePacketCreator.dropMesoFromMapObject(meso, getObjectId(), type == 4 ? 0 : dropper.getObjectId(), type == 4 ? 0 : type == 1 ? owner.getPartyId() : owner.getId(), null, getPosition(), (byte) 2, type, isPlayerDrop));
        } else {
            client.sendPacket(MaplePacketCreator.dropItemFromMapObject(item.getItemId(), getObjectId(), 0, type == 1 ? owner.getPartyId() : owner.getId(), null, getPosition(), (byte) 2, item.getExpiration(), type, isPlayerDrop));
        }
    }
}