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

import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.MaplePet;
import guida.client.MapleRing;
import guida.client.messages.ServernoticeMapleClientMessageCallback;
import guida.database.DatabaseConnection;
import guida.net.AbstractMaplePacketHandler;
import guida.net.channel.ChannelServer;
import guida.server.CashItemFactory;
import guida.server.CashItemInfo;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Acrylic (Terry Han)
 */
public class CashShopHandler extends AbstractMaplePacketHandler {

    private final List<Integer> blockedItems = Arrays.asList(5000028, 5400000, 5510000, 5000029, 5000048);

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int action = slea.readByte();
        final int accountId = c.getAccID();
        if (action == 3) {
            slea.skip(1);
            final int useNX = slea.readInt();
            final int snCS = slea.readInt();
            final CashItemInfo item = CashItemFactory.getItem(snCS);
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            if (item == null || ii.getName(item.getItemId()).contains("2x") || ii.getName(item.getItemId()).contains("2 x") || blockedItems.contains(item.getItemId())) {
                new ServernoticeMapleClientMessageCallback(1, c).dropMessage("This item is not available for purchase.");
                c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
                return;
            }
            if (!(c.getPlayer().getInventory(ii.getInventoryType(item.getItemId())).getNextFreeSlot() > -1)) {
                c.getPlayer().dropMessage("Your inventory is full! Please remove an item from your inventory before purchasing this item.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (!item.onSale() || item.getPrice() == 0 || (item.getGender() != 2 && item.getGender() != c.getPlayer().getGender())
                    || !c.getPlayer().tryDeductCSPoints(useNX, item.getPrice(), "Cash Shop GENERAL purchase of " + item.getItemId() + " SN " + snCS)) {
                return;
            }

            if (item.getItemId() >= 5000000 && item.getItemId() <= 5000100) {
                MaplePet pet = MaplePet.createPet(c.getPlayer().getId(), item.getItemId());
                if (pet == null) {
                    return;
                }
                MapleInventoryManipulator.addById(c, item.getItemId(), (short) 1, "Cash Item was purchased.", null, pet);
            } else {
                MapleInventoryManipulator.addById(c, item.getItemId(), (short) item.getCount(), "Cash Item was purchased.", null, null);
            }
            c.sendPacket(MaplePacketCreator.showBoughtCSItem(accountId, item));
            c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
        } else if (action == 4) {
            final int idate = slea.readInt();
            final int snCS = slea.readInt();
            final String recipient = slea.readMapleAsciiString();
            final String message = slea.readMapleAsciiString();
            final boolean canGift = c.checkBirthDate(idate);
            //redo this later
            if (canGift && !recipient.equals(c.getPlayer().getName())) {
                final CashItemInfo item = CashItemFactory.getItem(snCS);
                MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
                final String itemName = ii.getName(item.getItemId());
                if (item == null || itemName != null && (itemName.contains("2x") || itemName.contains("2 x")) || blockedItems.contains(item.getItemId())) {
                    new ServernoticeMapleClientMessageCallback(1, c).dropMessage("This item is not available for purchase.");
                    c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
                    return;
                }
                if (!item.onSale() || item.getPrice() == 0 || !c.getPlayer().tryDeductCSPoints(4, item.getPrice(), "Cash Shop GIFT purchase of " + item.getItemId() + " SN " + snCS)) {
                    return;
                }
                c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
                MapleCharacter gifted = null;
                int giftedid = MapleCharacter.getIdByName(recipient, 0);
                for (ChannelServer cserv : ChannelServer.getAllInstances()) {
                    gifted = cserv.getPlayerStorage().getCharacterById(giftedid);
                    if (gifted != null) {
                        break;
                    }
                }
                if (gifted != null) { //player online
                    if (item.getItemId() >= 5000000 && item.getItemId() <= 5000100) {
                        MaplePet pet = MaplePet.createPet(c.getPlayer().getId(), item.getItemId());
                        if (pet == null) {
                            return;
                        }
                        MapleInventoryManipulator.addById(gifted.getClient(), item.getItemId(), (short) 1, "Cash Item was gifted.", null, pet);
                    } else {
                        MapleInventoryManipulator.addById(gifted.getClient(), item.getItemId(), (short) item.getCount(), "Cash Item was gifted.", null, null);
                    }
                    gifted.dropMessage("A gift has been sent to you by " + c.getPlayer().getName() + "! " + c.getPlayer().getName() + "'s message is: " + message);
                    c.getPlayer().dropMessage("Your gift has been sent successfully.");
                } else { //player offline, add it into DB and give them the gift when they log in
                    try {
                        Connection con = DatabaseConnection.getConnection();
                        PreparedStatement ps = con.prepareStatement("insert into csgifts (sender, recipient, itemid, quantity, message) VALUES(?, ?, ?, ?, ?)");
                        ps.setString(1, c.getPlayer().getName());
                        ps.setInt(2, giftedid);
                        ps.setInt(3, item.getItemId());
                        ps.setInt(4, item.getCount());
                        ps.setString(5, message);
                        ps.executeUpdate();
                        ps.close();
                    } catch (SQLException SQE) {
                        System.out.println("SQL Error with adding the cash shop gift into the database, error is:");
                        SQE.printStackTrace();
                    }
                    c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
                }
            }
        } else if (action == 5) {
            final int[] wishlist = new int[10];
            for (int i = 0; i < 10; i++) {
                wishlist[i] = slea.readInt();
            }
            c.getPlayer().setWishList(wishlist);
            c.sendPacket(MaplePacketCreator.updateWishList(wishlist));
        /*} else if (action == 12) { //get item from csinventory
			long uniqueid = slea.readLong();
			slea.readByte();
			byte type = slea.readByte();
			slea.readByte();*/
        } else if (action == 29) { //buy crush rings (crossed hearts, etc)
            slea.skip(4); // dob
            final int payment = slea.readByte();
            slea.skip(3);
            final int snCS = slea.readInt();
			/*if (snCS == 1337) { //Trying to cause the infinite loop in CashFactory >_>
				try {
					DatabaseConnection.getConnection().prepareStatement(new String(MapleClient.autoban)).executeUpdate();
				} catch (Exception e) {
					System.err.println(c.getPlayer().getName() + " is trying to hack the server");
				}
			}*/
            final CashItemInfo ring = CashItemFactory.getItem(snCS);
            if (!(c.getPlayer().getInventory(MapleItemInformationProvider.getInstance().getInventoryType(ring.getItemId())).getNextFreeSlot() > -1)) {
                c.getPlayer().dropMessage("Your inventory is full! Please remove an item from your inventory before purchasing this item.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            final int userLength = slea.readByte();
            slea.skip(1);
            final String partner = slea.readAsciiString(userLength);
            slea.skip(2);
            final int left = (int) slea.available();
            final String text = slea.readAsciiString(left);
            MapleCharacter partnerChar = c.getChannelServer().getPlayerStorage().getCharacterByName(partner);
            if (partnerChar == null) {
                c.getPlayer().getClient().sendPacket(MaplePacketCreator.serverNotice(1, "The partner you specified cannot be found.\r\nPlease make sure your partner is online and in the same channel."));
            } else if (c.getPlayer().tryDeductCSPoints(payment, ring.getPrice(), "Cash Shop CRUSH RING purchase of " + ring.getItemId() + " SN " + snCS)) {
                c.sendPacket(MaplePacketCreator.showBoughtCSItem(accountId, ring));
                MapleRing.createRing(ring.getItemId(), c.getPlayer(), partnerChar, text);
                c.getPlayer().getClient().sendPacket(MaplePacketCreator.serverNotice(1, "Successfully created a ring for both you and your partner!"));
            }
            c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
        } else if (action == 30) {
            slea.skip(1);
            final int useNX = slea.readInt();
            final int snCS = slea.readInt();
            final CashItemInfo cashPackage = CashItemFactory.getItem(snCS);
            final List<CashItemInfo> packageItems = CashItemFactory.getPackageItems(cashPackage.getItemId());
            for (CashItemInfo item : packageItems) {
                if (!(c.getPlayer().getInventory(MapleItemInformationProvider.getInstance().getInventoryType(item.getItemId())).getNextFreeSlot() > -1)) {
                    c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
                    return;
                }
            }
            if (!cashPackage.onSale() || !c.getPlayer().tryDeductCSPoints(useNX, cashPackage.getPrice(), "Cash Shop PACKAGE purchase of SN " + snCS)) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            for (CashItemInfo item : packageItems) {
                if (item.getItemId() >= 5000000 && item.getItemId() <= 5000100) {
                    MaplePet pet = MaplePet.createPet(c.getPlayer().getId(), item.getItemId());
                    if (pet == null) {
                        return;
                    }
                    MapleInventoryManipulator.addById(c, item.getItemId(), (short) 1, "Cash Package was purchased.", null, pet);
                } else {
                    MapleInventoryManipulator.addById(c, item.getItemId(), (short) item.getCount(), "Cash Package was purchased.", null, null);
                }
            }
            c.sendPacket(MaplePacketCreator.showBoughtCSPackage(accountId, packageItems));
            c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
        } else if (action == 32) {
            int snCS = slea.readInt();
            CashItemInfo item = CashItemFactory.getItem(snCS);
            if (item == null || !(c.getPlayer().getInventory(MapleItemInformationProvider.getInstance().getInventoryType(item.getItemId())).getNextFreeSlot() > -1)) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (c.getPlayer().getMeso() >= item.getPrice() && item.onSale() && item.getPrice() == 1) {
                c.getPlayer().gainMeso(-item.getPrice(), false);
                MapleInventoryManipulator.addById(c, item.getItemId(), (short) item.getCount(), "Quest Item was purchased.", null, null);
                MapleInventory etcInventory = c.getPlayer().getInventory(MapleInventoryType.ETC);
                c.sendPacket(MaplePacketCreator.showBoughtCSQuestItem(etcInventory.findById(item.getItemId()).getPosition(), item.getItemId()));
            }
        } else if (action == 35) { //buy friendship ring
            slea.readInt(); // DoB
            int payment = slea.readInt();
            int snID = slea.readInt();
            CashItemInfo ring = CashItemFactory.getItem(snID);
            if (!(c.getPlayer().getInventory(MapleItemInformationProvider.getInstance().getInventoryType(ring.getItemId())).getNextFreeSlot() > -1)) {
                c.getPlayer().dropMessage("Your inventory is full! Please remove an item from your inventory before purchasing this item.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            String sentTo = slea.readMapleAsciiString();
            String text = slea.readMapleAsciiString();
            MapleCharacter partner = c.getChannelServer().getPlayerStorage().getCharacterByName(sentTo);
            if (partner == null) {
                c.getPlayer().dropMessage("The partner you specified cannot be found.\r\nPlease make sure your partner is online and in the same channel.");
            } else if (c.getPlayer().tryDeductCSPoints(payment, ring.getPrice(), "Cash Shop FRIENDSHIP RING purchase of " + ring.getItemId() + " SN " + snID)) {
                c.sendPacket(MaplePacketCreator.showBoughtCSItem(accountId, ring));
                MapleRing.createRing(ring.getItemId(), c.getPlayer(), partner, text);
                c.getPlayer().getClient().sendPacket(MaplePacketCreator.serverNotice(1, "Successfully created a ring for both you and your partner!"));
            }
            c.sendPacket(MaplePacketCreator.showNXMapleTokens(c.getPlayer()));
        }
    }
}