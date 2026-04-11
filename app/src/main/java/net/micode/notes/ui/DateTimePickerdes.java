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

// 导入必要的类和工具包
import java.text.DateFormatSymbols;  // 用于获取日期格式符号（如AM/PM字符串）
import java.util.Calendar;           // 日历类，处理日期时间计算

import net.micode.notes.R;           // 应用资源类

import android.content.Context;
import android.text.format.DateFormat; // Android日期格式化工具
import android.view.View;
import android.widget.FrameLayout;
import android.widget.NumberPicker;    // 数字选择器控件

/**
 * 自定义的日期时间选择器组件
 * 继承自FrameLayout，包含年月日、时分、AM/PM的选择功能
 * 支持12小时制和24小时制两种显示模式
 */
public class DateTimePicker extends FrameLayout {

    // ==================== 常量定义 ====================

    private static final boolean DEFAULT_ENABLE_STATE = true;  // 默认启用状态

    // 时间相关常量
    private static final int HOURS_IN_HALF_DAY = 12;   // 半天的小时数（12小时制）
    private static final int HOURS_IN_ALL_DAY = 24;    // 全天的小时数（24小时制）
    private static final int DAYS_IN_ALL_WEEK = 7;     // 一周的天数

    // 日期选择器范围（显示一周的日期，中间值为当前选中日期）
    private static final int DATE_SPINNER_MIN_VAL = 0;      // 日期选择器最小值
    private static final int DATE_SPINNER_MAX_VAL = DAYS_IN_ALL_WEEK - 1;  // 日期选择器最大值

    // 24小时制小时选择器范围
    private static final int HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW = 0;   // 24小时制最小值（0点）
    private static final int HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW = 23;  // 24小时制最大值（23点）

    // 12小时制小时选择器范围
    private static final int HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW = 1;    // 12小时制最小值（1点）
    private static final int HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW = 12;   // 12小时制最大值（12点）

    // 分钟选择器范围
    private static final int MINUT_SPINNER_MIN_VAL = 0;   // 分钟最小值
    private static final int MINUT_SPINNER_MAX_VAL = 59;  // 分钟最大值

    // AM/PM选择器范围
    private static final int AMPM_SPINNER_MIN_VAL = 0;    // AM/PM最小值（0表示AM）
    private static final int AMPM_SPINNER_MAX_VAL = 1;    // AM/PM最大值（1表示PM）

    // ==================== 控件成员变量 ====================

    private final NumberPicker mDateSpinner;    // 日期选择器（显示星期几和月日）
    private final NumberPicker mHourSpinner;    // 小时选择器
    private final NumberPicker mMinuteSpinner;  // 分钟选择器
    private final NumberPicker mAmPmSpinner;    // 上午/下午选择器

    private Calendar mDate;                     // 当前选择的日期时间对象

    private String[] mDateDisplayValues = new String[DAYS_IN_ALL_WEEK];  // 日期显示值数组（一周的日期）

    private boolean mIsAm;                      // 是否为上午（true=上午，false=下午）
    private boolean mIs24HourView;              // 是否为24小时制显示
    private boolean mIsEnabled = DEFAULT_ENABLE_STATE;  // 组件是否可用
    private boolean mInitialising;              // 是否正在初始化（避免初始化时触发回调）

    private OnDateTimeChangedListener mOnDateTimeChangedListener;  // 日期时间变化监听器

    // ==================== 监听器实现 ====================

