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

import guida.client.IEquip;
import guida.client.IEquip.ScrollResult;
import guida.client.IItem;
import guida.client.ItemFlag;
import guida.client.MapleCharacter;
import guida.client.MapleClient;
import guida.client.MapleInventory;
import guida.client.MapleInventoryType;
import guida.client.SkillFactory;
import guida.net.AbstractMaplePacketHandler;
import guida.server.MapleItemInformationProvider;
import guida.tools.MaplePacketCreator;
import guida.tools.Pair;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.List;

/**
 * @author Matze
 * @author Frz
 */
public class ScrollHandler extends AbstractMaplePacketHandler {

    private final List<Integer> bannedScrolls = Arrays.asList(2040603, 2044503, 2041024, 2041025, 2044703, 2044603, 2043303, 2040303, 2040807, 2040006, 2040007, 2043103, 2043203, 2043003, 2040507, 2040506, 2044403, 2040903, 2040709, 2040710, 2040711, 2044303, 2043803, 2040403, 2044103, 2044203, 2044003, 2043703);

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final int action = slea.readInt();
        if (action <= c.getLastAction()) {
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }
        c.setLastAction(action);
        final short slot = slea.readShort();
        final short dst = slea.readShort();
        final short ws = slea.readShort();
        boolean whiteScroll = false;
        boolean legendarySpirit = false;

        if ((ws & 2) == 2) {
            whiteScroll = true;
        }

        final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
        IEquip toScroll;
        if (dst < 0) {
            toScroll = (IEquip) c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).getItem(dst);
        } else {
            legendarySpirit = true;
            toScroll = (IEquip) c.getPlayer().getInventory(MapleInventoryType.EQUIP).getItem(dst);
        }
        final int LSLevel = c.getPlayer().getSkillLevel(SkillFactory.getSkill(1003));
        final MapleInventory useInventory = c.getPlayer().getInventory(MapleInventoryType.USE);
        final IItem scroll = useInventory.getItem(slot);
        IItem wscroll = null;
        if (scroll == null) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return;
        }
        final List<Integer> scrollReqs = ii.getScrollReqs(scroll.getItemId());

        if (whiteScroll) {
            wscroll = useInventory.findById(2340000);
            if (wscroll == null || wscroll.getItemId() != 2340000) {
                whiteScroll = false;
            }
        }

        if ((scroll.getQuantity() <= 0)
                || (legendarySpirit && (LSLevel <= 0))
                || (toScroll == null)
                || (!scrollReqs.isEmpty() && !scrollReqs.contains(toScroll.getItemId()))
                || ((scroll.getItemId() != 2049100) && !ii.isCleanSlate(scroll.getItemId()) && !ii.canScroll(scroll.getItemId(), toScroll.getItemId()))
                || (ii.isCleanSlate(scroll.getItemId()) && ((toScroll.getLevel() + toScroll.getUpgradeSlots()) >= ii.getTotalUpgrades(toScroll.getItemId())))
                || (!ii.isCleanSlate(scroll.getItemId()) && (toScroll.getUpgradeSlots() < 1))
                || (ii.isSpikeScroll(scroll.getItemId()) && ((toScroll.getFlag() & ItemFlag.SPIKES.getValue()) == 2))
                || (ii.isColdProtectionScroll(scroll.getItemId()) && ((toScroll.getFlag() & ItemFlag.COLD_PROTECTION.getValue()) == 4))
                || (ii.isSnowshoe(toScroll.getItemId()) && ii.isSpikeScroll(scroll.getItemId()))
                || (!c.getPlayer().isGM() && bannedScrolls.contains(scroll.getItemId()))) {
            c.sendPacket(MaplePacketCreator.getInventoryFull());
            return;
        }
        final int scrollType = scroll.getItemId() / 100 - 20400;
        final int eqpType = toScroll.getItemId() / 10000 - 100;
        if (scrollType != 90 && scrollType != 91 && scrollType != 92 && eqpType != scrollType && !scrollReqs.contains(toScroll.getItemId())) {
            MapleCharacter player = c.getPlayer();
            //c.getPlayer().ban(c.getPlayer().getName() + " was auto banned for scroll hacking. (ToScrollID:" + toScroll.getItemId() + " ScrollID:" + scroll.getItemId() + ")");
            player.dropMessage(1, "You cannot scroll a " + ii.getName(toScroll.getItemId()) + " with a " + ii.getName(scroll.getItemId()));
            try {
                player.getClient().getChannelServer().getWorldInterface().broadcastGMMessage(player.getName(), MaplePacketCreator.serverNotice(0, c.getPlayer().getName() + " is suspected of scroll hacking. (ToScrollID:" + toScroll.getItemId() + " ScrollID:" + scroll.getItemId() + " - scrolling \"" + ii.getName(toScroll.getItemId()) + "\" with a \"" + ii.getName(scroll.getItemId()) + "\")").getBytes());
            } catch (RemoteException ex) {
                player.getClient().getChannelServer().reconnectWorld();
            }
            c.sendPacket(MaplePacketCreator.enableActions());
            return;
        }

        final Pair<ScrollResult, IItem> ret = ii.scrollEquipWithId(toScroll, scroll.getItemId(), whiteScroll);
        final ScrollResult scrollSuccess = ret.getLeft();
        final IEquip scrolled = (IEquip) ret.getRight();

        useInventory.removeItem(scroll.getPosition(), (short) 1, false);
        if (whiteScroll) {
            useInventory.removeItem(wscroll.getPosition(), (short) 1, false);
            if (wscroll.getQuantity() < 1) {
                c.sendPacket(MaplePacketCreator.clearInventoryItem(MapleInventoryType.USE, wscroll.getPosition(), false));
            } else {
                c.sendPacket(MaplePacketCreator.updateInventorySlot(MapleInventoryType.USE, wscroll));
            }
        }
        if (scrollSuccess == IEquip.ScrollResult.CURSE) {
            c.sendPacket(MaplePacketCreator.scrolledItem(scroll, toScroll, true));
            if (dst < 0) {
                c.getPlayer().getInventory(MapleInventoryType.EQUIPPED).removeItem(toScroll.getPosition());
            } else {
                c.getPlayer().getInventory(MapleInventoryType.EQUIP).removeItem(toScroll.getPosition());
            }
        } else {
            c.sendPacket(MaplePacketCreator.scrolledItem(scroll, scrolled, false));
        }
        c.getPlayer().getMap().broadcastMessage(MaplePacketCreator.getScrollEffect(c.getPlayer().getId(), scrollSuccess, legendarySpirit));

        if (scrollSuccess == IEquip.ScrollResult.SUCCESS) {
            c.getPlayer().finishAchievement(12);
        }
        // equipped item was scrolled and changed
        if (dst < 0 && (scrollSuccess == IEquip.ScrollResult.SUCCESS || scrollSuccess == IEquip.ScrollResult.CURSE)) {
            c.getPlayer().equipChanged();
        }
    }
}