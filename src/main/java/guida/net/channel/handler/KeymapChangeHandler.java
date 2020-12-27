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

import guida.client.MapleClient;
import guida.client.MapleKeyBinding;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.data.input.SeekableLittleEndianAccessor;

public class KeymapChangeHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int mode = slea.readInt();
        if (mode == 0) {
            final int numChanges = slea.readInt();
            for (int i = 0; i < numChanges; i++) {
                final int key = slea.readInt();
                final int type = slea.readByte();
                final int action = slea.readInt();
                final MapleKeyBinding newbinding = new MapleKeyBinding(type, action);
                c.getPlayer().changeKeybinding(key, newbinding);
            }
        } else if (mode == 1) {
            c.getPlayer().setAutoHpPot(slea.readInt());
        } else if (mode == 2) {
            c.getPlayer().setAutoMpPot(slea.readInt());
        }
    }
}