package qa.translink.pom.pages;

import org.openqa.selenium.*;
import qa.translink.pom.components.TimeFilter;

import java.time.LocalDate;
import java.time.LocalTime;

public class RoutePage extends BasePage {

    public RoutePage(WebDriver d, org.openqa.selenium.support.ui.WebDriverWait wait) {
        super(d, wait);
    }

    public void setDateTime(LocalDate date, LocalTime start, LocalTime end) {
        new TimeFilter(d, wait)
                .setDate(date)
                .setStart(start)
                .setEnd(end)
                .submit();
    }

    public void openStopByNumber(String stopNumber) {
        WebElement link = retry(20, () -> d.findElements(By.tagName("a")).stream()
                .filter(WebElement::isDisplayed)
                .filter(a -> a.getText().toLowerCase().contains(stopNumber.toLowerCase()))
                .findFirst().orElseThrow(() -> new NoSuchElementException("stop link: " + stopNumber)));
        js("arguments[0].scrollIntoView({block:'center'});", link);
        try { link.click(); } catch (ElementClickInterceptedException e) { js("arguments[0].click();", link); }
    }

    public void addToFavourites(String name) {
        d.findElement(By.xpath("(//button[@data-infowindow='Add to Favourites'])[2]")).click();
        WebElement input = d.findElement(By.xpath("//input[@name='gtfsFavouriteKey']"));
        input.clear(); input.sendKeys(name);
        d.findElement(By.xpath("//*[@id='add-to-favourites_dialog']/form/section/gtfs-favourite/div/button")).click();
    }

    public FavouritesPage openManageFavourites() {
        clickLinkByTextContainsAny("Manage my favourites", "Manage my favorites", "My favourites", "My favorites");
        return new FavouritesPage(d, wait);
    }

    // internals
    private void clickLinkByTextContainsAny(String... texts) {
        for (String t : texts) { try { clickLinkByTextContains(t); return; } catch (RuntimeException ignored) {} }
        throw new NoSuchElementException("none of variants present");
    }

    private void clickLinkByTextContains(String text) {
        WebElement found = retry(20, () -> d.findElements(By.tagName("a")).stream()
                .filter(WebElement::isDisplayed)
                .filter(a -> a.getText().toLowerCase().contains(text.toLowerCase()))
                .findFirst().orElseThrow(() -> new NoSuchElementException("link: " + text)));
        js("arguments[0].scrollIntoView({block: 'center'});", found);
        try { found.click(); } catch (ElementClickInterceptedException e) { js("arguments[0].click();", found); }
    }
}
