package com.kh.lapshin.movieapi.controller;

import com.kh.lapshin.movieapi.model.FavoriteActor;
import com.kh.lapshin.movieapi.model.User;
import com.kh.lapshin.movieapi.model.WatchedMovie;
import com.kh.lapshin.movieapi.repositories.UserRepository;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("api")
public class MovieApiController {

    private static final SimpleDateFormat YEAR_MONTH_DAY = new SimpleDateFormat("yyyy-MM-dd");

    private UserRepository userRepository;

    @Value("${tmdb.apikey}")
    private String tmdbApiKey;
    @Value("${tmdb.language}")
    private String tmdbLanguage;
    @Value("${tmdb.api.base.url}")
    private String tmdbApiBaseUrl;
    @Value("${error.user.exists}")
    private String userExistsError;
    @Value("${error.incorrect.api.key}")
    private String incorrectApiKeyError;
    @Value("${error.tmdb.no.actor}")
    private String absentTmdbActorError;
    @Value("${error.incorrect.actor.id}")
    private String incorrectActorIdError;
    @Value("${error.tmdb.no.movie}")
    private String absentTmdbMovieError;
    @Value("${error.incorrect.year.month}")
    private String incorrectYearMonthError;

    public MovieApiController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @RequestMapping(
            value = "/registerUser",
            method = RequestMethod.POST,
            consumes="application/json",
            produces = "application/json"
    )
    @ResponseBody
    public String registerUser(@RequestBody User registerInfo) {
        User user = userRepository.findByLogin(registerInfo.getLogin());
        if (user != null) {
            return getJsonResult("error", userExistsError).toString();
        }

        user = new User(registerInfo.getLogin(), registerInfo.getPassword());
        userRepository.save(user);
        return getJsonResult("api_key", user.getApiKey()).toString();
    }

    @RequestMapping(
            value = "/addFavoriteActor/{actorId}",
            produces = "application/json"
    )
    public String addFavoriteActor(
            @PathVariable("actorId") long actorId,
            @RequestParam("apiKey") String apiKey
    ) throws UnirestException {

        User user = userRepository.findByApiKey(apiKey);
        if (user == null) {
            return getJsonResult("error", incorrectApiKeyError).toString();
        }

        String actorUrl = getTmdbUrl("/person/", actorId);
        HttpResponse<JsonNode> actorResponse = Unirest.get(actorUrl).asJson();
        if (actorResponse.getStatus() == 404) {
            return getJsonResult("error", absentTmdbActorError).toString();
        }

        FavoriteActor actor = new FavoriteActor(actorId);
        user.addFavoriteActor(actor);
        userRepository.save(user);

        return getJsonResult("added_favorite_actor", actorResponse.getBody().getObject()).toString();
    }

    @RequestMapping(
            value = "/removeFavoriteActor/{actorId}",
            produces = "application/json"
    )
    public String removeFavoriteActor(
            @PathVariable("actorId") long actorId,
            @RequestParam("apiKey") String apiKey
    ) throws UnirestException {
        User user = userRepository.findByApiKey(apiKey);
        if (user == null) {
            return getJsonResult("error", incorrectApiKeyError).toString();
        }

        FavoriteActor actor = new FavoriteActor(actorId);
        if (!user.getFavoriteActors().contains(actor)) {
            return getJsonResult("error", incorrectActorIdError).toString();
        }

        String actorUrl = getTmdbUrl("/person/", actorId);
        HttpResponse<JsonNode> actorResponse = Unirest.get(actorUrl).asJson();
        if (actorResponse.getStatus() == 404) {
            return getJsonResult("error", absentTmdbActorError).toString();
        }

        user.removeFavoriteActor(actor);
        userRepository.save(user);

        return getJsonResult("removed_favorite_actor", actorResponse.getBody().getObject()).toString();
    }

