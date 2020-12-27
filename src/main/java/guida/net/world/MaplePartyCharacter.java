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

package guida.net.world;

import guida.client.MapleCharacter;
import guida.net.channel.ChannelServer;
import guida.server.maps.MapleDoor;

import java.awt.Point;
import java.io.Serializable;

public class MaplePartyCharacter implements Serializable {

    private static final long serialVersionUID = 6215463252132450750L;
    private final String name;
    private int id;
    private int level;
    private int channel;
    private int jobid;
    private int mapid;
    private int gender;
    private int married;
    private int doorTown = 999999999;
    private int doorTarget = 999999999;
    private Point doorPosition = new Point(0, 0);
    private boolean online;

    public MaplePartyCharacter(MapleCharacter maplechar) {
        name = maplechar.getName();
        level = maplechar.getLevel();
        channel = maplechar.getClient().getChannel();
        id = maplechar.getId();
        jobid = maplechar.getJob().getId();
        mapid = maplechar.getMapId();
        online = true;
        gender = maplechar.getGender();
        married = maplechar.isMarried();
        MapleDoor door = maplechar.getDoors()[0];
        if (door != null) {
            doorTown = door.getTown().getId();
            doorTarget = door.getTarget().getId();
            doorPosition = door.getTargetPosition();
        }
    }

    public MaplePartyCharacter() {
        name = "";
    }

    public MapleCharacter getPlayer() {
        return ChannelServer.getInstance(channel).getPlayerStorage().getCharacterById(id);
    }

    public int getLevel() {
        return level;
    }

    public int getChannel() {
        return channel;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public int getMapId() {
        return mapid;
    }

    public String getName() {
        return name;
    }

    public int getId() {
        return id;
    }

    public int getJobId() {
        return jobid;
    }

    public int getDoorTown() {
        return doorTown;
    }

    public int getDoorTarget() {
        return doorTarget;
    }

    public Point getDoorPosition() {
        return doorPosition;
    }

    public int getGender() {
        return gender;
    }

    public int isMarried() {
        return married;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (name == null ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MaplePartyCharacter other = (MaplePartyCharacter) obj;
        if (name == null) {
            return other.name == null;
        } else return name.equals(other.name);
    }
}