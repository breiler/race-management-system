package se.cag.labs.currentrace.apicontroller;

import com.github.fakemongo.Fongo;
import com.jayway.restassured.RestAssured;
import com.lordofthejars.nosqlunit.mongodb.MongoDbRule;
import com.mongodb.Mongo;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.config.AbstractMongoConfiguration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import se.cag.labs.currentrace.CurrentRaceApplication;
import se.cag.labs.currentrace.apicontroller.apimodel.RaceStatus;
import se.cag.labs.currentrace.apicontroller.apimodel.User;
import se.cag.labs.currentrace.services.CallbackService;
import se.cag.labs.currentrace.services.UserManagerService;
import se.cag.labs.currentrace.services.repository.CurrentRaceRepository;
import se.cag.labs.currentrace.services.repository.datamodel.CurrentRaceStatus;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.jayway.restassured.RestAssured.given;
import static com.jayway.restassured.RestAssured.when;
import static com.lordofthejars.nosqlunit.mongodb.MongoDbRule.MongoDbRuleBuilder.newMongoDbRule;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = {CurrentRaceApplication.class})
// NOTE!! order is important
@WebAppConfiguration()
@IntegrationTest("server.port:0")
@TestPropertySource(locations = "classpath:application-test.properties")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@ActiveProfiles("test")
public class CurrentRaceControllerTest {
    @Rule
    public MongoDbRule mongoDbRule = newMongoDbRule().defaultSpringMongoDb("fongo-test");
    @Autowired
    private ApplicationContext applicationContext; //Needed by nosqlunit

    @Autowired
    private CurrentRaceRepository repository;
    @Autowired
    private CallbackService callbackService; // This is a singleton and is the same instance as injected into application services
    @Autowired
    private UserManagerService userManagerService; // This is a singleton and is the same instance as injected into application services


    @Value("${local.server.port}")
    private int port;

    private String callbackUrl;
    @Mock
    private RestTemplate restTemplateMock;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(callbackService, "restTemplate", restTemplateMock);
        ReflectionTestUtils.setField(userManagerService, "restTemplate", restTemplateMock);
        repository.deleteAll();
        callbackUrl = "http://localhost:" + port + "/onracestatusupdate";
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

        CurrentRaceStatus currentRaceStatus = repository.findByRaceId(CurrentRaceStatus.ID);

