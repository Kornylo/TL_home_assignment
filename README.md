# TransLink POM (Selenium + TestNG)

Page Object Model framework for end-to-end testing of TransLink website.  
Structured with Page Objects, reusable components, and separate assertions. Ready to run locally or in CI (Linux/Windows/macOS, headless).

## Quick Start

```bash
# first run
mvn -q -DHEADLESS=true test
```

WebDriverManager will automatically download the correct chromedriver for your OS.

---

## Requirements

- JDK 17+ (tested with MS OpenJDK 21 and Temurin 17)
- Maven 3.9+
- Google Chrome 109+ (latest stable recommended)

> Works on Windows/macOS/Linux. Headless mode by default.

---

## Project Structure

```
src/test/java/qa/translink/pom/
  BaseTest.java                # WebDriver + WebDriverWait initialization
  tests/HomeworkFlowTest.java  # example end-to-end test

  pages/                       # Page Objects
    BasePage.java
    HomePage.java
    BusSchedulesPage.java
    RoutePage.java
    FavouritesPage.java

  components/
    TimeFilter.java            # reusable date/time filter component

  assertions/
    ScheduleAsserts.java       # domain checks (headway/monotonic times)
```

---

## Running Tests

### Maven (local)

```bash
# headless (default)
mvn -q -DHEADLESS=true test

# run with UI
mvn -q -DHEADLESS=false test
```

### From IDE (IntelliJ IDEA)

- Run `HomeworkFlowTest` as TestNG.
- Add system properties in Run Configuration if needed:  
  `-DHEADLESS=true -DHEADWAY_MAX=60`

---

## Configuration

### Key dependencies (`pom.xml`)

- `selenium-java` 4.23+
- `testng` 7.10+
- `webdrivermanager` 5.9+
- (optional) `slf4j-simple` for logging in `test` scope

```xml
<dependency>
  <groupId>org.slf4j</groupId>
  <artifactId>slf4j-simple</artifactId>
  <version>2.0.13</version>
  <scope>test</scope>
</dependency>
```

### Optional `testng.xml`

```xml
<!DOCTYPE suite SYSTEM "https://testng.org/testng-1.0.dtd" >
<suite name="TransLink Suite" parallel="false">
  <test name="All">
    <classes>
      <class name="qa.translink.pom.tests.HomeworkFlowTest"/>
    </classes>
  </test>
</suite>
```

And configure in `maven-surefire-plugin`:

```xml
<suiteXmlFiles>
  <suiteXmlFile>testng.xml</suiteXmlFile>
</suiteXmlFiles>
```

---

## Cross-Platform Notes

- **Windows**: Works out of the box. WebDriverManager fetches `chromedriver.exe`.  
  If Chrome is in a non-standard path:
  ```java
  opts.setBinary("C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe");
  ```

- **Linux CI**: `--no-sandbox` and `--disable-dev-shm-usage` are already included.
- **macOS**: works without changes.

---



> For Windows runner replace `runs-on: windows-latest`. No code change required.

---

## Configurable Properties

| Property       | Type  | Default | Description                            |
|----------------|-------|---------|----------------------------------------|
| `HEADLESS`     | bool  | `true`  | Run Chrome in headless mode             |



## Troubleshooting

- **`NullPointerException` on `executeScript` or `wait`**  
  Ensure `BaseTest` creates the driver *before* `WebDriverWait`. Fixed in this project.

- **`SLF4J(W): No providers were found`**  
  Add `slf4j-simple` in test scope.

- **`chrome not found` (Linux CI / non-standard path)**
  ```java
  opts.setBinary("/usr/bin/google-chrome"); // or /usr/bin/chromium
  ```

- **Headless rendering issues (rare on Windows)**  
  Add flag:
  ```java
  opts.addArguments("--disable-software-rasterizer");
  ```

- **Chrome version mismatch**  
  WebDriverManager manages compatibility automatically. For corporate proxy, allow GitHub/Maven Central.

---

## Extending

- **Allure**: add listener + screenshot attachments in `@AfterMethod`.
- **Parallel run**: configure `maven-surefire-plugin` with `parallel=methods`, use `ThreadLocal` WebDriver.
- **Env configs**: externalize URLs, credentials, and timeouts via `config.properties` and `-Denv=...`.

---

## License

MIT. Free to use and modify.
