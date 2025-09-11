package qa.translink.pom.pages;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.Optional;

public class BusSchedulesPage extends BasePage {

    public BusSchedulesPage(WebDriver d, org.openqa.selenium.support.ui.WebDriverWait wait) {
        super(d, wait);
    }

    public void searchRoute(String query) {
        WebElement search = findSearchBox();
        search.clear();
        search.sendKeys(query);
        if (!clickFindScheduleIfPresent()) search.sendKeys(Keys.ENTER);
    }

    public RoutePage openRouteExact(String linkTextExact) {
        wait.until(ExpectedConditions.presenceOfElementLocated(By.linkText(linkTextExact))).click();
        return new RoutePage(d, wait);
    }

    private WebElement findSearchBox() {
        return retry(10, () -> {
            Optional<WebElement> hit = d.findElements(By.id("find-schedule-searchbox"))
                    .stream().filter(WebElement::isDisplayed).findFirst();
            if (hit.isPresent()) return hit.get();
            throw new NoSuchElementException("searchbox not found");
        });
    }

    private boolean clickFindScheduleIfPresent() {
        try {
            By withinForm = By.xpath("//button[@class='flexContainer']");
            WebElement btn = retry(3, () -> d.findElements(withinForm).stream()
                    .filter(e -> e.isDisplayed() && e.getText().toLowerCase().contains("find"))
                    .findFirst().orElseThrow(() -> new NoSuchElementException("no btn")));
            tryClick(btn);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}

