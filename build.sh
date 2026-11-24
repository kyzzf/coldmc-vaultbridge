#!/bin/sh
# Builds ColdmcVaultBridge.jar against the live server API jars. Install with mv (never cp over
# the live jar - [[replace-a-live-jar-with-mv-not-cp]]) into core/server/plugins/; loads on restart.
set -e
cd "$(dirname "$0")"
S=${COLDMC_SERVER:-../../../../core/server}; J=/usr/local/opt/openjdk@25/bin
CP="$S/libraries/cn/dreeam/leaf/leaf-api/1.21.11-R0.1-SNAPSHOT/leaf-api-1.21.11-R0.1-SNAPSHOT.jar:$S/plugins/Vault.jar:$S/plugins/PlaceholderAPI.jar:$S/plugins/MMOProfiles-1.2-27.jar:$S/plugins/nexo-1.28.jar:$S/plugins/MMOItems-6.10.1-55.jar:$S/plugins/MythicLib-dist-1.7.1 90.jar:$S/plugins/MMOCore-1.13.1 -54.jar:$S/plugins/CustomFishing.jar:$S/plugins/CustomCrops.jar"
for j in $(find $S/libraries/net/md-5 -name "bungeecord-chat*.jar") $(find $S/libraries/net/kyori -name '*4.26*.jar' -o -name 'examination*.jar' -o -name 'adventure-key*.jar') $(find $S/libraries/org/jetbrains/annotations -name '*.jar' | head -1) $(find $S/libraries/com/google/guava/guava -name "guava-*.jar"); do CP="$CP:$j"; done
rm -rf build && mkdir build
$J/javac --release 21 -nowarn -d build -cp "$CP" src/se/coldmc/vaultbridge/*.java
cp src/plugin.yml src/config.yml build/
rm -f ColdmcVaultBridge.jar && $J/jar cf ColdmcVaultBridge.jar -C build .
echo built ColdmcVaultBridge.jar
