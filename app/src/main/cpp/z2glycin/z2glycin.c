// z2glycin — gThumb 専用の glycin 互換シム (GPL-3.0)。
//
// Arch Linux の gdk-pixbuf 2.44 は SVG を含む画像を glycin 経由で読み、通常は
// bubblewrap の user/mount namespace 内で loader を起動する。Android の app sandbox
// 内で動く z2root では入れ子 namespace を作れず、gThumb は必須アイコンの読み込みで
// GTK abort する。
//
// gdk-pixbuf はプログラム名が "gdk-pixbuf-thumbnailer" の場合、外側ですでに隔離済みと
// 判断して glycin の公式 NotSandboxed 経路を選ぶ。この極小シムを gThumb の wrapper
// からだけ LD_PRELOAD し、その判定だけを成立させる。z2root 自体が Android の
// untrusted_app sandbox 内なので、loader が Android の権限境界を越えることはない。
//
// libc 非依存 (-nostdlib)。gThumb が途中で g_set_prgname() を呼んでも判定名が戻らない
// よう setter もこのプロセス内だけ無効化する。

const char *g_get_prgname(void) {
    return "gdk-pixbuf-thumbnailer";
}

void g_set_prgname(const char *name) {
    (void)name;
}
