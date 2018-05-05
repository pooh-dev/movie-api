package com.kh.lapshin.movieapi.model;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Objects;

@Entity(name = "FavoriteActor")
@Table(name = "favorite_actor")
public class FavoriteActor {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private Long actorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    public FavoriteActor() {
    }

    public FavoriteActor(Long actorId) {
        this.actorId = actorId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FavoriteActor actor = (FavoriteActor) o;
        return Objects.equals(actorId, actor.actorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(actorId);
    }
}
