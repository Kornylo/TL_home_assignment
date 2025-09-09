package qa.translink.pom.pages;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class FavouritesPage extends BasePage {
    public FavouritesPage(WebDriver d, org.openqa.selenium.support.ui.WebDriverWait wait) {
        super(d, wait);
    }

    public void assertFavouriteVisible(String favName) {
        wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//*[contains(normalize-space(.), \"" + favName + "\")]")
        ));
        if (!d.getPageSource().toLowerCase().contains(favName.toLowerCase()))
            throw new AssertionError("Favourite not visible: " + favName);
    }
}
