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

package guida.scripting.reactor;

import guida.client.MapleClient;
import guida.database.DatabaseConnection;
import guida.scripting.AbstractScriptManager;
import guida.server.life.MapleMonsterInformationProvider.DropEntry;
import guida.server.maps.MapleReactor;

import javax.script.Invocable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * @author Lerk
 */
public class ReactorScriptManager extends AbstractScriptManager {

    private static final ReactorScriptManager instance = new ReactorScriptManager();
    private final Map<Integer, List<DropEntry>> drops = new HashMap<>();

    public synchronized static ReactorScriptManager getInstance() {
        return instance;
    }

    public void act(MapleClient c, MapleReactor reactor) {
        try {
            final ReactorActionManager rm = new ReactorActionManager(c, reactor);

            final Invocable iv = getInvocable("reactor/" + reactor.getId() + ".js", c);
            if (iv == null) {
                return;
            }
            engine.put("rm", rm);
            final ReactorScript rs = iv.getInterface(ReactorScript.class);
            rs.act();
        } catch (Exception e) {
            log.error("Error executing reactor script.", e);
        }
    }

    public List<DropEntry> getDrops(int rid) {
        List<DropEntry> ret = drops.get(rid);
        if (ret == null) {
            ret = new LinkedList<>();
            try {
                final Connection con = DatabaseConnection.getConnection();
                final PreparedStatement ps = con.prepareStatement("SELECT itemid, chance FROM reactordrops WHERE reactorid = ? AND chance >= 0");
                ps.setInt(1, rid);
                final ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    ret.add(new DropEntry(rs.getInt("itemid"), rs.getInt("chance")));
                }
                rs.close();
                ps.close();
            } catch (Exception e) {
                log.error("Could not retrieve drops for reactor " + rid, e);
            }
            drops.put(rid, ret);
        }
        return ret;
    }

    public void clearDrops() {
        drops.clear();
    }
}