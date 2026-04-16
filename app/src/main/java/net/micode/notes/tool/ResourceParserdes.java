package net.micode.notes.tool;

import android.content.Context;
import android.preference.PreferenceManager;

import net.micode.notes.R;
import net.micode.notes.ui.NotesPreferenceActivity;

/**
 * 资源解析器类
 *
 * 负责管理和提供便签应用中各种资源的访问接口。
 * 包括背景颜色、字体大小、小部件样式等资源的 ID 映射。
 *
 * 主要功能：
 * 1. 定义颜色常量（黄、蓝、白、绿、红）
 * 2. 定义字体大小常量（小、中、大、超大）
 * 3. 提供编辑界面背景资源访问
 * 4. 提供列表界面背景资源访问（支持首项、中间项、末项、单项）
 * 5. 提供桌面小部件背景资源访问（2x2 和 4x4 尺寸）
 * 6. 提供文字样式资源访问
 * 7. 支持随机背景色功能
 *
 * 设计模式：
 * - 静态工厂模式：通过静态方法获取各种资源
 * - 内部类分组：按功能将资源分组到不同的内部类中
 *
 * @see NotesPreferenceActivity
 */
public class ResourceParser {

    // ==================== 背景颜色常量 ====================

    /**
     * 黄色背景（索引 0）
     * 也是默认背景色
     */
    public static final int YELLOW           = 0;

    /**
     * 蓝色背景（索引 1）
     */
    public static final int BLUE             = 1;

    /**
     * 白色背景（索引 2）
     */
    public static final int WHITE            = 2;

    /**
     * 绿色背景（索引 3）
     */
    public static final int GREEN            = 3;

    /**
     * 红色背景（索引 4）
     */
    public static final int RED              = 4;

    /**
     * 默认背景颜色
     * 当用户未选择背景色时，使用黄色
     */
    public static final int BG_DEFAULT_COLOR = YELLOW;

    // ==================== 字体大小常量 ====================

    /**
     * 小号字体（索引 0）
     */
    public static final int TEXT_SMALL       = 0;

    /**
     * 中号字体（索引 1）
     * 也是默认字体大小
     */
    public static final int TEXT_MEDIUM      = 1;

    /**
     * 大号字体（索引 2）
     */
    public static final int TEXT_LARGE       = 2;

    /**
     * 超大号字体（索引 3）
     */
    public static final int TEXT_SUPER       = 3;

    /**
     * 默认字体大小
     * 当用户未设置字体大小时，使用中号字体
     */
    public static final int BG_DEFAULT_FONT_SIZE = TEXT_MEDIUM;

    // ==================== 内部类：便签编辑界面背景资源 ====================

    /**
     * 便签编辑界面背景资源类
     *
     * 提供编辑界面（NoteEditActivity）中便签的背景图片资源。
     * 支持主背景和标题栏背景两种。
     */
    public static class NoteBgResources {

        /**
         * 编辑界面主背景资源数组
         * 顺序与颜色常量对应：黄、蓝、白、绿、红
         */
        private final static int [] BG_EDIT_RESOURCES = new int [] {
                R.drawable.edit_yellow,   // 黄色背景
                R.drawable.edit_blue,     // 蓝色背景
                R.drawable.edit_white,    // 白色背景
                R.drawable.edit_green,    // 绿色背景
                R.drawable.edit_red       // 红色背景
        };

        /**
         * 编辑界面标题栏背景资源数组
         * 顺序与颜色常量对应：黄、蓝、白、绿、红
         */
        private final static int [] BG_EDIT_TITLE_RESOURCES = new int [] {
                R.drawable.edit_title_yellow,   // 黄色标题栏
                R.drawable.edit_title_blue,     // 蓝色标题栏
                R.drawable.edit_title_white,    // 白色标题栏
                R.drawable.edit_title_green,    // 绿色标题栏
                R.drawable.edit_title_red       // 红色标题栏
        };

        /**
         * 获取编辑界面主背景资源 ID
         *
         * @param id 颜色索引（YELLOW/BLUE/WHITE/GREEN/RED）
         * @return 对应的图片资源 ID
         */
        public static int getNoteBgResource(int id) {
            return BG_EDIT_RESOURCES[id];
        }

