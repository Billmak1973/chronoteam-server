package org.example.website.repository;

import org.example.website.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByCustomer_Username(String username);

    List<Favorite> findByCustomer_UsernameOrderByCreatedAtDesc(String username);
    Favorite findByCustomer_UsernameAndProduct_Id(String username, Integer productId);
}