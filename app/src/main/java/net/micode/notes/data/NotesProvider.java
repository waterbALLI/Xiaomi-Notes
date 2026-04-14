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

package net.micode.notes.data;


import android.app.SearchManager;
import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.NoteColumns;
import net.micode.notes.data.NotesDatabaseHelper.TABLE;

/*
================================================================================
文件注释说明
================================================================================

文件路径: app/src/main/java/net/micode/notes/data/NotesProvider.java
文件类型: Java (ContentProvider/内容提供者)
创建时间: 2010-2011 (MiCode)
最后修改: 2026-04-13

功能描述:
小米便签应用的自定义内容提供者（ContentProvider）。
负责管理和封装所有的数据库CRUD（创建、读取、更新、删除）操作，对外提供统一的数据访问接口。
不仅处理便签（Note）、数据（Data）的常规访问，同时也提供了基于 SearchManager 的全局搜索和建议的数据支持。

主要元素说明:
1. URI匹配器 (UriMatcher): 定义了便签、便签项、内部数据、数据项、搜索等各种访问路径的路由规则。
2. query(): 执行数据库查询操作。除普通查询外，支持对全局搜索动作过滤并格式化内容。
3. insert(): 执行数据库插入操作（如创建便签、添加内容段），发生变化后自动通过 notifyChange 刷新相关UI。
4. delete(): 执行数据库删除操作，并且具备防止误删内置的系统文件夹（ID <= 0）的拦截机制。
5. update(): 执行数据库更新操作，涉及便签本体属性变化时，会触发其版本号(Version)加1，以便于状态跟踪和数据同步。

使用场景:
- 应用内的 Activity 或 Service 需要保存新建便签、读取历史记录、更改部分内容时调用。
- 其他组件或桌面小部件想获取便签内容时，通过 ContentResolver 发起请求。
- 系统全局搜索栏检索便签时进行匹配调用。

注意事项:
1. 在返回搜索结果展示使用的字段时（NOTES_SEARCH_PROJECTION），自动将文本内的换行符替换剔除，确保显示更多的预览信息。
2. 如果修改内容，必须检查触发相应的 ContentObserver，以确保界面及时重绘。

相关文件:
- Notes.java (定义了所有使用到的契约常量 URI 和 数据库表列名)
- NotesDatabaseHelper.java (实际控制数据库建表、升级的类)

================================================================================
*/
public class NotesProvider extends ContentProvider {
    private static final UriMatcher mMatcher;

    private NotesDatabaseHelper mHelper;

    private static final String TAG = "NotesProvider";

    // 定义支持访问的URI规则标识码
    //这些等号后面的数字（1, 2, 3, 4, 5, 6）是自定义的匹配码（Match Codes）。
    // 它们本身没有特殊的数学或业务含义，只要是互不相同的整数即可。
    //为什么要这么写？
    //        1. 预先注册「字符串路径」与「整型匹配码」的映射关系 在紧接着的静态代码块中
    //    ，应用会将定义好的各种 URI 路径规则和这些数字绑定起来：
    //在真实操作时进行快速的 Switch/Case 路由分发 当外部组件想要查询、插入、更新或删除数据时，
    // 会传过来一个具体的字符串 URI（比如 content://net.micode.notes/note/100）。
    // 这时候，我们在 query、insert 等方法里，调用 mMatcher.match(uri)：
    //UriMatcher 发现传过来的路径符合 note/# 的规则。
    //于是它直接返回当时绑定的数字代码：2 (即 URI_NOTE_ITEM)。
    //然后代码中就直接利用 switch-case 对这个数字进行判断，决定执行什么数据库查询
    private static final int URI_NOTE            = 1; // 所有的便签集合
    private static final int URI_NOTE_ITEM       = 2; // 单个指定的便签（附带具体的ID）
    private static final int URI_DATA            = 3; // 所有的便签内容数据集合
    private static final int URI_DATA_ITEM       = 4; // 单个指定的便签数据（附带具体的ID）

    private static final int URI_SEARCH          = 5; // 用于系统的全局搜索便签请求
    private static final int URI_SEARCH_SUGGEST  = 6; // 用于获取下拉搜索建议

