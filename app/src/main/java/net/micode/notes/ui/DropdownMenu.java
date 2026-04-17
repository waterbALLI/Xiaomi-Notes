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

import android.content.Context;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.micode.notes.R;

/**
 * 下拉菜单组件
 *
 * 封装了 Android 原生 PopupMenu，将按钮与弹出菜单绑定，简化下拉菜单的使用。
 * 主要功能：
 * 1. 将普通 Button 转换为带下拉图标的触发器
 * 2. 管理 PopupMenu 的创建和显示
 * 3. 提供菜单项查找和监听器设置的方法
 *
 * 使用场景：笔记列表顶部的排序菜单、筛选菜单等
 */
public class DropdownMenu {
    /** 触发下拉菜单的按钮 */
    private Button mButton;
    /** 弹出的菜单实例 */
    private PopupMenu mPopupMenu;
    /** 菜单对象，用于操作具体的菜单项 */
    private Menu mMenu;

    /**
     * 构造函数
     *
     * 初始化下拉菜单，将传入的按钮改造为下拉菜单触发器。
     * 主要工作：
     * 1. 为按钮设置下拉箭头图标背景
     * 2. 创建 PopupMenu 并加载指定的菜单布局
     * 3. 设置按钮点击监听，点击时弹出菜单
     *
     * @param context 上下文对象
     * @param button  用作触发器的按钮
     * @param menuId  菜单资源ID（如 R.menu.xxx），定义弹出菜单的选项
     */
    public DropdownMenu(Context context, Button button, int menuId) {
        // 设置按钮背景为下拉箭头图标（带向下箭头的样式）
        mButton = button;
        // 创建 PopupMenu，锚点为该按钮（菜单将显示在按钮下方）
        mButton.setBackgroundResource(R.drawable.dropdown_icon);
        mPopupMenu = new PopupMenu(context, mButton);
        mMenu = mPopupMenu.getMenu();
        // 从 XML 菜单资源文件中加载菜单项
        mPopupMenu.getMenuInflater().inflate(menuId, mMenu);
        mButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mPopupMenu.show();
            }
        });
    }

    /**
     * 设置菜单项点击监听器
     *
     * 供外部设置当用户点击菜单中某个选项时的回调处理。
     *
     * @param listener 菜单项点击监听器，实现 OnMenuItemClickListener 接口
     */
    public void setOnDropdownMenuItemClickListener(OnMenuItemClickListener listener) {
        if (mPopupMenu != null) {
            mPopupMenu.setOnMenuItemClickListener(listener);
        }
    }

    /**
     * 根据ID查找菜单项
     *
     * 用于在外部获取特定菜单项，以便动态修改其属性（如可见性、标题等）。
     *
     * @param id 菜单项的资源ID
     * @return 对应的 MenuItem 对象，若不存在则返回 null
     */
    public MenuItem findItem(int id) {
        return mMenu.findItem(id);
    }

    /**
     * 设置按钮显示的文本
     *
     * 通常用于在用户选择某个菜单项后，将按钮文字更新为选中的选项。
     *
     * @param title 要显示的文本
     */
    public void setTitle(CharSequence title) {
        mButton.setText(title);
    }
}
