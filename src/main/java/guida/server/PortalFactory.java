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

import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.server.maps.MapleGenericPortal;
import guida.server.maps.MapleMapPortal;

import java.awt.Point;

public class PortalFactory {

    private int nextDoorPortal;

    public PortalFactory() {
        nextDoorPortal = 0x80;
    }

    public MaplePortal makePortal(int type, MapleData portal) {
        MapleGenericPortal ret = null;
        if (type == MaplePortal.MAP_PORTAL) {
            ret = new MapleMapPortal();
        } else {
            ret = new MapleGenericPortal(type);
        }
        loadPortal(ret, portal);
        return ret;
    }

    private void loadPortal(MapleGenericPortal myPortal, MapleData portal) {
        myPortal.setName(DataUtil.toString(portal.getChild("pn")));
        myPortal.setTarget(DataUtil.toString(portal.getChild("tn")));
        myPortal.setTargetMapId(DataUtil.toInt(portal.getChild("tm")));
        int x = DataUtil.toInt(portal.getChild("x"));
        int y = DataUtil.toInt(portal.getChild("y"));
        myPortal.setPosition(new Point(x, y));
        String script = DataUtil.toString(portal.getChild("script"), null);
        if (script != null && script.length() == 0) {
            script = null;
        }
        myPortal.setScriptName(script);
        if (myPortal.getType() == MaplePortal.DOOR_PORTAL) {
            myPortal.setId(nextDoorPortal);
            nextDoorPortal++;
        } else {
            myPortal.setId(Integer.parseInt(portal.getName()));
        }
    }
}