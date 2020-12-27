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

package guida.server.playerinteractions;

import guida.client.IItem;
import guida.client.MapleCharacter;
import guida.client.messages.CommandProcessor;
import guida.server.MapleInventoryManipulator;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;

import java.util.LinkedList;
import java.util.List;

/**
 * @author Matze
 */
public class MapleTrade {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MapleTrade.class);
    private final List<IItem> items = new LinkedList<>();
    private final MapleCharacter chr;
    private final byte number;
    boolean locked = false;
    private MapleTrade partner = null;
    private List<IItem> exchangeItems;
    private int meso = 0;
    private int exchangeMeso;
    private boolean visited = false;

    public MapleTrade(byte number, MapleCharacter c) {
        chr = c;
        this.number = number;
    }

    public static void completeTrade(MapleCharacter c) {
        if (c == null || c.getTrade() == null) {
            return;
        }
        c.getTrade().lock();
        MapleTrade local = c.getTrade();
        MapleTrade partner = local.partner;
        if (partner.locked) {
            local.complete1();
            partner.complete1();
            // check for full inventories
            if (!local.fitsInInventory() || !partner.fitsInInventory()) {
                cancelTrade(c);
                c.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "There is not enough inventory space to complete the trade."));
                partner.chr.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "There is not enough inventory space to complete the trade."));
                return;
            }
            local.chr.setTradeRequested(false);
            partner.chr.setTradeRequested(false);
            local.complete2();
            partner.complete2();
            local.chr.finishAchievement(17);
            partner.chr.finishAchievement(17);
            partner.chr.setTrade(null);
            c.setTrade(null);
        }
    }

    public static void cancelTrade(MapleCharacter c) {
        c.getTrade().cancel();
        if (c.getTrade().partner != null) {
            c.getTrade().partner.cancel();
            c.getTrade().partner.chr.setTrade(null);
            c.getTrade().partner.chr.setTradeRequested(false);
        }
        c.setTrade(null);
        c.setTradeRequested(false);
    }

    public static void startTrade(MapleCharacter c) {
        if (c.getTrade() == null) {
            c.setTrade(new MapleTrade((byte) 0, c));
            c.getClient().sendPacket(MaplePacketCreator.getTradeStart(c.getClient(), c.getTrade(), (byte) 0));
        } else {
            c.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "You are already in a trade"));
        }
    }

    public static void inviteTrade(MapleCharacter c1, MapleCharacter c2) {
        if (c1.isTradeRequested() || c2 != null && c2.isTradeRequested()) {
            return;
        }
        if (c2 != null && c2.getTrade() == null) {
            c1.setTradeRequested(true);
            c2.setTradeRequested(true);
            c2.setTrade(new MapleTrade((byte) 1, c2));
            c2.getTrade().setPartner(c1.getTrade());
            c1.getTrade().setPartner(c2.getTrade());
            c2.getClient().sendPacket(MaplePacketCreator.getTradeInvite(c1));
        } else {
            c1.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "The other player is already trading with someone else."));
            cancelTrade(c1);
        }
    }

    public static void visitTrade(MapleCharacter c1, MapleCharacter c2) {
        if (c1.getTrade() != null && c1.getTrade().partner == c2.getTrade() && c2.getTrade() != null && c2.getTrade().partner == c1.getTrade() && !c1.getTrade().visited && !c2.getTrade().visited) {
            if (c1.getMap() != c2.getMap()) {
                c1.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "You are not in the same map as the trader."));
                c1.getClient().sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            c1.getTrade().visited = true;
            c2.getTrade().visited = true;
            c2.getClient().sendPacket(MaplePacketCreator.getTradePartnerAdd(c1));
            c1.getClient().sendPacket(MaplePacketCreator.getTradeStart(c1.getClient(), c1.getTrade(), (byte) 1));
        } else {
            c1.getClient().sendPacket(MaplePacketCreator.serverNotice(5, "The other player has already closed the trade"));
        }
    }

    public static void declineTrade(MapleCharacter c) {
        MapleTrade trade = c.getTrade();
        if (trade != null) {
            c.setTradeRequested(false);
            MapleCharacter other = trade.partner.chr;
            if (other != null && other.getTrade() != null) {
                other.setTradeRequested(false);
                other.getTrade().cancel();
                other.setTrade(null);
                other.getClient().sendPacket(MaplePacketCreator.serverNotice(5, c.getName() + " has declined your trade request"));
            }
            trade.cancel();
            c.setTrade(null);
        }
    }

    private int getFee(int meso) {
        int fee = 0;
        if (meso >= 100000000) {
            fee = (int) Math.round(0.06 * meso);
        } else if (meso >= 25000000) {
            fee = (int) Math.round(0.05 * meso);
        } else if (meso >= 10000000) {
            fee = (int) Math.round(0.04 * meso);
        } else if (meso >= 5000000) {
            fee = (int) Math.round(0.03 * meso);
        } else if (meso >= 1000000) {
            fee = (int) Math.round(0.018 * meso);
        } else if (meso >= 100000) {
            fee = (int) Math.round(0.008 * meso);
        }
        return fee;
    }

    public void lock() {
        locked = true;
        //chr.getClient().sendPacket(MaplePacketCreator.getTradeConfirmation()); // own side shouldn't see other side whited
        partner.chr.getClient().sendPacket(MaplePacketCreator.getTradeConfirmation());
    }

    public void complete1() {
        exchangeItems = partner.getItems();
        exchangeMeso = partner.meso;
    }

    public void complete2() {
        items.clear();
        meso = 0;
        if (exchangeItems != null) {
            for (IItem item : exchangeItems) {
                chr.getClient().sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(chr.getClient(), item, "Completed trade with " + partner.chr.getName() + ". " + chr.getName() + " received the item.", false)));
            }
            exchangeItems.clear();
        }
        if (exchangeMeso > 0) {
            chr.gainMeso(exchangeMeso - getFee(exchangeMeso), false, true, false);
        }
        // just to be on the safe side...
        exchangeMeso = 0;
        chr.getClient().sendPacket(MaplePacketCreator.getTradeCompletion(number));
    }

    public void cancel() {
        // return the things
        StringBuilder logInfo = new StringBuilder("Canceled trade ");
        if (partner != null) {
            logInfo.append("with ");
            logInfo.append(partner.chr.getName());
        }
        logInfo.append(". ");
        logInfo.append(chr.getName());
        logInfo.append(" received the item.");
        for (IItem item : items) {
            chr.getClient().sendPacket(MaplePacketCreator.modifyInventory(true, MapleInventoryManipulator.addByItem(chr.getClient(), item, logInfo.toString(), false)));
        }
        if (meso > 0) {
            chr.gainMeso(meso, false, true, false);
        }
        // just to be on the safe side...
        meso = 0;
        exchangeMeso = 0;
        if (exchangeItems != null) {
            exchangeItems.clear();
        }
        chr.getClient().sendPacket(MaplePacketCreator.getTradeCancel(number));
    }

    public boolean isLocked() {
        return locked;
    }

    public int getMeso() {
        return meso;
    }

    public void setMeso(int meso) {
        if (locked) {
            throw new RuntimeException("Trade is locked.");
        }
        if (meso < 0) {
            log.info("[h4x] {} Trying to trade < 0 meso", chr.getName());
            return;
        }
        if (chr.getMeso() >= meso) {
            if (this.meso + meso < 0) {
                if (chr.getMeso() >= 2147483647 - meso) {
                    meso = 2147483647 - meso;
                    chr.getClient().sendPacket(MaplePacketCreator.serverNotice(1, "Only " + meso + " mesos were added to the trade because adding more would cause a technical glitch."));
                    if (partner != null) {
                        partner.chr.getClient().sendPacket(MaplePacketCreator.serverNotice(1, "Only " + meso + " mesos were added to the trade because adding more would cause a technical glitch."));
                    }
                }
            }
            chr.gainMeso(-meso, false, true, false);
            this.meso += meso;
            chr.getClient().sendPacket(MaplePacketCreator.getTradeMesoSet((byte) 0, this.meso));
            if (partner != null) {
                partner.chr.getClient().sendPacket(MaplePacketCreator.getTradeMesoSet((byte) 1, this.meso));
            }
        }
    }

    public boolean canAddItem(IItem item) {
        return !(MapleItemInformationProvider.getInstance().canHaveOnlyOne(item.getItemId()) && partner.chr.haveItem(item.getItemId(), 1, true, false));
    }

    public void addItem(IItem item) {
        items.add(item);
        chr.getClient().sendPacket(MaplePacketCreator.getTradeItemAdd((byte) 0, item));
        if (partner != null) {
            partner.chr.getClient().sendPacket(MaplePacketCreator.getTradeItemAdd((byte) 1, item));
        }
    }

    public void chat(String message) {
        if (chr.isGM() && CommandProcessor.getInstance().processCommand(chr.getClient(), message)) {
            return;
        }
        chr.getClient().sendPacket(MaplePacketCreator.getPlayerShopChat(chr, message, true));
        if (partner != null) {
            partner.chr.getClient().sendPacket(MaplePacketCreator.getPlayerShopChat(chr, message, false));
        }
    }

    public MapleTrade getPartner() {
        return partner;
    }

    public void setPartner(MapleTrade partner) {
        if (locked) {
            throw new RuntimeException("Trade is locked.");
        }
        this.partner = partner;
    }

    public MapleCharacter getChr() {
        return chr;
    }

    public List<IItem> getItems() {
        return new LinkedList<>(items);
    }

    public boolean fitsInInventory() {
        return MapleInventoryManipulator.canHold(chr.getClient(), exchangeItems);
    }

    public byte getNextFreeSlot() {
        boolean[] slotsUsed = {false, false, false, false, false, false, false, false, false};
        for (IItem ii : items) {
            slotsUsed[ii.getPosition() - 1] = true;
        }

        for (int x = 0; x < slotsUsed.length; x++) {
            if (!slotsUsed[x]) {
                return (byte) (x + 1);
            }
        }
        return (byte) -1;
    }
}