/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

// 导入必要的Android类和工具类
import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义的EditText组件，用于便签编辑功能
 * 支持文本编辑、链接点击、以及动态添加/删除编辑框等功能
 */
public class NoteEditText extends EditText {
    // 日志标签
    private static final String TAG = "NoteEditText";

    // 当前编辑框的索引位置
    private int mIndex;

    // 删除操作前光标的位置，用于判断是否删除整个编辑框
    private int mSelectionStartBeforeDelete;

    // URI协议常量定义
    private static final String SCHEME_TEL = "tel:";      // 电话协议
    private static final String SCHEME_HTTP = "http:";    // HTTP协议
    private static final String SCHEME_EMAIL = "mailto:"; // 邮件协议

    // 协议类型与菜单资源ID的映射表
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<String, Integer>();

    // 静态初始化块，填充协议映射表
    static {
        sSchemaActionResMap.put(SCHEME_TEL, R.string.note_link_tel);     // 电话链接菜单文字
        sSchemaActionResMap.put(SCHEME_HTTP, R.string.note_link_web);    // 网页链接菜单文字
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email); // 邮件链接菜单文字
    }

    /**
     * 文本编辑框变化监听器接口
     * 用于与外部组件（如NoteEditActivity）通信，处理编辑框的增删操作
     */
    public interface OnTextViewChangeListener {
        /**
         * 删除当前编辑框
         * 当按下删除键且文本为空时触发
         * @param index 当前编辑框的索引
         * @param text 当前编辑框的文本内容
         */
        void onEditTextDelete(int index, String text);

        /**
         * 在当前编辑框后添加新的编辑框
         * 当按下回车键时触发
         * @param index 新编辑框的位置索引
         * @param text 当前光标后的文本（将移到新编辑框中）
         */
        void onEditTextEnter(int index, String text);

        /**
         * 文本内容变化时的回调
         * 用于显示或隐藏相关的操作选项
         * @param index 当前编辑框的索引
         * @param hasText 是否有文本内容
         */
        void onTextChange(int index, boolean hasText);
    }

    // 文本变化监听器实例
    private OnTextViewChangeListener mOnTextViewChangeListener;

    /**
     * 构造函数（单参数版本）
     * @param context 上下文环境
     */
    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;  // 默认索引为0
    }

    /**
     * 设置编辑框的索引位置
     * @param index 要设置的索引值
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 设置文本变化监听器
     * @param listener 监听器实例
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    /**
     * 构造函数（双参数版本）
     * @param context 上下文环境
     * @param attrs XML属性集
     */
    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    /**
     * 构造函数（三参数版本）
     * @param context 上下文环境
     * @param attrs XML属性集
     * @param defStyle 默认样式
     */
    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 处理触摸事件
     * 实现点击文本时自动将光标定位到点击位置的功能
     * @param event 触摸事件对象
     * @return 是否处理了该事件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:  // 手指按下事件
                // 获取触摸点的坐标
                int x = (int) event.getX();
                int y = (int) event.getY();

                // 减去内边距，得到实际文本区域的坐标
                x -= getTotalPaddingLeft();
                y -= getTotalPaddingTop();

                // 加上滚动距离，得到相对于文本的绝对坐标
                x += getScrollX();
                y += getScrollY();

                // 获取文本布局对象
                Layout layout = getLayout();
                // 根据Y坐标确定行号
                int line = layout.getLineForVertical(y);
                // 根据X坐标确定该行的字符偏移量
                int off = layout.getOffsetForHorizontal(line, x);
                // 设置光标位置
                Selection.setSelection(getText(), off);
                break;
        }

        return super.onTouchEvent(event);
    }

    /**
     * 处理按键按下事件
     * @param keyCode 按键代码
     * @param event 按键事件对象
     * @return 是否处理了该事件
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_ENTER:  // 回车键
                if (mOnTextViewChangeListener != null) {
                    return false;  // 返回false，让onKeyUp处理回车逻辑
                }
                break;
            case KeyEvent.KEYCODE_DEL:  // 删除键
                // 记录删除前光标的位置，用于后续判断是否删除整个编辑框
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 处理按键弹起事件
     * 实现删除空编辑框和回车换行添加新编辑框的逻辑
     * @param keyCode 按键代码
     * @param event 按键事件对象
     * @return 是否处理了该事件
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch(keyCode) {
            case KeyEvent.KEYCODE_DEL:  // 删除键弹起
                if (mOnTextViewChangeListener != null) {
                    // 如果光标在开头（位置0）且不是第一个编辑框（mIndex != 0）
                    if (0 == mSelectionStartBeforeDelete && mIndex != 0) {
                        // 通知外部删除当前编辑框
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            case KeyEvent.KEYCODE_ENTER:  // 回车键弹起
                if (mOnTextViewChangeListener != null) {
                    // 获取光标位置
                    int selectionStart = getSelectionStart();
                    // 获取光标后的文本（将被移动到新编辑框）
                    String text = getText().subSequence(selectionStart, length()).toString();
                    // 截断当前编辑框，只保留光标前的文本
                    setText(getText().subSequence(0, selectionStart));
                    // 通知外部在当前位置后添加新编辑框，并将光标后的文本放入新编辑框
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                } else {
                    Log.d(TAG, "OnTextViewChangeListener was not seted");
                }
                break;
            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变化时的回调
     * 当编辑框获得或失去焦点时，通知外部更新UI状态
     * @param focused 是否获得焦点
     * @param direction 焦点移动方向
     * @param previouslyFocusedRect 之前获得焦点的矩形区域
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            if (!focused && TextUtils.isEmpty(getText())) {
                // 失去焦点且文本为空时，通知外部隐藏选项
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                // 获得焦点或有文本时，通知外部显示选项
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 创建上下文菜单（长按菜单）
     * 当长按文本时，如果是链接类型，则显示相应的操作菜单
     * @param menu 上下文菜单对象
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        // 检查文本是否为Spanned类型（包含样式信息）
        if (getText() instanceof Spanned) {
            // 获取选中的文本范围
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();

            int min = Math.min(selStart, selEnd);  // 选择起始位置
            int max = Math.max(selStart, selEnd);  // 选择结束位置

            // 获取选中范围内的URLSpan（链接样式）
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);

            // 如果恰好选中了一个链接
            if (urls.length == 1) {
                int defaultResId = 0;  // 默认菜单项资源ID

                // 遍历协议映射表，查找匹配的链接类型
                for(String schema: sSchemaActionResMap.keySet()) {
                    if(urls[0].getURL().indexOf(schema) >= 0) {
                        defaultResId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                // 如果没有匹配的协议类型，使用默认的"其他链接"文字
                if (defaultResId == 0) {
                    defaultResId = R.string.note_link_other;
                }

                // 添加菜单项并设置点击监听器
                menu.add(0, 0, 0, defaultResId).setOnMenuItemClickListener(
                        new OnMenuItemClickListener() {
                            public boolean onMenuItemClick(MenuItem item) {
                                // 点击菜单项时，触发链接的点击事件（打开浏览器、拨号等）
                                urls[0].onClick(NoteEditText.this);
                                return true;
                            }
                        });
            }
        }
        super.onCreateContextMenu(menu);
    }
}