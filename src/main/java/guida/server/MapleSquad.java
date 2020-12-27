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

package guida.server;

import guida.client.MapleCharacter;
import guida.net.MaplePacket;
import guida.tools.MaplePacketCreator;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Danny
 */
public class MapleSquad {

    private final MapleCharacter leader;
    private final List<MapleCharacter> members = new LinkedList<>();
    private final List<MapleCharacter> bannedMembers = new LinkedList<>();
    private final List<Integer> memberIds = new LinkedList<>();
    private final Map<Integer, Integer> disconnectedMembers = new HashMap<>();
    private final int ch;
    private boolean groupRewarps = true;
    private int status = 0;

    public MapleSquad(int ch, MapleCharacter leader) {
        this.leader = leader;
        members.add(leader);
        memberIds.add(leader.getId());
        this.ch = ch;
        status = 1;
    }

    public MapleSquad(int ch, MapleCharacter leader, boolean groupWarp) {
        this.leader = leader;
        members.add(leader);
        memberIds.add(leader.getId());
        this.ch = ch;
        status = 1;
        groupRewarps = groupWarp;
    }

    public MapleCharacter getLeader() {
        return leader;
    }

    public boolean containsMember(MapleCharacter member) {
        for (MapleCharacter mmbr : members) {
            if (mmbr.getId() == member.getId()) {
                return true;
            }
        }
        return false;
    }

    public boolean isBanned(MapleCharacter member) {
        for (MapleCharacter banned : bannedMembers) {
            if (banned.getId() == member.getId()) {
                return true;
            }
        }
        return false;
    }

    public boolean canBeReWarped(int characterId, MapleSquadType squad) {
        if (squad == MapleSquadType.ZAKUM) {
            //1 individual, 5 group
            if (groupRewarps) {
                return getTotalRewarps(characterId) < 5;
            } else {
                return getTotalRewarps(characterId) < 1;
            }
        } else if (squad == MapleSquadType.HORNTAIL) {
            return getTotalRewarps(-1) < 15;
        }
        return false;
    }

    public boolean isDisconnected(int id) {
        return disconnectedMembers.containsKey(id);
    }

    public void removeDisconnected(int id) {
        disconnectedMembers.remove(id);
    }

    public int getTotalRewarps(int characterid) {
        if (groupRewarps) {
            int ret = 0;
            for (Integer i : disconnectedMembers.values()) {
                ret += i;
            }
            return ret;
        }
        if (disconnectedMembers.containsKey(characterid)) {
            return disconnectedMembers.get(characterid);
        }
        return 0;
    }

    public void addRewarp(int characterid) {
        if (disconnectedMembers.containsKey(characterid)) {
            int cur = disconnectedMembers.get(characterid);
            disconnectedMembers.remove(characterid);
            disconnectedMembers.put(characterid, cur + 1);
        } else {
            disconnectedMembers.put(characterid, 1);
        }
    }

    public void playerDisconnected(int characterId) {
        if (!disconnectedMembers.containsKey(characterId)) {
            disconnectedMembers.put(characterId, 0);
        }
    }

    public boolean isGroupRewarp() {
        return groupRewarps;
    }

    public void setRewarpType(boolean group) {
        groupRewarps = group;
    }

    public List<MapleCharacter> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public List<Integer> getMemberIds() {
        return Collections.unmodifiableList(memberIds);
    }

    public int getSquadSize() {
        return members.size();
    }

    public boolean addMember(MapleCharacter member) {
        if (isBanned(member)) {
            return false;
        } else {
            members.add(member);
            memberIds.add(member.getId());
            MaplePacket packet = MaplePacketCreator.serverNotice(5, member.getName() + " has joined the fight!");
            leader.getClient().sendPacket(packet);
            return true;
        }
    }

    public void banMember(MapleCharacter member, boolean ban) {
        int index = -1;
        for (MapleCharacter mmbr : members) {
            if (mmbr.getId() == member.getId()) {
                index = members.indexOf(mmbr);
            }
        }
        members.remove(index);
        if (ban) {
            bannedMembers.add(member);
        }
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getStatus() {
        return status;
    }

    public void clear() {
        members.clear();
        bannedMembers.clear();
    }

    public boolean matches(MapleSquad other) {
        return other.ch == ch && other.leader.getId() == leader.getId();
    }
}