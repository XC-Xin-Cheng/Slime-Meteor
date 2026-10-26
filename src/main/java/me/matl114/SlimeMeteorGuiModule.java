package me.matl114;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import me.matl114.hacks.MainTasks;
import me.matl114.hacks.api.BaseModule;
import me.matl114.hacks.api.ModuleGroup;
import me.matl114.hacks.modules.HackModules;
import me.matl114.hacks.modules.task.ClickGui;
import me.matl114.managers.Tasks;
import me.matl114.managers.config.FlagRef;
import me.matl114.utils.Debug;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * 桥接: 把粘液助手的模块按原版分组注册进 Meteor 的 ClickGui。 采用 I18nXC 的方式在运行时反射写入 Module.title 实现汉化
 * (汉化SM 开关: 开启显示中文, 关闭恢复英文)。 汉化范围严格限定为本桥接注册的粘液助手模块, 不触碰 Meteor 本体及其他附属。
 */
public class SlimeMeteorGuiModule extends Module {
    public static final Category SLIME_METEOR_CATEGORY = new Category("Slime-Meteor", new ItemStack(Items.SLIME_BALL));

    private static final String GROUP_NAME_KEY_PREFIX = "widget.click-gui.module-group-name.";
    private static final String MODULE_NAME_KEY_PREFIX = "widget.click-gui.module-name.";

    // 粘液助手的固定分组列表 (与 en_us 语言键一致); Meteor 只允许在 onRegisterCategories 中注册分类
    private static final List<String> GROUP_NAMES = List.of(
            "Tasks",
            "Mine",
            "Chat",
            "Move",
            "Render",
            "Inv",
            "Combat",
            "Networks",
            "Interaction",
            "Slimefun",
            "Model",
            "Extra");

    /** 内置补充汉化 (包内汉化文件未覆盖的模块) */
    private static final Map<String, String> SUPPLEMENT_ZH = Map.ofEntries(
            Map.entry("Tasks.Disabler", "禁用器"),
            Map.entry("Tasks.IQBoost", "IQ增强"),
            Map.entry("Move.NoGround", "无地面"),
            Map.entry("Move.SwimControl", "游泳控制"),
            Map.entry("Render.ExplosiveESP", "爆炸透视"),
            Map.entry("Render.ShulkerDisplay", "潜影盒显示"),
            Map.entry("Inv.InvDesyncFix", "物品栏同步修复"),
            Map.entry("Inv.NbtToolTips", "NBT信息提示"),
            Map.entry("Combat.AntiCrystal", "防水晶"),
            Map.entry("Combat.AntiReplenish", "防补给"),
            Map.entry("Combat.BowTap", "弓速点"),
            Map.entry("Model.CustomTextures", "自定义贴图"),
            Map.entry("Extra.AntiAFK", "防挂机"),
            Map.entry("Extra.BoatVClip", "船垂直穿墙"),
            Map.entry("Extra.FakeLag", "假延迟"),
            Map.entry("Extra.NoSound", "无声音"),
            Map.entry("Extra.PlayerLog", "玩家日志"),
            Map.entry("Extra.PlayerQueue", "玩家队列"),
            Map.entry("Extra.SoundNotify", "声音通知"),
            Map.entry("Slimefun.MultiBlockHelper", "多方块助手"),
            Map.entry("Slimefun.RecipeDatabase", "配方数据库"),
            Map.entry("Slimefun.ShowIdTooltips", "显示ID提示"));

    private static final Map<String, Category> GROUP_CATEGORIES = new HashMap<>();
    private static final List<BaseModuleWrapper> BRIDGED = new ArrayList<>();
    private static volatile Map<String, String> i18nXcById = Map.of();

    private static final Field TITLE_FIELD;
    private static final Field DESC_FIELD;

