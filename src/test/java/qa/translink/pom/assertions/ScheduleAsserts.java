package qa.translink.pom.assertions;

import org.openqa.selenium.*;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ScheduleAsserts {

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA);
    private static final Pattern TIME_RE = Pattern.compile("\\b(1[0-2]|0?\\d):[0-5]\\d\\s?(AM|PM)\\b", Pattern.CASE_INSENSITIVE);
    private static final int MAX_HEADWAY_MIN = Integer.getInteger("HEADWAY_MAX", 60);

    public static void assertFirstFourIncreasingAndHeadway(WebDriver d, String stopNumber) {
        WebDriverWait wait = new WebDriverWait(d, java.time.Duration.ofSeconds(20));
        WebElement main = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("main")));

        List<Integer> minutes = extractFirstFourMinutesFromRow(main, stopNumber);
        if (minutes.size() < 4) {
            List<String> times = extractFirstFourTextTimesFromRow(main, stopNumber);
            if (times.size() < 4) {
                throw new NoSuchElementException("Less than 4 times for stop #"+stopNumber+
                        ". Found minutes="+minutes+"; textTimes="+times);
            }
            minutes = toMinutes(times);
        }

        minutes = normalizeAcrossMidnight(minutes);

        for (int i = 1; i < minutes.size(); i++) {
            if (!(minutes.get(i) > minutes.get(i-1)))
                throw new AssertionError("Not strictly increasing: " + minutes);
            int gap = minutes.get(i) - minutes.get(i-1);
            if (!(gap > 0 && gap <= MAX_HEADWAY_MIN)) {
                throw new AssertionError("Interval > " + MAX_HEADWAY_MIN + " minutes between " +
                        fmt(minutes.get(i-1)) + " and " + fmt(minutes.get(i)) +
                        " (gap=" + gap + "m), all=" +
                        minutes.stream().map(ScheduleAsserts::fmt).toList());
            }
        }

        System.out.println("[OK] Stop #"+stopNumber+" first 4: " +
                minutes.stream().map(ScheduleAsserts::fmt).collect(Collectors.joining(", ")) +
                " — strictly increasing, headway ≤ " + MAX_HEADWAY_MIN + "m.");
    }

    // === helpers ===
    private static List<Integer> extractFirstFourMinutesFromRow(WebElement main, String stopNumber) {
        By rowXp = By.xpath(".//tr[.//th//a[contains(@href,'/schedules-and-maps/stop/"+stopNumber+"/schedule')]]");
        List<WebElement> rows = main.findElements(rowXp);
        if (rows.isEmpty()) return Collections.emptyList();
        WebElement row = rows.get(0);
        List<WebElement> tds = row.findElements(By.xpath(".//td[@data-stop-time]"))
                .stream().filter(WebElement::isDisplayed).toList();
        List<Integer> out = new ArrayList<>();
        for (WebElement td : tds) {
            String v = td.getAttribute("data-stop-time");
            if (v == null || v.trim().isEmpty()) continue;
            try {
                out.add(Integer.parseInt(v.trim()));
                if (out.size() == 4) break;
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    private static List<String> extractFirstFourTextTimesFromRow(WebElement main, String stopNumber) {
        By rowXp = By.xpath(".//tr[.//th//a[contains(@href,'/schedules-and-maps/stop/"+stopNumber+"/schedule')]]");
        List<WebElement> rows = main.findElements(rowXp);
        if (rows.isEmpty()) return Collections.emptyList();
        WebElement row = rows.get(0);
        List<WebElement> tds = row.findElements(By.xpath(".//td[contains(text(),':')]"))
                .stream().filter(WebElement::isDisplayed).toList();

        List<String> times = new ArrayList<>();
        for (WebElement td : tds) {
            String txt = safeText(td);
            if (txt == null) continue;
            java.util.regex.Matcher m = TIME_RE.matcher(txt);
            while (m.find()) {
                times.add(m.group().toUpperCase(Locale.CANADA).replaceAll("\\s+"," ").trim());
                if (times.size() == 4) break;
            }
            if (times.size() == 4) break;
        }
        return times;
    }

    private static List<Integer> toMinutes(List<String> times) {
        List<Integer> out = new ArrayList<>(times.size());
        for (String t : times) {
            LocalTime lt = LocalTime.parse(t, TF);
            out.add(lt.getHour()*60 + lt.getMinute());
        }
        return out;
    }

    private static List<Integer> normalizeAcrossMidnight(List<Integer> minutes) {
        List<Integer> norm = new ArrayList<>(minutes.size());
        int base = 0, prev = -1;
        for (int m : minutes) {
            int cur = m + base;
            if (prev >= 0 && cur <= prev) base += 24*60;
            cur = m + base;
            norm.add(cur);
            prev = cur;
        }
        return norm;
    }

    private static String fmt(int minutes) {
        int h = (minutes / 60) % 24;
        int m = minutes % 60;
        String suffix = (h >= 12) ? "PM" : "AM";
        int h12 = (h % 12 == 0) ? 12 : (h % 12);
        return String.format(Locale.US, "%d:%02d %s", h12, m, suffix);
    }

    private static String safeText(WebElement el) {
        try {
            String t = el.getText();
            return (t == null || t.trim().isEmpty()) ? null : t.trim();
        } catch (StaleElementReferenceException e) { return null; }
    }
}
