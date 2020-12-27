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

import guida.database.DatabaseConnection;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

public class MonsterBook {

    private final Map<Integer, Integer> cards = new LinkedHashMap<>();
    private int specialCard = 0, normalCard = 0, bookLevel = 1;

    public Map<Integer, Integer> getCards() {
        return cards;
    }

    public int getSpecialCard() {
        return specialCard;
    }

    public int getNormalCard() {
        return normalCard;
    }

    public int getBookLevel() {
        return bookLevel;
    }

    public int getCardMobID(int id) {
        return MapleItemInformationProvider.getInstance().getCardMobId(id);
    }

    public void loadCards(final int charid) throws SQLException {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("SELECT * FROM monsterbook WHERE charid = ? ORDER BY cardid ASC");
        ps.setInt(1, charid);
        ResultSet rs = ps.executeQuery();
        int cardid, level;
        MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        while (rs.next()) {
            cardid = rs.getInt("cardid");
            level = rs.getInt("level");
            if (ii.isSpecialCard(cardid)) {
                specialCard += level;
            } else {
                normalCard += level;
            }
            cards.put(cardid, level);
        }
        rs.close();
        ps.close();
        calculateLevel();
    }

    public void saveCards(final int charid) throws SQLException {
        if (cards.isEmpty()) {
            return;
        }
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con.prepareStatement("DELETE FROM monsterbook WHERE charid = ?");
        ps.setInt(1, charid);
        ps.execute();
        ps.close();
        boolean first = true;
        StringBuilder query = new StringBuilder();
        for (Entry<Integer, Integer> all : cards.entrySet()) {
            if (first) {
                query.append("INSERT INTO monsterbook(charid, cardid, level) VALUES (");
                first = false;
            } else {
                query.append(",(");
            }
            query.append(charid);
            query.append(", ");
            query.append(all.getKey()); // Card ID
            query.append(", ");
            query.append(all.getValue()); // Card level
            query.append(")");
        }
        ps = con.prepareStatement(query.toString());
        ps.execute();
        ps.close();
    }

    private void calculateLevel() {
        int size = normalCard + specialCard;
        bookLevel = 8;
        for (int i = 0; i < 8; i++) {
            if (size <= ExpTable.getBookLevel(i)) {
                bookLevel = i + 1;
                break;
            }
        }
    }

    public void checkAchievement(MapleClient c) {
        if (getTotalCards() >= 1000) {
            c.getPlayer().finishAchievement(38);
        }
    }

    public void updateCard(final MapleClient c, final int cardid) {
        c.sendPacket(MaplePacketCreator.changeCover(cardid));
    }

    public void addCard(final MapleClient c, final int cardid) {
        if (cards.containsKey(cardid)) {
            int n = cards.get(cardid) + 1;
            c.sendPacket(MaplePacketCreator.addCard(n == 6, cardid, n));
            if (n < 6) {
                c.sendPacket(MaplePacketCreator.showAnimationEffect((byte) 13));
                c.getPlayer().getMap().broadcastMessage(c.getPlayer(), MaplePacketCreator.showForeignEffect(c.getPlayer().getId(), 13), false);
                cards.remove(cardid);
                cards.put(cardid, n);
                if (MapleItemInformationProvider.getInstance().isSpecialCard(cardid)) {
                    specialCard++;
                } else {
                    normalCard++;
                }
                calculateLevel();
                checkAchievement(c);
                if (n == 5) {
                    c.getPlayer().finishAchievement(37);
                }
            }
            return;
        }
        // New card
        cards.put(cardid, 1);
        c.sendPacket(MaplePacketCreator.addCard(false, cardid, 1));
        c.sendPacket(MaplePacketCreator.showAnimationEffect((byte) 13));
        if (MapleItemInformationProvider.getInstance().isSpecialCard(cardid)) {
            specialCard++;
        } else {
            normalCard++;
        }
        calculateLevel();
        checkAchievement(c);
    }

    public int getCardLevel(int cid) {
        if (!cards.containsKey(cid)) {
            return 0;
        }
        return cards.get(cid);
    }

    public boolean isCardMaxed(int cid) {
        if (!cards.containsKey(cid)) {
            return false;
        }
        return cards.get(cid) == 5;
    }

    public int getTotalCards() {
        return specialCard + normalCard;
    }
}