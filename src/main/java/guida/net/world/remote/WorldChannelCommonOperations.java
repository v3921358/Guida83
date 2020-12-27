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

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface WorldChannelCommonOperations extends Remote {

    boolean isConnected(String charName, boolean removePlayer) throws RemoteException;

    void broadcastMessage(String sender, final byte[] message, boolean smega) throws RemoteException;

    void broadcastMessage(String sender, final byte[] message) throws RemoteException;

    void whisper(String sender, String target, int channel, String message) throws RemoteException;

    void shutdown(int time) throws RemoteException;

    void broadcastWorldMessage(String message) throws RemoteException;

    void loggedOn(String name, int characterId, int channel, int[] buddies) throws RemoteException;

    void loggedOff(String name, int characterId, int channel, int[] buddies) throws RemoteException;

    List<CheaterData> getCheaters() throws RemoteException;

    void buddyChat(int[] recipientCharacterIds, int cidFrom, String nameFrom, String chattext) throws RemoteException;

    void messengerInvite(String sender, int messengerid, String target, int fromchannel) throws RemoteException;

    void spouseChat(String sender, String target, String message) throws RemoteException;

    void broadcastGMMessage(String sender, final byte[] message) throws RemoteException;
}