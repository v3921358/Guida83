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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.messages.Command;
import guida.client.messages.CommandDefinition;
import guida.client.messages.MessageCallback;
import guida.server.TimerManager;
import guida.server.life.MapleLifeFactory;
import guida.server.life.MapleNPC;
import guida.server.maps.MapleMap;
import guida.server.maps.MapleMapObject;
import guida.tools.MaplePacketCreator;

import java.awt.Point;
import java.util.List;

public class NPCSpawningCommands implements Command {

    @Override
    public void execute(MapleClient c, MessageCallback mc, String[] splitted) throws Exception {
        switch (splitted[0]) {
            case "!npc" -> {
                int npcId = Integer.parseInt(splitted[1]);
                MapleNPC npc = MapleLifeFactory.getNPC(npcId);
                if (npc != null && !npc.getName().equals("MISSINGNO")) {
                    npc.setPosition(c.getPlayer().getPosition());
                    npc.setCy(c.getPlayer().getPosition().y);
                    npc.setRx0(c.getPlayer().getPosition().x + 50);
                    npc.setRx1(c.getPlayer().getPosition().x - 50);
                    npc.setFh(c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition()).getId());
                    npc.setCustom(true);
                    c.getPlayer().getMap().addMapObject(npc);
                    c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.spawnNPC(npc));
                } else {
                    mc.dropMessage("You have entered an invalid NPC ID.");
                }
                break;
            }
            case "!removenpcs" -> {
                MapleCharacter player = c.getPlayer();
                List<MapleMapObject> npcs = player.getMap().getAllNPCs();
                for (MapleMapObject npcmo : npcs) {
                    MapleNPC npc = (MapleNPC) npcmo;
                    if (npc.isCustom()) {
                        player.getMap().removeMapObject(npc.getObjectId());
                        player.getMap().broadcastMessage(MaplePacketCreator.npcShowHide(npc.getObjectId(), false), npc.getPosition());
                    }
                }
            }
            case "!mynpcpos" -> {
                Point pos = c.getPlayer().getPosition();
                mc.dropMessage("CY: " + pos.y + " | RX0: " + (pos.x + 50) + " | RX1: " + (pos.x - 50) + " | FH: " + c.getPlayer().getMap().getFootholds().findBelow(pos).getId());
            }
            case "!jqevent" -> {
                String map = splitted[1];
                int minutes = Integer.parseInt(splitted[2]);
                int npcid = 0;
                switch (map) {
                    case "fitness":
                        npcid = 2042003;
                        break;
                    case "chimney":
                        npcid = 9300012;
                        break;
                    case "clone1":
                        npcid = 9300014;
                        break;
                    case "clone2":
                        npcid = 9300007;
                        break;
                    case "clone3":
                        npcid = 9300008;
                        break;
                }
                if (npcid == 0) {
                    mc.dropMessage("This JQ Map is not available, please pick either fitness, chimney, clone1, clone2 or clone3.");
                    return;
                }
                final MapleNPC npc = MapleLifeFactory.getNPC(npcid);
                npc.setPosition(c.getPlayer().getPosition());
                npc.setCy(c.getPlayer().getPosition().y);
                npc.setRx0(c.getPlayer().getPosition().x + 50);
                npc.setRx1(c.getPlayer().getPosition().x - 50);
                npc.setFh(c.getPlayer().getMap().getFootholds().findBelow(c.getPlayer().getPosition()).getId());
                npc.setCustom(true);
                c.getPlayer().getMap().addMapObject(npc);
                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.spawnNPC(npc));
                c.getChannelServer().getWorldInterface().broadcastMessage(c.getPlayer().getName(), MaplePacketCreator.serverNotice(6, "The NPC " + npc.getName() + " will be in " + c.getPlayer().getMap().getMapName() + " for " + minutes + "minute(s). Please talk to it to be warped to the JQ Event").getBytes());
                final MapleMap targetMap = c.getPlayer().getMap();
                TimerManager.getInstance().schedule(() -> {
                    for (MapleMapObject npcmo : targetMap.getAllNPCs()) {
                        MapleNPC fnpc = (MapleNPC) npcmo;
                        if (fnpc.isCustom() && fnpc.getId() == npc.getId()) {
                            targetMap.removeMapObject(fnpc.getObjectId());
                            targetMap.broadcastMessage(MaplePacketCreator.npcShowHide(npc.getObjectId(), false), npc.getPosition());
                        }
                    }
                }, minutes * 60 * 1000L);
                break;
            }
        }
    }

    @Override
    public CommandDefinition[] getDefinition() {
        return new CommandDefinition[] {
                new CommandDefinition("npc", "npcid", "Spawns the npc with the given id at the player position", 5),
                new CommandDefinition("removenpcs", "", "Removes all custom spawned npcs from the map - requires reentering the map", 5),
                new CommandDefinition("mynpcpos", "", "Gets the info for making an npc", 5),
                new CommandDefinition("jqevent", "<chimney, fitness>", "Spawns an NPC for a JQ Event", 5)
        };
    }
}