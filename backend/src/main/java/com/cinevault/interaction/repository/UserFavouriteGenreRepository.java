package com.cinevault.interaction.repository;

import com.cinevault.interaction.domain.UserFavouriteGenre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserFavouriteGenreRepository
        extends JpaRepository<UserFavouriteGenre, UserFavouriteGenre.Key> {

    @Query("select f from UserFavouriteGenre f join fetch f.genre where f.user.id = :userId")
    List<UserFavouriteGenre> findByUserId(@Param("userId") Long userId);

    @Query("select f.genre.id from UserFavouriteGenre f where f.user.id = :userId")
    List<Long> findGenreIdsByUserId(@Param("userId") Long userId);

    void deleteByUserId(Long userId);
}
