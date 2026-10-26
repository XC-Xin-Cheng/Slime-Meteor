package me.matl114.hooks.meteor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import me.matl114.utils.Debug;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 为 Meteor 的模块界面补上分类窗口位置持久化 (1.21.11 版 GUI 重写后 meteor 自身未实现)。 保存/恢复按窗口 id (即分类名,
 * 对所有附属的分类与搜索/收藏窗口通用)。
 */
public final class GuiWindowsPersistence {
    private static final Gson GSON = new Gson();
    private static Map<String, double[]> savedPositions;

    private GuiWindowsPersistence() {}

    private static Path positionFile() {
        return FabricLoader.getInstance().getGameDir().resolve("slimefunhelper/gui-windows.json");
    }

    private static Map<String, double[]> loadPositions() {
        if (savedPositions != null) {
            return savedPositions;
        }
        try {
            Path file = positionFile();
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    savedPositions = GSON.fromJson(
                            new InputStreamReader(in, StandardCharsets.UTF_8),
                            new TypeToken<Map<String, double[]>>() {}.getType());
                }
            }
        } catch (Throwable e) {
            Debug.info("GUI位置: 读取失败 " + e);
        }
        if (savedPositions == null) {
            savedPositions = new HashMap<>();
        }
        return savedPositions;
    }

    /** 模块界面构建完成后调用: 按窗口 id 恢复上一次保存的位置 */
    public static void restore(ModulesScreen screen) {
        try {
            Map<String, double[]> saved = loadPositions();
            if (saved.isEmpty()) {
                return;
            }
            Object controller = readField(screen, "controller");
            int applied = 0;
            for (Object o : (Iterable<?>) readField(controller, "windows")) {
                if (o instanceof WWindow window) {
                    double[] pos = saved.get(window.id);
                    if (pos != null && pos.length == 2) {
                        window.x = pos[0];
                        window.y = pos[1];
                        applied++;
                    }
                }
            }
            Debug.info("GUI位置: 已恢复 " + applied + " 个分类窗口位置");
        } catch (Throwable e) {
            Debug.info("GUI位置: 恢复失败 " + e);
        }
    }

    /** 保存指定模块界面上全部窗口的当前位置 */
    public static void saveFromScreen(Object screen) {
        try {
            Object root = readField(screen, "root");
            Map<String, double[]> positions = new HashMap<>();
            collectWindows((WWidget) root, positions);
            save(positions);
        } catch (Throwable e) {
            Debug.info("GUI位置: 保存失败 " + e);
        }
    }

    private static void save(Map<String, double[]> positions) {
        try {
            Path file = positionFile();
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(positions));
            Debug.info("GUI位置: 已保存 " + positions.size() + " 个窗口位置");
        } catch (Throwable e) {
            Debug.info("GUI位置: 写入失败 " + e);
        }
    }

    private static void collectWindows(WWidget widget, Map<String, double[]> out) {
        if (widget instanceof WWindow window && window.id != null) {
            out.put(window.id, new double[] {window.x, window.y});
        }
        if (widget instanceof WContainer container) {
            for (Cell<?> cell : container.cells) {
                collectWindows(cell.widget(), out);
            }
        }
    }

    private static Object readField(Object owner, String name) throws Exception {
        Class<?> c = owner.getClass();
        Field field = null;
        while (c != null && field == null) {
            try {
                field = c.getDeclaredField(name);
            } catch (NoSuchFieldException ex) {
                c = c.getSuperclass();
            }
        }
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        return field.get(owner);
    }
}
