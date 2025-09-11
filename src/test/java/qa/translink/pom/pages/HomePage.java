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
