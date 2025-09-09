package qa.translink.pom.pages;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Supplier;

public abstract class BasePage {
    protected final WebDriver d;
    protected final WebDriverWait wait;

    protected BasePage(WebDriver d, WebDriverWait wait) {
        this.d = d;
        this.wait = wait;
    }

    protected Object js(String script, Object... args) {
        return ((JavascriptExecutor) d).executeScript(script, args);
    }

    protected void tryClick(WebElement el) {
        wait.until(ExpectedConditions.elementToBeClickable(el)).click();
    }

    protected <T> T retry(int timeoutSec, Supplier<T> fn) {
        long end = System.currentTimeMillis() + timeoutSec * 1000L;
        RuntimeException last = null;
        while (System.currentTimeMillis() < end) {
            try { return fn.get(); }
            catch (RuntimeException e) { last = e; try { Thread.sleep(200);} catch (InterruptedException ignored) {} }
        }
        throw (last != null ? last : new RuntimeException("timeout"));
    }


    protected void waitMainContentRefresh() {
        String before = (String) js("return document.querySelector('main')?.innerText || '';");
        long end = System.currentTimeMillis() + 6000L;
        while (System.currentTimeMillis() < end) {
            String after = (String) js("return document.querySelector('main')?.innerText || '';");
            if (Math.abs(after.length() - before.length()) > 50 || !after.equals(before)) return;
            try { Thread.sleep(200);} catch (InterruptedException ignored) {}
        }
    }

    protected void setValueWithEvents(WebElement el, String value) {
        js(
                "const el=arguments[0], val=arguments[1];" +
                        "const d=Object.getOwnPropertyDescriptor(el.__proto__,'value')||Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');" +
                        "d.set.call(el,''); el.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "d.set.call(el,val); el.dispatchEvent(new Event('input',{bubbles:true}));" +
                        "el.dispatchEvent(new Event('change',{bubbles:true})); el.blur();",
                el, value
        );
    }

    protected void openCalendarFor(WebElement input) {
        try { input.click(); } catch (Exception ignored) {}
        List<WebElement> btns = d.findElements(By.xpath(
                "(//button[contains(@aria-label,'Open' ) and contains(@aria-label,'calendar')]|" +
                        "//button[contains(@aria-label,'Calendar')]|" +
                        "//button[contains(@class,'calendar')]|" +
                        "//button[.//*[name()='svg']])[1]"));
        if (!btns.isEmpty()) tryClick(btns.get(0));
    }

    protected void clickDayInAnyDatepicker(LocalDate date) {
        String aria = date.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US));
        By dayBtn = By.xpath("//button[@aria-label='"+aria+"'] | //td//button[@aria-label='"+aria+"']");
        for (int i = 0; i < 12 && d.findElements(dayBtn).isEmpty(); i++) {
            List<WebElement> next = d.findElements(By.xpath("//button[contains(@aria-label,'Next') or contains(@aria-label,'next')]"));
            if (next.isEmpty()) break;
            tryClick(next.get(0));
        }
        WebElement day = retry(4, () -> d.findElement(dayBtn));
        tryClick(day);
    }
}
