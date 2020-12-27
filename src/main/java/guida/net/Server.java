/*
 * This file is part of Guida.
 * Copyright (C) 2020 Guida
 *
 * Guida is a fork of the OdinMS MapleStory Server.
 *
 * Guida is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License version 3
 * as published by the Free Software Foundation. You may not use, modify
 * or distribute this program under any other version of the
 * GNU Affero General Public License.
 *
 * Guida is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Guida.  If not, see <http://www.gnu.org/licenses/>.
 */

package guida.net;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import guida.client.MapleClient;
import guida.net.netty.LittleEndianByteBufAllocator;

import java.net.SocketAddress;

public class Server extends ChannelInitializer<SocketChannel> implements Runnable {

    private final int channel;
    private final SocketAddress bindTo;
    private Channel acceptor;

    public Server(SocketAddress address, int channel) {
        bindTo = address;
        this.channel = channel;
    }

    public Server(SocketAddress address) {
        this(address, -1);
    }

    @Override
    public void run() {
        EventLoopGroup acceptorGroup = new NioEventLoopGroup(4);
        EventLoopGroup clientGroup = new NioEventLoopGroup(10);

        acceptor = new ServerBootstrap()
                .group(acceptorGroup, clientGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(this)
                .option(ChannelOption.SO_BACKLOG, 64)
                .option(ChannelOption.ALLOCATOR, new LittleEndianByteBufAllocator(UnpooledByteBufAllocator.DEFAULT))
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .bind(bindTo).syncUninterruptibly().channel();
    }

    public void stop() {
        acceptor.close();
    }

    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        MapleClient c = new MapleClient(socketChannel);
        if (channel > -1) {
            c.setChannel(channel);
        }
        socketChannel.pipeline().addLast("MapleClient", c);
    }
}
