package io.quarkus.search.app;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.search.app.DatasetConstants.GuideIds;
import io.quarkus.search.app.dto.SearchHit;
import io.quarkus.search.app.dto.SearchResult;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.common.mapper.TypeRef;

@QuarkusTest
@TestHTTPEndpoint(SearchService.class)
public class SearchServiceTest {
    private static final TypeRef<SearchResult<SearchHit>> SEARCH_RESULT_SEARCH_HITS = new TypeRef<>() {
    };

    @Test
    public void queryNotMatching() {
        var result = given()
                .queryParam("q", "termnotmatching")
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.total()).isEqualTo(0);
        assertThat(result.hits()).isEmpty();
    }

    @Test
    public void queryMatchingFullTerm() {
        var result = given()
                .queryParam("q", "orm")
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.total()).isEqualTo(7);
        // We check order in another test
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(
                DatasetConstants.GuideIds.HIBERNATE_ORM,
                GuideIds.HIBERNATE_ORM_PANACHE,
                GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideIds.HIBERNATE_REACTIVE,
                GuideIds.HIBERNATE_REACTIVE_PANACHE,
                GuideIds.SPRING_DATA_JPA);
    }

    @Test
    public void queryMatchingPrefixTerm() {
        var result = given()
                .queryParam("q", "hiber")
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.total()).isEqualTo(8);
        // We check order in another test
        assertThat(result.hits()).extracting(SearchHit::id).containsExactlyInAnyOrder(
                GuideIds.HIBERNATE_ORM,
                GuideIds.HIBERNATE_ORM_PANACHE,
                GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                GuideIds.HIBERNATE_REACTIVE,
                GuideIds.HIBERNATE_REACTIVE_PANACHE,
                GuideIds.SPRING_DATA_JPA,
                GuideIds.DUPLICATED_CONTEXT);
    }

    @Test
    public void queryEmptyString() {
        var result = given()
                .queryParam("q", "")
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.hits()).extracting(SearchHit::id)
                .containsExactlyInAnyOrder(DatasetConstants.GuideIds.ALL);
    }

    @Test
    public void queryNotProvided() {
        var result = when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        assertThat(result.total()).isEqualTo(10);
        assertThat(result.hits()).extracting(SearchHit::id)
                .containsExactlyInAnyOrder(DatasetConstants.GuideIds.ALL);
    }

    @ParameterizedTest
    @MethodSource("relevance_params")
    public void relevance(String query, List<String> expectedGuideIds) {
        var result = given()
                .queryParam("q", query)
                .when().get()
                .then()
                .statusCode(200)
                .extract().body().as(SEARCH_RESULT_SEARCH_HITS);
        // Using "startsWith" here, because what we want is to have the most relevant hits first.
        // We don't mind that much if there's a trail of not-so-relevant hits.
        assertThat(result.hits()).extracting(SearchHit::id).startsWith(
                expectedGuideIds.toArray(String[]::new));
    }

    private static List<Arguments> relevance_params() {
        return List.of(
                Arguments.of("orm", List.of(
                        // TODO Shouldn't the ORM guide be before Panache? Hibernate Reactive before Spring Data JPA?
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.HIBERNATE_ORM,
                        GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideIds.HIBERNATE_REACTIVE_PANACHE,
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideIds.SPRING_DATA_JPA,
                        GuideIds.HIBERNATE_REACTIVE)),
                Arguments.of("reactive", List.of(
                        GuideIds.HIBERNATE_REACTIVE_PANACHE,
                        GuideIds.HIBERNATE_REACTIVE,
                        GuideIds.DUPLICATED_CONTEXT, // TODO: what is this doing here?
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.SPRING_DATA_JPA,
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideIds.HIBERNATE_ORM)),
                Arguments.of("hiber", List.of(
                        // TODO Hibernate Reactive/Search should be after ORM...
                        // TODO Shouldn't the ORM guide be before Panache? Hibernate Reactive before Spring Data JPA?
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH,
                        GuideIds.HIBERNATE_REACTIVE_PANACHE,
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.HIBERNATE_ORM,
                        GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideIds.HIBERNATE_REACTIVE,
                        GuideIds.SPRING_DATA_JPA)),
                Arguments.of("jpa", List.of(
                        // TODO we'd probably want ORM before Spring Data JPA (and before Panache?)
                        GuideIds.SPRING_DATA_JPA,
                        GuideIds.HIBERNATE_ORM_PANACHE_KOTLIN,
                        GuideIds.HIBERNATE_ORM_PANACHE,
                        GuideIds.HIBERNATE_REACTIVE_PANACHE, // TODO: this is odd... especially since the HR guide isn't here?
                        GuideIds.HIBERNATE_ORM)),
                Arguments.of("search", List.of(
                        GuideIds.HIBERNATE_SEARCH_ORM_ELASTICSEARCH)));
    }
}
