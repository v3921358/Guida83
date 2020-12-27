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

package guida.net.channel.remote;

import guida.client.BuddyList.BuddyAddResult;
import guida.client.BuddyList.BuddyOperation;
import guida.net.MaplePacket;
import guida.net.world.MapleMessenger;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.guild.MapleGuildSummary;
import guida.net.world.remote.WorldChannelCommonOperations;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * @author Matze
 */
public interface ChannelWorldInterface extends Remote, WorldChannelCommonOperations {

    void setChannelId(int id) throws RemoteException;

    int getChannelId() throws RemoteException;

    String getIP() throws RemoteException;

    boolean isConnected(int characterId) throws RemoteException;

    int getConnected() throws RemoteException;

    int getLocation(String name) throws RemoteException;

    void updateParty(MapleParty party, PartyOperation operation, MaplePartyCharacter target) throws RemoteException;

    void partyChat(MapleParty party, String chattext, String namefrom) throws RemoteException;

    boolean isAvailable() throws RemoteException;

    BuddyAddResult requestBuddyAdd(String addName, int channelFrom, int cidFrom, String nameFrom) throws RemoteException;

    void buddyChanged(int cid, int cidFrom, String name, int channel, BuddyOperation op) throws RemoteException;

    int[] multiBuddyFind(int charIdFrom, int[] characterIds) throws RemoteException;

    void sendPacket(List<Integer> targetIds, MaplePacket packet, int exception) throws RemoteException;

    void setGuildAndRank(int cid, int guildid, int rank) throws RemoteException;

    void setOfflineGuildStatus(int guildid, byte guildrank, int cid) throws RemoteException;

    void setGuildAndRank(List<Integer> cids, int guildid, int rank, int exception) throws RemoteException;

    void reloadGuildCharacters() throws RemoteException;

    void changeEmblem(int gid, List<Integer> affectedPlayers, MapleGuildSummary mgs) throws RemoteException;

    String listGMs() throws RemoteException;

    void addMessengerPlayer(MapleMessenger messenger, String namefrom, int fromchannel, int position) throws RemoteException;

    void removeMessengerPlayer(MapleMessenger messenger, int position) throws RemoteException;

    void messengerChat(MapleMessenger messenger, String chattext, String namefrom) throws RemoteException;

    void declineChat(String target, String namefrom) throws RemoteException;

    void updateMessenger(MapleMessenger messenger, String namefrom, int position, int fromchannel) throws RemoteException;

    boolean playerIsOnline(int id) throws RemoteException;
}