package me.matl114.hooks.mixin.meteor;

import me.matl114.events.Event;
import me.matl114.events.Listener;
import me.matl114.hooks.meteor.GuiWindowsPersistence;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 追踪模块界面生命周期: 每次新界面创建时保存上一次界面的窗口排布, 并注册周期保存 (GUI 打开期间每 5 秒自动保存,
 * 直接退出游戏也不丢)。
 */
@Environment(EnvType.CLIENT)
@Mixin(value = GuiTheme.class, remap = false)
public abstract class GuiThemeMixin {
    private static TabScreen lastModulesScreen;

    private static int tickCounter;

    private static boolean tickSaverRegistered;

    @Inject(method = "modulesScreen", at = @At("RETURN"))
    public void onModulesScreenCreated(CallbackInfoReturnable<TabScreen> cir) {
        try {
            TabScreen current = cir.getReturnValue();
            if (lastModulesScreen != null && lastModulesScreen != current) {
                GuiWindowsPersistence.saveFromScreen(lastModulesScreen);
            }
            lastModulesScreen = current;
            if (!tickSaverRegistered) {
                tickSaverRegistered = true;
                Listener.getPostTick().registerHandler(GuiThemeMixin::onPostTick);
            }
        } catch (Throwable e) {
            me.matl114.utils.Debug.info("GUI位置: 生命周期追踪失败 " + e);
        }
    }

    private static void onPostTick(Event<Void> e) {
        tickCounter++;
        // 仅在模块界面处于打开状态时周期保存, 界面关闭后停止写盘
        if (tickCounter % 100 == 0
                && lastModulesScreen != null
                && net.minecraft.client.MinecraftClient.getInstance().currentScreen == lastModulesScreen) {
            GuiWindowsPersistence.saveFromScreen(lastModulesScreen);
        }
    }
}
