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
import guida.client.messages.CommandProcessor;
import guida.client.messages.commands.NoticeCommand;
import guida.net.AbstractMaplePacketHandler;
import guida.net.MaplePacket;
import guida.net.channel.ChannelServer;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.rmi.RemoteException;
import java.util.Collection;

/**
 * @author Matze
 */
public class WhisperHandler extends AbstractMaplePacketHandler {

    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final byte mode = slea.readByte();
        c.getPlayer().resetAfkTimer();
        if (mode == 6) { // Whisper
            final String recipient = slea.readMapleAsciiString();
            final String text = slea.readMapleAsciiString();

            if (c.getPlayer().isGM() && recipient.toLowerCase().startsWith("gm")) {
                String whispcmd = recipient.toLowerCase().substring(2);
                MapleCharacter chr = c.getPlayer();
                int gml = chr.getGMLevel();
                if (whispcmd.equals("talk")) {
                    c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 1));
                    try {
                        c.getChannelServer().getWorldInterface().broadcastGMMessage(c.getPlayer().getName(), MaplePacketCreator.serverNotice(6, c.getPlayer().getName() + " : " + text).getBytes());
                    } catch (RemoteException ex) {
                        c.getChannelServer().reconnectWorld();
                        return;
                    }
                } else if (gml >= 4 && whispcmd.startsWith("notice")) {
                    String noticer = whispcmd.substring(6, 7);
                    String noticet = whispcmd.substring(7);
                    int range = NoticeCommand.getNoticeRange(noticer);
                    int type = NoticeCommand.getNoticeType(noticet);
                    StringBuilder notice = new StringBuilder();

                    if (noticet.equals("nv")) {
                        notice.append("[Notice] ");
                    }
                    if (type == 18 || type == 22) {
                        notice = new StringBuilder(c.getPlayer().getName() + " : ");
                        type -= 20;
                    }
                    notice.append(text);
                    MaplePacket packet = switch (type) {
                        case -2 -> MaplePacketCreator.serverNotice(3, 99, notice.toString(), false);
                        case -3 -> MaplePacketCreator.topMessage(notice.toString());
                        default -> MaplePacketCreator.serverNotice(type, notice.toString());
                    };
                    if (packet == null) {
                        chr.dropMessage("An unknown error occured. Report please.");
                        c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
                        return;
                    }
                    c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 1));
                    if (range == 0) {
                        c.getPlayer().getMap().broadcastMessage(packet);
                    } else if (range == 1) {
                        c.getChannelServer().broadcastPacket(packet);
                    } else if (range == 2) {
                        try {
                            ChannelServer.getInstance(1).getWorldInterface().broadcastMessage(c.getPlayer().getName(), packet.getBytes());
                        } catch (RemoteException e) {
                            c.getChannelServer().reconnectWorld();
                        }
                    }
                } else if (gml >= 4 && whispcmd.equals("weather")) {
                    chr.getMap().stopMapEffect();
                    chr.getMap().startMapEffect(text, 5120002);
                    c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 1));
                } else if (gml >= 4 && whispcmd.equals("me")) {
                    MaplePacket msgpacket = MaplePacketCreator.serverNotice(6, "[" + c.getPlayer().getName() + "] " + text);
                    try {
                        c.getChannelServer().getWorldInterface().broadcastMessage(c.getPlayer().getName(), msgpacket.getBytes());
                    } catch (RemoteException ex) {
                        c.getChannelServer().reconnectWorld();
                    }
                }
                return;
            }
            if (c.getPlayer().getChatSpam(0) + 500 > System.currentTimeMillis()) {
                c.getPlayer().dropMessage("Try again later.");
                c.sendPacket(MaplePacketCreator.enableActions());
                return;
            }
            c.getPlayer().setChatSpam(0);
            if (text.length() > 70 && !c.getPlayer().hasGMLevel(2)) {
                return;
            }
            if (!CommandProcessor.getInstance().processCommand(c, text)) {
                if (c.getPlayer().isMuted()) {
                    c.getPlayer().dropMessage(5, c.getPlayer().isMuted() ? "You are muted, therefore you are unable to talk." : "The map is muted, therefore you are unable to talk.");
                    return;
                }
                MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterByName(recipient);
                if (player != null) {
                    player.getClient().sendPacket(MaplePacketCreator.getWhisper(c.getPlayer().getName(), c.getChannel(), text));
                    if (player.hasGMLevel(2) && !c.getPlayer().hasGMLevel(2)) {
                        c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
                    } else {
                        c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 1));
                    }
                } else {
                    final Collection<ChannelServer> cservs = ChannelServer.getAllInstances();
                    for (ChannelServer cserv : cservs) {
                        player = cserv.getPlayerStorage().getCharacterByName(recipient);
                        if (player != null) {
                            break;
                        }
                    }
                    if (player != null) {
                        try {
                            c.getChannelServer().getWorldInterface().whisper(c.getPlayer().getName(), player.getName(), c.getChannel(), text);
                            if (player.hasGMLevel(2) && !c.getPlayer().hasGMLevel(2)) {
                                c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
                            } else {
                                c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 1));
                            }
                        } catch (RemoteException re) {
                            c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
                            c.getChannelServer().reconnectWorld();
                        }
                    } else {
                        c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
                    }
                }
            }
        } else if (mode == 5) { // Find
            final String recipient = slea.readMapleAsciiString();
            MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterByName(recipient);
            if (player != null && (!player.hasGMLevel(2) || c.getPlayer().hasGMLevel(2) && player.hasGMLevel(2))) {
                if (player.inCS()) {
                    c.sendPacket(MaplePacketCreator.getFindReplyWithCS(player.getName()));
                } else {
                    c.sendPacket(MaplePacketCreator.getFindReplyWithMap(player.getName(), player.getMap().getId()));
                }
            } else { // Not found
                final Collection<ChannelServer> cservs = ChannelServer.getAllInstances();
                for (ChannelServer cserv : cservs) {
                    player = cserv.getPlayerStorage().getCharacterByName(recipient);
                    if (player != null) {
                        break;
                    }
                }
                if (player != null && (!player.hasGMLevel(2) || c.getPlayer().hasGMLevel(2) && player.hasGMLevel(2))) {
                    c.sendPacket(MaplePacketCreator.getFindReply(player.getName(), (byte) player.getClient().getChannel()));
                } else {
                    c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
                }
            }
        } else if (mode == 68) {
            final String recipient = slea.readMapleAsciiString();
            MapleCharacter player = c.getChannelServer().getPlayerStorage().getCharacterByName(recipient);
            if (player != null && (!player.hasGMLevel(2) || c.getPlayer().hasGMLevel(2) && player.hasGMLevel(2))) {
                if (player.inCS()) {
                    c.sendPacket(MaplePacketCreator.getBuddyFindReplyWithCS(player.getName()));
                } else {
                    c.sendPacket(MaplePacketCreator.getBuddyFindReplyWithMap(player.getName(), player.getMap().getId()));
                }
            } else { // Not found
                final Collection<ChannelServer> cservs = ChannelServer.getAllInstances();
                for (ChannelServer cserv : cservs) {
                    player = cserv.getPlayerStorage().getCharacterByName(recipient);
                    if (player != null) {
                        break;
                    }
                }
                if (player != null && (!player.hasGMLevel(2) || c.getPlayer().hasGMLevel(2) && player.hasGMLevel(2))) {
                    c.sendPacket(MaplePacketCreator.getBuddyFindReply(player.getName(), (byte) player.getClient().getChannel()));
                } else {
                    c.sendPacket(MaplePacketCreator.getWhisperReply(recipient, (byte) 0));
                }
            }
        }
    }
}