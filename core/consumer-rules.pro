# RootService is instantiated reflectively in a separate app_process.
-keep class app.quieta.core.privilege.root.RootChannelService { *; }
-keep class app.quieta.core.privilege.root.IRootChannels** { *; }
