package se.cag.labs.currentrace.apicontroller;

import com.jayway.restassured.*;
import org.junit.*;
import org.junit.runner.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.*;
import org.springframework.http.*;
import org.springframework.test.context.*;
import org.springframework.test.context.junit4.*;
import org.springframework.test.context.web.*;
import se.cag.labs.currentrace.*;
import se.cag.labs.currentrace.services.repository.*;
import se.cag.labs.currentrace.services.repository.datamodel.*;

import static com.jayway.restassured.RestAssured.*;
import static org.hamcrest.core.Is.*;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = CurrentRaceApplication.class)
@WebAppConfiguration
@IntegrationTest("server.port:0")
@TestPropertySource(locations = "classpath:application-test.properties")
public class CurrentRaceControllerTest {
    @Autowired
    private CurrentRaceRepository repository;
    @Value("${local.server.port}")
    private int port;

    @Before
    public void setup() {
        repository.deleteAll();

        RestAssured.port = port;
    }

    @Test
    public void canStartRace_OnlyByPost() {
        given().param(CurrentRaceController.START_RACE_URL, "asd").when().get("/startRace").then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param(CurrentRaceController.START_RACE_URL, "asd").when().delete("/startRace").then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param(CurrentRaceController.START_RACE_URL, "asd").when().put("/startRace").then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param(CurrentRaceController.START_RACE_URL, "asd").when().patch("/startRace").then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());

        given().param("callbackUrl", "asd").
                when().post(CurrentRaceController.START_RACE_URL).
                then().statusCode(HttpStatus.ACCEPTED.value());

        RaceStatus raceStatus = repository.findByRaceId(RaceStatus.ID);

        assertNotNull(raceStatus);
        assertEquals(RaceStatus.State.ACTIVE, raceStatus.getState());
    }

    @Test
    public void startRace_returnFoundIfAlreadyActive() {
        RaceStatus raceStatus = new RaceStatus();
        raceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(raceStatus);

        given().param("callbackUrl", "asd").
                when().post(CurrentRaceController.START_RACE_URL).
                then().statusCode(HttpStatus.FOUND.value());
    }

    @Test
    public void canCancelRaceIfStartedAndOnlyPost() {
        when().get(CurrentRaceController.CANCEL_RACE_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        when().put(CurrentRaceController.CANCEL_RACE_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        when().delete(CurrentRaceController.CANCEL_RACE_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        when().patch(CurrentRaceController.CANCEL_RACE_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());

        RaceStatus raceStatus = new RaceStatus();
        raceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(raceStatus);

        when().post(CurrentRaceController.CANCEL_RACE_URL).
                then().statusCode(HttpStatus.ACCEPTED.value());

        raceStatus = repository.findByRaceId(RaceStatus.ID);

        assertNotNull(raceStatus);
        assertEquals(RaceStatus.State.INACTIVE, raceStatus.getState());
    }

    @Test
    public void canNotCancelRaceIfNotStarted() {
        when().post(CurrentRaceController.CANCEL_RACE_URL).
                then().statusCode(HttpStatus.NOT_FOUND.value());
    }

    @Test
    public void canGetStatusOnlyByGet() {
        when().post(CurrentRaceController.STATUS_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        when().delete(CurrentRaceController.STATUS_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        when().put(CurrentRaceController.STATUS_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        when().patch(CurrentRaceController.STATUS_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());

        RaceStatus raceStatus = new RaceStatus();
        raceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(raceStatus);

        when().get(CurrentRaceController.STATUS_URL).
                then().statusCode(HttpStatus.OK.value()).
                body("state", is(RaceStatus.State.ACTIVE.name()));
    }

    @Test
    public void canUpdatePassageTime_OnlyByPost() {
        given().param("sensorID", "START_ID").param("timestamp", 1234).
                when().get(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param("sensorID", "START_ID").param("timestamp", 1234).
                when().delete(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param("sensorID", "START_ID").param("timestamp", 1234).
                when().put(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param("sensorID", "START_ID").param("timestamp", 1234).
                when().patch(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());


        RaceStatus raceStatus = new RaceStatus();
        raceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(raceStatus);

        given().param("sensorID", "START_ID").param("timestamp", 1234).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());
        given().param("sensorID", "MIDDLE_ID").param("timestamp", 12345).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());
        given().param("sensorID", "FINISH_ID").param("timestamp", 123456).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());

        raceStatus = repository.findByRaceId(RaceStatus.ID);
        assertNotNull(raceStatus);
        assertEquals(new Long(1234), raceStatus.getStartTime());
        assertEquals(new Long(12345), raceStatus.getMiddleTime());
        assertEquals(new Long(123456), raceStatus.getFinishTime());
        assertEquals(RaceStatus.Event.FINISH, raceStatus.getEvent());
        assertEquals(RaceStatus.State.INACTIVE, raceStatus.getState());
    }

    @Test
    public void canNotUpdatePassageTime_WithFaultySensorID() {
        RaceStatus raceStatus = new RaceStatus();
        raceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(raceStatus);

        given().param("sensorID", "FAULTY").param("timestamp", 1234).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.EXPECTATION_FAILED.value());
    }

    @Test
    public void secondPassageOfMiddleSensorIsIgnored() {
        RaceStatus raceStatus = new RaceStatus();
        raceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(raceStatus);

        given().param("sensorID", "MIDDLE_ID").param("timestamp", 1234).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());
        given().param("sensorID", "MIDDLE_ID").param("timestamp", 12345).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ALREADY_REPORTED.value());

        raceStatus = repository.findByRaceId(RaceStatus.ID);
        assertNotNull(raceStatus);
        assertEquals(new Long(1234), raceStatus.getMiddleTime());
    }
}