    // 静态代码块，在类加载时初始化URI匹配器并注册各路径以映射对应的URI标识码，就是上面的1 2 3 4 5 6
    //第三个参数 code 就是你传进去的数字标识码。
    //这里的静态代码块正在做的事情，就是**“登记造册”**：
    //把 "note" 这个路径当做一号文件，登记为 1 (URI_NOTE)
    //把 "note/#" 这个路径当做二号文件，登记为 2 (URI_NOTE_ITEM)
    //...以此类推
    //登记完之后，当后面的 query 或 insert 方法收到别人发来的网址请求时，一查这本“登记册”，
    // 就能立刻知道该返回哪个数字，然后走下面对应的 case 1: 或者 case 2: 去执行数据库操作了。
    static {
        mMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        mMatcher.addURI(Notes.AUTHORITY, "note", URI_NOTE);
        mMatcher.addURI(Notes.AUTHORITY, "note/#", URI_NOTE_ITEM); // #代表匹配任意数字ID
        mMatcher.addURI(Notes.AUTHORITY, "data", URI_DATA);
        mMatcher.addURI(Notes.AUTHORITY, "data/#", URI_DATA_ITEM);
        mMatcher.addURI(Notes.AUTHORITY, "search", URI_SEARCH);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, URI_SEARCH_SUGGEST);
        mMatcher.addURI(Notes.AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", URI_SEARCH_SUGGEST); // *代表匹配任意文本短语
    }

    /**
     * x'0A' 在 sqlite 中代表换行符 '\n'。
     * 为了在系统全局搜索的紧凑结果列表中尽可能多地展现有价值的信息，
     * 我们需要将匹配到的便签片段中的换行符和部分空白字符进行截断或替换过滤处理。
     * 以下 SQL Projection 构建了可以接入 SearchManager 的特定列名。
     */
    //这段代码定义了一个 SQL 的投影（Projection）规则，也就是我们在写 SELECT ... FROM table 时，SELECT 后面的那一部分。
    //它的核心作用是：“数据格式翻译转换门”。
    //在 Android 中，当你把便签应用接入系统的**全局搜索栏（SearchManager）**时，
    // 系统搜索下拉框是不认识你数据库里的 NoteColumns.SNIPPET（片段）或者 NoteColumns.ID（编号）的。
    // 系统搜索只认识它自己规定好的一套“标准列名”（比如 SUGGEST_COLUMN_TEXT_1 代表标题，SUGGEST_COLUMN_INTENT_ACTION 代表点击事件）。
    //因此，这段代码使用了 SQL 中的 AS 关键字，把我们自己数据库的列，**伪装（映射）**成了系统搜索系统认识的列。
    private static final String NOTES_SEARCH_PROJECTION = NoteColumns.ID + ","
        + NoteColumns.ID + " AS " + SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_1 + ","
        + "TRIM(REPLACE(" + NoteColumns.SNIPPET + ", x'0A','')) AS " + SearchManager.SUGGEST_COLUMN_TEXT_2 + ","
        + R.drawable.search_result + " AS " + SearchManager.SUGGEST_COLUMN_ICON_1 + ","
        + "'" + Intent.ACTION_VIEW + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_ACTION + ","
        + "'" + Notes.TextNote.CONTENT_TYPE + "' AS " + SearchManager.SUGGEST_COLUMN_INTENT_DATA;

    // 当用户请求搜索时，组装出的预设查询SQL语句。它排除了回收站（ID_TRASH_FOLER）中的文件
    private static String NOTES_SNIPPET_SEARCH_QUERY = "SELECT " + NOTES_SEARCH_PROJECTION
        + " FROM " + TABLE.NOTE
        + " WHERE " + NoteColumns.SNIPPET + " LIKE ?"
        + " AND " + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER
        + " AND " + NoteColumns.TYPE + "=" + Notes.TYPE_NOTE;

    /**
     * ContentProvider被初始化时调用，实例化并保留一个数据库帮助类的单例对象。
     */
    @Override
    public boolean onCreate() {
        mHelper = NotesDatabaseHelper.getInstance(getContext());
        return true;
    }

