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

import java.util.Calendar;

import net.micode.notes.R;
import net.micode.notes.ui.DateTimePicker;
import net.micode.notes.ui.DateTimePicker.OnDateTimeChangedListener;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.text.format.DateFormat;
import android.text.format.DateUtils;

/**
 * 日期时间选择对话框
 * 继承自AlertDialog，封装了DateTimePicker控件，提供日期时间选择功能
 * 用户可以通过对话框选择日期和时间，确认后通过回调返回选中的时间戳
 */
public class DateTimePickerDialog extends AlertDialog implements OnClickListener {

    // 当前选中的日期时间（Calendar对象）
    private Calendar mDate = Calendar.getInstance();

    // 是否使用24小时制
    private boolean mIs24HourView;

    // 日期时间设置完成后的回调监听器
    private OnDateTimeSetListener mOnDateTimeSetListener;

    // 日期时间选择器控件
    private DateTimePicker mDateTimePicker;

    /**
     * 日期时间设置监听器接口
     * 当用户点击对话框的"确定"按钮时回调
     */
    public interface OnDateTimeSetListener {
        /**
         * 日期时间设置完成时的回调方法
         * @param dialog 当前对话框实例
         * @param date 用户选择的日期时间（毫秒时间戳）
         */
        void OnDateTimeSet(AlertDialog dialog, long date);
    }

    /**
     * 构造函数
     * @param context 上下文环境
     * @param date 初始日期时间（毫秒时间戳）
     */
    public DateTimePickerDialog(Context context, long date) {
        super(context);

        // 创建日期时间选择器实例
        mDateTimePicker = new DateTimePicker(context);
        // 将选择器设置为对话框的内容视图
        setView(mDateTimePicker);

        // 为选择器设置日期时间变化监听器
        mDateTimePicker.setOnDateTimeChangedListener(new OnDateTimeChangedListener() {
            /**
             * 当用户滚动选择器改变日期或时间时调用
             * 更新内部Calendar对象并刷新对话框标题显示
             */
            public void onDateTimeChanged(DateTimePicker view, int year, int month,
                                          int dayOfMonth, int hourOfDay, int minute) {
                // 更新Calendar对象的各个字段
                mDate.set(Calendar.YEAR, year);
                mDate.set(Calendar.MONTH, month);
                mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
                mDate.set(Calendar.MINUTE, minute);
                // 更新对话框标题（显示当前选中的日期时间）
                updateTitle(mDate.getTimeInMillis());
            }
        });

        // 设置初始日期时间
        mDate.setTimeInMillis(date);
        // 将秒数归零（只精确到分钟）
        mDate.set(Calendar.SECOND, 0);
        // 同步更新选择器显示的日期时间
        mDateTimePicker.setCurrentDate(mDate.getTimeInMillis());

        // 设置对话框按钮
        // 确定按钮 - 点击时触发当前类的onClick方法
        setButton(context.getString(R.string.datetime_dialog_ok), this);
        // 取消按钮 - 点击时关闭对话框（传null表示不处理额外逻辑）
        setButton2(context.getString(R.string.datetime_dialog_cancel), (OnClickListener)null);

        // 根据系统设置决定使用12小时制还是24小时制
        set24HourView(DateFormat.is24HourFormat(this.getContext()));

        // 更新对话框标题
        updateTitle(mDate.getTimeInMillis());
    }

    /**
     * 设置时间显示制式
     * @param is24HourView true=24小时制，false=12小时制
     */
    public void set24HourView(boolean is24HourView) {
        mIs24HourView = is24HourView;
    }

    /**
     * 设置日期时间设置监听器
     * @param callBack 监听器实例
     */
    public void setOnDateTimeSetListener(OnDateTimeSetListener callBack) {
        mOnDateTimeSetListener = callBack;
    }

    /**
     * 更新对话框标题
     * 根据当前选中的日期时间格式化显示标题
     * @param date 要显示的日期时间（毫秒时间戳）
     */
    private void updateTitle(long date) {
        // 设置格式化标志：显示年份、日期、时间
        int flag = DateUtils.FORMAT_SHOW_YEAR |
                DateUtils.FORMAT_SHOW_DATE |
                DateUtils.FORMAT_SHOW_TIME;

        // 根据时间制式设置24小时格式标志（注意：原代码有误，两次都是FORMAT_24HOUR）
        // 正确的应该是：非24小时制时使用其他标志，但这里写法实际上总是使用24小时格式
        flag |= mIs24HourView ? DateUtils.FORMAT_24HOUR : DateUtils.FORMAT_24HOUR;

        // 格式化日期时间并设置为对话框标题
        setTitle(DateUtils.formatDateTime(this.getContext(), date, flag));
    }

    /**
     * 确定按钮的点击回调
     * 当用户点击"确定"按钮时调用，触发监听器返回选中的时间
     * @param arg0 对话框接口（未使用）
     * @param arg1 按钮ID（未使用）
     */
    public void onClick(DialogInterface arg0, int arg1) {
        if (mOnDateTimeSetListener != null) {
            // 回调返回当前选中的日期时间（毫秒时间戳）
            mOnDateTimeSetListener.OnDateTimeSet(this, mDate.getTimeInMillis());
        }
    }
}