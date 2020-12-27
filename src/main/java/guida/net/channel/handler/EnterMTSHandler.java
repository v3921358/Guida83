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

import guida.client.Equip;
import guida.client.Item;
import guida.client.MapleClient;
import guida.database.DatabaseConnection;
import guida.net.AbstractMaplePacketHandler;
import guida.net.world.remote.WorldChannelInterface;
import guida.server.MTSItemInfo;
import guida.server.playerinteractions.MapleTrade;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class EnterMTSHandler extends AbstractMaplePacketHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EnterMTSHandler.class);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        if (c.getChannelServer().allowMTS()) {
            if (c.getPlayer().getLevel() >= 50) {
                if (c.getPlayer().getTrade() != null) {
                    MapleTrade.cancelTrade(c.getPlayer());
                }
                try {
                    WorldChannelInterface wci = c.getChannelServer().getWorldInterface();
                    wci.addBuffsToStorage(c.getPlayer().getId(), c.getPlayer().getAllBuffs());
                } catch (RemoteException e) {
                    c.getChannelServer().reconnectWorld();
                }
                c.getPlayer().stopAllTimers();
                c.getPlayer().getMap().removePlayer(c.getPlayer());
                c.sendPacket(MaplePacketCreator.warpMTS(c));
                c.getPlayer().setInMTS(true);
                c.sendPacket(MaplePacketCreator.enableMTS());
                c.sendPacket(MaplePacketCreator.MTSWantedListingOver(0, 0));
                c.sendPacket(MaplePacketCreator.showMTSCash(c.getPlayer()));

                List<MTSItemInfo> items = new ArrayList<>();
                int pages = 0;
                try {
                    Connection con = DatabaseConnection.getConnection();
                    PreparedStatement ps = con.prepareStatement("UPDATE `mts_items` SET `transfer` = 1 WHERE `sell_ends` < CURRENT_TIMESTAMP()");
                    ps.executeUpdate();
                    ps = con.prepareStatement("SELECT * FROM mts_items WHERE tab = 1 AND transfer = 0 ORDER BY id DESC LIMIT ?, 16");
                    ps.setInt(1, 0);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        if (rs.getInt("type") != 1) {
                            Item i = new Item(rs.getInt("itemid"), (byte) 0, rs.getShort("quantity"), rs.getShort("flag"));
                            i.setOwner(rs.getString("owner"));
                            i.setExpiration(rs.getTimestamp("ExpireDate"));
                            items.add(new MTSItemInfo(i, rs.getInt("price")/* + 100 + (int) (rs.getInt("price") * 0.1)*/, rs.getInt("id"), rs.getInt("seller"), rs.getString("sellername"), rs.getTimestamp("sell_ends")));
                        } else {
                            Equip equip = new Equip(rs.getInt("itemid"), (byte) rs.getInt("position"), -1);
                            equip.setOwner(rs.getString("owner"));
                            equip.setQuantity((short) 1);
                            equip.setAcc((short) rs.getInt("acc"));
                            equip.setAvoid((short) rs.getInt("avoid"));
                            equip.setDex((short) rs.getInt("dex"));
                            equip.setHands((short) rs.getInt("hands"));
                            equip.setHp((short) rs.getInt("hp"));
                            equip.setInt((short) rs.getInt("int"));
                            equip.setJump((short) rs.getInt("jump"));
                            equip.setLuk((short) rs.getInt("luk"));
                            equip.setMatk((short) rs.getInt("matk"));
                            equip.setMdef((short) rs.getInt("mdef"));
                            equip.setMp((short) rs.getInt("mp"));
                            equip.setSpeed((short) rs.getInt("speed"));
                            equip.setStr((short) rs.getInt("str"));
                            equip.setWatk((short) rs.getInt("watk"));
                            equip.setWdef((short) rs.getInt("wdef"));
                            equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
                            equip.setFlag((short) rs.getInt("flag"));
                            equip.setExpiration(rs.getTimestamp("ExpireDate"));
                            equip.setLevel((byte) rs.getInt("level"));
                            items.add(new MTSItemInfo(equip, rs.getInt("price")/* + 100 + (int) (rs.getInt("price") * 0.1)*/, rs.getInt("id"), rs.getInt("seller"), rs.getString("sellername"), rs.getTimestamp("sell_ends")));
                        }
                    }
                    rs.close();
                    ps.close();

                    ps = con.prepareStatement("SELECT COUNT(*) FROM mts_items WHERE transfer = 0");
                    rs = ps.executeQuery();

                    if (rs.next()) {
                        pages = (int) Math.ceil((double) rs.getInt(1) / 16);
                    }
                    rs.close();
                    ps.close();
                } catch (SQLException e) {
                    log.error("Err1: " + e);
                }

                c.sendPacket(MaplePacketCreator.sendMTS(items, 1, 0, 0, pages));
                c.sendPacket(MaplePacketCreator.transferInventory(getTransfer(c.getPlayer().getId())));
                c.sendPacket(MaplePacketCreator.notYetSoldInv(getNotYetSold(c.getPlayer().getId())));
                c.getPlayer().saveToDB(true);
            } else {
                c.getPlayer().dropMessage("Sorry, but you must be at least level 50 to use the MTS.");
                c.sendPacket(MaplePacketCreator.enableActions());
            }
        } else {
            c.getPlayer().dropMessage("Sorry, but the MTS is unavailable right now.");
            c.sendPacket(MaplePacketCreator.enableActions());
        }
    }

    public List<MTSItemInfo> getNotYetSold(int cid) {
        List<MTSItemInfo> items = new ArrayList<>();
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps;
        ResultSet rs;
        try {
            ps = con.prepareStatement("SELECT * FROM mts_items WHERE seller = ? AND transfer = 0 ORDER BY id DESC");
            ps.setInt(1, cid);

            rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getInt("type") != 1) {
                    Item i = new Item(rs.getInt("itemid"), (byte) 0, rs.getShort("quantity"), rs.getShort("flag"));
                    i.setOwner(rs.getString("owner"));
                    i.setExpiration(rs.getTimestamp("ExpireDate"));
                    items.add(new MTSItemInfo(i, rs.getInt("price"), rs.getInt("id"), rs.getInt("seller"), rs.getString("sellername"), rs.getTimestamp("sell_ends")));
                } else {
                    Equip equip = new Equip(rs.getInt("itemid"), (byte) rs.getInt("position"), -1);
                    equip.setOwner(rs.getString("owner"));
                    equip.setQuantity((short) 1);
                    equip.setAcc((short) rs.getInt("acc"));
                    equip.setAvoid((short) rs.getInt("avoid"));
                    equip.setDex((short) rs.getInt("dex"));
                    equip.setHands((short) rs.getInt("hands"));
                    equip.setHp((short) rs.getInt("hp"));
                    equip.setInt((short) rs.getInt("int"));
                    equip.setJump((short) rs.getInt("jump"));
                    equip.setLuk((short) rs.getInt("luk"));
                    equip.setMatk((short) rs.getInt("matk"));
                    equip.setMdef((short) rs.getInt("mdef"));
                    equip.setMp((short) rs.getInt("mp"));
                    equip.setSpeed((short) rs.getInt("speed"));
                    equip.setStr((short) rs.getInt("str"));
                    equip.setWatk((short) rs.getInt("watk"));
                    equip.setWdef((short) rs.getInt("wdef"));
                    equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
                    equip.setFlag((short) rs.getInt("flag"));
                    equip.setExpiration(rs.getTimestamp("ExpireDate"));
                    equip.setLevel((byte) rs.getInt("level"));
                    items.add(new MTSItemInfo(equip, rs.getInt("price"), rs.getInt("id"), rs.getInt("seller"), rs.getString("sellername"), rs.getTimestamp("sell_ends")));
                }
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            log.error("Err8: " + e);
        }
        return items;
    }

    public List<MTSItemInfo> getTransfer(int cid) {
        List<MTSItemInfo> items = new ArrayList<>();
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps;
        ResultSet rs;
        try {
            ps = con.prepareStatement("SELECT * FROM mts_items WHERE transfer = 1 AND seller = ? ORDER BY id DESC");
            ps.setInt(1, cid);

            rs = ps.executeQuery();
            while (rs.next()) {
                if (rs.getInt("type") != 1) {
                    Item i = new Item(rs.getInt("itemid"), (byte) 0, rs.getShort("quantity"), rs.getShort("flag"));
                    i.setOwner(rs.getString("owner"));
                    i.setExpiration(rs.getTimestamp("ExpireDate"));
                    items.add(new MTSItemInfo(i, rs.getInt("price"), rs.getInt("id"), rs.getInt("seller"), rs.getString("sellername"), rs.getTimestamp("sell_ends")));
                } else {
                    Equip equip = new Equip(rs.getInt("itemid"), (byte) rs.getInt("position"), -1);
                    equip.setOwner(rs.getString("owner"));
                    equip.setQuantity((short) 1);
                    equip.setAcc((short) rs.getInt("acc"));
                    equip.setAvoid((short) rs.getInt("avoid"));
                    equip.setDex((short) rs.getInt("dex"));
                    equip.setHands((short) rs.getInt("hands"));
                    equip.setHp((short) rs.getInt("hp"));
                    equip.setInt((short) rs.getInt("int"));
                    equip.setJump((short) rs.getInt("jump"));
                    equip.setLuk((short) rs.getInt("luk"));
                    equip.setMatk((short) rs.getInt("matk"));
                    equip.setMdef((short) rs.getInt("mdef"));
                    equip.setMp((short) rs.getInt("mp"));
                    equip.setSpeed((short) rs.getInt("speed"));
                    equip.setStr((short) rs.getInt("str"));
                    equip.setWatk((short) rs.getInt("watk"));
                    equip.setWdef((short) rs.getInt("wdef"));
                    equip.setUpgradeSlots((byte) rs.getInt("upgradeslots"));
                    equip.setFlag((short) rs.getInt("flag"));
                    equip.setLevel((byte) rs.getInt("level"));
                    equip.setExpiration(rs.getTimestamp("ExpireDate"));
                    items.add(new MTSItemInfo(equip, rs.getInt("price"), rs.getInt("id"), rs.getInt("seller"), rs.getString("sellername"), rs.getTimestamp("sell_ends")));
                }
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            log.error("Err7: " + e);
        }
        return items;
    }
}