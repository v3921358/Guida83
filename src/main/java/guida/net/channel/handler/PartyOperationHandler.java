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
import guida.net.AbstractMaplePacketHandler;
import guida.net.channel.ChannelServer;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.remote.WorldChannelInterface;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;

public class PartyOperationHandler extends AbstractMaplePacketHandler {

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        int operation = slea.readByte();
        MapleCharacter player = c.getPlayer();
        WorldChannelInterface wci = c.getChannelServer().getWorldInterface();
        MapleParty party = player.getParty();
        MaplePartyCharacter partyplayer = new MaplePartyCharacter(player);

        switch (operation) {
            case 1 -> { // create
                if (c.getPlayer().getParty() == null) {
                    try {
                        party = wci.createParty(partyplayer);
                        player.setParty(party);
                    } catch (RemoteException e) {
                        c.getChannelServer().reconnectWorld();
                    }
                    c.sendPacket(MaplePacketCreator.partyCreated(c.getPlayer().getPartyId()));
                } else {
                    c.sendPacket(MaplePacketCreator.serverNotice(5, "You can't create a party as you are already in one"));
                }
                break;
            }
            case 2 -> { // leave
                if (party != null) {
                    try {
                        if (partyplayer.equals(party.getLeader())) { // disband
                            wci.updateParty(party.getId(), PartyOperation.DISBAND, partyplayer);
                            if (player.getEventInstance() != null) {
                                player.getEventInstance().disbandParty();
                            }
                        } else {
                            wci.updateParty(party.getId(), PartyOperation.LEAVE, partyplayer);
                            if (player.getEventInstance() != null) {
                                player.getEventInstance().leftParty(player);
                            }
                        }
                    } catch (RemoteException e) {
                        c.getChannelServer().reconnectWorld();
                    }
                    player.setParty(null);
                }
                break;
            }
            case 3 -> { // accept invitation
                int partyid = slea.readInt();
                if (!c.getPlayer().getPartyInvited()) {
                    return;
                }
                if (c.getPlayer().getParty() == null) {
                    try {
                        party = wci.getParty(partyid);
                        if (party != null && party.getLeader().isOnline()) {
                            if (party.getMembers().size() < 6) {
                                wci.updateParty(party.getId(), PartyOperation.JOIN, partyplayer);
                                player.receivePartyMemberHP();
                                player.updatePartyMemberHP();
                            } else {
                                c.sendPacket(MaplePacketCreator.partyStatusMessage(17));
                            }
                        } else {
                            c.sendPacket(MaplePacketCreator.serverNotice(5, "The party you are trying to join does not exist"));
                        }
                        c.getPlayer().setPartyInvited(false);
                    } catch (RemoteException e) {
                        c.getChannelServer().reconnectWorld();
                    }
                } else {
                    c.sendPacket(MaplePacketCreator.serverNotice(5, "You can't join the party as you are already in one"));
                }
                break;
            }
            case 4 -> { // invite
                //TODO store pending invitations and check against them
                String name = slea.readMapleAsciiString();
                MapleCharacter invited = c.getChannelServer().getPlayerStorage().getCharacterByName(name);
                if (invited != null) {
                    if (invited.getParty() == null) {
                        if (party != null && party.getMembers().size() < 6) {
                            invited.setPartyInvited(true);
                            invited.getClient().sendPacket(MaplePacketCreator.partyInvite(player));
                        }
                    } else {
                        c.sendPacket(MaplePacketCreator.partyStatusMessage(16));
                    }
                } else {
                    c.sendPacket(MaplePacketCreator.partyStatusMessage(18));
                }
                break;
            }
            case 5 -> { // expel
                int cid = slea.readInt();
                if (party != null && partyplayer.equals(party.getLeader())) {
                    MaplePartyCharacter expelled = party.getMemberById(cid);
                    if (expelled != null && !expelled.equals(party.getLeader())) {
                        try {
                            wci.updateParty(party.getId(), PartyOperation.EXPEL, expelled);
                            if (player.getEventInstance() != null) {
                                /*if leader wants to boot someone, then the whole party gets expelled
								TODO: Find an easier way to get the character behind a MaplePartyCharacter
								possibly remove just the expel.*/
                                if (expelled.isOnline()) {
                                    MapleCharacter expellee = ChannelServer.getInstance(expelled.getChannel()).getPlayerStorage().getCharacterById(expelled.getId());
                                    if (expellee != null && expellee.getEventInstance() != null && expellee.getEventInstance().getName().equals(player.getEventInstance().getName())) {
                                        player.getEventInstance().disbandParty();
                                    }
                                }
                            }
                        } catch (RemoteException e) {
                            c.getChannelServer().reconnectWorld();
                        }
                    }
                }
                break;
            }
            case 6 -> { //change leader
                int nlid = slea.readInt();
                if (party != null) {
                    MaplePartyCharacter newleader = party.getMemberById(nlid);
                    if (newleader != null && partyplayer.equals(party.getLeader()) && newleader.isOnline()) {
                        try {
                            party.setLeader(newleader);
                            wci.updateParty(party.getId(), PartyOperation.CHANGE_LEADER, newleader);
                        } catch (RemoteException re) {
                            c.getChannelServer().reconnectWorld();
                        }
                    }
                }
                break;
            }
        }
    }
}