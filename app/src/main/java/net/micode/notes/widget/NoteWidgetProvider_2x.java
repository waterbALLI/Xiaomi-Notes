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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

/*
================================================================================
文件注释说明
================================================================================

文件路径: app/src/main/java/net/micode/notes/widget/NoteWidgetProvider_2x.java
文件类型: Java (AppWidget提供者 - 2x2尺寸实现类)
创建时间: 2010-2011 (MiCode)
最后修改: 2026-04-14

功能描述:
本文件是 `NoteWidgetProvider` 基类的具体实现类之一，专门负责在手机桌面上显示 **2x2 尺寸**的便签小部件。
它通过实现父类的抽象方法，将通用的小部件更新逻辑与 2x2 尺寸特定的 UI 布局文件和背景图片资源绑定在一起。

架构设计与模式:
1. **模板方法模式 (Template Method Pattern)**: 父类 `NoteWidgetProvider` 铺设好了更新、删除、展示的所有核心流程和骨架（如查询数据库、拼装 `RemoteViews` 等），而本类作为子类，只需要填补最关键的几个 UI 资源差异点，避免了大量代码的重复。

================================================================================
*/
public class NoteWidgetProvider_2x extends NoteWidgetProvider {
    
    /**
     * 接收系统更新小部件的广播（例如：部件刚被用户拖到桌面时，或者达到了系统配置的定期刷新时间时）。
     * 
     * @param context          上下文
     * @param appWidgetManager 系统 AppWidget 管理器
     * @param appWidgetIds     需要被更新的 2x2 小部件的系统分配 ID 数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 直接复用并调用父类的 update() 核心流程
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 提供属于 2x2 小部件独有的 XML 桌面布局文件。
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 根据便签设置的背景颜色 ID，向父类提供适合 2x2 尺寸裁剪的特别背景图片资源。
     * （不同尺寸的部件即便在同一种颜色下，四角弧度或九宫格拉伸设定也往往不同）
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 向父类提供本小部件在数据库中定义的身份常量（类型标识符为 2X）。
     * 用于在 `Note` 表的 WIDGET_TYPE 列做记录。
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}
