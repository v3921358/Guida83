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
import guida.net.ExternalCodeTableGetter;
import guida.net.PacketProcessor;
import guida.net.SendPacketOpcode;
import guida.net.channel.ChannelServer;
import guida.scripting.maps.MapScriptManager;
import guida.scripting.portal.PortalScriptManager;
import guida.scripting.reactor.ReactorScriptManager;
import guida.server.life.MapleMonsterInformationProvider;
import guida.server.playerinteractions.MapleShopFactory;

import java.rmi.RemoteException;

public class ReloadingCommands implements Command {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ReloadingCommands.class);

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        ChannelServer cserv = c.getChannelServer();
        switch (splitted[0]) {
            case "!reloadguilds":
                try {
                    mc.dropMessage("Attempting to reload all guilds... this may take a while...");
                    cserv.getWorldInterface().clearGuilds();
                    mc.dropMessage("Guilds reloaded.");
                } catch (RemoteException re) {
                    mc.dropMessage("RemoteException occurred while attempting to reload guilds.");
                    log.error("RemoteException occurred while attempting to reload guilds.", re);
                    cserv.reconnectWorld();
                }
                break;
            case "!reloadops":
                try {
                    ExternalCodeTableGetter.populateValues(SendPacketOpcode.getDefaultProperties(), SendPacketOpcode.values());
                } catch (Exception e) {
                    log.error("Failed to reload props", e);
                }
                PacketProcessor.getProcessor().reset(PacketProcessor.Mode.CHANNELSERVER);
                mc.dropMessage("Recvops and sendops reloaded.");
                break;
            case "!reloadportals":
                PortalScriptManager.getInstance().clearScripts();
                mc.dropMessage("Portals reloaded.");
                break;
            case "!reloaddrops":
                MapleMonsterInformationProvider.getInstance().clearDrops();
                mc.dropMessage("Drops and quest drops reloaded.");
                break;
            case "!reloadreactors":
                ReactorScriptManager.getInstance().clearDrops();
                mc.dropMessage("Reactors reloaded.");
                break;
            case "!reloadshops":
                MapleShopFactory.getInstance().clear();
                mc.dropMessage("Shops reloaded.");
                break;
            case "!reloadevents":
                for (ChannelServer instance : ChannelServer.getAllInstances()) {
                    if (instance != null) {
                        instance.reloadEvents();
                    }
                }
                mc.dropMessage("Events reloaded.");
                break;
            case "!reloadmapevents":
                MapScriptManager.getInstance().clearScripts();
                mc.dropMessage("Map events reloaded.");
                break;
            case "!reloadcommands":
                CommandProcessor.getInstance().reloadCommands();
                mc.dropMessage("Commands reloaded.");
                break;
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("reloadguilds", "", "", 4),
                new CommandDefinition("reloadops", "", "", 4),
                new CommandDefinition("reloadportals", "", "", 4),
                new CommandDefinition("reloaddrops", "", "", 4),
                new CommandDefinition("reloadreactors", "", "", 4),
                new CommandDefinition("reloadshops", "", "", 4),
                new CommandDefinition("reloadevents", "", "", 4),
                new CommandDefinition("reloadmapevents", "", "", 4),
                new CommandDefinition("reloadcommands", "", "", 4)
        };
    }
}