/**
 * 便签编辑Activity
 * 功能：创建新便签、编辑已有便签、设置提醒、更改背景颜色/字体大小、
 *       切换待办清单模式、分享便签、添加快捷方式到桌面等
 *
 * 实现接口：
 * - OnClickListener: 处理点击事件（颜色选择、字体选择等）
 * - NoteSettingChangedListener: 监听便签设置变化（背景色、提醒时间等）
 * - OnTextViewChangeListener: 监听待办清单中文本的变化
 */
package net.micode.notes.ui;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.SearchManager;
import android.appwidget.AppWidgetManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.TextNote;
import net.micode.notes.model.WorkingNote;
import net.micode.notes.model.WorkingNote.NoteSettingChangedListener;
import net.micode.notes.tool.DataUtils;
import net.micode.notes.tool.ResourceParser;
import net.micode.notes.tool.ResourceParser.TextAppearanceResources;
import net.micode.notes.ui.DateTimePickerDialog.OnDateTimeSetListener;
import net.micode.notes.ui.NoteEditText.OnTextViewChangeListener;
import net.micode.notes.widget.NoteWidgetProvider_2x;
import net.micode.notes.widget.NoteWidgetProvider_4x;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class NoteEditActivity extends Activity implements OnClickListener,
        NoteSettingChangedListener, OnTextViewChangeListener {
    // 私有的静态内部类，用于列表头部视图的缓存（ViewHolder设计模式）
    // 作用：避免在滚动列表时重复执行findViewById，提升性能
    private class HeadViewHolder {
        public TextView tvModified;// 显示“修改时间”或“最后编辑时间”的文本框

        public ImageView ivAlertIcon;// 提醒图标（例如闹钟小图标），用于显示该便签设置了提醒

        public TextView tvAlertDate;// 提醒日期文本，显示具体的提醒时间（如“今天 14:30”）

        public ImageView ibSetBgColor;// 设置背景颜色的按钮（可能是画笔或调色板图标），点击后可以更改便签卡片背景色
    }

    // 静态映射表：将背景颜色选择按钮的ID（资源ID）映射到对应的颜色常量
    // Map的键：按钮的R.id（如R.id.iv_bg_yellow）
    // Map的值：颜色常量（来自ResourceParser类，如ResourceParser.YELLOW）
    private static final Map<Integer, Integer> sBgSelectorBtnsMap = new HashMap<Integer, Integer>();
    // 静态初始化块，在类首次加载时执行一次，用于填充上面的映射表
    static {
        sBgSelectorBtnsMap.put(R.id.iv_bg_yellow, ResourceParser.YELLOW);// 黄色按钮 -> 黄色常量
        sBgSelectorBtnsMap.put(R.id.iv_bg_red, ResourceParser.RED);// 红色按钮 -> 红色常量
        sBgSelectorBtnsMap.put(R.id.iv_bg_blue, ResourceParser.BLUE);// 蓝色按钮 -> 蓝色常量
        sBgSelectorBtnsMap.put(R.id.iv_bg_green, ResourceParser.GREEN);// 绿色按钮 -> 绿色常量
        sBgSelectorBtnsMap.put(R.id.iv_bg_white, ResourceParser.WHITE);// 白色按钮 -> 白色常量
    }

    // 静态映射表：将背景颜色常量映射到对应的"选中状态"图标ID
    // Map的键：颜色常量（来自ResourceParser类，如ResourceParser.YELLOW）
    // Map的值：选中状态图标的资源ID（如R.id.iv_bg_yellow_select，通常是带对勾或高亮边框的图标）
    private static final Map<Integer, Integer> sBgSelectorSelectionMap = new HashMap<Integer, Integer>();
    // 静态初始化块，在类首次加载时执行一次，用于填充上面的映射表
    static {
        sBgSelectorSelectionMap.put(ResourceParser.YELLOW, R.id.iv_bg_yellow_select);// 黄色常量 -> 黄色按钮的选中状态图标
        sBgSelectorSelectionMap.put(ResourceParser.RED, R.id.iv_bg_red_select);// 红色常量 -> 红色按钮的选中状态图标
        sBgSelectorSelectionMap.put(ResourceParser.BLUE, R.id.iv_bg_blue_select);// 蓝色常量 -> 蓝色按钮的选中状态图标
        sBgSelectorSelectionMap.put(ResourceParser.GREEN, R.id.iv_bg_green_select);// 绿色常量 -> 绿色按钮的选中状态图标
        sBgSelectorSelectionMap.put(ResourceParser.WHITE, R.id.iv_bg_white_select);// 白色常量 -> 白色按钮的选中状态图标
    }

    // 映射表：按钮ID → 字体大小常量
    // 用于：用户点击字体大小按钮时，获取对应的字体大小值
    private static final Map<Integer, Integer> sFontSizeBtnsMap = new HashMap<Integer, Integer>();
    // 静态初始化块，填充字体大小按钮的映射表
    static {
        sFontSizeBtnsMap.put(R.id.ll_font_large, ResourceParser.TEXT_LARGE);// 大号字体按钮（可能是LinearLayout） -> 大号字体常量
        sFontSizeBtnsMap.put(R.id.ll_font_small, ResourceParser.TEXT_SMALL);// 小号字体按钮 -> 小号字体常量
        sFontSizeBtnsMap.put(R.id.ll_font_normal, ResourceParser.TEXT_MEDIUM);// 正常字体按钮 -> 中号字体常量
        sFontSizeBtnsMap.put(R.id.ll_font_super, ResourceParser.TEXT_SUPER);// 超大号字体按钮 -> 超大号字体常量
    }

    // 映射表：字体大小常量 → 选中图标ID
    // 用于：显示当前便签使用什么字体大小时，高亮对应的选中图标
    private static final Map<Integer, Integer> sFontSelectorSelectionMap = new HashMap<Integer, Integer>();
    // 静态初始化块，填充字体选中状态的映射表
    static {
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_LARGE, R.id.iv_large_select);// 大号字体常量 -> 大号字体的选中图标
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SMALL, R.id.iv_small_select);// 小号字体常量 -> 小号字体的选中图标
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_MEDIUM, R.id.iv_medium_select);// 中号字体常量 -> 中号字体的选中图标
        sFontSelectorSelectionMap.put(ResourceParser.TEXT_SUPER, R.id.iv_super_select);// 超大号字体常量 -> 超大号字体的选中图标
    }

    // 调试相关
    // 日志标签，用于Logcat中过滤该类的日志输出
    private static final String TAG = "NoteEditActivity";
    // UI组件相关
    // 列表头部的ViewHolder（缓存了头部视图的引用，如修改时间、提醒图标等）
    private HeadViewHolder mNoteHeaderHolder;
    // 头部视图面板（包含便签的元信息显示区域）
    private View mHeadViewPanel;
    // 背景颜色选择器面板（点击后弹出颜色选择界面
    private View mNoteBgColorSelector;
    // 字体大小选择器面板（点击后弹出字体大小选项）
    private View mFontSizeSelector;
    // 便签内容编辑器（核心输入控件，用于编辑便签文本）
    private EditText mNoteEditor;
    // 便签编辑器面板（包裹EditText的容器，可能包含格式工具栏）
    private View mNoteEditorPanel;
    // 正在编辑的便签对象（工作副本，封装了便签的数据和操作）
    private WorkingNote mWorkingNote;
    //偏好设置相关
    // 共享偏好实例（用于保存用户设置，如字体大小偏好）
    private SharedPreferences mSharedPrefs;
    // 当前使用的字体大小ID（对应ResourceParser中的TEXT_XXX常量）
    private int mFontSizeId;
    // 字体大小设置的偏好键名（用于保存到SharedPreferences）
    private static final String PREFERENCE_FONT_SIZE = "pref_font_size";
    // 快捷方式相关
    // 快捷方式图标标题的最大长度（限制为10个字符，超出可能截断）
    private static final int SHORTCUT_ICON_TITLE_MAX_LEN = 10;
    // 复选框符号相关
    // 已勾选状态的符号：√（对勾，Unicode字符U+221A）
    public static final String TAG_CHECKED = String.valueOf('\u221A');
    // 未勾选状态的符号：□（空心方框，Unicode字符U+25A1）
    public static final String TAG_UNCHECKED = String.valueOf('\u25A1');
    //便签列表相关
    // 编辑文本列表容器（可能用于显示便签中的待办事项列表或多段文本）


    private LinearLayout mEditTextList;
    //搜索高亮相关
    // 用户搜索的关键词（从外部传入，用于在便签中高亮显示）

    private String mUserQuery;
    // 正则表达式模式（用于匹配搜索关键词，实现高亮功能）
    private Pattern mPattern;


    @Override
    /**
     * Activity创建时的回调方法
     * @param savedInstanceState 保存的实例状态（如果Activity因配置变更被销毁重建，此参数非null）
     */
    protected void onCreate(Bundle savedInstanceState) {
        // 调用父类的onCreate方法，完成系统级初始化（
        super.onCreate(savedInstanceState);
        // 设置内容视图，从布局文件 R.layout.note_edit 中加载UI组件
        // note_edit.xml 定义了便签编辑界面的整体布局
        this.setContentView(R.layout.note_edit);

        /**
         * 初始化Activity状态的核心逻辑
         *
         * 条件判断：
         * 1. savedInstanceState == null：Activity是首次创建（不是重建）
         * 2. !initActivityState(getIntent())：从Intent中解析数据失败
         *
         * 如果满足上述条件（首次创建且初始化失败），则结束Activity
         */
        if (savedInstanceState == null && !initActivityState(getIntent())) {
            finish();// 结束当前Activity，返回到上一页面
            return;// 提前返回，不再执行后续代码
        }
        /**
         * 初始化资源
         * 包括：查找视图控件、设置监听器、恢复用户偏好设置等
         * 只有当Activity状态初始化成功时才会执行到这里
         */
        initResources();
    }

    /**
     * Current activity may be killed when the memory is low. Once it is killed, for another time
     * user load this activity, we should restore the former state
     */
    @Override
    /**
     * Activity被系统销毁后重建时调用的回调方法
     * 触发场景：屏幕旋转、内存不足被系统杀死后恢复、配置变更等
     *
     * @param savedInstanceState 系统保存的Bundle对象，包含之前的状态数据
     */
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        // 调用父类方法，让系统恢复默认的UI状态（如EditText中的文本）
        super.onRestoreInstanceState(savedInstanceState);
        /**
         * 检查条件：
         * 1. savedInstanceState != null：确保有保存的状态数据
         * 2. savedInstanceState.containsKey(Intent.EXTRA_UID)：检查是否包含便签的UID
         *
         * EXTRA_UID 通常用于存储便签的唯一标识ID
         */
        if (savedInstanceState != null && savedInstanceState.containsKey(Intent.EXTRA_UID)) {
            // 创建一个新的Intent对象（用于重新初始化Activity状态）
            Intent intent = new Intent(Intent.ACTION_VIEW);
            // 从保存的Bundle中取出之前保存的便签UID，放回Intent中
            // 这样initActivityState可以从Intent中获取到正确的便签ID
            intent.putExtra(Intent.EXTRA_UID, savedInstanceState.getLong(Intent.EXTRA_UID));
            /**
             * 使用恢复出来的Intent重新初始化Activity状态
             * initActivityState() 会：
             * - 从Intent中解析便签ID
             * - 加载对应的WorkingNote对象
             * - 初始化搜索高亮等状态
             */
            if (!initActivityState(intent)) {// 如果初始化失败（例如便签已被删除），则结束Activity
                finish();
                return;
            }
            Log.d(TAG, "Restoring from killed activity");// 输出调试日志，表示从被销毁的状态中恢复
        }
    }

    /**
     * 初始化Activity状态
     * 根据Intent的Action类型，决定是打开已有便签还是创建新便签
     *
     * @param intent 启动Activity的Intent对象
     * @return true: 初始化成功, false: 初始化失败（失败时会自动关闭Activity）
     */
    private boolean initActivityState(Intent intent) {
        /**
         * 如果用户指定了ACTION_VIEW但没有提供便签ID，
         * 则跳转到便签列表页面
         */
        mWorkingNote = null;  // 重置工作便签对象

        // 情况1：查看已有便签（ACTION_VIEW）
        if (TextUtils.equals(Intent.ACTION_VIEW, intent.getAction())) {
            // 从Intent中获取便签ID，默认值为0
            long noteId = intent.getLongExtra(Intent.EXTRA_UID, 0);
            mUserQuery = "";  // 初始化搜索关键词为空

            /**
             * 从搜索结果中启动
             * 如果Intent包含搜索数据，说明用户是从搜索结果点击进入的
             */
            if (intent.hasExtra(SearchManager.EXTRA_DATA_KEY)) {
                // 获取便签ID（搜索结果中存储为String类型，需要解析）
                noteId = Long.parseLong(intent.getStringExtra(SearchManager.EXTRA_DATA_KEY));
                // 获取用户输入的搜索关键词，用于后续高亮显示
                mUserQuery = intent.getStringExtra(SearchManager.USER_QUERY);
            }

            // 检查便签是否存在于数据库中（且未被删除）
            if (!DataUtils.visibleInNoteDatabase(getContentResolver(), noteId, Notes.TYPE_NOTE)) {
                // 便签不存在：跳转到便签列表页面
                Intent jump = new Intent(this, NotesListActivity.class);
                startActivity(jump);
                // 提示用户便签不存在
                showToast(R.string.error_note_not_exist);
                // 关闭当前Activity
                finish();
                return false;
            } else {
                // 便签存在：加载便签数据
                mWorkingNote = WorkingNote.load(this, noteId);
                if (mWorkingNote == null) {
                    // 加载失败（可能是数据损坏）
                    Log.e(TAG, "load note failed with note id" + noteId);
                    finish();
                    return false;
                }
            }
            // 设置软键盘模式：初始隐藏，调整布局大小以适应软键盘
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
                            | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        // 情况2：新建便签（ACTION_INSERT_OR_EDIT）
        else if(TextUtils.equals(Intent.ACTION_INSERT_OR_EDIT, intent.getAction())) {
            // 获取文件夹ID（便签所属的文件夹，默认为0即根目录）
            long folderId = intent.getLongExtra(Notes.INTENT_EXTRA_FOLDER_ID, 0);
            // 获取小部件ID（如果是从桌面小部件进入）
            int widgetId = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            // 获取小部件类型（2x2或4x4）
            int widgetType = intent.getIntExtra(Notes.INTENT_EXTRA_WIDGET_TYPE,
                    Notes.TYPE_WIDGET_INVALIDE);
            // 获取背景资源ID（默认背景）
            int bgResId = intent.getIntExtra(Notes.INTENT_EXTRA_BACKGROUND_ID,
                    ResourceParser.getDefaultBgId(this));

            // 解析通话记录便签（从拨号盘添加的通话记录便签）
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            long callDate = intent.getLongExtra(Notes.INTENT_EXTRA_CALL_DATE, 0);

            if (callDate != 0 && phoneNumber != null) {
                // 是通话记录便签
                if (TextUtils.isEmpty(phoneNumber)) {
                    Log.w(TAG, "The call record number is null");
                }
                long noteId = 0;
                // 检查是否已存在相同电话号码和日期的通话记录便签
                if ((noteId = DataUtils.getNoteIdByPhoneNumberAndCallDate(getContentResolver(),
                        phoneNumber, callDate)) > 0) {
                    // 已存在：加载已有的通话记录便签
                    mWorkingNote = WorkingNote.load(this, noteId);
                    if (mWorkingNote == null) {
                        Log.e(TAG, "load call note failed with note id" + noteId);
                        finish();
                        return false;
                    }
                } else {
                    // 不存在：创建新的空便签，然后转换为通话记录便签
                    mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId,
                            widgetType, bgResId);
                    mWorkingNote.convertToCallNote(phoneNumber, callDate);
                }
            } else {
                // 普通新建便签（非通话记录）
                mWorkingNote = WorkingNote.createEmptyNote(this, folderId, widgetId, widgetType,
                        bgResId);
            }

            // 设置软键盘模式：显示软键盘，调整布局大小
            getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                            | WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        } else {
            // 情况3：不支持的Action类型
            Log.e(TAG, "Intent not specified action, should not support");
            finish();
            return false;
        }

        // 设置便签设置变化监听器，以便在背景色、提醒等变化时更新UI
        mWorkingNote.setOnSettingStatusChangedListener(this);
        return true;
    }

    /**
     * Activity恢复前台时调用
     * 生命周期方法，在onCreate、onRestart之后，onStart之后调用
     * 用于刷新便签显示（字体、内容、背景色、提醒信息等）
     */
    @Override
    protected void onResume() {
        super.onResume();
        initNoteScreen();  // 初始化便签显示界面
    }

    /**
     * 初始化便签显示界面
     * 设置字体大小、内容显示模式（普通/待办清单）、背景颜色、修改时间、提醒信息等
     */
    private void initNoteScreen() {
        // 设置编辑框的字体大小（从SharedPreferences读取用户偏好）
        mNoteEditor.setTextAppearance(this, TextAppearanceResources
                .getTexAppearanceResource(mFontSizeId));

        // 根据便签模式选择不同的显示方式
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 待办清单模式：切换到清单列表视图
            switchToListMode(mWorkingNote.getContent());
        } else {
            // 普通模式：设置文本内容，并高亮搜索关键词（如果有）
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            // 将光标移动到文本末尾
            mNoteEditor.setSelection(mNoteEditor.getText().length());
        }

        // 隐藏所有背景颜色选中图标（对勾标记）
        for (Integer id : sBgSelectorSelectionMap.keySet()) {
            findViewById(sBgSelectorSelectionMap.get(id)).setVisibility(View.GONE);
        }

        // 设置头部面板背景色（标题栏区域）
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
        // 设置编辑面板背景色（便签主体区域）
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());

        // 设置修改时间（使用DateUtils格式化日期时间）
        mNoteHeaderHolder.tvModified.setText(DateUtils.formatDateTime(this,
                mWorkingNote.getModifiedDate(), DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_NUMERIC_DATE | DateUtils.FORMAT_SHOW_TIME
                        | DateUtils.FORMAT_SHOW_YEAR));

        /**
         * TODO: 添加设置提醒的菜单项。目前暂时禁用，因为DateTimePicker还未就绪
         * 注意：这是一个开发标记，提醒开发者后续需要完成此功能
         */
        showAlertHeader();  // 显示提醒信息头部
    }

    /**
     * 显示提醒信息头部
     * 如果便签设置了提醒，显示提醒时间和闹钟图标；
     * 如果提醒已过期，显示"已过期"文字；
     * 如果没有提醒，隐藏相关UI元素
     */
    private void showAlertHeader() {
        if (mWorkingNote.hasClockAlert()) {  // 判断是否有提醒
            long time = System.currentTimeMillis();  // 当前时间
            if (time > mWorkingNote.getAlertDate()) {
                // 提醒时间已过期
                mNoteHeaderHolder.tvAlertDate.setText(R.string.note_alert_expired);
            } else {
                // 提醒时间未到，显示相对时间（如"5分钟后"、"1小时后"）
                mNoteHeaderHolder.tvAlertDate.setText(DateUtils.getRelativeTimeSpanString(
                        mWorkingNote.getAlertDate(), time, DateUtils.MINUTE_IN_MILLIS));
            }
            // 显示提醒日期文本和闹钟图标
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.VISIBLE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.VISIBLE);
        } else {
            // 无提醒：隐藏相关UI
            mNoteHeaderHolder.tvAlertDate.setVisibility(View.GONE);
            mNoteHeaderHolder.ivAlertIcon.setVisibility(View.GONE);
        };
    }

    /**
     * 当Activity接收到新的Intent时调用
     * 例如：从搜索结果再次进入，或从桌面小部件打开
     *
     * @param intent 新的Intent对象
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 重新初始化Activity状态（切换便签内容）
        initActivityState(intent);
    }

    /**
     * 保存Activity状态（在内存不足被杀死前调用）
     * 用于在屏幕旋转等配置变更时保存数据
     *
     * @param outState 用于保存状态的Bundle对象
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        /**
         * 对于没有便签ID的新便签，需要先保存以生成ID
         * 如果正在编辑的便签不值得保存（空内容），则没有ID，等同于创建新便签
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();  // 先保存，生成便签ID
        }
        // 保存便签ID到Bundle
        outState.putLong(Intent.EXTRA_UID, mWorkingNote.getNoteId());
        Log.d(TAG, "Save working note id: " + mWorkingNote.getNoteId() + " onSaveInstanceState");
    }

    /**
     * 分发触摸事件
     * 实现点击选择器外部时自动关闭的功能（类似下拉菜单的点击外部关闭）
     *
     * @param ev 触摸事件
     * @return true: 事件已处理, false: 继续传递
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        // 如果背景颜色选择器可见，且触摸点不在选择器范围内
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mNoteBgColorSelector, ev)) {
            mNoteBgColorSelector.setVisibility(View.GONE);  // 关闭颜色选择器
            return true;  // 事件已处理
        }

        // 如果字体大小选择器可见，且触摸点不在选择器范围内
        if (mFontSizeSelector.getVisibility() == View.VISIBLE
                && !inRangeOfView(mFontSizeSelector, ev)) {
            mFontSizeSelector.setVisibility(View.GONE);  // 关闭字体选择器
            return true;  // 事件已处理
        }
        // 其他情况：交给父类处理
        return super.dispatchTouchEvent(ev);
    }

    /**
     * 判断触摸点是否在指定视图的范围内
     *
     * @param view 目标视图
     * @param ev 触摸事件
     * @return true: 触摸点在视图范围内, false: 在视图范围外
     */
    private boolean inRangeOfView(View view, MotionEvent ev) {
        int []location = new int[2];
        view.getLocationOnScreen(location);  // 获取视图在屏幕上的位置
        int x = location[0];  // 视图左上角X坐标
        int y = location[1];  // 视图左上角Y坐标

        // 判断触摸点是否在视图的矩形区域内
        if (ev.getX() < x
                || ev.getX() > (x + view.getWidth())
                || ev.getY() < y
                || ev.getY() > (y + view.getHeight())) {
            return false;  // 在范围外
        }
        return true;  // 在范围内
    }

    /**
     * 初始化UI资源
     * 查找所有视图控件、设置点击监听器、恢复用户偏好设置
     * 在onCreate中调用，只执行一次
     */
    private void initResources() {
        // 初始化头部视图
        mHeadViewPanel = findViewById(R.id.note_title);
        mNoteHeaderHolder = new HeadViewHolder();
        mNoteHeaderHolder.tvModified = (TextView) findViewById(R.id.tv_modified_date);
        mNoteHeaderHolder.ivAlertIcon = (ImageView) findViewById(R.id.iv_alert_icon);
        mNoteHeaderHolder.tvAlertDate = (TextView) findViewById(R.id.tv_alert_date);
        mNoteHeaderHolder.ibSetBgColor = (ImageView) findViewById(R.id.btn_set_bg_color);
        mNoteHeaderHolder.ibSetBgColor.setOnClickListener(this);  // 设置点击监听

        // 初始化编辑器
        mNoteEditor = (EditText) findViewById(R.id.note_edit_view);
        mNoteEditorPanel = findViewById(R.id.sv_note_edit);

        // 初始化背景颜色选择器及其按钮
        mNoteBgColorSelector = findViewById(R.id.note_bg_color_selector);
        for (int id : sBgSelectorBtnsMap.keySet()) {
            ImageView iv = (ImageView) findViewById(id);
            iv.setOnClickListener(this);  // 为每个颜色按钮设置监听
        }

        // 初始化字体大小选择器及其按钮
        mFontSizeSelector = findViewById(R.id.font_size_selector);
        for (int id : sFontSizeBtnsMap.keySet()) {
            View view = findViewById(id);
            view.setOnClickListener(this);  // 为每个字体按钮设置监听
        };

        // 恢复字体大小偏好设置
        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mFontSizeId = mSharedPrefs.getInt(PREFERENCE_FONT_SIZE, ResourceParser.BG_DEFAULT_FONT_SIZE);

        /**
         * HACKME: 修复存储资源ID在SharedPreferences中的bug
         * 资源ID可能大于资源数组的长度，这种情况下返回默认字体大小
         * 这是一个临时解决方案，防止应用崩溃
         */
        if(mFontSizeId >= TextAppearanceResources.getResourcesSize()) {
            mFontSizeId = ResourceParser.BG_DEFAULT_FONT_SIZE;
        }

        // 初始化待办清单列表容器
        mEditTextList = (LinearLayout) findViewById(R.id.note_edit_list);
    }

    /**
     * Activity进入后台时调用
     * 自动保存便签内容，并关闭弹出面板
     */
    @Override
    protected void onPause() {
        super.onPause();
        // 保存便签，如果保存成功则输出日志
        if(saveNote()) {
            Log.d(TAG, "Note data was saved with length:" + mWorkingNote.getContent().length());
        }
        clearSettingState();  // 关闭颜色/字体选择器面板
    }

    /**
     * 更新桌面小部件
     * 当便签内容变化时，通知对应的桌面小部件刷新显示
     */
    private void updateWidget() {
        Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);

        // 根据小部件类型选择对应的Provider
        if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_2X) {
            intent.setClass(this, NoteWidgetProvider_2x.class);  // 2x2小部件
        } else if (mWorkingNote.getWidgetType() == Notes.TYPE_WIDGET_4X) {
            intent.setClass(this, NoteWidgetProvider_4x.class);  // 4x4小部件
        } else {
            Log.e(TAG, "Unspported widget type");
            return;
        }

        // 传入需要更新小部件的ID
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[] {
                mWorkingNote.getWidgetId()
        });

        // 发送广播通知小部件更新
        sendBroadcast(intent);
        setResult(RESULT_OK, intent);
    }

    /**
     * 处理点击事件
     * 根据点击的视图ID执行相应操作
     *
     * @param v 被点击的视图
     */
    public void onClick(View v) {
        int id = v.getId();

        // 点击背景颜色设置按钮：显示颜色选择器
        if (id == R.id.btn_set_bg_color) {
            mNoteBgColorSelector.setVisibility(View.VISIBLE);
            // 显示当前颜色的选中图标
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.VISIBLE);
        }
        // 点击颜色按钮：更改背景颜色
        else if (sBgSelectorBtnsMap.containsKey(id)) {
            // 隐藏当前颜色的选中图标
            findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                    View.GONE);
            // 设置新的背景颜色
            mWorkingNote.setBgColorId(sBgSelectorBtnsMap.get(id));
            // 关闭颜色选择器面板
            mNoteBgColorSelector.setVisibility(View.GONE);
        }
        // 点击字体大小按钮：更改字体大小
        else if (sFontSizeBtnsMap.containsKey(id)) {
            // 隐藏当前字体大小的选中图标
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.GONE);
            // 更新字体大小ID
            mFontSizeId = sFontSizeBtnsMap.get(id);
            // 保存到SharedPreferences
            mSharedPrefs.edit().putInt(PREFERENCE_FONT_SIZE, mFontSizeId).commit();
            // 显示新字体大小的选中图标
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);

            // 根据当前模式刷新显示
            if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
                // 待办清单模式：重新加载清单
                getWorkingText();
                switchToListMode(mWorkingNote.getContent());
            } else {
                // 普通模式：直接设置编辑器字体
                mNoteEditor.setTextAppearance(this,
                        TextAppearanceResources.getTexAppearanceResource(mFontSizeId));
            }
            // 关闭字体选择器面板
            mFontSizeSelector.setVisibility(View.GONE);
        }
    }

    /**
     * 处理返回键按下事件
     * 优先关闭弹出面板，再保存并退出
     */
    @Override
    public void onBackPressed() {
        // 如果有弹出面板，先关闭
        if(clearSettingState()) {
            return;
        }

        // 保存便签
        saveNote();
        // 执行返回操作
        super.onBackPressed();
    }

    /**
     * 关闭所有弹出面板（背景颜色选择器、字体大小选择器）
     *
     * @return true: 有面板被关闭, false: 没有面板需要关闭
     */
    private boolean clearSettingState() {
        if (mNoteBgColorSelector.getVisibility() == View.VISIBLE) {
            mNoteBgColorSelector.setVisibility(View.GONE);
            return true;
        } else if (mFontSizeSelector.getVisibility() == View.VISIBLE) {
            mFontSizeSelector.setVisibility(View.GONE);
            return true;
        }
        return false;
    }

    /**
     * 背景颜色变化回调（实现NoteSettingChangedListener接口）
     * 当背景颜色改变时，更新UI显示
     */
    public void onBackgroundColorChanged() {
        // 显示新颜色的选中图标
        findViewById(sBgSelectorSelectionMap.get(mWorkingNote.getBgColorId())).setVisibility(
                View.VISIBLE);
        // 更新编辑面板背景色
        mNoteEditorPanel.setBackgroundResource(mWorkingNote.getBgColorResId());
        // 更新头部面板背景色
        mHeadViewPanel.setBackgroundResource(mWorkingNote.getTitleBgResId());
    }

    /**
     * 准备选项菜单（每次显示菜单前调用）
     * 动态调整菜单项的显示和文字
     *
     * @param menu 菜单对象
     * @return true: 显示菜单
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isFinishing()) {
            return true;
        }
        clearSettingState();  // 关闭弹出面板
        menu.clear();  // 清空菜单

        // 根据便签类型加载不同的菜单布局
        if (mWorkingNote.getFolderId() == Notes.ID_CALL_RECORD_FOLDER) {
            getMenuInflater().inflate(R.menu.call_note_edit, menu);  // 通话记录便签菜单
        } else {
            getMenuInflater().inflate(R.menu.note_edit, menu);       // 普通便签菜单
        }

        // 根据便签模式切换菜单项文字
        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_normal_mode);  // "普通模式"
        } else {
            menu.findItem(R.id.menu_list_mode).setTitle(R.string.menu_list_mode);    // "待办清单"
        }

        // 根据提醒状态显示/隐藏相关菜单项
        if (mWorkingNote.hasClockAlert()) {
            menu.findItem(R.id.menu_alert).setVisible(false);           // 已有提醒，隐藏"设置提醒"
        } else {
            menu.findItem(R.id.menu_delete_remind).setVisible(false);   // 无提醒，隐藏"删除提醒"
        }
        return true;
    }

    /**
     * 处理选项菜单点击事件
     *
     * @param item 被点击的菜单项
     * @return true: 事件已处理
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();

        if (itemId == R.id.menu_new_note) {
            createNewNote();                    // 新建便签
        } else if (itemId == R.id.menu_delete) {
            // 删除便签：显示确认对话框
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.alert_title_delete));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setMessage(getString(R.string.alert_message_delete_note));
            builder.setPositiveButton(android.R.string.ok,
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            deleteCurrentNote();  // 执行删除
                            finish();             // 关闭当前页面
                        }
                    });
            builder.setNegativeButton(android.R.string.cancel, null);
            builder.show();
        } else if (itemId == R.id.menu_font_size) {
            // 显示字体大小选择器
            mFontSizeSelector.setVisibility(View.VISIBLE);
            findViewById(sFontSelectorSelectionMap.get(mFontSizeId)).setVisibility(View.VISIBLE);
        } else if (itemId == R.id.menu_list_mode) {
            // 切换待办清单模式
            mWorkingNote.setCheckListMode(mWorkingNote.getCheckListMode() == 0 ?
                    TextNote.MODE_CHECK_LIST : 0);
        } else if (itemId == R.id.menu_share) {
            // 分享便签
            getWorkingText();
            sendTo(this, mWorkingNote.getContent());
        } else if (itemId == R.id.menu_send_to_desktop) {
            // 添加桌面快捷方式
            sendToDesktop();
        } else if (itemId == R.id.menu_alert) {
            // 设置提醒
            setReminder();
        } else if (itemId == R.id.menu_delete_remind) {
            // 删除提醒
            mWorkingNote.setAlertDate(0, false);
        }
        return true;
    }

    /**
     * 设置提醒时间
     * 弹出日期时间选择对话框
     */
    private void setReminder() {
        DateTimePickerDialog d = new DateTimePickerDialog(this, System.currentTimeMillis());
        d.setOnDateTimeSetListener(new OnDateTimeSetListener() {
            public void OnDateTimeSet(AlertDialog dialog, long date) {
                mWorkingNote.setAlertDate(date, true);  // 设置提醒时间
            }
        });
        d.show();
    }

    /**
     * 分享便签到其他应用
     * 使用ACTION_SEND Intent，支持微信、QQ、邮件等
     *
     * @param context 上下文
     * @param info 要分享的文本内容
     */
    private void sendTo(Context context, String info) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, info);  // 设置分享内容
        intent.setType("text/plain");              // 设置数据类型为纯文本
        context.startActivity(intent);              // 启动分享选择器
    }

    /**
     * 创建新便签
     * 先保存当前便签，然后启动一个新的编辑页面
     */
    private void createNewNote() {
        // 首先保存当前编辑的便签
        saveNote();

        // 为安全起见，关闭当前Activity
        finish();
        // 启动新的便签编辑Activity
        Intent intent = new Intent(this, NoteEditActivity.class);
        intent.setAction(Intent.ACTION_INSERT_OR_EDIT);
        intent.putExtra(Notes.INTENT_EXTRA_FOLDER_ID, mWorkingNote.getFolderId());
        startActivity(intent);
    }

    /**
     * 删除当前便签
     * 同步模式：移动到回收站
     * 非同步模式：直接删除
     */
    private void deleteCurrentNote() {
        if (mWorkingNote.existInDatabase()) {
            HashSet<Long> ids = new HashSet<Long>();
            long id = mWorkingNote.getNoteId();
            if (id != Notes.ID_ROOT_FOLDER) {
                ids.add(id);
            } else {
                Log.d(TAG, "Wrong note id, should not happen");
            }

            if (!isSyncMode()) {
                // 非同步模式：直接删除
                if (!DataUtils.batchDeleteNotes(getContentResolver(), ids)) {
                    Log.e(TAG, "Delete Note error");
                }
            } else {
                // 同步模式：移动到回收站（软删除，可恢复）
                if (!DataUtils.batchMoveToFolder(getContentResolver(), ids, Notes.ID_TRASH_FOLER)) {
                    Log.e(TAG, "Move notes to trash folder error, should not happens");
                }
            }
        }
        mWorkingNote.markDeleted(true);  // 标记为已删除
    }

    /**
     * 判断是否为同步模式
     * 通过检查是否登录了小米账号（有同步账号）
     *
     * @return true: 同步模式, false: 本地模式
     */
    private boolean isSyncMode() {
        return NotesPreferenceActivity.getSyncAccountName(this).trim().length() > 0;
    }

    /**
     * 提醒时间变化回调（实现NoteSettingChangedListener接口）
     * 设置或取消AlarmManager定时提醒
     *
     * @param date 提醒时间（毫秒）
     * @param set true: 设置提醒, false: 取消提醒
     */
    public void onClockAlertChanged(long date, boolean set) {
        /**
         * 用户可能为未保存的便签设置提醒，所以在设置闹钟前需要先保存便签
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();  // 先保存，生成便签ID
        }

        if (mWorkingNote.getNoteId() > 0) {
            // 创建用于启动AlarmReceiver的Intent
            Intent intent = new Intent(this, AlarmReceiver.class);
            intent.setData(ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, mWorkingNote.getNoteId()));

            // 创建PendingIntent，用于AlarmManager触发
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            AlarmManager alarmManager = ((AlarmManager) getSystemService(ALARM_SERVICE));
            showAlertHeader();  // 刷新提醒头部显示

            if(!set) {
                // 取消提醒
                alarmManager.cancel(pendingIntent);
            } else {
                // 设置提醒（使用RTC_WAKEUP模式，唤醒设备）
                alarmManager.set(AlarmManager.RTC_WAKEUP, date, pendingIntent);
            }
        } else {
            /**
             * 用户没有输入任何内容（便签不值得保存），没有便签ID
             * 提醒用户应该输入内容
             */
            Log.e(TAG, "Clock alert setting error");
            showToast(R.string.error_note_empty_for_clock);
        }
    }

    /**
     * 小部件变化回调（实现NoteSettingChangedListener接口）
     * 更新桌面小部件显示
     */
    public void onWidgetChanged() {
        updateWidget();
    }

    /**
     * 待办清单中文本删除时的回调（实现OnTextViewChangeListener接口）
     * 处理删除清单项的逻辑，将删除的文本合并到上一个清单项
     *
     * @param index 删除的位置索引
     * @param text 被删除的文本内容
     */
    public void onEditTextDelete(int index, String text) {
        int childCount = mEditTextList.getChildCount();
        if (childCount == 1) {
            return;  // 只剩最后一项时不能删除
        }

        // 更新后续项的位置索引（向前移动一位）
        for (int i = index + 1; i < childCount; i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i - 1);
        }

        // 删除指定位置的视图
        mEditTextList.removeViewAt(index);

        // 将删除的文本追加到上一个清单项（或第一个清单项）
        NoteEditText edit = null;
        if(index == 0) {
            edit = (NoteEditText) mEditTextList.getChildAt(0).findViewById(
                    R.id.et_edit_text);
        } else {
            edit = (NoteEditText) mEditTextList.getChildAt(index - 1).findViewById(
                    R.id.et_edit_text);
        }

        int length = edit.length();
        edit.append(text);           // 追加文本
        edit.requestFocus();         // 获取焦点
        edit.setSelection(length);   // 将光标移动到末尾
    }

    /**
     * 待办清单中按下回车键时的回调（实现OnTextViewChangeListener接口）
     * 创建新的清单项
     *
     * @param index 当前位置索引
     * @param text 当前文本内容
     */
    public void onEditTextEnter(int index, String text) {
        /**
         * 不应该发生，仅为调试检查
         */
        if(index > mEditTextList.getChildCount()) {
            Log.e(TAG, "Index out of mEditTextList boundrary, should not happen");
        }

        // 创建新的清单项视图并添加到指定位置
        View view = getListItem(text, index);
        mEditTextList.addView(view, index);
        NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.requestFocus();        // 新项获取焦点
        edit.setSelection(0);       // 光标移动到开头

        // 更新后续项的位置索引（向后移动一位）
        for (int i = index + 1; i < mEditTextList.getChildCount(); i++) {
            ((NoteEditText) mEditTextList.getChildAt(i).findViewById(R.id.et_edit_text))
                    .setIndex(i);
        }
    }

    /**
     * 切换到待办清单模式
     * 将普通文本按换行符分割，转换为带复选框的清单列表
     *
     * @param text 原始文本内容
     */
    private void switchToListMode(String text) {
        mEditTextList.removeAllViews();                     // 清空现有清单
        String[] items = text.split("\n");                  // 按换行符分割
        int index = 0;

        // 为每一行创建清单项
        for (String item : items) {
            if(!TextUtils.isEmpty(item)) {
                mEditTextList.addView(getListItem(item, index));
                index++;
            }
        }

        // 添加一个空行用于输入新内容
        mEditTextList.addView(getListItem("", index));
        mEditTextList.getChildAt(index).findViewById(R.id.et_edit_text).requestFocus();

        // 切换视图可见性：隐藏普通编辑器，显示清单列表
        mNoteEditor.setVisibility(View.GONE);
        mEditTextList.setVisibility(View.VISIBLE);
    }

    /**
     * 获取搜索关键词高亮显示结果
     * 将文本中匹配搜索关键词的部分标记为高亮背景色
     *
     * @param fullText 完整文本内容
     * @param userQuery 用户搜索的关键词
     * @return 带高亮效果的Spannable对象
     */
    private Spannable getHighlightQueryResult(String fullText, String userQuery) {
        SpannableString spannable = new SpannableString(fullText == null ? "" : fullText);
        if (!TextUtils.isEmpty(userQuery)) {
            mPattern = Pattern.compile(userQuery);           // 编译正则表达式
            Matcher m = mPattern.matcher(fullText);          // 创建匹配器
            int start = 0;
            while (m.find(start)) {                          // 循环查找所有匹配项
                spannable.setSpan(
                        new BackgroundColorSpan(this.getResources().getColor(
                                R.color.user_query_highlight)),  // 高亮背景色
                        m.start(), m.end(),
                        Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                start = m.end();
            }
        }
        return spannable;
    }

    /**
     * 创建待办清单项视图
     * 每个清单项包含：复选框、文本编辑框
     *
     * @param item 文本内容（可能包含√或□前缀）
     * @param index 位置索引
     * @return 清单项视图
     */
    private View getListItem(String item, int index) {
        // 加载清单项布局
        View view = LayoutInflater.from(this).inflate(R.layout.note_edit_list_item, null);
        final NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
        edit.setTextAppearance(this, TextAppearanceResources.getTexAppearanceResource(mFontSizeId));

        CheckBox cb = ((CheckBox) view.findViewById(R.id.cb_edit_item));
        // 设置复选框状态变化监听：勾选时添加删除线，取消勾选时移除删除线
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                } else {
                    edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);
                }
            }
        });

        // 解析标记符号：√（已勾选）或 □（未勾选）
        if (item.startsWith(TAG_CHECKED)) {
            cb.setChecked(true);   // 复选框勾选
            edit.setPaintFlags(edit.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);  // 添加删除线
            item = item.substring(TAG_CHECKED.length(), item.length()).trim();  // 移除标记符号
        } else if (item.startsWith(TAG_UNCHECKED)) {
            cb.setChecked(false);  // 复选框未勾选
            edit.setPaintFlags(Paint.ANTI_ALIAS_FLAG | Paint.DEV_KERN_TEXT_FLAG);  // 正常样式
            item = item.substring(TAG_UNCHECKED.length(), item.length()).trim();  // 移除标记符号
        }

        // 设置文本变化监听器和位置索引
        edit.setOnTextViewChangeListener(this);
        edit.setIndex(index);
        edit.setText(getHighlightQueryResult(item, mUserQuery));  // 设置文本并高亮搜索词
        return view;
    }

    /**
     * 文本变化回调（实现OnTextViewChangeListener接口）
     * 根据是否有文本内容显示或隐藏复选框
     *
     * @param index 位置索引
     * @param hasText 是否有文本内容
     */
    public void onTextChange(int index, boolean hasText) {
        if (index >= mEditTextList.getChildCount()) {
            Log.e(TAG, "Wrong index, should not happen");
            return;
        }
        if(hasText) {
            // 有文本：显示复选框
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.VISIBLE);
        } else {
            // 无文本：隐藏复选框
            mEditTextList.getChildAt(index).findViewById(R.id.cb_edit_item).setVisibility(View.GONE);
        }
    }

    /**
     * 待办清单模式切换回调（实现NoteSettingChangedListener接口）
     * 在普通模式和待办清单模式之间切换
     *
     * @param oldMode 旧模式
     * @param newMode 新模式
     */
    public void onCheckListModeChanged(int oldMode, int newMode) {
        if (newMode == TextNote.MODE_CHECK_LIST) {
            // 切换到待办清单模式
            switchToListMode(mNoteEditor.getText().toString());
        } else {
            // 切换到普通模式
            if (!getWorkingText()) {
                // 如果没有勾选项，移除未勾选标记
                mWorkingNote.setWorkingText(mWorkingNote.getContent().replace(TAG_UNCHECKED + " ",
                        ""));
            }
            // 设置普通编辑器文本
            mNoteEditor.setText(getHighlightQueryResult(mWorkingNote.getContent(), mUserQuery));
            // 切换视图可见性：隐藏清单列表，显示普通编辑器
            mEditTextList.setVisibility(View.GONE);
            mNoteEditor.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 获取正在编辑的文本内容
     * 根据当前模式（普通/待办清单）分别处理
     *
     * @return true: 有待办项被勾选（仅待办清单模式有意义）
     */
    private boolean getWorkingText() {
        boolean hasChecked = false;

        if (mWorkingNote.getCheckListMode() == TextNote.MODE_CHECK_LIST) {
            // 待办清单模式：遍历所有清单项，拼接带标记的文本
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mEditTextList.getChildCount(); i++) {
                View view = mEditTextList.getChildAt(i);
                NoteEditText edit = (NoteEditText) view.findViewById(R.id.et_edit_text);
                if (!TextUtils.isEmpty(edit.getText())) {
                    if (((CheckBox) view.findViewById(R.id.cb_edit_item)).isChecked()) {
                        sb.append(TAG_CHECKED).append(" ").append(edit.getText()).append("\n");
                        hasChecked = true;
                    } else {
                        sb.append(TAG_UNCHECKED).append(" ").append(edit.getText()).append("\n");
                    }
                }
            }
            mWorkingNote.setWorkingText(sb.toString());
        } else {
            // 普通模式：直接获取编辑器文本
            mWorkingNote.setWorkingText(mNoteEditor.getText().toString());
        }
        return hasChecked;
    }

    /**
     * 保存便签
     * 获取编辑内容并调用WorkingNote的保存方法
     *
     * @return true: 保存成功
     */
    private boolean saveNote() {
        getWorkingText();  // 获取编辑内容
        boolean saved = mWorkingNote.saveNote();  // 执行保存
        if (saved) {
            /**
             * 从列表页面进入编辑页面有两种情况：打开已有便签、创建/编辑便签
             * 打开已有便签时，返回需要回到原位置；
             * 创建新便签时，返回需要回到列表顶部。
             * RESULT_OK用于区分创建/编辑状态
             */
            setResult(RESULT_OK);
        }
        return saved;
    }

    /**
     * 添加桌面快捷方式
     * 将当前便签添加到桌面，方便快速打开
     */
    private void sendToDesktop() {
        /**
         * 发送到桌面之前，确保当前编辑的便签已存在于数据库中
         * 对于新便签，需要先保存
         */
        if (!mWorkingNote.existInDatabase()) {
            saveNote();  // 先保存
        }

        if (mWorkingNote.getNoteId() > 0) {
            Intent sender = new Intent();
            // 创建快捷方式点击时启动的Intent
            Intent shortcutIntent = new Intent(this, NoteEditActivity.class);
            shortcutIntent.setAction(Intent.ACTION_VIEW);
            shortcutIntent.putExtra(Intent.EXTRA_UID, mWorkingNote.getNoteId());

            // 设置快捷方式的各种属性
            sender.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);      // 点击后启动的Intent
            sender.putExtra(Intent.EXTRA_SHORTCUT_NAME,
                    makeShortcutIconTitle(mWorkingNote.getContent()));           // 快捷方式名称
            sender.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                    Intent.ShortcutIconResource.fromContext(this, R.drawable.icon_app));  // 图标
            sender.putExtra("duplicate", true);  // 允许重复创建
            sender.setAction("com.android.launcher.action.INSTALL_SHORTCUT");    // 安装快捷方式的Action

            showToast(R.string.info_note_enter_desktop);
            sendBroadcast(sender);  // 发送广播创建快捷方式
        } else {
            /**
             * 用户没有输入任何内容（便签不值得保存），没有便签ID
             * 提醒用户应该输入内容
             */
            Log.e(TAG, "Send to desktop error");
            showToast(R.string.error_note_empty_for_send_to_desktop);
        }
    }

    /**
     * 生成桌面快捷方式标题
     * 移除标记符号（√和□），并截断过长的文本
     *
     * @param content 原始内容
     * @return 处理后的标题
     */
    private String makeShortcutIconTitle(String content) {
        content = content.replace(TAG_CHECKED, "");      // 移除已勾选标记
        content = content.replace(TAG_UNCHECKED, "");    // 移除未勾选标记
        // 超过最大长度则截断
        return content.length() > SHORTCUT_ICON_TITLE_MAX_LEN
                ? content.substring(0, SHORTCUT_ICON_TITLE_MAX_LEN)
                : content;
    }

    /**
     * 显示Toast提示（短时间）
     *
     * @param resId 字符串资源ID
     */
    private void showToast(int resId) {
        showToast(resId, Toast.LENGTH_SHORT);
    }

    /**
     * 显示Toast提示
     *
     * @param resId 字符串资源ID
     * @param duration 显示时长（Toast.LENGTH_SHORT 或 Toast.LENGTH_LONG）
     */
    private void showToast(int resId, int duration) {
        Toast.makeText(this, resId, duration).show();
    }
}