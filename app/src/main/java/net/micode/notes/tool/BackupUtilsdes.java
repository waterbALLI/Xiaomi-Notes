
package net.micode.notes.tool;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.data.Notes.DataColumns;
import net.micode.notes.data.Notes.DataConstants;
import net.micode.notes.data.Notes.NoteColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 备份工具类
 *
 * 提供便签数据导出到文本文件的功能。
 * 采用单例模式设计，支持将用户的便签数据导出为可读的文本格式保存到 SD 卡。
 *
 * 主要功能：
 * 1. 导出所有便签到文本文件
 * 2. 支持文件夹结构和便签内容的完整导出
 * 3. 支持普通便签和通话记录便签的导出
 * 4. 导出文件按日期命名，存放在指定目录
 *
 * 文件格式：
 * - 文件夹以【文件夹名称】格式标识
 * - 每条便签显示最后修改时间
 * - 便签内容逐行输出
 * - 通话记录显示电话号码、通话时间和录音文件位置
 *
 * 导出示例：
 * 【工作】
 * 2024-01-15 14:30
 * 完成项目报告
 * 与客户沟通需求
 *
 * 【个人】
 * 2024-01-16 09:00
 * 买牛奶和面包
 *
 * @see Notes
 * @see PrintStream
 */
public class BackupUtils {

    // ==================== 常量定义 ====================

    /**
     * 日志标签，用于 Logcat 输出时标识来源
     */
    private static final String TAG = "BackupUtils";

    // ==================== 状态码定义 ====================

    /**
     * SD 卡未挂载状态
     * 当外部存储不可用时返回此状态
     */
    public static final int STATE_SD_CARD_UNMOUONTED           = 0;

    /**
     * 备份文件不存在状态
     * 恢复操作时备份文件不存在
     */
    public static final int STATE_BACKUP_FILE_NOT_EXIST        = 1;

    /**
     * 数据被破坏状态
     * 备份文件格式不正确或被其他程序修改
     */
    public static final int STATE_DATA_DESTROIED               = 2;

    /**
     * 系统错误状态
     * 运行时异常导致备份或恢复失败
     */
    public static final int STATE_SYSTEM_ERROR                 = 3;

    /**
     * 操作成功状态
     * 备份或恢复成功完成
     */
    public static final int STATE_SUCCESS                      = 4;

    // ==================== 单例模式 ====================

    /**
     * 单例实例
     */
    private static BackupUtils sInstance;

    /**
     * 文本导出器实例
     * 实际执行导出操作的对象
     */
    private TextExport mTextExport;

    /**
     * 私有构造函数（单例模式）
     *
     * @param context 上下文环境
     */
    private BackupUtils(Context context) {
        mTextExport = new TextExport(context);
    }