    /**
     * 响应查询数据的请求。
     */
    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        Cursor c = null;
        SQLiteDatabase db = mHelper.getReadableDatabase(); // 获取只读数据库句柄
        String id = null;
        // 根据调用方传入的URI进行匹配，执行不同的查询方案， URI 路由分发 (Switch-Case 逻辑)，就用到我们之前定义的哪个1 2 3 4 5 6了

        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                c = db.query(TABLE.NOTE, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1); // 提取路径中的第二段即 ID
                c = db.query(TABLE.NOTE, projection, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_DATA:
                c = db.query(TABLE.DATA, projection, selection, selectionArgs, null, null,
                        sortOrder);
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                c = db.query(TABLE.DATA, projection, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs, null, null, sortOrder);
                break;
            case URI_SEARCH:
            case URI_SEARCH_SUGGEST:
                // 收到搜索请求时，强制规定调用方不可自定义排序条件和选择投影条件
                if (sortOrder != null || projection != null) {
                    throw new IllegalArgumentException(
                            "do not specify sortOrder, selection, selectionArgs, or projection" + "with this query");
                }

                String searchString = null;
                // 解析出需要搜索的关键词参数
                if (mMatcher.match(uri) == URI_SEARCH_SUGGEST) {
                    if (uri.getPathSegments().size() > 1) {
                        searchString = uri.getPathSegments().get(1);
                    }
                } else {
                    searchString = uri.getQueryParameter("pattern");
                }

                // 如果搜索词为空则直接返回null结束
                if (TextUtils.isEmpty(searchString)) {
                    return null;
                }

                try {
                    // 使用 SQL 的 LIKE 做模糊匹配所以需要在两边添加 "%"
                    searchString = String.format("%%%s%%", searchString);
                    c = db.rawQuery(NOTES_SNIPPET_SEARCH_QUERY,
                            new String[] { searchString });
                } catch (IllegalStateException ex) {
                    Log.e(TAG, "got exception: " + ex.toString());
                }
                break;
            default://未知 URI 拦截，抛出异常
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 当查询成功返回有效的Cursor时，为其绑定ContentResolver的观察监听通知，一旦该URI下数据发生变动，这个游标会自动感知
        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return c;
    }

    /**
     * 响应插入数据的请求。
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        SQLiteDatabase db = mHelper.getWritableDatabase(); // 与查询不同，插入操作需要获取可写的数据库句柄。
        // 如果磁盘空间不足，这一步可能会报错
        long dataId = 0, noteId = 0, insertedId = 0;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                insertedId = noteId = db.insert(TABLE.NOTE, null, values);
                break;
            case URI_DATA:
                if (values.containsKey(DataColumns.NOTE_ID)) {
                    noteId = values.getAsLong(DataColumns.NOTE_ID);
                } else {
                    Log.d(TAG, "Wrong data format without note id:" + values.toString());
                }
                insertedId = dataId = db.insert(TABLE.DATA, null, values);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        // 通知通过URI监听了便签列表变动的下游对象（如更新UI列表）
        if (noteId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_NOTE_URI, noteId), null);
        }

        // 通知对应的Data变动
        if (dataId > 0) {
            getContext().getContentResolver().notifyChange(
                    ContentUris.withAppendedId(Notes.CONTENT_DATA_URI, dataId), null);
        }

        return ContentUris.withAppendedId(uri, insertedId);
    }

    /**
     * 响应删除数据的请求。
     */
    //ContentProvider 中的 delete 方法实现。
    // 的作用是根据传入的 URI 和条件，从底层 SQLite 数据库中删除数据，并返回受影响的行数。
    //① 安全防护：系统文件夹保护
    //这是这段代码最特别的地方。在处理 URI_NOTE（删除笔记列表）和 URI_NOTE_ITEM（删除特定笔记）时，它做了一个强制限制：
    //
    //NoteColumns.ID + ">0 ": 代码默认 ID 小于或等于 0 的记录是“系统级”数据（比如根目录、回收站、或默认文件夹）。
    //
    //拦截机制：如果你尝试删除 ID 为 0 或负数的笔记，代码要么在 SQL 条件里过滤掉它们，要么直接 break 跳出，从而保证了应用核心结构的稳定性。
    //
    //② 灵活的删除范围
    //批量删除 (URI_NOTE, URI_DATA)：根据传入的 selection 条件删除多行。
    //
    //精准删除 (URI_NOTE_ITEM, URI_DATA_ITEM)：通过 getPathSegments().get(1) 提取 URI 路径中的 ID，并将其锁定为删除条件。
    //
    //③ 标志位 deleteData 的作用
    //代码中使用了一个布尔值 deleteData。当删除的是 DATA 表中的内容（比如笔记中的某一项具体内容、一张图片等）时，
    // 该值设为 true。这是为了解决数据间的依赖关系

