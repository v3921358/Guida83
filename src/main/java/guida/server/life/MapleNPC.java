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

import guida.client.MapleClient;
import guida.server.maps.MapleMapObjectType;
import guida.server.playerinteractions.MapleShopFactory;
import guida.tools.MaplePacketCreator;

public class MapleNPC extends AbstractLoadedMapleLife {

    private final MapleNPCStats stats;
    private boolean custom = false;

    public MapleNPC(int id, MapleNPCStats stats) {
        super(id);
        this.stats = stats;
    }

    public boolean hasShop() {
        return MapleShopFactory.getInstance().getShopForNPC(getId()) != null;
    }

    public void sendShop(MapleClient c) {
        MapleShopFactory.getInstance().getShopForNPC(getId()).sendShop(c);
    }

    @Override
    public void sendSpawnData(MapleClient client) {
        if (getName().contains("Maple TV")) {
            return;
        }
        if (getId() == 9000036 || getId() == 9000021 || getId() >= 9010011 && getId() <= 9010013 || getId() == 9201116 || getId() == 9209000 || getId() == 9209001 || getId() == 9209008 || getId() == 1022101 || getId() == 9250052 || getId() == 2041017 || getId() == 9000017) {
            client.sendPacket(MaplePacketCreator.spawnNPCRequestController(this, false));
        } else {
            client.sendPacket(MaplePacketCreator.spawnNPC(this));
            if (!custom) {
                client.sendPacket(MaplePacketCreator.spawnNPCRequestController(this, true));
            }
        }
    }

    @Override
    public void sendDestroyData(MapleClient client) {
        client.sendPacket(MaplePacketCreator.removeNPC(getObjectId()));
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.NPC;
    }

    public String getName() {
        return stats.getName();
    }

    public boolean isCustom() {
        return custom;
    }

    public void setCustom(boolean custom) {
        this.custom = custom;
    }
}