    /**
     * 日期选择器数值变化监听器
     * 当用户滚动日期选择器时，更新内部日期并刷新界面
     */
    private NumberPicker.OnValueChangeListener mOnDateChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            // 根据变化量调整日期（向前或向后移动一天）
            mDate.add(Calendar.DAY_OF_YEAR, newVal - oldVal);
            updateDateControl();  // 更新日期显示控件
            onDateTimeChanged();   // 触发日期时间变化回调
        }
    };

    /**
     * 小时选择器数值变化监听器
     * 处理小时变化时的复杂逻辑，包括：
     * 1. 12小时制下跨越上/下午时的日期调整
     * 2. 24小时制下跨越午夜时的日期调整
     * 3. AM/PM状态的切换
     */
    private NumberPicker.OnValueChangeListener mOnHourChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            boolean isDateChanged = false;  // 标记日期是否发生变化
            Calendar cal = Calendar.getInstance();

            if (!mIs24HourView) {
                // ===== 12小时制处理 =====
                // 从11点（上午）切换到12点（下午）时，日期加1天
                if (!mIsAm && oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 从12点（下午）切换到11点（上午）时，日期减1天
                else if (mIsAm && oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }

                // 跨越上下午边界时，切换AM/PM状态
                if (oldVal == HOURS_IN_HALF_DAY - 1 && newVal == HOURS_IN_HALF_DAY ||
                        oldVal == HOURS_IN_HALF_DAY && newVal == HOURS_IN_HALF_DAY - 1) {
                    mIsAm = !mIsAm;
                    updateAmPmControl();  // 更新AM/PM控件显示
                }
            } else {
                // ===== 24小时制处理 =====
                // 从23点切换到0点时，日期加1天
                if (oldVal == HOURS_IN_ALL_DAY - 1 && newVal == 0) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, 1);
                    isDateChanged = true;
                }
                // 从0点切换到23点时，日期减1天
                else if (oldVal == 0 && newVal == HOURS_IN_ALL_DAY - 1) {
                    cal.setTimeInMillis(mDate.getTimeInMillis());
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                    isDateChanged = true;
                }
            }

            // 计算实际的小时值（12小时制转换为24小时制）
            int newHour = mHourSpinner.getValue() % HOURS_IN_HALF_DAY + (mIsAm ? 0 : HOURS_IN_HALF_DAY);
            mDate.set(Calendar.HOUR_OF_DAY, newHour);
            onDateTimeChanged();  // 触发回调

            // 如果日期发生变化，更新年月日
            if (isDateChanged) {
                setCurrentYear(cal.get(Calendar.YEAR));
                setCurrentMonth(cal.get(Calendar.MONTH));
                setCurrentDay(cal.get(Calendar.DAY_OF_MONTH));
            }
        }
    };

    /**
     * 分钟选择器数值变化监听器
     * 处理分钟变化时的小时进位/退位逻辑
     */
    private NumberPicker.OnValueChangeListener mOnMinuteChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            int minValue = mMinuteSpinner.getMinValue();
            int maxValue = mMinuteSpinner.getMaxValue();
            int offset = 0;  // 小时变化偏移量

            // 从59分钟变为0分钟时，小时+1
            if (oldVal == maxValue && newVal == minValue) {
                offset += 1;
            }
            // 从0分钟变为59分钟时，小时-1
            else if (oldVal == minValue && newVal == maxValue) {
                offset -= 1;
            }

            // 如果有小时进位/退位
            if (offset != 0) {
                mDate.add(Calendar.HOUR_OF_DAY, offset);
                mHourSpinner.setValue(getCurrentHour());  // 更新小时选择器
                updateDateControl();  // 更新日期控件

                // 更新AM/PM状态
                int newHour = getCurrentHourOfDay();
                if (newHour >= HOURS_IN_HALF_DAY) {
                    mIsAm = false;
                    updateAmPmControl();
                } else {
                    mIsAm = true;
                    updateAmPmControl();
                }
            }

            mDate.set(Calendar.MINUTE, newVal);  // 设置分钟
            onDateTimeChanged();  // 触发回调
        }
    };

    /**
     * AM/PM选择器数值变化监听器
     * 切换上下午时，调整小时数（加减12小时）
     */
    private NumberPicker.OnValueChangeListener mOnAmPmChangedListener = new NumberPicker.OnValueChangeListener() {
        @Override
        public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
            mIsAm = !mIsAm;  // 切换上下午状态
            // 调整小时数（加减12小时）
            if (mIsAm) {
                mDate.add(Calendar.HOUR_OF_DAY, -HOURS_IN_HALF_DAY);
            } else {
                mDate.add(Calendar.HOUR_OF_DAY, HOURS_IN_HALF_DAY);
            }
            updateAmPmControl();  // 更新控件显示
            onDateTimeChanged();   // 触发回调
        }
    };

    // ==================== 监听器接口 ====================

    /**
     * 日期时间变化监听器接口
     * 当用户修改日期或时间时回调
     */
    public interface OnDateTimeChangedListener {
        /**
         * 日期时间变化时的回调方法
         * @param view 当前DateTimePicker实例
         * @param year 年份
         * @param month 月份（0-11）
         * @param dayOfMonth 日期（1-31）
         * @param hourOfDay 小时（0-23）
         * @param minute 分钟（0-59）
         */
        void onDateTimeChanged(DateTimePicker view, int year, int month,
                               int dayOfMonth, int hourOfDay, int minute);
    }

    // ==================== 构造函数 ====================

    /**
     * 构造函数（使用当前系统时间）
     * @param context 上下文
     */
    public DateTimePicker(Context context) {
        this(context, System.currentTimeMillis());
    }

    /**
     * 构造函数（指定初始时间，使用系统时间格式）
     * @param context 上下文
     * @param date 初始时间（毫秒值）
     */
    public DateTimePicker(Context context, long date) {
        this(context, date, DateFormat.is24HourFormat(context));
    }

    /**
     * 主构造函数
     * @param context 上下文
     * @param date 初始时间（毫秒值）
     * @param is24HourView 是否使用24小时制
     */
    public DateTimePicker(Context context, long date, boolean is24HourView) {
        super(context);

        mDate = Calendar.getInstance();  // 初始化日历对象
        mInitialising = true;            // 标记开始初始化
        mIsAm = getCurrentHourOfDay() >= HOURS_IN_HALF_DAY;  // 根据当前时间判断上下午

        // 加载布局文件
        inflate(context, R.layout.datetime_picker, this);

        // ===== 初始化日期选择器 =====
        mDateSpinner = (NumberPicker) findViewById(R.id.date);
        mDateSpinner.setMinValue(DATE_SPINNER_MIN_VAL);
        mDateSpinner.setMaxValue(DATE_SPINNER_MAX_VAL);
        mDateSpinner.setOnValueChangedListener(mOnDateChangedListener);

        // ===== 初始化小时选择器 =====
        mHourSpinner = (NumberPicker) findViewById(R.id.hour);
        mHourSpinner.setOnValueChangedListener(mOnHourChangedListener);

        // ===== 初始化分钟选择器 =====
        mMinuteSpinner = (NumberPicker) findViewById(R.id.minute);
        mMinuteSpinner.setMinValue(MINUT_SPINNER_MIN_VAL);
        mMinuteSpinner.setMaxValue(MINUT_SPINNER_MAX_VAL);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);  // 长按时更新间隔100ms
        mMinuteSpinner.setOnValueChangedListener(mOnMinuteChangedListener);

        // ===== 初始化AM/PM选择器 =====
        String[] stringsForAmPm = new DateFormatSymbols().getAmPmStrings();  // 获取系统AM/PM字符串
        mAmPmSpinner = (NumberPicker) findViewById(R.id.amPm);
        mAmPmSpinner.setMinValue(AMPM_SPINNER_MIN_VAL);
        mAmPmSpinner.setMaxValue(AMPM_SPINNER_MAX_VAL);
        mAmPmSpinner.setDisplayedValues(stringsForAmPm);  // 设置显示文本（"上午"/"下午"）
        mAmPmSpinner.setOnValueChangedListener(mOnAmPmChangedListener);

        // 更新所有控件到初始状态
        updateDateControl();
        updateHourControl();
        updateAmPmControl();

        set24HourView(is24HourView);  // 设置时间制式

        setCurrentDate(date);  // 设置当前时间

        setEnabled(isEnabled());

        mInitialising = false;  // 初始化完成
    }

    // ==================== 公共方法 ====================

    /**
     * 设置组件的启用/禁用状态
     * @param enabled true=启用，false=禁用
     */
    @Override
    public void setEnabled(boolean enabled) {
        if (mIsEnabled == enabled) {
            return;
        }
        super.setEnabled(enabled);
        mDateSpinner.setEnabled(enabled);
        mMinuteSpinner.setEnabled(enabled);
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    /**
     * 获取当前日期时间（毫秒值）
     * @return 当前日期时间的毫秒数
     */
    public long getCurrentDateInTimeMillis() {
        return mDate.getTimeInMillis();
    }

    /**
     * 设置当前日期时间
     * @param date 日期时间的毫秒值
     */
    public void setCurrentDate(long date) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(date);
        setCurrentDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
    }

    /**
     * 设置当前日期时间（各字段分别指定）
     * @param year 年份
     * @param month 月份（0-11）
     * @param dayOfMonth 日期（1-31）
     * @param hourOfDay 小时（0-23）
     * @param minute 分钟（0-59）
     */
    public void setCurrentDate(int year, int month,
                               int dayOfMonth, int hourOfDay, int minute) {
        setCurrentYear(year);
        setCurrentMonth(month);
        setCurrentDay(dayOfMonth);
        setCurrentHour(hourOfDay);
        setCurrentMinute(minute);
    }

    /**
     * 获取当前年份
     */
    public int getCurrentYear() {
        return mDate.get(Calendar.YEAR);
    }

    /**
     * 设置当前年份
     */
    public void setCurrentYear(int year) {
        if (!mInitialising && year == getCurrentYear()) {
            return;
        }
        mDate.set(Calendar.YEAR, year);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前月份（0-11）
     */
    public int getCurrentMonth() {
        return mDate.get(Calendar.MONTH);
    }

    /**
     * 设置当前月份
     * @param month 月份（0-11）
     */
    public void setCurrentMonth(int month) {
        if (!mInitialising && month == getCurrentMonth()) {
            return;
        }
        mDate.set(Calendar.MONTH, month);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前日期（1-31）
     */
    public int getCurrentDay() {
        return mDate.get(Calendar.DAY_OF_MONTH);
    }

    /**
     * 设置当前日期
     * @param dayOfMonth 日期（1-31）
     */
    public void setCurrentDay(int dayOfMonth) {
        if (!mInitialising && dayOfMonth == getCurrentDay()) {
            return;
        }
        mDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        updateDateControl();
        onDateTimeChanged();
    }

    /**
     * 获取当前小时（24小时制，0-23）
     */
    public int getCurrentHourOfDay() {
        return mDate.get(Calendar.HOUR_OF_DAY);
    }

    /**
     * 获取当前小时（根据当前时间制式返回对应格式）
     * 24小时制：返回0-23
     * 12小时制：返回1-12
     */
    private int getCurrentHour() {
        if (mIs24HourView){
            return getCurrentHourOfDay();
        } else {
            int hour = getCurrentHourOfDay();
            if (hour > HOURS_IN_HALF_DAY) {
                return hour - HOURS_IN_HALF_DAY;  // 下午13-23点转换为1-11点
            } else {
                return hour == 0 ? HOURS_IN_HALF_DAY : hour;  // 0点显示为12点
            }
        }
    }

    /**
     * 设置当前小时（24小时制）
     * @param hourOfDay 小时（0-23）
     */
    public void setCurrentHour(int hourOfDay) {
        if (!mInitialising && hourOfDay == getCurrentHourOfDay()) {
            return;
        }
        mDate.set(Calendar.HOUR_OF_DAY, hourOfDay);

        if (!mIs24HourView) {
            // 12小时制下，需要调整显示和AM/PM状态
            if (hourOfDay >= HOURS_IN_HALF_DAY) {
                mIsAm = false;  // 下午
                if (hourOfDay > HOURS_IN_HALF_DAY) {
                    hourOfDay -= HOURS_IN_HALF_DAY;  // 13-23转换为1-11
                }
            } else {
                mIsAm = true;   // 上午
                if (hourOfDay == 0) {
                    hourOfDay = HOURS_IN_HALF_DAY;  // 0点显示为12点
                }
            }
            updateAmPmControl();
        }

        mHourSpinner.setValue(hourOfDay);
        onDateTimeChanged();
    }

    /**
     * 获取当前分钟
     */
    public int getCurrentMinute() {
        return mDate.get(Calendar.MINUTE);
    }

    /**
     * 设置当前分钟
     * @param minute 分钟（0-59）
     */
    public void setCurrentMinute(int minute) {
        if (!mInitialising && minute == getCurrentMinute()) {
            return;
        }
        mMinuteSpinner.setValue(minute);
        mDate.set(Calendar.MINUTE, minute);
        onDateTimeChanged();
    }

    /**
     * 判断是否为24小时制
     */
    public boolean is24HourView() {
        return mIs24HourView;
    }

    /**
     * 设置时间制式（12小时制或24小时制）
     * @param is24HourView true=24小时制，false=12小时制
     */
    public void set24HourView(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        mIs24HourView = is24HourView;
        mAmPmSpinner.setVisibility(is24HourView ? View.GONE : View.VISIBLE);  // 24小时制隐藏AM/PM选择器

        int hour = getCurrentHourOfDay();
        updateHourControl();  // 更新小时选择器的范围
        setCurrentHour(hour);  // 重新设置小时值（适配新制式）
        updateAmPmControl();
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 更新日期控件显示
     * 显示当前日期前后各3天，共7天的日期（格式：MM.dd EEEE）
     */
    private void updateDateControl() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(mDate.getTimeInMillis());
        cal.add(Calendar.DAY_OF_YEAR, -DAYS_IN_ALL_WEEK / 2 - 1);  // 向前移动3天

        mDateSpinner.setDisplayedValues(null);

        // 生成一周的日期显示值
        for (int i = 0; i < DAYS_IN_ALL_WEEK; ++i) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
            mDateDisplayValues[i] = (String) DateFormat.format("MM.dd EEEE", cal);
        }

        mDateSpinner.setDisplayedValues(mDateDisplayValues);
        mDateSpinner.setValue(DAYS_IN_ALL_WEEK / 2);  // 设置当前选中项为中间值
        mDateSpinner.invalidate();  // 刷新控件
    }

    /**
     * 更新AM/PM控件显示
     * 根据当前时间和时间制式更新AM/PM选择器的状态
     */
    private void updateAmPmControl() {
        if (mIs24HourView) {
            mAmPmSpinner.setVisibility(View.GONE);
        } else {
            int index = mIsAm ? Calendar.AM : Calendar.PM;
            mAmPmSpinner.setValue(index);
            mAmPmSpinner.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 更新小时控件范围
     * 根据时间制式设置小时选择器的最小值和最大值
     */
    private void updateHourControl() {
        if (mIs24HourView) {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_24_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_24_HOUR_VIEW);
        } else {
            mHourSpinner.setMinValue(HOUR_SPINNER_MIN_VAL_12_HOUR_VIEW);
            mHourSpinner.setMaxValue(HOUR_SPINNER_MAX_VAL_12_HOUR_VIEW);
        }
    }

    /**
     * 设置日期时间变化监听器
     * @param callback 监听器实例
     */
    public void setOnDateTimeChangedListener(OnDateTimeChangedListener callback) {
        mOnDateTimeChangedListener = callback;
    }

    /**
     * 触发日期时间变化事件
     * 调用监听器的回调方法通知外部
     */
    private void onDateTimeChanged() {
        if (mOnDateTimeChangedListener != null) {
            mOnDateTimeChangedListener.onDateTimeChanged(this, getCurrentYear(),
                    getCurrentMonth(), getCurrentDay(), getCurrentHourOfDay(), getCurrentMinute());
        }
    }
}