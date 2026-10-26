package me.matl114.hooks.mixin.meteor;

import me.matl114.hooks.meteor.GuiWindowsPersistence;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 模块界面构建完成后恢复上一次保存的分类窗口位置 */
@Environment(EnvType.CLIENT)
@Mixin(value = ModulesScreen.class, remap = false)
public abstract class ModulesScreenMixin {
    @Inject(method = "initWidgets", at = @At("TAIL"))
    public void onInitWidgetsRestore(CallbackInfo ci) {
        GuiWindowsPersistence.restore((ModulesScreen) (Object) this);
    }
}
