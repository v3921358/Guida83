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

package guida.net.channel.handler;

import guida.client.MapleClient;
import guida.client.MapleStat;
import guida.net.AbstractMaplePacketHandler;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Xterminator
 */
public class AutoDistributeAPHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        slea.readInt();
        slea.readInt();
        int stat = slea.readInt();
        int amount = slea.readInt();
        int stat2 = slea.readInt();
        int amount2 = slea.readInt();
        List<Pair<MapleStat, Integer>> statupdate = new ArrayList<>(2);
        c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
        if (c.getPlayer().getRemainingAp() == amount + amount2) {
            switch (stat) {
                case 64: // Str
                    if (c.getPlayer().getStr() >= 999) {
                        return;
                    }
                    c.getPlayer().setStr(c.getPlayer().getStr() + amount);
                    statupdate.add(new Pair<>(MapleStat.STR, c.getPlayer().getStr()));
                    break;
                case 128: // Dex
                    if (c.getPlayer().getDex() >= 999) {
                        return;
                    }
                    c.getPlayer().setDex(c.getPlayer().getDex() + amount);
                    statupdate.add(new Pair<>(MapleStat.DEX, c.getPlayer().getDex()));
                    break;
                case 256: // Int
                    if (c.getPlayer().getInt() >= 999) {
                        return;
                    }
                    c.getPlayer().setInt(c.getPlayer().getInt() + amount);
                    statupdate.add(new Pair<>(MapleStat.INT, c.getPlayer().getInt()));
                    break;
                case 512: // Luk
                    if (c.getPlayer().getLuk() >= 999) {
                        return;
                    }
                    c.getPlayer().setLuk(c.getPlayer().getLuk() + amount);
                    statupdate.add(new Pair<>(MapleStat.LUK, c.getPlayer().getLuk()));
                    break;
                default:
                    c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                    return;
            }
            switch (stat2) {
                case 64: // Str
                    if (c.getPlayer().getStr() >= 999) {
                        return;
                    }
                    c.getPlayer().setStr(c.getPlayer().getStr() + amount2);
                    statupdate.add(new Pair<>(MapleStat.STR, c.getPlayer().getStr()));
                    break;
                case 128: // Dex
                    if (c.getPlayer().getDex() >= 999) {
                        return;
                    }
                    c.getPlayer().setDex(c.getPlayer().getDex() + amount2);
                    statupdate.add(new Pair<>(MapleStat.DEX, c.getPlayer().getDex()));
                    break;
                case 256: // Int
                    if (c.getPlayer().getInt() >= 999) {
                        return;
                    }
                    c.getPlayer().setInt(c.getPlayer().getInt() + amount2);
                    statupdate.add(new Pair<>(MapleStat.INT, c.getPlayer().getInt()));
                    break;
                case 512: // Luk
                    if (c.getPlayer().getLuk() >= 999) {
                        return;
                    }
                    c.getPlayer().setLuk(c.getPlayer().getLuk() + amount2);
                    statupdate.add(new Pair<>(MapleStat.LUK, c.getPlayer().getLuk()));
                    break;
                default:
                    c.sendPacket(MaplePacketCreator.updatePlayerStats(MaplePacketCreator.EMPTY_STATUPDATE, true));
                    return;
            }
            c.getPlayer().setRemainingAp(c.getPlayer().getRemainingAp() - (amount + amount2));
            statupdate.add(new Pair<>(MapleStat.AVAILABLEAP, c.getPlayer().getRemainingAp()));
            c.sendPacket(MaplePacketCreator.updatePlayerStats(statupdate, true));
        }
    }
}