        /**
         * 获取编辑界面标题栏背景资源 ID
         *
         * @param id 颜色索引
         * @return 对应的图片资源 ID
         */
        public static int getNoteTitleBgResource(int id) {
            return BG_EDIT_TITLE_RESOURCES[id];
        }
    }

    /**
     * 获取默认背景颜色 ID
     *
     * 根据用户设置决定是否使用随机背景色。
     *
     * 随机背景色功能：
     * - 用户在设置中开启 "随机背景色" 后
     * - 每次创建新便签时随机选择一种背景色
     * - 增加视觉多样性，提升用户体验
     *
     * @param context 上下文，用于访问 SharedPreferences
     * @return 背景颜色索引
     */
    public static int getDefaultBgId(Context context) {
        // 检查用户是否开启了随机背景色功能
        if (PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                NotesPreferenceActivity.PREFERENCE_SET_BG_COLOR_KEY, false)) {
            // 随机返回 0-4 之间的颜色索引
            return (int) (Math.random() * NoteBgResources.BG_EDIT_RESOURCES.length);
        } else {
            // 返回默认颜色（黄色）
            return BG_DEFAULT_COLOR;
        }
    }

    // ==================== 内部类：便签列表界面背景资源 ====================

    /**
     * 便签列表界面背景资源类
     *
     * 提供便签列表（NotesListActivity）中各项的背景图片资源。
     *
     * 列表项背景类型：
     * - FIRST：列表中的第一项（顶部带圆角）
     * - NORMAL：列表中的中间项（无圆角）
     * - LAST：列表中的最后一项（底部带圆角）
     * - SINGLE：列表中唯一的一项（四周都带圆角）
     *
     * 为什么要区分这些类型？
     * 为了在列表视图中实现卡片式的圆角效果，提升视觉体验。
     */
    public static class NoteItemBgResources {

        /**
         * 第一项背景资源数组
         * 用于列表中的第一个项目（顶部圆角）
         */
        private final static int [] BG_FIRST_RESOURCES = new int [] {
                R.drawable.list_yellow_up,   // 黄色 - 顶部
                R.drawable.list_blue_up,     // 蓝色 - 顶部
                R.drawable.list_white_up,    // 白色 - 顶部
                R.drawable.list_green_up,    // 绿色 - 顶部
                R.drawable.list_red_up       // 红色 - 顶部
        };

        /**
         * 中间项背景资源数组
         * 用于列表中的中间项目（无圆角）
         */
        private final static int [] BG_NORMAL_RESOURCES = new int [] {
                R.drawable.list_yellow_middle,   // 黄色 - 中间
                R.drawable.list_blue_middle,     // 蓝色 - 中间
                R.drawable.list_white_middle,    // 白色 - 中间
                R.drawable.list_green_middle,    // 绿色 - 中间
                R.drawable.list_red_middle       // 红色 - 中间
        };

        /**
         * 最后一项背景资源数组
         * 用于列表中的最后一个项目（底部圆角）
         */
        private final static int [] BG_LAST_RESOURCES = new int [] {
                R.drawable.list_yellow_down,   // 黄色 - 底部
                R.drawable.list_blue_down,     // 蓝色 - 底部
                R.drawable.list_white_down,    // 白色 - 底部
                R.drawable.list_green_down,    // 绿色 - 底部
                R.drawable.list_red_down       // 红色 - 底部
        };

        /**
         * 唯一项背景资源数组
         * 用于列表中只有一个项目的情况（四周圆角）
         */
        private final static int [] BG_SINGLE_RESOURCES = new int [] {
                R.drawable.list_yellow_single,   // 黄色 - 单独
                R.drawable.list_blue_single,     // 蓝色 - 单独
                R.drawable.list_white_single,    // 白色 - 单独
                R.drawable.list_green_single,    // 绿色 - 单独
                R.drawable.list_red_single       // 红色 - 单独
        };

        /**
         * 获取第一项背景资源
         *
         * @param id 颜色索引
         * @return 图片资源 ID
         */
        public static int getNoteBgFirstRes(int id) {
            return BG_FIRST_RESOURCES[id];
        }

        /**
         * 获取最后一项背景资源
         *
         * @param id 颜色索引
         * @return 图片资源 ID
         */
        public static int getNoteBgLastRes(int id) {
            return BG_LAST_RESOURCES[id];
        }

        /**
         * 获取唯一项背景资源
         *
         * @param id 颜色索引
         * @return 图片资源 ID
         */
        public static int getNoteBgSingleRes(int id) {
            return BG_SINGLE_RESOURCES[id];
        }

        /**
         * 获取中间项背景资源
         *
         * @param id 颜色索引
         * @return 图片资源 ID
         */
        public static int getNoteBgNormalRes(int id) {
            return BG_NORMAL_RESOURCES[id];
        }

        /**
         * 获取文件夹背景资源
         *
         * 文件夹在列表中显示为特殊的背景样式
         *
         * @return 文件夹背景图片资源 ID
         */
        public static int getFolderBgRes() {
            return R.drawable.list_folder;
        }
    }

    // ==================== 内部类：桌面小部件背景资源 ====================

    /**
     * 桌面小部件背景资源类
     *
     * 提供桌面小部件（App Widget）的背景图片资源。
     * 支持两种尺寸：2x2 和 4x4 网格
     */
    public static class WidgetBgResources {

        /**
         * 2x2 小部件背景资源数组
         * 适用于 2x2 网格大小的桌面小部件
         */
        private final static int [] BG_2X_RESOURCES = new int [] {
                R.drawable.widget_2x_yellow,   // 黄色 - 2x2
                R.drawable.widget_2x_blue,     // 蓝色 - 2x2
                R.drawable.widget_2x_white,    // 白色 - 2x2
                R.drawable.widget_2x_green,    // 绿色 - 2x2
                R.drawable.widget_2x_red,      // 红色 - 2x2
        };

        /**
         * 获取 2x2 小部件背景资源
         *
         * @param id 颜色索引
         * @return 图片资源 ID
         */
        public static int getWidget2xBgResource(int id) {
            return BG_2X_RESOURCES[id];
        }

        /**
         * 4x4 小部件背景资源数组
         * 适用于 4x4 网格大小的桌面小部件
         */
        private final static int [] BG_4X_RESOURCES = new int [] {
                R.drawable.widget_4x_yellow,   // 黄色 - 4x4
                R.drawable.widget_4x_blue,     // 蓝色 - 4x4
                R.drawable.widget_4x_white,    // 白色 - 4x4
                R.drawable.widget_4x_green,    // 绿色 - 4x4
                R.drawable.widget_4x_red       // 红色 - 4x4
        };

        /**
         * 获取 4x4 小部件背景资源
         *
         * @param id 颜色索引
         * @return 图片资源 ID
         */
        public static int getWidget4xBgResource(int id) {
            return BG_4X_RESOURCES[id];
        }
    }

    // ==================== 内部类：文字样式资源 ====================

    /**
     * 文字样式资源类
     *
     * 提供不同大小的文字样式资源。
     * 字体大小分为：小、中、大、超大四个级别。
     */
    public static class TextAppearanceResources {

        /**
         * 文字样式资源数组
         * 顺序与小、中、大、超大的常量对应
         */
        private final static int [] TEXTAPPEARANCE_RESOURCES = new int [] {
                R.style.TextAppearanceNormal,   // 正常大小（小）
                R.style.TextAppearanceMedium,   // 中等大小
                R.style.TextAppearanceLarge,    // 较大大小
                R.style.TextAppearanceSuper     // 超大小
        };

        /**
         * 获取文字样式资源 ID
         *
         * 注意：存在一个已知问题（HACKME）
         * 当从 SharedPreferences 读取的 ID 超出数组范围时，
         * 返回默认字体大小作为 fallback。
         *
         * @param id 字体大小索引
         * @return 样式资源 ID
         */
        public static int getTexAppearanceResource(int id) {
            /**
             * HACKME: 修复存储在 SharedPreferences 中的资源 ID 的 bug。
             * 这个 ID 可能大于资源数组的长度，在这种情况下，
             * 返回 {@link ResourceParser#BG_DEFAULT_FONT_SIZE}
             */
            if (id >= TEXTAPPEARANCE_RESOURCES.length) {
                return BG_DEFAULT_FONT_SIZE;
            }
            return TEXTAPPEARANCE_RESOURCES[id];
        }

        /**
         * 获取文字样式资源数量
         *
         * @return 可用的字体大小级别数量
         */
        public static int getResourcesSize() {
            return TEXTAPPEARANCE_RESOURCES.length;
        }
    }
}