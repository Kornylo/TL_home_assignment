package qa.translink.pom;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.ITestResult;
import org.testng.SkipException;
import org.testng.annotations.*;

import java.time.Duration;
import java.util.List;

public class BaseTest {
    protected WebDriver d;
    protected WebDriverWait wait;

    @BeforeClass(alwaysRun = true)
    public void setUp() { initDriver(); }

    @BeforeMethod(alwaysRun = true)
    public void ensureDriverReady() {
        if (d == null) initDriver();
    }

    private void initDriver() {
        if (d != null) return;

        try {
            WebDriverManager.chromedriver().setup();

            ChromeOptions opts = new ChromeOptions();
            boolean headless = !"false".equalsIgnoreCase(System.getProperty("HEADLESS", "true"));
            if (headless) opts.addArguments("--headless=new");
            opts.addArguments(
                    "--window-size=1400,900",
                    "--disable-gpu",
                    "--no-sandbox",
                    "--lang=en-US",
                    "--disable-dev-shm-usage",
                    "--disable-features=AutomationControlled",
                    "--disable-blink-features=AutomationControlled",
                    "--remote-allow-origins=*",
                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36"
            );
            opts.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
            opts.setExperimentalOption("useAutomationExtension", false);

            d = new ChromeDriver(opts);

            wait = new WebDriverWait(d, Duration.ofSeconds(30), Duration.ofMillis(250));

            if (d instanceof JavascriptExecutor js) {
                js.executeScript("Object.defineProperty(navigator,'webdriver',{get:()=>undefined})");
            }
        } catch (Throwable t) {
            System.err.println("[BaseTest] ChromeDriver init failed: " + t);
            t.printStackTrace();
            throw new SkipException("Cannot start ChromeDriver: " + t.getMessage(), t);
        }

        if (d == null) {
            throw new SkipException("Driver is null after init (unexpected).");
        }
    }

    @AfterMethod(alwaysRun = true)
    public void snapOnFail(ITestResult r) {
        try {
            if (!r.isSuccess() && d instanceof TakesScreenshot ts) {
                byte[] shot = ts.getScreenshotAs(OutputType.BYTES);
                // TODO: Allure.addAttachment("screenshot", new ByteArrayInputStream(shot));
            }
        } catch (Throwable ignore) {}
    }

    @AfterClass(alwaysRun = true)
    public void tearDown() {
        try { if (d != null) d.quit(); } catch (Throwable ignore) {}
        d = null; wait = null;
    }
}
