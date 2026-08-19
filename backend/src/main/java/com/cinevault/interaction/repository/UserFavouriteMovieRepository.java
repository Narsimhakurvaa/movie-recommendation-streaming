package com.cinevault.interaction.repository;

import com.cinevault.interaction.domain.UserFavouriteMovie;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFavouriteMovieRepository
        extends JpaRepository<UserFavouriteMovie, UserFavouriteMovie.Key> {

    @Query("select f from UserFavouriteMovie f join fetch f.movie where f.user.id = :userId")
    List<UserFavouriteMovie> findByUserId(@Param("userId") Long userId);

    boolean existsByUserIdAndMovieId(Long userId, Long movieId);

    void deleteByUserIdAndMovieId(Long userId, Long movieId);
}
