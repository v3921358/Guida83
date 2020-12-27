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

package guida.server.quest;

import guida.provider.MapleData;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author Matze
 */
public class MapleCustomQuestData extends MapleData implements Serializable {

    private static final long serialVersionUID = -8600005891655365066L;
    private final List<MapleCustomQuestData> children = new LinkedList<>();
    private final String name;
    private final Object data;

    public MapleCustomQuestData(String name, Object data) {
        this.name = name;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public List<MapleData> getChildren() {
        MapleData[] ret = new MapleData[children.size()];
        ret = children.toArray(ret);
        return new ArrayList<>(Arrays.asList(ret));
    }

    public MapleData resolve(String name) {
        if (name.equals(this.name)) {
            return this;
        }
        String lookup;
        String nextName;
        if (!name.contains("/")) {
            lookup = name;
            nextName = name;
        } else {
            lookup = name.substring(0, name.indexOf("/"));
            nextName = name.substring(name.indexOf("/") + 1);
        }
        for (MapleData child : children) {
            if (child.getName().equals(lookup)) {
                return child.resolve(nextName);
            }
        }
        return null;
    }

    @Override
    public MapleData getChild(String name) {
        for (MapleData child : children) {
            if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    public Object getData() {
        return data;
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    public Iterator<MapleData> iterator() {
        return getChildren().iterator();
    }

}