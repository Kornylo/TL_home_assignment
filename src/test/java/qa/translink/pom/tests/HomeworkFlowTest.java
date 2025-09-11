package qa.translink.pom.tests;

import org.testng.Assert;
import org.testng.annotations.Test;
import qa.translink.pom.BaseTest;
import qa.translink.pom.assertions.ScheduleAsserts;
import qa.translink.pom.pages.*;

import java.time.*;

public class HomeworkFlowTest extends BaseTest {

    @Test
    public void homework_and_bonus_pom() {
        // 1) Home → Bus Schedules
        HomePage home = new HomePage(d, wait).open();
        BusSchedulesPage schedules = home.openBusSchedules();
        // 2) Search 99
        schedules.searchRoute("99");
        // 3) Open exact route link (#99 - UBC B-Line)
        RoutePage route = schedules.openRouteExact("#99 - UBC B-Line");
        // 4) Set date tomorrow 07:30–08:30
        ZoneId van = ZoneId.of("America/Vancouver");
        LocalDate target = LocalDate.now(van).plusDays(1);
        route.setDateTime(target,
                LocalTime.parse("07:30"),
                LocalTime.parse("08:30"));
        // 5) Bonus assert: stop times monotonic + headway
        ScheduleAsserts.assertFirstFourIncreasingAndHeadway(d, "50913");
        // 6) Open stop and add favourite
        route.openStopByNumber("50913");
        String fav = "99 UBC B-Line – Morning Schedule";
        route.addToFavourites(fav);
        // 7) Manage favourites and assert presence
        FavouritesPage favs = route.openManageFavourites();
        favs.assertFavouriteVisible(fav);
        // sanity
        Assert.assertTrue(true);
    }
}
