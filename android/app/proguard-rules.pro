# Java AFK release keep rules.
#
# The protocol engine is pure Kotlin and has no JNI, no reflection and no
# serialized class names, so R8 can minify and shrink everything. Only the
# engine's public surface is kept because the app talks to it through
# interfaces that R8 would otherwise inline away in debug-only paths.
-keep class dev.mstheesha.afk.engine.AfkEngine { public *; }
-keep interface dev.mstheesha.afk.engine.BotListener { *; }
-keep class dev.mstheesha.afk.engine.BotConfig { *; }
-keep class dev.mstheesha.afk.engine.BotSnapshot { *; }
