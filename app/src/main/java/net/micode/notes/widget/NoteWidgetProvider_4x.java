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

文件路径: app/src/main/java/net/micode/notes/widget/NoteWidgetProvider_4x.java
文件类型: Java (AppWidget提供者 - 4x4尺寸实现类)
创建时间: 2010-2011 (MiCode)
最后修改: 2026-04-14

功能描述:
本文件是 `NoteWidgetProvider` 基类的具体实现类之一，专门负责在手机桌面上显示 **4x4 尺寸（大尺寸）**的便签小部件。
与 2x2 版本类似，它通过实现父类的三个核心抽象方法，将通用的小部件更新逻辑与 4x4 特定的 UI 布局框架、背景贴图资源关联在一起。

架构设计与模式:
1. **模板方法模式 (Template Method Pattern)**: 与 2x2 版本同属一个体系。这里只需为父类提供 4x4 专属的皮肤和类型，由父类的引擎去驱动拉取数据并更新桌面。

================================================================================
*/
public class NoteWidgetProvider_4x extends NoteWidgetProvider {
    
    /**
     * 接收系统发来的更新小部件广播（例如将这个大号部件拖到桌面时）。
     * 
     * @param context          上下文
     * @param appWidgetManager 系统 AppWidget 管理器
     * @param appWidgetIds     需要被更新的 4x4 小部件的系统分配 ID 数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 交由基类的 update() 执行跨进程(RemoteViews)核心更新流程
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 向父类提供 4x4 小部件的特有桌面布局文件 (xml)。
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_4x;
    }

    /**
     * 根据所属便签选定的背景色，向父类提供适合 4x4 尺寸（更大的铺展区域与曲率）的专用背景图片 ID。
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget4xBgResource(bgId);
    }

    /**
     * 定义该小部件在底层系统记录时的身份类型常量为 4X。
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_4X;
    }
}
