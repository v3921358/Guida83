#!/bin/sh
java -cp server.jar -Dguida.recvops=recvops.properties \
-Dguida.sendops=sendops.properties \
-Dguida.nxpath=nx/ \
-Djavax.net.ssl.keyStore=filename.keystore \
-Djavax.net.ssl.keyStorePassword=passwd \
-Djavax.net.ssl.trustStore=filename.keystore \
-Djavax.net.ssl.trustStorePassword=passwd \
-Djava.util.logging.config.file=world.log.properties \
guida.net.world.WorldServer