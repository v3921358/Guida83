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

package guida.net.login.handler;

import guida.client.MapleClient;
import guida.net.MaplePacketHandler;
import guida.net.login.LoginWorker;
import guida.tools.MaplePacketCreator;
import guida.tools.data.input.SeekableLittleEndianAccessor;

import java.text.SimpleDateFormat;
import java.util.Calendar;

public class LoginPasswordHandler implements MaplePacketHandler {

    private static final String[] greason = {"This account has been blocked or deleted", "Hacking", "Botting", "Advertising", "Harrassment", "Cursing", "Scamming", "Misconduct", "Illegal Charging (NX Cash)", "Illegal Charging/Funding", "Requested", "Impersonating a GM", "Using Illegal Programs", "Cursing, Scamming or Illegal Trading over a Megaphone"};

    @Override
    public boolean validateState(MapleClient c) {
        return !c.isLoggedIn();
    }

    @Override
    public void handlePacket(SeekableLittleEndianAccessor slea, MapleClient c) {
        final String login = slea.readMapleAsciiString();
        final String password = slea.readMapleAsciiString();

        c.setAccountName(login);

        int loginok = 0;
        boolean ipBan;
        boolean macBan;

        loginok = c.login(login, password);
        boolean permban = loginok == 3;
        ipBan = c.hasBannedIP();
        macBan = c.hasBannedMac();
        final Calendar tempbannedTill = c.getTempBanCalendar();
        if (loginok == 0 && (ipBan || macBan)) {
            loginok = 3;
        }
        boolean banned = false;
        StringBuilder sb = new StringBuilder("You may not log in to your account\r\nfor the following reasons:\r\n\r\n");
        if (ipBan) {
            sb.append("* Your IP is banned\r\n");
            banned = true;
        }
        if (macBan) {
            sb.append("* One or more of your MACs are banned\r\n");
            banned = true;
        }

        if (permban) {
            final String reason = c.getPermabanReason();
            sb.append("* Your account has been permanently banned\r\n");
            sb.append(reason);
            sb.append("\r\n");
            banned = true;

        } else if (tempbannedTill != null && tempbannedTill.getTimeInMillis() != 0) {
            final byte reason = c.getBanReason();
            final SimpleDateFormat tempbanf = new SimpleDateFormat("HH:mm:ss, d MMMMM yyyy");
            sb.append("* Your account has been temporarily banned for\r\n\"");
            sb.append(greason[reason]);
            sb.append("\" until ");
            sb.append(tempbanf.format(tempbannedTill.getTime()));
            sb.append("\r\n");
            sb.append(c.getPermabanReason());
            sb.append("\r\n");
            banned = true;
        }

        if (banned) {
            c.sendPacket(MaplePacketCreator.getLoginFailed(3));
            c.sendPacket(MaplePacketCreator.serverNotice(1, sb.toString()));
        } else if (loginok != 0) {
            c.sendPacket(MaplePacketCreator.getLoginFailed(loginok));
        } else {
            c.sendPacket(MaplePacketCreator.getAuthSuccess(c));
            if (c.getPIC().length() == 0 && c.getPin() != 10000) {
                c.updateLoginState(MapleClient.ENTERING_PIN);
            } else {
                c.updateLoginState(MapleClient.PIN_CORRECT);
            }
            LoginWorker.getInstance().updateLoad();
        }
        /*if (c.getGender() == 10 || c.getPin() == 10000) {
			c.updateLoginState(MapleClient.LOGIN_LOGGEDIN);
			c.sendPacket(MaplePacketCreator.getAuthSuccess(c));
		} else {
			if (c.isGM()) {
				LoginWorker.getInstance().registerGMClient(c);
			} else {
				LoginWorker.getInstance().registerClient(c);
			}
		}*/

    }
}