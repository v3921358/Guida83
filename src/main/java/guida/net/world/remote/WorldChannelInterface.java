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

package guida.net.world.remote;

import guida.net.MaplePacket;
import guida.net.channel.remote.ChannelWorldInterface;
import guida.net.world.CharacterIdChannelPair;
import guida.net.world.MapleMessenger;
import guida.net.world.MapleMessengerCharacter;
import guida.net.world.MapleParty;
import guida.net.world.MaplePartyCharacter;
import guida.net.world.PartyOperation;
import guida.net.world.PlayerBuffValueHolder;
import guida.net.world.guild.MapleAlliance;
import guida.net.world.guild.MapleGuild;
import guida.net.world.guild.MapleGuildCharacter;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Matze
 */
public interface WorldChannelInterface extends Remote, WorldChannelCommonOperations {

    Properties getDatabaseProperties() throws RemoteException;

    Properties getGameProperties() throws RemoteException;

    void serverReady() throws RemoteException;

    String getIP(int channel) throws RemoteException;

    int find(String charName) throws RemoteException;

    int find(int characterId) throws RemoteException;

    Map<Integer, Integer> getConnected() throws RemoteException;

    MapleParty createParty(MaplePartyCharacter chrfor) throws RemoteException;

    MapleParty getParty(int partyid) throws RemoteException;

    void updateParty(int partyid, PartyOperation operation, MaplePartyCharacter target) throws RemoteException;

    void partyChat(int partyid, String chattext, String namefrom) throws RemoteException;

    boolean isAvailable() throws RemoteException;

    ChannelWorldInterface getChannelInterface(int channel) throws RemoteException;

    WorldLocation getLocation(String name) throws RemoteException;

    CharacterIdChannelPair[] multiBuddyFind(int charIdFrom, int[] characterIds) throws RemoteException;

    MapleGuild getGuild(int id) throws RemoteException;

    void clearGuilds() throws RemoteException;

    void setGuildMemberOnline(MapleGuildCharacter mgc, boolean bOnline, int channel) throws RemoteException;

    int addGuildMember(MapleGuildCharacter mgc) throws RemoteException;

    void leaveGuild(MapleGuildCharacter mgc) throws RemoteException;

    void guildChat(int gid, String name, int cid, String msg) throws RemoteException;

    void changeRank(int gid, int cid, int newRank) throws RemoteException;

    void expelMember(MapleGuildCharacter initiator, String name, int cid) throws RemoteException;

    void setGuildNotice(int gid, String notice) throws RemoteException;

    void memberLevelJobUpdate(MapleGuildCharacter mgc) throws RemoteException;

    void changeRankTitle(int gid, String[] ranks) throws RemoteException;

    int createGuild(int leaderId, String name) throws RemoteException;

    void setGuildEmblem(int gid, short bg, byte bgcolor, short logo, byte logocolor) throws RemoteException;

    void disbandGuild(int gid) throws RemoteException;

    boolean increaseGuildCapacity(int gid) throws RemoteException;

    void gainGP(int gid, int amount) throws RemoteException;

    MapleAlliance getAlliance(int id) throws RemoteException;

    void addAlliance(int id, MapleAlliance addAlliance) throws RemoteException;

    void disbandAlliance(int id) throws RemoteException;

    boolean setAllianceNotice(int aId, String notice) throws RemoteException;

    boolean setAllianceRanks(int aId, String[] ranks) throws RemoteException;

    boolean removeGuildFromAlliance(int aId, int guildId) throws RemoteException;

    boolean addGuildtoAlliance(int aId, int guildId) throws RemoteException;

    boolean setGuildAllianceId(int gId, int aId) throws RemoteException;

    boolean increaseAllianceCapacity(int aId, int inc) throws RemoteException;

    void broadcastToGuild(int guildid, MaplePacket packet) throws RemoteException;

    void allianceMessage(int id, MaplePacket packet, int exception, int guildex) throws RemoteException;

    void broadcastToAlliance(int id, MaplePacket packet) throws RemoteException;

    String listGMs() throws RemoteException;

    MapleMessenger createMessenger(MapleMessengerCharacter chrfor) throws RemoteException;

    MapleMessenger getMessenger(int messengerid) throws RemoteException;

    void leaveMessenger(int messengerid, MapleMessengerCharacter target) throws RemoteException;

    void joinMessenger(int messengerid, MapleMessengerCharacter target, String from, int fromchannel) throws RemoteException;

    void silentJoinMessenger(int messengerid, MapleMessengerCharacter target, int position) throws RemoteException;

    void silentLeaveMessenger(int messengerid, MapleMessengerCharacter target) throws RemoteException;

    void messengerChat(int messengerid, String chattext, String namefrom) throws RemoteException;

    void declineChat(String target, String namefrom) throws RemoteException;

    void updateMessenger(int messengerid, String namefrom, int fromchannel) throws RemoteException;

    void addBuffsToStorage(int chrid, List<PlayerBuffValueHolder> toStore) throws RemoteException;

    List<PlayerBuffValueHolder> getBuffsFromStorage(int chrid) throws RemoteException;
}