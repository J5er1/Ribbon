import Foundation

/// Time, days, and zones (build book §4.9).
///
/// Timestamps are relative and coarse — "this morning", "last night",
/// "Tuesday" — never "14 hours ago", which is a count, and never a
/// wall-clock time that would reveal that someone was awake at 3 a.m.
/// A quiet day is displayed relative to the viewer's clock. Date ranges on
/// an ember are absolute ("March – June") because they are addresses in
/// time, not durations.
public enum RibbonClock {

    /// A coarse, warm phrase for when something happened, written to sit
    /// mid-sentence: "Ruth read this morning". Standalone small-caps uses
    /// take the same string; the face does the capitalization work.
    public static func phrase(
        for date: Date,
        now: Date = Date(),
        calendar: Calendar = .current
    ) -> String {
        guard date <= now else { return "just now" }

        if calendar.isDate(date, inSameDayAs: now) {
            let hour = calendar.component(.hour, from: date)
            if hour < 5 { return "in the night" }
            if hour < 12 { return "this morning" }
            if hour < 17 { return "this afternoon" }
            return "this evening"
        }

        if let yesterday = calendar.date(byAdding: .day, value: -1, to: now),
           calendar.isDate(date, inSameDayAs: yesterday) {
            let hour = calendar.component(.hour, from: date)
            return hour >= 21 || hour < 5 ? "last night" : "yesterday"
        }

        // Within the last six days: the weekday, the way a person says it.
        if let weekAgo = calendar.date(byAdding: .day, value: -6, to: now), date >= weekAgo {
            let weekday = calendar.component(.weekday, from: date)
            return Self.weekdayNames[weekday - 1]
        }

        // Older: the month — an address, not a distance.
        let month = Self.monthNames[calendar.component(.month, from: date) - 1]
        if calendar.isDate(date, equalTo: now, toGranularity: .year) {
            return month
        }
        let year = calendar.component(.year, from: date)
        return "\(month) \(year)"
    }

    // The phrases are product copy, not locale output — they must read the
    // same on every device and every CI box (a stripped-down ICU renders
    // "M03" for March, which is both wrong and a leaked numeral).
    // Localization, when it comes, goes through strings files like all
    // other copy.
    private static let weekdayNames = [
        "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
    ]
    private static let monthNames = [
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ]

    /// The date range on an ember record: "March – June", or "March" when a
    /// book was read within one month, with years only when the range
    /// crosses one: "November 2026 – January 2027".
    public static func emberRange(
        start: Date,
        end: Date,
        calendar: Calendar = .current
    ) -> String {
        let startMonth = Self.monthNames[calendar.component(.month, from: start) - 1]
        let endMonth = Self.monthNames[calendar.component(.month, from: end) - 1]
        let startYear = calendar.component(.year, from: start)
        let endYear = calendar.component(.year, from: end)

        if startYear != endYear {
            return "\(startMonth) \(startYear) – \(endMonth) \(endYear)"
        }
        if startMonth == endMonth {
            return startMonth
        }
        return "\(startMonth) – \(endMonth)"
    }
}
