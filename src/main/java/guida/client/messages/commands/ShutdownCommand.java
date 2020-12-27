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

package guida.client.messages.commands;

import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.CommandProcessor;
import guida.client.messages.MessageCallback;
import guida.net.channel.ChannelServer;
import guida.server.playerinteractions.HiredMerchant;

import java.util.Collection;

public class ShutdownCommand implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        if (splitted.length == 1) {
            mc.dropMessage("Please specifiy when you want the server to shut down in minutes as well.");
        } else if (splitted[0].equals("!shutdownworld")) {
            int time = Integer.parseInt(splitted[1]);
            if (time < 3) {
                mc.dropMessage("Please allow at least 3 minutes before shutting down, this ensures everything shuts down smoothly!");
                return;
            }
            CommandProcessor.forcePersisting();
            ChannelServer.getInstance(1).shutdownWorld(time);
            Collection<HiredMerchant> merchs = HiredMerchant.getAllMerchants().values();
            HiredMerchant[] hmerchs = merchs.toArray(new HiredMerchant[0]);
            for (HiredMerchant imps : hmerchs) {
                imps.closeShop();
                imps.makeAvailableAtFred();
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("shutdownworld", "[when in Minutes]", "Cleanly shuts down all channels and the loginserver of this world", 4)
        };
    }
}