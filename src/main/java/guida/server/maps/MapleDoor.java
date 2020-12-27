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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.net.world.MaplePartyCharacter;
import guida.server.MaplePortal;
import guida.tools.MaplePacketCreator;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Matze
 */
public class MapleDoor extends AbstractMapleMapObject {

    private final MapleCharacter owner;
    private final MapleMap town;
    private final MapleMap target;
    private final Point targetPosition;
    private MaplePortal townPortal;

    public MapleDoor(MapleCharacter owner, Point targetPosition) {
        super();
        this.owner = owner;
        target = owner.getMap();
        this.targetPosition = targetPosition;
        setPosition(this.targetPosition);
        town = target.getReturnMap();
        townPortal = getFreePortal();
    }

    public MapleDoor(MapleDoor origDoor) {
        super();
        owner = origDoor.owner;
        town = origDoor.town;
        townPortal = origDoor.townPortal;
        target = origDoor.target;
        targetPosition = origDoor.targetPosition;
        townPortal = origDoor.townPortal;
        setPosition(townPortal.getPosition());
    }

    private MaplePortal getFreePortal() {
        List<MaplePortal> freePortals = new ArrayList<>();

        for (MaplePortal port : town.getPortals()) {
            if (port.getType() == MaplePortal.DOOR_PORTAL) {
                freePortals.add(port);
            }
        }
        freePortals.sort((o1, o2) -> Integer.compare(o1.getId(), o2.getId()));
        for (MapleMapObject obj : town.getAllDoors()) {
            MapleDoor door = (MapleDoor) obj;
            if (door.owner.getParty() != null && owner.getParty() != null && owner.getParty().containsMember(new MaplePartyCharacter(door.owner))) {
                freePortals.remove(door.townPortal);
            }
        }
        if (freePortals.iterator().hasNext()) {
            return freePortals.iterator().next();
        }
        return null;
    }

    public void sendSpawnData(MapleClient client) {
        if (target.getId() == client.getPlayer().getMapId() || owner == client.getPlayer() && owner.getParty() == null) {
            client.sendPacket(MaplePacketCreator.spawnDoor(owner.getId(), town.getId() == client.getPlayer().getMapId() ? townPortal.getPosition() : targetPosition, true));
            if (owner.getParty() != null && (owner == client.getPlayer() || owner.getParty().containsMember(new MaplePartyCharacter(client.getPlayer())))) {
                client.sendPacket(MaplePacketCreator.partyPortal(town.getId(), target.getId(), targetPosition));
            }
            client.sendPacket(MaplePacketCreator.spawnPortal(town.getId(), target.getId(), targetPosition));
        }
    }

    public void sendDestroyData(MapleClient client) {
        if (client.getPlayer() != null && (target.getId() == client.getPlayer().getMapId() || owner == client.getPlayer() || owner.getParty() != null && owner.getParty().containsMember(new MaplePartyCharacter(client.getPlayer())))) {
            if (owner.getParty() != null && (owner == client.getPlayer() || owner.getParty().containsMember(new MaplePartyCharacter(client.getPlayer())))) {
                client.sendPacket(MaplePacketCreator.partyPortal(999999999, 999999999, new Point(-1, -1)));
            }
            client.sendPacket(MaplePacketCreator.removeDoor(owner.getId(), false));
            client.sendPacket(MaplePacketCreator.removeDoor(owner.getId(), true));
        }
    }

    public void warp(MapleCharacter chr, boolean toTown) {
        if (chr == owner || owner.getParty() != null && owner.getParty().containsMember(new MaplePartyCharacter(chr))) {
            if (!toTown) {
                if (!chr.getMap().canExit() && !chr.isGM()) {
                    chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "You are not allowed to exit this map."));
                    chr.getClient().sendPacket(MaplePacketCreator.enableActions());
                    return;
                }
                if (!target.canEnter() && !chr.isGM()) {
                    chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "You are not allowed to enter " + target.getStreetName() + " : " + target.getMapName()));
                    chr.getClient().sendPacket(MaplePacketCreator.enableActions());
                    return;
                }
                chr.changeMap(target, targetPosition);
            } else {
                if (!chr.getMap().canExit() && !chr.isGM()) {
                    chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "You are not allowed to exit this map."));
                    chr.getClient().sendPacket(MaplePacketCreator.enableActions());
                    return;
                }
                if (!town.canEnter() && !chr.isGM()) {
                    chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "You are not allowed to enter " + town.getStreetName() + " : " + town.getMapName()));
                    chr.getClient().sendPacket(MaplePacketCreator.enableActions());
                    return;
                }
                chr.changeMap(town, townPortal);
            }
        } else {
            chr.getClient().sendPacket(MaplePacketCreator.enableActions());
        }
    }

    public MapleCharacter getOwner() {
        return owner;
    }

    public MapleMap getTown() {
        return town;
    }

    public MaplePortal getTownPortal() {
        return townPortal;
    }

    public MapleMap getTarget() {
        return target;
    }

    public Point getTargetPosition() {
        return targetPosition;
    }

    @Override
    public MapleMapObjectType getType() {
        return MapleMapObjectType.DOOR;
    }
}