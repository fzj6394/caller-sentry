package cn.fzj.callersentry;

import android.content.Context;

/**
 * 三套配色。都是"液态玻璃"的路子：底色是柔和渐变，卡片是半透明玻璃，
 * 靠描边高光和轻微投影撑出层次——安卓上没有 iOS 那种实时毛玻璃，
 * 所以用渐变 + 半透明 + 亮边模拟，观感接近且不吃性能。
 */
public final class UiTheme {

    public static final int ICE = 0;    // 冰川蓝
    public static final int MIST = 1;   // 晨雾白
    public static final int NIGHT = 2;  // 暗夜灰

    public final int id;
    public final String name;
    public final String desc;

    public final int bgTop;
    public final int bgBottom;
    public final int glassTop;      // 卡片上半
    public final int glassBottom;   // 卡片下半（比上面略淡，形成玻璃下沉感）
    public final int stroke;        // 亮边
    public final int strokeSoft;    // 内圈细线
    public final int text;
    public final int textMuted;
    public final int accent;
    public final int accentText;    // 主要按钮上的文字
    public final int danger;
    public final int shadow;

    private UiTheme(int id, String name, String desc,
                    int bgTop, int bgBottom, int glassTop, int glassBottom,
                    int stroke, int strokeSoft, int text, int textMuted,
                    int accent, int accentText, int danger, int shadow) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.glassTop = glassTop;
        this.glassBottom = glassBottom;
        this.stroke = stroke;
        this.strokeSoft = strokeSoft;
        this.text = text;
        this.textMuted = textMuted;
        this.accent = accent;
        this.accentText = accentText;
        this.danger = danger;
        this.shadow = shadow;
    }

    private static final UiTheme[] ALL = new UiTheme[]{
            new UiTheme(ICE, "冰川蓝", "冷调玻璃，白底上一层淡蓝",
                    0xFFEAF3FF, 0xFFD8E7FA,
                    0xF2FFFFFF, 0xD9FFFFFF,
                    0xFFFFFFFF, 0x66FFFFFF,
                    0xFF0F1E33, 0xFF5D6F88,
                    0xFF2E7DFF, 0xFFFFFFFF, 0xFFD9453D, 0x1A2E5A96),

            new UiTheme(MIST, "晨雾白", "极简黑白，安静不抢眼",
                    0xFFF8F9FB, 0xFFECEEF3,
                    0xFAFFFFFF, 0xE6FFFFFF,
                    0xFFFFFFFF, 0x55FFFFFF,
                    0xFF14161A, 0xFF6B7280,
                    0xFF1F2937, 0xFFFFFFFF, 0xFFD9453D, 0x14101828),

            new UiTheme(NIGHT, "暗夜灰", "深色底 + 冷蓝玻璃，夜里不刺眼",
                    0xFF0D1117, 0xFF171C24,
                    0xE6202530, 0xC61A1F28,
                    0x33FFFFFF, 0x1AFFFFFF,
                    0xFFF2F5FA, 0xFF9AA6B8,
                    0xFF6AA9FF, 0xFF0B1220, 0xFFFF6B63, 0x40000000),
    };

    public static UiTheme of(int id) {
        if (id < 0 || id >= ALL.length) return ALL[ICE];
        return ALL[id];
    }

    public static UiTheme[] all() {
        return ALL;
    }

    public static UiTheme current(Context c) {
        return of(Prefs.theme(c));
    }

    public boolean isDark() {
        return id == NIGHT;
    }
}
