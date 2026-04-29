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
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  /** 节次开始前允许提前发起考勤的窗口。 */
  public static final Duration PREP_WINDOW = Duration.ofMinutes(15);

  /** 节次结束后允许补开的窗口。 */
  public static final Duration GRACE_WINDOW = Duration.ofMinutes(5);

  /** 连排合并阈值：相邻节次间隔不超过该时长视为一段连续课。 */
  public static final Duration MERGE_GAP = Duration.ofMinutes(30);

  public record Period(int period, LocalTime start, LocalTime end) {}

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

  public static List<Period> all() {
    return PERIODS;
  }

  public static Optional<Period> find(int period) {
    return PERIODS.stream().filter(p -> p.period() == period).findFirst();
  }

  public static LocalTime startOf(int period) {
    return find(period).orElseThrow(() -> new IllegalArgumentException("未知节次：" + period)).start();
  }

  public static LocalTime endOf(int period) {
    return find(period).orElseThrow(() -> new IllegalArgumentException("未知节次：" + period)).end();
  }

  /** 相邻 period 是否应合并：必须严格连续 (p2 = p1 + 1) 且 p1 结束到 p2 开始的间隔不超过 {@link #MERGE_GAP}。 */
  public static boolean isContiguous(int p1, int p2) {
    if (p2 != p1 + 1) return false;
    Optional<Period> a = find(p1);
    Optional<Period> b = find(p2);
    if (a.isEmpty() || b.isEmpty()) return false;
    Duration gap = Duration.between(a.get().end(), b.get().start());
    return !gap.isNegative() && gap.compareTo(MERGE_GAP) <= 0;
  }

  public static ZonedDateTime startAt(LocalDate date, int period) {
    return ZonedDateTime.of(date, startOf(period), ZONE);
  }

  public static ZonedDateTime endAt(LocalDate date, int period) {
    return ZonedDateTime.of(date, endOf(period), ZONE);
  }

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
