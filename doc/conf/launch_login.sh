#!/bin/sh
java -cp server.jar \
-Dguida.recvops=recvops.properties \
-Dguida.sendops=sendops.properties \
-Dguida.nxpath=nx/ \
-Dguida.login.config=login.properties \
-Djavax.net.ssl.keyStore=filename.keystore \
-Djavax.net.ssl.keyStorePassword=passwd \
-Djavax.net.ssl.trustStore=filename.keystore \
-Djavax.net.ssl.trustStorePassword=passwd \
-Djava.util.logging.config.file=login.log.properties \
guida.net.login.LoginServer