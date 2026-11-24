# ColdmcVaultBridge

ColdMC's own glue plugin. It ties the server's plugins together where none of them do it on their own:

- **Chat and economy for Vault:** the CoreTools rank prefix in chat, and an economy front with Towny town and nation bank accounts.
- **Eras:** each MMOProfiles profile's ColdMC era (`%coldmc_era%`, `/coldmcera`), which switches the Nexo era packs.
- **Mastery:** drops, crops, fishing, Nexo ores and levels.
- **Custom items stay in custom stations:** recipe redirects and recipe unlocks.
- **Rewards that wait:** `/cmchold` holds a reward until the player picks a profile.
- **Small fixes:** `/warp` tab completion, command refresh, and the common crate key drop.

- `src/se/coldmc/vaultbridge/`: the plugin source. `src/plugin.yml` and `src/config.yml` are bundled into the jar.
- `build.sh`: builds `ColdmcVaultBridge.jar` with JDK 25 (`--release 21`) against the server's API jars. Set `COLDMC_SERVER` to the server directory.
