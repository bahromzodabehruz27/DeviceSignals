package tj.behruz.devicesignals.sdk.internal

import android.util.Base64

internal object SignatureStore {

    // All signatures are Base64-encoded to prevent trivial string extraction (NFR-010)

    val suPaths: List<String> by lazy {
        decode(
            "L3N5c3RlbS9iaW4vc3U=",           // /system/bin/su
            "L3N5c3RlbS94YmluL3N1",           // /system/xbin/su
            "L3N5c3RlbS9hcHAvU3VwZXJ1c2VyLmFwaw==", // /system/app/Superuser.apk
            "L3N5c3RlbS9hcHAvU3VwZXJTVS5hcGs=",     // /system/app/SuperSU.apk
            "L3N5c3RlbS9ldGMvaW5pdC5kL3N1cmM=",     // /system/etc/init.d/surc
            "L3N5c3RlbS9iaW4vLmV4dC8uc3U=",         // /system/bin/.ext/.su
            "L3ZlbmRvci9iaW4vc3U=",                  // /vendor/bin/su
        )
    }

    val fridaLibraries: List<String> by lazy {
        decode(
            "ZnJpZGEtYWdlbnQ=",     // frida-agent
            "ZnJpZGEtZ2FkZ2V0",     // frida-gadget
            "ZnJpZGEtc2VydmVy",     // frida-server
            "bGliZnJpZGE=",         // libfrida
        )
    }

    val xposedArtifacts: List<String> by lazy {
        decode(
            "WHBvc2VkQnJpZGdlLmphcg==",                         // XposedBridge.jar
            "ZGUucm9idi5hbmRyb2lkLnhwb3NlZA==",                 // de.robv.android.xposed
            "ZGUucm9idi5hbmRyb2lkLnhwb3NlZC5pbnN0YWxsZXI=",     // de.robv.android.xposed.installer
        )
    }

    val rootAppPackages: List<String> by lazy {
        decode(
            "Y29tLnRvcGpvaG53dS5tYWdpc2s=",               // com.topjohnwu.magisk
            "Y29tLm53cm9tYW5kZS5zdXBlcnN1",               // com.noshufou.android.su
            "Y29tLmtvdXNob3VwYWlzLnN1cGVyc3U=",           // com.koushikdutta.superuser
            "Y29tLnRoaXJkd2F2ZS5zdXBlcnVzZXI=",           // com.thirdwave.superuser
            "ZXUuY2hhaW5maXJlLnN1cGVyc3U=",               // eu.chainfire.supersu
            "Y29tLm53cm9tYW5kZS5zdQ==",                   // com.nwromande.su
        )
    }

    val ratPackages: List<String> by lazy {
        decode(
            "Y29tLnRlYW12aWV3ZXIudGVhbXZpZXdlcg==",   // com.teamviewer.teamviewer
            "Y29tLmFueWRlc2suYW55ZGVzaw==",             // com.anydesk.anydesk
            "Y29tLnJlYWx2bmMuYW5kcm9pZC5yZW1vdGU=",     // com.realvnc.android.remote
        )
    }

    private fun decode(vararg encoded: String): List<String> =
        encoded.map { String(Base64.decode(it, Base64.NO_WRAP), Charsets.UTF_8) }
}
