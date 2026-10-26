package me.matl114;

import java.util.Set;
import lombok.Getter;
import me.matl114.plugins.MainPlugin;
import me.matl114.plugins.PluginManager;
import me.matl114.plugins.PluginManager.BootstrapPhase;
import me.matl114.utils.Debug;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

public class SlimefunHelper extends MeteorAddon implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MOD_ID = "slimefunhelper";
    // public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    @Getter
    public static SlimefunHelper instance;

    @Getter
    public static ModContainer modContainer;

    public static boolean DEV_ENV = false;

    public SlimefunHelper() {
        // 标题屏幕作者名单 (作者那里): 原作者不变, 追加 GLMv5.3F 与 XC_Xin_Cheng (颜色由 TitleCredits 补丁设置)
        name = "Slime-Meteor";
        authors = new String[] {"m1919810", "GLMv5.3F", "XC_Xin_Cheng"};
    }

    public static Set<String> DEV_NAME =
            Set.of("matl114", "matl_test", "matl_test2", "mtl", "||matl_test", "||matl_test2", "||mtl");

    public static void authentication() {
        try {
            Class.forName("net.minecraft.client.MinecraftClient");
            DEV_ENV = true;
            Debug.info("Dev Environment Detected ?");
        } catch (Throwable e) {
        }
    }

    public static void initializeEnvironment() {
        modContainer = FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .orElseThrow(() -> new IllegalStateException("Missing mod container for " + MOD_ID));
    }

    @Override
    public void onInitialize() {
        instance = this;
        authentication();
        initializeEnvironment();
        Debug.info("SlimefunHelper, start!");
        Debug.info("SlimefunHelper start loading!");
        PluginManager pluginManager = PluginManager.getInstance();
        pluginManager.begin(new MainPlugin(getModContainer()));
        pluginManager.discover();
        pluginManager.advance(BootstrapPhase.CONFIG_READ);
        pluginManager.advance(BootstrapPhase.LISTENERS);
        pluginManager.advance(BootstrapPhase.MODULES);
        pluginManager.advance(BootstrapPhase.OTHER);
        pluginManager.complete();
        Debug.info("SlimefunHelper loading finish");
        // 注册 Meteor GUI 桥接模块, 使粘液助手出现在 Meteor 的 ClickGui 中
        Modules.get().add(new SlimeMeteorGuiModule());
        SlimeMeteorGuiModule.registerAllBridgeModules();
    }

    @Override
    public String getPackage() {
        return "me.matl114";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("matl114", "SlimefunHelper");
    }

    @Override
    public void onRegisterCategories() {
        // 在 Meteor 的 ClickGui 中注册粘液助手分类 (SM + 原版分组名) 与入口分类
        SlimeMeteorGuiModule.registerCategories();
    }
}
