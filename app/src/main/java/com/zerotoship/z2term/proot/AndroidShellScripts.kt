package com.zerotoship.z2term.proot

/** Reuse CLI parsing and bridge protocol without changing the Linux helpers. */
internal object AndroidShellScripts {
    fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    fun create(lang: String, bin: String, bridge: String): Map<String, String> {
        val scripts = z2ApiScripts(lang).mapValues { (_, body) ->
            body.replace("#!/bin/sh", "#!/system/bin/sh")
                .replace("/tmp/", "\${TMPDIR}/")
                .replace("/usr/local/bin/z2api", quote("$bin/z2api"))
                .replace("/usr/local/bin/z2attach", quote("$bin/z2attach"))
                .replace("DIR=/storage/app/z2api", "DIR=${quote(bridge)}")
        }.toMutableMap()
        // These helpers require Linux packages; do not try a package manager on Android.
        for (name in listOf("z2-audio", "z2-img")) {
            val message = if (lang == "ja") "$name: Linux環境が必要です。設定 › Linux環境からOSを導入してください。"
                else "$name: install a Linux OS in Settings > Linux environment first."
            scripts[name] = "#!/system/bin/sh\nprintf '%s\\n' ${quote(message)} >&2\nexit 1\n"
        }
        val help = if (lang == "ja")
            "Linux未導入: Android標準シェルでz2-*を利用できます。各コマンドの --help を参照してください。\n" +
                "自動化: z2-when / Android操作: z2-action / エッジパネル: z2-edge\n" +
                "シェルマクロ: sh \"\$HOME/.z2term/macros/名前.sh\"\n" +
                "Linuxパッケージ、z2-macroのサンプル導入、z2-audio、z2-img、Linux常駐サーバーにはOSが必要です。\n" +
                "Androidの権限・バックグラウンド制限は適用されます。\n"
        else "Android shell: use z2-* without installing Linux. See each command's --help.\n" +
            "Automation: z2-when / Android actions: z2-action / Panels: z2-edge\n" +
            "Shell macros: sh \"\$HOME/.z2term/macros/name.sh\"\n" +
            "Linux packages, z2-macro sample installation, z2-audio, z2-img and Linux servers require an OS.\n" +
            "Android permissions and background limits still apply.\n"
        scripts["z2help"] = "#!/system/bin/sh\nprintf '%s\\n' ${quote(help)}\nprintf '%s\\n' " +
            quote(scripts.keys.filter { it != "z2api" }.sorted().joinToString("\n")) + "\n"
        return scripts
    }
}