 //据联动与通知 (Observer Pattern)
    //在删除执行成功（count > 0）后，代码进行了两次通知：
    //
    //数据层级联动通知：
    //
    //Java
    //if (deleteData) {
    //    getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
    //}
    //逻辑： 如果删除了 Data 表的内容，即便 Note 表本身没变，但因为 Note 的显示通常依赖于 Data，所以必须通知监听 Note 表的 UI 界面也去刷新一下。
    //
    //自身通知：
    //
    //Java
    //getContext().getContentResolver().notifyChange(uri, null);
    //逻辑： 通知所有监听当前删除地址的观察者。
    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean deleteData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 安全判定：保护系统文件夹不被删除 (ID必须大于0)
                selection = "(" + selection + ") AND " + NoteColumns.ID + ">0 ";
                count = db.delete(TABLE.NOTE, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                /**
                 * 小于等于0的ID代表这是系统自带的文件夹（例如根目录或回收站等）。
                 * 此处拦截，禁止随意删除将其放入回收站。
                 */
                long noteId = Long.valueOf(id);
                if (noteId <= 0) {
                    break;
                }
                count = db.delete(TABLE.NOTE,
                        NoteColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.delete(TABLE.DATA, selection, selectionArgs);
                deleteData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.delete(TABLE.DATA,
                        DataColumns.ID + "=" + id + parseSelection(selection), selectionArgs);
                deleteData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        if (count > 0) {
            // 如果删除操作确实影响了Data表，由于Data的改变会直接影响相应Note的呈现，因此需要通知Note更新
            if (deleteData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    /**
     * 响应更新数据的请求。
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count = 0;
        String id = null;
        SQLiteDatabase db = mHelper.getWritableDatabase();
        boolean updateData = false;
        switch (mMatcher.match(uri)) {
            case URI_NOTE:
                // 更新任意多条笔记前，统一把将被影响到的笔记版本号自增(+1)
                increaseNoteVersion(-1, selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, selection, selectionArgs);
                break;
            case URI_NOTE_ITEM:
                id = uri.getPathSegments().get(1);
                // 仅自增当前这一条笔记的版本号
                increaseNoteVersion(Long.valueOf(id), selection, selectionArgs);
                count = db.update(TABLE.NOTE, values, NoteColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                break;
            case URI_DATA:
                count = db.update(TABLE.DATA, values, selection, selectionArgs);
                updateData = true;
                break;
            case URI_DATA_ITEM:
                id = uri.getPathSegments().get(1);
                count = db.update(TABLE.DATA, values, DataColumns.ID + "=" + id
                        + parseSelection(selection), selectionArgs);
                updateData = true;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (count > 0) {
            if (updateData) {
                getContext().getContentResolver().notifyChange(Notes.CONTENT_NOTE_URI, null);
            }
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return count;
    }

    // 格式化SQL查询末尾的条件拼接方式 (前置加上 " AND " 防止语法错误)
    private String parseSelection(String selection) {
        return (!TextUtils.isEmpty(selection) ? " AND (" + selection + ')' : "");
    }

    /**
     * 该私有方法用于在便签内容发生更新时，强制使其 Version 字段递增 1。
     * 可以基于此进行本地修改状态记录以及将来可能的服务器同步冲突对比。
     */
    private void increaseNoteVersion(long id, String selection, String[] selectionArgs) {
        StringBuilder sql = new StringBuilder(120);
        sql.append("UPDATE ");
        sql.append(TABLE.NOTE);
        sql.append(" SET ");
        sql.append(NoteColumns.VERSION);
        sql.append("=" + NoteColumns.VERSION + "+1 ");

        if (id > 0 || !TextUtils.isEmpty(selection)) {
            sql.append(" WHERE ");
        }
        if (id > 0) {
            sql.append(NoteColumns.ID + "=" + String.valueOf(id));
        }
        if (!TextUtils.isEmpty(selection)) {
            String selectString = id > 0 ? parseSelection(selection) : selection;
            for (String args : selectionArgs) {
                selectString = selectString.replaceFirst("\\?", args);
            }
            sql.append(selectString);
        }

        mHelper.getWritableDatabase().execSQL(sql.toString());
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null; // 目前该方法未实际产生作用，正常在此应返回MIME类型。
    }

}
