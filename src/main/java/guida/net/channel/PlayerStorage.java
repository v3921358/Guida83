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

package guida.net.channel;

import guida.client.MapleCharacter;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class PlayerStorage implements IPlayerStorage {

    final Map<String, MapleCharacter> nameToChar = new LinkedHashMap<>();
    final Map<Integer, MapleCharacter> idToChar = new LinkedHashMap<>();

    public void registerPlayer(MapleCharacter chr) {
        nameToChar.put(chr.getName().toLowerCase(), chr);
        idToChar.put(chr.getId(), chr);
    }

    public void deregisterPlayer(MapleCharacter chr) {
        nameToChar.remove(chr.getName().toLowerCase());
        idToChar.remove(chr.getId());
    }

    public MapleCharacter getCharacterByName(String name) {
        return nameToChar.get(name.toLowerCase());
    }

    public MapleCharacter getCharacterById(int id) {
        return idToChar.get(id);
    }

    public Collection<MapleCharacter> getAllCharacters() {
        return nameToChar.values();
    }
}