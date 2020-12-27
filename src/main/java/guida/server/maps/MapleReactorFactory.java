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

import guida.provider.DataUtil;
import guida.provider.MapleData;
import guida.provider.MapleDataProvider;
import guida.provider.MapleDataProviderFactory;
import guida.tools.Pair;
import guida.tools.StringUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Lerk
 */
public class MapleReactorFactory {
    // private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleReactorFactory.class);

    private static final MapleDataProvider data = MapleDataProviderFactory.getDataProvider("Reactor");
    private static final Map<Integer, MapleReactorStats> reactorStats = new HashMap<>();

    public static MapleReactorStats getReactor(int rid) {
        MapleReactorStats stats = reactorStats.get(rid);
        if (stats == null) {
            int infoId = rid;
            MapleData reactorData = data.getData(StringUtil.getLeftPaddedStr(infoId + ".img", '0', 11));
            MapleData link = reactorData.resolve("info/link");
            if (link != null) {
                infoId = DataUtil.toInt(reactorData.resolve("info/link"));
                stats = reactorStats.get(infoId);
            }
            if (stats == null) {
                reactorData = data.getData(StringUtil.getLeftPaddedStr(infoId + ".img", '0', 11));
                MapleData reactorInfoData = reactorData.resolve("0/event/0");
                stats = new MapleReactorStats();

                if (reactorInfoData != null) {
                    boolean areaSet = false;
                    int i = 0;
                    while (reactorInfoData != null) {
                        Pair<Integer, Integer> reactItem = null;
                        int type = DataUtil.toInt(reactorInfoData.resolve("type"));
                        if (type == 100) { //reactor waits for item
                            reactItem = new Pair<>(DataUtil.toInt(reactorInfoData.resolve("0")), DataUtil.toInt(reactorInfoData.resolve("1")));
                            if (!areaSet) { //only set area of effect for item-triggered reactors once
                                stats.setTL(DataUtil.toPoint(reactorInfoData.resolve("lt")));
                                stats.setBR(DataUtil.toPoint(reactorInfoData.resolve("rb")));
                                areaSet = true;
                            }
                        }
                        byte nextState = (byte) DataUtil.toInt(reactorInfoData.resolve("state"));
                        stats.addState((byte) i, type, reactItem, nextState);
                        i++;
                        reactorInfoData = reactorData.resolve(i + "/event/0");
                    }
                } else { //sit there and look pretty; likely a reactor such as Zakum/Papulatus doors that shows if player can enter
                    stats.addState((byte) 0, 999, null, (byte) 0);
                }

                reactorStats.put(infoId, stats);
                if (rid != infoId) {
                    reactorStats.put(rid, stats);
                }
            } else { // stats exist at infoId but not rid; add to map
                reactorStats.put(rid, stats);
            }
        }
        return stats;
    }
}