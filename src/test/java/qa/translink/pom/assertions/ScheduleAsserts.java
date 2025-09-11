package qa.translink.pom.assertions;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ScheduleAsserts {

    private ScheduleAsserts() {}

    // -------- configuration --------
    private static final int MAX_HEADWAY_MIN = Integer.getInteger("HEADWAY_MAX", 60);
    private static final int SERVICE_OFFSET_MIN = 240; // service day starts at 04:00 → +240 minutes

    // -------- time parsing --------
    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA);
    private static final Pattern TIME_RE = Pattern.compile("\\b(1[0-2]|0?\\d):[0-5]\\d\\s?(AM|PM)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Main assertion (visible-first):
     * 1) Try first 4 *visible* times in the current horizontal viewport of the row.
     * 2) If fewer than 4 → fallback to ALL cells: sort ascending, distinct, take first 4 of the service day.
     * 3) If still fewer than 4 → fallback to visible text times.
     * 4) Normalize across midnight and assert strictly increasing + headway ≤ MAX_HEADWAY_MIN.
     */
    public static void assertFirstFourIncreasingAndHeadway(WebDriver d, String stopNumber) {
        WebDriverWait wait = new WebDriverWait(d, Duration.ofSeconds(20));
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("main")));

        // (1) Visible-first extraction
        List<Integer> svcMinutes = retryOnStale(
                () -> extractFirstFourVisibleServiceMinutes(d, stopNumber),
                Duration.ofSeconds(4)
        );

        // (2) Fallback to ALL data-stop-time cells (earliest-of-day)
        if (svcMinutes.size() < 4) {
            svcMinutes = retryOnStale(
                    () -> extractFirstFourServiceMinutesFromRow(d, stopNumber),
                    Duration.ofSeconds(6)
            );
        }

        // (3) Fallback to visible text times
        if (svcMinutes.size() < 4) {
            List<String> times = retryOnStale(
                    () -> extractFirstFourTextTimesFromRow(d, stopNumber),
                    Duration.ofSeconds(6)
            );
            if (times.size() < 4) {
                throw new NoSuchElementException("Less than 4 times for stop #" + stopNumber);
            }
            svcMinutes = toMinutesServiceClock(times);
        }

        // (4) Normalize across midnight and assert
        List<Integer> norm = normalizeAcrossMidnight(svcMinutes);

        for (int i = 1; i < norm.size(); i++) {
            if (!(norm.get(i) > norm.get(i - 1))) {
                throw new AssertionError("Not strictly increasing: " + norm.stream().map(ScheduleAsserts::fmt).toList());
            }
            int gap = norm.get(i) - norm.get(i - 1);
            if (!(gap > 0 && gap <= MAX_HEADWAY_MIN)) {
                throw new AssertionError("Interval > " + MAX_HEADWAY_MIN + " minutes between " +
                        fmt(norm.get(i - 1)) + " and " + fmt(norm.get(i)) +
                        " (gap=" + gap + "m), all=" +
                        norm.stream().map(ScheduleAsserts::fmt).toList());
            }
        }

        System.out.println("[OK] Stop #" + stopNumber + " first 4: " +
                norm.stream().map(ScheduleAsserts::fmt).collect(Collectors.joining(", ")) +
                " — strictly increasing, headway ≤ " + MAX_HEADWAY_MIN + "m.");
    }

    // =================== extraction ===================

    /** Visible-first: take the first 4 cells that are actually visible in the row's horizontal viewport. */
    @SuppressWarnings("unchecked")
    private static List<Integer> extractFirstFourVisibleServiceMinutes(WebDriver d, String stopNumber) {
        By rowXp = By.xpath(".//tr[.//th//a[contains(@href,'/schedules-and-maps/stop/" + stopNumber + "/schedule')]]");
        WebElement row = waitRowPresent(d, rowXp);

        // JS finds the nearest horizontal scroll container and returns raw data-stop-time for intersecting cells
        String js = """
            const row = arguments[0];
            function getScrollContainer(el){
              while (el && el !== document.body){
                const cs = getComputedStyle(el);
                if (/(auto|scroll)/.test(cs.overflowX)) return el;
                el = el.parentElement;
              }
              return window;
            }
            const sc = getScrollContainer(row);
            const scRect = (sc === window)
              ? {left: 0, right: window.innerWidth, top: 0, bottom: window.innerHeight}
              : sc.getBoundingClientRect();

            const cells = Array.from(row.querySelectorAll("td[data-stop-time]"));
            const out = [];
            for (const td of cells){
              const r = td.getBoundingClientRect();
              const horiz = r.right > scRect.left && r.left < scRect.right;
              const vert  = r.bottom > scRect.top  && r.top  < scRect.bottom;
              if (horiz && vert) {
                const v = td.getAttribute("data-stop-time");
                if (v && /^\\d+$/.test(v)) out.push(parseInt(v,10));
              }
            }
            return out;
        """;

        List<Number> rawVisible = (List<Number>) ((JavascriptExecutor) d).executeScript(js, row);
        if (rawVisible == null) return Collections.emptyList();

        // Map raw -> service-clock; keep original visible order; take first 4 distinct
        List<Integer> svc = new ArrayList<>(rawVisible.size());
        for (Number n : rawVisible) svc.add(toServiceClock(n.intValue()));

        List<Integer> first4 = new ArrayList<>(4);
        Integer prev = null;
        for (Integer m : svc) {
            if (!Objects.equals(prev, m)) {
                first4.add(m);
                if (first4.size() == 4) break;
                prev = m;
            }
        }
        return first4;
    }

    /** Read ALL data-stop-time cells, convert to service clock, sort asc, distinct, take first 4 (earliest-of-day). */
    private static List<Integer> extractFirstFourServiceMinutesFromRow(WebDriver d, String stopNumber) {
        By rowXp = By.xpath(".//tr[.//th//a[contains(@href,'/schedules-and-maps/stop/" + stopNumber + "/schedule')]]");
        WebElement row = waitRowPresent(d, rowXp);

        List<WebElement> tds = row.findElements(By.xpath(".//td[@data-stop-time]"));
        List<Integer> svc = new ArrayList<>(tds.size());

        for (WebElement td : tds) {
            try {
                String v = td.getAttribute("data-stop-time");
                if (v == null || v.isBlank()) continue;
                int raw = Integer.parseInt(v.trim());
                svc.add(toServiceClock(raw));
            } catch (StaleElementReferenceException | NumberFormatException ignored) { /* skip */ }
        }

        Collections.sort(svc);
        List<Integer> first4 = new ArrayList<>(4);
        Integer prev = null;
        for (Integer m : svc) {
            if (prev == null || !prev.equals(m)) {
                first4.add(m);
                if (first4.size() == 4) break;
                prev = m;
            }
        }
        return first4;
    }

    /** Fallback: parse first 4 visible text times (already service clock, e.g. "6:55 am"). */
    private static List<String> extractFirstFourTextTimesFromRow(WebDriver d, String stopNumber) {
        By rowXp = By.xpath(".//tr[.//th//a[contains(@href,'/schedules-and-maps/stop/" + stopNumber + "/schedule')]]");
        WebElement row = waitRowPresent(d, rowXp);

        List<WebElement> tds = row.findElements(By.xpath(".//td[contains(.,':')]"));
        List<String> times = new ArrayList<>(4);

        for (WebElement td : tds) {
            String txt = safeText(td);
            if (txt == null) continue;

            java.util.regex.Matcher m = TIME_RE.matcher(txt);
            while (m.find()) {
                times.add(m.group().toUpperCase(Locale.CANADA).replaceAll("\\s+", " ").trim());
                if (times.size() == 4) break;
            }
            if (times.size() == 4) break;
        }
        return times;
    }

    // =================== transforms & formatting ===================

    /** Convert visible "h:mm a" times into service-clock minutes, then sort, distinct, take first 4. */
    private static List<Integer> toMinutesServiceClock(List<String> times) {
        List<Integer> out = new ArrayList<>(times.size());
        for (String t : times) {
            LocalTime lt = LocalTime.parse(t, TF);
            out.add(lt.getHour() * 60 + lt.getMinute()); // already service clock 0..1439
        }
        Collections.sort(out);
        List<Integer> first4 = new ArrayList<>(4);
        Integer prev = null;
        for (Integer m : out) {
            if (prev == null || !prev.equals(m)) {
                first4.add(m);
                if (first4.size() == 4) break;
                prev = m;
            }
        }
        return first4;
    }

    /** Convert raw data-stop-time to service clock minutes (0..1439). */
    private static int toServiceClock(int rawMinutes) {
        int v = (rawMinutes + SERVICE_OFFSET_MIN) % 1440;
        return v < 0 ? v + 1440 : v;
    }

    /** Normalize across midnight by adding 1440 when the time goes backwards. */
    private static List<Integer> normalizeAcrossMidnight(List<Integer> svc) {
        List<Integer> res = new ArrayList<>(svc.size());
        int base = 0, prev = -1;
        for (int m : svc) {
            int cur = m + base;
            if (prev >= 0 && cur <= prev) base += 1440;
            cur = m + base;
            res.add(cur);
            prev = cur;
        }
        return res;
    }

    /** Format minutes (normalized) into "h:mm AM/PM". */
    private static String fmt(int normalizedMinutes) {
        int m = ((normalizedMinutes % 1440) + 1440) % 1440;
        int h = m / 60;
        int mm = m % 60;
        String suffix = (h >= 12) ? "PM" : "AM";
        int h12 = (h % 12 == 0) ? 12 : (h % 12);
        return String.format(Locale.US, "%d:%02d %s", h12, mm, suffix);
    }

    // =================== robustness ===================

    /** Safe text getter that ignores stale elements. */
    private static String safeText(WebElement el) {
        try {
            String t = el.getText();
            return (t == null || t.trim().isEmpty()) ? null : t.trim();
        } catch (StaleElementReferenceException e) {
            return null;
        }
    }

    /** Generic retry wrapper to recover from StaleElementReferenceException. */
    private static <T> T retryOnStale(Supplier<T> fn, Duration timeout) {
        long end = System.currentTimeMillis() + timeout.toMillis();
        RuntimeException last = null;
        while (System.currentTimeMillis() < end) {
            try {
                return fn.get();
            } catch (StaleElementReferenceException e) {
                last = e;
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }
        throw last != null ? last : new RuntimeException("Timeout in retryOnStale");
    }

    /** Wait until a row for stopNumber is present. */
    private static WebElement waitRowPresent(WebDriver d, By rowXp) {
        WebDriverWait wait = new WebDriverWait(d, Duration.ofSeconds(10));
        return wait.until(ExpectedConditions.presenceOfElementLocated(rowXp));
    }
}
