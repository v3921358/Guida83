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

package guida.client.messages;

import guida.client.MapleClient;
import guida.tools.MaplePacketCreator;

public class WhisperMapleClientMessageCallback implements MessageCallback {

    private final MapleClient client;
    private final String whisperfrom;

    public WhisperMapleClientMessageCallback(String whisperfrom, MapleClient client) {
        this.whisperfrom = whisperfrom;
        this.client = client;
    }

    @Override
    public void dropMessage(String message) {
        client.sendPacket(MaplePacketCreator.getWhisper(whisperfrom, client.getChannel(), message));
    }
}