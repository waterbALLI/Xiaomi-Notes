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

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;
//它的工作可以拆解为以下三个核心任务：
//文件功能管理/文件数据类型/文件生命周期管理。/以及文件的自动化维护（触发器 Triggers）。
//1. 仓库蓝图设计（建表）
/*
 * 文件注释说明
 * 文件路径: app/src/main/java/net/micode/notes/data/NotesDatabaseHelper.java
 * 文件类型: SQLiteOpenHelper
 * 功能描述:
 * 负责便签应用数据库的创建、表结构定义、触发器维护与版本升级。
 * 这里相当于“数据库总控中心”：决定 note/data 两张核心表如何建立，
 * 以及当笔记移动、删除、编辑时，数据库内部如何自动联动更新。
 *
 * 主要职责:
 * 1. 首次安装时创建数据库和基础系统文件夹
 * 2. 为 note/data 两张表建立触发器，自动维护计数和摘要
 * 3. 在应用升级时按版本迁移旧数据，尽量保留历史内容
 * 4. 为后续同步、回收站、widget 等功能提供数据库基础
 *
 * 注意事项:
 * - 这里只负责数据库结构和迁移，不负责 UI 逻辑
 * - 不要随意修改常量名、SQL 字面量或历史拼写，否则会影响旧数据兼容性
 */
//它规定了笔记在手机硬盘里到底长什么样。它设计了两张“货架”：
    // 数据库文件名：实际存储在系统数据库目录中，由 SQLiteOpenHelper 统一管理。
//
//清单货架（note表）：只放笔记的“名片”（标题、颜色、什么时候写的、在哪个文件夹里）。
    // 数据库版本号：只要这里变大，系统就会触发 onUpgrade()。
    // 版本升级时，代码会根据 oldVersion/newVersion 决定执行哪些迁移步骤。
//
//内容货架（data表）：放笔记的“具体货物”（具体写的文字、电话号码、录音时间戳等）。
    // 统一定义表名，避免在 SQL 和增删改查代码中反复写硬编码字符串。
    // 这个接口只是常量容器，表示本项目使用的核心两张表。
//
//2. 自动化管理机器人（触发器 Triggers）
//这是这个文件最牛的地方。它在数据库里安插了很多“全自动监控器”。只要你动了数据，数据库会自动做出反应，不需要你在主代码里操心：
//
//自动算账：当你往文件夹里丢一张便签，仓库会自动在文件夹的“统计数量”上加 1。
//
//同步快照：当你改了笔记内容，它会自动抓取前几十个字存到“名片”里，方便列表显示。
//
//连带清理：你把文件夹撕了，它会自动把里面所有的笔记和具体内容全部销毁，防止仓库堆满垃圾。
//
//3. 系统初始化与扩建（生命周期管理）
//初始装修：软件第一次安装时，它会自动划出“废纸篓”、“通话记录”等特殊区域。
//
//仓库扩建（版本升级）：当软件版本从 1.0 升到 4.0，需要增加新功能（比如支持同步到 Google Task）时，这个文件负责在不弄丢你原有笔记的前提下，给数据库“动手术”增加新字段。

