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

import guida.client.IItem;
import guida.client.ItemFlag;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventoryType;
import guida.client.messages.CommandProcessor;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.server.maps.MapleMapObject;
import guida.server.maps.MapleMapObjectType;
import guida.server.playerinteractions.AbstractPlayerStore;
import guida.server.playerinteractions.HiredMerchant;
import guida.server.playerinteractions.IMaplePlayerShop;
import guida.server.playerinteractions.MaplePlayerShop;
import guida.server.playerinteractions.MaplePlayerShopItem;
import guida.server.playerinteractions.MapleTrade;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.util.Arrays;
import java.util.Collections;

/**
 * @author Matze
 */
public class PlayerInteractionHandler extends AbstractMaplePacketHandler {

    private static final String psConfirmGreeting = "How's it going? This is the Stop-Using-Regular-Shops-To-Hold-Spots-In-The-FM-For-Hired-Merchants! Test.";
    private static final String psConfirm1 = "#eTERMS OF SERVICE, PART 1#n\r\n\r\n1. The Staff are not responsible for the loss of items when using a shop.\r\n2. The use of a shop is not required.\r\n3. Loss of items due to use of a shop will not be dealt by the staff.\r\n4. Shops may cause you to lose your items.\r\n\r\n#eEND#n\r\n\r\nDo you agree? If so, solve:";
    private static final String psConfirm2 = "#eTERMS OF SERVICE, PART 2#n\r\n\r\n#e#r1. You are not allowed to exploit glitches in the system (if any) to duplicate items.#k#n\r\n\r\n#eEND#n\r\n\r\nDo you agree? If so, solve:";

    private enum Action {

        CREATE(0x00),
        INVITE(0x02),
        DECLINE(0x03),
        VISIT(0x04),
        CHAT(0x06),
        EXIT(0x0A),
        OPEN(0x0B),
        SET_ITEMS(0x0F),
        SET_MESO(0x10),
        CONFIRM(0x11),
        ADD_ITEM(0x16),
        BUY(0x15),
        REMOVE_ITEM(0x1B), //slot(byte) bundlecount(short)
        BAN_PLAYER(0x1C),
        PUT_ITEM(0x21),
        MERCHANT_BUY(0x22),
        TAKE_ITEM_BACK(0x26),
        MAINTENANCE_OFF(0x27),
        MERCHANT_ORGANIZE(0x28),
        CLOSE_MERCHANT(0x29);

        final byte code;

        Action(int code) {
            this.code = (byte) code;
        }

        public byte getCode() {
            return code;
        }
    }