    static {
        try {
            // I18nXC 的做法: 反射写入 Module 的 final 字段 title/description
            TITLE_FIELD = Module.class.getDeclaredField("title");
            TITLE_FIELD.setAccessible(true);
            DESC_FIELD = Module.class.getDeclaredField("description");
            DESC_FIELD.setAccessible(true);
        } catch (Throwable e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** 在 Meteor 的 onRegisterCategories 回调内预创建全部分组分类: 分类名 = "SM " + 原版分组名 */
    public static void registerCategories() {
        Modules.registerCategory(SLIME_METEOR_CATEGORY);
        for (String group : GROUP_NAMES) {
            String display = Text.translatableWithFallback(GROUP_NAME_KEY_PREFIX + group, group)
                    .getString();
            Category category = new Category("SM " + display, new ItemStack(Items.SLIME_BALL));
            GROUP_CATEGORIES.put(group, category);
            Modules.registerCategory(category);
        }
    }

    public SlimeMeteorGuiModule() {
        super(SLIME_METEOR_CATEGORY, "slime-meteor-gui", "打开粘液助手的自定义 ClickGui 界面。");
    }

    @Override
    public void onActivate() {
        Tasks.scheduleDelayed(() -> MainTasks.getClickGui().openClickGui(), 1);
        // 仅作为按钮使用, 打开界面后立即还原开关状态
        toggle();
    }

    /** 把粘液助手的全部可开关模块按原版分组包装为 Meteor 模块, 并注册 汉化SM 开关 */
    public static void registerAllBridgeModules() {
        Modules modules = Modules.get();
        for (ModuleGroup group : HackModules.getModuleGroups()) {
            Category category = GROUP_CATEGORIES.getOrDefault(group.getName(), SLIME_METEOR_CATEGORY);
            group.getModules().stream()
                    .filter(BaseModule::shouldShowInGui)
                    .filter(base -> base.getBindFlag() != null)
                    .forEach(base -> {
                        BaseModuleWrapper wrapper = new BaseModuleWrapper(category, base);
                        BRIDGED.add(wrapper);
                        modules.add(wrapper);
                    });
        }
        modules.add(new SmLocalizationToggle());
        patchTitleCredits();
    }

    /**
     * 重建标题屏幕中本附属的作者行: Slime-Meteor by m1919810 & GLMv5.3F(红) & XC_Xin_Cheng(灰斜)。 原有内容与 meteor
     * 默认行为保持一致, 仅追加并设置指定颜色 (I18nXC 式反射)。
     */
    private static void patchTitleCredits() {
        try {
            Class<?> tsc = Class.forName("meteordevelopment.meteorclient.utils.player.TitleScreenCredits");
            Field creditsField = tsc.getDeclaredField("credits");
            creditsField.setAccessible(true);
            List<?> credits = (List<?>) creditsField.get(null);
            if (credits == null || credits.isEmpty()) {
                java.lang.reflect.Method init = tsc.getDeclaredMethod("init");
                init.setAccessible(true);
                init.invoke(null);
            }
            Object ourAddon = SlimefunHelper.instance;
            for (Object credit : credits) {
                Field addonField = credit.getClass().getField("addon");
                addonField.setAccessible(true);
                if (addonField.get(credit) == ourAddon) {
                    Field textField = credit.getClass().getField("text");
                    textField.setAccessible(true);
                    MutableText text = Text.literal("Slime-Meteor")
                            .styled(style -> style.withColor(TextColor.fromRgb(0xFFE11919)));
                    text.append(Text.literal(" by ").styled(style -> style.withColor(Formatting.GRAY)));
                    text.append(Text.literal("m1919810"));
                    text.append(Text.literal(" & ").styled(style -> style.withColor(Formatting.GRAY)));
                    text.append(Text.literal("GLMv5.3F").styled(style -> style.withColor(Formatting.RED)));
                    text.append(Text.literal(" & ").styled(style -> style.withColor(Formatting.GRAY)));
                    text.append(Text.literal("XC_Xin_Cheng")
                            .styled(style -> style.withColor(Formatting.GRAY).withItalic(true)));
                    textField.set(credit, text);
                    Debug.info("TitleCredits: 已更新作者行");
                    break;
                }
            }
        } catch (Throwable e) {
            Debug.info("TitleCredits: 补丁失败 " + e);
        }
    }

    /** 汉化SM 开关: 开启后 SM 分类的模块标题切换为中文, 关闭恢复英文 */
    public static class SmLocalizationToggle extends Module {
        public SmLocalizationToggle() {
            super(SLIME_METEOR_CATEGORY, "sm-localization", "汉化SM");
            setTitles(this, "汉化SM", "开启后 SM 分类的模块名显示为中文, 关闭恢复英文。仅影响粘液助手的模块。");
        }

        @Override
        public void onActivate() {
            applyTitles(true);
        }

        @Override
        public void onDeactivate() {
            applyTitles(false);
        }
    }

    /** 按开关状态应用所有桥接模块的标题 (中文/英文) */
    private static void applyTitles(boolean zh) {
        Map<String, String> zhPack = zh ? loadZhLangMap() : Map.of();
        int applied = 0;
        List<String> missing = new ArrayList<>();
        for (BaseModuleWrapper wrapper : BRIDGED) {
            BaseModule base = wrapper.target;
            String title;
            if (zh) {
                title = chineseName(base, zhPack);
                if (!title.equals(base.getName())) {
                    applied++;
                } else {
                    missing.add(base.getModuleManager().getName() + "." + base.getName());
                }
            } else {
                title = base.getName();
            }
            setTitles(wrapper, title, "粘液助手模块 (ID: " + base.getName() + ")");
        }
        Debug.info("汉化SM: 已将 " + applied + "/" + BRIDGED.size() + " 个模块标题设为中文"
                + (missing.isEmpty() ? "" : ", 未覆盖: " + missing));
    }

    /** 中文名查找顺序: 包内汉化文件 → 内置补充表 → I18nXC (按模块id) → 原版翻译 → 原名 */
    private static String chineseName(BaseModule base, Map<String, String> zhPack) {
        String key = MODULE_NAME_KEY_PREFIX + base.getModuleManager().getName() + "." + base.getName();
        String v = zhPack.get(key);
        if (v != null && !v.isBlank()) {
            return v;
        }
        String supplementKey = base.getModuleManager().getName() + "." + base.getName();
        v = SUPPLEMENT_ZH.get(supplementKey);
        if (v != null && !v.isBlank()) {
            return v;
        }
        v = i18nXcById.get(toSnakeCase(base.getName()));
        if (v != null && !v.isBlank()) {
            return v;
        }
        return Text.translatableWithFallback(key, base.getName()).getString();
    }

    /** 从 mods 目录的 I18nXC jar 中读取 meteor.<分类>.<模块id> 中文索引 (惰性加载一次) */
    private static Map<String, String> loadI18nXcIndex() {
        try {
            Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
            if (!Files.isDirectory(mods)) {
                return Map.of();
            }
            try (var files = Files.list(mods)) {
                for (Path jar : (Iterable<Path>) files::iterator) {
                    String name = jar.getFileName().toString();
                    if (!name.toLowerCase().endsWith(".jar") || !name.startsWith("Meteor-I18n-XC")) {
                        continue;
                    }
                    try (ZipFile zip = new ZipFile(jar.toFile())) {
                        ZipEntry entry = zip.getEntry("assets/meteor_i18n_xc/lang/zh_cn.json");
                        if (entry == null) {
                            continue;
                        }
                        Map<String, String> all = new Gson()
                                .fromJson(
                                        new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8),
                                        new TypeToken<Map<String, String>>() {}.getType());
                        Map<String, String> index = new HashMap<>();
                        for (Map.Entry<String, String> e : all.entrySet()) {
                            String k = e.getKey();
                            // 仅取 meteor.<分类>.<模块id> 三段键, 排除 description/setting
                            if (k.startsWith("meteor.")
                                    && k.indexOf('.', 7) == k.lastIndexOf('.')
                                    && k.lastIndexOf('.') > 7) {
                                index.putIfAbsent(k.substring(k.lastIndexOf('.') + 1), e.getValue());
                            }
                        }
                        Debug.info("汉化SM: 已从 " + name + " 载入 " + index.size() + " 条 Meteor 模块汉化");
                        return index;
                    }
                }
            }
        } catch (Throwable e) {
            Debug.info("汉化SM: 读取 I18nXC 失败 " + e);
        }
        return Map.of();
    }

    /** 驼峰命名转下划线: AnchorAura -> anchor_aura */
    private static String toSnakeCase(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** I18nXC 式反射写入 Meteor Module 的 final 字段 */
    private static void setTitles(Module module, String title, String description) {
        try {
            TITLE_FIELD.set(module, title);
            DESC_FIELD.set(module, description);
        } catch (Throwable e) {
            Debug.info("汉化SM: 写入标题失败 " + e);
        }
    }

    private static Map<String, String> loadZhLangMap() {
        try (InputStream in = SlimeMeteorGuiModule.class.getResourceAsStream(
                "/assets/slimefunhelper/lang/zh_ch_resource_pack.json")) {
            if (in == null) {
                Debug.info("汉化SM: 未找到包内汉化文件");
                return Map.of();
            }
            return new Gson()
                    .fromJson(
                            new InputStreamReader(in, StandardCharsets.UTF_8),
                            new TypeToken<Map<String, String>>() {}.getType());
        } catch (Throwable e) {
            Debug.info("汉化SM: 汉化文件解析失败 " + e);
            return Map.of();
        }
    }

    /** 单个粘液助手模块的 Meteor 包装: 开关状态与内部 FlagRef 双向同步 */
    private static class BaseModuleWrapper extends Module {
        private final BaseModule target;
        private final FlagRef flag;

        BaseModuleWrapper(Category category, BaseModule target) {
            super(category, target.getName(), target.getName());
            this.target = target;
            this.flag = target.getBindFlag();
        }

        @Override
        public void onActivate() {
            flag.set(true);
        }

        @Override
        public void onDeactivate() {
            flag.set(false);
        }

        @Override
        public boolean isActive() {
            return flag.get();
        }

        @Override
        public WWidget getWidget(GuiTheme theme) {
            WButton button = theme.button("打开完整设置");
            button.action = () -> ClickGui.INSTANCE.openConfigurateScreen(target);
            return button;
        }
    }
}