public class NotesDatabaseHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "note.db";

    private static final int DB_VERSION = 4;

    public interface TABLE {
        public static final String NOTE = "note";
//note 才是真正存储在 SQLite 数据库文件里的物理表名。
        public static final String DATA = "data";
    }

    private static final String TAG = "NotesDatabaseHelper";

    private static NotesDatabaseHelper mInstance;

    // note 主表建表 SQL：保存笔记或文件夹的元信息。
    // 这一张表更像“目录索引表”，内容包括父子层级、颜色、摘要、同步状态、widget 关联等。
    // 字段设计上既支持普通文本笔记，也支持文件夹和系统文件夹，因此有多种状态字段。
    private static final String CREATE_NOTE_TABLE_SQL =
        "CREATE TABLE " + TABLE.NOTE + "(" +
            NoteColumns.ID + " INTEGER PRIMARY KEY," +
            NoteColumns.PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ALERTED_DATE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.BG_COLOR_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.HAS_ATTACHMENT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.NOTES_COUNT + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.SNIPPET + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.TYPE + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.WIDGET_TYPE + " INTEGER NOT NULL DEFAULT -1," +
            NoteColumns.SYNC_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.LOCAL_MODIFIED + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.ORIGIN_PARENT_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.GTASK_ID + " TEXT NOT NULL DEFAULT ''," +
            NoteColumns.VERSION + " INTEGER NOT NULL DEFAULT 0" +
        ")";

    // data 子表建表 SQL：保存便签的具体内容和扩展数据。
    // note 表负责“这是什么笔记”，data 表负责“这篇笔记的正文/附加信息是什么”。
    // MIME_TYPE 用于区分不同数据类型，比如 text_note 和 call_note。
    private static final String CREATE_DATA_TABLE_SQL =
        "CREATE TABLE " + TABLE.DATA + "(" +
            DataColumns.ID + " INTEGER PRIMARY KEY," +
            DataColumns.MIME_TYPE + " TEXT NOT NULL," +
            DataColumns.NOTE_ID + " INTEGER NOT NULL DEFAULT 0," +
            NoteColumns.CREATED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            NoteColumns.MODIFIED_DATE + " INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)," +
            DataColumns.CONTENT + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA1 + " INTEGER," +
            DataColumns.DATA2 + " INTEGER," +
            DataColumns.DATA3 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA4 + " TEXT NOT NULL DEFAULT ''," +
            DataColumns.DATA5 + " TEXT NOT NULL DEFAULT ''" +
        ")";

    // data 表索引：围绕 NOTE_ID 建立索引，加速按笔记 ID 查询附属数据的操作。
    // 在编辑、展示或者同步过程中，系统经常需要先找出某篇便签对应的所有 data 记录，
    // 索引能够明显减少全表扫描开销。
    private static final String CREATE_DATA_NOTE_ID_INDEX_SQL =
        "CREATE INDEX IF NOT EXISTS note_id_index ON " +
        TABLE.DATA + "(" + DataColumns.NOTE_ID + ");";

    /**
     * Increase folder's note count when move note to the folder
     * 中文说明:管文件的移动
     * 当某条 note 记录的 parent_id 被更新为某个文件夹 ID 时，说明这篇便签被移动到了该文件夹中。
     * 这个触发器会自动把目标文件夹的 notes_count 加 1，保证文件夹内笔记数量始终准确。
     * 这样列表页无需每次都临时统计，直接读取计数即可。
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_update "+
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * Decrease folder's note count when move note from folder
     * 中文说明:
     * 当某条 note 的 parent_id 从原文件夹变更到别处时，原文件夹中的笔记数应该减少 1。
     * 这个触发器通过 old.parent_id 找回原文件夹，并在计数大于 0 时执行减法，避免出现负数。
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_update " +
        " AFTER UPDATE OF " + NoteColumns.PARENT_ID + " ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0" + ";" +
        " END";

    /**
     * Increase folder's note count when insert new note to the folder
     * 中文说明:
     * 只要新增一条属于某个文件夹的 note 记录，系统就自动增加该文件夹的 notes_count。
     * 这意味着“新建便签”和“文件夹数量变化”之间的关系由数据库自动维护，而不是交给界面层手工处理。
     */
    private static final String NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER increase_folder_count_on_insert " +
        " AFTER INSERT ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + " + 1" +
        "  WHERE " + NoteColumns.ID + "=new." + NoteColumns.PARENT_ID + ";" +
        " END";

    /**
     * Decrease folder's note count when delete note from the folder
     * 中文说明:
     * 当文件夹里的 note 被彻底删除时，原文件夹计数需要同步减少。
     * 这里同样使用 old.parent_id 找到删除前所属的文件夹，并保证 notes_count 不会小于 0。
     */
    private static final String NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER decrease_folder_count_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN " +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.NOTES_COUNT + "=" + NoteColumns.NOTES_COUNT + "-1" +
        "  WHERE " + NoteColumns.ID + "=old." + NoteColumns.PARENT_ID +
        "  AND " + NoteColumns.NOTES_COUNT + ">0;" +
        " END";

    /**
     * Update note's content when insert data with type {@link DataConstants#NOTE}
     * 中文说明:
     * 当 data 表插入的是普通文本便签内容时，note 表中的 snippet 应同步更新为最新正文。
     * snippet 一般用于列表页预览，因此这个触发器保证列表展示与正文内容保持一致。
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER =
        "CREATE TRIGGER update_note_content_on_insert " +
        " AFTER INSERT ON " + TABLE.DATA +
        " WHEN new." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has changed
     * 中文说明:
     * 当文本便签对应的 data 记录内容发生修改时，note 表里的 snippet 也要同步更新。
     * 这样用户返回列表页时看到的仍然是最新摘要，而不是旧内容快照。
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_update " +
        " AFTER UPDATE ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=new." + DataColumns.CONTENT +
        "  WHERE " + NoteColumns.ID + "=new." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Update note's content when data with {@link DataConstants#NOTE} type has deleted
     * 中文说明:
     * 当文本便签的 data 记录被删除后，note 表中用于列表预览的 snippet 也应被清空。
     * 这样可以避免 note 还保留一段已经失效的摘要文本。
     */
    private static final String DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER =
        "CREATE TRIGGER update_note_content_on_delete " +
        " AFTER delete ON " + TABLE.DATA +
        " WHEN old." + DataColumns.MIME_TYPE + "='" + DataConstants.NOTE + "'" +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.SNIPPET + "=''" +
        "  WHERE " + NoteColumns.ID + "=old." + DataColumns.NOTE_ID + ";" +
        " END";

    /**
     * Delete datas belong to note which has been deleted
     * 中文说明:
     * note 主表中的一篇便签如果被删除，那么它对应的 data 子表内容也必须同步删除。
     * 这是一个典型的级联清理，目的是防止数据库中残留“孤儿记录”。
     */
    private static final String NOTE_DELETE_DATA_ON_DELETE_TRIGGER =
        "CREATE TRIGGER delete_data_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.DATA +
        "   WHERE " + DataColumns.NOTE_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Delete notes belong to folder which has been deleted
     * 中文说明:
     * 当一个文件夹被删除时，文件夹下所有子笔记也应该一起删除。
     * 这个触发器负责把 parent_id 指向被删文件夹的所有 note 记录同步清空，保持层级关系一致。
     */
    private static final String FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER =
        "CREATE TRIGGER folder_delete_notes_on_delete " +
        " AFTER DELETE ON " + TABLE.NOTE +
        " BEGIN" +
        "  DELETE FROM " + TABLE.NOTE +
        "   WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    /**
     * Move notes belong to folder which has been moved to trash folder
     * 中文说明:
     * 如果某个文件夹被移动到回收站，那么它下面的所有子笔记也应该一起“进回收站”。
     * 这个触发器会把子笔记的 parent_id 改成垃圾桶 ID，保持回收站语义一致。
     */
    private static final String FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER =
        "CREATE TRIGGER folder_move_notes_on_trash " +
        " AFTER UPDATE ON " + TABLE.NOTE +
        " WHEN new." + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        " BEGIN" +
        "  UPDATE " + TABLE.NOTE +
        "   SET " + NoteColumns.PARENT_ID + "=" + Notes.ID_TRASH_FOLER +
        "  WHERE " + NoteColumns.PARENT_ID + "=old." + NoteColumns.ID + ";" +
        " END";

    public NotesDatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    public void createNoteTable(SQLiteDatabase db) {
        // 创建 note 主表并立即补齐其触发器与系统文件夹。
        // 这个顺序不能乱：先有主表，再有联动逻辑，最后再插入系统预置数据。
        db.execSQL(CREATE_NOTE_TABLE_SQL);
        reCreateNoteTableTriggers(db);
        createSystemFolder(db);
        Log.d(TAG, "note table has been created");
    }

    private void reCreateNoteTableTriggers(SQLiteDatabase db) {
        // 先删除旧触发器，再重新创建新触发器。
        // 这样可以避免重复创建造成 SQLite 报错，同时也能确保升级后所有触发逻辑是最新版本。
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS decrease_folder_count_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS delete_data_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS increase_folder_count_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS folder_delete_notes_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS folder_move_notes_on_trash");

        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_UPDATE_TRIGGER);
        db.execSQL(NOTE_DECREASE_FOLDER_COUNT_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_DELETE_DATA_ON_DELETE_TRIGGER);
        db.execSQL(NOTE_INCREASE_FOLDER_COUNT_ON_INSERT_TRIGGER);
        db.execSQL(FOLDER_DELETE_NOTES_ON_DELETE_TRIGGER);
        db.execSQL(FOLDER_MOVE_NOTES_ON_TRASH_TRIGGER);
    }

    private void createSystemFolder(SQLiteDatabase db) {
        // 系统文件夹初始化：这些记录不是普通用户笔记，而是应用运行所依赖的特殊目录。
        // 它们通常使用负数 ID，方便与正常笔记 ID 区分，也能减少和用户自建数据冲突的概率。
        ContentValues values = new ContentValues();

        // call record foler for call notes
        // 通话记录文件夹：用于存放与通话相关的便签数据，是系统预置目录之一。
        values.put(NoteColumns.ID, Notes.ID_CALL_RECORD_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // root folder which is default folder
        // 根目录：应用默认文件夹，用户未指定归属时通常会落到这个默认位置。
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_ROOT_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // temporary folder which is used for moving note
        // 临时文件夹：用于笔记移动过程中的中间状态，避免在迁移期间数据丢失。
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TEMPARAY_FOLDER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);

        // create trash folder
        // 废纸篓文件夹：用于保存已删除的便签及其层级关系，支持回收站语义。
        values.clear();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    public void createDataTable(SQLiteDatabase db) {
        // 创建 data 子表并同步补上触发器与索引。
        // data 表通常不会单独被用户感知，但它是便签正文、电话记录等内容的真正存储位置。
        db.execSQL(CREATE_DATA_TABLE_SQL);
        reCreateDataTableTriggers(db);
        db.execSQL(CREATE_DATA_NOTE_ID_INDEX_SQL);
        Log.d(TAG, "data table has been created");
    }

    private void reCreateDataTableTriggers(SQLiteDatabase db) {
        // 与 note 表触发器一样，data 表触发器也采取“先删后建”的方式。
        // 这能保证插入/更新/删除正文时，snippet 联动逻辑始终与当前代码保持一致。
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_content_on_delete");

        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_INSERT_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_UPDATE_TRIGGER);
        db.execSQL(DATA_UPDATE_NOTE_CONTENT_ON_DELETE_TRIGGER);
    }

    static synchronized NotesDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new NotesDatabaseHelper(context);
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // 首次创建数据库时调用。
        // 这里的流程是：先建 note 主表，再建 data 子表；两者都创建完成后，数据库才算可用。
        createNoteTable(db);
        createDataTable(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 数据库升级入口。
        // 设计思路是按版本逐级迁移，而不是直接跳到目标版本，这样能兼容老版本用户的数据。
        boolean reCreateTriggers = false;
        boolean skipV2 = false;

        if (oldVersion == 1) {
            upgradeToV2(db);
            skipV2 = true; // this upgrade including the upgrade from v2 to v3
            // 这里表示 v1 升级路径已经顺带处理了后续一部分结构，所以后面不会重复执行 v2->v3 的逻辑。
            oldVersion++;
        }

        if (oldVersion == 2 && !skipV2) {
            upgradeToV3(db);
            reCreateTriggers = true;
            // v2 -> v3 之后，部分表结构/字段或触发器语义发生变化，所以后面需要重建触发器确保一致。
            oldVersion++;
        }

        if (oldVersion == 3) {
            upgradeToV4(db);
            // v3 -> v4 只新增字段，不改核心表结构，因此升级完成后版本号继续递增即可。
            oldVersion++;
        }

        if (reCreateTriggers) {
            // 只有在触发器相关结构有变更时，才需要整体重建触发器。
            reCreateNoteTableTriggers(db);
            reCreateDataTableTriggers(db);
        }

        if (oldVersion != newVersion) {
            throw new IllegalStateException("Upgrade notes database to version " + newVersion
                    + "fails");
        }
    }

    private void upgradeToV2(SQLiteDatabase db) {
        // v1 -> v2 的升级方式比较直接：先删掉旧表，再重新创建新表结构。
        // 这是一种“重建式迁移”，适合早期版本结构差异较大的场景。
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.NOTE);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE.DATA);
        createNoteTable(db);
        createDataTable(db);
    }

    private void upgradeToV3(SQLiteDatabase db) {
        // drop unused triggers
        // 清理掉旧版本里已经不再需要的 modified_date 相关触发器。
        // 这些触发器在新的数据模型中可能已经被其他机制替代，因此升级时要先移除。
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_note_modified_date_on_update");
        // add a column for gtask id
        // 为便签增加 gtask_id 字段，用来支持与云端任务系统的关联。
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.GTASK_ID
                + " TEXT NOT NULL DEFAULT ''");
        // add a trash system folder
        // 同步加入“废纸篓”系统文件夹，确保回收站语义在新版本里依然存在。
        ContentValues values = new ContentValues();
        values.put(NoteColumns.ID, Notes.ID_TRASH_FOLER);
        values.put(NoteColumns.TYPE, Notes.TYPE_SYSTEM);
        db.insert(TABLE.NOTE, null, values);
    }

    private void upgradeToV4(SQLiteDatabase db) {
        // v4 在 note 表中新增 VERSION 字段。
        // 这个字段通常用于记录笔记版本号，便于后续同步、冲突判断或扩展能力。
        db.execSQL("ALTER TABLE " + TABLE.NOTE + " ADD COLUMN " + NoteColumns.VERSION
                + " INTEGER NOT NULL DEFAULT 0");
    }
}