        assertNotNull(currentRaceStatus);
        assertEquals(RaceStatus.State.ACTIVE, currentRaceStatus.getState());
    }

    @Test
    public void startRace_returnFoundIfAlreadyActive() {
        CurrentRaceStatus currentRaceStatus = new CurrentRaceStatus();
        currentRaceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(currentRaceStatus);

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

        CurrentRaceStatus currentRaceStatus = new CurrentRaceStatus();
        currentRaceStatus.setState(RaceStatus.State.ACTIVE);
        currentRaceStatus.setCallbackUrl(callbackUrl);
        repository.save(currentRaceStatus);

        when().post(CurrentRaceController.CANCEL_RACE_URL).
                then().statusCode(HttpStatus.ACCEPTED.value());

        currentRaceStatus = repository.findByRaceId(CurrentRaceStatus.ID);

        verify(restTemplateMock, atLeastOnce()).postForLocation(
                "http://localhost:" + port + "/onracestatusupdate",
                RaceStatus.builder().state(RaceStatus.State.INACTIVE).build());

        assertNotNull(currentRaceStatus);
        assertEquals(RaceStatus.State.INACTIVE, currentRaceStatus.getState());
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

        CurrentRaceStatus currentRaceStatus = new CurrentRaceStatus();
        currentRaceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(currentRaceStatus);

        when().get(CurrentRaceController.STATUS_URL).
                then().statusCode(HttpStatus.OK.value()).
                body("state", is(RaceStatus.State.ACTIVE.name()));
    }

    @Test
    public void canUpdatePassageTime_OnlyByPost() {
        given().param("sensorID", "START").param("timestamp", 1234).
                when().get(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param("sensorID", "START").param("timestamp", 1234).
                when().delete(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param("sensorID", "START").param("timestamp", 1234).
                when().put(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());
        given().param("sensorID", "START").param("timestamp", 1234).
                when().patch(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.METHOD_NOT_ALLOWED.value());


        CurrentRaceStatus currentRaceStatus = new CurrentRaceStatus();
        currentRaceStatus.setState(RaceStatus.State.ACTIVE);
        currentRaceStatus.setCallbackUrl(callbackUrl);
        repository.save(currentRaceStatus);

        assertEquals(1, repository.findAll().size());
        given().param("sensorID", "START").param("timestamp", 1234).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());
        given().param("sensorID", "SPLIT").param("timestamp", 12345).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());
        given().param("sensorID", "FINISH").param("timestamp", 123456).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());

        currentRaceStatus = repository.findByRaceId(CurrentRaceStatus.ID);

        verify(restTemplateMock, times(1)).postForLocation(
                "http://localhost:" + port + "/onracestatusupdate",
                RaceStatus.builder()
                        .event(RaceStatus.Event.START)
                        .startTime(new Date(1234))
                        .state(RaceStatus.State.ACTIVE)
                        .build());
        verify(restTemplateMock, times(1)).postForLocation(
                "http://localhost:" + port + "/onracestatusupdate",
                RaceStatus.builder()
                        .event(RaceStatus.Event.MIDDLE)
                        .startTime(new Date(1234))
                        .middleTime(new Date(12345))
                        .state(RaceStatus.State.ACTIVE)
                        .build());
        verify(restTemplateMock, times(1)).postForLocation(
                "http://localhost:" + port + "/onracestatusupdate",
                RaceStatus.builder()
                        .event(RaceStatus.Event.FINISH)
                        .startTime(new Date(1234))
                        .middleTime(new Date(12345))
                        .finishTime(new Date(123456))
                        .state(RaceStatus.State.INACTIVE)
                        .build());
        assertNotNull(currentRaceStatus);
        assertEquals(new Long(1234), currentRaceStatus.getStartTime());
        assertEquals(new Long(12345), currentRaceStatus.getMiddleTime());
        assertEquals(new Long(123456), currentRaceStatus.getFinishTime());
        assertEquals(RaceStatus.Event.FINISH, currentRaceStatus.getEvent());
        assertEquals(RaceStatus.State.INACTIVE, currentRaceStatus.getState());
    }

    @Test
    public void canNotUpdatePassageTime_WithFaultySensorID() {
        CurrentRaceStatus currentRaceStatus = new CurrentRaceStatus();
        currentRaceStatus.setState(RaceStatus.State.ACTIVE);
        repository.save(currentRaceStatus);

        given().param("sensorID", "FAULTY").param("timestamp", 1234).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.EXPECTATION_FAILED.value());
    }

    @Test
    public void secondPassageOfMiddleSensorIsIgnored() {
        CurrentRaceStatus currentRaceStatus = new CurrentRaceStatus();
        currentRaceStatus.setState(RaceStatus.State.ACTIVE);
        currentRaceStatus.setCallbackUrl(callbackUrl);
        repository.save(currentRaceStatus);

        given().param("sensorID", "SPLIT").param("timestamp", 1234).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ACCEPTED.value());
        given().param("sensorID", "SPLIT").param("timestamp", 12345).
                when().post(CurrentRaceController.PASSAGE_DETECTED_URL).then().statusCode(HttpStatus.ALREADY_REPORTED.value());

        currentRaceStatus = repository.findByRaceId(CurrentRaceStatus.ID);
        verify(restTemplateMock, times(1)).postForLocation(
                "http://localhost:" + port + "/onracestatusupdate",
                RaceStatus.builder()
                        .event(RaceStatus.Event.MIDDLE)
                        .middleTime(new Date(1234))
                        .state(RaceStatus.State.ACTIVE)
                        .build());
        assertNotNull(currentRaceStatus);
        assertEquals(new Long(1234), currentRaceStatus.getMiddleTime());
    }

    @Test
    public void getUsersFromExternalService() {
        ResponseEntity<List<User>> responseEntity = new ResponseEntity<>(Arrays.asList(User.builder().name("nisse").build()), HttpStatus.OK);
        org.mockito.Mockito.when(restTemplateMock.exchange(
                eq("http://localhost:10280/users"),
                eq(HttpMethod.GET),
                eq(null),
                eq(new ParameterizedTypeReference<List<User>>() {
                })))
                .thenReturn(responseEntity);

        List<User> userList = userManagerService.getUsers();
        verify(restTemplateMock, times(1)).exchange(
                eq("http://localhost:10280/users"),
                eq(HttpMethod.GET),
                eq(null),
                eq(new ParameterizedTypeReference<List<User>>() {
                }));
        assertNotNull(userList);
        assertEquals("nisse", userList.get(0).getName());
    }

    @Profile("test")
    @Configuration
    @EnableMongoRepositories
    @ComponentScan(basePackageClasses = {CurrentRaceRepository.class})
    static class MongoConfiguration extends AbstractMongoConfiguration {

        @Override
        protected String getDatabaseName() {
            return "fongo-test";
        }

        @Bean
        @Override
        public Mongo mongo() {
            return new Fongo("test-db").getMongo();
        }

        @Override
        protected String getMappingBasePackage() {
            return "se.cag.labs.currentrace.services.repository";
        }
    }
}
