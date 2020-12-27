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

package guida.tools.data.output;

import guida.net.ByteArrayMaplePacket;
import guida.net.MaplePacket;
import guida.tools.HexTool;

import java.io.ByteArrayOutputStream;

/**
 * Writes a maplestory-packet little-endian stream of bytes.
 *
 * @author Frz
 * @version 1.0
 * @since Revision 352
 */
public class MaplePacketLittleEndianWriter extends GenericLittleEndianWriter {

    private final ByteArrayOutputStream baos;

    /**
     * Constructor - initializes this stream with a default size.
     */
    public MaplePacketLittleEndianWriter() {
        this(32);
    }

    /**
     * Constructor - initializes this stream with size <code>size</code>.
     *
     * @param size The size of the underlying stream.
     */
    public MaplePacketLittleEndianWriter(int size) {
        baos = new ByteArrayOutputStream(size);
        setByteOutputStream(new BAOSByteOutputStream(baos));
    }

    /**
     * Gets a <code>MaplePacket</code> instance representing this
     * sequence of bytes.
     *
     * @return A <code>MaplePacket</code> with the bytes in this stream.
     */
    public MaplePacket getPacket() {
        return new ByteArrayMaplePacket(baos.toByteArray());
    }

    /**
     * Changes this packet into a human-readable hexadecimal stream of bytes.
     *
     * @return This packet as hex digits.
     */
    @Override
    public String toString() {
        return HexTool.toString(baos.toByteArray());
    }
}