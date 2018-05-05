package com.kh.lapshin.movieapi.repositories;

import com.kh.lapshin.movieapi.model.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
    User findByLogin(String login);
    User findByApiKey(String apiKey);
}