    public void handlePacket(SeekableLittleEndianAccessor slea, final MapleClient c) {
        c.getPlayer().resetAfkTimer();
        byte mode = slea.readByte();
        if (mode == Action.CREATE.getCode()) {
            byte createType = slea.readByte();
            if (createType == 3) { // trade
                if (!c.getPlayer().isAlive()) {
                    c.getPlayer().dropMessage(1, "You must be alive in order to start a trade!");
                    return;
                }
                MapleTrade.startTrade(c.getPlayer());
            } else if (createType == 4 || createType == 5) { // shop
                if (c.getChannelServer().isShutdown()) {
                    c.getPlayer().dropMessage(1, "You cannot open a shop while the server is shutting down.");
                    return;
                }
                if (!c.getPlayer().getMap().getMapObjectsInRange(c.getPlayer().getPosition(), 23000, Arrays.asList(MapleMapObjectType.HIRED_MERCHANT, MapleMapObjectType.SHOP)).isEmpty() || !c.getPlayer().getMap().allowShops()) {
                    c.getPlayer().dropMessage(1, "You cannot open a shop here.");
                    return;
                }
                if (c.getPlayer().hasMerchant()) {
                    c.getPlayer().dropMessage(1, "You already have a shop or merchant open.");
                    return;
                }
                if (createType == 5 && !c.getPlayer().passedMerchTest()) {
                    c.getPlayer().dropMessage(1, "You did not pass the merchant test. Stupid packet editor.");
                    return;
                }
                if (!c.getPlayer().getMap().getMapObjectsInRange(c.getPlayer().getPosition(), 19500, Arrays.asList(MapleMapObjectType.SHOP, MapleMapObjectType.HIRED_MERCHANT)).isEmpty()) {
                    c.getPlayer().dropMessage(1, "You may not establish a store here.");
                    return;
                }
                String desc = slea.readMapleAsciiString();
                slea.skip(3);
                int itemId = slea.readInt();
                if (!c.getPlayer().haveItem(itemId, 1, false, false)) {
                    c.sendPacket(MaplePacketCreator.enableActions());
                    c.getPlayer().dropMessage(1, "You do not have the permit.");
                    return;
                }
                IMaplePlayerShop shop;
                if (createType == 4) {
                    shop = new MaplePlayerShop(c.getPlayer(), itemId, desc);
                } else {
                    shop = new HiredMerchant(c.getPlayer(), itemId, desc);
                }
                c.getPlayer().setPlayerShop(shop);
                if (createType == 5) {
                    c.sendPacket(MaplePacketCreator.getMaplePlayerStore(c.getPlayer(), true));
                } else {
                    Runnable r = () -> c.getPlayer().getClient().sendPacket(MaplePacketCreator.getMaplePlayerStore(c.getPlayer(), true));
                    Runnable fr = () -> {
                        c.getPlayer().setHasMerchant(false);
                        c.getPlayer().setPlayerShop(null);
                    };
                    c.getPlayer().callConfirmationNpc(r, fr, 9030000, 1, psConfirmGreeting, psConfirm1, psConfirm2);
                }
                c.getPlayer().setPassMerchTest(false);
            }
        } else if (mode == Action.INVITE.getCode()) {
            int otherPlayer = slea.readInt();
            MapleCharacter otherChar = c.getPlayer().getMap().getCharacterById(otherPlayer);
            MapleTrade.inviteTrade(c.getPlayer(), otherChar);
        } else if (mode == Action.DECLINE.getCode()) {
            MapleTrade.declineTrade(c.getPlayer());
        } else if (mode == Action.VISIT.getCode()) { // we will ignore the trade oids for now
            if (c.hasPacketLog()) {
                c.writePacketLog("PlayerInteraction: VISIT - IsTradeNull? " + (c.getPlayer().getTrade() == null));
            }
            if (c.getPlayer().getTrade() != null && c.getPlayer().getTrade().getPartner() != null) {
                if (c.hasPacketLog()) {
                    c.writePacketLog(" Trade not null - partner is " + c.getPlayer().getTrade().getPartner().getChr().getName() + " with " + c.getPlayer().getTrade().getMeso() + " mesos and " + c.getPlayer().getTrade().getPartner().getMeso() + " mesos on partner\r\n");
                }
                MapleTrade.visitTrade(c.getPlayer(), c.getPlayer().getTrade().getPartner().getChr());
            } else {
                int oid = slea.readInt();
                MapleMapObject ob = c.getPlayer().getMap().getMapObject(oid);
                if (ob instanceof IMaplePlayerShop && c.getPlayer().getPlayerShop() == null) {
                    IMaplePlayerShop ips = (IMaplePlayerShop) ob;
                    if (ips.getShopType() == 1) {
                        HiredMerchant merchant = (HiredMerchant) ips;
                        if (merchant.isOwner(c.getPlayer())) {
                            merchant.setOpen(false);
                            merchant.broadcastToVisitors(MaplePacketCreator.shopErrorMessage(0x0D, 1));
                            merchant.removeAllVisitors((byte) 16, (byte) 0);
                            c.getPlayer().setPlayerShop(ips);
                            c.sendPacket(MaplePacketCreator.getMaplePlayerStore(c.getPlayer(), false));
                            return;
                        } else if (!merchant.isOpen()) {
                            c.getPlayer().dropMessage(1, "This shop is in maintenance, please come by later.");
                            return;
                        }
                    } else if (ips.getShopType() == 2) {
                        if (((MaplePlayerShop) ips).isBanned(c.getPlayer().getName())) {
                            c.getPlayer().dropMessage(1, "You have been banned from this store.");
                            return;
                        }
                    }
                    if (ips.getFreeSlot() == -1) {
                        c.getPlayer().dropMessage(1, "This shop has reached it's maximum capacity, please come by later.");
                        return;
                    }
                    c.getPlayer().setPlayerShop(ips);
                    ips.addVisitor(c.getPlayer());
                    c.sendPacket(MaplePacketCreator.getMaplePlayerStore(c.getPlayer(), false));
                }
            }
        } else if (mode == Action.CHAT.getCode()) { // chat lol
            if (c.getPlayer().getTrade() != null) {
                c.getPlayer().getTrade().chat(slea.readMapleAsciiString());
            } else if (c.getPlayer().getPlayerShop() != null) {
                IMaplePlayerShop ips = c.getPlayer().getPlayerShop();
                String message = slea.readMapleAsciiString();
                CommandProcessor.getInstance().processCommand(c, message);
                ips.broadcastToVisitors(MaplePacketCreator.shopChat(c.getPlayer().getName() + " : " + message, ips.isOwner(c.getPlayer()) ? 0 : ips.getVisitorSlot(c.getPlayer()) + 1));
            }
        } else if (mode == Action.EXIT.getCode()) {
            if (c.hasPacketLog()) {
                c.writePacketLog("PlayerInteraction: EXIT - IsTradeNull? " + (c.getPlayer().getTrade() == null));
            }
            if (c.getPlayer().getTrade() != null) {
                MapleTrade.cancelTrade(c.getPlayer());
            } else {
                IMaplePlayerShop ips = c.getPlayer().getPlayerShop();
                if (ips != null) {
                    c.getPlayer().setPlayerShop(null);
                    if (ips.isOwner(c.getPlayer())) {
                        if (ips.getShopType() == 2) {
                            for (MaplePlayerShopItem items : ips.getItems()) {
                                if (items.getBundles() > 0) {
                                    IItem item = items.getItem();
                                    short x = item.getQuantity();
                                    short y = (short) (x * items.getBundles());
                                    if (y < 1) {
                                        y = 1;
                                    }
                                    item.setQuantity(y);
                                    if (MapleInventoryManipulator.canHold(c, Collections.singletonList(item))) {
                                        if (!MapleInventoryManipulator.addByItem(c, item, "returned from shop", true).isEmpty()) {
                                            items.setBundles((short) 0);
                                        }

                                        try {
                                            ips.updateItem(items);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }
                            }
                            ips.removeAllVisitors(3, 1);
                            ips.closeShop();
                            c.getPlayer().setHasMerchant(false);
                        } else if (ips.getShopType() == 1) {
                            c.sendPacket(MaplePacketCreator.shopVisitorLeave(0));
                        }
                    } else {
                        ips.removeVisitor(c.getPlayer());
                    }
                    c.getPlayer().setPlayerShop(null);
                }
            }
        } else if (mode == Action.OPEN.getCode()) {
            if (c.getPlayer().getMap().allowShops()) {
                IMaplePlayerShop shop = c.getPlayer().getPlayerShop();
                if (shop != null && !shop.isOwner(c.getPlayer())) {
                    return;
                }
                if (shop != null && shop.isOwner(c.getPlayer())) {
                    if (shop.getShopType() == 1) {
                        if (c.getPlayer().hasHiredMerchantTicket()) {
                            HiredMerchant merchant = (HiredMerchant) shop;
                            if (c.getPlayer().getMap().getMapObjectsInRange(c.getPlayer().getPosition(), 23000, Arrays.asList(MapleMapObjectType.HIRED_MERCHANT, MapleMapObjectType.SHOP)).isEmpty()) {
                                c.getPlayer().getMap().addMapObject((AbstractPlayerStore) shop);
                                c.getPlayer().setHasMerchant(true);
                                merchant.setOpen(true);
                                merchant.spawned();
                                c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.spawnHiredMerchant(merchant));
                                c.getPlayer().setPlayerShop(null);
                            } else {
                                for (MaplePlayerShopItem items : merchant.getItems()) {
                                    if (items.getBundles() > 0) {
                                        IItem item = items.getItem();
                                        short x = item.getQuantity();
                                        short y = (short) (x * items.getBundles());
                                        if (y < 1) {
                                            y = 1;
                                        }
                                        item.setQuantity(y);
                                        if (MapleInventoryManipulator.canHold(c, Collections.singletonList(item))) {
                                            if (!MapleInventoryManipulator.addByItem(c, item, "returned from shop", true).isEmpty()) {
                                                items.setBundles((short) 0);
                                            }
                                            try {
                                                merchant.updateItem(items);
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                                c.getPlayer().dropMessage(1, "Someone else has already opened their shop. Items have been returned to your inventory. If your inventory was full, items will be transferred to Fredrick on the next Server Check.");
                                merchant.cancelShop();
                                c.getPlayer().setPlayerShop(null);
                                c.getPlayer().setHasMerchant(false);
                            }
                        } else {
                            c.disconnect();
                        }
                    } else if (shop.getShopType() == 2) {
                        if (c.getPlayer().hasMerchant() || !c.getPlayer().getMap().getMapObjectsInRange(c.getPlayer().getPosition(), 23000, Arrays.asList(MapleMapObjectType.HIRED_MERCHANT, MapleMapObjectType.SHOP)).isEmpty() || !c.getPlayer().getMap().allowShops()) {
                            for (MaplePlayerShopItem items : shop.getItems()) {
                                if (items.getBundles() > 0) {
                                    IItem item = items.getItem();
                                    short x = item.getQuantity();
                                    short y = (short) (x * items.getBundles());
                                    if (y < 1) {
                                        y = 1;
                                    }
                                    item.setQuantity(y);
                                    if (!MapleInventoryManipulator.addByItem(c, item, "", true).isEmpty()) {
                                        items.setBundles((short) 0);
                                    }
                                }
                            }
                            shop.closeShop();
                            return;
                        }
                        if (c.getPlayer().hasPlayerShopTicket()) {
                            c.getPlayer().getMap().addMapObject((AbstractPlayerStore) shop);
                            c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.sendPlayerShopBox(c.getPlayer()));
                            c.getPlayer().setHasMerchant(true); //so they cant open a player shop and merchant
                            shop.spawned();
                            //c.getPlayer().saveToDB(true);
                        } else {
                            c.disconnect();
                        }
                    }
                    slea.readByte();
                }
            }
        } else if (mode == Action.SET_MESO.getCode()) {
            int x = slea.readInt();
            if (x > 0 && c.getPlayer().getTrade() != null) {
                c.getPlayer().getTrade().setMeso(x);
            }
        } else if (mode == Action.SET_ITEMS.getCode()) {
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            MapleInventoryType ivType = MapleInventoryType.getByType(slea.readByte());
            IItem item = c.getPlayer().getInventory(ivType).getItem(slea.readShort());
            if (item == null) {
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (item.isByGM() && c.getPlayer().isGMLegit()) {
                c.getPlayer().dropMessage(6, "As you are a GM-funded legit, you may not release this GM-created item.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            short quantity = slea.readShort();
            byte targetSlot = slea.readByte();
            if (c.getPlayer().getTrade() != null) {
                if (quantity <= item.getQuantity() && quantity >= 0 || ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId())) {
                    if (!c.getChannelServer().allowUndroppablesDrop() && (ii.isDropRestricted(item.getItemId()) && (item.getFlag() & ItemFlag.TRADE_ONCE.getValue()) != 16 || (item.getFlag() & ItemFlag.UNTRADEABLE.getValue()) == 8)) { // ensure that undroppable items do not make it to the trade window
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    if (!c.getPlayer().getTrade().canAddItem(item)) {
                        c.getPlayer().dropMessage(1, "The other party already has this One Of A Kind item.");
                        c.sendPacket(MaplePacketCreator.enableActions());
                        return;
                    }
                    IItem tradeItem = item.copy();
                    if (ii.isThrowingStar(item.getItemId()) || ii.isShootingBullet(item.getItemId())) {
                        tradeItem.setQuantity(item.getQuantity());
                        MapleInventoryManipulator.removeFromSlot(c, ivType, item.getPosition(), item.getQuantity(), true);
                    } else {
                        tradeItem.setQuantity(quantity);
                        MapleInventoryManipulator.removeFromSlot(c, ivType, item.getPosition(), quantity, true);
                    }
                    tradeItem.setPosition(targetSlot);
                    c.getPlayer().getTrade().addItem(tradeItem);
                } /* else if (quantity < 0) {
                }*/
            }
        } else if (mode == Action.CONFIRM.getCode()) {
            MapleTrade.completeTrade(c.getPlayer());
        } else if (mode == Action.ADD_ITEM.getCode() || mode == Action.PUT_ITEM.getCode()) {
            /* 6F 00
			   1F
			   02 08
			   00 01
			   00 01
			   00 A0 86 01 00 */
            MapleInventoryType type = MapleInventoryType.getByType(slea.readByte());
            short slot = slea.readShort(); // 02
            short bundles = slea.readShort(); // 0C 00
            short perBundle = slea.readShort(); // 01 00
            int price = slea.readInt(); // 01 00 A0 86
            IItem ivItem = c.getPlayer().getInventory(type).getItem(slot);
            MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
            if (price <= 0 || bundles <= 0 || perBundle <= 0 || ivItem == null) {
                return;
            }
            if (ivItem.isByGM() && c.getPlayer().isGMLegit()) {
                c.getPlayer().dropMessage(6, "As you are a GM-funded legit, you may not release this GM-created item.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            if (ii.isDropRestricted(ivItem.getItemId()) && (ivItem.getFlag() & ItemFlag.TRADE_ONCE.getValue()) != 16 || (ivItem.getFlag() & ItemFlag.UNTRADEABLE.getValue()) == 8) {
                c.getPlayer().dropMessage(1, "You may not put this item into the shop.");
                return;
            }
            IItem sellItem = ivItem.copy();
            if (!c.getPlayer().haveItem(sellItem.getItemId(), perBundle * bundles, true, false)) {
                return;
            }
            sellItem.setQuantity(perBundle);
            MaplePlayerShopItem item = new MaplePlayerShopItem(sellItem, bundles, price);
            IMaplePlayerShop shop = c.getPlayer().getPlayerShop();
            if (shop != null && shop.isOwner(c.getPlayer())) {
                if (ivItem != null && ivItem.getQuantity() >= bundles * perBundle) {
                    if (ii.isThrowingStar(ivItem.getItemId()) || ii.isShootingBullet(ivItem.getItemId())) {
                        MapleInventoryManipulator.removeFromSlot(c, type, slot, ivItem.getQuantity(), true);
                    } else {
                        MapleInventoryManipulator.removeFromSlot(c, type, slot, (short) (bundles * perBundle), true);
                    }
                    try {
                        shop.addItem(item);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    c.sendPacket(MaplePacketCreator.shopItemUpdate(shop));
                }
            }
        } else if (mode == Action.TAKE_ITEM_BACK.getCode() || mode == Action.REMOVE_ITEM.getCode()) {
            int slot = slea.readShort();
            IMaplePlayerShop shop = c.getPlayer().getPlayerShop();
            if (shop != null && shop.isOwner(c.getPlayer())) {
                if (slot < 0 || slot >= shop.getItems().size()) {
                    c.disconnect();
                    return;
                }
                MaplePlayerShopItem item = shop.getItems().get(slot);
                if (item.getBundles() > 0) {
                    IItem iitem = item.getItem();
                    iitem.setQuantity(item.getBundles());
                    MapleInventoryManipulator.addByItem(c, iitem, "returned from shop", true);
                }
                item.setBundles((short) 0);
                shop.removeFromSlot(slot);
                try {
                    shop.updateItem(item);
                } catch (Exception e) {
                    System.out.println("Error with updating shopitem after the owner removed from shop.");
                    e.printStackTrace();
                }
                c.sendPacket(MaplePacketCreator.shopItemUpdate(shop));
            }
        } else if (mode == Action.BUY.getCode() || mode == Action.MERCHANT_BUY.getCode()) {
            int item = slea.readByte();
            short quantity = slea.readShort();
            IMaplePlayerShop shop = c.getPlayer().getPlayerShop();
            if (shop != null && shop.getItems().get(item).getBundles() >= quantity && quantity > 0 && !shop.isOwner(c.getPlayer())) {
                shop.buy(c, item, quantity);
                shop.broadcastToVisitors(MaplePacketCreator.shopItemUpdate(shop));
            }
        } else if (mode == Action.CLOSE_MERCHANT.getCode()) {
            IMaplePlayerShop merchant = c.getPlayer().getPlayerShop();
            if (merchant != null && merchant.getShopType() == 1 && merchant.isOwner(c.getPlayer())) {
                for (MaplePlayerShopItem items : merchant.getItems()) {
                    if (items.getBundles() > 0) {
                        IItem item = items.getItem();
                        short x = item.getQuantity();
                        short y = (short) (x * items.getBundles());
                        if (y < 1) {
                            y = 1;
                        }
                        item.setQuantity(y);
                        if (MapleInventoryManipulator.canHold(c, Collections.singletonList(item))) {
                            if (!MapleInventoryManipulator.addByItem(c, item, "returned from shop", true).isEmpty()) {
                                items.setBundles((short) 0);
                            }

                            try {
                                merchant.updateItem(items);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
                c.sendPacket(MaplePacketCreator.shopErrorMessage(0x10, 0));
                merchant.closeShop();
                c.getPlayer().setPlayerShop(null);
                c.getPlayer().setHasMerchant(false);
                //c.getPlayer().saveToDB(true);
            }
        } else if (mode == Action.MAINTENANCE_OFF.getCode()) {
            HiredMerchant merchant = (HiredMerchant) c.getPlayer().getPlayerShop();
            if (merchant != null && merchant.isOwner(c.getPlayer())) {
                merchant.setOpen(true);
                //c.getPlayer().saveToDB(true);
            }
        } else if (mode == Action.BAN_PLAYER.getCode()) {
            slea.skip(1);
            IMaplePlayerShop imps = c.getPlayer().getPlayerShop();
            if (imps != null && imps.isOwner(c.getPlayer())) {
                ((MaplePlayerShop) imps).banPlayer(slea.readMapleAsciiString());
            }
        } else if (mode == Action.MERCHANT_ORGANIZE.getCode()) {
            IMaplePlayerShop imps = c.getPlayer().getPlayerShop();
            if (imps != null && imps.isOwner(c.getPlayer())) {
                for (int i = 0; i < imps.getItems().size(); i++) {
                    if (imps.getItems().get(i).getBundles() == 0) {
                        imps.removeFromSlot(i);
                    }
                }
                c.sendPacket(MaplePacketCreator.shopItemUpdate(imps));
            }
        }
    }
}