    /**
     * 获取单例实例（线程安全）
     *
     * @param context 上下文环境
     * @return BackupUtils 单例对象
     */
    public static synchronized BackupUtils getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new BackupUtils(context);
        }
        return sInstance;
    }

    // ==================== 外部存储检查 ====================

    /**
     * 检查外部存储是否可用
     *
     * 判断 SD 卡是否已挂载且可读写
     *
     * @return true 表示 SD 卡可用，false 表示不可用
     */
    private static boolean externalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    // ==================== 公共接口方法 ====================

    /**
     * 导出便签到文本文件
     *
     * 将用户的所有便签数据导出为文本文件，保存到 SD 卡。
     *
     * @return 操作状态码
     *         STATE_SD_CARD_UNMOUONTED - SD 卡不可用
     *         STATE_SYSTEM_ERROR - 系统错误
     *         STATE_SUCCESS - 导出成功
     */
    public int exportToText() {
        return mTextExport.exportToText();
    }

    /**
     * 获取导出的文本文件名
     *
     * @return 文件名（如 "notes_20240115.txt"）
     */
    public String getExportedTextFileName() {
        return mTextExport.mFileName;
    }

    /**
     * 获取导出的文本文件目录
     *
     * @return 文件目录路径（如 "/sdcard/MIUI/notes/"）
     */
    public String getExportedTextFileDir() {
        return mTextExport.mFileDirectory;
    }

    // ==================== 内部类：文本导出器 ====================

    /**
     * 文本导出器内部类
     *
     * 负责实际执行便签数据导出到文本文件的核心逻辑。
     *
     * 导出流程：
     * 1. 创建输出文件流
     * 2. 导出所有文件夹及其包含的便签
     * 3. 导出根目录下的便签
     * 4. 关闭文件流
     */
    private static class TextExport {

        // ==================== 数据库查询投影 ====================

        /**
         * 便签表查询字段投影
         */
        private static final String[] NOTE_PROJECTION = {
                NoteColumns.ID,              // 便签 ID
                NoteColumns.MODIFIED_DATE,   // 最后修改时间
                NoteColumns.SNIPPET,         // 摘要（文件夹名称或便签预览）
                NoteColumns.TYPE             // 类型（文件夹/便签）
        };

        // 便签投影列索引
        private static final int NOTE_COLUMN_ID = 0;              // ID 列索引
        private static final int NOTE_COLUMN_MODIFIED_DATE = 1;   // 修改时间列索引
        private static final int NOTE_COLUMN_SNIPPET = 2;         // 摘要列索引

        /**
         * 数据表查询字段投影
         */
        private static final String[] DATA_PROJECTION = {
                DataColumns.CONTENT,    // 内容
                DataColumns.MIME_TYPE,  // MIME 类型
                DataColumns.DATA1,      // 数据字段1（通话日期）
                DataColumns.DATA2,      // 数据字段2
                DataColumns.DATA3,      // 数据字段3（电话号码）
                DataColumns.DATA4,      // 数据字段4
        };

        // 数据投影列索引
        private static final int DATA_COLUMN_CONTENT = 0;        // 内容列索引
        private static final int DATA_COLUMN_MIME_TYPE = 1;      // MIME 类型列索引
        private static final int DATA_COLUMN_CALL_DATE = 2;      // 通话日期列索引
        private static final int DATA_COLUMN_PHONE_NUMBER = 4;    // 电话号码列索引

        // ==================== 格式化字符串索引 ====================

        /**
         * 文本格式化字符串数组
         * 从资源文件加载，支持国际化和自定义格式
         */
        private final String [] TEXT_FORMAT;

        /**
         * 格式化类型：文件夹名称
         */
        private static final int FORMAT_FOLDER_NAME          = 0;

        /**
         * 格式化类型：便签日期
         */
        private static final int FORMAT_NOTE_DATE            = 1;

        /**
         * 格式化类型：便签内容
         */
        private static final int FORMAT_NOTE_CONTENT         = 2;

        // ==================== 成员变量 ====================

        private Context mContext;          // 上下文
        private String mFileName;          // 导出文件名
        private String mFileDirectory;     // 导出文件目录

        /**
         * 构造函数
         *
         * @param context 上下文环境
         */
        public TextExport(Context context) {
            // 从资源加载文本格式化模板
            TEXT_FORMAT = context.getResources().getStringArray(R.array.format_for_exported_note);
            mContext = context;
            mFileName = "";
            mFileDirectory = "";
        }

        /**
         * 获取格式化字符串
         *
         * @param id 格式化类型 ID
         * @return 格式化模板字符串
         */
        private String getFormat(int id) {
            return TEXT_FORMAT[id];
        }

        /**
         * 导出文件夹内容到文本
         *
         * 递归导出指定文件夹下的所有便签。
         *
         * 输出格式：
         * 【文件夹名称】
         * 2024-01-15 14:30
         * 便签内容第一行
         * 便签内容第二行
         *
         * @param folderId 文件夹 ID
         * @param ps 打印流，指向输出文件
         */
        private void exportFolderToText(String folderId, PrintStream ps) {
            // 查询该文件夹下的所有便签
            Cursor notesCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.PARENT_ID + "=?",
                    new String[] { folderId },
                    null);

            if (notesCursor != null) {
                if (notesCursor.moveToFirst()) {
                    do {
                        // 打印便签的最后修改时间
                        // 格式示例：2024-01-15 14:30
                        ps.println(String.format(
                                getFormat(FORMAT_NOTE_DATE),
                                DateFormat.format(
                                        mContext.getString(R.string.format_datetime_mdhm),
                                        notesCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));

                        // 导出便签内容
                        String noteId = notesCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (notesCursor.moveToNext());
                }
                notesCursor.close();
            }
        }

        /**
         * 导出便签内容到文本
         *
         * 根据便签的 MIME 类型，导出相应的内容：
         * - 普通便签：导出文本内容
         * - 通话记录便签：导出电话号码、通话时间、录音文件位置
         *
         * @param noteId 便签 ID
         * @param ps 打印流，指向输出文件
         */
        private void exportNoteToText(String noteId, PrintStream ps) {
            // 查询该便签下的所有数据
            Cursor dataCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_DATA_URI,
                    DATA_PROJECTION,
                    DataColumns.NOTE_ID + "=?",
                    new String[] { noteId },
                    null);

            if (dataCursor != null) {
                if (dataCursor.moveToFirst()) {
                    do {
                        String mimeType = dataCursor.getString(DATA_COLUMN_MIME_TYPE);

                        if (DataConstants.CALL_NOTE.equals(mimeType)) {
                            // ===== 通话记录便签 =====
                            // 获取电话号码
                            String phoneNumber = dataCursor.getString(DATA_COLUMN_PHONE_NUMBER);
                            // 获取通话时间
                            long callDate = dataCursor.getLong(DATA_COLUMN_CALL_DATE);
                            // 获取录音文件位置
                            String location = dataCursor.getString(DATA_COLUMN_CONTENT);

                            // 输出电话号码
                            if (!TextUtils.isEmpty(phoneNumber)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        phoneNumber));
                            }
                            // 输出通话时间
                            ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                    DateFormat.format(
                                            mContext.getString(R.string.format_datetime_mdhm),
                                            callDate)));
                            // 输出录音文件位置
                            if (!TextUtils.isEmpty(location)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        location));
                            }
                        } else if (DataConstants.NOTE.equals(mimeType)) {
                            // ===== 普通便签 =====
                            String content = dataCursor.getString(DATA_COLUMN_CONTENT);
                            if (!TextUtils.isEmpty(content)) {
                                ps.println(String.format(getFormat(FORMAT_NOTE_CONTENT),
                                        content));
                            }
                        }
                    } while (dataCursor.moveToNext());
                }
                dataCursor.close();
            }

            // 在便签之间打印分隔符
            try {
                ps.write(new byte[] {
                        Character.LINE_SEPARATOR, Character.LETTER_NUMBER
                });
            } catch (IOException e) {
                Log.e(TAG, e.toString());
            }
        }

        /**
         * 导出所有便签到文本文件
         *
         * 这是导出的主入口方法，执行完整的导出流程：
         *
         * 1. 检查 SD 卡是否可用
         * 2. 创建输出文件
         * 3. 导出所有文件夹及其中的便签
         * 4. 导出根目录下的便签
         * 5. 关闭文件流
         *
         * @return 操作状态码
         */
        public int exportToText() {
            // 检查 SD 卡是否可用
            if (!externalStorageAvailable()) {
                Log.d(TAG, "Media was not mounted");
                return STATE_SD_CARD_UNMOUONTED;
            }

            // 获取输出文件流
            PrintStream ps = getExportToTextPrintStream();
            if (ps == null) {
                Log.e(TAG, "get print stream error");
                return STATE_SYSTEM_ERROR;
            }

            // ===== 第一步：导出所有文件夹及其内容 =====
            // 查询条件：
            // - 类型为文件夹（TYPE_FOLDER）且不在回收站中
            // - 或者通话记录文件夹（ID_CALL_RECORD_FOLDER）
            Cursor folderCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    "(" + NoteColumns.TYPE + "=" + Notes.TYPE_FOLDER + " AND "
                            + NoteColumns.PARENT_ID + "<>" + Notes.ID_TRASH_FOLER + ") OR "
                            + NoteColumns.ID + "=" + Notes.ID_CALL_RECORD_FOLDER,
                    null, null);

            if (folderCursor != null) {
                if (folderCursor.moveToFirst()) {
                    do {
                        // 获取文件夹名称
                        String folderName = "";
                        if (folderCursor.getLong(NOTE_COLUMN_ID) == Notes.ID_CALL_RECORD_FOLDER) {
                            // 通话记录文件夹使用特殊名称
                            folderName = mContext.getString(R.string.call_record_folder_name);
                        } else {
                            folderName = folderCursor.getString(NOTE_COLUMN_SNIPPET);
                        }

                        // 输出文件夹标题
                        if (!TextUtils.isEmpty(folderName)) {
                            ps.println(String.format(getFormat(FORMAT_FOLDER_NAME), folderName));
                        }

                        // 导出该文件夹下的所有便签
                        String folderId = folderCursor.getString(NOTE_COLUMN_ID);
                        exportFolderToText(folderId, ps);
                    } while (folderCursor.moveToNext());
                }
                folderCursor.close();
            }

            // ===== 第二步：导出根目录下的便签 =====
            // 查询条件：类型为便签且父文件夹为根目录（parent_id=0）
            Cursor noteCursor = mContext.getContentResolver().query(
                    Notes.CONTENT_NOTE_URI,
                    NOTE_PROJECTION,
                    NoteColumns.TYPE + "=" + Notes.TYPE_NOTE + " AND "
                            + NoteColumns.PARENT_ID + "=0",
                    null, null);

            if (noteCursor != null) {
                if (noteCursor.moveToFirst()) {
                    do {
                        // 输出便签修改时间
                        ps.println(String.format(
                                getFormat(FORMAT_NOTE_DATE),
                                DateFormat.format(
                                        mContext.getString(R.string.format_datetime_mdhm),
                                        noteCursor.getLong(NOTE_COLUMN_MODIFIED_DATE))));

                        // 输出便签内容
                        String noteId = noteCursor.getString(NOTE_COLUMN_ID);
                        exportNoteToText(noteId, ps);
                    } while (noteCursor.moveToNext());
                }
                noteCursor.close();
            }

            // 关闭打印流
            ps.close();
            return STATE_SUCCESS;
        }

        /**
         * 获取导出文件的打印流
         *
         * 创建导出文件并返回对应的 PrintStream。
         *
         * 文件命名规则：
         * - 文件路径：/sdcard/MIUI/notes/
         * - 文件名：note_export_20240115.txt
         *
         * @return PrintStream 对象，失败返回 null
         */
        private PrintStream getExportToTextPrintStream() {
            // 生成输出文件
            File file = generateFileMountedOnSDcard(mContext, R.string.file_path,
                    R.string.file_name_txt_format);
            if (file == null) {
                Log.e(TAG, "create file to exported failed");
                return null;
            }

            // 保存文件名和目录
            mFileName = file.getName();
            mFileDirectory = mContext.getString(R.string.file_path);

            // 创建打印流
            PrintStream ps = null;
            try {
                FileOutputStream fos = new FileOutputStream(file);
                ps = new PrintStream(fos);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return null;
            } catch (NullPointerException e) {
                e.printStackTrace();
                return null;
            }
            return ps;
        }
    }

    /**
     * 在 SD 卡上生成导出文件
     *
     * 创建用于存储导出数据的目录和文件。
     *
     * 目录结构：
     * /sdcard/MIUI/notes/          (文件路径)
     * └── note_export_20240115.txt  (文件名)
     *
     * @param context 上下文环境
     * @param filePathResId 文件路径资源 ID
     * @param fileNameFormatResId 文件名格式资源 ID
     * @return 生成的 File 对象，失败返回 null
     */
    private static File generateFileMountedOnSDcard(Context context,
                                                    int filePathResId, int fileNameFormatResId) {

        // 构建目录路径
        StringBuilder sb = new StringBuilder();
        sb.append(Environment.getExternalStorageDirectory());  // /sdcard
        sb.append(context.getString(filePathResId));          // /MIUI/notes/
        File filedir = new File(sb.toString());

        // 构建文件路径（带日期）
        sb.append(context.getString(
                fileNameFormatResId,
                DateFormat.format(context.getString(R.string.format_date_ymd),
                        System.currentTimeMillis())));
        File file = new File(sb.toString());

        try {
            // 创建目录（如果不存在）
            if (!filedir.exists()) {
                filedir.mkdir();
            }
            // 创建文件（如果不存在）
            if (!file.exists()) {
                file.createNewFile();
            }
            return file;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }
}