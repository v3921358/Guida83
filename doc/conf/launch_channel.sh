#!/bin/sh
java -cp server.jar -Xms512M -Xmx2G \
-XX:+HeapDumpOnOutOfMemoryError \
-Dguida.recvops=recvops.properties \
-Dguida.sendops=sendops.properties \
-Dguida.nxpath=nx/ \
-Dguida.channel.config=channel.properties \
-Djavax.net.ssl.keyStore=filename.keystore \
-Djavax.net.ssl.keyStorePassword=passwd \
-Djavax.net.ssl.trustStore=filename.keystore \
-Djavax.net.ssl.trustStorePassword=passwd \
-Djava.util.logging.config.file=channel.log.properties \
guida.net.channel.ChannelServer