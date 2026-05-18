package com.example.qrattendance.schedule;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/** 节次时间表，与 Android 端 ui/schedule/SchedulePeriods.kt 保持一致。 */
public final class SchedulePeriods {
  // 全局时区：所有节次时刻按上海本地时间计算
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  /** 节次开始前允许提前发起考勤的窗口。 */
  public static final Duration PREP_WINDOW = Duration.ofMinutes(15);

  /** 节次结束后允许补开的窗口。 */
  public static final Duration GRACE_WINDOW = Duration.ofMinutes(5);

  /** 连排合并阈值：相邻节次间隔不超过该时长视为一段连续课。 */
  public static final Duration MERGE_GAP = Duration.ofMinutes(30);

  // 节次定义：第几节 + 起止时间
  public record Period(int period, LocalTime start, LocalTime end) {}

  // 全天 9 节课的固定时间表：第 1-5 节为上午，第 6-9 节为下午（中午 12:15-13:45 休息）
  private static final List<Period> PERIODS =
      List.of(
          new Period(1, LocalTime.of(8, 0), LocalTime.of(8, 45)),
          new Period(2, LocalTime.of(8, 50), LocalTime.of(9, 35)),
          new Period(3, LocalTime.of(9, 50), LocalTime.of(10, 35)),
          new Period(4, LocalTime.of(10, 40), LocalTime.of(11, 25)),
          new Period(5, LocalTime.of(11, 30), LocalTime.of(12, 15)),
          new Period(6, LocalTime.of(13, 45), LocalTime.of(14, 30)),
          new Period(7, LocalTime.of(14, 35), LocalTime.of(15, 20)),
          new Period(8, LocalTime.of(15, 35), LocalTime.of(16, 20)),
          new Period(9, LocalTime.of(16, 25), LocalTime.of(17, 10)));

  private SchedulePeriods() {}

  // 返回完整的 9 节课时间表（只读）
  public static List<Period> all() {
    return PERIODS;
  }

  // 按节次号查找对应的 Period，未找到返回 Optional.empty()
  public static Optional<Period> find(int period) {
    return PERIODS.stream().filter(p -> p.period() == period).findFirst();
  }

  // 取指定节次的开始时刻；节次不存在抛 IllegalArgumentException
  public static LocalTime startOf(int period) {
    return find(period).orElseThrow(() -> new IllegalArgumentException("未知节次：" + period)).start();
  }

  // 取指定节次的结束时刻；节次不存在抛 IllegalArgumentException
  public static LocalTime endOf(int period) {
    return find(period).orElseThrow(() -> new IllegalArgumentException("未知节次：" + period)).end();
  }

  /** 相邻 period 是否应合并：必须严格连续 (p2 = p1 + 1) 且 p1 结束到 p2 开始的间隔不超过 {@link #MERGE_GAP}。 */
  // 用于教师端 today 视图合并连排（如 1-2 节、3-4 节）
  public static boolean isContiguous(int p1, int p2) {
    // 必须严格相邻，p2 = p1 + 1；否则一定不能合并（避免把 1 节和 3 节拼起来）
    if (p2 != p1 + 1) return false;
    Optional<Period> a = find(p1);
    Optional<Period> b = find(p2);
    if (a.isEmpty() || b.isEmpty()) return false;
    // 计算 p1 结束到 p2 开始的时间间隔
    Duration gap = Duration.between(a.get().end(), b.get().start());
    // 间隔非负（防御性检查）且 ≤ 30 分钟才视为连排
    // 例如：第 5 节 12:15 结束，第 6 节 13:45 开始，gap = 1h30min > MERGE_GAP，不合并（午休断开）
    return !gap.isNegative() && gap.compareTo(MERGE_GAP) <= 0;
  }

  // 拼出某日某节次的开始时刻（带时区），用于考勤会话边界判定
  public static ZonedDateTime startAt(LocalDate date, int period) {
    return ZonedDateTime.of(date, startOf(period), ZONE);
  }

  // 拼出某日某节次的结束时刻（带时区）
  public static ZonedDateTime endAt(LocalDate date, int period) {
    return ZonedDateTime.of(date, endOf(period), ZONE);
  }

  // 将日期转为中文星期标签（如"周一"），用于课表展示
  public static String weekdayLabel(LocalDate date) {
    return switch (date.getDayOfWeek()) {
      case MONDAY -> "周一";
      case TUESDAY -> "周二";
      case WEDNESDAY -> "周三";
      case THURSDAY -> "周四";
      case FRIDAY -> "周五";
      case SATURDAY -> "周六";
      case SUNDAY -> "周日";
    };
  }
}
