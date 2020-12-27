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

package guida.server.fourthjobquests;

import guida.client.MapleCharacter;
import guida.client.MapleJob;
import guida.client.messages.ServernoticeMapleClientMessageCallback;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.tools.MaplePacketCreator;
import guida.tools.ValueHolder;

import java.util.Collection;

/**
 * @author AngelSL
 */
public class FourthJobQuestsPortalHandler {

    public enum FourthJobQuests implements ValueHolder<String> {

        RUSH("s4rush"),
        BERSERK("s4berserk");
        private final String name;

        FourthJobQuests(String Newname) {
            name = Newname;
        }

        @Override
        public String getValue() {
            return name;
        }
    }

    private FourthJobQuestsPortalHandler() {
    }

    public static boolean handlePortal(String name, MapleCharacter c) {
        ServernoticeMapleClientMessageCallback snmcmc = new ServernoticeMapleClientMessageCallback(5, c.getClient());
        if (name.equals(FourthJobQuests.RUSH.getValue())) {
            if (!checkPartyLeader(c) && !checkRush(c)) {
                snmcmc.dropMessage("You step into the portal, but it swiftly kicks you out.");
                c.getClient().sendPacket(MaplePacketCreator.enableActions());
            }
            if (!checkPartyLeader(c) && checkRush(c)) {
                snmcmc.dropMessage("You're not the party leader.");
                c.getClient().sendPacket(MaplePacketCreator.enableActions());
                return true;
            }
            if (!checkRush(c)) {
                snmcmc.dropMessage("Someone in your party is not a 4th Job warrior.");
                c.getClient().sendPacket(MaplePacketCreator.enableActions());
                return true;
            }
            c.getClient().getChannelServer().getEventSM().getEventManager("4jrush").startInstance(c.getParty(), c.getMap());
            return true;
        } else if (name.equals(FourthJobQuests.BERSERK.getValue())) {
            if (!checkBerserk(c)) {
                snmcmc.dropMessage("The portal to the Forgotten Shrine is locked");
                c.getClient().sendPacket(MaplePacketCreator.enableActions());
                return true;
            }
            c.getClient().getChannelServer().getEventSM().getEventManager("4jberserk").startInstance(c.getParty(), c.getMap());
            return true;
        }
        return false;
    }

    private static boolean checkRush(MapleCharacter c) {
        MapleParty CsParty = c.getParty();
        if (CsParty == null) {
            return false;
        }
        Collection<MaplePartyCharacter> CsPartyMembers = CsParty.getMembers();
        for (MaplePartyCharacter mpc : CsPartyMembers) {
            if (!MapleJob.getById(mpc.getJobId()).isA(MapleJob.WARRIOR)) {
                return false;
            }
            if (!(MapleJob.getById(mpc.getJobId()).isA(MapleJob.HERO) || MapleJob.getById(mpc.getJobId()).isA(MapleJob.PALADIN) || MapleJob.getById(mpc.getJobId()).isA(MapleJob.DARKKNIGHT))) {
                return false;
            }
        }
        return true;
    }

    private static boolean checkPartyLeader(MapleCharacter c) {
        if (c == null || c.getParty() == null || c.getParty().getLeader() == null) {
            return false;
        }
        return c.getParty().getLeader().getId() == c.getId();
    }

    private static boolean checkBerserk(MapleCharacter c) {
        return c.haveItem(4031475, 1, false, false);
    }
}