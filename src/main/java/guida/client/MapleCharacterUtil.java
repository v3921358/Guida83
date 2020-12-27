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

package guida.client;

import guida.provider.MapleData;
import guida.provider.MapleDataProviderFactory;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class MapleCharacterUtil {

    private static final Pattern namePattern = Pattern.compile("[a-zA-Z0-9]{3,12}");
    private static final ArrayList<String> bannedNames = new ArrayList<>();

    private MapleCharacterUtil() {
    }

    public static boolean canCreateChar(String name, int world) {
        return isNameLegal(name) && !exist(name, world);
    }

    public static boolean exist(String name, int world) {
        return MapleCharacter.getIdByName(name, world) != -1;
    }

    public static boolean isNameLegal(String name) {
        return !(name.length() < 4 || name.length() > 12 || isBanned(name)) && namePattern.matcher(name).matches();
    }

    public static boolean isBanned(String name) {
        if (bannedNames.isEmpty()) {
            final MapleData bannedName = MapleDataProviderFactory.getDataProvider("Etc").getData("ForbiddenName.img");
            for (MapleData bname : bannedName) {
                bannedNames.add(bname.getData().toString());
            }
            bannedNames.trimToSize();
        }
        for (String bName : bannedNames) {
            if (name.toLowerCase().contains(bName)) {
                return true;
            }
        }
        return false;
    }

    public static String makeMapleReadable(String in) {
        String wui = in.replace('I', 'i');
        wui = wui.replace('l', 'L');
        wui = wui.replace("rn", "Rn");
        wui = wui.replace("vv", "Vv");
        wui = wui.replace("VV", "Vv");
        return wui;
    }
}