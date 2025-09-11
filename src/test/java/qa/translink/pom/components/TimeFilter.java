package qa.translink.pom.components;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.Select;
import qa.translink.pom.pages.BasePage;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public class TimeFilter extends BasePage {

    public TimeFilter(WebDriver d, org.openqa.selenium.support.ui.WebDriverWait wait) {
        super(d, wait);
    }

    public TimeFilter setDate(LocalDate date) {
        WebElement dateInput = findDateInputOrThrow();
        setDateRobust(dateInput, date);
        return this;
    }

    public TimeFilter setStart(LocalTime t) { setTimeUniversal(t, true); return this; }

    public TimeFilter setEnd(LocalTime t) { setTimeUniversal(t, false); return this; }

    public void submit() {
        clickByAnyText(new String[]{"Search"});
        waitMainContentRefresh();
    }

    // ===== internals =====
    private WebElement findDateInputOrThrow() {
        return retry(12, () -> {
            java.util.List<By> locs = java.util.Arrays.asList(
                    By.cssSelector("input[type='date']"),
                    By.xpath("//input[contains(translate(@id,'DATE','date'),'date')]")
            );
            for (By by : locs) {
                Optional<WebElement> el = d.findElements(by).stream().filter(WebElement::isDisplayed).findFirst();
                if (el.isPresent()) return el.get();
            }
            js("document.querySelector('#schedule,#route-schedule,#content')?.scrollIntoView({block:'center'});");
            for (By by : locs) {
                Optional<WebElement> el = d.findElements(by).stream().filter(WebElement::isDisplayed).findFirst();
                if (el.isPresent()) return el.get();
            }
            throw new NoSuchElementException("Date input not found");
        });
    }

    private void setDateRobust(WebElement input, LocalDate date) {
        js("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", input);

        String type = (input.getAttribute("type") == null ? "" : input.getAttribute("type")).toLowerCase(Locale.ROOT);
        String placeholder = (input.getAttribute("placeholder") == null ? "" : input.getAttribute("placeholder"));
        String current = (input.getAttribute("value") == null ? "" : input.getAttribute("value"));

        String ymd = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String mmddyyyy = date.format(DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US));
        String ddmmyyyy = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US));

        if ("date".equals(type)) {
            setValueWithEvents(input, ymd);
            retry(4, () -> {
                String v = input.getAttribute("value") == null ? "" : input.getAttribute("value");
                if (!ymd.equals(v)) throw new RuntimeException("date not applied(native): " + v);
                return Boolean.TRUE;
            });
            return;
        }

        if (placeholder.matches(".*[Mm]{2}[/-][Dd]{2}[/-][Yy]{4}.*") || current.matches("\\d{2}[/\\-]\\d{2}[/\\-]\\d{4}")) {
            if (tryApplyMasked(input, mmddyyyy)) return;
            if (tryApplyMasked(input, ddmmyyyy)) return;
        }

        js("arguments[0].removeAttribute('readonly');", input);
        input.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE, mmddyyyy);
        js("arguments[0].dispatchEvent(new Event('input',{bubbles:true})); arguments[0].dispatchEvent(new Event('change',{bubbles:true})); arguments[0].blur();", input);
        try {
            retry(2, () -> {
                String v = input.getAttribute("value") == null ? "" : input.getAttribute("value");
                if (!mmddyyyy.equals(v)) throw new RuntimeException("date not applied(typed): " + v);
                return Boolean.TRUE;
            });
            return;
        } catch (RuntimeException ignored) {}

        openCalendarFor(input);
        clickDayInAnyDatepicker(date);
        retry(4, () -> {
            String v = input.getAttribute("value") == null ? "" : input.getAttribute("value");
            if (!(ymd.equals(v) || mmddyyyy.equals(v) || ddmmyyyy.equals(v))) {
                throw new RuntimeException("date not reflected: " + v);
            }
            return Boolean.TRUE;
        });
    }

    private boolean tryApplyMasked(WebElement input, String val) {
        setValueWithEvents(input, val);
        try {
            retry(2, () -> {
                String v = input.getAttribute("value") == null ? "" : input.getAttribute("value");
                if (!val.equals(v)) throw new RuntimeException("not applied: " + v);
                return Boolean.TRUE;
            });
            return true;
        } catch (RuntimeException ignore) { return false; }
    }

    private void setTimeUniversal(LocalTime t, boolean isStart) {
        Optional<WebElement> nativeTime = d.findElements(By.cssSelector(
                (isStart? "#schedulestimefilter-starttime" : "#schedulestimefilter-endtime") + ", " +
                        "input[type='time'][id*='"+(isStart?"start":"end")+"' i], " +
                        "input[type='time'][name*='"+(isStart?"start":"end")+"' i]"
        )).stream().filter(WebElement::isDisplayed).findFirst();

        if (nativeTime.isPresent()) {
            String hhmm = t.format(DateTimeFormatter.ofPattern("HH:mm"));
            setNativeTime(nativeTime.get(), hhmm);
            return;
        }

        Optional<WebElement> selectBox = findFieldByLabel(isStart ? "Start" : "End", "select");
        if (selectBox.isPresent()) {
            String opt12 = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
            String opt24 = t.format(DateTimeFormatter.ofPattern("HH:mm"));
            setSelectValue(selectBox.get(), opt12, opt24);
            return;
        }

        Optional<WebElement> textInput = findFieldByLabel(isStart ? "Start" : "End", "input");
        if (textInput.isPresent()) {
            String opt12 = t.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US));
            js("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", textInput.get());
            setValueWithEvents(textInput.get(), opt12);
            return;
        }

        throw new NoSuchElementException("cannot find "+(isStart?"start":"end")+" time control");
    }

    private Optional<WebElement> findFieldByLabel(String startOrEnd, String tag) {
        String key = startOrEnd.toLowerCase(Locale.ROOT);
        String xp = String.join(" | ",
                "//label[contains(lower-case(.),'" + key + " time')]/following::" + tag + "[1]",
                "//fieldset[.//legend[contains(lower-case(.),'" + key + "')]]//" + tag,
                "//div[label[contains(lower-case(.),'" + key + "')]]//" + tag
        );
        return d.findElements(By.xpath(xp)).stream().filter(WebElement::isDisplayed).findFirst();
    }

    private void setNativeTime(WebElement el, String hhmm) {
        if (!"time".equalsIgnoreCase(el.getAttribute("type"))) throw new IllegalStateException("not <input type='time'>");
        js("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus();", el);
        setValueWithEvents(el, hhmm);
        try {
            retry(2, () -> {
                String v = el.getAttribute("value") == null ? "" : el.getAttribute("value");
                if (!hhmm.equals(v)) throw new RuntimeException("time not applied");
                return Boolean.TRUE;
            });
        } catch (RuntimeException ex) {
            el.sendKeys(Keys.chord(Keys.CONTROL, "a"), Keys.DELETE, hhmm);
            js("arguments[0].dispatchEvent(new Event('input',{bubbles:true})); arguments[0].dispatchEvent(new Event('change',{bubbles:true})); arguments[0].blur();", el);
        }
    }

    private void setSelectValue(WebElement selectEl, String... candidates) {
        Select s = new Select(selectEl);
        for (String c : candidates) {
            var byVal = s.getOptions().stream().filter(o -> c.equalsIgnoreCase(o.getAttribute("value")==null?"":o.getAttribute("value"))).collect(Collectors.toList());
            if (!byVal.isEmpty()) { s.selectByValue(byVal.get(0).getAttribute("value")); return; }
            var byText = s.getOptions().stream().filter(o -> (o.getText()==null?"":o.getText())
                    .toLowerCase(Locale.ROOT).contains(c.toLowerCase(Locale.ROOT))).toList();
            if (!byText.isEmpty()) { s.selectByVisibleText(byText.get(0).getText()); return; }
        }
        try { s.selectByVisibleText(candidates[0]); } catch (Exception e) {
            throw new NoSuchElementException("select option not found for: " + java.util.Arrays.toString(candidates));
        }
    }

    private void clickByAnyText(String[] variants) {
        for (String v : variants) {
            WebElement el = findByAnyText(v, "button", 4);
            if (el != null) { tryClick(el); return; }
        }
        throw new NoSuchElementException("none of texts found: " + java.util.Arrays.toString(variants));
    }

    private WebElement findByAnyText(String text, String tag, int timeoutSec) {
        try {
            return retry(timeoutSec, () -> {
                String xp = "//" + tag + "[normalize-space()='" + text + "'] | " +
                        "//" + tag + "[contains(translate(.,'ABCDEFGHIJKLMNOPQRSTUVWXYZ','abcdefghijklmnopqrstuvwxyz'),'" +
                        text.toLowerCase() + "')]";
                return d.findElements(By.xpath(xp)).stream()
                        .filter(WebElement::isDisplayed)
                        .findFirst()
                        .orElseThrow(() -> new NoSuchElementException("no element: " + text));
            });
        } catch (RuntimeException e) { return null; }
    }
}
