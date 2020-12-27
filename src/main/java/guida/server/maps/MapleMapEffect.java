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

import guida.client.MapleClient;
import guida.net.MaplePacket;
import guida.tools.MaplePacketCreator;

public class MapleMapEffect {

    private final String msg;
    private final int itemId;
    private boolean active = true;

    public MapleMapEffect(String msg, int itemId) {
        this.msg = msg;
        this.itemId = itemId;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public MaplePacket makeDestroyData() {
        return MaplePacketCreator.removeMapEffect();
    }

    public MaplePacket makeStartData() {
        return MaplePacketCreator.startMapEffect(msg, itemId, active);
    }

    public void sendStartData(MapleClient client) {
        client.sendPacket(makeStartData());
    }
}