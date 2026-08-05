java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
dependencies {
    // Paper API 1.21+ is required for the Folia region/entity/async schedulers and
    // player.getScheduler(). The plugin still runs on classic Paper too.
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    // Patched local build: run `mvn install` inside the NoteBlockAPI project first,
    // then this resolves from the local Maven repository.
    compileOnly("com.xxmicloxx:NoteBlockAPI:1.7.0-SNAPSHOT")

    // 13.7.1: parses the calendar (26.x) server version correctly (11.x throws
    // "Failed to parse server version" on 26.x). Verified that the XMaterial enum API,
    // XSound#play(Entity) and XEnchantment#getEnchant() used here are source-compatible.
    api("com.github.cryptomorin:XSeries:13.7.1")
    api("io.github.bananapuncher714:nbteditor:7.19.0")
    api("org.bstats:bstats-bukkit:3.0.2")

    api(project(":nms"))

    compileOnly("org.yaml:snakeyaml:2.0")

}