    @RequestMapping(
            value = "/markMovieWatched/{movieId}",
            produces = "application/json"
    )
    public String markMovieWatched(
            @PathVariable("movieId") long movieId,
            @RequestParam("apiKey") String apiKey
    ) throws UnirestException {

        User user = userRepository.findByApiKey(apiKey);
        if (user == null) {
            return getJsonResult("error", incorrectApiKeyError).toString();
        }

        String movieUrl = getTmdbUrl("/movie/", movieId);
        HttpResponse<JsonNode> movieResponse = Unirest.get(movieUrl).asJson();
        if (movieResponse.getStatus() == 404) {
            return getJsonResult("error", absentTmdbMovieError).toString();
        }

        WatchedMovie movie = new WatchedMovie(movieId);
        user.addWatchedMovie(movie);
        userRepository.save(user);

        return getJsonResult("watched_movie", movieResponse.getBody().getObject()).toString();
    }

    @RequestMapping(
            value = "/searchMoviesByYearMonth/{year}/{month}",
            produces = "application/json"
    )
    public String searchMoviesByYearMonth(
            @PathVariable("year") int year,
            @PathVariable("month") int month,
            @RequestParam("apiKey") String apiKey
    ) throws UnirestException, InterruptedException {

        User user = userRepository.findByApiKey(apiKey);
        if (user == null) {
            return getJsonResult("error", incorrectApiKeyError).toString();
        }

        if (year < 1881 || year > 2050 || month < 0 || month > 12) {
            return getJsonResult("error", incorrectYearMonthError).toString();
        }

        // make a start and finish dates
        Calendar calendar = Calendar.getInstance();
        calendar.set(year, --month, 1);
        String startDate = YEAR_MONTH_DAY.format(calendar.getTime());
        calendar.set(Calendar.DATE, calendar.getActualMaximum(Calendar.DATE));
        String finishDate = YEAR_MONTH_DAY.format(calendar.getTime());

        JSONArray unwatchedMoviesWithFavoriteActors = new JSONArray();
        int page = 1;

        JSONArray moviesOnPage;
        do {
            String moviesByYearMonthUrl =
                    getMoviesByYearMonthWithFavoriteActorsUrl(startDate, finishDate, page, user.getFavoriteActors());
            HttpResponse<JsonNode> movieResponse = Unirest.get(moviesByYearMonthUrl).asJson();
            moviesOnPage = movieResponse.getBody().getObject().getJSONArray("results");
            moviesOnPage.forEach(movie -> {
                if (! isMovieWatched(movie, user.getWatchedMovies())) {
                    unwatchedMoviesWithFavoriteActors.put(movie);
                }
            });
            page++;
            TimeUnit.MILLISECONDS.sleep(100); // TMDB requires not more than 10 requests per second
        } while (moviesOnPage.length() != 0);

        return getJsonResult("unwatched_movies", unwatchedMoviesWithFavoriteActors).toString();
    }

    private boolean isMovieWatched(Object movie, Set<WatchedMovie> watchedMovies) {
        JSONObject jsonMovie = (JSONObject) movie;
        return watchedMovies.stream()
                .map(WatchedMovie::getMovieId)
                .anyMatch(movieId -> movieId == jsonMovie.getLong("id"));
    }

    private JSONObject getJsonResult(String key, Object objectToTransform) {
        JSONObject result = new JSONObject();
        result.put(key, objectToTransform);
        return result;
    }

    private String getMoviesByYearMonthWithFavoriteActorsUrl(
            String startDate,
            String finishDate,
            int page,
            Set<FavoriteActor> favoriteActors
    ) {
        String favoriteActorIds = favoriteActors.stream()
                .map(actor -> actor.getActorId().toString())
                .collect(Collectors.joining("%7C"));

        return tmdbApiBaseUrl + "/discover/movie?api_key=" + tmdbApiKey +
            "&language=" + tmdbLanguage + "&page=" + page +
            "&primary_release_date.gte=" + startDate + "&primary_release_date.lte=" + finishDate +
            "&with_cast=" + favoriteActorIds;
    }

    private String getTmdbUrl(String tmdbObject, long id) {
        return tmdbApiBaseUrl + tmdbObject + id + "?language=" + tmdbLanguage + "&api_key=" + tmdbApiKey;
    }
}
