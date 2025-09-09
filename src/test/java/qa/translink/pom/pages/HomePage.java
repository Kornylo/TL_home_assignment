package qa.translink.pom.pages;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class HomePage extends BasePage {

    public HomePage(WebDriver d, WebDriverWait wait) {
        super(d, wait);
    }

    public HomePage open() {
        d.navigate().to("https://www.translink.ca/");
        return this;
    }

    public HomePage acceptConsentIfAny() {
        d.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(0));
        try {
            WebDriverWait w = new WebDriverWait(d, java.time.Duration.ofSeconds(4));
            WebElement btn = w.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("#onetrust-accept-btn-handler, " +
                            "button[aria-label*='Accept' i], " +
                            "button[aria-label*='agree' i], " +
                            "button:has(svg[aria-label*='close' i])")));
            if (btn.isDisplayed()) tryClick(btn);
        } catch (Exception ignore) {
            // silent best effort
        } finally {
            d.manage().timeouts().implicitlyWait(java.time.Duration.ofSeconds(5));
        }
        return this;
    }

    public BusSchedulesPage openBusSchedules() {
        try {
            WebElement sched = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("header a[href*='/schedules-and-maps']")));
            js("arguments[0].scrollIntoView({block:'center'});", sched);

            WebElement bus = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("a[href*='/schedules-and-maps/bus-schedules']")));
            js("arguments[0].scrollIntoView({block:'center'});", bus);
            try { tryClick(bus); } catch (ElementClickInterceptedException e) { js("arguments[0].click();", bus); }
        } catch (TimeoutException e) {
            d.navigate().to("https://www.translink.ca/schedules-and-maps/bus-schedules");
        }

        wait.until(ExpectedConditions.urlContains("/schedules-and-maps"));
        wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.xpath("//main//h1[contains(.,'Schedules') and contains(.,'Bus')]")),
                ExpectedConditions.presenceOfElementLocated(By.xpath("//a[contains(@href,'bus-schedules')]"))
        ));

        return new BusSchedulesPage(d, wait);
    }
}
