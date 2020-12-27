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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapleParty implements Serializable {

    private static final long serialVersionUID = 9179541993413738569L;
    private final ArrayList<MaplePartyCharacter> members = new ArrayList<>(6);
    private final Map<String, String> variables = new HashMap<>();
    private MaplePartyCharacter leader;
    private int id;
    private int CP;
    private int team;
    private int totalCP;
    private MapleParty CPQEnemy = null;

    public MapleParty(int id, MaplePartyCharacter chrfor) {
        leader = chrfor;
        members.add(leader);
        this.id = id;
    }

    public boolean containsMember(MaplePartyCharacter member) {
        return members.contains(member);
    }

    public void addMember(MaplePartyCharacter member) {
        members.add(member);
    }

    public void removeMember(MaplePartyCharacter member) {
        members.remove(member);
    }

    public void updateMember(MaplePartyCharacter member) {
        for (int i = 0; i < members.size(); i++) {
            MaplePartyCharacter chr = members.get(i);
            if (chr.equals(member)) {
                members.set(i, member);
            }
        }
    }

    public MaplePartyCharacter getMemberById(int id) {
        for (MaplePartyCharacter chr : members) {
            if (chr.getId() == id) {
                return chr;
            }
        }
        return null;
    }

    public Collection<MaplePartyCharacter> getMembers() {
        return Collections.unmodifiableList(members);
    }

    public List<MapleCharacter> getPartyMembers(MapleParty party) {
        if (party == null) {
            return null;
        }
        List<MapleCharacter> chars = new ArrayList<>(6);
        for (ChannelServer channel : ChannelServer.getAllInstances()) {
            for (MapleCharacter chrs : channel.getPartyMembers(party)) {
                if (chrs != null) {
                    chars.add(chrs);
                }
            }
        }
        return chars;
    }

    public List<MapleCharacter> getPartyMembersOnMap(MapleCharacter member) {
        List<MapleCharacter> chars = new ArrayList<>(6);
        for (MaplePartyCharacter mpc : members) {
            if (mpc.isOnline() && mpc.getMapId() == member.getMapId() && mpc.getPlayer() != null && !mpc.getPlayer().inCS() && !mpc.getPlayer().inMTS()) {
                chars.add(mpc.getPlayer());
            }
        }
        return chars;
    }

    public List<MapleCharacter> getPartyMembers() {
        return getPartyMembers(this);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getCP() {
        return CP;
    }

    public int getTeam() {
        return team;
    }

    public int getTotalCP() {
        return totalCP;
    }

    public void setCP(int cp) {
        CP = cp;
    }

    public void setTeam(int team) {
        this.team = team;
    }

    public void setTotalCP(int totalcp) {
        totalCP = totalcp;
    }

    public void setEnemy(MapleParty CPQEnemy) {
        this.CPQEnemy = CPQEnemy;
    }

    public MapleParty getEnemy() {
        return CPQEnemy;
    }

    public MaplePartyCharacter getLeader() {
        return leader;
    }

    public void setLeader(MaplePartyCharacter nLeader) {
        leader = nLeader;
    }

    public void addVar(String name, String val) {
        variables.put(name, val);
    }

    public String getVar(String name) {
        if (variables.containsKey(name)) {
            return variables.get(name);
        }
        return "";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + id;
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
        final MapleParty other = (MapleParty) obj;
        return id == other.id;
    }
}